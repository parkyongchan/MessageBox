package com.ah.acr.messagebox;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.databinding.FragmentBleLoginBinding;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.util.LocaleHelper;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.clj.fastble.BleManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;


public class BleLoginFragment extends Fragment {
    private static final String TAG = BleLoginFragment.class.getSimpleName();

    private FragmentBleLoginBinding binding;
    private KeyViewModel mKeyViewModel;

    private OnBackPressedCallback backPressedCallback;

    // ⭐ Observer 등록 여부 (중복 등록 방지)
    private boolean loginButtonObserverAttached = false;

    // ⭐ 언어 변경 중 플래그 (BLE 연결 유지용)
    // true일 때는 onDestroyView에서 BLE 해제 skip
    private boolean mIsChangingLanguage = false;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentBleLoginBinding.inflate(inflater, container, false);

        // ⚠️ 수정: postValue(NONE) 즉시 하면 비동기 때문에 꼬임!
        // setValue로 즉시 설정 (메인 스레드에서만 가능)
        BLE.INSTANCE.getBleLoginStatus().setValue(BLE.BLE_LOGIN_NONE);

        // DeviceInfo Observer (IMEI 표시용)
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                Log.v(TAG, "DeviceInfo: " + deviceInfo.toString());
                binding.textId.setText(deviceInfo.getImei());
            }
        });

        // 로그인 상태 Observer
        BLE.INSTANCE.getBleLoginStatus().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(String s) {
                Log.v(TAG, "Login status: " + s);

                if (s.equals(BLE.BLE_LOGIN_NONE)) {
                    // NONE 상태 = 사용자가 아직 로그인 안 함
                    setButtonDefault();

                } else if (s.equals(BLE.BLE_LOGIN_TRY)) {
                    // TRY = INFO= 받음 (자동) 또는 로그인 시도 중
                    // → 버튼 Connecting 표시는 버튼 클릭 시에만!
                    // → 여기서는 아무것도 안 함 (정상 상태)
                    Log.v(TAG, "TRY state - waiting for user action");

                } else if (s.equals(BLE.BLE_LOGIN_OK)) {
                    // ⭐ 로그인 성공
                    Log.v(TAG, "✅ BLE_LOGIN_OK received - navigating");
                    setButtonSuccess();

                    NavController navController = NavHostFragment.findNavController(BleLoginFragment.this);
                    NavOptions navOptions = new NavOptions.Builder()
                            .setPopUpTo(R.id.main_fragment, false)
                            .build();
                    navController.navigate(R.id.action_main_ble_login_fragment_to_main_fragment, null, navOptions);

                    // ⭐ Navigation 후 Devices 탭으로 전환
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        switchToDevicesTab();
                    }, 300);

                } else if (s.equals(BLE.BLE_LOGIN_FAIL)) {
                    setButtonDefault();
                    Toast.makeText(getContext(),
                            "❌ Connection failed. Check password.",
                            Toast.LENGTH_SHORT).show();

                    BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());

                    NavController navController = NavHostFragment.findNavController(BleLoginFragment.this);
                    NavOptions navOptions = new NavOptions.Builder()
                            .setPopUpTo(R.id.main_fragment, false)
                            .build();
                    navController.navigate(R.id.action_main_ble_login_fragment_to_main_ble_set_fragment, null, navOptions);

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_TRY)) {
                    // 비밀번호 변경 시도 중
                    Log.v(TAG, "CHANGE_TRY state");

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
                    Log.v(TAG, "✅ BLE_LOGIN_CHANGE_OK - navigating");
                    setButtonSuccess();

                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        switchToDevicesTab();
                    }, 300);

                } else if (s.equals(BLE.BLE_LOGIN_CHANGE_FAIL)) {
                    setButtonDefault();
                }
            }
        });

        mKeyViewModel = new ViewModelProvider(requireActivity()).get(KeyViewModel.class);

        // 저장된 비밀번호 복원
        BLE.INSTANCE.getDeviceInfo().observe(getViewLifecycleOwner(), new Observer<DeviceInfo>() {
            @Override
            public void onChanged(DeviceInfo deviceInfo) {
                String serialNum = deviceInfo.getSerialNum();

                SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                boolean remember = shared.getBoolean("lonin_remember");
                if (remember) {
                    String password = shared.getString(serialNum + "_login_pw");
                    binding.textPw.setText(password);
                    binding.checkboxRemember.setChecked(remember);
                }
            }
        });

        binding.checkboxRemember.setOnClickListener(v -> {
            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
            shared.putAny("lonin_remember", binding.checkboxRemember.isChecked());
        });


        // ⭐ BLE Connect 버튼 클릭
        binding.buttonLogin.setOnClickListener(v -> {
            String password = binding.textPw.getText().toString().trim();

            if (!isPasswordValid(password)) {
                Toast.makeText(getContext(),
                        "⚠️ Password must be at least 9 digits.",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // ⭐ Connecting 표시
            setButtonConnecting();

            // ⭐ 현재 DeviceInfo 즉시 사용 (Observer 재등록 X)
            DeviceInfo currentInfo = BLE.INSTANCE.getDeviceInfo().getValue();

            if (currentInfo == null) {
                Log.w(TAG, "DeviceInfo is null, cannot login");
                Toast.makeText(getContext(),
                        "⚠️ Device info not ready. Try again.",
                        Toast.LENGTH_SHORT).show();
                setButtonDefault();
                return;
            }

            Log.v(TAG, "Sending LOGIN command: serialNum=" + currentInfo.getSerialNum());
            BLE.INSTANCE.getWriteQueue().offer(
                    String.format("LOGIN=%s,%s", currentInfo.getSerialNum(), password));

            // 비밀번호 저장
            SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
            shared.putAny(currentInfo.getSerialNum() + "_login_pw", password);
        });


        // ⭐ NEW: 언어 선택 라디오 버튼 초기화 (2026-04-24)
        setupLanguageSelection();


        binding.getRoot().setOnClickListener(v -> hideKeyboard());

        return binding.getRoot();
    }


    // ═══════════════════════════════════════════════════════════════
    //   ⭐ NEW: 언어 선택 (2026-04-24)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 언어 라디오 버튼 초기화 + 리스너 등록
     *
     * ⚠️ 중요: Fragment 생명주기 중 Activity 재시작은 위험!
     * - BLE 연결 중일 때 recreate() → 크래시 가능
     * - 대신: 안전한 Activity 완전 재시작 방식 사용
     */
    private void setupLanguageSelection() {
        if (binding == null) return;

        RadioGroup groupLanguage = binding.getRoot().findViewById(R.id.group_language);
        RadioButton radioEn = binding.getRoot().findViewById(R.id.radio_en);
        RadioButton radioJa = binding.getRoot().findViewById(R.id.radio_ja);

        if (groupLanguage == null || radioEn == null || radioJa == null) {
            Log.w(TAG, "Language radio buttons not found");
            return;
        }

        // 현재 저장된 언어에 따라 초기 선택
        String currentLang = LocaleHelper.getLanguage(requireContext());
        Log.v(TAG, "Current language: " + currentLang);

        // ⚠️ 리스너 해제 후 초기 설정 (중복 호출 방지)
        groupLanguage.setOnCheckedChangeListener(null);

        if (LocaleHelper.LANG_JAPANESE.equals(currentLang)) {
            radioJa.setChecked(true);
        } else {
            radioEn.setChecked(true);
        }

        // ⭐ 라디오 버튼 초기 설정이 끝난 후 리스너 등록 (race condition 방지)
        binding.getRoot().post(() -> {
            if (groupLanguage == null) return;

            groupLanguage.setOnCheckedChangeListener((group, checkedId) -> {
                String newLang;
                if (checkedId == R.id.radio_ja) {
                    newLang = LocaleHelper.LANG_JAPANESE;
                } else {
                    newLang = LocaleHelper.LANG_ENGLISH;
                }

                // 현재 언어와 같으면 skip
                String savedLang = LocaleHelper.getLanguage(requireContext());
                if (newLang.equals(savedLang)) {
                    return;
                }

                Log.v(TAG, "Language change requested: " + savedLang + " → " + newLang);

                // 안전한 언어 변경
                changeLanguageSafely(newLang);
            });
        });
    }


    /**
     * ⭐ 안전한 언어 변경 (C 방식: Fragment만 재생성)
     *
     * Activity.recreate() 대신 Fragment만 재생성:
     * ✅ BLE 연결 유지
     * ✅ 부드러운 전환
     * ✅ 사용자 경험 좋음
     *
     * 로직:
     * 1. 언어 저장 (SharedPreferences)
     * 2. Locale 즉시 적용 (Configuration 업데이트)
     * 3. 현재 Fragment 제거 후 새 Fragment 생성
     *    → 새 Fragment는 새 Locale로 렌더링됨
     */
    /**
     * ⭐ 안전한 언어 변경 (저장만 + 재시작 안내)
     *
     * 복잡한 recreate 로직 대신:
     * 1. 언어 저장
     * 2. 사용자에게 재시작 안내
     * 3. 사용자가 앱 재시작 → 일본어 적용
     *
     * 이유: BLE 연결 끊김/크래시 방지
     */
    private void changeLanguageSafely(String newLang) {
        try {
            Log.v(TAG, "Language saved: " + newLang);

            // 1. 언어 저장
            LocaleHelper.setLocale(requireContext(), newLang);

            // 2. 사용자 안내
            String message;
            if (LocaleHelper.LANG_JAPANESE.equals(newLang)) {
                message = "言語設定を保存しました。アプリを再起動すると適用されます。";
            } else {
                message = "Language saved. Please restart the app to apply.";
            }

            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();

            Log.v(TAG, "✅ 언어 저장 완료. 앱 재시작 필요.");

        } catch (Exception e) {
            Log.e(TAG, "changeLanguageSafely error: " + e.getMessage(), e);
        }
    }


    /**
     * (사용 안 함)
     */
    private void updateAppLocale(String langCode) {
        // 사용 안 함
    }


    // ═══════════════════════════════════════════════════════════════
    //   BUTTON STATE HELPERS
    // ═══════════════════════════════════════════════════════════════

    private void setButtonDefault() {
        if (binding == null) return;
        try {
            binding.textLoginLabel.setText("Connect to TYTO2");
            binding.buttonLogin.setEnabled(true);
            binding.buttonLogin.setAlpha(1.0f);
            binding.textStatusHint.setText("👆 Tap above to connect");
        } catch (Exception e) {
            Log.e(TAG, "setButtonDefault error: " + e.getMessage());
        }
    }

    private void setButtonConnecting() {
        if (binding == null) return;
        try {
            binding.textLoginLabel.setText("Connecting...");
            binding.buttonLogin.setEnabled(false);
            binding.buttonLogin.setAlpha(0.7f);
            binding.textStatusHint.setText("⏳ Please wait...");
        } catch (Exception e) {
            Log.e(TAG, "setButtonConnecting error: " + e.getMessage());
        }
    }

    private void setButtonSuccess() {
        if (binding == null) return;
        try {
            binding.textLoginLabel.setText("Connected ✓");
            binding.buttonLogin.setEnabled(false);
            binding.textStatusHint.setText("✅ Moving to Devices...");
        } catch (Exception e) {
            Log.e(TAG, "setButtonSuccess error: " + e.getMessage());
        }
    }


    // ═══════════════════════════════════════════════════════════════
    //   AUTO TAB SWITCH
    // ═══════════════════════════════════════════════════════════════

    private void switchToDevicesTab() {
        try {
            if (getActivity() == null) {
                Log.w(TAG, "⚠️ Activity is null, cannot switch tab");
                return;
            }

            BottomNavigationView bottomNav =
                    requireActivity().findViewById(R.id.bottom_nav);

            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.tab_devices);
                Log.v(TAG, "✅ Auto-switched to Devices tab");
            } else {
                Log.w(TAG, "⚠️ BottomNav not found in Activity");
            }
        } catch (Exception e) {
            Log.e(TAG, "Tab switch failed: " + e.getMessage());
        }
    }


    private boolean isPasswordValid(String password) {
        return password != null && password.trim().length() > 8;
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setButtonDefault();

        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                showExitConfirmationDialog();
            }
        };

        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                callback
        );
    }

    private void showExitConfirmationDialog() {
        String state = BLE.INSTANCE.getBleLoginStatus().getValue();

        if (state != null
                && !state.equals(BLE.BLE_LOGIN_OK)
                && !state.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
        }

        navigateBack();
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

        // ⭐ 언어 변경 중이면 BLE 해제 skip (연결 유지)
        if (mIsChangingLanguage) {
            Log.v(TAG, "🔄 언어 변경 중 → BLE 연결 유지");
            return;
        }

        String status = BLE.INSTANCE.getBleLoginStatus().getValue();
        if (status != null && !status.equals(BLE.BLE_LOGIN_OK)) {
            BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }
}
