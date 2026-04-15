package com.ah.acr.messagebox;

import android.app.ProgressDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.ah.acr.messagebox.adapter.DeviceAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.database.InboxMsg;
import com.ah.acr.messagebox.databinding.FragmentBleItemBinding;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.databinding.FragmentMainBinding;
import com.clj.fastble.BleManager;
import com.clj.fastble.data.BleDevice;

import java.util.List;

public class BleItemFragment extends Fragment {
    private static final String TAG = BleItemFragment.class.getSimpleName();
    private FragmentBleItemBinding binding;
    private DeviceAdapter mDeviceAdapter;
    private ProgressDialog progressDialog;
    private BleViewModel mBleViewModel;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentBleItemBinding.inflate(inflater, container, false);

        progressDialog = new ProgressDialog(getContext());
        mDeviceAdapter = new DeviceAdapter(getContext());

        mBleViewModel.checkScanStatus();
        BLE.INSTANCE.getBleLiveDeviceList().observe(getViewLifecycleOwner(), new Observer<List<BleDevice>>() {
            @Override   /* DEVICE ADD */
            public void onChanged(List<BleDevice> bleDevices) {
                mDeviceAdapter.setBleDeviceList(bleDevices);
            }
        });
        BLE.INSTANCE.getScanStatus().observe(getViewLifecycleOwner(), new Observer<Boolean>() {
            @Override
            public void onChanged(Boolean aBoolean) {
                if (!aBoolean) binding.refreshLayout.setRefreshing(false);
                else binding.refreshLayout.setRefreshing(true);
            }
        });
        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), new Observer<BleDevice>() {
            @Override
            public void onChanged(BleDevice bleDevice) {
                if (bleDevice != null)
                    progressDialog.dismiss();
            }
        });
        BLE.INSTANCE.getConnectionStatus().observe(getViewLifecycleOwner(), new Observer<String>(){
            @Override
            public void onChanged(String s) {
                Log.v(TAG, s);
                if (s.equals(BLE.CONNECT_STATUS_TRYING)) {

                } else if (s.equals(BLE.CONNECT_STATUS_FAILED)) {
                    progressDialog.dismiss();
                } else if (s.equals(BLE.CONNECT_STATUS_CONNECTED)) {

                } else if (s.equals(BLE.CONNECT_STATUS_DISCONNECTED)) {

                } else if (s.equals(BLE.CONNECT_STATUS_LOST)) {

                }
            }
        });

        BLE.INSTANCE.getBleLoginStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.v(TAG, s);
                if (s.equals(BLE.BLE_LOGIN_TRY)) {
                    NavHostFragment.findNavController(BleItemFragment.this)
                            .navigate(R.id.action_main_ble_set_fragment_to_main_ble_login_fragment);
                } else if (s.equals(BLE.BLE_LOGIN_OK)) {

                } else if (s.equals(BLE.BLE_LOGIN_FAIL)) {

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_TRY)) {
                    NavHostFragment.findNavController(BleItemFragment.this)
                            .navigate(R.id.action_main_ble_set_fragment_to_main_ble_login_change_fragment);
                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_OK)) {

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_FAIL)) {

                }
            }
        });

        mDeviceAdapter.setOnDeviceClickListener(new DeviceAdapter.OnDeviceClickListener() {
            @Override
            public void onConnect(BleDevice bleDevice) {
                progressDialog.show();
                binding.refreshLayout.setRefreshing(false);
                BLE.INSTANCE.stopScan();
                BLE.INSTANCE.connect(bleDevice);
            }

            @Override
            public void onDisConnect(final BleDevice bleDevice) {
                BleManager.getInstance().disconnect(bleDevice);
                //BLE.INSTANCE.disconnect(bleDevice);  //not used
            }

            @Override
            public void onDetail(BleDevice bleDevice) {
            }
        });

        RecyclerView recyclerView = binding.listDevice ;
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext())) ;
        DividerItemDecoration dividerDecoration =
                new DividerItemDecoration(recyclerView.getContext(), new LinearLayoutManager(getContext()).getOrientation());
        recyclerView.addItemDecoration(dividerDecoration);

        recyclerView.setAdapter(mDeviceAdapter);

        mDeviceAdapter.setOnItemClickListener(new DeviceAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(View v, int position) {
            }
        });


        binding.refreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                if(BLE.INSTANCE.getScanStatus().getValue() == true) return;
                BLE.INSTANCE.startScan();
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BLE.INSTANCE.stopScan();
    }

}