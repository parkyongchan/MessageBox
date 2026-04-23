package com.ah.acr.messagebox;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.ah.acr.messagebox.ble.BLE;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.data.FirmUpdate;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.SatTrackStateHolder;
import com.ah.acr.messagebox.databinding.ActivityMainBinding;
import com.ah.acr.messagebox.tabs.BleTabFragment;
import com.ah.acr.messagebox.tabs.ChatTabFragment;
import com.ah.acr.messagebox.tabs.DevicesTabFragment;
import com.ah.acr.messagebox.tabs.MapTabFragment;
import com.ah.acr.messagebox.tabs.SettingsTabFragment;
import com.ah.acr.messagebox.util.ImeiStorage;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.utils.HexUtil;
import com.google.android.material.snackbar.Snackbar;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final UUID BLE_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    private static final int PERMISSION_BLUETOOTH_SCAN = 5;
    private static final int PERMISSION_BLUETOOTH_CONNECT = 4;
    private static final int PERMISSION_BLUETOOTH_ADVERTISE = 3;
    private static final int PERMISSION_ACCESS_FINE_LOCATION = 2;
    private static final int PERMISSION_ACCESS_COARSE_LOCATION = 1;
    // ⭐ v4 Phase B-1: Android 13+ Notification 권한
    private static final int PERMISSION_POST_NOTIFICATIONS = 6;

    // Test data IMEIs
    private static final String TEST_IMEI_TRACK = "TEST-001";
    private static final String TEST_IMEI_SOS = "TEST-002";
    private static final String TEST_IMEI_MSG = "1111111111111111";

    // ⭐ 스마트 자동 받기 (Smart Auto Receive)
    public static final String PREF_AUTO_RECEIVE = "pref_auto_receive_enabled";
    public static final boolean DEFAULT_AUTO_RECEIVE = true;  // 기본 ON
    private static final long RECEIVE_TIMEOUT_MS = 30000;     // 30초 타임아웃

    // ⭐ 색상 (자동 받기 토글)
    private static final int COLOR_AUTO_ON = 0xFF00E5D1;       // 민트 (활성)
    private static final int COLOR_AUTO_OFF = 0xFF95B0D4;      // 회색 (비활성)
    private static final int COLOR_AUTO_RECEIVING = 0xFFFFB300; // 주황 (받는 중)

    private ActivityMainBinding binding;

    private BleViewModel mBleViewModel;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;
    private LocationViewModel locationViewModel;

    private int mTestTapCount = 0;
    private boolean mIsTestMode = false;

    // 헤더 버튼 토글 상태
    private boolean mIsTrackingMode = false;
    private boolean mIsSosMode = false;

    // 주기 동기화 Handler
    private Handler mSyncHandler;
    private Runnable mBroadRetryRunnable;
    private Runnable mInfoRetryRunnable;
    private long mLastBroadReceivedTime = 0;
    private long mLastInfoReceivedTime = 0;
    private static final long BROAD_TIMEOUT_MS = 15000;
    private static final long INFO_TIMEOUT_MS = 8000;
    private static final long PERIODIC_SYNC_MS = 30000;

    // ⭐ 스마트 자동 받기 상태 관리
    private boolean mIsAutoReceiving = false;
    private int mLastInboxCount = 0;
    private long mLastAutoReceiveTime = 0;
    private Runnable mAutoReceiveTimeoutRunnable;
    private Animation mAutoReceiveRotation;  // 회전 애니메이션

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ⭐ v4 Phase B-1: Android 13+ Notification 권한 요청
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_POST_NOTIFICATIONS);
            }
        }

        // ⭐ v4 Phase B-1: Foreground Service 시작
        // 앱 시작과 동시에 Service 시작 (백그라운드에서도 BLE 유지)
        com.ah.acr.messagebox.service.TytoConnectService.start(this);

        // Permission checks
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_BLUETOOTH_SCAN);
                    }
                });
                builder.show();
            }
            if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_BLUETOOTH_CONNECT);
                    }
                });
                builder.show();
            }
            if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, PERMISSION_BLUETOOTH_ADVERTISE);
                    }
                });
                builder.show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION);
                    }
                });
                builder.show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        mSyncHandler = new Handler(Looper.getMainLooper());

        BLE.INSTANCE.getSelectedDevice().observe(this, bleDevice -> {
            if (bleDevice != null) {
                Log.v("BLE", bleDevice.toString());
                setConnectBleDevice(bleDevice);
            } else {
                Log.v("BLE", "disconnected Ble device...");
                stopPeriodicSync();
                resetAutoReceive();
            }
        });

        BLE.INSTANCE.getWriteQueue().observe(this, queue -> {
            String request = queue.poll();
            bleSendMessage(request);
        });

        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MODE_PRIVATE);
        checkExternalStorage();

        setupFixedHeaderObservers();
        setupReconnectUI();
        setupAutoReceiveToggle();  // ⭐ 자동 받기 토글 설정
        setupHeaderButtons();
        setupBottomTabs();
    }


    // ═════════════════════════════════════════════════════════════
    //   ⭐ 스마트 자동 받기 (Smart Auto Receive) - Phase A + B
    // ═════════════════════════════════════════════════════════════

    /**
     * 자동 받기 활성화 상태 조회
     */
    private boolean isAutoReceiveEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(PREF_AUTO_RECEIVE, DEFAULT_AUTO_RECEIVE);
    }

    /**
     * 자동 받기 설정 저장
     */
    private void setAutoReceiveEnabled(boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_AUTO_RECEIVE, enabled).apply();
        Log.v("AUTO-RECV", "설정 변경: " + (enabled ? "ON" : "OFF"));
    }

    /**
     * ⭐ Phase B: 자동 받기 토글 UI 설정
     */
    private void setupAutoReceiveToggle() {
        // 회전 애니메이션 로드
        mAutoReceiveRotation = AnimationUtils.loadAnimation(this, R.anim.rotate_auto_receive);

        // 초기 UI 상태 설정
        updateAutoReceiveToggleUI(isAutoReceiveEnabled(), false);

        // 토글 클릭 리스너
        binding.statusArea.btnAutoReceive.setOnClickListener(v -> {
            boolean newState = !isAutoReceiveEnabled();
            setAutoReceiveEnabled(newState);
            updateAutoReceiveToggleUI(newState, false);

            String msg = newState ? "자동 받기 ON" : "자동 받기 OFF";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            // OFF로 변경했는데 받는 중이면 중지 표시 (실제 통신은 이미 진행 중)
            if (!newState && mIsAutoReceiving) {
                stopAutoReceiveAnimation();
                Log.v("AUTO-RECV", "사용자가 OFF로 변경 (진행 중인 수신은 계속됨)");
            }
        });
    }

    /**
     * ⭐ 자동 받기 토글 UI 업데이트
     *
     * @param enabled    활성화 여부
     * @param isReceiving 현재 받는 중인지
     */
    private void updateAutoReceiveToggleUI(boolean enabled, boolean isReceiving) {
        if (!enabled) {
            // OFF 상태: 회색, 정지
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_OFF);
            binding.statusArea.btnAutoReceive.clearAnimation();
        } else if (isReceiving) {
            // ON + 받는 중: 주황, 회전
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_RECEIVING);
            if (binding.statusArea.btnAutoReceive.getAnimation() == null) {
                binding.statusArea.btnAutoReceive.startAnimation(mAutoReceiveRotation);
            }
        } else {
            // ON + 대기: 민트, 정지
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_ON);
            binding.statusArea.btnAutoReceive.clearAnimation();
        }
    }

    /**
     * ⭐ 자동 받기 애니메이션 시작 (회전)
     */
    private void startAutoReceiveAnimation() {
        runOnUiThread(() -> {
            if (isAutoReceiveEnabled()) {
                updateAutoReceiveToggleUI(true, true);
            }
        });
    }

    /**
     * ⭐ 자동 받기 애니메이션 정지
     */
    private void stopAutoReceiveAnimation() {
        runOnUiThread(() -> {
            updateAutoReceiveToggleUI(isAutoReceiveEnabled(), false);
        });
    }

    /**
     * BROAD 응답에서 inbox 카운트 변화 감지하여 자동 받기 시작
     */
    private void checkAndTriggerAutoReceive(int currentInboxCount) {
        if (!isAutoReceiveEnabled()) {
            Log.v("AUTO-RECV", "자동 받기 비활성화 상태");
            return;
        }

        if (currentInboxCount <= 0) {
            mLastInboxCount = 0;
            return;
        }

        if (mIsAutoReceiving) {
            Log.v("AUTO-RECV", "이미 받는 중 (inbox=" + currentInboxCount + ")");
            return;
        }

        boolean shouldTrigger = (currentInboxCount > mLastInboxCount) || (mLastInboxCount == 0);
        if (!shouldTrigger) {
            Log.v("AUTO-RECV", "inbox 변화 없음 (inbox=" + currentInboxCount + ")");
            return;
        }

        Log.v("AUTO-RECV", "★ 자동 받기 시작 (inbox=" + currentInboxCount + ")");
        mIsAutoReceiving = true;
        mLastAutoReceiveTime = System.currentTimeMillis();
        mLastInboxCount = currentInboxCount;

        // ⭐ 애니메이션 시작
        startAutoReceiveAnimation();

        // RECEIVED 요청
        BLE.INSTANCE.getWriteQueue().offer("RECEIVED=0,OK");

        startAutoReceiveTimeout();
    }

    private void startAutoReceiveTimeout() {
        if (mAutoReceiveTimeoutRunnable != null) {
            mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
        }

        mAutoReceiveTimeoutRunnable = () -> {
            if (mIsAutoReceiving) {
                long elapsed = System.currentTimeMillis() - mLastAutoReceiveTime;
                Log.w("AUTO-RECV", "⚠ 자동 받기 타임아웃 (" + elapsed + "ms) - 리셋");
                mIsAutoReceiving = false;

                // ⭐ 애니메이션 정지
                stopAutoReceiveAnimation();
            }
        };

        mSyncHandler.postDelayed(mAutoReceiveTimeoutRunnable, RECEIVE_TIMEOUT_MS);
    }

    private void completeAutoReceive() {
        if (mIsAutoReceiving) {
            long elapsed = System.currentTimeMillis() - mLastAutoReceiveTime;
            Log.v("AUTO-RECV", "✓ 자동 받기 완료 (" + elapsed + "ms 소요)");
            mIsAutoReceiving = false;
            mLastInboxCount = 0;

            if (mAutoReceiveTimeoutRunnable != null) {
                mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
            }

            // ⭐ 애니메이션 정지
            stopAutoReceiveAnimation();
        }
    }

    private void resetAutoReceive() {
        mIsAutoReceiving = false;
        mLastInboxCount = 0;
        if (mAutoReceiveTimeoutRunnable != null) {
            mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
        }

        // ⭐ 애니메이션 정지
        stopAutoReceiveAnimation();

        Log.v("AUTO-RECV", "상태 리셋");
    }


    // ═════════════════════════════════════════════════════════════
    //   헤더 버튼 (TRACK, SOS, 종료)
    // ═════════════════════════════════════════════════════════════

    private void setupHeaderButtons() {
        binding.headerArea.btnTrackHeader.setOnClickListener(v -> onTrackButtonClick());
        binding.headerArea.btnSosHeader.setOnClickListener(v -> onSosButtonClick());
        binding.headerArea.btnExitHeader.setOnClickListener(v -> onExitButtonClick());
    }

    private void onTrackButtonClick() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(this, "장비가 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsTrackingMode) {
            new AlertDialog.Builder(this)
                    .setTitle("추적 모드 중지")
                    .setMessage("추적(Tracking) 모드를 중지하시겠습니까?")
                    .setPositiveButton("중지", (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=3");
                        Log.v("TRACK", "LOCATION=3 (추적 중지 요청)");
                    })
                    .setNegativeButton("취소", null)
                    .show();
        } else {
            if (mIsSosMode) {
                Toast.makeText(this, "SOS 모드 중에는 추적을 시작할 수 없습니다.", Toast.LENGTH_LONG).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle("추적 모드 시작")
                    .setMessage("추적(Tracking) 모드를 시작하시겠습니까?\n\n설정된 주기에 따라 위치가 전송됩니다.")
                    .setPositiveButton("시작", (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=2");
                        Log.v("TRACK", "LOCATION=2 (추적 시작 요청)");
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    private void onSosButtonClick() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(this, "장비가 연결되어 있지 않습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsSosMode) {
            new AlertDialog.Builder(this)
                    .setTitle("SOS 중지")
                    .setMessage("SOS 긴급 모드를 중지하시겠습니까?")
                    .setPositiveButton("중지", (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=5");
                        Log.v("SOS", "LOCATION=5 (SOS 중지 요청)");
                    })
                    .setNegativeButton("취소", null)
                    .show();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("🚨 SOS Emergency")
                    .setMessage("SOS 긴급 신호를 전송하시겠습니까?\n\n3분 주기로 전송됩니다.")
                    .setPositiveButton("전송", (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=4");
                        Log.v("SOS", "LOCATION=4 (SOS 시작 요청)");
                    })
                    .setNegativeButton("취소", null)
                    .show();
        }
    }

    private void onExitButtonClick() {
        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("TYTO Connect 를 종료하시겠습니까?")
                .setPositiveButton("종료", (d, w) -> {
                    Log.v("EXIT", "사용자가 앱 종료 선택");
                    finishAndRemoveTask();
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void updateTrackButtonUI(boolean isActive) {
        mIsTrackingMode = isActive;
        if (isActive) {
            binding.headerArea.btnTrackHeader.setBackgroundResource(R.drawable.bg_track_button_active);
            binding.headerArea.textTrackLabel.setTextColor(0xFF0A1628);
        } else {
            binding.headerArea.btnTrackHeader.setBackgroundResource(R.drawable.bg_track_button);
            binding.headerArea.textTrackLabel.setTextColor(0xFF00E5D1);
        }
    }

    private void updateSosButtonUI(boolean isActive) {
        mIsSosMode = isActive;
        if (isActive) {
            binding.headerArea.btnSosHeader.setBackgroundResource(R.drawable.bg_sos_button_active);
            binding.headerArea.textSosLabel.setTextColor(0xFFFFFFFF);
        } else {
            binding.headerArea.btnSosHeader.setBackgroundResource(R.drawable.bg_sos_button);
            binding.headerArea.textSosLabel.setTextColor(0xFFE24B4A);
        }
    }


    // ═════════════════════════════════════════════════════════════
    //   ⭐ v4 Phase 3A: Satellite TRACK 세션 자동 동기화
    // ═════════════════════════════════════════════════════════════
    //
    // 원칙: 헤더 TRACK/SOS 상태 = Satellite TRACK 세션 상태
    //
    //   tracking=false, sos=false  → IDLE (세션 없음)
    //   tracking=true              → TRACK 세션
    //   sos=true                   → SOS 세션 (TRACK보다 우선)
    //
    // BROAD 수신 시마다 호출되며, 상태 변화 시만 실제 전환
    // ═════════════════════════════════════════════════════════════

    // 세션 이전 상태 추적 (변화 감지용)
    private boolean mPrevSatSessionActive = false;
    private int mPrevSatSessionMode = 0; // 0=IDLE, 1=TRACK, 2=SOS

    private void syncSatTrackSession(boolean isTracking, boolean isSos) {
        // 현재 세션 모드 결정
        int currentMode;
        if (isSos) currentMode = 2;        // SOS (우선)
        else if (isTracking) currentMode = 1; // TRACK
        else currentMode = 0;               // IDLE

        boolean currentActive = (currentMode != 0);

        // 상태 변화 없으면 skip (중복 호출 방지)
        if (currentMode == mPrevSatSessionMode) {
            return;
        }

        Log.v("SAT-SESSION", "상태 변화 감지: " +
                modeToString(mPrevSatSessionMode) + " → " + modeToString(currentMode));

        // 변화 타입 판별
        if (!mPrevSatSessionActive && currentActive) {
            // IDLE → TRACK/SOS : 세션 시작
            Log.v("SAT-SESSION", "⭐ Satellite TRACK 세션 시작 (" + modeToString(currentMode) + ")");
            startSatTrackSession(currentMode);

        } else if (mPrevSatSessionActive && !currentActive) {
            // TRACK/SOS → IDLE : 세션 종료
            Log.v("SAT-SESSION", "⏹ Satellite TRACK 세션 종료 요청 (" +
                    modeToString(mPrevSatSessionMode) + " → IDLE)");
            stopSatTrackSession(mPrevSatSessionMode);

        } else if (mPrevSatSessionActive && currentActive) {
            // TRACK ↔ SOS : 모드 전환 (세션은 유지)
            Log.v("SAT-SESSION", "세션 모드 전환: " +
                    modeToString(mPrevSatSessionMode) + " → " + modeToString(currentMode));
        }

        mPrevSatSessionActive = currentActive;
        mPrevSatSessionMode = currentMode;
    }

    private String modeToString(int mode) {
        switch (mode) {
            case 1: return "TRACK";
            case 2: return "SOS";
            default: return "IDLE";
        }
    }

    /**
     * 세션 시작 요청:
     * DevicesTabFragment를 찾아 직접 호출
     */
    private void startSatTrackSession(int mode) {
        runOnUiThread(() -> {
            try {
                // DevicesTabFragment 찾기
                androidx.fragment.app.Fragment devicesFragment =
                        findDevicesTabFragment();
                if (devicesFragment != null) {
                    // reflection으로 메서드 호출
                    java.lang.reflect.Method m = devicesFragment.getClass().getMethod(
                            "startSatSessionFromHeader", int.class);
                    m.invoke(devicesFragment, mode);
                    Log.v("SAT-SESSION", "✅ DevicesTabFragment에 시작 신호 전달");
                } else {
                    Log.v("SAT-SESSION", "⚠ DevicesTabFragment 없음 (탭 미방문 상태)");
                }
            } catch (Exception e) {
                Log.v("SAT-SESSION", "세션 시작 호출 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 세션 종료 요청:
     * DevicesTabFragment가 저장 다이얼로그 표시
     */
    private void stopSatTrackSession(int prevMode) {
        runOnUiThread(() -> {
            try {
                androidx.fragment.app.Fragment devicesFragment =
                        findDevicesTabFragment();
                if (devicesFragment != null) {
                    java.lang.reflect.Method m = devicesFragment.getClass().getMethod(
                            "stopSatSessionFromHeader", int.class);
                    m.invoke(devicesFragment, prevMode);
                    Log.v("SAT-SESSION", "✅ DevicesTabFragment에 종료 신호 전달");
                } else {
                    Log.v("SAT-SESSION", "⚠ DevicesTabFragment 없음");
                }
            } catch (Exception e) {
                Log.v("SAT-SESSION", "세션 종료 호출 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 현재 표시 중인 DevicesTabFragment 찾기
     */
    private androidx.fragment.app.Fragment findDevicesTabFragment() {
        try {
            androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
            for (androidx.fragment.app.Fragment f : fm.getFragments()) {
                if (f == null) continue;
                if (f.getClass().getSimpleName().equals("DevicesTabFragment")) {
                    return f;
                }
                // 중첩 탐색 (NavHost 등)
                if (f.getChildFragmentManager() != null) {
                    for (androidx.fragment.app.Fragment child : f.getChildFragmentManager().getFragments()) {
                        if (child != null &&
                                child.getClass().getSimpleName().equals("DevicesTabFragment")) {
                            return child;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.v("SAT-SESSION", "Fragment 찾기 실패: " + e.getMessage());
        }
        return null;
    }


    // ═════════════════════════════════════════════════════════════
    //   v2 재연결 UI (자동 재접속 메커니즘 연동)
    // ═════════════════════════════════════════════════════════════

    private void setupReconnectUI() {
        BLE.INSTANCE.getConnectionStatus().observe(this, status -> {
            if (status == null || mIsTestMode) return;

            Log.v("BLE-UI", "ConnectionStatus changed: " + status);

            switch (status) {
                case BLE.CONNECT_STATUS_RECONNECTING: {
                    int attempts = BLE.INSTANCE.getReconnectAttempts();
                    int max = BLE.INSTANCE.getMaxReconnectAttempts();

                    binding.statusArea.textBleStatusMain.setText("Reconnecting");
                    binding.statusArea.textBleStatusMain.setTextColor(0xFFFFB300);
                    binding.statusArea.imgStatusBle.setColorFilter(0xFFFFB300);

                    binding.statusArea.textReconnectCount.setText(
                            String.format("(%d/%d)", attempts, max)
                    );
                    binding.statusArea.textReconnectCount.setVisibility(View.VISIBLE);

                    binding.statusArea.btnConnectionAction.setText("취소");
                    binding.statusArea.btnConnectionAction.setVisibility(View.VISIBLE);
                    break;
                }

                case BLE.CONNECT_STATUS_FAILED: {
                    binding.statusArea.textBleStatusMain.setText("Failed");
                    binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
                    binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);

                    binding.statusArea.textReconnectCount.setVisibility(View.GONE);

                    binding.statusArea.btnConnectionAction.setText("재시도");
                    binding.statusArea.btnConnectionAction.setVisibility(View.VISIBLE);
                    break;
                }

                case BLE.CONNECT_STATUS_CONNECTED:
                case BLE.CONNECT_STATUS_DISCONNECTED:
                case BLE.CONNECT_STATUS_LOST:
                case BLE.CONNECT_STATUS_TRYING:
                default: {
                    binding.statusArea.textReconnectCount.setVisibility(View.GONE);
                    binding.statusArea.btnConnectionAction.setVisibility(View.GONE);
                    break;
                }
            }
        });

        binding.statusArea.btnConnectionAction.setOnClickListener(v -> {
            String currentStatus = BLE.INSTANCE.getConnectionStatus().getValue();
            Log.v("BLE-UI", "Action button clicked, status: " + currentStatus);

            if (BLE.CONNECT_STATUS_RECONNECTING.equals(currentStatus)) {
                BLE.INSTANCE.cancelReconnect();
                Toast.makeText(this, "재연결을 취소했습니다.", Toast.LENGTH_SHORT).show();
            } else if (BLE.CONNECT_STATUS_FAILED.equals(currentStatus)) {
                Log.v("BLE-UI", "Retry → Reset selectedDevice & navigate to BLE tab");

                BLE.INSTANCE.getSelectedDevice().postValue(null);

                binding.statusArea.textReconnectCount.setVisibility(View.GONE);
                binding.statusArea.btnConnectionAction.setVisibility(View.GONE);

                binding.bottomNav.setSelectedItemId(R.id.tab_ble);

                Toast.makeText(this,
                        "장비를 다시 검색합니다. TYTO2 전원을 확인해주세요.",
                        Toast.LENGTH_LONG).show();
            }
        });
    }


    // ═════════════════════════════════════════════════════════════
    //   주기 동기화
    // ═════════════════════════════════════════════════════════════

    private void startPeriodicSync() {
        Log.v("SYNC", "▶ Starting periodic sync");
        mLastBroadReceivedTime = System.currentTimeMillis();
        mLastInfoReceivedTime = System.currentTimeMillis();

        BLE.INSTANCE.getWriteQueue().offer("BROAD=5");

        mSyncHandler.removeCallbacksAndMessages(null);
        mSyncHandler.postDelayed(mPeriodicSyncRunnable, PERIODIC_SYNC_MS);

        if (mBroadRetryRunnable == null) {
            mBroadRetryRunnable = new Runnable() {
                @Override
                public void run() {
                    long elapsed = System.currentTimeMillis() - mLastBroadReceivedTime;
                    if (elapsed >= BROAD_TIMEOUT_MS && BLE.INSTANCE.getSelectedDevice().getValue() != null) {
                        Log.v("SYNC", "⚠ BROAD timeout (" + elapsed + "ms) - re-requesting BROAD=5");
                        BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
                    }
                    mSyncHandler.postDelayed(this, BROAD_TIMEOUT_MS);
                }
            };
        }
        mSyncHandler.postDelayed(mBroadRetryRunnable, BROAD_TIMEOUT_MS);

        if (mInfoRetryRunnable == null) {
            mInfoRetryRunnable = new Runnable() {
                @Override
                public void run() {
                    long elapsed = System.currentTimeMillis() - mLastInfoReceivedTime;
                    DeviceInfo currentInfo = BLE.INSTANCE.getDeviceInfo().getValue();
                    boolean noImei = currentInfo == null || currentInfo.getImei() == null || currentInfo.getImei().isEmpty();

                    if (noImei && BLE.INSTANCE.getSelectedDevice().getValue() != null) {
                        Log.v("SYNC", "⚠ INFO not received - re-requesting INFO=?");
                        BLE.INSTANCE.getWriteQueue().offer("INFO=?");
                        mSyncHandler.postDelayed(this, INFO_TIMEOUT_MS);
                    }
                }
            };
        }
        mSyncHandler.postDelayed(mInfoRetryRunnable, INFO_TIMEOUT_MS);
    }

    private final Runnable mPeriodicSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Log.v("SYNC", "■ Device disconnected - stopping periodic sync");
                return;
            }

            long broadElapsed = System.currentTimeMillis() - mLastBroadReceivedTime;
            Log.v("SYNC", "▶ Periodic check - BROAD last received " + broadElapsed + "ms ago");

            if (broadElapsed >= BROAD_TIMEOUT_MS) {
                Log.v("SYNC", "⚠ BROAD stale - re-requesting BROAD=5");
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            }

            mSyncHandler.postDelayed(this, PERIODIC_SYNC_MS);
        }
    };

    private void stopPeriodicSync() {
        Log.v("SYNC", "■ Stopping all sync handlers");
        if (mSyncHandler != null) {
            mSyncHandler.removeCallbacksAndMessages(null);
        }
    }


    // ═════════════════════════════════════════════════════════════
    //   Fixed Header / Status Card
    // ═════════════════════════════════════════════════════════════

    private void setupFixedHeaderObservers() {
        BLE.INSTANCE.getDeviceInfo().observe(this, info -> {
            Log.v("OBS-INFO", "DeviceInfo observer fired: " + (info != null ? info.getImei() : "null"));

            if (info != null && info.getImei() != null && !info.getImei().isEmpty()) {
                binding.headerArea.textHeaderSub.setText("IMEI  " + info.getImei());
                ImeiStorage.save(this, info.getImei());
            } else {
                binding.headerArea.textHeaderSub.setText("IMEI  -");
            }

            if (info != null) {
                updateTrackButtonUI(info.isTrackingMode());
                updateSosButtonUI(info.isSosStarted());

                if (info.isSosStarted()) updateLocationStatus(2);
                else if (info.isTrackingMode()) updateLocationStatus(1);
                else updateLocationStatus(0);
            }
        });

        BLE.INSTANCE.getSelectedDevice().observe(this, device -> {
            if (device != null) {
                mIsTestMode = false;
                binding.statusArea.textBleStatusMain.setText("Connected");
                binding.statusArea.textBleStatusMain.setTextColor(0xFF00E5D1);
                binding.statusArea.imgStatusBle.setColorFilter(0xFF00E5D1);

                binding.statusArea.textReconnectCount.setVisibility(View.GONE);
                binding.statusArea.btnConnectionAction.setVisibility(View.GONE);

                startPeriodicSync();
            } else {
                if (mIsTestMode) return;

                String status = BLE.INSTANCE.getConnectionStatus().getValue();
                if (BLE.CONNECT_STATUS_RECONNECTING.equals(status) ||
                    BLE.CONNECT_STATUS_FAILED.equals(status)) {
                    return;
                }

                binding.statusArea.textBleStatusMain.setText("Disconnected");
                binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
                binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
                binding.statusArea.textMainBattery.setText("- %");
                binding.statusArea.textMainInbox.setText("0");
                binding.statusArea.textMainOutbox.setText("0");
                binding.headerArea.textHeaderSub.setText("IMEI  -");
                updateSignalBar(0);
                updateLocationStatus(0);

                updateTrackButtonUI(false);
                updateSosButtonUI(false);

                stopPeriodicSync();
            }
        });

        mBleViewModel.getDeviceStatus().observe(this, status -> {
            Log.v("OBS-STATUS", "DeviceStatus observer fired: " +
                    (status != null ? ("battery=" + status.getBattery() + " signal=" + status.getSignal()) : "null"));

            if (status == null) return;
            binding.statusArea.textMainBattery.setText(String.format("%d%%", status.getBattery()));
            updateSignalBar(status.getSignal());
            binding.statusArea.textMainInbox.setText(String.valueOf(status.getInBox()));
            binding.statusArea.textMainOutbox.setText(String.valueOf(status.getOutBox()));

            updateTrackButtonUI(status.isTrackingMode());
            updateSosButtonUI(status.isSosMode());

            if (status.isSosMode()) updateLocationStatus(2);
            else if (status.isTrackingMode()) updateLocationStatus(1);
            else updateLocationStatus(0);

            // ⭐⭐⭐ v4 Phase 3A: 헤더 TRACK/SOS ↔ Satellite TRACK 세션 자동 동기화
            // BROAD의 tracking/sos 상태로 세션 자동 시작/종료
            syncSatTrackSession(status.isTrackingMode(), status.isSosMode());
        });

        msgViewModel.getAllMsgs().observe(this, allMsgs -> {
            if (allMsgs == null) return;
            int unsent = 0;
            for (MsgEntity m : allMsgs) {
                if (m.isSendMsg() && !m.isSend()) unsent++;
            }
            if (unsent > 0) {
                binding.statusArea.textMainUnsent.setVisibility(View.VISIBLE);
                binding.statusArea.textMainUnsent.setText("(" + unsent + ")");
            } else {
                binding.statusArea.textMainUnsent.setVisibility(View.GONE);
            }
        });

        binding.headerArea.textHeaderTitle.setOnClickListener(v -> {
            mTestTapCount++;
            if (mTestTapCount >= 5) {
                mTestTapCount = 0;
                toggleTestMode();
            }
        });
    }

    private void updateSignalBar(int signal) {
        View[] bars = {
                binding.statusArea.sigBar1, binding.statusArea.sigBar2,
                binding.statusArea.sigBar3, binding.statusArea.sigBar4,
                binding.statusArea.sigBar5
        };
        int active = 0xFF378ADD, inactive = 0xFF1E3A5F;
        if (signal <= 0) {
            binding.statusArea.sigNoSignal.setVisibility(View.VISIBLE);
            for (View b : bars) {
                b.setBackgroundColor(0xFF2A1A1A);
                b.setAlpha(0.4f);
            }
        } else {
            binding.statusArea.sigNoSignal.setVisibility(View.GONE);
            for (int i = 0; i < bars.length; i++) {
                bars[i].setAlpha(1.0f);
                bars[i].setBackgroundColor(i < signal ? active : inactive);
            }
        }
    }

    private void updateLocationStatus(int state) {
        switch (state) {
            case 1:
                binding.statusArea.textLocationStatusLabel.setText("TRACKING");
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFF00E5D1);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFF00E5D1);
                break;
            case 2:
                binding.statusArea.textLocationStatusLabel.setText("SOS");
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFFFF5252);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFFFF5252);
                break;
            default:
                binding.statusArea.textLocationStatusLabel.setText("OFF");
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFF95B0D4);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFF95B0D4);
                break;
        }
    }

    private void toggleTestMode() {
        if (mIsTestMode) {
            mIsTestMode = false;
            binding.statusArea.textBleStatusMain.setText("Disconnected");
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
            binding.statusArea.textMainBattery.setText("- %");
            binding.statusArea.textMainInbox.setText("0");
            binding.statusArea.textMainOutbox.setText("0");
            binding.headerArea.textHeaderSub.setText("IMEI  -");
            updateSignalBar(0);
            updateLocationStatus(0);
            updateTrackButtonUI(false);
            updateSosButtonUI(false);

            deleteTestLocationData();
            deleteTestMessages();
            Toast.makeText(this, "🧪 Test mode OFF (data cleared)", Toast.LENGTH_SHORT).show();
        } else {
            mIsTestMode = true;
            DeviceStatus test = new DeviceStatus();
            test.setBattery(85);
            test.setSignal(3);
            test.setInBox(10);
            test.setOutBox(1);
            test.setTrackingMode(true);
            test.setSosMode(false);
            mBleViewModel.getDeviceStatus().postValue(test);
            binding.statusArea.textBleStatusMain.setText("Test Mode");
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFFB300);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFFB300);
            binding.headerArea.textHeaderSub.setText("IMEI  300434061000001");

            ImeiStorage.save(this, "300434061000001");

            insertTestLocationData();
            insertTestMessages();
            Toast.makeText(this, "🧪 Test data injected (20 locations + 10 messages)",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void insertTestLocationData() {
        double[][] trackCoords = {
                {37.5264, 126.8960}, {37.5270, 126.8975}, {37.5280, 126.8990},
                {37.5290, 126.9005}, {37.5300, 126.9020}, {37.5310, 126.9035},
                {37.5305, 126.9050}, {37.5295, 126.9060}, {37.5285, 126.9055},
                {37.5275, 126.9050}
        };
        double[][] sosCoords = {
                {37.5240, 126.8930}, {37.5245, 126.8935}, {37.5235, 126.8925},
                {37.5242, 126.8940}, {37.5238, 126.8928}, {37.5244, 126.8932},
                {37.5236, 126.8938}, {37.5241, 126.8926}, {37.5239, 126.8936},
                {37.5243, 126.8929}
        };

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();

        for (int i = 0; i < trackCoords.length; i++) {
            cal.setTime(now);
            cal.add(Calendar.MINUTE, -(10 * (trackCoords.length - i)));
            Date pastTime = cal.getTime();

            LocationEntity entity = new LocationEntity(
                    0, false, 2, TEST_IMEI_TRACK,
                    trackCoords[i][0], trackCoords[i][1],
                    10 + i * 5, 45 + i * 10, 15 + i,
                    pastTime, pastTime,
                    false, false, false
            );
            locationViewModel.insert(entity);
        }

        for (int i = 0; i < sosCoords.length; i++) {
            cal.setTime(now);
            cal.add(Calendar.MINUTE, -(5 * (sosCoords.length - i) + 5));
            Date pastTime = cal.getTime();

            LocationEntity entity = new LocationEntity(
                    0, false, 4, TEST_IMEI_SOS,
                    sosCoords[i][0], sosCoords[i][1],
                    0, 0, 0,
                    pastTime, pastTime,
                    false, false, false
            );
            locationViewModel.insert(entity);
        }

        Log.v("TEST_MODE", "Inserted 20 test locations");
    }


    private void deleteTestLocationData() {
        locationViewModel.getAllLocations().observe(this, new androidx.lifecycle.Observer<List<LocationEntity>>() {
            @Override
            public void onChanged(List<LocationEntity> allLocations) {
                if (allLocations == null) return;
                locationViewModel.getAllLocations().removeObserver(this);

                int deleted = 0;
                for (LocationEntity loc : allLocations) {
                    if (TEST_IMEI_TRACK.equals(loc.getCodeNum())
                            || TEST_IMEI_SOS.equals(loc.getCodeNum())) {
                        locationViewModel.delete(loc);
                        deleted++;
                    }
                }
                Log.v("TEST_MODE", "Deleted " + deleted + " test locations");
            }
        });
    }

    private void insertTestMessages() {
        String[][] testMsgs = {
                {"안녕하세요",         "오늘도 좋은 하루 보내세요! 😊"},
                {"날씨 확인",           "서울 현재 맑음, 기온 15도입니다."},
                {"미팅 변경 안내",      "내일 오후 2시 미팅이 3시로 변경되었습니다. 확인 부탁드려요."},
                {"Status OK",          "Base camp check-in at 10:30. All systems green."},
                {"복귀 예정",           "오늘 18시경 베이스캠프 복귀 예정입니다."},
                {"좌표 수신 확인",      "전송해주신 GPS 좌표 정상 수신했습니다."},
                {"Weather Alert",      "Heavy rain expected in your area after 16:00. Stay safe."},
                {"저녁 식사",           "7시에 저녁 준비 완료됩니다. 시간 맞춰 오세요."},
                {"Signal Test",        "통신 테스트 - 신호 양호, 응답 부탁드립니다."},
                {"작전 브리핑",         "내일 08시 작전 브리핑 예정. 참석 필수입니다."}
        };

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();

        int[] hoursBack = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12};

        for (int i = 0; i < testMsgs.length; i++) {
            cal.setTime(now);
            cal.add(Calendar.HOUR_OF_DAY, -hoursBack[i]);
            cal.add(Calendar.MINUTE, -(i * 7));
            Date sentTime = cal.getTime();

            boolean isRead = i >= 3;

            MsgEntity msg = new MsgEntity(
                    0, false, TEST_IMEI_MSG,
                    testMsgs[i][0], testMsgs[i][1],
                    sentTime, sentTime, sentTime,
                    isRead, false, false
            );
            msgViewModel.insert(msg, success -> {
                if (success) Log.v("TEST_MSG", "Test message " + (testMsgs.length) + " saved");
                return null;
            });
        }

        Log.v("TEST_MODE", "Inserted 10 test messages from " + TEST_IMEI_MSG);
    }


    private void deleteTestMessages() {
        msgViewModel.getAllMsgs().observe(this, new androidx.lifecycle.Observer<List<MsgEntity>>() {
            @Override
            public void onChanged(List<MsgEntity> allMsgs) {
                if (allMsgs == null) return;
                msgViewModel.getAllMsgs().removeObserver(this);

                int deleted = 0;
                for (MsgEntity m : allMsgs) {
                    if (TEST_IMEI_MSG.equals(m.getCodeNum())) {
                        msgViewModel.delete(m);
                        deleted++;
                    }
                }
                Log.v("TEST_MODE", "Deleted " + deleted + " test messages");
            }
        });
    }


    // ═════════════════════════════════════════════════════════════
    //   Bottom Tabs
    // ═════════════════════════════════════════════════════════════

    private void setupBottomTabs() {
        if (getSupportFragmentManager().findFragmentById(R.id.tab_container) == null) {
            switchTab(new MapTabFragment());
            binding.bottomNav.setSelectedItemId(R.id.tab_map);
        }

        binding.bottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment f = null;
            if (id == R.id.tab_chat)          f = new ChatTabFragment();
            else if (id == R.id.tab_devices)  f = new DevicesTabFragment();
            else if (id == R.id.tab_map)      f = new MapTabFragment();
            else if (id == R.id.tab_settings) f = new SettingsTabFragment();
            else if (id == R.id.tab_ble)      f = new BleTabFragment();
            if (f == null) return false;
            switchTab(f);
            return true;
        });
    }

    private void switchTab(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.tab_container, fragment)
                .commitAllowingStateLoss();
    }


    // ═════════════════════════════════════════════════════════════
    //   뒤로가기 가드
    // ═════════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        Fragment currentTab = getSupportFragmentManager().findFragmentById(R.id.tab_container);
        if (currentTab != null && currentTab.getChildFragmentManager().getBackStackEntryCount() > 0) {
            currentTab.getChildFragmentManager().popBackStack();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("앱 종료")
                .setMessage("TYTO Connect 를 종료하시겠습니까?")
                .setPositiveButton("종료", (d, w) -> super.onBackPressed())
                .setNegativeButton("취소", null)
                .show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSION_ACCESS_COARSE_LOCATION:
            case PERMISSION_ACCESS_FINE_LOCATION:
            case PERMISSION_BLUETOOTH_ADVERTISE:
            case PERMISSION_BLUETOOTH_CONNECT:
            case PERMISSION_BLUETOOTH_SCAN: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.d("debug", "coarse location permission granted");
                }
                break;
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPeriodicSync();
        resetAutoReceive();
        // ⭐ v4 Phase B-2-1: destroyBle() 제거!
        // BLE는 이제 TytoConnectService가 관리
        // Activity 종료해도 BLE 연결 유지됨 (백그라운드 작동)
        // BLE.INSTANCE.destroyBle();  ← 제거

        // ⭐ v4 Phase B-2-4-B: Service에 Activity 종료 알림
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // ⭐ v4 Phase B-2-4-B: Activity 활성 상태 알림
        // Service는 Activity가 살아있으면 저장 skip (중복 방지)
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // ⭐ v4 Phase B-2-4-B: Activity 비활성 상태 알림
        // 백그라운드 진입 → Service가 저장 담당
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(false);
    }

    boolean checkExternalStorage() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return false;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        } else {
            return false;
        }
    }


    // ═════════════════════════════════════════════════════════════
    //   위치 파싱 헬퍼 메서드
    // ═════════════════════════════════════════════════════════════

    private String parseAddress(ByteBuf buffer, int senderLen) {
        if (senderLen == 5) {
            byte senderF = buffer.readByte();
            int senderB = buffer.readInt();
            return String.format("%d%09d", senderF, senderB);
        } else if (senderLen == 8) {
            int senderF = buffer.readInt();
            int senderB = buffer.readInt();
            return String.format("%08d%07d", senderF, senderB);
        }
        return null;
    }


    public void receivePacketProcess(String packet) throws Exception {

        Log.v("RECEVICE", packet);

        if (packet.startsWith("INFO=")) {
            String msg = packet.substring(5);
            String[] vals = msg.split(",");
            DeviceInfo info = new DeviceInfo();
            info.setSerialNum(vals[0]);
            info.setBudaeNum(vals[1]);
            info.setImei(vals[2]);
            info.setVersion(vals[3]);

            info.setPwChanged(!vals[8].equals("0"));
            if (vals.length > 9) info.setSosStarted(!vals[9].equals("0"));
            if (vals.length > 10) info.setTrackingMode((!vals[10].equals("0")));

            BLE.INSTANCE.getDeviceInfo().postValue(info);
            mLastInfoReceivedTime = System.currentTimeMillis();
            Log.v("SYNC", "✓ INFO received - imei=" + info.getImei());

            if (info.isPwChanged()) {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_TRY);
            } else {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_CHANGE_TRY);
            }
        } else if (packet.startsWith("LOGIN=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
            if (vals[0].equals("FAIL")) {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_FAIL);
            } else {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_OK);
                BLE.INSTANCE.isLogon().postValue(true);
                mSyncHandler.postDelayed(() -> {
                    BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
                    Log.v("SYNC", "▶ Post-login BROAD=5 sent");
                }, 500);
            }
        } else if (packet.startsWith("CHANGELOGIN=")) {
            String msg = packet.substring(12);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_CHANGE_OK);
                BLE.INSTANCE.isLogon().postValue(true);
                mSyncHandler.postDelayed(() -> {
                    BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
                    Log.v("SYNC", "▶ Post-changelogin BROAD=5 sent");
                }, 500);
            } else {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_CHANGE_FAIL);
            }
        } else if (packet.startsWith("UOPEN=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
            int size = Integer.parseInt(vals[0]);
            if (vals[1].equals("START")) {
                FirmUpdate state = new FirmUpdate(1, "START");
                BLE.INSTANCE.getFirmwareUdateState().postValue(state);
            }
        } else if (packet.startsWith("UFILE=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
            int idx = Integer.parseInt(vals[0]);
            if (vals[1].equals("OK")) {
                FirmUpdate state = new FirmUpdate(idx, "NEXT");
                BLE.INSTANCE.getFirmwareUdateState().postValue(state);
            } else if (vals[1].equals("FAIL")) {
                if (vals[2].equals("0")) {
                    FirmUpdate state = new FirmUpdate(idx, "FAILEND");
                    BLE.INSTANCE.getFirmwareUdateState().postValue(state);
                } else {
                    FirmUpdate state = new FirmUpdate(idx, "RESEND");
                    BLE.INSTANCE.getFirmwareUdateState().postValue(state);
                }
            } else if (vals[1].equals("END")) {
                FirmUpdate state = new FirmUpdate(idx, "END");
                BLE.INSTANCE.getFirmwareUdateState().postValue(state);
            }
        } else if (packet.startsWith("SET=")) {
            String msg = packet.substring(4);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                Toast.makeText(this, "Change successful.", Toast.LENGTH_LONG).show();
            } else if (vals[0].equals("FAIL")) {
                Toast.makeText(this, "Change failed.", Toast.LENGTH_LONG).show();
            } else {
                BLE.INSTANCE.getDeviceSet().postValue(packet);
            }
        } else if (packet.startsWith("LOCATION=")) {
            String msg = packet.substring(9);
            String[] vals = msg.split(",");
            if (vals[0].equals("1")) Toast.makeText(this, "A single location request has been sent to the terminal.", Toast.LENGTH_LONG).show();
            if (vals[0].equals("2")) Toast.makeText(this, "The terminal was told to start tracking mode.", Toast.LENGTH_LONG).show();
            if (vals[0].equals("3")) Toast.makeText(this, "The terminal has been told to stop tracking mode.", Toast.LENGTH_LONG).show();
            if (vals[0].equals("4")) Toast.makeText(this, "The terminal was told to start SOS mode.", Toast.LENGTH_LONG).show();
            if (vals[0].equals("5")) Toast.makeText(this, "The terminal was told to stop SOS mode.", Toast.LENGTH_LONG).show();
        } else if (packet.startsWith("SENDING=")) {
            String msg = packet.substring(8);
            String[] vals = msg.split(",");
            if (vals[1].equals("OK")) {
                BLE.INSTANCE.getOutboxMsgStatus().postValue(packet);
            }
        } else if (packet.startsWith("DEVICESEND=")) {
            String msg = packet.substring(11);
            String[] vals = msg.split(",");
            if (vals[1].equals("OK")) {
                int id = Integer.parseInt(vals[0]);
            }
        } else if (packet.startsWith("RECEIVED=")) {
            String sms = packet.substring(9);
            String[] vals = sms.split(",");
            try {
                Log.v("RECEIVE", "number of remaining  : " + vals[1]);
                if (vals[1].equals("0")) {
                    Toast.makeText(this, getString(R.string.inbox_receive_complite), Toast.LENGTH_LONG).show();
                    completeAutoReceive();  // ⭐ 자동 받기 완료
                    return;
                }
                byte[] data = Base64.decode(vals[2], Base64.NO_WRAP);
                Log.v("RECEIVE-HEX", HexUtil.formatHexString(data));
                ByteBuf buffer = Unpooled.wrappedBuffer(data);
                byte ver = buffer.getByte(0);

                // CAR / SOS 모드
                if (ver == 0x00 || ver == 0x01) {
                    buffer.readByte();
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    String myImei = ImeiStorage.getLast(this);
                    Date now = new Date();
                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            myImei, lat, lng, 0, 0, 0, now,
                            now, false, false, false);
                    Log.v("LOC SEND " + String.format("0x%02X", ver),
                            "myImei=" + myImei + " lat=" + lat + " lng=" + lng);
                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            Log.v("Location ADD", "내 위치 저장 (" + String.format("0x%02X", ver) + ")");

                            // ⭐ v4 Phase B-2-5: Service에 저장 완료 알림
                            com.ah.acr.messagebox.service.TytoConnectService
                                    .notifyPointSavedByActivity(MainActivity.this);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, 0.0, 0.0, 0.0, null, ver
                    );

                } else if (ver == 0x11 || ver == 0x10) {
                    int senderLen = buffer.readableBytes() - 10;
                    buffer.readByte();
                    String sender = parseAddress(buffer, senderLen);
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    Date now = new Date();

                    // ⭐ v4 Phase 5-B: Echo back 판별
                    // sender가 내 IMEI와 같으면 "내가 발송한 것의 echo back"
                    // → isMy=true로 저장 (실제로 내 위치임)
                    String myImei = ImeiStorage.getLast(this);
                    boolean isMyEcho = sender != null
                            && myImei != null
                            && sender.equals(myImei);

                    LocationEntity addLoc = new LocationEntity(
                            0,
                            isMyEcho,      // ⭐ Echo면 isMy=true, 아니면 false
                            ver,
                            sender,        // sender 주소 (내 IMEI 또는 상대방)
                            lat, lng,
                            0, 0, 0,
                            now,           // sendDate = 앱 수신 시간 (new Date)
                            now,           // recvDate = 앱 수신 시간
                            false, false, false);

                    if (isMyEcho) {
                        Log.v("LOC ECHO " + String.format("0x%02X", ver),
                                "⭐ 내 Echo back! sender=" + sender +
                                " lat=" + lat + " lng=" + lng);
                    } else {
                        Log.v("LOC RECV " + String.format("0x%02X", ver),
                                "상대방 수신 sender=" + sender +
                                " lat=" + lat + " lng=" + lng);
                    }

                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            String label = isMyEcho ? "내 Echo 저장" : "수신 위치 저장";
                            Log.v("Location ADD",
                                    label + " (" + String.format("0x%02X", ver) + ")");

                            // ⭐ v4 Phase B-2-5: Service에 저장 완료 알림
                            // Notification의 pt 카운트 동기화
                            com.ah.acr.messagebox.service.TytoConnectService
                                    .notifyPointSavedByActivity(MainActivity.this);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, 0.0, 0.0, 0.0, null, ver
                    );

                }
                // UAV 모드
                else if (ver == 0x02) {
                    buffer.readByte();
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    String myImei = ImeiStorage.getLast(this);
                    Date now = new Date();
                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            myImei, lat, lng, alt, dir, speed, now,
                            now, false, false, false);
                    Log.v("LOC SEND 0x02", "UAV myImei=" + myImei + " lat=" + lat);
                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "내 UAV 위치 저장");
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, null, ver
                    );

                } else if (ver == 0x12) {
                    int senderLen = buffer.readableBytes() - 14;
                    buffer.readByte();
                    String sender = parseAddress(buffer, senderLen);
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    Date now = new Date();

                    // ⭐ v4 Phase 5-B: UAV Echo back 판별
                    String myImeiUav = ImeiStorage.getLast(this);
                    boolean isMyEchoUav = sender != null
                            && myImeiUav != null
                            && sender.equals(myImeiUav);

                    LocationEntity addLoc = new LocationEntity(
                            0,
                            isMyEchoUav,    // ⭐ Echo면 isMy=true
                            ver,
                            sender,
                            lat, lng,
                            alt, dir, speed,
                            now, now,
                            false, false, false);

                    if (isMyEchoUav) {
                        Log.v("LOC ECHO 0x12",
                                "⭐ 내 UAV Echo! sender=" + sender + " lat=" + lat);
                    } else {
                        Log.v("LOC RECV 0x12",
                                "UAV sender=" + sender + " lat=" + lat);
                    }

                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            String label = isMyEchoUav ? "내 UAV Echo 저장" : "수신 UAV 위치 저장";
                            Log.v("Location ADD", label);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, null, ver
                    );

                }
                // UAT 모드
                else if (ver == 0x03) {
                    buffer.readByte();
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    int year = buffer.readShort();
                    int mon = buffer.readUnsignedByte();
                    int day = buffer.readUnsignedByte();
                    int hour = buffer.readUnsignedByte();
                    int min = buffer.readUnsignedByte();
                    int sec = buffer.readUnsignedByte();

                    LocalDateTime ldt = LocalDateTime.of(year, mon, day, hour, min, sec);
                    ZonedDateTime zdtUtc = ldt.atZone(ZoneId.of("UTC"));
                    Date date = Date.from(zdtUtc.toInstant());

                    String myImei = ImeiStorage.getLast(this);
                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            myImei, lat, lng, alt, dir, speed, date,
                            new Date(), false, false, false);
                    Log.v("LOC SEND 0x03", "UAT myImei=" + myImei + " lat=" + lat);
                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "내 UAT 위치 저장");
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, date, ver
                    );

                } else if (ver == 0x13) {
                    int senderLen = buffer.readableBytes() - 21;
                    buffer.readByte();
                    String sender = parseAddress(buffer, senderLen);
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    int year = buffer.readShort();
                    int mon = buffer.readUnsignedByte();
                    int day = buffer.readUnsignedByte();
                    int hour = buffer.readUnsignedByte();
                    int min = buffer.readUnsignedByte();
                    int sec = buffer.readUnsignedByte();

                    // ⭐ UAT은 GPS 시간 사용 (펌웨어 제공)
                    LocalDateTime ldt = LocalDateTime.of(year, mon, day, hour, min, sec);
                    ZonedDateTime zdtUtc = ldt.atZone(ZoneId.of("UTC"));
                    Date date = Date.from(zdtUtc.toInstant());

                    // ⭐ v4 Phase 5-B: UAT Echo back 판별
                    String myImeiUat = ImeiStorage.getLast(this);
                    boolean isMyEchoUat = sender != null
                            && myImeiUat != null
                            && sender.equals(myImeiUat);

                    LocationEntity addLoc = new LocationEntity(
                            0,
                            isMyEchoUat,    // ⭐ Echo면 isMy=true
                            ver,
                            sender,
                            lat, lng,
                            alt, dir, speed,
                            date,           // UAT은 GPS 시간!
                            new Date(),     // recvDate는 앱 수신 시간
                            false, false, false);

                    if (isMyEchoUat) {
                        Log.v("LOC ECHO 0x13",
                                "⭐ 내 UAT Echo! sender=" + sender + " lat=" + lat +
                                " gps_time=" + date);
                    } else {
                        Log.v("LOC RECV 0x13",
                                "UAT sender=" + sender + " lat=" + lat);
                    }

                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            String label = isMyEchoUat ? "내 UAT Echo 저장" : "수신 UAT 위치 저장";
                            Log.v("Location ADD", label);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, date, ver
                    );

                }
                // 메시지
                else if (ver == 0x16) {
                    byte[] header = new byte[21];
                    byte[] body = new byte[data.length - 22];
                    System.arraycopy(data, 1, header, 0, header.length);
                    System.arraycopy(data, header.length + 1, body, 0, body.length);

                    String codeNum = new String(header, StandardCharsets.UTF_8).trim();
                    String message = new String(body, StandardCharsets.UTF_8);

                    Log.v("MSG 0x16", "sender=" + codeNum + " msg=" + message);

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum, "", message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "Message saved (0x16)");
                        return null;
                    });

                } else if (ver == 0x17) {
                    Log.v("MSG 0x17", "Size : " + buffer.readableBytes());
                    buffer.readByte();

                    int addrSize = buffer.readUnsignedByte();
                    String codeNum = buffer.readCharSequence(addrSize, StandardCharsets.US_ASCII).toString().trim();

                    int titleSize = buffer.readUnsignedByte();
                    String title = buffer.readCharSequence(titleSize, StandardCharsets.UTF_8).toString().trim();

                    int memoSize = buffer.readUnsignedByte();
                    String message = buffer.readCharSequence(memoSize, StandardCharsets.UTF_8).toString().trim();

                    Log.v("MSG 0x17", "sender=" + codeNum + " title=" + title + " msg=" + message);

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum, title, message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "Message saved (0x17)");
                        return null;
                    });
                }

                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,OK", vals[0]));
            } catch (Exception e) {
                Log.e("RECEIVE-ERR", "PARSE FAILED: " + e.getMessage(), e);
                Log.e("RECEIVE-ERR", "raw packet: " + packet);
                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,FAIL", vals[0]));
            }
        } else if (packet.startsWith("MSGDEL=")) {
            String msg = packet.substring(7);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                Toast.makeText(getApplicationContext(), "All messages in the terminal have been deleted.", Toast.LENGTH_LONG).show();
            }
        } else if (packet.startsWith("BROAD=")) {
            Log.v("BROAD-RX", packet);
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
            Log.v("BROAD-RX", "battery=" + vals[0] + " inbox=" + vals[1] + " UNSENT=" + vals[2] + " signal=" + vals[3]);

            DeviceStatus sta = new DeviceStatus();
            sta.setBattery(Integer.parseInt(vals[0]));
            sta.setInBox(Integer.parseInt(vals[1]));
            sta.setOutBox(Integer.parseInt(vals[2]));
            sta.setSignal(Integer.parseInt(vals[3]));

            if (vals.length > 4) sta.setGpsTime(vals[4]);
            if (vals.length > 5) sta.setGpsLat(vals[5]);
            if (vals.length > 6) sta.setGpsLng(vals[6]);
            if (vals.length > 7) sta.setSosMode(!vals[7].equals("0"));
            if (vals.length > 8) sta.setTrackingMode(!vals[8].equals("0"));

            mBleViewModel.getDeviceStatus().postValue(sta);
            mLastBroadReceivedTime = System.currentTimeMillis();
            Log.v("SYNC", "✓ BROAD received - battery=" + sta.getBattery() +
                    " signal=" + sta.getSignal() + " tracking=" + sta.isTrackingMode());

            // ⭐ 스마트 자동 받기 트리거
            int inboxCount = Integer.parseInt(vals[1]);
            checkAndTriggerAutoReceive(inboxCount);

        } else if (packet.startsWith("SN=")) {
            String msg = packet.substring(3);
            String[] vals = msg.split(",");
        }
    }

    BluetoothGattCharacteristic getWriteCharacteristic(final BleDevice bleDevice) {
        BluetoothGattService service = BleManager.getInstance().getBluetoothGatt(bleDevice).getService(BLE_SERVICE_UUID);
        if (service == null) return null;
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            int charaProp = characteristic.getProperties();
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                return characteristic;
            }
        }
        return null;
    }

    public void bleSendMessage(String msg) {
        Log.v("BLE Write", msg);
        String sendMsg = String.format("%s\n", Base64.encodeToString(msg.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
        if (bleDevice == null) {
            final Snackbar snackbar = Snackbar.make(binding.mainLayout, getString(R.string.ble_test_nul_device), Snackbar.LENGTH_LONG);
            snackbar.setAction("OK", v -> snackbar.dismiss());
            snackbar.show();
            return;
        }
        BluetoothGattCharacteristic characteristic = getWriteCharacteristic(bleDevice);
        if (characteristic == null) {
            BleManager.getInstance().disconnect(bleDevice);
            return;
        }
        BleManager.getInstance().write(
                bleDevice,
                BLE_SERVICE_UUID.toString(),
                characteristic.getUuid().toString(),
                sendMsg.getBytes(),
                new BleWriteCallback() {
                    @Override public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                        runOnUiThread(() -> { });
                    }
                    @Override public void onWriteFailure(final BleException exception) {
                        runOnUiThread(() -> { });
                    }
                });
    }

    public void setConnectBleDevice(@NonNull BleDevice bleDevice) {
        // ⭐ v4 Phase B-2-3-fix: BLE 수신 큐 초기화
        // Activity 재생성 시 이전 패킷 파편이 남아있으면
        // 새 패킷과 섞여서 파싱 실패 → "무한 커넥팅" 증상 발생
        try {
            BLE.INSTANCE.getReceiveData().clear();
            Log.v("BLE", "🔄 수신 큐 초기화 (Activity 재시작 대응)");
        } catch (Exception e) {
            Log.v("BLE", "수신 큐 초기화 실패: " + e.getMessage());
        }

        BluetoothGatt gatt = BleManager.getInstance().getBluetoothGatt(bleDevice);
        BluetoothGattService service = gatt.getService(BLE_SERVICE_UUID);
        BluetoothGattCharacteristic readCharacteristic = null;
        if (service == null) {
            BleManager.getInstance().disconnect(bleDevice);
            return;
        }
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
            int charaProp = characteristic.getProperties();
            if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                readCharacteristic = characteristic;
            }
        }
        if (readCharacteristic == null) {
            BleManager.getInstance().disconnect(bleDevice);
            return;
        }
        BleManager.getInstance().notify(bleDevice,
                BLE_SERVICE_UUID.toString(),
                readCharacteristic.getUuid().toString(),
                new BleNotifyCallback() {
                    @Override public void onNotifySuccess() {
                        runOnUiThread(() -> {
                            Log.v("BLE", "connect success");
                            BLE.INSTANCE.getWriteQueue().offer("INFO=?");
                        });
                    }
                    @Override public void onNotifyFailure(final BleException exception) {
                        runOnUiThread(() -> { });
                    }
                    @Override public void onCharacteristicChanged(byte[] data) {
                        runOnUiThread(() -> {
                            BLE.INSTANCE.addReceviceData(new String(data));
                            if (data[data.length - 1] == '\n') {
                                try {
                                    List<String> read = BLE.INSTANCE.getReceiveData();
                                    String reads = String.join("", read);
                                    BLE.INSTANCE.getReceiveData().clear();
                                    String packet = new String(Base64.decode(reads, Base64.NO_WRAP));

                                    // 기존 로직 (Activity에서 먼저 완전히 처리)
                                    receivePacketProcess(packet);

                                    // ⭐ v4 Phase B-2-3: 처리 완료 후 Service에 Broadcast
                                    // LiveData 방식(메인 스레드 경합)과 달리
                                    // Broadcast는 비동기로 전달되어 안전
                                    Intent packetIntent = new Intent(
                                        com.ah.acr.messagebox.service.TytoConnectService
                                            .BROADCAST_PACKET_RECEIVED);
                                    packetIntent.putExtra("packet", packet);
                                    packetIntent.setPackage(getPackageName()); // 보안
                                    sendBroadcast(packetIntent);
                                } catch (Exception e) {
                                    // ⭐ v4 Phase B-2-3-fix: 파싱 실패 시 큐 초기화
                                    // 파편 패킷이 계속 쌓여서 "무한 커넥팅" 방지
                                    Log.v("BLE", "⚠ 패킷 파싱 실패, 큐 초기화: " + e.getMessage());
                                    BLE.INSTANCE.getReceiveData().clear();
                                }
                            }
                        });
                    }
                });
    }
}
