package com.ah.acr.messagebox;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
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
import com.ah.acr.messagebox.databinding.FragmentSettingBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.search.SearchDialogFragment;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;

import java.util.Locale;


public class SettingFragment extends Fragment {
    private static final String TAG = SettingFragment.class.getSimpleName();

    private FragmentSettingBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    // 장비 전송용 코드 배열
    private static final String[] UNIT_TYPE_CODES = {"CAR", "UAV", "UAT"};

    // 다크 테마 색상
    private static final int COLOR_CYAN    = 0xFF00E5D1;
    private static final int COLOR_GRAY_BG = 0xFF2A3A5A;

    // ⭐ 최소/최대값 상수
    private static final int MIN_TIME = 1;
    private static final int MAX_TIME = 9999;
    private static final int MIN_DIST = 1;
    private static final int MAX_DIST = 9999;


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

        // Unit Type 스피너
        ArrayAdapter<CharSequence> displayAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.unit_type_display,
                R.layout.spinner_item);
        displayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.spinnerUnitType.setAdapter(displayAdapter);

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // ⭐ Receiver 버튼 클릭 → 다이얼로그
        binding.layoutReceiverDisplay.setOnClickListener(v -> showReceiverMenu());

        // 장비 상태 관찰
        mBleViewModel.getDeviceStatus().observe(getViewLifecycleOwner(), new Observer<DeviceStatus>() {
            @Override
            public void onChanged(@Nullable final DeviceStatus status) {
                if (BLE.INSTANCE.getSelectedDevice().getValue() != null && status != null) {
                    updateStartStopButtonState(status.isTrackingMode());
                }
            }
        });

        // 장비 설정 수신 (기존 로직)
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

                String type = vals[0];
                String time = vals[1].replaceAll("[^0-9]", "");
                String dist = vals[2].replaceAll("[^0-9]", "");

                try {
                    int timeVal = Integer.parseInt(time);
                    int distVal = Integer.parseInt(dist);

                    if (timeVal != 0) binding.chkTime.setChecked(true);
                    if (distVal != 0) binding.chkDist.setChecked(true);

                    if (timeVal > 0) setTimeValue(timeVal, false);
                    if (distVal > 0) setDistValue(distVal, false);
                } catch (NumberFormatException e) {
                    Log.e(TAG, "parse error: " + e);
                }

                // Receiver 처리
                if (vals.length > 3) {
                    String receiver = vals[3];
                    if (!receiver.equals("0")) {
                        addressViewModel.getAddressByNumbers(receiver).observe(getViewLifecycleOwner(), addressEntity -> {
                            if (addressEntity != null) {
                                setReceiverFromContact(receiver, addressEntity.getNumbersNic());
                            } else {
                                setReceiverManual(receiver);
                            }
                        });
                    } else {
                        setReceiverWeb();
                    }
                }

                int position = findCodeIndex(type);
                if (position >= 0) {
                    binding.spinnerUnitType.setSelection(position);
                }
            }
        });

        return binding.getRoot();
    }


    private int findCodeIndex(String code) {
        for (int i = 0; i < UNIT_TYPE_CODES.length; i++) {
            if (UNIT_TYPE_CODES[i].equals(code)) return i;
        }
        return 0;
    }

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


    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BLE.INSTANCE.getSelectedDevice().observe(getViewLifecycleOwner(), device -> {
            if (device != null) {
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            }
        });

        setupTimePresets();
        setupDistPresets();
        setupCheckBoxes();
        setupTextListeners();

        // Start / Stop
        binding.buttonSetStart.setOnClickListener(v -> BLE.INSTANCE.getWriteQueue().offer("LOCATION=2"));
        binding.buttonSetStop.setOnClickListener(v -> BLE.INSTANCE.getWriteQueue().offer("LOCATION=3"));

        // Save 버튼
        binding.buttonSetSave.setOnClickListener(v -> handleSave());

        // 초기값 설정
        setTimeValue(3, false);
        setDistValue(10, false);
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ RECEIVER MENU
    // ═══════════════════════════════════════════════════════════════

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


    private void setReceiverWeb() {
        binding.textReceiver.setText("");
        binding.textReceiverIcon.setText("📧");
        binding.textReceiverLabel.setText("Web Server");
        binding.textReceiverSub.setText("Default (no specific receiver)");
    }


    private void setReceiverFromContact(String number, String nickname) {
        binding.textReceiver.setText(nickname != null ? nickname : number);
        binding.textReceiverIcon.setText("📇");
        binding.textReceiverLabel.setText(nickname != null ? nickname : number);
        binding.textReceiverSub.setText(number);
    }


    private void setReceiverManual(String number) {
        binding.textReceiver.setText(number);
        binding.textReceiverIcon.setText("⌨");
        binding.textReceiverLabel.setText(number);
        binding.textReceiverSub.setText("Manual entry");
    }


    private void showAddressBookPicker() {
        setupFragmentResultListener();
        SearchDialogFragment searchDialog = new SearchDialogFragment();
        searchDialog.show(getParentFragmentManager(), "SearchDialog");
    }


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
    //   ⭐ TIME PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void setupTimePresets() {
        binding.presetTime3.setOnClickListener(v -> setTimeValue(3, true));
        binding.presetTime5.setOnClickListener(v -> setTimeValue(5, true));
        binding.presetTime10.setOnClickListener(v -> setTimeValue(10, true));
        binding.presetTime15.setOnClickListener(v -> setTimeValue(15, true));
        binding.presetTime30.setOnClickListener(v -> setTimeValue(30, true));
        binding.presetTime60.setOnClickListener(v -> setTimeValue(60, true));
    }


    private void setTimeValue(int minutes, boolean autoEnable) {
        // ⭐ 최소값 1로 변경 (기존 3 → 1)
        if (minutes < MIN_TIME) minutes = MIN_TIME;
        if (minutes > MAX_TIME) minutes = MAX_TIME;

        // EditText (재진입 방지)
        String currentText = binding.textTime.getText().toString().trim();
        String newText = String.valueOf(minutes);
        if (!currentText.equals(newText)) {
            binding.textTime.setText(newText);
        }

        // 프리셋 버튼 selected 상태
        updateTimePresetSelection(minutes);

        // 자동 체크
        if (autoEnable && !binding.chkTime.isChecked()) {
            binding.chkTime.setChecked(true);
        }
    }


    private void updateTimePresetSelection(int minutes) {
        binding.presetTime3.setSelected(minutes == 3);
        binding.presetTime5.setSelected(minutes == 5);
        binding.presetTime10.setSelected(minutes == 10);
        binding.presetTime15.setSelected(minutes == 15);
        binding.presetTime30.setSelected(minutes == 30);
        binding.presetTime60.setSelected(minutes == 60);
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ DISTANCE PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void setupDistPresets() {
        binding.presetDist2.setOnClickListener(v -> setDistValue(2, true));
        binding.presetDist5.setOnClickListener(v -> setDistValue(5, true));
        binding.presetDist10.setOnClickListener(v -> setDistValue(10, true));
        binding.presetDist50.setOnClickListener(v -> setDistValue(50, true));
        binding.presetDist100.setOnClickListener(v -> setDistValue(100, true));
        binding.presetDist200.setOnClickListener(v -> setDistValue(200, true));
    }


    private void setDistValue(int x10m, boolean autoEnable) {
        // ⭐ 최소값 1로 변경 (기존 2 → 1)
        if (x10m < MIN_DIST) x10m = MIN_DIST;
        if (x10m > MAX_DIST) x10m = MAX_DIST;

        // EditText (재진입 방지)
        String currentText = binding.textDist.getText().toString().trim();
        String newText = String.valueOf(x10m);
        if (!currentText.equals(newText)) {
            binding.textDist.setText(newText);
        }

        // 표시 업데이트
        updateDistDisplay(x10m);

        // 프리셋 버튼 selected 상태
        updateDistPresetSelection(x10m);

        // 자동 체크
        if (autoEnable && !binding.chkDist.isChecked()) {
            binding.chkDist.setChecked(true);
        }
    }


    private void updateDistDisplay(int x10m) {
        int meters = x10m * 10;
        String display;
        if (meters >= 1000) {
            if (meters % 1000 == 0) {
                display = String.format(Locale.US, "(%dkm)", meters / 1000);
            } else {
                display = String.format(Locale.US, "(%.1fkm)", meters / 1000.0);
            }
        } else {
            display = String.format(Locale.US, "(%dm)", meters);
        }
        binding.textDistDisplay.setText(display);
    }


    private void updateDistPresetSelection(int x10m) {
        binding.presetDist2.setSelected(x10m == 2);
        binding.presetDist5.setSelected(x10m == 5);
        binding.presetDist10.setSelected(x10m == 10);
        binding.presetDist50.setSelected(x10m == 50);
        binding.presetDist100.setSelected(x10m == 100);
        binding.presetDist200.setSelected(x10m == 200);
    }


    // ═══════════════════════════════════════════════════════════════
    //   CHECK BOXES
    // ═══════════════════════════════════════════════════════════════

    private void setupCheckBoxes() {
        binding.chkDist.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                String cur = binding.textDist.getText().toString().trim();
                if (cur.isEmpty() || cur.equals("0")) {
                    setDistValue(10, false);
                }
            }
        });

        binding.chkTime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                String cur = binding.textTime.getText().toString().trim();
                if (cur.isEmpty() || cur.equals("0")) {
                    setTimeValue(3, false);
                }
            }
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ TEXT LISTENERS (수동 입력 → 프리셋 동기화)
    // ═══════════════════════════════════════════════════════════════

    private void setupTextListeners() {
        // Time EditText
        binding.textTime.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding.textTime.hasFocus()) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    updateTimePresetSelection(val);
                    // ⭐ 최소값 3 → 1로 변경
                    if (val >= MIN_TIME && !binding.chkTime.isChecked()) {
                        binding.chkTime.setChecked(true);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });

        // Distance EditText
        binding.textDist.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding.textDist.hasFocus()) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    updateDistDisplay(val);
                    updateDistPresetSelection(val);
                    // ⭐ 최소값 2 → 1로 변경
                    if (val >= MIN_DIST && !binding.chkDist.isChecked()) {
                        binding.chkDist.setChecked(true);
                    }
                } catch (NumberFormatException ignored) {}
            }
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   SAVE LOGIC
    // ═══════════════════════════════════════════════════════════════

    private void handleSave() {
        String nicName = binding.textReceiver.getText().toString().trim();

        if (nicName.isEmpty()) {
            buildAndSendSetting("0");
        } else {
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
        setting.append("SET=");

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
            // ⭐ 최소값 3 → 1로 변경
            if (timeValue < MIN_TIME) timeValue = MIN_TIME;
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
            // ⭐ 최소값 2 → 1로 변경
            if (distValue < MIN_DIST) distValue = MIN_DIST;
            setting.append(String.format("D%04d", distValue));
        } else {
            setting.append("D0000");
        }

        setting.append(",");
        setting.append(codeNum);

        Log.v(TAG, setting.toString());
        BLE.INSTANCE.getWriteQueue().offer(setting.toString());

        Toast.makeText(getContext(), "Settings sent to device", Toast.LENGTH_SHORT).show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   SEARCH DIALOG
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
            Toast.makeText(getContext(), "Selected: " + title, Toast.LENGTH_SHORT).show();
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
