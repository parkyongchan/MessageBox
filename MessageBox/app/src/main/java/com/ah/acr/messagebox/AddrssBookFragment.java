package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
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
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ah.acr.messagebox.adapter.AddressAdapter;
import com.ah.acr.messagebox.adapter.LocationAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.databinding.FragmentAddressBookBinding;
import com.ah.acr.messagebox.databinding.FragmentLocationBinding;
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


        binding.buttonAddrNew.setOnClickListener(view -> {
            showAddressDialog(null);
        });

        binding.buttonEdit.setOnClickListener(view -> showMeAddressDialog());

        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, deviceInfo.toString());

                //String unitNum = deviceInfo.getBudaeNum();
                //binding.textNumber.setText(unitNum);

                String unitNum = deviceInfo.getImei();
                binding.textNumber.setText(unitNum);


                addressViewModel.getAddressByNumbers(unitNum).observe(getViewLifecycleOwner(), new Observer<AddressEntity>() {
                    @Override
                    public void onChanged(AddressEntity addressEntity) {
                        //Log.v(TAG, addressEntity.toString());
                        if (addressEntity == null) {
                            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                            String nicName = shared.getString("nicName");
                            if (nicName.isEmpty()) shared.putAny("nicName", "My Name");
                            binding.textName.setText(nicName.isEmpty()?"My Name": nicName);
                        } else {
                            binding.textName.setText(addressEntity.getNumbersNic());
                        }
                    }
                });

                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                shared.putAny("unitCode", unitNum);
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
        addressViewModel.getAllAddress().observe(getViewLifecycleOwner(), new Observer<List<AddressEntity>>(){
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


        RecyclerView recyclerView = binding.listAddress ;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())) ;
        DividerItemDecoration dividerDecoration =
                new DividerItemDecoration(recyclerView.getContext(), new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);

        recyclerView.setAdapter(mAdapter);
    }


    public void handleAddressClick(AddressEntity addr) {
        Log.v(TAG, "Click Item...");
        showAddressDialog(addr);
    }

    public void handleAddressDelClick(AddressEntity addr) {
        Log.v(TAG, "handleLocationDelClick Click Item...");

        new AlertDialog.Builder(getContext())
                .setTitle("Location Delete")
                .setMessage(getString(R.string.address_del_alert))
                .setPositiveButton("Delete", (dialog, which) -> {
                    addressViewModel.delete(addr);
                })
                .setNegativeButton("cancel", null)
                .show();
    }




    private void showAddressDialog(AddressEntity addr) {
        // 커스텀 다이얼로그 생성
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // 다이얼로그 뷰 요소들
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



        // 취소 버튼
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // 닫기 버튼
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // 저장 버튼
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                String code = etCode.getText().toString().trim();

                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (addr == null) {
                    new AddressEntity(0, code, name, new Date(), null);
                    addressViewModel.insert( new AddressEntity(0, code, name, new Date(), null));
                }
                else addressViewModel.updateNumbersNic(code, name);

                dialog.dismiss();

                Toast.makeText(getContext(), "It was saved.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }


    private void showMeAddressDialog() {
        // 커스텀 다이얼로그 생성
        Dialog dialog = new Dialog(getContext());
        dialog.setContentView(R.layout.dialog_address);
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        // 다이얼로그 뷰 요소들
        EditText etName = dialog.findViewById(R.id.et_name);
        EditText etCode = dialog.findViewById(R.id.et_code);
        Button btnCancel = dialog.findViewById(R.id.btn_cancel);
        Button btnSave = dialog.findViewById(R.id.btn_save);
        ImageView btnClose = dialog.findViewById(R.id.btn_close);


        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
        String unitNum = shared.getString("unitCode");
        String nicName = shared.getString("nicName");
        if (unitNum.isEmpty()) unitNum="";
        if (nicName.isEmpty()) nicName="MyName";

        etName.setText(nicName);
        etCode.setText(unitNum);
        etCode.setEnabled(false);

        // 취소 버튼
        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // 닫기 버튼
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        // 저장 버튼
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String name = etName.getText().toString().trim();
                String code = etCode.getText().toString().trim();

                if (name.isEmpty() || code.isEmpty()) {
                    Toast.makeText(getContext(), "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                    return;
                }
               // save
                shared.putAny("nicName", name);
//                shared.putAny("unitCode", code);
//
                binding.textName.setText(name);
//                binding.textNumber.setText(code);

                dialog.dismiss();

                Toast.makeText(getContext(), "It was saved.", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }




    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


}