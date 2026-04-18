package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

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
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

import java.util.Date;
import java.util.List;


public class AddrssBookFragment extends Fragment {
    private static final String TAG = AddrssBookFragment.class.getSimpleName();

    private FragmentAddressBookBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressAdapter mAdapter;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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

        // ⭐ My Profile 아바타 박스 클릭 (이미지 편집 - 향후 구현)
        binding.frameMyAvatar.setOnClickListener(view -> {
            // TODO: My Profile 이미지 편집/업로드 기능 구현 예정
            Toast.makeText(getContext(),
                    "Image upload coming soon",
                    Toast.LENGTH_SHORT).show();
        });

        // ⭐ 🔑 Password 변경 버튼 (BLE 미연결 시 경고)
        binding.buttonPassword.setOnClickListener(view -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                // BLE 미연결
                Toast.makeText(getContext(),
                        "Please connect to a device first.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            // BLE 연결됨 → Password 변경 화면으로 이동
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

        // BLE Device Info observer - 자물쇠 버튼 시각적 상태
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, deviceInfo.toString());

                String unitNum = deviceInfo.getImei();
                binding.textNumber.setText(unitNum);

                addressViewModel.getAddressByNumbers(unitNum).observe(getViewLifecycleOwner(), new Observer<AddressEntity>() {
                    @Override
                    public void onChanged(AddressEntity addressEntity) {
                        if (addressEntity == null) {
                            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                            String nicName = shared.getString("nicName");
                            if (nicName.isEmpty()) shared.putAny("nicName", "My Name");
                            binding.textName.setText(nicName.isEmpty() ? "My Name" : nicName);
                        } else {
                            binding.textName.setText(addressEntity.getNumbersNic());
                        }
                    }
                });

                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                shared.putAny("unitCode", unitNum);
            }
        });

        // ⭐ BLE 연결 상태에 따라 자물쇠 버튼 시각 효과
        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                binding.buttonPassword.setAlpha(1.0f);        // 선명하게
            } else {
                binding.buttonPassword.setAlpha(0.4f);        // 흐리게 (연결 필요)
            }
        });

        return binding.getRoot();
    }


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
