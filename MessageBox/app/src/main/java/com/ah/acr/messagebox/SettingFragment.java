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
import android.widget.AdapterView;
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


/**
 * Settings Fragment - Location Report Setting
 *
 * ⭐ v6 patch (2026-05-03):
 * - Dirty state lock + Save button highlight + Distance disable visualization
 *
 * ⭐ patch (2026-05-28): SET 응답 실시간 자동 갱신 — observeForever 방식
 *
 * ⭐ patch (2026-05-29): SET 응답 캐시 + 진입 시 복원
 * - 문제: 장비가 자발적으로 SET을 주기 송신하지만(예: 19초 간격),
 *         화면 진입 직후엔 다음 SET이 올 때까지 빈 상태로 보임.
 *         또한 ViewPager2가 SettingFragment 인스턴스를 여러 개 생성하면
 *         observeForever 콜백을 받은 인스턴스와 화면에 보이는 인스턴스가
 *         달라 화면 갱신이 안 되는 케이스 발생.
 * - 해결: SET 응답을 SharedPreferences에 캐시 → 어느 인스턴스든
 *         onCreateView에서 캐시값을 즉시 화면에 복원. 이후 새 SET 오면
 *         observeForever가 자동 갱신하면서 캐시도 갱신.
 *         → 인스턴스 문제 우회 + 재접속/재진입 시 즉시 표시.
 *
 * ⭐ patch (2026-05-29 #3): SET=? 주기 폴링 추가
 * - 진단: 장비는 BROAD(위치)만 5초마다 자발 송신하고 SET(설정)은 절대 자발 송신하지 않음
 *         (BLE Protocol Rev1.0 3.6 — TYTO2 responds only when requested by APP).
 *         "장비가 19초마다 SET 자발 송신" 전제는 오류였음.
 *         BROAD=5는 위치 주기 송신 설정 명령이지 SET 조회가 아님.
 * - 해결: 화면이 보이는 동안(onResume~onPause) SET=? 를 주기 전송 →
 *         장비가 SET=... 으로 회신 → observe가 받아 화면 자동 갱신.
 *         (웹의 폴링과 동일 구조. 화면 벗어나면 폴링 중지로 BLE 트래픽 절약)
 *
 * 변경 감지 대상:
 *   - Unit Type (Spinner), Time 체크/값, Distance 체크/값, Receiver
 */
public class SettingFragment extends Fragment {
    private static final String TAG = SettingFragment.class.getSimpleName();

    // ★★★ 캐시 키 (SharedPreferences)
    private static final String PREF_LAST_SET = "pref_last_device_set";

    private FragmentSettingBinding binding;
    private KeyViewModel mKeyViewModel;
    private AddressViewModel addressViewModel;
    private BleViewModel mBleViewModel;

    // ★★★ SET=? 주기 폴링 (화면 떠 있는 동안 장비에 현재 설정 요청)
    //     BLE Protocol 3.6: SET=? 요청 → 장비가 SET=Mode,Tcycle,Dist,TrackAddr,SosAddr 회신
    private final android.os.Handler mSetPollHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long SET_POLL_INTERVAL_MS = 15000L;   // 15초 (BROAD 5초 트래픽 고려)
    private final Runnable mSetPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding == null) return;
            BLE.INSTANCE.getWriteQueue().offer("SET=?");
            Log.v(TAG, "⟳ SET=? 폴링 전송");
            mSetPollHandler.postDelayed(this, SET_POLL_INTERVAL_MS);
        }
    };


    // 장비 전송용 코드 배열
    private static final String[] UNIT_TYPE_CODES = {"CAR", "UAV", "UAT"};

    // 다크 테마 색상
    private static final int COLOR_CYAN     = 0xFF00E5D1;
    private static final int COLOR_GRAY_BG  = 0xFF2A3A5A;
    private static final int COLOR_DIRTY    = 0xFFFFB300;
    private static final int COLOR_SAVE_OK  = 0xFF00E5D1;

    // 최소/최대값 상수
    private static final int MIN_TIME = 0;
    private static final int MAX_TIME = 9999;
    private static final int MIN_DIST = 0;
    private static final int MAX_DIST = 9999;

    // Disable 시각화 alpha
    private static final float ALPHA_ENABLED  = 1.0f;
    private static final float ALPHA_DISABLED = 0.4f;

    // Dirty 상태 관리
    private boolean mIsDirty = false;
    private boolean mIsInitializing = true;


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
        Log.v(TAG, "☆ onCreateView 시작 [inst=" + this.hashCode() + "]");

        binding = FragmentSettingBinding.inflate(inflater, container, false);

        // Unit Type 스피너
        ArrayAdapter<CharSequence> displayAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.unit_type_display,
                R.layout.spinner_item);
        displayAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        binding.spinnerUnitType.setAdapter(displayAdapter);

        // Spinner 변경 감지
        binding.spinnerUnitType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!mIsInitializing) markDirty();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        // Receiver 버튼 클릭 → 다이얼로그
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

        // ⭐ patch (2026-05-29 #2): observeForever → lifecycle observe 전환
        // 이유: ViewPager2가 SettingFragment를 여러 개 생성할 때 observeForever는
        //       화면에 안 보이는(detached) 인스턴스까지 콜백을 받아, 보이는 인스턴스가
        //       제때 갱신되지 않는 혼선이 발생했음.
        //       getViewLifecycleOwner()로 observe하면 STARTED(화면에 보이는) 인스턴스만
        //       콜백을 받고, 등록 즉시 현재값을 1회 전달받아 진입 시점 표시도 보장됨.
        //       → 화면 보는 도중 새 SET이 와도 그 자리에서 자동 갱신. 탭 이동 불필요.
        BLE.INSTANCE.getDeviceSet().observe(getViewLifecycleOwner(), s -> {
            Log.v(TAG, "▶ Observer 콜백 [inst=" + SettingFragment.this.hashCode() + "] value=" + s);
            applySetResponse(s);
        });

        return binding.getRoot();
    }


    @Override
    public void onResume() {
        super.onResume();
        // ★★★ 화면이 보이기 시작 → 즉시 1회 조회 + 주기 폴링 시작
        BLE.INSTANCE.getWriteQueue().offer("SET=?");
        Log.v(TAG, "⟳ onResume: SET=? 1회 + 폴링 시작");
        mSetPollHandler.removeCallbacks(mSetPollRunnable);
        mSetPollHandler.postDelayed(mSetPollRunnable, SET_POLL_INTERVAL_MS);
    }


    @Override
    public void onPause() {
        super.onPause();
        // ★★★ 화면이 벗어남 → 폴링 중지 (불필요한 BLE 트래픽 방지)
        mSetPollHandler.removeCallbacks(mSetPollRunnable);
        Log.v(TAG, "⟳ onPause: SET=? 폴링 중지");
    }


    // ★★★ SET 응답을 화면에 반영 + 캐시 저장
    private void applySetResponse(String s) {
        Log.v(TAG, "★1 진입 [inst=" + this.hashCode()
                + " binding=" + (binding == null ? "null" : String.valueOf(binding.hashCode()))
                + "]: " + s);
        if (binding == null) { Log.v(TAG, "★X binding=null, 종료"); return; }
        if (s == null || !s.startsWith("SET=")) { Log.v(TAG, "★X SET= 아님, 종료"); return; }
        String msg = s.substring(4);
        String[] vals = msg.split(",");
        if (vals.length == 0) return;
        if (vals[0].equals("OK") || vals[0].equals("FAIL")) {
            Toast.makeText(getContext(), s, Toast.LENGTH_LONG).show();
            return;
        }
        if (vals.length < 3) { Log.v(TAG, "★X vals.length < 3, 종료"); return; }
        Log.v(TAG, "★2 파싱: vals=" + java.util.Arrays.toString(vals));

        // ★★★ 캐시 저장 — 다음 진입 시 즉시 복원할 수 있도록
        saveLastSet(s);

        mIsInitializing = true;
        String type = vals[0];
        String time = vals[1].replaceAll("[^0-9]", "");
        String dist = vals[2].replaceAll("[^0-9]", "");
        try {
            int timeVal = Integer.parseInt(time);
            int distVal = Integer.parseInt(dist);
            Log.v(TAG, "★3 적용: type=" + type + " timeVal=" + timeVal + " distVal=" + distVal);
            binding.chkTime.setChecked(timeVal != 0);
            binding.chkDist.setChecked(distVal != 0);
            if (timeVal > 0) setTimeValue(timeVal, false);
            if (distVal > 0) setDistValue(distVal, false);
            Log.v(TAG, "★4 setText 후 화면값: time=" + binding.textTime.getText()
                    + " dist=" + binding.textDist.getText());
        } catch (NumberFormatException e) {
            Log.e(TAG, "parse error: " + e);
        }
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
        applyDistanceEnableVisual(binding.chkDist.isChecked());
        applyTimeEnableVisual(binding.chkTime.isChecked());

        // 리스너 비동기 markDirty() 방지 — 150ms 후 clearDirty
        if (binding != null && binding.getRoot() != null) {
            binding.getRoot().postDelayed(() -> {
                if (binding == null) return;
                mIsInitializing = false;
                clearDirty();
            }, 150);
        } else {
            mIsInitializing = false;
            clearDirty();
        }
    }


    // ★★★ SharedPreferences에 마지막 SET 응답 저장
    private void saveLastSet(String setStr) {
        if (setStr == null) return;
        try {
            android.content.SharedPreferences prefs =
                    android.preference.PreferenceManager
                            .getDefaultSharedPreferences(requireContext());
            prefs.edit().putString(PREF_LAST_SET, setStr).apply();
        } catch (Exception e) {
            Log.v(TAG, "saveLastSet 실패: " + e.getMessage());
        }
    }

    // ★★★ 저장된 마지막 SET 응답 불러와 화면 복원
    private void restoreLastSet() {
        try {
            android.content.SharedPreferences prefs =
                    android.preference.PreferenceManager
                            .getDefaultSharedPreferences(requireContext());
            String last = prefs.getString(PREF_LAST_SET, null);
            if (last != null && last.startsWith("SET=")) {
                Log.v(TAG, "♻ 캐시에서 복원: " + last);
                applySetResponse(last);
            } else {
                Log.v(TAG, "♻ 캐시 비어있음 (첫 진입)");
            }
        } catch (Exception e) {
            Log.v(TAG, "restoreLastSet 실패: " + e.getMessage());
        }
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
            updateStartButtonByDirtyState();
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

        // Start 버튼: dirty 체크 후 BLE 송신
        binding.buttonSetStart.setOnClickListener(v -> {
            if (mIsDirty) {
                Toast.makeText(getContext(),
                        getString(R.string.setting_toast_save_first),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            BLE.INSTANCE.getWriteQueue().offer("LOCATION=2");
        });

        binding.buttonSetStop.setOnClickListener(v -> BLE.INSTANCE.getWriteQueue().offer("LOCATION=3"));

        // Save 버튼
        binding.buttonSetSave.setOnClickListener(v -> handleSave());

        // 초기값 설정 (dirty 트리거 안 함)
        mIsInitializing = true;
        setTimeValue(3, false);
        setDistValue(10, false);
        mIsInitializing = false;

        // 초기 시각 상태 적용
        clearDirty();
        applyDistanceEnableVisual(binding.chkDist.isChecked());
        applyTimeEnableVisual(binding.chkTime.isChecked());

        // ★★★ 캐시에서 마지막 SET 값 복원 (초기값 위에 덮어씀)
        // 화면에 떴을 때 펌웨어 실제 값이 즉시 보이도록.
        restoreLastSet();
    }


    // ═══════════════════════════════════════════════════════════════
    //   DIRTY STATE MANAGEMENT
    // ═══════════════════════════════════════════════════════════════

    private void markDirty() {
        if (mIsInitializing) return;
        if (mIsDirty) return;
        mIsDirty = true;
        updateSaveButtonHighlight();
        updateStartButtonByDirtyState();
        Log.v(TAG, "-> DIRTY (변경사항 있음)");
    }

    private void clearDirty() {
        mIsDirty = false;
        updateSaveButtonHighlight();
        updateStartButtonByDirtyState();
        Log.v(TAG, "-> CLEAN (저장됨)");
    }

    private void updateSaveButtonHighlight() {
        if (binding == null || binding.buttonSetSave == null) return;
        if (mIsDirty) {
            binding.buttonSetSave.setBackgroundColor(COLOR_DIRTY);
        } else {
            binding.buttonSetSave.setBackgroundColor(COLOR_SAVE_OK);
        }
    }

    private void updateStartButtonByDirtyState() {
        if (binding == null || binding.buttonSetStart == null) return;

        DeviceStatus status = mBleViewModel.getDeviceStatus().getValue();
        boolean isTracking = status != null && status.isTrackingMode();

        if (isTracking) {
            binding.buttonSetStart.setBackgroundColor(COLOR_GRAY_BG);
            binding.buttonSetStart.setAlpha(ALPHA_ENABLED);
        } else if (mIsDirty) {
            binding.buttonSetStart.setBackgroundColor(COLOR_GRAY_BG);
            binding.buttonSetStart.setAlpha(ALPHA_DISABLED);
        } else {
            binding.buttonSetStart.setBackgroundColor(COLOR_CYAN);
            binding.buttonSetStart.setAlpha(ALPHA_ENABLED);
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   DISABLE VISUALIZATION
    // ═══════════════════════════════════════════════════════════════

    private void applyDistanceEnableVisual(boolean enabled) {
        if (binding == null) return;

        float alpha = enabled ? ALPHA_ENABLED : ALPHA_DISABLED;

        binding.presetDist2.setEnabled(enabled);
        binding.presetDist5.setEnabled(enabled);
        binding.presetDist10.setEnabled(enabled);
        binding.presetDist50.setEnabled(enabled);
        binding.presetDist100.setEnabled(enabled);
        binding.presetDist200.setEnabled(enabled);
        binding.presetDist2.setAlpha(alpha);
        binding.presetDist5.setAlpha(alpha);
        binding.presetDist10.setAlpha(alpha);
        binding.presetDist50.setAlpha(alpha);
        binding.presetDist100.setAlpha(alpha);
        binding.presetDist200.setAlpha(alpha);

        binding.textDist.setEnabled(enabled);
        binding.textDist.setAlpha(alpha);
        binding.textDistDisplay.setAlpha(alpha);
    }

    private void applyTimeEnableVisual(boolean enabled) {
        if (binding == null) return;

        float alpha = enabled ? ALPHA_ENABLED : ALPHA_DISABLED;

        binding.presetTime3.setEnabled(enabled);
        binding.presetTime5.setEnabled(enabled);
        binding.presetTime10.setEnabled(enabled);
        binding.presetTime15.setEnabled(enabled);
        binding.presetTime30.setEnabled(enabled);
        binding.presetTime60.setEnabled(enabled);
        binding.presetTime3.setAlpha(alpha);
        binding.presetTime5.setAlpha(alpha);
        binding.presetTime10.setAlpha(alpha);
        binding.presetTime15.setAlpha(alpha);
        binding.presetTime30.setAlpha(alpha);
        binding.presetTime60.setAlpha(alpha);

        binding.textTime.setEnabled(enabled);
        binding.textTime.setAlpha(alpha);
    }


    // ═══════════════════════════════════════════════════════════════
    //   RECEIVER MENU
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
                        case 0: setReceiverWeb(); markDirty(); break;
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
        input.setHint("Enter Unitcode (10) or IMEI (15)");
        input.setPadding(40, 30, 40, 30);

        new AlertDialog.Builder(requireContext())
                .setTitle("Enter Receiver")
                .setView(input)
                .setPositiveButton("OK", (dialog, which) -> {
                    String number = input.getText().toString().trim();
                    if (!number.isEmpty() && number.matches("\\d+")) {
                        setReceiverManual(number);
                        markDirty();
                    } else if (!number.isEmpty()) {
                        Toast.makeText(getContext(),
                                "Receiver must be digits only (10 or 15)",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    // ═══════════════════════════════════════════════════════════════
    //   TIME PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void setupTimePresets() {
        binding.presetTime3.setOnClickListener(v -> { setTimeValue(3, true); markDirty(); });
        binding.presetTime5.setOnClickListener(v -> { setTimeValue(5, true); markDirty(); });
        binding.presetTime10.setOnClickListener(v -> { setTimeValue(10, true); markDirty(); });
        binding.presetTime15.setOnClickListener(v -> { setTimeValue(15, true); markDirty(); });
        binding.presetTime30.setOnClickListener(v -> { setTimeValue(30, true); markDirty(); });
        binding.presetTime60.setOnClickListener(v -> { setTimeValue(60, true); markDirty(); });
    }


    private void setTimeValue(int minutes, boolean autoEnable) {
        if (minutes < MIN_TIME) minutes = MIN_TIME;
        if (minutes > MAX_TIME) minutes = MAX_TIME;

        String currentText = binding.textTime.getText().toString().trim();
        String newText = String.valueOf(minutes);
        if (!currentText.equals(newText)) {
            binding.textTime.setText(newText);
        }

        updateTimePresetSelection(minutes);

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
    //   DISTANCE PRESETS
    // ═══════════════════════════════════════════════════════════════

    private void setupDistPresets() {
        binding.presetDist2.setOnClickListener(v -> { setDistValue(2, true); markDirty(); });
        binding.presetDist5.setOnClickListener(v -> { setDistValue(5, true); markDirty(); });
        binding.presetDist10.setOnClickListener(v -> { setDistValue(10, true); markDirty(); });
        binding.presetDist50.setOnClickListener(v -> { setDistValue(50, true); markDirty(); });
        binding.presetDist100.setOnClickListener(v -> { setDistValue(100, true); markDirty(); });
        binding.presetDist200.setOnClickListener(v -> { setDistValue(200, true); markDirty(); });
    }


    private void setDistValue(int x10m, boolean autoEnable) {
        if (x10m < MIN_DIST) x10m = MIN_DIST;
        if (x10m > MAX_DIST) x10m = MAX_DIST;

        String currentText = binding.textDist.getText().toString().trim();
        String newText = String.valueOf(x10m);
        if (!currentText.equals(newText)) {
            binding.textDist.setText(newText);
        }

        updateDistDisplay(x10m);
        updateDistPresetSelection(x10m);

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
            applyDistanceEnableVisual(isChecked);
            if (!mIsInitializing) markDirty();
        });

        binding.chkTime.setOnCheckedChangeListener((buttonView, isChecked) -> {
            applyTimeEnableVisual(isChecked);
            if (!mIsInitializing) markDirty();
        });
    }


    // ═══════════════════════════════════════════════════════════════
    //   TEXT LISTENERS
    // ═══════════════════════════════════════════════════════════════

    private void setupTextListeners() {
        binding.textTime.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!binding.textTime.hasFocus()) return;
                try {
                    int val = Integer.parseInt(s.toString());
                    updateTimePresetSelection(val);
                    binding.chkTime.setChecked(val > 0);
                    if (!mIsInitializing) markDirty();
                } catch (NumberFormatException ignored) {}
            }
        });

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
                    binding.chkDist.setChecked(val > 0);
                    if (!mIsInitializing) markDirty();
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

        if (binding.chkTime.isChecked()) {
            String time = binding.textTime.getText().toString().trim();
            int timeValue = 0;
            try {
                timeValue = Integer.parseInt(time);
            } catch (NumberFormatException e) {
                timeValue = 0;
            }
            if (timeValue < MIN_TIME) timeValue = MIN_TIME;
            setting.append(String.format("T%04d", timeValue));
        } else {
            setting.append("T0000");
        }

        setting.append(",");

        if (binding.chkDist.isChecked()) {
            String dist = binding.textDist.getText().toString().trim();
            int distValue = 0;
            try {
                distValue = Integer.parseInt(dist);
            } catch (NumberFormatException e) {
                distValue = 0;
            }
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

        clearDirty();
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
            markDirty();
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
        Log.v(TAG, "☆ onDestroyView [inst=" + this.hashCode() + "]");
        mSetPollHandler.removeCallbacks(mSetPollRunnable);   // ★ SET=? 폴링 정리
        super.onDestroyView();
        binding = null;
    }
}