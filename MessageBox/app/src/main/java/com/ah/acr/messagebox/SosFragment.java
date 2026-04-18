package com.ah.acr.messagebox;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentResultListener;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
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

    // 다크 테마 SOS 색상
    private static final int COLOR_SOS_ACTIVE    = 0xFFFF5252;  // 빨강 (긴급)
    private static final int COLOR_SOS_INACTIVE  = 0xFF3A1A1A;  // 진한 빨강 (비활성)
    private static final int COLOR_STOP_ACTIVE   = 0xFF00E5D1;  // 청록 (정지 활성)
    private static final int COLOR_STOP_INACTIVE = 0xFF152A4A;  // 진한 카드 배경


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

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // 검색 버튼
        binding.buttonSearch.setOnClickListener(v -> {
            setupFragmentResultListener();
            showSearchDialog();
        });

        // 장비 설정 수신
        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                if (s == null || !s.startsWith("SET=")) return;

                String msg = s.substring(4);
                String[] vals = msg.split(",");

                if (vals[0].equals("OK") || vals[0].equals("FAIL")) {
                    Toast.makeText(getContext(), "SOS Setting: " + vals[0], Toast.LENGTH_LONG).show();
                    return;
                }

                if (vals.length > 4) {
                    String receiver = vals[4];
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
            }
        });

        // 장비 상태 관찰 (SOS 모드 표시)
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null && status != null) {
                    updateStartStopButtonState(status.isSosMode());
                }
            }
        });

        return binding.getRoot();
    }

    /** Start/Stop 버튼 시각 상태 업데이트 */
    private void updateStartStopButtonState(boolean isSosMode) {
        if (binding == null) return;

        if (isSosMode) {
            // SOS 활성 중: Start(어두운 빨강 - 이미 작동중), Stop(청록 - 눌러서 정지 가능)
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_INACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_ACTIVE);
        } else {
            // 정지 상태: Start(밝은 빨강 - 긴급 전송 가능), Stop(어두운 카드)
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_ACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_INACTIVE);
        }
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // SOS Start (LOCATION=4)
        binding.buttonSetStart.setOnClickListener(v -> {
            // 긴급 전송 확인 다이얼로그
            new android.app.AlertDialog.Builder(getContext())
                    .setTitle("Start SOS")
                    .setMessage("SOS signal will be sent every 3 minutes.\nAre you sure?")
                    .setPositiveButton("Start SOS", (d, w) ->
                            BLE.INSTANCE.getWriteQueue().offer("LOCATION=4"))
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        // SOS Stop (LOCATION=5)
        binding.buttonSetStop.setOnClickListener(v ->
                BLE.INSTANCE.getWriteQueue().offer("LOCATION=5"));

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
                setting.append("SET=SOS,T0000,D0000,");
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
