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

    // ⭐ Avatar edit state: which IMEI is currently being edited
    private String targetAvatarImei = null;

    // ⭐ Gallery launcher
    private ActivityResultLauncher<String> pickImageLauncher;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ⭐ Register gallery launcher (must be in onCreate, not onCreateView)
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

        // New 버튼
        binding.buttonAddrNew.setOnClickListener(view -> {
            showAddressDialog(null);
        });

        // Edit 버튼 (My Profile 편집)
        binding.buttonEdit.setOnClickListener(view -> showMeAddressDialog());

        // ⭐ My Profile 아바타 박스 클릭 → 아바타 편집 메뉴
        binding.frameMyAvatar.setOnClickListener(view -> {
            if (myImei == null || myImei.isEmpty()) {
                Toast.makeText(getContext(),
                        "Please connect to a device first",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            showAvatarMenu(myImei);
        });

        // 🔑 Password 변경 버튼
        binding.buttonPassword.setOnClickListener(view -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Toast.makeText(getContext(),
                        "Please connect to a device first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                NavHostFragment.findNavController(AddrssBookFragment.this)
                        .navigate(R.id.action_main_setting_fragment_to_main_ble_login_change_fragment);
            } catch (Exception e) {
                Log.e(TAG, "Navigation error: " + e);
                Toast.makeText(getContext(),
                        "Unable to open password change screen.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        // BLE Device Info observer
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
                            if (nicName.isEmpty()) shared.putAny("nicName", "My Name");
                            String displayName = nicName.isEmpty() ? "My Name" : nicName;
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

        // BLE 연결 상태에 따라 자물쇠 버튼 시각 효과
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
    //   ⭐ AVATAR EDIT (NEW)
    // ═══════════════════════════════════════════════════════════════

    /**
     * ⭐ Show avatar edit menu (gallery / reset / cancel)
     */
    private void showAvatarMenu(String imei) {
        if (imei == null || imei.isEmpty()) {
            Toast.makeText(getContext(),
                    "IMEI not available",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        targetAvatarImei = imei;

        String[] options = {
                "📷 Choose from Gallery",
                "🔤 Use Initial Avatar"
        };

        new AlertDialog.Builder(getContext())
                .setTitle("Avatar")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        // Gallery
                        openGallery();
                    } else {
                        // Reset to initial
                        resetAvatar(imei);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    /**
     * ⭐ Open gallery to pick image
     */
    private void openGallery() {
        try {
            pickImageLauncher.launch("image/*");
        } catch (Exception e) {
            Log.e(TAG, "Gallery launch failed: " + e.getMessage());
            Toast.makeText(getContext(),
                    "Cannot open gallery",
                    Toast.LENGTH_SHORT).show();
        }
    }


    /**
     * ⭐ Handle image picked from gallery
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
        targetAvatarImei = null;  // Clear state

        // Save on background thread
        new Thread(() -> {
            String savedPath = AvatarPickerHelper.saveAvatarFromUri(
                    getContext(), uri, imei);

            // Update UI on main thread
            if (getActivity() == null) return;
            getActivity().runOnUiThread(() -> {
                if (savedPath != null) {
                    // Update DB
                    addressViewModel.updateAvatarPath(imei, savedPath);

                    // If this is My Profile, update local state and UI
                    if (imei.equals(myImei)) {
                        myAvatarPath = savedPath;
                        updateMyAvatar();
                    }

                    Toast.makeText(getContext(),
                            "✅ Avatar updated",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(),
                            "❌ Failed to save avatar",
                            Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }


    /**
     * ⭐ Reset avatar to initial (delete custom image)
     */
    private void resetAvatar(String imei) {
        if (imei == null || imei.isEmpty()) return;

        // Delete file
        AvatarPickerHelper.deleteAvatar(getContext(), imei);

        // Update DB (set avatarPath = null)
        addressViewModel.updateAvatarPath(imei, null);

        // If this is My Profile, update local state and UI
        if (imei.equals(myImei)) {
            myAvatarPath = null;
            updateMyAvatar();
        }

        Toast.makeText(getContext(),
                "✅ Avatar reset to initial",
                Toast.LENGTH_SHORT).show();
    }


    /**
     * My Profile 아바타 업데이트 (AvatarHelper 사용)
     */
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
    //   EXISTING METHODS
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
            }
        });
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
                // ⭐ 주소록 아이템 아바타 편집
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
                .setTitle("Delete contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Also delete avatar file if exists
                    if (addr.getNumbers() != null) {
                        AvatarPickerHelper.deleteAvatar(getContext(), addr.getNumbers());
                    }
                    addressViewModel.delete(addr);
                })
                .setNegativeButton("Cancel", null)
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
                Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (addr == null) {
                addressViewModel.insert(new AddressEntity(0, code, name, new Date(), null));
            } else {
                addressViewModel.updateNumbersNic(code, name);
            }

            dialog.dismiss();
            Toast.makeText(getContext(), "Saved.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
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
        if (unitNum.isEmpty()) unitNum = "";
        if (nicName.isEmpty()) nicName = "My Name";

        etName.setText(nicName);
        etCode.setText(unitNum);
        etCode.setEnabled(false);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnClose.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String code = etCode.getText().toString().trim();

            if (name.isEmpty() || code.isEmpty()) {
                Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            shared.putAny("nicName", name);
            binding.textName.setText(name);

            myNickname = name;
            updateMyAvatar();

            dialog.dismiss();
            Toast.makeText(getContext(), "Saved.", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
