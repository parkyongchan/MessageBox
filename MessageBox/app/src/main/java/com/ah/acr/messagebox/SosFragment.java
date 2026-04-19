package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
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

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // ⭐ Receiver 카드 클릭 → 다이얼로그
        binding.layoutReceiverDisplay.setOnClickListener(v -> showReceiverMenu());

        // 장비 설정 수신 (기존 로직)
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

                // SOS 프로토콜: SET=SOS,T0000,D0000,receiver
                // vals[0]=SOS, vals[1]=T, vals[2]=D, vals[3]=receiver
                // ⚠️ 기존 코드는 vals[4]였으나, 실제 receiver는 vals[3]으로 보임
                //    기존 유지하되 길이 체크
                String receiver = null;
                if (vals.length > 4) {
                    receiver = vals[4];
                } else if (vals.length > 3) {
                    receiver = vals[3];
                }

                if (receiver != null) {
                    if (!receiver.equals("0")) {
                        final String finalReceiver = receiver;
                        addressViewModel.getAddressByNumbers(receiver).observe(getViewLifecycleOwner(), addressEntity -> {
                            if (addressEntity != null) {
                                setReceiverFromContact(finalReceiver, addressEntity.getNumbersNic());
                            } else {
                                setReceiverManual(finalReceiver);
                            }
                        });
                    } else {
                        setReceiverWeb();
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
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_INACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_ACTIVE);
        } else {
            binding.buttonSetStart.setBackgroundColor(COLOR_SOS_ACTIVE);
            binding.buttonSetStop.setBackgroundColor(COLOR_STOP_INACTIVE);
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // SOS Start (LOCATION=4)
        binding.buttonSetStart.setOnClickListener(v -> {
            new AlertDialog.Builder(getContext())
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
        binding.buttonSetSave.setOnClickListener(v -> handleSave());
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ RECEIVER MENU (NEW)
    // ═══════════════════════════════════════════════════════════════

    /** Receiver 선택 다이얼로그 */
    private void showReceiverMenu() {
        String[] options = {
                "📧 Web Server (default)",
                "📇 From Address Book",
                "⌨ Type Number Manually"
        };

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Receiver")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: setReceiverWeb(); break;
                        case 1: showAddressBookPicker(); break;
                        case 2: showManualInputDialog(); break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    /** Web Server로 설정 (빈 값) */
    private void setReceiverWeb() {
        binding.textReceiver.setText("");
        binding.textReceiverIcon.setText("📧");
        binding.textReceiverLabel.setText("Web Server");
        binding.textReceiverSub.setText("Default (no specific receiver)");
    }


    /** 주소록에서 선택한 연락처로 설정 */
    private void setReceiverFromContact(String number, String nickname) {
        binding.textReceiver.setText(nickname != null ? nickname : number);
        binding.textReceiverIcon.setText("📇");
        binding.textReceiverLabel.setText(nickname != null ? nickname : number);
        binding.textReceiverSub.setText(number);
    }


    /** 수동 입력한 번호로 설정 */
    private void setReceiverManual(String number) {
        binding.textReceiver.setText(number);
        binding.textReceiverIcon.setText("⌨");
        binding.textReceiverLabel.setText(number);
        binding.textReceiverSub.setText("Manual entry");
    }


    /** 주소록 선택 다이얼로그 */
    private void showAddressBookPicker() {
        setupFragmentResultListener();
        SearchDialogFragment searchDialog = new SearchDialogFragment();
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }


    /** 수동 입력 다이얼로그 */
    private void showManualInputDialog() {
        EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Enter IMEI number");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(requireContext())
                .setTitle("Enter Receiver IMEI")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty() && number.matches("\\d+")) {
                        setReceiverManual(number);
                    } else if (!number.isEmpty()) {
                        Toast.makeText(getContext(),
                                "IMEI must be digits only",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SAVE LOGIC
    // ═══════════════════════════════════════════════════════════════

    private void handleSave() {
        String nicName = binding.textReceiver.getText().toString().trim();

        if (nicName.isEmpty()) {
            // Web Server 전송
            buildAndSendSetting("0");
        } else {
            // 주소록 조회
            addressViewModel.getAddressByNicName(nicName).observe(getViewLifecycleOwner(), addressEntity -> {
                String codeNum = nicName;
                if (addressEntity != null) {
                    codeNum = addressEntity.getNumbers();
                }

                if (!codeNum.matches("\\d+")) {
                    Toast.makeText(getContext(),
                            "The recipient's number must contain only numbers.",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                buildAndSendSetting(codeNum);
            });
        }
    }


    private void buildAndSendSetting(String codeNum) {
        StringBuilder setting = new StringBuilder();
        setting.append("SET=SOS,T0000,D0000,");
        setting.append(codeNum);

        Log.v(TAG, setting.toString());
        BLE.INSTANCE.getWriteQueue().offer(setting.toString());

        Toast.makeText(getContext(), "SOS settings sent to device", Toast.LENGTH_SHORT).show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SEARCH DIALOG (기존)
    // ═══════════════════════════════════════════════════════════════

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
            setReceiverFromContact(code, title);
        }
    }


    private void hideKeyboard() {
        if (getActivity() != null && requireActivity().getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager)
                    requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(
                    requireActivity().getCurrentFocus().getWindowToken(),
                    InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
