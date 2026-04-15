package com.ah.acr.messagebox;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentSosBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;


public class SosFragment extends Fragment {
    private static final String TAG = SosFragment.class.getSimpleName();
    private FragmentSosBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;


    //Android 6.0 (API level 23)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requireActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);
        addressViewModel = new ViewModelProvider(requireActivity()).get(AddressViewModel.class);
        mBleViewModel = new ViewModelProvider(requireActivity()).get(BleViewModel.class);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentSosBinding.inflate(inflater, container, false);
        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();


        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });

        //setupFragmentResultListener();

        binding.buttonSearch.setOnClickListener(v-> {
            setupFragmentResultListener();
            showSearchDialog();
        });

        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s.startsWith("SET=")) {
                    String msg = s.substring(4);
                    String[] vals = msg.split(",");

                    if (vals[0].equals("OK") || (vals[0].equals("FAIL"))) {
                        Toast.makeText(getContext(), "SOS Setting :" + vals[0], Toast.LENGTH_LONG).show();
                    } else {
                        String type = vals[0];

                        if (vals.length > 4) {
                            String recevier = vals[4];
                            if (!recevier.equals("0")) {
                                addressViewModel.getAddressByNumbers(vals[4]).observe(getViewLifecycleOwner(), addressEntity -> {
                                    if (addressEntity != null) {
                                        binding.textReceiver.setText(addressEntity.getNumbersNic());
                                    } else binding.textReceiver.setText(recevier);
                                });
                            }
                        }

                    }
                }
            }
        });

        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null) {
//                    Log.v("SOS DEVICE: ", String.format("%d%%", status.getBattery()));
//                    Log.v("SOS DEVICE: ", String.valueOf(status.getInBox()));
//                    Log.v("SOS DEVICE: ", String.valueOf(status.getOutBox()));
//                    Log.v("SOS DEVICE: ", String.valueOf(status.getSignal()));
//
//                    DeviceInfo deviceInfo = BLE.INSTANCE.getDeviceInfo().getValue();
//                    Log.v("SOS DEVICE: ", deviceInfo.getImei().toString());
//                    Log.v("SOS DEVICE: ", status.getGpsTime());
//                    Log.v("SOS DEVICE: ", String.format("%s, %s", status.getGpsLat(), status.getGpsLng()));
//                    Log.v("SOS DEVICE: ", String.format("%s, %s", status.isSosMode(), status.isTrackingMode()));

                    if (status.isSosMode()) {
                        binding.buttonSetStart.setBackgroundColor(Color.parseColor("#9E9E9E"));
                        binding.buttonSetStop.setBackgroundColor(Color.parseColor("#1DE9B6"));
                    } else {
                        binding.buttonSetStart.setBackgroundColor(Color.parseColor("#1DE9B6"));
                        binding.buttonSetStop.setBackgroundColor(Color.parseColor("#9E9E9E"));
                    }
                }
            }
        });



        return binding.getRoot();
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
//            if (device != null) {
//                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
//            }
//        });

        binding.buttonSetStart.setOnClickListener(v-> BLE.INSTANCE.getWriteQueue().offer("LOCATION=4"));

        binding.buttonSetStop.setOnClickListener(v-> BLE.INSTANCE.getWriteQueue().offer("LOCATION=5"));

        binding.buttonSetSave.setOnClickListener(v -> {

            String nicName = binding.textReceiver.getText().toString().trim();
            addressViewModel.getAddressByNicName(nicName).observe(getViewLifecycleOwner(), addressEntity -> {
                String codeNum = nicName;
                if (addressEntity != null) {
                    codeNum = addressEntity.getNumbers();
                }

                if (nicName.isEmpty()) codeNum = "0";

                if (!codeNum.matches("\\d+")) {
                    Toast.makeText(getContext(), "The recipient's number must contain only numbers.", Toast.LENGTH_LONG).show();
                    codeNum = "0";
                }


                StringBuilder setting = new StringBuilder();
                setting.append("SET=SOS,T0000,D0000,");
                setting.append(codeNum);

                Log.v(TAG, setting.toString());

                BLE.INSTANCE.getWriteQueue().offer(setting.toString());
            });


        });

    }


    private void showSearchDialog() {
        SearchDialogFragment searchDialog = new SearchDialogFragment();
        // Fragment에서는 getParentFragmentManager() 또는 getChildFragmentManager() 사용
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }

    private void setupFragmentResultListener() {
        // 검색 결과 받기 - Fragment에서는 getParentFragmentManager() 사용
        getParentFragmentManager().setFragmentResultListener("search_result", this,
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                        int selectedId = bundle.getInt("selected_id");
                        String selectedTitle = bundle.getString("selected_nic");
                        String selectedDescription = bundle.getString("selected_code");

                        // 선택된 결과 처리
                        handleSearchResult(selectedId, selectedTitle, selectedDescription);
                    }
                });
    }


    private void handleSearchResult(int id, String title, String code) {
        // 검색 결과 처리 로직
        if (getContext() != null) {
            //Toast.makeText(getContext(), "선택됨: " + title, Toast.LENGTH_SHORT).show();
            binding.textReceiver.setText(title);
            // save........
        }
    }


    private void navigateBack() {
        try {
            NavController navController = Navigation.findNavController(requireView());
            navController.navigateUp();
            // 또는
            // navController.popBackStack();
        } catch (Exception e) {
            // Navigation이 설정되지 않은 경우 기본 방법 사용
            closeFragment();
        }
    }

    private void closeFragment() {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void hideKeyboard(){
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(requireActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS );
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //BLE.INSTANCE.getWriteQueue().offer("BROAD=0");
        binding = null;
    }

}