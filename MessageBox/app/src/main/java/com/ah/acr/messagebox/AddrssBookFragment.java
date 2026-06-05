package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.AddressAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentAddressBookBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.util.AvatarHelper;
import com.ah.acr.messagebox.util.AvatarPickerHelper;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

import java.util.Date;
import java.util.List;


public class AddrssBookFragment extends Fragment {
    private static final String TAG = AddrssBookFragment.class.getSimpleName();
    private static final int MY_AVATAR_SIZE_DP = 60;

    private FragmentAddressBookBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressAdapter mAdapter;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    // My Profile state
    private String myImei = null;
    private String myNickname = null;
    private String myAvatarPath = null;

    // Avatar edit state
    private String targetAvatarImei = null;

    // Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> handleImagePicked(uri)
        );
    }


    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        binding = FragmentAddressBookBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupViewModel();

        binding.buttonAddrNew.setOnClickListener(view -> {
            showAddressDialog(null);
        });

        binding.buttonEdit.setOnClickListener(view -> showMeAddressDialog());

        binding.btnAckSettings.setOnClickListener(view -> showAckSettingsDialog());

        binding.frameMyAvatar.setOnClickListener(view -> {
            if (myImei == null || myImei.isEmpty()) {
                Toast.makeText(getContext(),
                        getString(R.string.addr_connect_first),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showAvatarMenu(myImei);
        });

        binding.buttonPassword.setOnClickListener(view -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Toast.makeText(getContext(),
                        getString(R.string.addr_connect_first),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                NavHostFragment.findNavController(AddrssBookFragment.this)
                        .navigate(R.id.action_main_setting_fragment_to_main_ble_login_change_fragment);
            } catch (Exception e) {
                Log.e(TAG, "Navigation error: " + e);
                Toast.makeText(getContext(),
                        getString(R.string.addr_password_open_fail),
                        Toast.LENGTH_SHORT).show();
            }
        });

        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, deviceInfo.toString());

                String unitNum = deviceInfo.getImei();
                myImei = unitNum;
                binding.textNumber.setText(unitNum);

                addressViewModel.getAddressByNumbers(unitNum).observe(getViewLifecycleOwner(), new Observer<AddressEntity>() {
                    @Override
                    public void onChanged(AddressEntity addressEntity) {
                        if (addressEntity == null) {
                            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                            String nicName = shared.getString("nicName");
                            String defaultName = getString(R.string.addr_my_default_name);
                            if (nicName.isEmpty()) shared.putAny("nicName", defaultName);
                            String displayName = nicName.isEmpty() ? defaultName : nicName;
                            binding.textName.setText(displayName);
                            myNickname = displayName;
                            myAvatarPath = null;
                        } else {
                            binding.textName.setText(addressEntity.getNumbersNic());
                            myNickname = addressEntity.getNumbersNic();
                            myAvatarPath = addressEntity.getAvatarPath();
                        }

                        updateMyAvatar();
                    }
                });

                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                shared.putAny("unitCode", unitNum);
            }
        });

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                binding.buttonPassword.setAlpha(1.0f);
            } else {
                binding.buttonPassword.setAlpha(0.4f);
            }
        });

        updateMyAvatar();

        return binding.getRoot();
    }


    // ═══════════════════════════════════════════════════════════════
    //   AVATAR EDIT
    // ═══════════════════════════════════════════════════════════════

    private void showAvatarMenu(String imei) {
        if (imei == null || imei.isEmpty()) {
            Toast.makeText(getContext(),
                    getString(R.string.addr_imei_not_available),
                    Toast.LENGTH_SHORT).show();
            return;
        }

        targetAvatarImei = imei;

        String[] options = {
                getString(R.string.addr_avatar_gallery),
                getString(R.string.addr_avatar_initial)
        };

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.addr_avatar_title))
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        openGallery();
                    } else {
                        resetAvatar(imei);
                    }
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void openGallery() {
        try {
            pickImageLauncher.launch("image/*");
        } catch (Exception e) {
            Log.e(TAG, "Gallery launch failed: " + e.getMessage());
            Toast.makeText(getContext(),
                    getString(R.string.addr_avatar_gallery_fail),
                    Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * Handle image picked from gallery.
     *
     * BUGFIX (2026-04-25): Avatar not refreshing on 2nd upload
     * Root cause: Same file path -> DiffUtil thinks data unchanged -> no re-bind
     * Solution: AvatarPickerHelper now uses timestamp in filename
     *   {IMEI}_{timestamp}.jpg → unique path each time → DiffUtil detects change
     */
    private void handleImagePicked(Uri uri) {
        if (uri == null) {
            Log.v(TAG, "User cancelled image picker");
            return;
        }

        if (targetAvatarImei == null || targetAvatarImei.isEmpty()) {
            Log.e(TAG, "targetAvatarImei is null");
            return;
        }

        final String imei = targetAvatarImei;
        targetAvatarImei = null;

        // Save on background thread
        new Thread(() -> {
            String savedPath = AvatarPickerHelper.saveAvatarFromUri(
                    getContext(), uri, imei);

            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (savedPath != null) {
                    // Update DB - DiffUtil will see new path and refresh
                    addressViewModel.updateAvatarPath(imei, savedPath);

                    // If My Profile, update local state and UI
                    if (imei.equals(myImei)) {
                        myAvatarPath = savedPath;
                        updateMyAvatar();
                    }

                    Toast.makeText(getContext(),
                            getString(R.string.addr_avatar_updated),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(),
                            getString(R.string.addr_avatar_save_fail),
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }


    private void resetAvatar(String imei) {
        if (imei == null || imei.isEmpty()) return;

        AvatarPickerHelper.deleteAvatar(getContext(), imei);
        addressViewModel.updateAvatarPath(imei, null);

        if (imei.equals(myImei)) {
            myAvatarPath = null;
            updateMyAvatar();
        }

        Toast.makeText(getContext(),
                getString(R.string.addr_avatar_reset_ok),
                Toast.LENGTH_SHORT).show();
    }


    private void updateMyAvatar() {
        if (binding == null) return;

        try {
            Bitmap avatarBitmap = AvatarHelper.loadOrCreate(
                    getContext(),
                    myImei,
                    myNickname,
                    myAvatarPath,
                    MY_AVATAR_SIZE_DP
            );
            binding.imgMyAvatar.setImageBitmap(avatarBitmap);
            binding.imgMyAvatar.setVisibility(View.VISIBLE);
            binding.textMyAvatarInitial.setVisibility(View.GONE);
        } catch (Exception e) {
            Log.e(TAG, "My avatar update failed: " + e.getMessage());
            binding.imgMyAvatar.setImageDrawable(null);
            binding.imgMyAvatar.setVisibility(View.GONE);
            binding.textMyAvatarInitial.setVisibility(View.VISIBLE);
            binding.textMyAvatarInitial.setText(
                    AvatarHelper.getInitial(myImei, myNickname)
            );
        }
    }


    // ═══════════════════════════════════════════════════════════════

    private void setupViewModel() {
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);
        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);

        observeUsers();
    }

    private void observeUsers() {
        addressViewModel.getAllAddress().observe(getViewLifecycleOwner(), new Observer<List<AddressEntity>>() {
            @Override
            public void onChanged(List<AddressEntity> addrs) {
                mAdapter.submitList(addrs);
                ensureServerContact(addrs);
            }
        });
    }

    // 서버 전송 전용 디폴트 연락처(codeNum="SERVER") 자동 생성 (없을 때 1회)
    private boolean mServerContactChecked = false;
    private void ensureServerContact(java.util.List<AddressEntity> addrs) {
        if (mServerContactChecked) return;
        if (addrs == null) return;
        boolean exists = false;
        for (AddressEntity a : addrs) {
            if (a.getNumbers() != null && a.getNumbers().equals("SERVER")) { exists = true; break; }
        }
        mServerContactChecked = true;
        if (!exists) {
            addressViewModel.insert(new AddressEntity(0, "SERVER", "Server", new Date(), null));
            Log.v(TAG, "기본 Server 연락처 생성");
        }
    }

    private void setupRecyclerView() {
        mAdapter = new AddressAdapter(new AddressAdapter.OnAddressClickListener() {
            @Override
            public void onAddressClick(AddressEntity addr) {
                handleAddressClick(addr);
            }

            @Override
            public void onAddressDeleteClick(AddressEntity addr) {
                handleAddressDelClick(addr);
            }

            @Override
            public void onAvatarEditClick(AddressEntity addr) {
                showAvatarMenu(addr.getNumbers());
            }
        });

        RecyclerView recyclerView = binding.listAddress;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(mAdapter);
    }


    public void handleAddressClick(AddressEntity addr) {
        Log.v(TAG, "Click Item...");
        showAddressDialog(addr);
    }

    public void handleAddressDelClick(AddressEntity addr) {
        Log.v(TAG, "handleAddressDelClick...");

        new AlertDialog.Builder(getContext())
                .setTitle(getString(R.string.addr_delete_title))
                .setMessage(getString(R.string.addr_delete_message))
                .setPositiveButton(getString(R.string.addr_btn_delete), (dialog, which) -> {
                    if (addr.getNumbers() != null) {
                        AvatarPickerHelper.deleteAvatar(getContext(), addr.getNumbers());
                    }
                    addressViewModel.delete(addr);
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }


    private void showAddressDialog(AddressEntity addr) {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = dialog.findViewById(R.id.et_name);
        EditText etCode = dialog.findViewById(R.id.et_code);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);
        ImageView btnClose = dialog.findViewById(R.id.btn_close);

        if (addr == null) {
            etName.setText("");
            etCode.setText("");
            etCode.setEnabled(true);
        } else {
            etName.setText(addr.getNumbersNic());
            etCode.setText(addr.getNumbers());
            etCode.setEnabled(false);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.addr_fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }
            if (addr == null) {
                addressViewModel.insert(new AddressEntity(0, code, name, new Date(), null));
            } else {
                addressViewModel.updateNumbersNic(code, name);
            }

            dialog.dismiss();
            Toast.makeText(getContext(), getString(R.string.addr_saved), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    /** ACK 설정 다이얼로그. 메시지 종류별 ACK on/off를 PreferenceManager에 저장.
     *  ACK ON: 송신 시 식별자(msgId) 부착 + ~A:/~D: 수신 시 V/VV 표시. OFF: 식별자 미부착, ACK 무시. */
    public static final String PREF_ACK_SHORT = "pref_ack_short";
    public static final String PREF_ACK_LARGE = "pref_ack_large";
    public static final String PREF_ACK_PHOTO = "pref_ack_photo";
    public static final String PREF_ACK_FILE  = "pref_ack_file";
    // Integrity (CRC) settings: short=optional, large=default ON, photo/file=forced ON
    public static final String PREF_INTEGRITY_SHORT = "pref_integrity_short";
    public static final String PREF_INTEGRITY_LARGE = "pref_integrity_large";
    // Resend mode: auto(자동 재시도)/manual(배너 수동), 자동 주기(분)
    public static final String PREF_RESEND_AUTO = "pref_resend_auto";
    public static final String PREF_RESEND_INTERVAL = "pref_resend_interval";

    /** 자동 주기 입력 파싱 — 빈값/오류/범위밖이면 10분 기본. */
    private static int parseIntervalOr10(String s) {
        try { int v = Integer.parseInt(s.trim()); return (v >= 1 && v <= 120) ? v : 10; }
        catch (Exception e) { return 10; }
    }

    private void showAckSettingsDialog() {
        android.content.SharedPreferences prefs =
                android.preference.PreferenceManager.getDefaultSharedPreferences(requireContext());

        android.view.View v = getLayoutInflater().inflate(R.layout.dialog_ack_settings, null);
        androidx.appcompat.widget.SwitchCompat swShort = v.findViewById(R.id.switch_ack_short);
        androidx.appcompat.widget.SwitchCompat swLarge = v.findViewById(R.id.switch_ack_large);
        androidx.appcompat.widget.SwitchCompat swPhoto = v.findViewById(R.id.switch_ack_photo);
        androidx.appcompat.widget.SwitchCompat swFile  = v.findViewById(R.id.switch_ack_file);
        androidx.appcompat.widget.SwitchCompat swIntShort = v.findViewById(R.id.switch_integrity_short);
        androidx.appcompat.widget.SwitchCompat swIntLarge = v.findViewById(R.id.switch_integrity_large);
        androidx.appcompat.widget.SwitchCompat swIntPhoto = v.findViewById(R.id.switch_integrity_photo);
        androidx.appcompat.widget.SwitchCompat swIntFile  = v.findViewById(R.id.switch_integrity_file);
        androidx.appcompat.widget.SwitchCompat swResendAuto = v.findViewById(R.id.switch_resend_auto);
        android.widget.EditText editResendInterval = v.findViewById(R.id.edit_resend_interval);

        swShort.setChecked(prefs.getBoolean(PREF_ACK_SHORT, false));
        swLarge.setChecked(prefs.getBoolean(PREF_ACK_LARGE, false));
        swPhoto.setChecked(prefs.getBoolean(PREF_ACK_PHOTO, false));
        swFile.setChecked(prefs.getBoolean(PREF_ACK_FILE, false));
        swIntShort.setChecked(prefs.getBoolean(PREF_INTEGRITY_SHORT, false));   // short: optional, default OFF
        swIntLarge.setChecked(prefs.getBoolean(PREF_INTEGRITY_LARGE, true));    // large: default ON
        swIntPhoto.setChecked(true);   // photo: forced ON (disabled)
        swIntFile.setChecked(true);    // file: forced ON (disabled)
        swResendAuto.setChecked(prefs.getBoolean(PREF_RESEND_AUTO, false));   // 기본 OFF=수동
        editResendInterval.setText(String.valueOf(prefs.getInt(PREF_RESEND_INTERVAL, 10)));

        androidx.appcompat.app.AlertDialog dlg =
                new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                        .setView(v).create();

        v.findViewById(R.id.btn_ack_close).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_ack_cancel).setOnClickListener(x -> dlg.dismiss());
        v.findViewById(R.id.btn_ack_save).setOnClickListener(x -> {
            prefs.edit()
                    .putBoolean(PREF_ACK_SHORT, swShort.isChecked())
                    .putBoolean(PREF_ACK_LARGE, swLarge.isChecked())
                    .putBoolean(PREF_ACK_PHOTO, swPhoto.isChecked())
                    .putBoolean(PREF_ACK_FILE, swFile.isChecked())
                    .putBoolean(PREF_INTEGRITY_SHORT, swIntShort.isChecked())
                    .putBoolean(PREF_INTEGRITY_LARGE, swIntLarge.isChecked())
                    .putBoolean(PREF_RESEND_AUTO, swResendAuto.isChecked())
                    .putInt(PREF_RESEND_INTERVAL, parseIntervalOr10(editResendInterval.getText().toString()))
                    .apply();
            android.util.Log.w("ACK-CFG", "ACK \uc124\uc815 \uc800\uc7a5: short=" + swShort.isChecked()
                    + " large=" + swLarge.isChecked());
            Toast.makeText(requireContext(), "ACK \uc124\uc815 \uc800\uc7a5\ub428", Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        });
        dlg.show();
    }

    private void showMeAddressDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        EditText etName = dialog.findViewById(R.id.et_name);
        EditText etCode = dialog.findViewById(R.id.et_code);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);
        ImageView btnClose = dialog.findViewById(R.id.btn_close);

        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
        String unitNum = shared.getString("unitCode");
        String nicName = shared.getString("nicName");
        String defaultName = getString(R.string.addr_my_default_name);
        if (unitNum.isEmpty()) unitNum = "";
        if (nicName.isEmpty()) nicName = defaultName;

        etName.setText(nicName);
        etCode.setText(unitNum);
        etCode.setEnabled(false);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(getContext(), getString(R.string.addr_fill_all_fields), Toast.LENGTH_SHORT).show();
                return;
            }

            shared.putAny("nicName", name);
            binding.textName.setText(name);

            myNickname = name;
            updateMyAvatar();

            dialog.dismiss();
            Toast.makeText(getContext(), getString(R.string.addr_saved), Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
