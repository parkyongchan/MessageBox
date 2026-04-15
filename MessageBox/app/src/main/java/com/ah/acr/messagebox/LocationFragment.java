package com.ah.acr.messagebox;

import static androidx.core.content.ContextCompat.getSystemService;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.ah.acr.messagebox.adapter.LocationAdapter;
import com.ah.acr.messagebox.adapter.OutBoxAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.database.AddressEntity;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.database.LocationEntity;

import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.LocationWithAddress;
import com.ah.acr.messagebox.databinding.FragmentLocationBinding;

import java.util.Date;
import java.util.List;


public class LocationFragment extends Fragment {
    private static final String TAG = LocationFragment.class.getSimpleName();

    private FragmentLocationBinding binding;
    private LocationAdapter mAdapter;
    private LocationViewModel locationViewModel;

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
        binding = FragmentLocationBinding.inflate(inflater, container, false);

        setupRecyclerView();
        setupViewModel();
        setupClickListeners();

        return binding.getRoot();
    }


    private void setupViewModel() {
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);
        addressViewModel = new ViewModelProvider(this).get(AddressViewModel.class);

        observeUsers();
    }

    private void observeUsers() {
        locationViewModel.getAllLocationAddress().observe(getViewLifecycleOwner(), new Observer<List<LocationWithAddress>>(){
            @Override
            public void onChanged(List<LocationWithAddress> locations) {
                mAdapter.submitList(locations);
            }
        });
    }

    private void setupRecyclerView() {

        mAdapter = new LocationAdapter(new LocationAdapter.OnLocationClickListener() {
            @Override
            public void onLocationClick(LocationEntity location) {
                handleLocationClick(location);
            }

            @Override
            public void onLocationDeleteClick(LocationEntity location) {
                handleLocationDelClick(location);
            }

            @Override
            public void onLocationCopyClick(LocationEntity location) {
                handleLocationCopyClick(location);
            }

            @Override
            public void onLocationMapClick(LocationEntity location) {
                handleLocationMapClick(location);
            }

            @Override
            public void onAddressClick(LocationWithAddress location) {
                handleAddressClick(location);
            }
        });


        RecyclerView recyclerView = binding.listLocation ;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())) ;
        DividerItemDecoration dividerDecoration =
                new DividerItemDecoration(recyclerView.getContext(), new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);

        recyclerView.setAdapter(mAdapter);
    }

    private void setupClickListeners() {

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });

        binding.buttonReflesh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BLE.INSTANCE.getWriteQueue().offer("RECEIVED=?");
            }
        });


    }
    public void handleLocationClick(LocationEntity location) {
        Log.v(TAG, "Click Item...");
    }

    public void handleLocationDelClick(LocationEntity location) {
        Log.v(TAG, "handleLocationDelClick Click Item...");

        new AlertDialog.Builder(getContext())
                .setTitle("Location Delete")
                .setMessage(getString(R.string.location_del_alert))
                .setPositiveButton("Delete", (dialog, which) -> {
                    locationViewModel.delete(location);
                })
                .setNegativeButton("cancel", null)
                .show();
    }

    public void handleLocationCopyClick(LocationEntity location) {
        Log.v(TAG, "handleLocationCopyClick Click Item...");

        String loc = String.format("%f,%f", location.getLatitude(), location.getLongitude());
        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("copy", loc);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(getContext(), "Copied to clipboard.", Toast.LENGTH_SHORT).show();


//        // 클립보드에 데이터가 있는지 확인
//        if (clipboard.hasPrimaryClip()) {
//            ClipData clipData = clipboard.getPrimaryClip();
//
//            if (clipData != null && clipData.getItemCount() > 0) {
//                ClipData.Item item = clipData.getItemAt(0);
//                String pastedText = item.getText().toString();
//
//                Log.v(TAG, pastedText);
//
//                Toast.makeText(getContext(), "붙여넣기 완료", Toast.LENGTH_SHORT).show();
//            } else {
//                Toast.makeText(getContext(), "클립보드가 비어있습니다", Toast.LENGTH_SHORT).show();
//            }
//        } else {
//            Toast.makeText(getContext(), "클립보드에 복사된 내용이 없습니다", Toast.LENGTH_SHORT).show();
//        }
    }

    public void handleLocationMapClick(LocationEntity location) {
        Log.v(TAG, "handleLocationMapClick Click Item...");

        Bundle bundle = new Bundle();
       // bundle.putInt("id", location.getId());
        bundle.putString("title", location.getNicName()!=null? location.getNicName(): location.getCodeNum());
        bundle.putDouble("lat", location.getLatitude());
        bundle.putDouble("lng", location.getLongitude());

        NavHostFragment.findNavController(this)
                .navigate(R.id.action_main_location_fragment_to_main_map_fragment, bundle);

    }


    public void  handleAddressClick(LocationWithAddress location) {
        showAddressDialog(location);
    }
    private void showAddressDialog(LocationWithAddress location) {
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

        if (location.getAddress() == null) etName.setText(location.getLocation().getCodeNum());
        else etName.setText(location.getAddress().getNumbersNic());
        etCode.setText(location.getLocation().getCodeNum());
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

                if (location.getAddress() == null) {
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




    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }


}