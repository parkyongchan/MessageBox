package com.ah.acr.messagebox;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.databinding.FragmentStatusBinding;
import com.ah.acr.messagebox.util.Coordinates;

import java.nio.DoubleBuffer;
import java.text.SimpleDateFormat;

public class StatusFragment extends Fragment {
    //private static final String TAG = StatusFragment.class.getSimpleName();
    private FragmentStatusBinding binding;

    private BleViewModel mBleViewModel;
    private SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private Double mLat;
    private Double mLng;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentStatusBinding.inflate(inflater, container, false);

        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null) {
                    binding.textStatusBattery.setText(String.format("%d%%", status.getBattery()));
                    binding.textStatusInbox.setText(String.valueOf(status.getInBox()));
                    binding.textStatusOutbox.setText(String.valueOf(status.getOutBox()));
                    binding.textStatusSignal.setText(String.valueOf(status.getSignal()));

                    DeviceInfo deviceInfo = BLE.INSTANCE.getDeviceInfo().getValue();
                    binding.textStatusImei.setText(deviceInfo.getImei().toString());

                    binding.textStatusGpsTime.setText(status.getGpsTime());
                    binding.textStatusGpsDd.setText(String.format("%s, %s", status.getGpsLat(), status.getGpsLng()));
                    mLat = Double.valueOf(status.getGpsLat());
                    mLng = Double.valueOf(status.getGpsLng());

                    binding.textStatusGpsMgrs.setText(Coordinates.mgrsFromLatLon(mLat, mLng));

                    if (status.isSosMode()) binding.textRunSos.setBackgroundColor(Color.GREEN);
                    else binding.textRunSos.setBackgroundColor(Color.WHITE);
                    if (status.isTrackingMode()) binding.textRunTrack.setBackgroundColor(Color.GREEN);
                    else binding.textRunTrack.setBackgroundColor(Color.WHITE);

                } else {
                    binding.textStatusBattery.setText("");
                    binding.textStatusInbox.setText("");
                    binding.textStatusOutbox.setText("");
                    binding.textStatusSignal.setText("");
                    //binding.textStatusImei.setText("");

                    binding.textStatusGpsTime.setText("");
                    binding.textStatusGpsDd.setText("");
                    binding.textStatusGpsMgrs.setText("");

                    binding.textRunSos.setBackgroundColor(Color.WHITE);
                    binding.textRunTrack.setBackgroundColor(Color.WHITE);
                }
            }
        });

        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);


        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            }
        });


//        binding.buttonRefresh.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
//            }
//        });

        binding.btnLocationSend.setOnClickListener(v -> {
            BLE.INSTANCE.getWriteQueue().offer("LOCATION=1");
        });


        binding.btnLocationView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (mLat == null) {
                    Toast.makeText(getContext(), "The location coordinates are invalid.", Toast.LENGTH_SHORT).show();
                    return;
                }

                Bundle bundle = new Bundle();
                //bundle.putInt("id", 1);
                bundle.putString("title", "My Point");
                bundle.putDouble("lat", mLat);
                bundle.putDouble("lng", mLng);
                NavHostFragment.findNavController(StatusFragment.this)
                        .navigate(R.id.action_main_status_fragment_to_main_map_fragment, bundle);
            }
        });

        binding.btnLocationCopy.setOnClickListener(view1 -> {
            if (mLat == null) {
                Toast.makeText(getContext(), "The location coordinates are invalid.", Toast.LENGTH_SHORT).show();
                return;
            }

            String loc = String.format("%f,%f", mLat, mLng);
            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("copy", loc);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(getContext(), "Copied to clipboard.", Toast.LENGTH_SHORT).show();
        });


    }

    @Override
    public void onResume() {
        super.onResume();
       //BLE.INSTANCE.getWriteQueue().offer("BROAD=0");
        binding.textStatusBattery.setText("");
        binding.textStatusInbox.setText("");
        binding.textStatusOutbox.setText("");
        binding.textStatusSignal.setText("");
        //binding.textStatusImei.setText("");

        binding.textStatusGpsTime.setText("");
        binding.textStatusGpsDd.setText("");
        binding.textStatusGpsMgrs.setText("");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        BLE.INSTANCE.getWriteQueue().offer("BROAD=0");
        binding = null;

    }
}