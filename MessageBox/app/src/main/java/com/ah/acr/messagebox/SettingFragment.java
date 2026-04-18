package com.ah.acr.messagebox;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.database.AddressViewModel;
import com.ah.acr.messagebox.databinding.FragmentSettingBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;


public class SettingFragment extends Fragment {
    private static final String TAG = SettingFragment.class.getSimpleName();
    private FragmentSettingBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    // ⭐ 장비 전송용 코드 배열 (고정)
    private static final String[] UNIT_TYPE_CODES = {"CAR", "UAV", "UAT"};

    // 다크 테마 색상
    private static final int COLOR_CYAN    = 0xFF00E5D1;
    private static final int COLOR_GRAY_BG = 0xFF2A3A5A;


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

        // ⭐ Unit Type 스피너 - 화면 표시용 배열 사용 (긴 설명)
        ArrayAdapter<CharSequence> displayAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.unit_type_display,
                R.layout.spinner_item);
        displayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.spinnerUnitType.setAdapter(displayAdapter);

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // 검색 버튼
        binding.buttonSearch.setOnClickListener(v -> {
            setupFragmentResultListener();
            showSearchDialog();
        });

        // 장비 상태 관찰 (Tracking 모드 표시)
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null && status != null) {
                    updateStartStopButtonState(status.isTrackingMode());
                }
            }
        });

        // 장비 설정 수신
        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s == null || !s.startsWith("SET=")) return;

                String msg = s.substring(4);
                String[] vals = msg.split(",");

                if (vals[0].equals("OK") || vals[0].equals("FAIL")) {
                    Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
                    return;
                }

                String type = vals[0];   // CAR, UAV, UAT
                String time = vals[1].replaceAll("[^0-9]", "");
                String dist = vals[2].replaceAll("[^0-9]", "");

                try {
                    if (Integer.parseInt(time) != 0) binding.chkTime.setChecked(true);
                    if (Integer.parseInt(dist) != 0) binding.chkDist.setChecked(true);
                    binding.textDist.setText(String.valueOf(Integer.parseInt(dist)));
                    binding.textTime.setText(String.valueOf(Integer.parseInt(time)));
                } catch (NumberFormatException e) {
                    Log.e(TAG, "parse error: " + e);
                }

                if (vals.length > 3) {
                    String receiver = vals[3];
                    if (!receiver.equals("0")) {
                        addressViewModel.getAddressByNumbers(receiver).observe(getViewLifecycleOwner(), addressEntity -> {
                            if (addressEntity != null) {
                                binding.textReceiver.setText(addressEntity.getNumbersNic());
                            } else {
                                binding.textReceiver.setText(receiver);
                            }
                        });
                    }
                }

                // ⭐ 코드(CAR/UAV/UAT)를 인덱스로 변환해서 스피너 선택
                int position = findCodeIndex(type);
                if (position >= 0) {
                    binding.spinnerUnitType.setSelection(position);
                }
            }
        });

        return binding.getRoot();
    }

    /** 코드 문자열을 배열 인덱스로 변환 */
    private int findCodeIndex(String code) {
        for (int i = 0; i < UNIT_TYPE_CODES.length; i++) {
            if (UNIT_TYPE_CODES[i].equals(code)) {
                return i;
            }
        }
        return 0;  // 기본: CAR
    }

    /** Start/Stop 버튼 시각 상태 업데이트 */
    private void updateStartStopButtonState(boolean isTracking) {
        if (binding == null) return;

        if (isTracking) {
            binding.buttonSetStart.setBackgroundColor(COLOR_GRAY_BG);
            binding.buttonSetStop.setBackgroundColor(0x30FF5252);
        } else {
            binding.buttonSetStart.setBackgroundColor(COLOR_CYAN);
            binding.buttonSetStop.setBackgroundColor(0x15FF5252);
        }
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            }
        });

        // Distance 체크박스
        binding.chkDist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.textDist.setText("10");
            } else {
                binding.textDist.setText("0");
            }
        });

        // Time 체크박스
        binding.chkTime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                binding.textTime.setText("3");
            } else {
                binding.textTime.setText("0");
            }
        });

        // Start / Stop 버튼
        binding.buttonSetStart.setOnClickListener(v -> BLE.INSTANCE.getWriteQueue().offer("LOCATION=2"));
        binding.buttonSetStop.setOnClickListener(v -> BLE.INSTANCE.getWriteQueue().offer("LOCATION=3"));

        // Save 버튼
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

                // ⭐ 선택된 인덱스 → 코드 값(CAR/UAV/UAT)으로 변환
                int selectedIdx = binding.spinnerUnitType.getSelectedItemPosition();
                String unitCode = (selectedIdx >= 0 && selectedIdx < UNIT_TYPE_CODES.length)
                        ? UNIT_TYPE_CODES[selectedIdx]
                        : UNIT_TYPE_CODES[0];
                setting.append(unitCode);

                setting.append(",");

                // Time
                if (binding.chkTime.isChecked()) {
                    String time = binding.textTime.getText().toString().trim();
                    int timeValue = 0;
                    try {
                        timeValue = Integer.parseInt(time);
                    } catch (NumberFormatException e) {
                        timeValue = 0;
                    }
                    if (timeValue < 3) timeValue = 3;
                    setting.append(String.format("T%04d", timeValue));
                } else {
                    setting.append("T0000");
                }

                setting.append(",");

                // Distance
                if (binding.chkDist.isChecked()) {
                    String dist = binding.textDist.getText().toString().trim();
                    int distValue = 0;
                    try {
                        distValue = Integer.parseInt(dist);
                    } catch (NumberFormatException e) {
                        distValue = 0;
                    }
                    if (distValue < 2) distValue = 2;
                    setting.append(String.format("D%04d", distValue));
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
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }

    private void setupFragmentResultListener() {
        getParentFragmentManager().setFragmentResultListener("search_result", this,
                new FragmentResultListener() {
                    @Override
                    public void onFragmentResult(@NonNull String requestKey, @NonNull Bundle bundle) {
                        int selectedId = bundle.getInt("selected_id");
                        String selectedTitle = bundle.getString("selected_nic");
                        String selectedDescription = bundle.getString("selected_code");
                        handleSearchResult(selectedId, selectedTitle, selectedDescription);
                    }
                });
    }


    private void handleSearchResult(int id, String title, String code) {
        if (getContext() != null) {
            Toast.makeText(getContext(), "Selected: " + title, Toast.LENGTH_SHORT).show();
            binding.textReceiver.setText(title);
        }
    }


    private void navigateBack() {
        try {
            NavController navController = Navigation.findNavController(requireView());
            navController.navigateUp();
        } catch (Exception e) {
            closeFragment();
        }
    }

    private void closeFragment() {
        if (getParentFragmentManager() != null) {
            getParentFragmentManager().popBackStack();
        }
    }

    private void hideKeyboard() {
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(requireActivity().getCurrentFocus().getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

}
