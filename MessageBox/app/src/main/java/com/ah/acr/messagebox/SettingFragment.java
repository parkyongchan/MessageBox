package com.ah.acr.messagebox;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.provider.OpenableColumns;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.ah.acr.messagebox.adapter.PublicKeyAdapter;
import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.data.KeyValue;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentSettingBinding;
import com.ah.acr.messagebox.databinding.FragmentStatusBinding;
import com.ah.acr.messagebox.packet.security.Security;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class SettingFragment extends Fragment {
    private static final String TAG = SettingFragment.class.getSimpleName();
    private FragmentSettingBinding binding;
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

        binding = FragmentSettingBinding.inflate(inflater, container, false);
        SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();

        ArrayAdapter msgTypeAdapter = ArrayAdapter.createFromResource(getContext(),
                R.array.unit_type, android.R.layout.simple_spinner_item);
        msgTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerUnitType.setAdapter(msgTypeAdapter);


        binding.getRoot().setOnClickListener(v->{
            hideKeyboard();
        });


        //setupFragmentResultListener();

        binding.buttonSearch.setOnClickListener(v-> {
            setupFragmentResultListener();
            showSearchDialog();
        });


//        if( BLE.INSTANCE.getSelectedDevice().getValue() == null) {
//            binding.buttonPw.setVisibility(View.INVISIBLE);
//            binding.textPw.setVisibility(View.INVISIBLE);
//        }
//        else {
//            binding.buttonPw.setVisibility(View.VISIBLE);
//            binding.textPw.setVisibility(View.VISIBLE);
//        }



        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null) {
//                    Log.v("DEVICE: ", String.format("%d%%", status.getBattery()));
//                    Log.v("DEVICE: ", String.valueOf(status.getInBox()));
//                    Log.v("DEVICE: ", String.valueOf(status.getOutBox()));
//                    Log.v("DEVICE: ", String.valueOf(status.getSignal()));
//
//                    DeviceInfo deviceInfo = BLE.INSTANCE.getDeviceInfo().getValue();
//                    Log.v("DEVICE: ", deviceInfo.getImei().toString());
//
//                    Log.v("DEVICE: ", status.getGpsTime());
//                    Log.v("DEVICE: ", String.format("%s, %s", status.getGpsLat(), status.getGpsLng()));
//                    Log.v("DEVICE: ", String.format("%s, %s", status.isSosMode(), status.isTrackingMode()));

                    if (status.isTrackingMode()) {
                        binding.buttonSetStart.setBackgroundColor(Color.parseColor("#9E9E9E"));
                        binding.buttonSetStop.setBackgroundColor(Color.parseColor("#1DE9B6"));
                    } else {
                        binding.buttonSetStart.setBackgroundColor(Color.parseColor("#1DE9B6"));
                        binding.buttonSetStop.setBackgroundColor(Color.parseColor("#9E9E9E"));
                    }
                }
            }
        });




        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s.startsWith("SET=")) {
                    String msg = s.substring(4);
                    String[] vals = msg.split(",");

                    if (vals[0].equals("OK") || (vals[0].equals("FAIL"))) {
                        Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
                    } else {
                        String type = vals[0];
                        String time = vals[1].replaceAll("[^0-9]", "");  // 결과: "0000"
                        String dist = vals[2].replaceAll("[^0-9]", "");  // 결과: "0000"

                        if (Integer.parseInt(time) != 0)  binding.chkTime.setChecked(true);
                        if (Integer.parseInt(dist) != 0) binding.chkDist.setChecked(true);
                        binding.textDist.setText(String.valueOf(Integer.parseInt(dist)));
                        binding.textTime.setText(String.valueOf(Integer.parseInt(time)));

                        if (vals.length > 3) {
                            String recevier = vals[3];
                            if (!recevier.equals("0")) {
                                addressViewModel.getAddressByNumbers(vals[3]).observe(getViewLifecycleOwner(), addressEntity -> {
                                    if (addressEntity != null) {
                                        binding.textReceiver.setText(addressEntity.getNumbersNic());
                                    }else binding.textReceiver.setText(recevier);
                                });
                            }
                        }

                        ArrayAdapter unitAdapter = ArrayAdapter.createFromResource(getContext(),
                                R.array.unit_type, android.R.layout.simple_spinner_item);
                        int position = unitAdapter.getPosition(type);
                        binding.spinnerUnitType.setSelection(position);
                    }
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



        binding.chkDist.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d("CheckBox", "Dist 체크됨");
                    binding.textDist.setText("10");
                    //binding.textDist.setOnKeyListener((View.OnKeyListener) TextKeyListener.getInstance());
                } else {
                    Log.d("CheckBox", "Dist 해제됨");
                    binding.textDist.setText("0");
                }
            }
        });

        binding.chkTime.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Log.d("CheckBox", "Time 체크됨");
                    binding.textTime.setText("3");
                } else {
                    Log.d("CheckBox", "Time 해제됨");
                    binding.textTime.setText("0");
                }
            }
        });



//        binding.buttonPw.setOnClickListener(v -> {
//            NavHostFragment.findNavController(SettingFragment.this)
//                    .navigate(R.id.action_main_setting_fragment_to_main_ble_login_change_fragment);
//        });


        binding.buttonSetStart.setOnClickListener(v-> BLE.INSTANCE.getWriteQueue().offer("LOCATION=2"));
        binding.buttonSetStop.setOnClickListener(v-> BLE.INSTANCE.getWriteQueue().offer("LOCATION=3"));

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
                setting.append("SET=");

                setting.append(binding.spinnerUnitType.getSelectedItem().toString().trim());

                setting.append(",");

                if (binding.chkTime.isChecked()) {
                    String time = binding.textTime.getText().toString().trim();

                    int timeValue = 0;  // 기본값
                    try {
                        timeValue = Integer.parseInt(time);
                    } catch (NumberFormatException e) {
                        timeValue = 0;  // 변환 실패 시 기본값 사용
                    }

                    if (timeValue < 3 )  timeValue = 3;

                    String timeStr = String.format("T%04d", timeValue);
                    setting.append(timeStr);
                } else {
                    setting.append("T0000");
                }

                setting.append(",");

                if (binding.chkDist.isChecked()) {
                    String dist = binding.textDist.getText().toString().trim();
                    int distValue = 0;  // 기본값
                    try {
                        distValue = Integer.parseInt(dist);
                    } catch (NumberFormatException e) {
                        distValue = 0;  // 변환 실패 시 기본값 사용
                    }

                    if (distValue < 2 )  distValue = 2;

                    //if (dist.length() == 0) dist = "10";
                    String distStr = String.format("D%04d", distValue);
                    setting.append(distStr);
                } else {
                    setting.append("D0000");
                }


                setting.append(",");
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
            Toast.makeText(getContext(), "선택됨: " + title, Toast.LENGTH_SHORT).show();
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