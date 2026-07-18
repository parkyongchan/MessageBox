package com.ah.acr.messagebox;

import com.ah.acr.messagebox.group.GroupStore;

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
import android.content.Context;
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
import com.ah.acr.messagebox.database.InsertResult;
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
import com.ah.acr.messagebox.util.LocaleHelper;
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
    private static final int PERMISSION_POST_NOTIFICATIONS = 6;

    private static final String TEST_IMEI_TRACK = "TEST-001";
    private static final String TEST_IMEI_SOS = "TEST-002";
    private static final String TEST_IMEI_MSG = "1111111111111111";

    public static final String PREF_AUTO_RECEIVE = "pref_auto_receive_enabled";
    public static final boolean DEFAULT_AUTO_RECEIVE = true;
    private static final long RECEIVE_TIMEOUT_MS = 30000;

    private static final int COLOR_AUTO_ON = 0xFF00E5D1;
    private static final int COLOR_AUTO_OFF = 0xFF95B0D4;
    private static final int COLOR_AUTO_RECEIVING = 0xFFFFB300;

    private ActivityMainBinding binding;

    private BleViewModel mBleViewModel;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;
    private LocationViewModel locationViewModel;

    private int mTestTapCount = 0;
    private boolean mIsTestMode = false;

    private boolean mIsTrackingMode = false;
    // [대용량 TRACK] 대용량 수신 중 TRACK 일시정지 (위성 송신 경합 방지). 60초 무활동 시 자동 복원.
    private volatile boolean mTrackPausedForLarge = false;
    private volatile long mLastLargeActivityAt = 0L;
    private boolean mIsSosMode = false;

    private Handler mSyncHandler;

    // [SURVIVAL] 생존 진입 재시도 (단일 슬롯 + 10분 주기 + 최대 3회 + ACK 중단)
    private String mSurvivalKey = null;
    private String mActiveSurvivalKey = null;   // [S5-chat] ACK 무관, 세션 유지용 키       // 재시도 중인 세션키 (null=비활성)
    private String mSurvivalTitle = null;     // 재전송할 SENDING= 패킷
    private int mSurvivalRetryCount = 0;      // 재시도 횟수
    private static final long SURVIVAL_RETRY_MS = 600000L; // 10분
    private static final int SURVIVAL_RETRY_MAX = 3;       // 최대 3회 (감도 나쁠 때 아웃박스 과적 방지)
    private final Runnable mSurvivalRetryRunnable = new Runnable() {
        @Override public void run() {
            if (mSurvivalKey == null || mSurvivalTitle == null) return;
            if (mSurvivalRetryCount >= SURVIVAL_RETRY_MAX) {
                Log.v("SURVIVAL", "retry max(" + SURVIVAL_RETRY_MAX + ") reached, giving up: " + mSurvivalKey);
                mSurvivalKey = null;
                mSurvivalTitle = null;
                updateSurvivalStatus(false, false);   // 요청 실패(상태 해제)
                return;
            }
            mSurvivalRetryCount++;
            BLE.INSTANCE.getWriteQueue().offer(mSurvivalTitle);
            Log.v("SURVIVAL", "retry send #" + mSurvivalRetryCount + ": " + mSurvivalTitle);
            mSyncHandler.postDelayed(this, SURVIVAL_RETRY_MS);
        }
    };
    private Runnable mBroadRetryRunnable;
    // [outboxStuck] outbox 좀비 감지 (오래 안 빠지는 미발신)
    private int mLastOutboxVal = -1;
    private long mOutboxStuckSince = 0;
    private long mOutboxWarnedAt = 0;
    private static final long OUTBOX_STUCK_MS = 30 * 60 * 1000L; // 30분
    private Runnable mInfoRetryRunnable;
    private long mLastBroadReceivedTime = 0;
    private long mLastInfoReceivedTime = 0;
    // 대용량 메시지 조립 버퍼: chunkMsgId → (seq → memo조각)
    private final java.util.Map<Integer, java.util.TreeMap<Integer, String>> mLargeMsgBuf = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> mLargeMsgTotal = new java.util.HashMap<>();
    private final java.util.Map<Integer,Long> mLargeMsgCrc = new java.util.HashMap<>();   // [MT-idFix] msgId -> seq0 fullCrc (옛 버퍼 구분용)
    private final java.util.Map<Integer, String> mLargeMsgSender = new java.util.HashMap<>();
    // ⭐ 완성된 msgId의 완료 시각(중복 조각 재수신 차단용, 윈도우 지나면 새 메시지로 취급)
    private final java.util.Map<Integer, Long> mLargeMsgDoneAt = new java.util.HashMap<>();

    // [fileMsg] 파일/사진(~L:F:/~L:I:) 수신 전용 버퍼 — 텍스트(mLargeMsgBuf)와 분리(바이너리 보존)
    private final java.util.Map<Integer, java.util.TreeMap<Integer, byte[]>> mLargeFileBuf = new java.util.HashMap<>();
    private final java.util.Map<Integer, Integer> mLargeFileTotal = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mLargeFileCrc = new java.util.HashMap<>();    // seq0 fullCrc (검증/옛버퍼구분)
    private final java.util.Map<Integer, String> mLargeFileName = new java.util.HashMap<>(); // seq0 파일명
    private final java.util.Map<Integer, Character> mLargeFileType = new java.util.HashMap<>(); // 'F'(파일)/'I'(사진)
    private final java.util.Map<Integer, String> mLargeFileSender = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mLargeFileLastAt = new java.util.HashMap<>();
    private final java.util.Map<Integer, Long> mLargeFileDoneAt = new java.util.HashMap<>(); // 완성 msgId 중복차단
    private static final long LARGE_MSG_DONE_WINDOW_MS = 10 * 60 * 1000L;
    private final java.util.Map<Integer, String> mSentLargeMsg = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> mSentLargeMsgTo = new java.util.HashMap<>();
    // [fileMsg MO복구] 보낸 파일/사진 byte[] + 메타 (~Q:/auto/수동 재전송용)
    private final java.util.Map<Integer, byte[]> mSentLargeFile = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> mSentLargeFileName = new java.util.HashMap<>();
    private final java.util.Map<Integer, Character> mSentLargeFileType = new java.util.HashMap<>();
    private final java.util.Map<Integer, String> mSentTacticalRaw = new java.util.HashMap<>();
    // [tactical-recv] 수신 ~L:G: 조립용 — G 타입 msgId 표시 + 파싱 결과 보관
    private final java.util.Set<Integer> mRecvTacticalMsgIds = java.util.Collections.synchronizedSet(new java.util.HashSet<>());   // [tactical] 전술 원문(~Q: 재전송용). mSentLargeMsg엔 요약이 들어가므로 분리.
    // [abortReSend] 모뎀 거부로 송신 못한 조각 seq 기록 → 송신 후 자동 재송신용
    private final java.util.Map<Integer, java.util.List<Integer>> mSentLargeFileAborted = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.atomic.AtomicInteger mLargeMsgIdSeq = new java.util.concurrent.atomic.AtomicInteger(new java.util.Random().nextInt(256));   // [idFix3] 부팅마다 랜덤 시작 → msgId=0 고정 충돌 방지
    private final java.util.concurrent.atomic.AtomicInteger mLargeSendIdSeq = new java.util.concurrent.atomic.AtomicInteger(800);
    // ⭐ ACK 송신: 모뎀 수락 에코(SENDING=<idx>,OK) 대기 (doSendPending과 동일 메커니즘)
    private static final long ACK_ECHO_TIMEOUT_MS = 10000;   // 1회 대기 10초
    private static final int  ACK_MAX_ATTEMPTS = 3;          // 에코 없으면 재송신(최대 3회)
    private static final long BROAD_TIMEOUT_MS = 15000;
    private static final long INFO_TIMEOUT_MS = 8000;
    private static final long PERIODIC_SYNC_MS = 30000;

    private boolean mIsAutoReceiving = false;
    private int mLastInboxCount = 0;
    // 단문 서버-ACK 지연 송신 큐 (인박스 배수 중 SENDING <-> RECEIVED=? BLE 경쟁 방지)
    private final java.util.ArrayList<Integer> mPendingServerAckIds = new java.util.ArrayList<>();
    // [gap-fill 1-C] ~R: 재요청 지연 큐("msgId:seq") + 상한/쿨다운
    private final java.util.ArrayList<String> mPendingGapReqs = new java.util.ArrayList<>();
    private final java.util.HashMap<String, Integer> mGapReqCount = new java.util.HashMap<>();   // "msgId:seq" -> 시도횟수
    private final java.util.HashMap<String, Long> mGapReqLastAt = new java.util.HashMap<>();     // "msgId:seq" -> 마지막 시도 시각
    private static final int  GAP_REQ_MAX_ATTEMPTS = 3;
    private static final long GAP_REQ_COOLDOWN_MS = 30000;   // 같은 seq 30초 내 재요청 차단
    // 최근 저장한 (msgId+본문) -> 시각: 같은 단문 본문 재수신(인박스 재독) 차단
    private final java.util.HashMap<String, Long> mRecvShortAckDoneAt = new java.util.HashMap<>();
    private long mLastAutoReceiveTime = 0;
    private Runnable mAutoReceiveTimeoutRunnable;
    private Animation mAutoReceiveRotation;


    // ═════════════════════════════════════════════════════════════
    //   Localization Support
    //   attachBaseContext is called first when Activity is created.
    //   Apply saved language (en/ja) to Context.
    // ═════════════════════════════════════════════════════════════

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.onAttach(newBase));
    }

    /**
     * Override Resources.getResources()
     * Prevents getResources() from returning original in some cases.
     */
    @Override
    public android.content.res.Resources getResources() {
        android.content.res.Resources resources = super.getResources();
        try {
            String lang = LocaleHelper.getLanguage(this);
            java.util.Locale locale = new java.util.Locale(lang);
            java.util.Locale.setDefault(locale);

            android.content.res.Configuration config = new android.content.res.Configuration(
                    resources.getConfiguration());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                config.setLocale(locale);
            } else {
                config.locale = locale;
            }

            resources.updateConfiguration(config, resources.getDisplayMetrics());
        } catch (Exception e) {
            // ignore
        }
        return resources;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // [최초 1회] 메시지 설정 기본값 (설치 후 첫 실행)
        {
            android.content.SharedPreferences _p = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(this);
            if (!_p.getBoolean("pref_defaults_initialized", false)) {
                _p.edit()
                    .putBoolean("pref_ack_short", true)
                    .putBoolean("pref_ack_large", true)
                    .putBoolean("pref_ack_media", true)
                    .putBoolean("pref_integrity_short", true)
                    .putBoolean("pref_integrity_large", true)
                    .putBoolean("pref_resend_auto", true)
                    .putInt("pref_resend_interval", 5)
                    .putBoolean("pref_defaults_initialized", true)
                    .apply();
            }
        }

        // Debug log - check actual Locale and resource values
        try {
            Log.v("LOCALE-DEBUG", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            Log.v("LOCALE-DEBUG", "Stored language: " + LocaleHelper.getLanguage(this));
            Log.v("LOCALE-DEBUG", "Current Locale: " +
                    getResources().getConfiguration().getLocales().get(0).toString());
            Log.v("LOCALE-DEBUG", "Default Locale: " +
                    java.util.Locale.getDefault().toString());
            Log.v("LOCALE-DEBUG", "ble_login_title: " +
                    getString(R.string.ble_login_title));
            Log.v("LOCALE-DEBUG", "ble_login_subtitle: " +
                    getString(R.string.ble_login_subtitle));
            Log.v("LOCALE-DEBUG", "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        } catch (Exception e) {
            Log.e("LOCALE-DEBUG", "Debug log error: " + e.getMessage());
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        WeatherStore.load(getApplicationContext());   // [CLIMATE] 저장된 날씨 로드

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (this.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_POST_NOTIFICATIONS);
            }
        }

        com.ah.acr.messagebox.service.TytoConnectService.start(this);

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

        // ⭐ v5 Phase B-2-6 (2026-05-11): writeQueue 송신을 Service로 완전 이관
        // 배경: observe(this, ...)는 Activity lifecycle 종속이라 STOPPED(백그라운드) 시
        //       콜백 호출 안 됨 → RECEIVED=? 등의 명령이 단말기에 전달 안 됨
        // 해결: TytoConnectService.setupBleObservers에서 observeForever로 처리
        //       (Service는 백그라운드에서도 lifecycle 유효)
        // BLE.INSTANCE.getWriteQueue().observe(this, queue -> {
        //     String request = queue.poll();
        //     bleSendMessage(request);
        // });

        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MODE_PRIVATE);
        checkExternalStorage();

        setupFixedHeaderObservers();
        setupReconnectUI();
        setupAutoReceiveToggle();
        setupHeaderButtons();
        setupBottomTabs();


    }

    


    // ═════════════════════════════════════════════════════════════
    //   Smart Auto Receive
    // ═════════════════════════════════════════════════════════════

    private boolean isAutoReceiveEnabled() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        return prefs.getBoolean(PREF_AUTO_RECEIVE, DEFAULT_AUTO_RECEIVE);
    }

    private void setAutoReceiveEnabled(boolean enabled) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_AUTO_RECEIVE, enabled).apply();
        Log.v("AUTO-RECV", "Setting changed: " + (enabled ? "ON" : "OFF"));
    }

    private void setupAutoReceiveToggle() {
        mAutoReceiveRotation = AnimationUtils.loadAnimation(this, R.anim.rotate_auto_receive);
        updateAutoReceiveToggleUI(isAutoReceiveEnabled(), false);

        binding.statusArea.btnAutoReceive.setOnClickListener(v -> {
            boolean newState = !isAutoReceiveEnabled();
            setAutoReceiveEnabled(newState);
            updateAutoReceiveToggleUI(newState, false);

            // Localized: Auto receive ON/OFF
            String msg = newState
                    ? getString(R.string.toast_auto_receive_on)
                    : getString(R.string.toast_auto_receive_off);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();

            if (!newState && mIsAutoReceiving) {
                stopAutoReceiveAnimation();
                Log.v("AUTO-RECV", "User changed to OFF");
            }
        });

        // 단말 메시지 전체 삭제 버튼 (MO 버퍼 클리어 - 깨진 프레임으로 막힌 송신 복구용)
        binding.statusArea.btnMsgDelete.setOnClickListener(v -> sendMsgDelete());
        // [CLIMATE] SURV \ubc84\ud2bc: \uc704\uce58 1\ud68c + \uc0dd\uc874\uc9c4\uc785 (SOS \ubc18\ubcf5 \uc5c6\uc74c)
        binding.statusArea.btnClimateSurv.setOnClickListener(v -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Toast.makeText(this, getString(R.string.toast_device_not_connected), Toast.LENGTH_SHORT).show();
                return;
            }
            startSurvivalEntry();
        });
        // [CLIMATE] WEATHER \ubc84\ud2bc: \uc704\uce58 1\ud68c + \ub0a0\uc528 \uc694\uccad (\ub370\uc774\ud130 \uac00\uacf5\uc740 \ucd94\ud6c4 \uc11c\ubc84 \uad6c\ud604)
        binding.statusArea.btnClimateWeather.setOnClickListener(v -> {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
                Toast.makeText(this, getString(R.string.toast_device_not_connected), Toast.LENGTH_SHORT).show();
                return;
            }
            startWeatherEntry();
        });
    }

    private void updateAutoReceiveToggleUI(boolean enabled, boolean isReceiving) {
        if (!enabled) {
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_OFF);
            binding.statusArea.btnAutoReceive.clearAnimation();
        } else if (isReceiving) {
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_RECEIVING);
            if (binding.statusArea.btnAutoReceive.getAnimation() == null) {
                binding.statusArea.btnAutoReceive.startAnimation(mAutoReceiveRotation);
            }
        } else {
            binding.statusArea.btnAutoReceive.setColorFilter(COLOR_AUTO_ON);
            binding.statusArea.btnAutoReceive.clearAnimation();
        }
    }

    private void startAutoReceiveAnimation() {
        runOnUiThread(() -> {
            if (isAutoReceiveEnabled()) {
                updateAutoReceiveToggleUI(true, true);
            }
        });
    }

    private void stopAutoReceiveAnimation() {
        runOnUiThread(() -> {
            updateAutoReceiveToggleUI(isAutoReceiveEnabled(), false);
        });
    }

    private void checkAndTriggerAutoReceive(int currentInboxCount) {
        if (!isAutoReceiveEnabled()) {
            return;
        }

        if (currentInboxCount <= 0) {
            mLastInboxCount = 0;
            return;
        }

        if (currentInboxCount > mLastInboxCount || mLastInboxCount == 0) {
            mLastInboxCount = currentInboxCount;
            Log.v("AUTO-RECV", "Inbox change detected -> Service handles auto receive");
        }
    }

    private void startAutoReceiveTimeout() {
        if (mAutoReceiveTimeoutRunnable != null) {
            mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
        }

        mAutoReceiveTimeoutRunnable = () -> {
            if (mIsAutoReceiving) {
                long elapsed = System.currentTimeMillis() - mLastAutoReceiveTime;
                Log.w("AUTO-RECV", "Timeout (" + elapsed + "ms) - Reset");
                mIsAutoReceiving = false;
                stopAutoReceiveAnimation();
            }
        };

        mSyncHandler.postDelayed(mAutoReceiveTimeoutRunnable, RECEIVE_TIMEOUT_MS);
    }

    private void completeAutoReceive() {
        if (mIsAutoReceiving) {
            long elapsed = System.currentTimeMillis() - mLastAutoReceiveTime;
            Log.v("AUTO-RECV", "Completed (" + elapsed + "ms)");
            mIsAutoReceiving = false;
            mLastInboxCount = 0;

            if (mAutoReceiveTimeoutRunnable != null) {
                mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
            }

            stopAutoReceiveAnimation();
        }
    }

    private void resetAutoReceive() {
        mIsAutoReceiving = false;
        mLastInboxCount = 0;
        if (mAutoReceiveTimeoutRunnable != null) {
            mSyncHandler.removeCallbacks(mAutoReceiveTimeoutRunnable);
        }

        stopAutoReceiveAnimation();
        Log.v("AUTO-RECV", "State reset");
    }


    // ═════════════════════════════════════════════════════════════
    //   Header Buttons
    // ═════════════════════════════════════════════════════════════

    private void setupHeaderButtons() {
        binding.headerArea.btnTrackHeader.setOnClickListener(v -> onTrackButtonClick());
        binding.headerArea.btnSosHeader.setOnClickListener(v -> onSosButtonClick());
        binding.headerArea.btnExitHeader.setOnClickListener(v -> onExitButtonClick());
    }

    private void onTrackButtonClick() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(this, getString(R.string.toast_device_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsTrackingMode) {
            // TRACK Stop dialog (localized)
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_track_stop_title))
                    .setMessage(getString(R.string.dialog_track_stop_message))
                    .setPositiveButton(getString(R.string.btn_stop), (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=3");
                        Log.v("TRACK", "LOCATION=3");
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        } else {
            if (mIsSosMode) {
                Toast.makeText(this, getString(R.string.toast_cannot_track_during_sos), Toast.LENGTH_LONG).show();
                return;
            }

            // TRACK Start dialog (localized)
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_track_start_title))
                    .setMessage(getString(R.string.dialog_track_start_message))
                    .setPositiveButton(getString(R.string.btn_start), (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=2");
                        Log.v("TRACK", "LOCATION=2");
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        }
    }

    private void onSosButtonClick() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(this, getString(R.string.toast_device_not_connected), Toast.LENGTH_SHORT).show();
            return;
        }

        if (mIsSosMode) {
            // SOS Stop dialog (localized)
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_sos_stop_title))
                    .setMessage(getString(R.string.dialog_sos_stop_message))
                    .setPositiveButton(getString(R.string.btn_stop), (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=5");
                        Log.v("SOS", "LOCATION=5");
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        } else {
            // SOS Start dialog (localized)
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.dialog_sos_start_title))
                    .setMessage(getString(R.string.dialog_sos_start_message))
                    .setPositiveButton(getString(R.string.btn_send), (d, w) -> {
                        BLE.INSTANCE.getWriteQueue().offer("LOCATION=4");
                        Log.v("SOS", "LOCATION=4");
                        promptSurvivalEntry();   // [SURVIVAL] SOS after -> survival prompt
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
        }
    }

    // [SURVIVAL] 생존 지원 요청 팝업 → 확인 시 발신 시작
    public void promptSurvivalEntry() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_survival_title))
                .setMessage(getString(R.string.dialog_survival_message))
                .setPositiveButton(getString(R.string.btn_survival_yes), (d, w) -> startSurvivalEntry())
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setCancelable(false)
                .show();
    }

    // [SURVIVAL] 생존 진입 발신 시작: ~S:<key>:<lat>:<lon>, 단일 슬롯 + 1분 재시도
    /** [S5-chat] 활성 생존 세션키 (조난자 질의 ~V: 송신용). 없으면 null. */
    public String getActiveSurvivalKey() { return mActiveSurvivalKey; }

    /** [S5-chat] victim query send: title=~V:<key>, memo=content. */
    public boolean sendSurvivalQuery(String content) {
        if (content == null || content.trim().isEmpty()) return false;
        if (mActiveSurvivalKey == null || mActiveSurvivalKey.isEmpty()) return false;
        String packet = buildSurvivalPacket("~V:" + mActiveSurvivalKey, content.trim());
        BLE.INSTANCE.getWriteQueue().offer(packet);
        Log.v("SURVIVAL", "victim query send: key=" + mActiveSurvivalKey + " len=" + content.length());
        return true;
    }

    private void startSurvivalEntry() {
        DeviceStatus st = mBleViewModel.getDeviceStatus().getValue();
        String imei = ImeiStorage.getSanitizedLast(this);
        if (imei == null || imei.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_survival_no_imei), Toast.LENGTH_SHORT).show();
            return;
        }
        String lat = (st != null && st.getGpsLat() != null) ? st.getGpsLat() : "0";
        String lon = (st != null && st.getGpsLng() != null) ? st.getGpsLng() : "0";

        String key = imei + "-" + (System.currentTimeMillis() / 1000L);
        mSurvivalKey = key;
        mActiveSurvivalKey = key;   // [S5-chat] 대화 송신용 (ACK 후에도 유지)
        mSurvivalRetryCount = 0;
        // title=~S:1 (20B 제한 회피), memo=key:lat:lon (실데이터). 0x07 패킷으로 서버 발신.
        mSurvivalTitle = buildSurvivalPacket("~S:1", key + ":" + lat + ":" + lon);

        BLE.INSTANCE.getWriteQueue().offer(mSurvivalTitle);
        Log.v("SURVIVAL", "start send: key=" + key + " lat=" + lat + " lon=" + lon);
        updateSurvivalStatus(true, false);   // 요청 중

        mSyncHandler.removeCallbacks(mSurvivalRetryRunnable);
        mSyncHandler.postDelayed(mSurvivalRetryRunnable, SURVIVAL_RETRY_MS);
        showCancelSnackbar("SURVIVAL", key);   // [CLIMATE] 발신 후 취소 스낵바
    }

    // [CLIMATE] 발신 후 취소 스낵바 (약 6초). 취소 시 앱 재전송 중단.
    private void showCancelSnackbar(String kind, String key) {
        try {
            View root = findViewById(android.R.id.content);
            if (root == null) return;
            com.google.android.material.snackbar.Snackbar
                .make(root, kind + " request sent", 6000)
                .setAction("CANCEL", v -> cancelSurvivalSend(kind, key))
                .show();
        } catch (Exception ignore) {}
    }

    // [CLIMATE] 생존/날씨 발신 취소: 앱 재전송 중단 + 세션 키 초기화.
    //   주의: 첫 발신은 이미 나갔을 수 있어 서버 세션은 별도 종료 필요(추후).
    private void cancelSurvivalSend(String kind, String key) {
        mSyncHandler.removeCallbacks(mSurvivalRetryRunnable);
        if (key != null && key.equals(mSurvivalKey)) {
            mSurvivalKey = null;
            mSurvivalTitle = null;
            mSurvivalRetryCount = 0;
        }
        if (key != null && key.equals(mActiveSurvivalKey)) {
            mActiveSurvivalKey = null;
        }
        Log.v("SURVIVAL", "cancelled by user: kind=" + kind + " key=" + key);
        Toast.makeText(this, "Cancelled — resend stopped", Toast.LENGTH_SHORT).show();
    }

    // [CLIMATE] 날씨 진입 발신: ~W:1 + key:lat:lon (위치 1회). 생존과 동일 방식, 식별자만 ~W.
    //   데이터 가공/회신은 추후 서버에서 기획대로 구현. 지금은 발신 방식만.
    private void startWeatherEntry() {
        DeviceStatus st = mBleViewModel.getDeviceStatus().getValue();
        String imei = ImeiStorage.getSanitizedLast(this);
        if (imei == null || imei.isEmpty()) {
            Toast.makeText(this, getString(R.string.toast_survival_no_imei), Toast.LENGTH_SHORT).show();
            return;
        }
        String lat = (st != null && st.getGpsLat() != null) ? st.getGpsLat() : "0";
        String lon = (st != null && st.getGpsLng() != null) ? st.getGpsLng() : "0";
        String key = imei + "-" + (System.currentTimeMillis() / 1000L);
        // title=~W:1 (20B 제한), memo=key:lat:lon. 0x07 FREE 프레임, 서버 발신.
        String packet = buildSurvivalPacket("~W:1", key + ":" + lat + ":" + lon);
        BLE.INSTANCE.getWriteQueue().offer(packet);
        Log.v("WEATHER", "start send: key=" + key + " lat=" + lat + " lon=" + lon);
        showCancelSnackbar("WEATHER", key);   // [CLIMATE] 발신 후 취소 스낵바
    }

    // [SURVIVAL] 0x07 패킷 조립 (채팅 doSendPending과 동일 형식). codeNum=""=서버.
    private String buildSurvivalPacket(String title, String memo) {
        String codeNum = "";   // addrForSend: SERVER -> ""
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0x07);
        buffer.writeByte(codeNum.getBytes(StandardCharsets.US_ASCII).length);
        buffer.writeCharSequence(codeNum, StandardCharsets.US_ASCII);
        buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(title, StandardCharsets.UTF_8);
        buffer.writeByte(memo.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(memo, StandardCharsets.UTF_8);
        byte[] body = new byte[buffer.readableBytes()];
        buffer.readBytes(body);
        return String.format("SENDING=%d,%s",
                (int)(System.currentTimeMillis() % 100000),
                Base64.encodeToString(body, Base64.NO_WRAP));
    }

    // [SURVIVAL] ~SA:<key> ACK 수신 시 재시도 중단
    private void onSurvivalAck(String ackKey) {
        if (mSurvivalKey != null && mSurvivalKey.equals(ackKey)) {
            mSyncHandler.removeCallbacks(mSurvivalRetryRunnable);
            mSurvivalKey = null;
            mSurvivalTitle = null;
            mSurvivalRetryCount = 0;
            Log.v("SURVIVAL", "ACK ok, retry stopped: " + ackKey);
            updateSurvivalStatus(false, true);   // 연결됨
        }
    }

    // [SURVIVAL] 상태 표시 (요청 중 / 연결됨)
    private void updateSurvivalStatus(boolean requesting, boolean connected) {
        String msg = connected ? getString(R.string.survival_connected)
                : requesting ? getString(R.string.survival_requesting) : "";
        if (!msg.isEmpty()) {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private void onExitButtonClick() {
        // Exit App dialog (localized)
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_exit_title))
                .setMessage(getString(R.string.dialog_exit_message))
                .setPositiveButton(getString(R.string.btn_exit), (d, w) -> {
                    Log.v("EXIT", "App exit");
                    try {
                        com.ah.acr.messagebox.service.TytoConnectService.stop(this);
                    } catch (Exception e) {
                        Log.v("EXIT", "Service stop failed: " + e.getMessage());
                    }
                    finishAndRemoveTask();
                })
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .show();
    }

    // [outboxStuck] outbox가 30분 이상 같은 값으로 안 빠지면 좀비 의심 → 경고
    private void checkOutboxStuck(int outbox) {
        long now = System.currentTimeMillis();
        if (outbox <= 0) {
            mOutboxStuckSince = 0;
            mLastOutboxVal = outbox;
            binding.statusArea.textMainOutbox.setTextColor(0xFFFF5252); // 기본 빨강 유지
            return;
        }
        if (outbox == mLastOutboxVal) {
            if (mOutboxStuckSince == 0) mOutboxStuckSince = now;
            long stuck = now - mOutboxStuckSince;
            if (stuck > OUTBOX_STUCK_MS) {
                // 깜빡임 강조
                binding.statusArea.textMainOutbox.setTextColor(
                        (now / 600 % 2 == 0) ? 0xFFFF1744 : 0xFFFFFFFF);
                // 경고 Toast (10분에 한 번만)
                if (now - mOutboxWarnedAt > 10 * 60 * 1000L) {
                    mOutboxWarnedAt = now;
                    Toast.makeText(this,
                            "Send stuck: outbox " + outbox + " not clearing for "
                                    + (stuck / 60000) + " min (satellite/modem). Check & resend or clear.",
                            Toast.LENGTH_LONG).show();
                }
            } else {
                binding.statusArea.textMainOutbox.setTextColor(0xFFFF5252);
            }
        } else {
            // 값이 바뀜 = 빠지는 중 → 리셋
            mOutboxStuckSince = now;
            mOutboxWarnedAt = 0;
            binding.statusArea.textMainOutbox.setTextColor(0xFFFF5252);
        }
        mLastOutboxVal = outbox;
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
    //   Satellite TRACK Session Sync
    // ═════════════════════════════════════════════════════════════

    private boolean mPrevSatSessionActive = false;
    private int mPrevSatSessionMode = 0;

    private void syncSatTrackSession(boolean isTracking, boolean isSos) {
        // [DISABLED 2026-05] Satellite track session creation removed per user request.
        // Header TRACK/SOS buttons still send BLE commands but do not create DB sessions.
        if (true) return;
        int currentMode;

        if (isSos) currentMode = 2;
        else if (isTracking) currentMode = 1;
        else currentMode = 0;

        boolean currentActive = (currentMode != 0);

        if (currentMode == mPrevSatSessionMode) return;

        Log.v("SAT-SESSION", "State change: " + modeToString(mPrevSatSessionMode) + " -> " + modeToString(currentMode));

        if (!mPrevSatSessionActive && currentActive) {
            startSatTrackSession(currentMode);
        } else if (mPrevSatSessionActive && !currentActive) {
            stopSatTrackSession(mPrevSatSessionMode);
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

    private void startSatTrackSession(int mode) {
        runOnUiThread(() -> {
            try {
                androidx.fragment.app.Fragment devicesFragment = findDevicesTabFragment();
                if (devicesFragment != null) {
                    java.lang.reflect.Method m = devicesFragment.getClass().getMethod(
                            "startSatSessionFromHeader", int.class);
                    m.invoke(devicesFragment, mode);
                }
            } catch (Exception e) {
                Log.v("SAT-SESSION", "Session start failed: " + e.getMessage());
            }
        });
    }

    private void stopSatTrackSession(int prevMode) {
        runOnUiThread(() -> {
            try {
                androidx.fragment.app.Fragment devicesFragment = findDevicesTabFragment();
                if (devicesFragment != null) {
                    java.lang.reflect.Method m = devicesFragment.getClass().getMethod(
                            "stopSatSessionFromHeader", int.class);
                    m.invoke(devicesFragment, prevMode);
                }
            } catch (Exception e) {
                Log.v("SAT-SESSION", "Session end failed: " + e.getMessage());
            }
        });
    }

    private androidx.fragment.app.Fragment findDevicesTabFragment() {
        try {
            androidx.fragment.app.FragmentManager fm = getSupportFragmentManager();
            for (androidx.fragment.app.Fragment f : fm.getFragments()) {
                if (f == null) continue;
                if (f.getClass().getSimpleName().equals("DevicesTabFragment")) {
                    return f;
                }
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
            Log.v("SAT-SESSION", "Fragment find failed: " + e.getMessage());
        }
        return null;
    }


    // ═════════════════════════════════════════════════════════════
    //   Reconnect UI
    // ═════════════════════════════════════════════════════════════

    private void setupReconnectUI() {
        BLE.INSTANCE.getConnectionStatus().observe(this, status -> {
            if (status == null || mIsTestMode) return;

            switch (status) {
                case BLE.CONNECT_STATUS_RECONNECTING: {
                    int attempts = BLE.INSTANCE.getReconnectAttempts();
                    int max = BLE.INSTANCE.getMaxReconnectAttempts();

                    binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_reconnecting));
                    binding.statusArea.textBleStatusMain.setTextColor(0xFFFFB300);
                    binding.statusArea.imgStatusBle.setColorFilter(0xFFFFB300);

                    binding.statusArea.textReconnectCount.setText(String.format("(%d/%d)", attempts, max));
                    binding.statusArea.textReconnectCount.setVisibility(View.VISIBLE);

                    binding.statusArea.btnConnectionAction.setText(getString(R.string.btn_cancel));
                    binding.statusArea.btnConnectionAction.setVisibility(View.VISIBLE);
                    break;
                }

                case BLE.CONNECT_STATUS_FAILED: {
                    binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_failed));
                    binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
                    binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);

                    binding.statusArea.textReconnectCount.setVisibility(View.GONE);

                    binding.statusArea.btnConnectionAction.setText(getString(R.string.btn_retry));
                    binding.statusArea.btnConnectionAction.setVisibility(View.VISIBLE);
                    break;
                }

                default: {
                    binding.statusArea.textReconnectCount.setVisibility(View.GONE);
                    binding.statusArea.btnConnectionAction.setVisibility(View.GONE);
                    break;
                }
            }
        });

        binding.statusArea.btnConnectionAction.setOnClickListener(v -> {
            String currentStatus = BLE.INSTANCE.getConnectionStatus().getValue();

            if (BLE.CONNECT_STATUS_RECONNECTING.equals(currentStatus)) {
                BLE.INSTANCE.cancelReconnect();
                Toast.makeText(this, getString(R.string.toast_reconnect_cancelled), Toast.LENGTH_SHORT).show();
            } else if (BLE.CONNECT_STATUS_FAILED.equals(currentStatus)) {
                BLE.INSTANCE.getSelectedDevice().postValue(null);
                binding.statusArea.textReconnectCount.setVisibility(View.GONE);
                binding.statusArea.btnConnectionAction.setVisibility(View.GONE);
                binding.bottomNav.setSelectedItemId(R.id.tab_ble);
                Toast.makeText(this, getString(R.string.toast_searching_device), Toast.LENGTH_LONG).show();
            }
        });
    }


    // ═════════════════════════════════════════════════════════════
    //   Periodic Sync
    // ═════════════════════════════════════════════════════════════

    private void startPeriodicSync() {
        Log.v("SYNC", "Starting");
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
                    DeviceInfo currentInfo = BLE.INSTANCE.getDeviceInfo().getValue();
                    boolean noImei = currentInfo == null || currentInfo.getImei() == null || currentInfo.getImei().isEmpty();

                    if (noImei && BLE.INSTANCE.getSelectedDevice().getValue() != null) {
                        BLE.INSTANCE.getWriteQueue().offer("INFO=?");
                        mSyncHandler.postDelayed(this, INFO_TIMEOUT_MS);
                    }
                }
            };
        }
        mSyncHandler.postDelayed(mInfoRetryRunnable, INFO_TIMEOUT_MS);
    }

    /** 단말 MO 버퍼(flash) 전체 삭제. MSGDEL=? -> 펌웨어가 fformat("F:") 실행.
     *  주의: 단말의 모든 메시지(송신대기+수신함) 삭제됨. 깨진 프레임으로 막힌 버퍼 복구용. */
    public void sendMsgDelete() {
        if (BLE.INSTANCE.getSelectedDevice().getValue() == null) {
            Toast.makeText(this, "\ub2e8\ub9d0 \uc5f0\uacb0 \uc548 \ub428", Toast.LENGTH_SHORT).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("\ub2e8\ub9d0 \uba54\uc2dc\uc9c0 \uc804\uccb4 \uc0ad\uc81c")
            .setMessage("\ub2e8\ub9d0\uc5d0 \uc800\uc7a5\ub41c \ubaa8\ub4e0 \uba54\uc2dc\uc9c0(\uc1a1\uc2e0\ub300\uae30 + \uc218\uc2e0\ud568)\ub97c \uc0ad\uc81c\ud569\ub2c8\ub2e4.\n\ub9c9\ud78c \uc1a1\uc2e0 \ubc84\ud37c\ub97c \ube44\uc6b8 \ub54c \uc0ac\uc6a9\ud558\uc138\uc694. \uacc4\uc18d\ud560\uae4c\uc694?")
            .setPositiveButton("\uc0ad\uc81c", (d, w) -> {
                BLE.INSTANCE.getWriteQueue().offer("MSGDEL=?");
                android.util.Log.w("MSGDEL", "\ub2e8\ub9d0 \ubc84\ud37c \uc804\uccb4 \uc0ad\uc81c \uba85\ub839 \uc804\uc1a1: MSGDEL=?");
                Toast.makeText(this, "\ub2e8\ub9d0 \uba54\uc2dc\uc9c0 \uc0ad\uc81c \uc694\uccad \uc804\uc1a1", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("\ucde8\uc18c", null)
            .show();
    }

    /** ACK 식별자(msgId) 발급. 단문/장문 공유 (0~255 순환).
     *  ~M:msgId(단문), ~L:T:msgId:...(장문)의 msgId, ~A:/~D: 응답 매칭에 사용. */
    public int nextAckMsgId() {
        return mLargeMsgIdSeq.getAndUpdate(p -> (p + 1) & 0xFF);
    }

    private final Runnable mPeriodicSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) return;

            // ⭐ 펌웨어 전송 중에는 주기 송신 차단
            Boolean fwUpdating = BLE.INSTANCE.isFirmwareUdate().getValue();
            if (fwUpdating != null && fwUpdating) {
                mSyncHandler.postDelayed(this, PERIODIC_SYNC_MS);
                return;
            }

            long broadElapsed = System.currentTimeMillis() - mLastBroadReceivedTime;
            if (broadElapsed >= BROAD_TIMEOUT_MS) {
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            }

            mSyncHandler.postDelayed(this, PERIODIC_SYNC_MS);
        }
    };

    private void stopPeriodicSync() {
        if (mSyncHandler != null) {
            mSyncHandler.removeCallbacksAndMessages(null);
        }
    }


    // ═════════════════════════════════════════════════════════════
    //   Fixed Header / Status Card
    // ═════════════════════════════════════════════════════════════

    private void setupFixedHeaderObservers() {
        BLE.INSTANCE.getDeviceInfo().observe(this, info -> {
            if (info != null && info.getImei() != null && !info.getImei().isEmpty()) {
                binding.headerArea.textHeaderSub.setText("IMEI  " + info.getImei());
                ImeiStorage.save(this, info.getImei());
            } else {
                binding.headerArea.textHeaderSub.setText("IMEI  -");
            }

            if (info != null) {
                updateTrackButtonUI(info.isTrackingMode());
                updateSosButtonUI(info.isSosStarted());
            }
        });

        BLE.INSTANCE.getSelectedDevice().observe(this, device -> {
            if (device != null) {
                mIsTestMode = false;
                binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_connected));
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

                binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_disconnected));
                binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
                binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
                binding.statusArea.textMainBattery.setText("- %");
                binding.statusArea.textMainInbox.setText("0");
                binding.statusArea.textMainOutbox.setText("0");
                binding.headerArea.textHeaderSub.setText("IMEI  -");
                updateSignalBar(0);
                updateLedStatus(false);

                updateTrackButtonUI(false);
                updateSosButtonUI(false);

                stopPeriodicSync();
            }
        });

        mBleViewModel.getDeviceStatus().observe(this, status -> {
            if (status == null) return;
            binding.statusArea.textMainBattery.setText(String.format("%d%%", status.getBattery()));
            updateSignalBar(status.getSignal());
            binding.statusArea.textMainInbox.setText(String.valueOf(status.getInBox()));
            binding.statusArea.textMainOutbox.setText(String.valueOf(status.getOutBox()));
            checkOutboxStuck(status.getOutBox());

            updateTrackButtonUI(status.isTrackingMode());
            updateSosButtonUI(status.isSosMode());

            updateLedStatus(status.isLedOn());

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

        binding.headerArea.textHeaderTitle.setOnLongClickListener(v -> {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 30; k++) sb.append("대용량 테스트 ").append(k).append(" 한글ABC ");
            sendLargeMsg(sb.toString());
            return true;
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

    private void updateLedStatus(boolean on) {
        binding.statusArea.textLocationStatusLabel.setText(
                getString(on ? R.string.led_status_on : R.string.led_status_off));
        int c = on ? 0xFFFBBF24 : 0xFF95B0D4;
        binding.statusArea.textLocationStatusLabel.setTextColor(c);
        binding.statusArea.imgLocationStatus.setColorFilter(c);
    }
    private void updateLocationStatus(int state) {
        switch (state) {
            case 1:
                binding.statusArea.textLocationStatusLabel.setText(getString(R.string.location_status_tracking));
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFF00E5D1);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFF00E5D1);
                break;
            case 2:
                binding.statusArea.textLocationStatusLabel.setText(getString(R.string.location_status_sos));
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFFFF5252);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFFFF5252);
                break;
            default:
                binding.statusArea.textLocationStatusLabel.setText(getString(R.string.location_status_off));
                binding.statusArea.textLocationStatusLabel.setTextColor(0xFF95B0D4);
                binding.statusArea.imgLocationStatus.setColorFilter(0xFF95B0D4);
                break;
        }
    }

    private void toggleTestMode() {
        if (mIsTestMode) {
            mIsTestMode = false;
            binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_disconnected));
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
            binding.statusArea.textMainBattery.setText("- %");
            binding.statusArea.textMainInbox.setText("0");
            binding.statusArea.textMainOutbox.setText("0");
            binding.headerArea.textHeaderSub.setText("IMEI  -");
            updateSignalBar(0);
            updateLedStatus(false);
            updateTrackButtonUI(false);
            updateSosButtonUI(false);

            deleteTestLocationData();
            deleteTestMessages();
            Toast.makeText(this, "Test mode OFF", Toast.LENGTH_SHORT).show();
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
            binding.statusArea.textBleStatusMain.setText(getString(R.string.ble_status_test_mode));
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFFB300);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFFB300);
            binding.headerArea.textHeaderSub.setText("IMEI  300434061000001");

            ImeiStorage.save(this, "300434061000001");

            insertTestLocationData();
            insertTestMessages();
            Toast.makeText(this, "Test data injected", Toast.LENGTH_SHORT).show();
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

            // 테스트 데이터 - TRACK은 RECV로 표시 (다른 단말이 보낸 것처럼)
            LocationEntity entity = new LocationEntity(
                    0, true, 0x11, TEST_IMEI_TRACK,
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

            // 테스트 데이터 - SOS는 RECV로 표시 (다른 단말이 보낸 것처럼)
            LocationEntity entity = new LocationEntity(
                    0, true, 0x10, TEST_IMEI_SOS,
                    sosCoords[i][0], sosCoords[i][1],
                    0, 0, 0,
                    pastTime, pastTime,
                    false, false, false
            );
            locationViewModel.insert(entity);
        }
    }

    private void deleteTestLocationData() {
        locationViewModel.getAllLocations().observe(this, new androidx.lifecycle.Observer<List<LocationEntity>>() {
            @Override
            public void onChanged(List<LocationEntity> allLocations) {
                if (allLocations == null) return;
                locationViewModel.getAllLocations().removeObserver(this);

                for (LocationEntity loc : allLocations) {
                    if (TEST_IMEI_TRACK.equals(loc.getCodeNum())
                            || TEST_IMEI_SOS.equals(loc.getCodeNum())) {
                        locationViewModel.delete(loc);
                    }
                }
            }
        });
    }

    private void insertTestMessages() {
        String[][] testMsgs = {
                {"Hello",              "Have a wonderful day! :)"},
                {"Weather Check",      "Seoul is currently sunny, 15 degrees."},
                {"Meeting Update",     "Tomorrow's 2pm meeting moved to 3pm."},
                {"Status OK",          "Base camp check-in at 10:30."},
                {"Return Schedule",    "Returning around 18:00 today."},
                {"GPS Confirmed",      "GPS coordinates received normally."},
                {"Weather Alert",      "Heavy rain expected after 16:00."},
                {"Dinner Ready",       "Dinner will be ready at 7pm."},
                {"Signal Test",        "Communication test - signal good."},
                {"Operation Briefing", "Operation briefing tomorrow at 08:00."}
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
            msgViewModel.insert(msg, success -> null);
        }
    }

    private void deleteTestMessages() {
        msgViewModel.getAllMsgs().observe(this, new androidx.lifecycle.Observer<List<MsgEntity>>() {
            @Override
            public void onChanged(List<MsgEntity> allMsgs) {
                if (allMsgs == null) return;
                msgViewModel.getAllMsgs().removeObserver(this);

                for (MsgEntity m : allMsgs) {
                    if (TEST_IMEI_MSG.equals(m.getCodeNum())) {
                        msgViewModel.delete(m);
                    }
                }
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
    //   Back Pressed
    // ═════════════════════════════════════════════════════════════

    @Override
    public void onBackPressed() {
        Fragment currentTab = getSupportFragmentManager().findFragmentById(R.id.tab_container);
        if (currentTab != null && currentTab.getChildFragmentManager().getBackStackEntryCount() > 0) {
            currentTab.getChildFragmentManager().popBackStack();
            return;
        }

        // Exit App dialog (localized)
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_exit_title))
                .setMessage(getString(R.string.dialog_exit_message))
                .setPositiveButton(getString(R.string.btn_exit), (d, w) -> super.onBackPressed())
                .setNegativeButton(getString(R.string.btn_cancel), null)
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
                    Log.d("debug", "permission granted");
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
        unregisterAutoRecvReceiver();
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(true);
        registerAutoRecvReceiver();

        // [전술지도] 전송 대기중이면 채팅 탭으로 전환
        if (com.ah.acr.messagebox.TacticalShare.hasPending()) {
            if (binding.bottomNav.getSelectedItemId() != R.id.tab_chat) {
                binding.bottomNav.setSelectedItemId(R.id.tab_chat);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        com.ah.acr.messagebox.service.TytoConnectService.setActivityAlive(false);
    }


    // ═════════════════════════════════════════════════════════════
    //   AutoRecv Broadcast Receiver
    // ═════════════════════════════════════════════════════════════

    private android.content.BroadcastReceiver mAutoRecvReceiver;

    private void registerAutoRecvReceiver() {
        if (mAutoRecvReceiver != null) return;

        mAutoRecvReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (intent == null) return;
                String action = intent.getAction();

                if (com.ah.acr.messagebox.service.TytoConnectService
                        .BROADCAST_AUTO_RECV_STARTED.equals(action)) {
                    mIsAutoReceiving = true;
                    mLastAutoReceiveTime = System.currentTimeMillis();
                    startAutoReceiveAnimation();
                } else if (com.ah.acr.messagebox.service.TytoConnectService
                        .BROADCAST_AUTO_RECV_COMPLETED.equals(action)) {
                    mIsAutoReceiving = false;
                    mLastInboxCount = 0;
                    stopAutoReceiveAnimation();
                }
            }
        };

        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(com.ah.acr.messagebox.service.TytoConnectService.BROADCAST_AUTO_RECV_STARTED);
        filter.addAction(com.ah.acr.messagebox.service.TytoConnectService.BROADCAST_AUTO_RECV_COMPLETED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mAutoRecvReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mAutoRecvReceiver, filter);
        }
    }

    private void unregisterAutoRecvReceiver() {
        if (mAutoRecvReceiver != null) {
            try {
                unregisterReceiver(mAutoRecvReceiver);
            } catch (Exception e) {
                // ignore
            }
            mAutoRecvReceiver = null;
        }
    }

    boolean checkExternalStorage() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }


    // ═════════════════════════════════════════════════════════════
    //   Location Parsing
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


    // ═════════════════════════════════════════════════════════════
    //   ⭐ Phase 5-P 후속 패치 (2026-05-04): isIncomeLoc 의미 정리
    //
    //   LocationEntity.isIncomeLoc 의미:
    //   - true  = 수신 메시지 (다른 단말 → 나에게)   → MapTabFragment의 if 분기
    //   - false = 송신 메시지 (내가 → 다른 단말)     → MapTabFragment의 else 분기
    //
    //   판정 기준 (ver 값):
    //   - RECV mode: 0x10(SOS), 0x11(CAR), 0x12(UAV), 0x13(UAT), 0x17(FREE)
    //   - SEND mode: 0x00(SOS), 0x01(CAR), 0x02(UAV), 0x03(UAT), 0x07(FREE)
    //
    //   기존 버그: isMyEcho를 isIncomeLoc 자리에 넣어서
    //              ATAK이 보낸 SOS(0x10)가 "My Transmit"으로 잘못 표시됨
    // ═════════════════════════════════════════════════════════════

    /**
     * ⭐ Phase 5-P 후속 패치: ver가 RECV mode인지 판정.
     * LocationEntity.isIncomeLoc 결정에 사용.
     *
     * @param ver 메시지 mode byte
     * @return true=수신(0x10/11/12/13/17), false=송신(0x00/01/02/03/07)
     */
    // addr 정규화: SERVER/null/빈값 → "" (서버행, addr 길이 0). 그 외는 상대 IMEI 그대로.
    //   단문/대용량/사진/파일 등 모든 0x07 송신부가 codeNum을 이 메서드에 통과시킨다.
    public static String addrForSend(String codeNum) {
        if (codeNum == null) return "";
        if ("SERVER".equals(codeNum)) return "";
        return codeNum;
    }

    private static boolean isRecvMode(int ver) {
        return ver == 0x10 || ver == 0x11
            || ver == 0x12 || ver == 0x13
            || ver == 0x17;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 대용량 메시지 분할 (서버 MessageSplitter와 동일 규칙)
    //   - UTF-8 기준 200바이트 이하
    //   - 코드포인트 경계 보존 (한글/이모지 중간에서 안 자름)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private static final int LARGE_CHUNK_BYTES = 200;

    /** 본문을 UTF-8 200바이트 이하 조각들로 분할 (문자 경계 보존). */
    private java.util.List<String> splitLargeMessage(String text) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        StringBuilder current = new StringBuilder();
        int currentBytes = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int cpCharCount = Character.charCount(cp);
            String ch = text.substring(i, i + cpCharCount);
            int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;

            // 현재 조각에 더 넣으면 200B 초과 → 조각 마감하고 새로 시작
            if (currentBytes + chBytes > LARGE_CHUNK_BYTES) {
                chunks.add(current.toString());
                current.setLength(0);
                currentBytes = 0;
            }
            current.append(ch);
            currentBytes += chBytes;
            i += cpCharCount;
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /** 0~255 범위 msgId 생성 (서버 nextChunkMsgId와 동일 방식). */
    private int nextLargeMsgId() {
        return (int) (System.currentTimeMillis() % 256);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 대용량 메시지 송신 (MO) — 본문을 ~L:T 조각으로 쪼개 순차 전송
    //   awaitAckEcho 패턴 재사용: 조각마다 모뎀 수락(SENDING=id,OK) 확인 후 다음 조각
    //   BLE write 충돌 방지를 위해 반드시 순차(한 건씩 대기)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    /**
     * 대용량 메시지 전송 진입점.
     * @param recipientImei 수신처 IMEI (서버행이면 ""=빈주소)
     * @param fullText      보낼 전체 본문
     */
    public void sendLargeMessage(final String recipientImei, final String fullText) {
        sendLargeMessage(recipientImei, fullText, 'T');
    }

    // 전술 데이터 전송용 (~L:G:). type='G'
    public void sendLargeTactical(final String recipientImei, final String payload) {
        // [tacticalMerge] 요약 말풍선 생성 (원문은 사람이 못 읽으므로)
        int mC = 0, lC = 0, rC = 0;
        if (payload != null) {
            for (String seg : payload.split(";")) {
                if (seg.startsWith("M:")) mC++;
                else if (seg.startsWith("L:")) lC++;
                else if (seg.startsWith("R:")) rC++;
            }
        }
        String summary = "[Tactical] markers " + mC + ", lines " + lC + ", measures " + rC;
        sendLargeMsg(recipientImei, payload, 'G', summary);
    }

    public void sendLargeMessage(final String recipientImei, final String fullText, final char lmType) {
        final java.util.List<String> chunks = splitLargeMessage(fullText);
        if (chunks.isEmpty()) return;
        final int msgId = nextLargeMsgId();
        final int total = chunks.size();

        android.util.Log.d("LARGE-MSG", "📤 대용량 송신 시작 to=" + recipientImei
                + " msgId=" + msgId + " 조각수=" + total + " 바이트="
                + fullText.getBytes(StandardCharsets.UTF_8).length);

        // 전체 송신을 별도 스레드 1개에서 순차 처리 (조각 간 충돌 방지)
        new Thread(() -> {
            for (int seq = 0; seq < total; seq++) {
                String title = "~L:" + lmType + ":" + msgId + ":" + seq + ":" + total;
                String body = chunks.get(seq);
                // SENDING id: 700~ 대역 (단문/ACK와 안 겹치게). 700 + seq (조각당 구분)
                int sendId = 700 + (seq & 0xFF);
                boolean ok = sendOneChunkBlocking(recipientImei, title, body, sendId, msgId, seq, total);
                if (!ok) {
                    Log.e("LARGE-MSG", "❌ 조각 송신 실패 msgId=" + msgId + " seq=" + seq
                            + " → 중단");
                    return;   // 한 조각 실패하면 중단 (재전송 정책은 추후)
                }
            }
            android.util.Log.d("LARGE-MSG", "✅ 대용량 전체 송신 완료 msgId=" + msgId
                    + " 조각=" + total);
        }, "large-send-" + msgId).start();
    }

    /**
     * 조각 1개를 0x07 FREE 패킷으로 빌드해 BLE 전송하고, 모뎀 수락(SENDING=id,OK)을 기다린다.
     * awaitAckEcho와 동일 메커니즘 (블로킹). 이미 워커 스레드 안에서 호출됨.
     * @return 모뎀 수락 확인 시 true
     */
    private boolean sendOneChunkBlocking(final String recipientImei, final String title,
                                         final String body, final int sendId,
                                         final int msgId, final int seq, final int total) {
        // 0x07 FREE 패킷 빌드: [0x07][addrLen][addr][titleLen][title][memoLen][memo]
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(0x07);
        String addr = recipientImei == null ? "" : recipientImei;
        buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
        buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
        buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(title, StandardCharsets.UTF_8);
        buffer.writeByte(body.getBytes(StandardCharsets.UTF_8).length);
        buffer.writeCharSequence(body, StandardCharsets.UTF_8);

        byte[] packet = new byte[buffer.readableBytes()];
        buffer.readBytes(packet);
        final String sms = String.format("SENDING=%d,%s",
                sendId, Base64.encodeToString(packet, Base64.NO_WRAP));

        final Object lock = new Object();
        final boolean[] acked = {false};
        final boolean[] armed = {false};
        final androidx.lifecycle.Observer<String> obs = sReceive -> {
            if (!armed[0] || sReceive == null || !sReceive.startsWith("SENDING=")) return;
            String[] vals = sReceive.substring(8).split(",");
            if (vals.length >= 2 && "OK".equals(vals[1])
                    && String.valueOf(sendId).equals(vals[0])) {
                synchronized (lock) {
                    acked[0] = true;
                    lock.notifyAll();
                }
            }
        };
        runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().observeForever(obs));
        try {
            for (int attempt = 1; attempt <= ACK_MAX_ATTEMPTS; attempt++) {
                final int a = attempt;
                runOnUiThread(() -> {
                    BLE.INSTANCE.getWriteQueue().offer(sms);
                    armed[0] = true;
                    android.util.Log.d("LARGE-MSG", "📤 조각 송신 msgId=" + msgId
                            + " seq=" + seq + "/" + (total - 1) + " 시도 " + a
                            + " sendId=" + sendId);
                });
                synchronized (lock) {
                    if (acked[0]) break;
                    lock.wait(ACK_ECHO_TIMEOUT_MS);
                    if (acked[0]) break;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().removeObserver(obs));
        }
        return acked[0];
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    //  대용량/단문 수신확인 ACK MO 송신
    //  - 조립 완성 시 호출
    //  - title="~A:<msgId>", memo=빈값, 0x07 FREE 프레임
    //  - SENDING id는 일반 채팅(작은 수)과 안 겹치게 9000번대 사용
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    /** 인박스 배수 완료(RECEIVED=0,0) 후 호출. 대기 서버-ACK 송신해 SENDING/RECEIVED=? 경쟁 방지. */
    private void flushPendingServerAcks() {
        if (mPendingServerAckIds.isEmpty()) return;
        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>(mPendingServerAckIds);
        mPendingServerAckIds.clear();
        for (int id : ids) {
            android.util.Log.d("ACK", "지연 서버 ACK flush msgId=" + id);
            sendLargeMsgAck("", id);
        }
        // [gap-fill 1-C] 대기 중인 ~R: 재요청도 함께 flush (인박스 배수 후, ACK와 동일 타이밍)
        if (!mPendingGapReqs.isEmpty()) {
            java.util.ArrayList<String> reqs = new java.util.ArrayList<>(mPendingGapReqs);
            mPendingGapReqs.clear();
            for (String key : reqs) {
                try {
                    String[] kv = key.split(":");
                    int rId = Integer.parseInt(kv[0]); int rSeq = Integer.parseInt(kv[1]);
                    android.util.Log.d("GAP-FILL", "지연 ~R: flush " + key);
                    sendGapFillRequest(rId, rSeq);
                } catch (Exception ex) { Log.e("GAP-FILL", "flush 파싱 실패 key=" + key); }
            }
        }
    }

    // ============================================================
    //  [gap-fill 1-C] ~R: 빠진 조각 재요청 송신 (서버행). sendLargeMsgAck 패턴 복제.
    //    프레임: [0x07][addr=빈(서버행)][title="~R:msgId:seq"][memo=빈] + SENDING + 모뎀에코
    //    상한/쿨다운은 호출 전(enqueueGapFillRequests)에서 거른다. 여기선 순수 송신.
    // ============================================================
    // [대용량 TRACK] 송신 조각마다 호출 — 활동 시각만 갱신(장시간 송신 중 60초 조기복원 방지).
    private void touchLargeActivity() {
        mLastLargeActivityAt = System.currentTimeMillis();
    }
    // [대용량 TRACK] 대용량 조각 수신 시 호출. 첫 조각이면 TRACK OFF(LOCATION=3), 활동 시각 갱신.
    private void onLargeActivity() {
        mLastLargeActivityAt = System.currentTimeMillis();
        if (!mTrackPausedForLarge && mIsTrackingMode) {
            BLE.INSTANCE.getWriteQueue().offer("LOCATION=3");   // Tracking Stop
            mTrackPausedForLarge = true;
            android.util.Log.d("TRACK-PAUSE", "대용량 수신 시작 → TRACK OFF (LOCATION=3)");
            mSyncHandler.postDelayed(mTrackRestoreCheck, 10000);   // 10초마다 복원 체크
        }
    }

    // [대용량 TRACK] 60초간 대용량 조각 없으면 TRACK 복원(LOCATION=2). 완성/실패 무관하게 안전 복원.
    private final Runnable mTrackRestoreCheck = new Runnable() {
        @Override public void run() {
            if (mTrackPausedForLarge
                    && System.currentTimeMillis() - mLastLargeActivityAt > 60000) {
                BLE.INSTANCE.getWriteQueue().offer("LOCATION=2");   // Tracking Start
                mTrackPausedForLarge = false;
                android.util.Log.d("TRACK-PAUSE", "대용량 60초 무활동 → TRACK 복원 (LOCATION=2)");
            }
            if (mTrackPausedForLarge) {
                mSyncHandler.postDelayed(this, 10000);
            }
        }
    };
    private void sendGapFillRequest(int msgId, int seq) {
        try {
            String reqTitle = "~R:" + msgId + ":" + seq;
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeByte(0x07);
            String addr = "";   // 서버행 (addrLen=0)
            buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
            buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
            buffer.writeByte(reqTitle.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(reqTitle, StandardCharsets.UTF_8);
            String memo = "";
            buffer.writeByte(memo.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(memo, StandardCharsets.UTF_8);
            byte[] body = new byte[buffer.readableBytes()];
            buffer.readBytes(body);
            // SENDING id: 600~ 대역 (ACK 500, 대용량송신 700~ 와 안 겹치게). 600 + seq.
            int sendId = 600 + (seq & 0xFF);
            String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(body, Base64.NO_WRAP));
            android.util.Log.d("GAP-FILL", "~R: 송신 title=" + reqTitle + " sendId=" + sendId);
            // 상한/쿨다운 기록 (실제 송신하는 시점)
            String key = msgId + ":" + seq;
            // [2-A] 카운트는 에코 OK 시에만 증가(awaitAckEcho). 쿨다운은 시도 시점에 기록(연타 방지).
            mGapReqLastAt.put(key, System.currentTimeMillis());
            awaitAckEcho(sms, sendId, msgId, key);   // gapKey=key → 에코 OK 시 카운트 증가
        } catch (Exception e) {
            Log.e("GAP-FILL", "~R: 송신 실패 msgId=" + msgId + " seq=" + seq + " : " + e.getMessage());
        }
    }

    /**
     * [gap-fill 1-C] 버튼에서 호출. 빠진 seq들을 상한/쿨다운 거쳐 지연 큐에 적재.
     * 자동 아님(수동 버튼만). 인박스 배수 후 flush 에서 실제 송신.
     * @return 큐에 새로 적재된 개수 (0이면 상한/쿨다운으로 다 막힘)
     */
    public int enqueueGapFillRequests(int msgId) {
        android.util.Log.d("GAP-FILL", "enqueue 진입 msgId=" + msgId + " missing=" + getMissingSeqs(msgId) + " buf키=" + mLargeMsgBuf.keySet() + " total=" + mLargeMsgTotal + " done=" + mLargeMsgDoneAt.keySet());
        java.util.List<Integer> missing = getMissingSeqs(msgId);
        if (missing.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        int queued = 0;
        for (int seq : missing) {
            String key = msgId + ":" + seq;
            int attempts = mGapReqCount.getOrDefault(key, 0);
            if (attempts >= GAP_REQ_MAX_ATTEMPTS) {
                android.util.Log.d("GAP-FILL", "상한 초과 skip " + key + " (" + attempts + ")");
                continue;
            }
            Long last = mGapReqLastAt.get(key);
            if (last != null && (now - last) < GAP_REQ_COOLDOWN_MS) {
                android.util.Log.d("GAP-FILL", "쿨다운 skip " + key);
                continue;
            }
            if (!mPendingGapReqs.contains(key)) {
                sendGapFillRequest(msgId, seq);   // [A'] 즉시 송신 (지연큐 안 거침 — 버튼은 능동 액션, flush 트리거 불필요)
                queued++;
                android.util.Log.d("GAP-FILL", "~R: 즉시 송신 " + key);
            }
        }
        return queued;
    }

    // ═══ [Step4] MO 재송신: 서버 ~Q:msgId:seq들 받으면 원본에서 그 조각만 다시 보냄 ═══
    //   gap-fill(~R:)의 발신측 대칭. 수동 트리거(감도 보고)로 호출. 3회 상한.
    private final java.util.Map<String,Integer> mMoResendCount = new java.util.HashMap<>();   // "msgId:seq" -> 시도횟수
    private final java.util.Map<Integer,java.util.List<Integer>> mPendingMoResend = new java.util.HashMap<>();   // [Step5] msgId -> 재송신 대기 seq들 (수동 트리거용)
    private final java.util.Map<Integer,Long> mMoResendAt = new java.util.HashMap<>();   // [MO7min] ~Q: 받은 시각 (7분 지연용)
    public static final long MO_STALE_MS = 420000;   // [MO7min] ~Q: 후 7분 지나야 배너 (상행 감도 회복 대기)
    private volatile int mLargeSendingCount = 0;   // [STALE] 대용량 송신 진행 중 카운트 (배너 억제용)
    private static final int MO_RESEND_MAX = 3;

    /** ~Q: 로 받은 seq들을 원본(mSentLargeMsg)에서 꺼내 재송신. 반환=실제 보낸 개수. */
    private int sendMoResend(final int msgId, final java.util.List<Integer> seqs) {
        // [fileMsg MO복구] 이 msgId가 파일/사진이면 파일 재전송으로 위임
        boolean _isFileMsg;
        synchronized (mSentLargeFile) { _isFileMsg = mSentLargeFile.containsKey(msgId); }
        if (_isFileMsg) { return sendMoResendFile(msgId, seqs); }
        final boolean _isTactical; synchronized (mSentTacticalRaw) { _isTactical = mSentTacticalRaw.containsKey(msgId); }
        final String fullText;
        final String recipientImei;
        synchronized (mSentLargeMsg) {
            fullText = _isTactical ? mSentTacticalRaw.get(msgId) : mSentLargeMsg.get(msgId);
            recipientImei = mSentLargeMsgTo.get(msgId);
        }
        if (fullText == null) {
            android.util.Log.w("MO-RESEND", "원본 없음 msgId=" + msgId + " (이미 완료/만료?)");
            return 0;
        }
        final java.util.List<String> parts = splitUtf8(fullText, 200);
        final int total = parts.size();
        final java.util.List<Integer> todo = new java.util.ArrayList<>();
        for (int seq : seqs) {
            if (seq < 0 || seq >= total) continue;
            String key = msgId + ":" + seq;
            int cnt = mMoResendCount.getOrDefault(key, 0);
            if (cnt >= MO_RESEND_MAX) { android.util.Log.d("MO-RESEND", "상한 초과 skip " + key); continue; }
            todo.add(seq);
        }
        if (todo.isEmpty()) return 0;
        // 전체 CRC (seq0 재송신 시 필요)
        final long fullCrc;
        { java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(fullText.getBytes(StandardCharsets.UTF_8)); fullCrc = c.getValue(); }
        new Thread(() -> {
            for (int seq : todo) {
                String body = parts.get(seq);
                long chunkCrc;
                { java.util.zip.CRC32 cc = new java.util.zip.CRC32(); cc.update(body.getBytes(StandardCharsets.UTF_8)); chunkCrc = cc.getValue(); }
                String title = "~L:T:" + msgId + ":" + seq + ":" + total
                        + ":" + (seq == 0 ? String.valueOf(fullCrc) : "") + ":" + chunkCrc;
                io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
                buf.writeByte(0x07);
                String addr = (recipientImei == null) ? "" : recipientImei;
                buf.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
                buf.writeCharSequence(addr, StandardCharsets.US_ASCII);
                buf.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
                buf.writeCharSequence(title, StandardCharsets.UTF_8);
                buf.writeByte(body.getBytes(StandardCharsets.UTF_8).length);
                buf.writeCharSequence(body, StandardCharsets.UTF_8);
                byte[] frame = new byte[buf.readableBytes()]; buf.readBytes(frame);
                int sendId = mLargeSendIdSeq.updateAndGet(p -> p >= 999 ? 800 : p + 1);
                String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(frame, Base64.NO_WRAP));
                boolean ok = sendChunkAwaitEcho(sms, sendId, msgId, seq);
                if (ok) {
                    String key = msgId + ":" + seq;
                    mMoResendCount.merge(key, 1, Integer::sum);
                    android.util.Log.d("MO-RESEND", "재송신 OK seq=" + seq + " (시도 " + mMoResendCount.get(key) + ")");
                } else {
                    android.util.Log.e("MO-RESEND", "재송신 모뎀거부 seq=" + seq);
                }
            }
        }, "mo-resend").start();
        return todo.size();
    }

    /** [fileMsg MO복구] ~Q: 받은 seq들을 보낸 파일(mSentLargeFile)에서 꺼내 재송신. 텍스트 sendMoResend의 파일 버전. */
    private int sendMoResendFile(final int msgId, final java.util.List<Integer> seqs) {
        final byte[] data; final String recipientImei; final String fileName; final char type;
        synchronized (mSentLargeFile) {
            data = mSentLargeFile.get(msgId);
            recipientImei = mSentLargeMsgTo.get(msgId);
            fileName = mSentLargeFileName.getOrDefault(msgId, "");
            Character _t = mSentLargeFileType.get(msgId);
            type = (_t == null) ? 'I' : _t;
        }
        if (data == null) { android.util.Log.w("MO-RESEND", "파일원본 없음 msgId=" + msgId); return 0; }
        final java.util.List<byte[]> parts = splitBytes(data, 200);
        final int total = parts.size();
        final java.util.List<Integer> todo = new java.util.ArrayList<>();
        for (int seq : seqs) {
            if (seq < 0 || seq >= total) continue;
            int cnt = mMoResendCount.getOrDefault(msgId + ":" + seq, 0);
            if (cnt >= MO_RESEND_MAX) { android.util.Log.d("MO-RESEND", "상한 초과 skip " + msgId + ":" + seq); continue; }
            todo.add(seq);
        }
        if (todo.isEmpty()) return 0;
        final long fullCrc;
        { java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(data); fullCrc = c.getValue(); }
        final String marker = (type == 'F') ? "~L:F:" : (type == 'V') ? "~L:V:" : "~L:I:";
        new Thread(() -> {
            for (int seq : todo) {
                byte[] body = parts.get(seq);
                long chunkCrc;
                { java.util.zip.CRC32 cc = new java.util.zip.CRC32(); cc.update(body); chunkCrc = cc.getValue(); }
                // seq0: marker:msgId:seq:total:fullCrc:chunkCrc:fileName, 그외: marker:msgId:seq:total::chunkCrc
                String title = marker + msgId + ":" + seq + ":" + total + ":"
                        + (seq == 0 ? String.valueOf(fullCrc) : "") + ":" + chunkCrc
                        + (seq == 0 ? (":" + fileName) : "");
                io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
                buf.writeByte(0x07);
                String addr = (recipientImei == null) ? "" : recipientImei;
                buf.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
                buf.writeCharSequence(addr, StandardCharsets.US_ASCII);
                buf.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
                buf.writeCharSequence(title, StandardCharsets.UTF_8);
                buf.writeByte(body.length);
                buf.writeBytes(body);   // 바이너리 직접
                byte[] frame = new byte[buf.readableBytes()]; buf.readBytes(frame);
                int sendId = mLargeSendIdSeq.updateAndGet(p -> p >= 999 ? 800 : p + 1);
                String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(frame, Base64.NO_WRAP));
                boolean ok = sendChunkAwaitEcho(sms, sendId, msgId, seq);
                if (ok) {
                    mMoResendCount.merge(msgId + ":" + seq, 1, Integer::sum);
                    android.util.Log.d("MO-RESEND", "[file] 재송신 OK seq=" + seq);
                } else {
                    android.util.Log.e("MO-RESEND", "[file] 재송신 모뎀거부 seq=" + seq);
                }
            }
        }, "mo-resend-file").start();
        return todo.size();
    }

    // ═══ [Step5] MO/MT 배너용 헬퍼 (Fragment 호출) ═══
    /** MO 재송신 대기 상태 조회 → [msgId, 빠진개수, total] or null. */
    public int[] getPendingMoResend(String recipientImei) {
        synchronized (mPendingMoResend) {
            for (java.util.Map.Entry<Integer,java.util.List<Integer>> e : mPendingMoResend.entrySet()) {
                int msgId = e.getKey();
                String to;
                synchronized (mSentLargeMsg) { to = mSentLargeMsgTo.get(msgId); }
                String toNorm = (to == null || to.isEmpty()) ? "" : to;
                String reqNorm = (recipientImei == null) ? "" : recipientImei;
                if (!toNorm.equals(reqNorm)) continue;   // 이 채팅방 대상만
                String full;
                synchronized (mSentLargeMsg) { full = mSentLargeMsg.get(msgId); }
                if (full == null) continue;
                int total = splitUtf8(full, 200).size();
                int missing = e.getValue().size();
                Long atL = mMoResendAt.get(msgId);
                long at = (atL == null) ? 0L : atL;
                long elapsed = System.currentTimeMillis() - at;
                if (elapsed < MO_STALE_MS) continue;   // [MO7min] 7분 안 — 아직 배너 X (상행 감도 회복 대기)
                return new int[]{ msgId, missing, total, (int) elapsed };
            }
        }
        return null;
    }

    /** MO 재송신 수동 트리거 (배너 [Resend]). 대기 seq들 재송신. */
    public int triggerMoResend(int msgId) {
        java.util.List<Integer> seqs;
        synchronized (mPendingMoResend) { seqs = mPendingMoResend.get(msgId); }
        if (seqs == null || seqs.isEmpty()) return 0;
        // [Step5] 수동 재송신은 상한 무시 (사용자 의지 우선). 자동 모드 때만 상한 적용 예정.
        for (int s : seqs) mMoResendCount.remove(msgId + ":" + s);
        int sent = sendMoResend(msgId, new java.util.ArrayList<>(seqs));
        // [fix] 재송신했으면 대기 해제. 재송신분이 또 깨지면 서버가 ~Q: 다시 보냄 → 그때 재기록.
        if (sent > 0) { synchronized (mPendingMoResend) { mPendingMoResend.remove(msgId); } }
        return sent;
    }

    /** MO 포기 (배너 [Discard]). 대기/원본 제거 → 재송신 안 함. */
    public void discardMoResend(int msgId) {
        synchronized (mPendingMoResend) { mPendingMoResend.remove(msgId); }
        synchronized (mSentLargeMsg) { mSentLargeMsg.remove(msgId); mSentLargeMsgTo.remove(msgId); }
        android.util.Log.d("MO-RESEND", "포기(Discard) msgId=" + msgId);
    }

    /** MT gap-fill 포기 (배너 [Discard]). 미완 버퍼 비움 → 재요청 안 함. */
    public void discardGapfill(int msgId) {
        mLargeMsgBuf.remove(msgId);
        mLargeMsgTotal.remove(msgId);
        mLargeMsgLastAt.remove(msgId);
        mLargeMsgDoneAt.put(msgId, System.currentTimeMillis());   // 완료 표시로 재등장 차단
        android.util.Log.d("GAP-FILL", "포기(Discard) msgId=" + msgId);
    }

    /** [STALE] 대용량 송신 진행 중? (배너 억제용 — 위치정보는 sendLargeMsg 무관하니 자동 제외) */
    public boolean isLargeSending() { return mLargeSendingCount > 0; }

    // ═══ [Step2-auto] 자동 재시도 인프라 ═══
    private final android.os.Handler mAutoHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final java.util.Map<Integer,Integer> mAutoMoCount = new java.util.HashMap<>();   // msgId -> 자동 재송신 횟수
    public static final int AUTO_RESEND_MAX = 10;            // 자동 3회 상한
    public static final long AUTO_INTERVAL_DEFAULT_MS = 600000;   // 기본 10분

    /** 설정된 자동 주기(ms). pref 분 단위 → ms. */
    public long getAutoIntervalMs() {
        int min = android.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getInt(AddrssBookFragment.PREF_RESEND_INTERVAL, 10);
        if (min < 1 || min > 120) min = 10;
        return (long) min * 60000L;
    }

    /** 자동 모드 ON? */
    public boolean isAutoResend() {
        return android.preference.PreferenceManager.getDefaultSharedPreferences(getApplicationContext())
                .getBoolean(AddrssBookFragment.PREF_RESEND_AUTO, false);
    }

    /** MO 자동 재시도 횟수 조회 (배너 "Auto-resending N/3" 표시용). */
    public int getAutoMoCount(int msgId) {
        synchronized (mAutoMoCount) { Integer c = mAutoMoCount.get(msgId); return c == null ? 0 : c; }
    }

    /** [Step2-auto] MO 자동 재시도 스케줄 — 10분 후 1회 → 10분마다 → 3회 → 포기. */
    public void scheduleAutoMoResend(final int msgId) {
        synchronized (mAutoMoCount) { if (mAutoMoCount.containsKey(msgId)) return; mAutoMoCount.put(msgId, 0); }   // 이미 스케줄됨
        final long interval = getAutoIntervalMs();
        Runnable task = new Runnable() {
            @Override public void run() {
                int cnt;
                synchronized (mAutoMoCount) { Integer c = mAutoMoCount.get(msgId); if (c == null) return; cnt = c; }
                java.util.List<Integer> seqs;
                synchronized (mPendingMoResend) { seqs = mPendingMoResend.get(msgId); }
                if (seqs == null || seqs.isEmpty()) {   // 이미 완성/해제됨 → 중단
                    synchronized (mAutoMoCount) { mAutoMoCount.remove(msgId); }
                    android.util.Log.d("MO-RESEND", "[auto] msgId=" + msgId + " 완료/해제 → 중단");
                    return;
                }
                if (cnt >= AUTO_RESEND_MAX) {   // 3회 다 씀 → 포기
                    synchronized (mAutoMoCount) { mAutoMoCount.remove(msgId); }
                    synchronized (mPendingMoResend) { mPendingMoResend.remove(msgId); mMoResendAt.remove(msgId); }   // [Step2-auto] 완전 포기 → 수동 배너도 안 뜨게
                    android.util.Log.d("MO-RESEND", "[auto] msgId=" + msgId + " 3회 소진 → 포기");
                    return;
                }
                cnt++;
                synchronized (mAutoMoCount) { mAutoMoCount.put(msgId, cnt); }
                for (int s : seqs) mMoResendCount.remove(msgId + ":" + s);   // 상한 우회
                int sent = sendMoResend(msgId, new java.util.ArrayList<>(seqs));
                android.util.Log.d("MO-RESEND", "[auto] msgId=" + msgId + " " + cnt + "/" + AUTO_RESEND_MAX + " sent=" + sent);
                mAutoHandler.postDelayed(this, interval);   // 다음 회차 예약
            }
        };
        mAutoHandler.postDelayed(task, interval);   // 첫 회도 10분 후 (즉시 X)
        android.util.Log.d("MO-RESEND", "[auto] msgId=" + msgId + " 스케줄 시작 (" + (interval/60000) + "분 주기, 최대 " + AUTO_RESEND_MAX + "회)");
    }

    private final java.util.Map<Integer,Integer> mAutoMtCount = new java.util.HashMap<>();   // MT msgId -> 자동 재요청 횟수

    private final java.util.Set<Integer> mAutoMtGaveUp = new java.util.HashSet<>();   // [fix①] MT 자동 3회 포기 확정 → 폴링 재스케줄 차단

    /** [fix①] MT 자동 재요청 포기 확정? (배너 수동 폴백 판단용) */
    public boolean isAutoMtGaveUp(int msgId) {
        synchronized (mAutoMtGaveUp) { return mAutoMtGaveUp.contains(msgId); }
    }

    public int getAutoMtCount(int msgId) {
        synchronized (mAutoMtCount) { Integer c = mAutoMtCount.get(msgId); return c == null ? 0 : c; }
    }

    /** [Step2-auto] MT gap-fill 자동 재요청 — 10분 후 1회 → 10분마다 → 3회 → 포기. */
    public void scheduleAutoMtGapfill(final int msgId) {
        synchronized (mAutoMtGaveUp) { if (mAutoMtGaveUp.contains(msgId)) return; }   // [fix①] 포기 확정 → 재스케줄 금지
        synchronized (mAutoMtCount) { if (mAutoMtCount.containsKey(msgId)) return; mAutoMtCount.put(msgId, 0); }
        final long interval = getAutoIntervalMs();
        Runnable task = new Runnable() {
            @Override public void run() {
                int cnt;
                synchronized (mAutoMtCount) { Integer c = mAutoMtCount.get(msgId); if (c == null) return; cnt = c; }
                java.util.List<Integer> missing = getMissingSeqs(msgId);
                if (missing == null || missing.isEmpty()) {   // 완성됨 → 중단
                    synchronized (mAutoMtCount) { mAutoMtCount.remove(msgId); }
                    synchronized (mAutoMtGaveUp) { mAutoMtGaveUp.remove(msgId); }   // [fix①] Set 정리(메모리 누수 방지)
                    android.util.Log.d("GAP-FILL", "[auto] msgId=" + msgId + " 완성 → 중단");
                    return;
                }
                if (cnt >= AUTO_RESEND_MAX) {   // 3회 → 포기
                    synchronized (mAutoMtCount) { mAutoMtCount.remove(msgId); }
                    synchronized (mAutoMtGaveUp) { mAutoMtGaveUp.add(msgId); }   // [fix①] 폴링이 못 살리게 박음
                    android.util.Log.d("GAP-FILL", "[auto] msgId=" + msgId + " 3회 소진 → 포기");
                    return;
                }
                // [gapOutboxWait] 위성 모뎀 outbox 적체(미발신>0)면 ~R: 보내봤자 더 밀림. cnt 증가 없이 다음 주기 대기 → 위성 빌 때까지 무한 인내(횟수 소진 방지). 위성 비면 그때 발송하고 카운트.
                {
                    com.ah.acr.messagebox.data.DeviceStatus _gst = mBleViewModel.getDeviceStatus().getValue();
                    int _goutbox = (_gst != null) ? _gst.getOutBox() : 0;
                    if (_goutbox > 0) {
                        android.util.Log.d("GAP-FILL", "[auto] msgId=" + msgId + " 위성 outbox 적체(" + _goutbox + ") - cnt 보존, 다음 주기 대기 (" + cnt + "/" + AUTO_RESEND_MAX + ")");
                        mAutoHandler.postDelayed(this, interval);
                        return;
                    }
                }
                cnt++;
                synchronized (mAutoMtCount) { mAutoMtCount.put(msgId, cnt); }
                // [gapSeqFix] auto는 순차송신(B)으로 — 누락 전부 하나씩, 매번 최신 재계산
                sendGapFillBatch(msgId);   // [batchFix] 빠진 거 묶어서 1건(분할가드)
                android.util.Log.d("GAP-FILL", "[auto] msgId=" + msgId + " " + cnt + "/" + AUTO_RESEND_MAX + " (순차송신 시작)");
                mAutoHandler.postDelayed(this, interval);
            }
        };
        mAutoHandler.postDelayed(task, interval);   // 첫 회 10분 후
        android.util.Log.d("GAP-FILL", "[auto] msgId=" + msgId + " 스케줄 시작 (" + (interval/60000) + "분 주기, 최대 " + AUTO_RESEND_MAX + "회)");
    }

    private void sendLargeMsgAck(String recipientImei, int msgId) {
        try {
            String ackTitle = "~A:" + msgId;

            // ⭐ 주소 구성은 doSendPending(일반 채팅)과 동일 구조:
            //   [0x07][addrLen][addr(US_ASCII)][titleLen][title(UTF-8)][memoLen][memo(UTF-8)]
            //   채팅은 주소 필드에 수신자 IMEI 하나만 넣는다(송신자는 모뎀이 자동 부착).
            //   서버행 ACK는 수신자를 비워서(addrLen=0) 보낸다 → codeNum="" 와 동일 로직.
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeByte(0x07);                                // FREE 모드 송신
            String ackAddr = "";                                   // ★ 수신자 비움 = 서버행 (addrLen=0)
            buffer.writeByte(ackAddr.getBytes(StandardCharsets.US_ASCII).length);   // = 0
            buffer.writeCharSequence(ackAddr, StandardCharsets.US_ASCII);
            buffer.writeByte(ackTitle.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(ackTitle, StandardCharsets.UTF_8);
            String ackMemo = "";                                   // ★ 메모 빈칸 (memoLen=0)
            buffer.writeByte(ackMemo.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(ackMemo, StandardCharsets.UTF_8);

            byte[] body = new byte[buffer.readableBytes()];
            buffer.readBytes(body);

            /// SENDING index는 1~999만 허용(BLE 프로토콜). 500+msgId → 500~755.
            int ackSendId = 500 + (msgId & 0xFF);
            String sms = String.format("SENDING=%d,%s",
                    ackSendId, Base64.encodeToString(body, Base64.NO_WRAP));

            android.util.Log.d("LARGE-MSG", "📤 ACK 송신 to=" + recipientImei
                    + " title=" + ackTitle + " sendId=" + ackSendId + " sms=" + sms);

            // ⭐ doSendPending과 동일하게 모뎀 수락 에코(SENDING=<ackSendId>,OK)를 기다린다.
            //   - 송신은 직렬 펌프(offer)로 유지(동시 write 충돌 방지),
            //   - 대기는 백그라운드 스레드에서(메인 스레드는 블록 불가, 에코도 메인 스레드로 옴).
            awaitAckEcho(sms, ackSendId, msgId);
        } catch (Exception e) {
            Log.e("LARGE-MSG", "ACK 송신 실패 msgId=" + msgId + " : " + e.getMessage());
        }
    }

    /**
     * ACK SENDING을 직렬 펌프로 송신하고, 모뎀의 {@code SENDING=<ackSendId>,OK} 수락 에코를
     * 백그라운드 스레드에서 기다린다. (doSendPending의 outboxMsgStatus + lock.wait 메커니즘 적용)
     * 타임아웃 시 최대 {@link #ACK_MAX_ATTEMPTS}회까지 재송신한다.
     */
    private void awaitAckEcho(final String sms, final int ackSendId, final int msgId) {
        awaitAckEcho(sms, ackSendId, msgId, null);   // gapKey 없음 = 카운트 안 함 (ACK/대용량용)
    }

    // gapKey != null 이면 모뎀 에코 OK 확인 시 mGapReqCount 증가 (gap-fill ~R:/~Q: 전용)
    private void awaitAckEcho(final String sms, final int ackSendId, final int msgId, final String gapKey) {
        new Thread(() -> {
            final Object lock = new Object();
            final boolean[] acked = {false};
            // ★ sticky 가드: observeForever 등록 시 들어오는 이전 잔류값(같은 id)을 오인하지 않도록
            //    "송신 후 도착분"만 인정한다.
            final boolean[] armed = {false};

            final androidx.lifecycle.Observer<String> obs = sReceive -> {
                if (!armed[0] || sReceive == null || !sReceive.startsWith("SENDING=")) return;
                String[] vals = sReceive.substring(8).split(",");
                if (vals.length >= 2 && "OK".equals(vals[1])
                        && String.valueOf(ackSendId).equals(vals[0])) {
                    synchronized (lock) {
                        acked[0] = true;
                        lock.notifyAll();
                    }
                }
            };

            // observeForever 등록/해제·offer는 모두 메인 스레드에서 (offer는 @MainThread)
            runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().observeForever(obs));
            try {
                for (int attempt = 1; attempt <= ACK_MAX_ATTEMPTS; attempt++) {
                    final int a = attempt;
                    runOnUiThread(() -> {
                        BLE.INSTANCE.getWriteQueue().offer(sms);   // 직렬 펌프로 송신
                        armed[0] = true;                            // 이후 도착하는 에코부터 인정
                        android.util.Log.d("LARGE-MSG", "📤 ACK 송신 시도 " + a + "/"
                                + ACK_MAX_ATTEMPTS + " sendId=" + ackSendId);
                    });
                    synchronized (lock) {
                        if (acked[0]) break;
                        lock.wait(ACK_ECHO_TIMEOUT_MS);
                        if (acked[0]) break;
                    }
                    android.util.Log.d("LARGE-MSG", "⏳ ACK 에코 타임아웃(시도 " + attempt
                            + ") sendId=" + ackSendId);
                }
                if (acked[0]) {
                    android.util.Log.d("LARGE-MSG", "✅ ACK 모뎀 수락 확인 sendId="
                            + ackSendId + " msgId=" + msgId);
                    if (gapKey != null) {
                        mGapReqCount.merge(gapKey, 1, Integer::sum);   // [2-A] 실제 모뎀 수락 시에만 카운트
                        android.util.Log.d("GAP-FILL", "에코 OK → 카운트 " + gapKey + "=" + mGapReqCount.get(gapKey));
                    }
                } else {
                    Log.e("LARGE-MSG", "⚠ ACK 모뎀 수락 실패(에코 없음) sendId="
                            + ackSendId + " msgId=" + msgId);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            } finally {
                runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().removeObserver(obs));
            }
        }, "large-ack-" + msgId).start();
    }

    // ============================================================
    //  [gapSeqFix] gap-fill ~R: 순차 송신 (B방식) — 단일 스레드에서 누락 조각을 하나씩
    //  매 송신 직전 getMissingSeqs 재계산 = "최신 교체"(그새 받은 건 자동 제외, 서버 중복 방지)
    //  에코 OK→다음 / 에코 실패→중단(다음 틱 재시도). 동시송신 충돌 원천 차단.
    //  ※ MT gap-fill 전용. BROAD/위치정보 경로는 안 건드림.
    // ============================================================
    private volatile boolean mGapSeqRunning = false;

    private void sendGapFillSequential(final int msgId) {
        if (mGapSeqRunning) {
            android.util.Log.d("GAP-FILL", "[seq] 이미 실행 중 - skip msgId=" + msgId);
            return;
        }
        new Thread(() -> {
            mGapSeqRunning = true;
            try {
                while (true) {
                    // ★ 매번 최신 누락 재계산 (그새 받은 조각은 자동 제외 = 최신 교체)
                    java.util.List<Integer> missing = getMissingSeqs(msgId);
                    if (missing == null || missing.isEmpty()) {
                        android.util.Log.d("GAP-FILL", "[seq] 완성/없음 - 종료 msgId=" + msgId);
                        break;
                    }
                    long now = System.currentTimeMillis();
                    int target = -1;
                    for (int seq : missing) {
                        String key = msgId + ":" + seq;
                        if (mGapReqCount.getOrDefault(key, 0) >= GAP_REQ_MAX_ATTEMPTS) continue;
                        Long last = mGapReqLastAt.get(key);
                        if (last != null && (now - last) < GAP_REQ_COOLDOWN_MS) continue;
                        target = seq;
                        break;
                    }
                    if (target < 0) {
                        android.util.Log.d("GAP-FILL", "[seq] 보낼 seq 없음(상한/쿨다운) - 종료 msgId=" + msgId);
                        break;
                    }
                    boolean ok = sendGapFillRequestBlocking(msgId, target);
                    if (!ok) {
                        android.util.Log.d("GAP-FILL", "[seq] 에코 실패 - 중단(다음 틱 재시도) " + msgId + ":" + target);
                        break;
                    }
                    android.util.Log.d("GAP-FILL", "[seq] 에코 OK - 다음 조각으로 " + msgId + ":" + target);
                }
            } finally {
                mGapSeqRunning = false;
            }
        }, "gap-seq-" + msgId).start();
    }

    // ============================================================
    //  [batchFix] gap-fill ~R: 묶음 송신 — 빠진 seq들을 한 ~R:로 묶어 1건 송신
    //  ~R:msgId:0,1,2,...  (서버 batchSeqFix가 콤마 split해 다 relay)
    //  매번 최신 missing 재계산(최신 교체). title 300바이트 초과 시 분할.
    //  에코 OK→남은 거 다음 묶음 / 에코 실패→중단(다음 틱). MT 전용, 위치정보 무관.
    // ============================================================
    private static final int GAP_BATCH_TITLE_MAX = 300;   // ~R: title 안전 한도(SBD MO 340 여유)

    private void sendGapFillBatch(final int msgId) {
        if (mGapSeqRunning) {
            android.util.Log.d("GAP-FILL", "[batch] 이미 실행 중 - skip msgId=" + msgId);
            return;
        }
        new Thread(() -> {
            mGapSeqRunning = true;
            try {
                while (true) {
                    // ★ 매번 최신 누락 재계산 (받은 건 자동 제외)
                    java.util.List<Integer> missing = getMissingSeqs(msgId);
                    if (missing == null || missing.isEmpty()) {
                        android.util.Log.d("GAP-FILL", "[batch] 완성/없음 - 종료 msgId=" + msgId);
                        break;
                    }
                    long now = System.currentTimeMillis();
                    // 상한/쿨다운 통과 seq만 모으되, title 길이 한도 내에서 묶음
                    java.util.List<Integer> toSend = new java.util.ArrayList<>();
                    StringBuilder sb = new StringBuilder("~R:").append(msgId).append(":");
                    int baseLen = sb.length();
                    for (int seq : missing) {
                        String key = msgId + ":" + seq;
                        if (mGapReqCount.getOrDefault(key, 0) >= GAP_REQ_MAX_ATTEMPTS) continue;
                        Long last = mGapReqLastAt.get(key);
                        if (last != null && (now - last) < GAP_REQ_COOLDOWN_MS) continue;
                        String add = (toSend.isEmpty() ? "" : ",") + seq;
                        if (sb.length() + add.length() > GAP_BATCH_TITLE_MAX) break;   // 분할: 이번 묶음은 여기까지
                        sb.append(add);
                        toSend.add(seq);
                    }
                    if (toSend.isEmpty()) {
                        android.util.Log.d("GAP-FILL", "[batch] 보낼 seq 없음(상한/쿨다운) - 종료 msgId=" + msgId);
                        break;
                    }
                    // [outboxGuard] 이전 ~R:이 아직 위성에 못 나갔으면(outbox>0) 새로 안 보냄(쌓임 방지). 모뎀 outbox에 ~R: 1개만 유지 → 감도 회복 시 최신 1개만 발신.
                    com.ah.acr.messagebox.data.DeviceStatus _st = mBleViewModel.getDeviceStatus().getValue();
                    int _outbox = (_st != null) ? _st.getOutBox() : 0;
                    if (_outbox > 0) {
                        android.util.Log.d("GAP-FILL", "[batch] outbox \uC801\uCCB4(" + _outbox + ") - \uC774\uC804 \uC1A1\uC2E0\uBB3C \uBBF8\uBC1C\uC2E0, \uC774\uBC88 \uC8FC\uAE30 skip(\uC30C\uC784\uBC29\uC9C0). \uB2E4\uC74C \uD2F1\uC5D0 \uCD5C\uC2E0 \uB204\uB77D \uC7AC\uACC4\uC0B0.");
                        break;
                    }
                    boolean ok = sendGapFillBatchRequest(msgId, sb.toString(), toSend);
                    if (!ok) {
                        android.util.Log.d("GAP-FILL", "[batch] 에코 실패 - 중단(다음 틱 재시도) msgId=" + msgId + " seqs=" + toSend);
                        break;
                    }
                    android.util.Log.d("GAP-FILL", "[batch] 에코 OK - seqs=" + toSend + " (남은 거 있으면 다음 묶음)");
                    // 분할로 일부만 보낸 경우 while 계속 → 다음 묶음 (쿨다운 걸려 다음 틱으로 갈 수도)
                }
            } finally {
                mGapSeqRunning = false;
            }
        }, "gap-batch-" + msgId).start();
    }

    /** [batchFix] ~R:묶음(title) 1건 송신 + 에코 대기. 에코 OK 시 묶인 seq 전부 카운트/쿨다운 기록. */
    private boolean sendGapFillBatchRequest(int msgId, String reqTitle, java.util.List<Integer> seqs) {
        try {
            io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
            buffer.writeByte(0x07);
            String addr = "";
            buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
            buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
            buffer.writeByte(reqTitle.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(reqTitle, StandardCharsets.UTF_8);
            String memo = "";
            buffer.writeByte(memo.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(memo, StandardCharsets.UTF_8);
            byte[] body = new byte[buffer.readableBytes()];
            buffer.readBytes(body);
            int sendId = 600;   // 묶음은 단일 sendId (600). seq별 안 나눔
            String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(body, Base64.NO_WRAP));
            long now = System.currentTimeMillis();
            for (int seq : seqs) { mGapReqLastAt.put(msgId + ":" + seq, now); }   // 쿨다운 기록(시도 시점)
            android.util.Log.d("GAP-FILL", "[batch] ~R: 송신 title=" + reqTitle + " sendId=" + sendId);
            boolean ok = awaitAckEchoBlocking(sms, sendId, msgId, null);   // gapKey=null: 카운트는 아래서 직접
            if (ok) {
                for (int seq : seqs) { mGapReqCount.merge(msgId + ":" + seq, 1, Integer::sum); }   // 에코 OK 시 묶인 seq 전부 +1
                android.util.Log.d("GAP-FILL", "[batch] 에코 OK → 카운트 기록 seqs=" + seqs);
            }
            return ok;
        } catch (Exception e) {
            Log.e("GAP-FILL", "[batch] ~R: 송신 실패 msgId=" + msgId + " : " + e.getMessage());
            return false;
        }
    }

    /** [gapSeqFix] ~R: 1개 송신 + 에코 동기 대기. 에코 OK=true. (sendGapFillRequest 프레임과 동일) */
    private boolean sendGapFillRequestBlocking(int msgId, int seq) {
        try {
            String reqTitle = "~R:" + msgId + ":" + seq;
            io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
            buffer.writeByte(0x07);
            String addr = "";
            buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
            buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
            buffer.writeByte(reqTitle.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(reqTitle, StandardCharsets.UTF_8);
            String memo = "";
            buffer.writeByte(memo.getBytes(StandardCharsets.UTF_8).length);
            buffer.writeCharSequence(memo, StandardCharsets.UTF_8);
            byte[] body = new byte[buffer.readableBytes()];
            buffer.readBytes(body);
            int sendId = 600 + (seq & 0xFF);
            String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(body, Base64.NO_WRAP));
            String key = msgId + ":" + seq;
            android.util.Log.d("GAP-FILL", "[seq] ~R: 송신 title=" + reqTitle + " sendId=" + sendId);
            mGapReqLastAt.put(key, System.currentTimeMillis());
            return awaitAckEchoBlocking(sms, sendId, msgId, key);
        } catch (Exception e) {
            Log.e("GAP-FILL", "[seq] ~R: 송신 실패 msgId=" + msgId + " seq=" + seq + " : " + e.getMessage());
            return false;
        }
    }

    /** [gapSeqFix] awaitAckEcho의 동기(블로킹) 버전. 현재 스레드에서 에코 대기, OK=true 반환. */
    private boolean awaitAckEchoBlocking(final String sms, final int ackSendId, final int msgId, final String gapKey) {
        final Object lock = new Object();
        final boolean[] acked = {false};
        final boolean[] armed = {false};
        final androidx.lifecycle.Observer<String> obs = sReceive -> {
            if (!armed[0] || sReceive == null || !sReceive.startsWith("SENDING=")) return;
            String[] vals = sReceive.substring(8).split(",");
            if (vals.length >= 2 && "OK".equals(vals[1])
                    && String.valueOf(ackSendId).equals(vals[0])) {
                synchronized (lock) {
                    acked[0] = true;
                    lock.notifyAll();
                }
            }
        };
        runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().observeForever(obs));
        try {
            for (int attempt = 1; attempt <= ACK_MAX_ATTEMPTS; attempt++) {
                final int a = attempt;
                runOnUiThread(() -> {
                    BLE.INSTANCE.getWriteQueue().offer(sms);
                    armed[0] = true;
                    android.util.Log.d("GAP-FILL", "[seq] 송신 시도 " + a + "/" + ACK_MAX_ATTEMPTS + " sendId=" + ackSendId);
                });
                synchronized (lock) {
                    if (acked[0]) break;
                    lock.wait(ACK_ECHO_TIMEOUT_MS);
                    if (acked[0]) break;
                }
                android.util.Log.d("GAP-FILL", "[seq] 에코 타임아웃(시도 " + attempt + ") sendId=" + ackSendId);
            }
            if (acked[0] && gapKey != null) {
                mGapReqCount.merge(gapKey, 1, Integer::sum);
                android.util.Log.d("GAP-FILL", "[seq] 에코 OK → 카운트 " + gapKey + "=" + mGapReqCount.get(gapKey));
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().removeObserver(obs));
        }
        return acked[0];
    }

    // ============================================================
    //  대용량 MO 송신 (앱 -> 서버) : 원문을 ~L:T:msgId:seq:total 조각으로 분할 전송
    // ============================================================
    public void sendLargeMsg(final String fullText) { sendLargeMsg("", fullText); }

    // [tacticalMerge] 2-인자 = 텍스트 기본 (말풍선=본문, type='T')
    public void sendLargeMsg(final String recipientImei, final String fullText) {
        sendLargeMsg(recipientImei, fullText, 'T', fullText);
    }

    // [tacticalMerge] 4-인자 핵심: lmType(T/G), bubbleBody(말풍선/ACK용)
    //   wire=fullText(원문), 말풍선=bubbleBody(요약 가능). 재시도/ACK/CRC/~Q: 전부 공유.
    public void sendLargeMsg(final String recipientImei, final String fullText,
                             final char lmType, final String bubbleBody) {
        if (fullText == null || fullText.isEmpty()) {
            Log.e("LARGE-MSG", "sendLargeMsg: 본문 없음");
            return;
        }
        new Thread(() -> {
            try {
                java.util.List<String> parts = splitUtf8(fullText, 200);
                final int total = parts.size();
                if (total > 255) {
                    Log.e("LARGE-MSG", "조각 너무 많음: " + total + " > 255");
                    return;
                }
                final int msgId = mLargeMsgIdSeq.getAndUpdate(p -> (p + 1) & 0xFF);

                synchronized (mSentLargeMsg) {
                    if (lmType == 'G') { mSentTacticalRaw.put(msgId, fullText); mSentLargeMsg.put(msgId, bubbleBody); } else { mSentLargeMsg.put(msgId, fullText); }
                    mSentLargeMsgTo.put(msgId, recipientImei == null ? "" : recipientImei);
                }

                // ★ 보내는 즉시 말풍선 DB 생성 (미전송 상태). 원문 DB 영구저장 → RAM소실 무관.
                //   ~A: 오면 is_send=1(✓), ~D: 오면 is_device_send=1(✓✓)로 업데이트만.
                final String bubbleTo = (recipientImei == null || recipientImei.isEmpty())
                        ? "SERVER" : recipientImei;
                runOnUiThread(() -> {
                    MsgEntity sendingMsg = new MsgEntity(0, true, bubbleTo, "", bubbleBody,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            true, true, false);   // isRead=true, isSend=true(대용량은 조각으로 직접 전송하므로 단문큐에 안 잡히게), isDeviceSend=false
                    msgViewModel.insert(sendingMsg);
                });

                mLargeSendingCount++;   // [STALE] 송신 시작
                runOnUiThread(this::onLargeActivity);   // [대용량 TRACK] 송신 시작 → TRACK OFF
                android.util.Log.d("LARGE-MSG", "TX large start msgId=" + msgId
                        + " total=" + total
                        + " bytes=" + fullText.getBytes(StandardCharsets.UTF_8).length);

                // [Step1] 무결성(large) 설정 — ON일 때만 CRC 부착 (OFF면 기존 형식, 하위호환)
                boolean _integrityOn = android.preference.PreferenceManager
                        .getDefaultSharedPreferences(getApplicationContext())
                        .getBoolean(AddrssBookFragment.PREF_INTEGRITY_LARGE, true);
                // [2-B] 전체 본문 CRC32 (서버 LargeMessageService.crc32()와 동일: CRC32 + UTF-8 bytes)
                long fullCrc;
                {
                    java.util.zip.CRC32 _crc = new java.util.zip.CRC32();
                    _crc.update(fullText.getBytes(StandardCharsets.UTF_8));
                    fullCrc = _crc.getValue();
                }
                android.util.Log.d("LARGE-MSG", "TX crc=" + fullCrc + " msgId=" + msgId);
                // [txGapFix2] 첫 조각 전 큐 배수 여유 (seq0이 직전 BROAD/상태 송신과 겹쳐 echo 타임아웃 나는 것 방지)
                try { Thread.sleep(1500); } catch (InterruptedException _ie2) { Thread.currentThread().interrupt(); }
                for (int seq = 0; seq < total; seq++) {
                    String body = parts.get(seq);
                    // [2-B] 첫 조각(seq=0)에만 CRC 부착: ~L:T:msgId:0:total:crc (나머지는 그대로)
                    // [Step1] 조각 CRC(chunkCrc) 계산 + 제목 부착
                    //   무결성 ON: seq0=~L:T:m:0:total:fullCrc:chunkCrc / seq>0=~L:T:m:seq:total::chunkCrc
                    //   무결성 OFF: ~L:T:m:seq:total (기존, CRC 없음)
                    String title;
                    if (_integrityOn) {
                        long chunkCrc;
                        {
                            java.util.zip.CRC32 _cc = new java.util.zip.CRC32();
                            _cc.update(body.getBytes(StandardCharsets.UTF_8));
                            chunkCrc = _cc.getValue();
                        }
                        title = "~L:" + lmType + ":" + msgId + ":" + seq + ":" + total
                                + ":" + (seq == 0 ? String.valueOf(fullCrc) : "")
                                + ":" + chunkCrc;
                        android.util.Log.d("LARGE-MSG", "TX chunkCrc seq=" + seq + " crc=" + chunkCrc);
                    } else {
                        title = "~L:" + lmType + ":" + msgId + ":" + seq + ":" + total;
                    }

                    ByteBuf buffer = Unpooled.buffer();
                    buffer.writeByte(0x07);
                    String addr = (recipientImei == null) ? "" : recipientImei;
                    buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
                    buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
                    buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
                    buffer.writeCharSequence(title, StandardCharsets.UTF_8);
                    buffer.writeByte(body.getBytes(StandardCharsets.UTF_8).length);
                    buffer.writeCharSequence(body, StandardCharsets.UTF_8);

                    byte[] frame = new byte[buffer.readableBytes()];
                    buffer.readBytes(frame);

                    int sendId = mLargeSendIdSeq.updateAndGet(p -> p >= 999 ? 800 : p + 1);
                    String sms = String.format("SENDING=%d,%s",
                            sendId, Base64.encodeToString(frame, Base64.NO_WRAP));

                    touchLargeActivity();   // [대용량 TRACK] 조각 송신 → 활동 갱신(조기복원 방지)
                    boolean ok = sendChunkAwaitEcho(sms, sendId, msgId, seq);
                    if (!ok) {
                        Log.e("LARGE-MSG", "chunk modem reject msgId=" + msgId
                                + " seq=" + seq + " - abort");
                        // [abortNotify] MO는 수동 일괄송신 — 감도 불량으로 조각 송신 실패 시 자동복구 경로 없음(~Q:는 서버 도착분 한정). 깨끗이 취소하고 사용자에게 재전송 안내. 원본(mSentLargeMsg)은 유지(서버 도착분 ~Q: 대응 가능).
                        final int _abSeq = seq; final int _abMsgId = msgId;
                        runOnUiThread(() -> Toast.makeText(MainActivity.this,
                            "\u26A0 \uC804\uC1A1 \uC2E4\uD328(\uC2E0\uD638 \uBD88\uB7C9, \uC870\uAC01 " + _abSeq + "). \uC2E0\uD638 \uC591\uD638 \uC2DC \uB2E4\uC2DC \uC804\uC1A1\uD558\uC138\uC694.",
                            Toast.LENGTH_LONG).show());
                        return;
                    }
                }
                android.util.Log.d("LARGE-MSG", "TX large all-accepted msgId=" + msgId
                        + " - wait server ~A:");
            } catch (Exception e) {
                Log.e("LARGE-MSG", "sendLargeMsg fail: " + e.getMessage(), e);
            }
            finally { mLargeSendingCount--; }   // [STALE] 송신 종료(성공/실패/중단 모두)
        }, "large-send").start();
    }

    private boolean sendChunkAwaitEcho(final String sms, final int sendId,
                                       final int msgId, final int seq) {
        final Object lock = new Object();
        final boolean[] acked = {false};
        final boolean[] armed = {false};

        final androidx.lifecycle.Observer<String> obs = sReceive -> {
            if (!armed[0] || sReceive == null || !sReceive.startsWith("SENDING=")) return;
            String[] vals = sReceive.substring(8).split(",");
            if (vals.length >= 2 && "OK".equals(vals[1])
                    && String.valueOf(sendId).equals(vals[0])) {
                synchronized (lock) {
                    acked[0] = true;
                    lock.notifyAll();
                }
            }
        };

        runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().observeForever(obs));
        try {
            for (int attempt = 1; attempt <= ACK_MAX_ATTEMPTS; attempt++) {
                final int a = attempt;
                runOnUiThread(() -> {
                    BLE.INSTANCE.getWriteQueue().offer(sms);
                    armed[0] = true;
                    android.util.Log.d("LARGE-MSG", "TX chunk try " + a + "/"
                            + ACK_MAX_ATTEMPTS + " sendId=" + sendId + " seq=" + seq);
                });
                synchronized (lock) {
                    if (acked[0]) break;
                    lock.wait(ACK_ECHO_TIMEOUT_MS);
                    if (acked[0]) break;
                }
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } finally {
            runOnUiThread(() -> BLE.INSTANCE.getOutboxMsgStatus().removeObserver(obs));
        }
        return acked[0];
    }

    private java.util.List<String> splitUtf8(String text, int maxBytes) {
        java.util.List<String> chunks = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int curBytes = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int cc = Character.charCount(cp);
            String ch = text.substring(i, i + cc);
            int chBytes = ch.getBytes(StandardCharsets.UTF_8).length;
            if (curBytes + chBytes > maxBytes && cur.length() > 0) {
                chunks.add(cur.toString());
                cur.setLength(0);
                curBytes = 0;
            }
            cur.append(ch);
            curBytes += chBytes;
            i += cc;
        }
        if (cur.length() > 0) chunks.add(cur.toString());
        return chunks;
    }

    // ============================================================
    //  [fileMsg] 바이너리(파일/사진) 조각 분할 — splitUtf8의 byte[] 버전.
    //  텍스트는 UTF-8 경계를 지켜야 하지만, 바이너리는 그냥 maxBytes씩 자르면 됨.
    // ============================================================
    private java.util.List<byte[]> splitBytes(byte[] data, int maxBytes) {
        java.util.List<byte[]> chunks = new java.util.ArrayList<>();
        if (data == null || data.length == 0) return chunks;
        for (int off = 0; off < data.length; off += maxBytes) {
            int len = Math.min(maxBytes, data.length - off);
            byte[] chunk = new byte[len];
            System.arraycopy(data, off, chunk, 0, len);
            chunks.add(chunk);
        }
        return chunks;
    }

    // ============================================================
    //  [fileMsg] 파일/사진 MO 송신 (앱 -> 서버). sendLargeMsg의 바이너리 버전.
    //  body가 byte[] (텍스트 아님) → frame도 byte[] → 기존 Base64/SENDING= 경로 재활용.
    //  마커: ~L:F:(파일) / ~L:I:(사진). 파일명은 seq0 title 끝에 부착.
    //    seq0:  ~L:F:msgId:0:total:fullCrc:chunkCrc:fileName
    //    seq>0: ~L:F:msgId:seq:total::chunkCrc
    //  type: 'F'=파일, 'I'=사진. data: 원본 바이너리(10KB 이하, 호출 전 압축/검증).
    // ============================================================
    public void sendLargeFile(final String recipientImei, final byte[] data,
                              final String fileName, final char type,
                              final String bubbleBodyForAck) {   // [ackMedia] 말풍선 본문(content 매칭용)
        if (data == null || data.length == 0) {
            Log.e("FILE-MSG", "sendLargeFile: 데이터 없음");
            return;
        }
            if (type != 'F' && type != 'I' && type != 'V') {
                Log.e("FILE-MSG", "sendLargeFile: 잘못된 type=" + type + " (F/I/V만)");
            return;
        }
        new Thread(() -> {
            try {
                java.util.List<byte[]> parts = splitBytes(data, 200);
                final int total = parts.size();
                if (total > 255) {
                    Log.e("FILE-MSG", "조각 너무 많음: " + total + " > 255 (data=" + data.length + "B)");
                    return;
                }
                final int msgId = mLargeMsgIdSeq.getAndUpdate(p -> (p + 1) & 0xFF);
                final String marker = (type == 'F') ? "~L:F:" : (type == 'V') ? "~L:V:" : "~L:I:";
                final String safeName = (fileName == null) ? "" : fileName.replace(":", "_").replace(",", "_");

                // 전체 CRC32 (바이너리 원본)
                long fullCrc;
                { java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(data); fullCrc = c.getValue(); }
                // [fileMsg MO복구] ~Q:/auto/수동 재전송 위해 원본 byte[]+메타 보관
                synchronized (mSentLargeFile) {
                    mSentLargeFile.put(msgId, data);
                    mSentLargeFileName.put(msgId, safeName);
                    mSentLargeFileType.put(msgId, type);
                    mSentLargeMsgTo.put(msgId, recipientImei == null ? "" : recipientImei);
                    // [ackMedia] 말풍선 본문 보관 → ~A: 수신 시 content 매칭으로 V/VV 표시
                    if (bubbleBodyForAck != null) mSentLargeMsg.put(msgId, bubbleBodyForAck);
                }

                mLargeSendingCount++;
                runOnUiThread(this::onLargeActivity);   // [대용량 TRACK] 파일 송신 시작 → TRACK OFF
                android.util.Log.d("FILE-MSG", "TX file start msgId=" + msgId + " type=" + type
                        + " total=" + total + " bytes=" + data.length + " name=" + safeName
                        + " crc=" + fullCrc);

                try { Thread.sleep(1500); } catch (InterruptedException _ie) { Thread.currentThread().interrupt(); }

                for (int seq = 0; seq < total; seq++) {
                    byte[] body = parts.get(seq);
                    long chunkCrc;
                    { java.util.zip.CRC32 cc = new java.util.zip.CRC32(); cc.update(body); chunkCrc = cc.getValue(); }
                    // seq0: 마커+msgId:0:total:fullCrc:chunkCrc:fileName / seq>0: ...:seq:total::chunkCrc
                    String title;
                    if (type == 'F' && seq == 0) {
                        // [extCode] 파일 seq0: 6자리hex fullCrc + 확장자코드 (title 20byte 한도)
                        String _hex = String.format("%06X", fullCrc & 0xFFFFFFL);
                        title = marker + msgId + ":" + seq + ":" + total + ":" + _hex + extToCode(safeName);
                    } else {
                        title = marker + msgId + ":" + seq + ":" + total
                                + ":" + (seq == 0 ? String.valueOf(fullCrc) : "")
                                + ":" + chunkCrc
                                + (seq == 0 ? (":" + safeName) : "");
                    }
                    android.util.Log.d("FILE-MSG", "TX chunk seq=" + seq + " len=" + body.length + " crc=" + chunkCrc);

                    io.netty.buffer.ByteBuf buffer = io.netty.buffer.Unpooled.buffer();
                    buffer.writeByte(0x07);
                    String addr = (recipientImei == null) ? "" : recipientImei;
                    buffer.writeByte(addr.getBytes(StandardCharsets.US_ASCII).length);
                    buffer.writeCharSequence(addr, StandardCharsets.US_ASCII);
                    buffer.writeByte(title.getBytes(StandardCharsets.UTF_8).length);
                    buffer.writeCharSequence(title, StandardCharsets.UTF_8);
                    buffer.writeByte(body.length);          // bodyLen (1바이트, ≤200 OK)
                    buffer.writeBytes(body);                 // ★ 바이너리 body 직접 (String 변환 없음)

                    byte[] frame = new byte[buffer.readableBytes()];
                    buffer.readBytes(frame);

                    int sendId = mLargeSendIdSeq.updateAndGet(p -> p >= 999 ? 800 : p + 1);
                    String sms = String.format("SENDING=%d,%s", sendId, Base64.encodeToString(frame, Base64.NO_WRAP));

                    touchLargeActivity();   // [대용량 TRACK] 파일 조각 송신 → 활동 갱신(조기복원 방지)
                    boolean ok = sendChunkAwaitEcho(sms, sendId, msgId, seq);
                    if (!ok) {
                        // [fileRetry] 일시적 감도 불량 극복: 실패 조각 5초 간격 3회 재시도 후 계속.
                        int _retry = 0;
                        while (!ok && _retry < 3) {
                            _retry++;
                            Log.w("FILE-MSG", "chunk echo 실패 msgId=" + msgId + " seq=" + seq + " - 재시도 " + _retry + "/3");
                            try { Thread.sleep(5000); } catch (InterruptedException _ie) { Thread.currentThread().interrupt(); }
                            ok = sendChunkAwaitEcho(sms, sendId, msgId, seq);
                        }
                        if (!ok) {
                            Log.e("FILE-MSG", "chunk modem reject msgId=" + msgId + " seq=" + seq + " - abort(3회 실패)");
                            final int _abSeq = seq;
                            runOnUiThread(() -> Toast.makeText(MainActivity.this,
                                "\u26A0 \uD30C\uC77C \uC804\uC1A1 \uC2E4\uD328(\uC2E0\uD638 \uBD88\uB7C9, \uC870\uAC01 " + _abSeq + "). \uC2E0\uD638 \uC591\uD638 \uC2DC \uB2E4\uC2DC \uC804\uC1A1\uD558\uC138\uC694.",
                                Toast.LENGTH_LONG).show());
                            // [abortReSend] 전체 중단(return) 대신 이 조각만 기록하고 다음 조각 계속 → 뒤 조각 손실 방지
                            mSentLargeFileAborted.computeIfAbsent(msgId, k -> new java.util.ArrayList<>()).add(seq);
                            continue;
                        }
                        Log.d("FILE-MSG", "chunk 재시도 성공 msgId=" + msgId + " seq=" + seq);
                    }
                }
                android.util.Log.d("FILE-MSG", "TX file all-accepted msgId=" + msgId + " - wait server ~A:");

                // [abortReSend] 모뎀 거부로 못 보낸 조각이 있으면 60초(위성 감도 회복 대기) 후 1회 재송신.
                //   원본(mSentLargeFile)에서 그 seq만 다시 frame 생성 → 성공분은 기록에서 제거. 남으면 gap-fill이 추후 처리.
                java.util.List<Integer> _abList = mSentLargeFileAborted.get(msgId);
                if (_abList != null && !_abList.isEmpty()) {
                    java.util.List<Integer> _retrySeqs = new java.util.ArrayList<>(_abList);
                    android.util.Log.w("FILE-MSG", "[abortReSend] 미송신 조각 " + _retrySeqs + " - 60초 후 재송신 예정 msgId=" + msgId);
                    try { Thread.sleep(60000); } catch (InterruptedException _ie2) { Thread.currentThread().interrupt(); }
                    for (int _rseq : _retrySeqs) {
                        try {
                            byte[] _rbody = parts.get(_rseq);
                            long _rcrc;
                            { java.util.zip.CRC32 _rc = new java.util.zip.CRC32(); _rc.update(_rbody); _rcrc = _rc.getValue(); }
                            String _rtitle = marker + msgId + ":" + _rseq + ":" + total
                                    + ":" + (_rseq == 0 ? String.valueOf(fullCrc) : "")
                                    + ":" + _rcrc
                                    + (_rseq == 0 ? (":" + safeName) : "");
                            io.netty.buffer.ByteBuf _rbuf = io.netty.buffer.Unpooled.buffer();
                            _rbuf.writeByte(0x07);
                            String _raddr = (recipientImei == null) ? "" : recipientImei;
                            _rbuf.writeByte(_raddr.getBytes(StandardCharsets.US_ASCII).length);
                            _rbuf.writeCharSequence(_raddr, StandardCharsets.US_ASCII);
                            _rbuf.writeByte(_rtitle.getBytes(StandardCharsets.UTF_8).length);
                            _rbuf.writeCharSequence(_rtitle, StandardCharsets.UTF_8);
                            _rbuf.writeByte(_rbody.length);
                            _rbuf.writeBytes(_rbody);
                            byte[] _rframe = new byte[_rbuf.readableBytes()];
                            _rbuf.readBytes(_rframe);
                            int _rsendId = mLargeSendIdSeq.updateAndGet(p -> p >= 999 ? 800 : p + 1);
                            String _rsms = String.format("SENDING=%d,%s", _rsendId, Base64.encodeToString(_rframe, Base64.NO_WRAP));
                            boolean _rok = sendChunkAwaitEcho(_rsms, _rsendId, msgId, _rseq);
                            if (_rok) {
                                _abList.remove(Integer.valueOf(_rseq));
                                android.util.Log.d("FILE-MSG", "[abortReSend] 재송신 성공 msgId=" + msgId + " seq=" + _rseq);
                            } else {
                                android.util.Log.w("FILE-MSG", "[abortReSend] 재송신 또 실패 msgId=" + msgId + " seq=" + _rseq + " (gap-fill 대기)");
                            }
                        } catch (Exception _re) {
                            Log.e("FILE-MSG", "[abortReSend] 재송신 예외 seq=" + _rseq + " : " + _re.getMessage());
                        }
                    }
                    if (_abList.isEmpty()) mSentLargeFileAborted.remove(msgId);
                    android.util.Log.d("FILE-MSG", "[abortReSend] 재송신 종료 msgId=" + msgId + " 남은=" + (mSentLargeFileAborted.get(msgId)));
                }
            } catch (Exception e) {
                Log.e("FILE-MSG", "sendLargeFile fail: " + e.getMessage(), e);
            } finally {
                mLargeSendingCount--;
            }
        }, "file-send").start();
    }

    /** [extCode] 파일 확장자 → 1글자 코드 (title 20byte 한도). 파일만 사용. */
    private static char extToCode(String fileName) {
        if (fileName == null) return 'm';
        int dot = fileName.lastIndexOf('.');
        String e = (dot >= 0) ? fileName.substring(dot + 1).toLowerCase() : "";
        switch (e) {
            case "txt": return 't';
            case "pdf": return 'p';
            case "doc": case "docx": return 'd';
            case "xls": case "xlsx": return 'x';
            case "hwp": return 'h';
            case "zip": return 'z';
            case "csv": return 'c';
            case "png": return 'g';
            case "jpg": case "jpeg": return 'j';
            case "ppt": case "pptx": return 'n';
            default: return 'm';   // 기타 → .bin
        }
    }

    /** [extCode] 1글자 코드 → 확장자(점 포함). 수신/저장용. */
    public static String codeToExt(char code) {
        switch (code) {
            case 't': return ".txt";
            case 'p': return ".pdf";
            case 'd': return ".docx";
            case 'x': return ".xlsx";
            case 'h': return ".hwp";
            case 'z': return ".zip";
            case 'c': return ".csv";
            case 'g': return ".png";
            case 'j': return ".jpg";
            case 'n': return ".pptx";
            default: return ".bin";
        }
    }

    /** [publicSave] 받은 미디어를 종류별 공용 폴더에 저장 → 갤러리/음악앱/파일앱에서 바로 보임.
     *  type: I→Pictures/MessageBox, V→Music/MessageBox, F→Download/MessageBox.
     *  Android 10+(API29)는 MediaStore(권한 불필요), 실패/구버전은 앱 전용 폴더 폴백.
     *  반환: 저장 위치(content:// URI 또는 절대경로). 실패 시 null. */
    private String saveReceivedToPublic(char type, String fname, byte[] data, int msgId) {
        String sub = "MessageBox";
        String safe = (fname == null || fname.isEmpty()) ? ("file_" + msgId) : fname;
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            try {
                android.content.ContentResolver resolver = getContentResolver();
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, msgId + "_" + safe);
                android.net.Uri collection;
                String relPath;
                if (type == 'I') {
                    collection = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    relPath = android.os.Environment.DIRECTORY_PICTURES + "/" + sub;
                } else if (type == 'V') {
                    collection = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    relPath = android.os.Environment.DIRECTORY_MUSIC + "/" + sub;
                } else {
                    collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                    relPath = android.os.Environment.DIRECTORY_DOWNLOADS + "/" + sub;
                }
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, (type == 'V') ? "audio/amr" : (type == 'I') ? "image/jpeg" : "application/octet-stream");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, relPath);
                cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
                android.net.Uri uri = resolver.insert(collection, cv);
                if (uri == null) throw new java.io.IOException("MediaStore insert null");
                try (java.io.OutputStream os = resolver.openOutputStream(uri)) { os.write(data); }
                cv.clear();
                cv.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(uri, cv, null, null);
                android.util.Log.d("FILE-MSG", "[publicSave] MediaStore OK type=" + type + " uri=" + uri);
                return uri.toString();
            } catch (Exception e) {
                Log.e("FILE-MSG", "[publicSave] MediaStore fail -> fallback: " + e.getMessage());
            }
        }
        try {
            java.io.File dir = new java.io.File(getExternalFilesDir(null), "received_files");
            if (!dir.exists()) dir.mkdirs();
            java.io.File outFile = new java.io.File(dir, msgId + "_" + safe);
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outFile)) { fos.write(data); }
            android.util.Log.d("FILE-MSG", "[publicSave] fallback OK path=" + outFile.getAbsolutePath());
            return outFile.getAbsolutePath();
        } catch (Exception e2) {
            Log.e("FILE-MSG", "[publicSave] fallback fail: " + e2.getMessage());
            return null;
        }
    }
    // ═════════════════════════════════════════════════════════════
    //   ⭐ v6 헬퍼 함수: 위치/메시지 dedup insert (2026-05-03)
    //   - 중복 수신 패킷 차단
    //   - 자기 에코 메시지는 update로 처리 (insert 안 함)
    // ═════════════════════════════════════════════════════════════

    /**
     * 위치 dedup insert.
     * 결과에 따라 SatTrackStateHolder.recordPoint() 호출 여부 결정.
     */
    private void insertLocationWithDedup(LocationEntity loc,
                                         double lat, double lng,
                                         double alt, double speed, double dir,
                                         Date gpsDate, int ver,
                                         boolean notifyService) {
        locationViewModel.insertWithDedup(loc, result -> {
            if (result instanceof InsertResult.Inserted) {
                if (notifyService) {
                    com.ah.acr.messagebox.service.TytoConnectService
                            .notifyPointSavedByActivity(MainActivity.this);
                }
                // [DISABLED 2026-05] Satellite track point DB save removed per user request.
                // BLE transmission and packet processing remain functional.
                // SatTrackStateHolder.recordPoint(this, lat, lng, alt, speed, dir, gpsDate, ver);
            } else {
                Log.d("RECEVICE", "Dup loc skip: ver=0x" + Integer.toHexString(ver));
            }
            return null;
        });
    }

    /**
     * 메시지 dedup insert.
     *
     * ⭐ Phase 5-Q 후속 (2026-05-04): self-echo 매칭 로직 제거.
     *
     * 이전 동작: codeNum=myImei면 tryMarkSelfEcho로 송신 레코드 찾아 update
     * 변경 사유: 웹/다른 단말이 보낸 메시지에서 payload Address가 수신자(자기)
     *           IMEI라 isMyImei=true 오판정. findSelfSentMessage가 우연히
     *           오래된 송신 레코드와 매칭되어 메시지가 화면에서 사라지는 버그.
     *
     * 새 동작: 모든 받은 메시지를 dedup insert.
     *         - dedup_hash + 30초 윈도우로 중복 수신 차단
     *         - 자기 자신에게 보낸 메시지는 송신/수신 둘 다 표시되지만 큰 문제 없음
     */
    private void insertMsgWithDedupAndEcho(MsgEntity addMsg, String codeNum, String message) {
        msgViewModel.insertWithDedup(addMsg, result -> {
            if (result instanceof InsertResult.Duplicate) {
                Log.d("RECEVICE", "Dup msg skip");
            }
            return null;
        });
    }

    // ============================================================
    //  [fileMsg 2-b] 파일/사진(~L:F:/~L:I:) 조각 수신 처리.
    //  텍스트 ~L:T:와 분리된 바이너리 경로. mLargeFileBuf(byte[])에 쌓고,
    //  다 모이면 조립 → fullCrc 검증 → 앱전용 디렉토리 저장 → DB(title=[FILE]/[IMG], msg=경로).
    //  h: title.split(":"), fileBody: 이 조각의 바이너리(2-a에서 읽음).
    //  title 형식: ~L:F:msgId:seq:total:fullCrc:chunkCrc:fileName (seq0) / :seq:total::chunkCrc (seq>0)
    // ============================================================
    private void handleFileChunk(String[] h, int msgId, int seq, int total, byte[] fileBody, String codeNum) {
        try {
            if (fileBody == null) { Log.e("FILE-MSG", "handleFileChunk: fileBody null msgId=" + msgId); return; }
            char ftype = h[1].charAt(0);   // 'F' or 'I'

            // 완성된 msgId 중복 조각 무시
            if (mLargeFileDoneAt.get(msgId) != null) {
                android.util.Log.d("FILE-MSG", "이미 완성 msgId=" + msgId + " 중복조각(seq=" + seq + ") 무시");
                return;
            }

            // seq0: fullCrc / 파일명 / 타입 기록 + 옛 버퍼 폐기(crc 다르면)
            if (seq == 0) {
                long newCrc = -1;
                // [extCode] 파일(F)은 h[5]=6자리hex+확장자코드. 16진 6자만 newCrc로(24비트). 사진/음성은 기존 10진.
                if (h.length >= 6 && !h[5].isEmpty()) {
                    try {
                        if (ftype == 'F' && h[5].length() >= 1) {
                            String _h5 = h[5];
                            String _hexPart = (_h5.length() >= 7) ? _h5.substring(0, 6) : _h5.substring(0, Math.max(0, _h5.length()-1));
                            newCrc = Long.parseLong(_hexPart, 16);
                        } else {
                            newCrc = Long.parseLong(h[5]);
                        }
                    } catch (Exception e) {}
                }
                Long oldCrc = mLargeFileCrc.get(msgId);
                if (oldCrc != null && newCrc != -1 && oldCrc != newCrc) {
                    mLargeFileBuf.remove(msgId); mLargeFileTotal.remove(msgId); mLargeFileLastAt.remove(msgId);
                    android.util.Log.d("FILE-MSG", "[idFix] 옛 파일버퍼 폐기 msgId=" + msgId + " (crc 다름)");
                }
                if (newCrc != -1) mLargeFileCrc.put(msgId, newCrc);
                // [extCode] 파일(F)은 h[5] 끝 1글자=확장자코드 → 원본 확장자. 사진/음성은 타입으로 확장자.
                String fname;
                if (ftype == 'F') {
                    char _ec = (h.length >= 6 && h[5].length() >= 1) ? h[5].charAt(h[5].length()-1) : 'm';
                    fname = "file_" + msgId + codeToExt(_ec);
                } else {
                    fname = (h.length >= 8 && !h[7].isEmpty()) ? h[7] : ("file_" + msgId + ((ftype=='V')?".amr":(ftype=='I')?".jpg":".bin"));
                }
                mLargeFileName.put(msgId, fname);
                mLargeFileType.put(msgId, ftype);
            }

            // 조각 쌓기
            mLargeFileBuf.computeIfAbsent(msgId, k -> new java.util.TreeMap<>()).put(seq, fileBody);
            onLargeActivity();   // [대용량 TRACK] 파일/사진 조각 수신 → TRACK 일시정지
            mLargeFileTotal.put(msgId, total);
            mLargeFileLastAt.put(msgId, System.currentTimeMillis());
            mLargeFileSender.put(msgId, codeNum);
            java.util.TreeMap<Integer, byte[]> parts = mLargeFileBuf.get(msgId);
            android.util.Log.d("FILE-MSG", "파일조각 수신 msgId=" + msgId + " type=" + ftype
                    + " seq=" + seq + "/" + (total - 1) + " 누적=" + parts.size() + "/" + total);

            // [fileGapSchedule] 미완성이면 자동 MT gap-fill 예약. 유실 조각 ~R: 재요청 → 서버가 ~L:I:/~L:F: 로 재전송 → 복구.
            if (parts.size() < total) {
                scheduleAutoMtGapfill(msgId);
                return;   // 아직 미완성
            }

            // ── 조립 ──
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            for (byte[] p : parts.values()) bos.write(p);
            byte[] full = bos.toByteArray();

            // ── fullCrc 검증 ──
            Long expectCrc = mLargeFileCrc.get(msgId);
            long actualCrc;
            { java.util.zip.CRC32 c = new java.util.zip.CRC32(); c.update(full); actualCrc = c.getValue(); }
            // [extCode] 파일(F)은 fullCrc 24비트만 전송(title 한도) → actualCrc도 24비트 마스킹 비교. 사진/음성은 32비트 전체.
            long _actualCmp = (ftype == 'F') ? (actualCrc & 0xFFFFFFL) : actualCrc;
            if (expectCrc != null && expectCrc != -1 && expectCrc != _actualCmp) {
                Log.e("FILE-MSG", "❌ CRC 불일치 msgId=" + msgId + " expect=" + expectCrc + " actual=" + actualCrc
                        + " → 버퍼 폐기(재요청 대기)");
                mLargeFileBuf.remove(msgId);   // 깨진 조립 → 버리고 ~R: 복구(2-c)로 다시 받게
                return;
            }

            // ── 파일 저장 (앱 전용 디렉토리) ──
            char tp = mLargeFileType.getOrDefault(msgId, ftype);
            String fname = mLargeFileName.getOrDefault(msgId, "file_" + msgId);
            // [publicSave] 종류별 공용 폴더 저장 (갤러리/음악앱/다운로드)
            String savedLoc = saveReceivedToPublic(tp, fname, full, msgId);
            android.util.Log.d("FILE-MSG", "✅ 파일 조립완료 msgId=" + msgId + " type=" + tp
                    + " size=" + full.length + " saved=" + savedLoc);

            // ── DB insert (title=[FILE]/[IMG] + 파일명, msg=저장경로) ──
            final String marker = (tp == 'I') ? "[IMG]" : (tp == 'V') ? "[VOICE]" : "[FILE]";
            final String dbTitle = marker + fname;
            final String dbMsg = (savedLoc != null) ? savedLoc : ("[저장실패] " + fname);
            final String fCode = codeNum;
            runOnUiThread(() -> {
                MsgEntity addMsg = new MsgEntity(0, false, fCode, dbTitle, dbMsg,
                        new Date(),
                        new Date(System.currentTimeMillis()),
                        new Date(System.currentTimeMillis()),
                        false, false, false);
                insertMsgWithDedupAndEcho(addMsg, fCode, dbTitle + "|" + dbMsg);
            });

            // ── 정리 + 완성 표시 ──
            mLargeFileBuf.remove(msgId); mLargeFileTotal.remove(msgId);
            mLargeFileSender.remove(msgId); mLargeFileLastAt.remove(msgId);
            mLargeFileCrc.remove(msgId); mLargeFileName.remove(msgId); mLargeFileType.remove(msgId);
            mLargeFileDoneAt.put(msgId, System.currentTimeMillis());

            // ── ACK 송신 (텍스트와 동일: pref_ack_large ON 시 지연 큐) ──
            boolean ackLargeOn = android.preference.PreferenceManager
                    .getDefaultSharedPreferences(MainActivity.this)
                    .getBoolean("pref_ack_media", false);
            if (ackLargeOn && !mPendingServerAckIds.contains(msgId)) {
                mPendingServerAckIds.add(msgId);
                android.util.Log.d("ACK", "파일 완성 -> 서버 ACK 지연 큐 적재 msgId=" + msgId);
            }
        } catch (Exception e) {
            Log.e("FILE-MSG", "handleFileChunk 실패 msgId=" + msgId + " : " + e.getMessage(), e);
        }
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
                }, 500);
            } else {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_CHANGE_FAIL);
            }
        } else if (packet.startsWith("UOPEN=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
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
                Toast.makeText(this, getString(R.string.toast_change_successful), Toast.LENGTH_LONG).show();
            } else if (vals[0].equals("FAIL")) {
                Toast.makeText(this, getString(R.string.toast_change_failed), Toast.LENGTH_LONG).show();
            } else {
                BLE.INSTANCE.getDeviceSet().postValue(packet);
            }
        } else if (packet.startsWith("LOCATION=")) {
            String msg = packet.substring(9);
            String[] vals = msg.split(",");
            if (vals[0].equals("1")) Toast.makeText(this, getString(R.string.toast_single_location_sent), Toast.LENGTH_LONG).show();
            if (vals[0].equals("2")) Toast.makeText(this, getString(R.string.toast_tracking_started), Toast.LENGTH_LONG).show();
            if (vals[0].equals("3")) Toast.makeText(this, getString(R.string.toast_tracking_stopped), Toast.LENGTH_LONG).show();
            if (vals[0].equals("4")) Toast.makeText(this, getString(R.string.toast_sos_started), Toast.LENGTH_LONG).show();
            if (vals[0].equals("5")) Toast.makeText(this, getString(R.string.toast_sos_stopped), Toast.LENGTH_LONG).show();
        } else if (packet.startsWith("SENDING=")) {
            android.util.Log.d("ACK-PROBE", "수신 응답=" + packet);
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
                if (vals[1].equals("0")) {
                    flushPendingServerAcks();   // 인박스 배수 완료 -> 대기 ACK 송신 (BLE 경쟁 방지)
                    Toast.makeText(this, getString(R.string.inbox_receive_complite), Toast.LENGTH_LONG).show();
                    completeAutoReceive();
                    return;
                }
                byte[] data = Base64.decode(vals[2], Base64.NO_WRAP);
                ByteBuf buffer = Unpooled.wrappedBuffer(data);
                byte ver = buffer.getByte(0);

                // ⭐ v6 (2026-05-03): 모든 위치/메시지 insert를 dedup 버전으로 변경
                // ⭐ Phase 5-P 후속 (2026-05-04): isIncomeLoc은 isRecvMode(ver)로 결정
                //   - RECV mode (0x10/0x11/0x12/0x13/0x17): true (수신)
                //   - SEND mode (0x00/0x01/0x02/0x03):     false (송신)

                if (ver == 0x00 || ver == 0x01) {
                    // SOS_SEND(0x00) / CAR_SEND(0x01) - 자기가 보낸 메시지
                    buffer.readByte();
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    String myImei = ImeiStorage.getLast(this);
                    Date now = new Date();
                    LocationEntity addLoc = new LocationEntity(0, isRecvMode(ver), ver,
                            myImei, lat, lng, 0, 0, 0, now,
                            now, false, false, false);
                    insertLocationWithDedup(addLoc, lat, lng, 0.0, 0.0, 0.0, null, ver, true);

                } else if (ver == 0x11 || ver == 0x10) {
                    // SOS_RECV(0x10) / CAR_RECV(0x11) - 다른 단말이 보낸 메시지
                    int senderLen = buffer.readableBytes() - 10;
                    buffer.readByte();
                    String sender = parseAddress(buffer, senderLen);
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    Date now = new Date();
                    // sender = 송신자 IMEI (Phase 5-P 변경 후 payload IMEI 의미 반전)
                    LocationEntity addLoc = new LocationEntity(
                            0, isRecvMode(ver), ver, sender,
                            lat, lng, 0, 0, 0, now, now,
                            false, false, false);

                    insertLocationWithDedup(addLoc, lat, lng, 0.0, 0.0, 0.0, null, ver, true);

                } else if (ver == 0x02) {
                    // UAV_SEND - 자기가 보낸 메시지
                    buffer.readByte();
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    String myImei = ImeiStorage.getLast(this);
                    Date now = new Date();
                    LocationEntity addLoc = new LocationEntity(0, isRecvMode(ver), ver,
                            myImei, lat, lng, alt, dir, speed, now,
                            now, false, false, false);
                    insertLocationWithDedup(addLoc, lat, lng, (double) alt, (double) speed, (double) dir, null, ver, false);

                } else if (ver == 0x12) {
                    // UAV_RECV - 다른 단말이 보낸 메시지
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
                    // sender = 송신자 IMEI (Phase 5-P 변경 후 payload IMEI 의미 반전)
                    LocationEntity addLoc = new LocationEntity(
                            0, isRecvMode(ver), ver, sender,
                            lat, lng, alt, dir, speed,
                            now, now, false, false, false);

                    insertLocationWithDedup(addLoc, lat, lng, (double) alt, (double) speed, (double) dir, null, ver, false);

                } else if (ver == 0x03) {
                    // UAT_SEND - 자기가 보낸 메시지
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
                    LocationEntity addLoc = new LocationEntity(0, isRecvMode(ver), ver,
                            myImei, lat, lng, alt, dir, speed, date,
                            new Date(), false, false, false);
                    insertLocationWithDedup(addLoc, lat, lng, (double) alt, (double) speed, (double) dir, date, ver, false);

                } else if (ver == 0x13) {
                    // UAT_RECV - 다른 단말이 보낸 메시지
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

                    LocalDateTime ldt = LocalDateTime.of(year, mon, day, hour, min, sec);
                    ZonedDateTime zdtUtc = ldt.atZone(ZoneId.of("UTC"));
                    Date date = Date.from(zdtUtc.toInstant());

                    // sender = 송신자 IMEI (Phase 5-P 변경 후 payload IMEI 의미 반전)
                    LocationEntity addLoc = new LocationEntity(
                            0, isRecvMode(ver), ver, sender,
                            lat, lng, alt, dir, speed,
                            date, new Date(),
                            false, false, false);

                    insertLocationWithDedup(addLoc, lat, lng, (double) alt, (double) speed, (double) dir, date, ver, false);

                } else if (ver == 0x16) {
                    byte[] header = new byte[21];
                    byte[] body = new byte[data.length - 22];
                    System.arraycopy(data, 1, header, 0, header.length);
                    System.arraycopy(data, header.length + 1, body, 0, body.length);

                    String codeNum = new String(header, StandardCharsets.UTF_8).trim();
                    String message = new String(body, StandardCharsets.UTF_8);

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum, "", message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    insertMsgWithDedupAndEcho(addMsg, codeNum, message);

                } else if (ver == 0x17) {
                    buffer.readByte();

                    int addrSize = buffer.readUnsignedByte();
                    String codeNum = buffer.readCharSequence(addrSize, StandardCharsets.US_ASCII).toString().trim();

                    int titleSize = buffer.readUnsignedByte();
                    String title = buffer.readCharSequence(titleSize, StandardCharsets.UTF_8).toString().trim();

                    int memoSize = buffer.readUnsignedByte();
                    // [fileMsg 2-a] ~L:F:/~L:I:(파일/사진)면 body를 byte[]로 읽기(바이너리 보존, trim 안 함). 그 외(텍스트/단문)는 기존 String.
                    String message;
                    byte[] fileBody = null;
                    if (title.startsWith("~L:F:") || title.startsWith("~L:I:") || title.startsWith("~L:V:")) {
                        fileBody = new byte[memoSize];
                        buffer.readBytes(fileBody);
                        message = "";   // 파일 body는 fileBody(byte[])에. message는 파싱 진행용 빈값.
                    } else {
                        message = buffer.readCharSequence(memoSize, StandardCharsets.UTF_8).toString().trim();
                    }

                    android.util.Log.d("LARGE-MSG", "RX title=[" + title + "] msgLen=" + message.length());

                    if (title.startsWith("~L:")) {
                        // ~L:T:msgId:seq:total  (memo = 조각 본문)
                        try {
                            String[] h = title.split(":");
                            int msgId = Integer.parseInt(h[2]);
                            int seq   = Integer.parseInt(h[3]);
                            int total = Integer.parseInt(h[4]);
                            // [fileMsg 2-b] 파일/사진(~L:F:/~L:I:)이면 전용 처리. 텍스트(~L:T:)는 아래 else 기존 로직.
                            if (h[1].equals("G")) { mRecvTacticalMsgIds.add(msgId); }
                            if (h[1].equals("F") || h[1].equals("I") || h[1].equals("V")) {
                                handleFileChunk(h, msgId, seq, total, fileBody, codeNum);
                            } else {
                            // ⭐ 무한 재수신 차단: 최근 완성된 msgId의 조각이 또 오면 무시
                            Long doneAt = mLargeMsgDoneAt.get(msgId);
                            if (doneAt != null
                                    ) {   // [doneFix5] 완성된 msgId는 윈도우 무관 영구 무시 (위성 지연으로 10분 후 중복조각 와도 부활 안 함)
                                // 이미 완성된 msgId의 중복 조각 → 무시 (재조립/재송신 안 함)
                                android.util.Log.d("LARGE-MSG", "이미 완성된 msgId=" + msgId
                                        + " 중복 조각(seq=" + seq + ") 무시");
                            } else {
                                // 새 메시지(또는 윈도우 지난 것) → 조각 쌓고 조립
                                mLargeMsgDoneAt.remove(msgId);
                                // [MT-idFix] seq0(fullCrc 보유) 도착 시, 같은 msgId 옛 버퍼의 crc와 다르면 → 다른 메시지 → 옛 버퍼 폐기 (유령 누락 방지)
                                if (seq == 0 && h.length >= 6 && !h[5].isEmpty()) {
                                    Long oldCrc = mLargeMsgCrc.get(msgId);
                                    long newCrc;
                                    try { newCrc = Long.parseLong(h[5]); } catch (Exception e) { newCrc = -1; }
                                    if (oldCrc != null && oldCrc != newCrc) {
                                        mLargeMsgBuf.remove(msgId); mLargeMsgTotal.remove(msgId); mLargeMsgLastAt.remove(msgId);
                                        android.util.Log.d("LARGE-MSG", "[MT-idFix] 옛 수신버퍼 폐기 msgId=" + msgId + " (crc 다름 " + oldCrc + "!=" + newCrc + ")");
                                    }
                                    if (newCrc != -1) mLargeMsgCrc.put(msgId, newCrc);
                                }
                                mLargeMsgBuf.computeIfAbsent(msgId, k -> new java.util.TreeMap<>()).put(seq, message);
                                onLargeActivity();   // [대용량 TRACK] 조각 수신 → TRACK 일시정지
                                mLargeMsgTotal.put(msgId, total);
                                mLargeMsgLastAt.put(msgId, System.currentTimeMillis());   // [gap-fill] 마지막 조각 시각
                                mLargeMsgSender.put(msgId, codeNum);
                                java.util.TreeMap<Integer, String> parts = mLargeMsgBuf.get(msgId);
                                // [gap-fill] 미완성 대용량 → 설정 간격으로 자동 조각 재요청 (파일과 동일 메커니즘, SURV/TAC/WX 공통)
                                if (parts != null && parts.size() < total && isAutoResend()) {
                                    scheduleAutoMtGapfill(msgId);
                                }
                                android.util.Log.d("LARGE-MSG", "조각 수신 msgId=" + msgId
                                        + " seq=" + seq + "/" + (total - 1)
                                        + " 누적=" + parts.size() + "/" + total);
                                if (parts.size() >= total) {
                                    StringBuilder sb = new StringBuilder();
                                    for (String part : parts.values()) sb.append(part);
                                    String full = sb.toString();
                                    android.util.Log.d("LARGE-MSG", "✅ 조립 완료 msgId=" + msgId
                                            + " 총길이=" + full.length());
                                    // [tactical-recv] 전술 데이터면 파싱 + 보관 (지도 표시용). 채팅엔 요약 표시.
                                    boolean _isTactical = mRecvTacticalMsgIds.remove(msgId);
                                    String tacticalSummary = null;
                                    if (_isTactical) {
                                        try {
                                            TacticalParser.TacticalData td = TacticalParser.parse(full);
                                            final String _payload = full; final String _code = codeNum;
                                            new Thread(() -> TacticalStore.addAndPersist(getApplicationContext(), _code, _payload, false)).start();
                                            StringBuilder sm = new StringBuilder();
                                            sm.append("[TAC] markers ").append(td.markers.size())
                                              .append(", lines ").append(td.lines.size())
                                              .append(", measures ").append(td.measures.size());
                                            if (td.note != null && !td.note.isEmpty()) sm.append("\nNote: ").append(td.note);
                                            tacticalSummary = sm.toString();
                                            android.util.Log.d("TACTICAL-RECV", "전술 수신 파싱 OK: markers=" + td.markers.size()
                                                    + " lines=" + td.lines.size() + " measures=" + td.measures.size()
                                                    + " from=" + td.fromImei + " note=" + (td.note.isEmpty() ? "-" : td.note));
                                        } catch (Exception te) {
                                            android.util.Log.e("TACTICAL-RECV", "전술 파싱 실패", te);
                                            tacticalSummary = "[TAC] data received (parse failed)";
                                        }
                                    }
                                    // 전술이면 채팅 본문 = 요약, 아니면 원문
                                    boolean _isGuide = full.startsWith("GUIDE:");
                                    boolean _isWeather = full.startsWith("WX:");
                                    if (_isGuide) {
                                        SurvivalChatStore.add(codeNum, "server", full.substring(6));
                                        android.util.Log.d("SURV-CHAT", "GUIDE recv from=" + codeNum + " len=" + (full.length()-6));
                                    } else if (_isWeather) {
                                        WeatherStore.Weather _w = WeatherStore.addAndPersist(getApplicationContext(), codeNum, full);
                                        android.util.Log.d("WEATHER-RECV", "WX recv marine=" + (_w != null && _w.marine)
                                                + " fields=" + (_w != null ? _w.fields : "-")
                                                + " days=" + (_w != null ? _w.forecast7.size() : 0));
                                        runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this,
                                                (_w != null && _w.marine ? "\ud574\uc0c1 \ub0a0\uc528 \uc218\uc2e0" : "\uc721\uc0c1 \ub0a0\uc528 \uc218\uc2e0"),
                                                android.widget.Toast.LENGTH_SHORT).show());
                                    }
                                    String bubbleBody = (tacticalSummary != null) ? tacticalSummary : full;
                                    MsgEntity addMsg = new MsgEntity(0, false, codeNum, "", bubbleBody,
                                            new Date(),
                                            new Date(System.currentTimeMillis()),
                                            new Date(System.currentTimeMillis()),
                                            false, false, false);
                                    if (!_isGuide && !_isWeather) insertMsgWithDedupAndEcho(addMsg, codeNum, bubbleBody);
                                    mLargeMsgBuf.remove(msgId);
                                    mLargeMsgTotal.remove(msgId);
                                    mLargeMsgSender.remove(msgId);
                                    mLargeMsgLastAt.remove(msgId); mLargeMsgCrc.remove(msgId);   // [MT-idFix] 완성 시 정리
                                    mLargeMsgDoneAt.put(msgId, System.currentTimeMillis());   // 완성 표시
                                    // 최초 조립 완료 시 서버로 ACK 송신 (수신확인) — ACK ON일 때만, 지연 큐로
                                    boolean ackLargeOn = android.preference.PreferenceManager
                                            .getDefaultSharedPreferences(MainActivity.this)
                                            .getBoolean("pref_ack_large", false);
                                    boolean isSurvivalTarget = bubbleBody != null && bubbleBody.contains("MSA:");
                                    if ((ackLargeOn || isSurvivalTarget) && !mPendingServerAckIds.contains(msgId)) {
                                        mPendingServerAckIds.add(msgId);   // 즉시 송신 금지: 인박스 배수 후 flush
                                        android.util.Log.d("ACK", "대용량 완성 -> 서버 ACK 지연 큐 적재 msgId=" + msgId);
                                    }
                                }
                            }
                        }   // [fileMsg 2-b] else(텍스트) 끝
                        } catch (Exception ex) {
                            Log.e("LARGE-MSG", "헤더 파싱 실패 title=" + title + " : " + ex.getMessage());
                        }
                    } else if (title.startsWith("~SA:")) {
                        // [SURVIVAL] 생존 진입 ACK: ~SA:<sessionKey> → 재시도 중단
                        String _ackKey = message.trim();   // memo에 sessionKey (title=~SA:1)
                        onSurvivalAck(_ackKey);
                    } else if (title.startsWith("~A:")) {
                        // 서버가 보낸 내 대용량 도착확인 → 보냈던 원문을 내 말풍선으로 표시 (모델 B)
                        try {
                            int ackId = Integer.parseInt(title.substring(3).trim());
                            // [그룹 등록 확정] 같은 msgId의 PENDING 그룹이 있으면 확정 처리
                            try {
                                GroupStore.Group _g = new GroupStore(MainActivity.this).confirmByMsgId(ackId);
                                if (_g != null) {
                                    android.util.Log.d("GROUP-REG", "그룹 확정: " + _g.getDisplayLabel() + " (msgId=" + ackId + ")");
                                    runOnUiThread(() -> android.widget.Toast.makeText(
                                            MainActivity.this, _g.shortNo() + "번 그룹이 확정되었습니다.",
                                            android.widget.Toast.LENGTH_SHORT).show());
                                }
                            } catch (Exception _ge) {
                                android.util.Log.w("GROUP-REG", "그룹 확정 처리 중 오류: " + _ge.getMessage());
                            }
                            String sentText;
                            String sentTo;
                            synchronized (mSentLargeMsg) {
                                sentText = mSentLargeMsg.get(ackId);
                                sentTo   = mSentLargeMsgTo.get(ackId);
                            }
                            if (sentText != null) {
                                String to = (sentTo == null || sentTo.isEmpty()) ? "SERVER" : sentTo;
                                android.util.Log.d("LARGE-MSG", "✅ 내 대용량 발송확인 ~A:" + ackId
                                        + " to=" + to + " len=" + sentText.length());
                                // 말풍선은 보낼 때 이미 생성됨 → 여기선 ✓(보냄)로 상태 업데이트만
                                msgViewModel.markAckServerByContent(to, sentText);   // 대용량 ackState=1(V)
                                synchronized (mPendingMoResend) { mPendingMoResend.remove(ackId); }   // [fix] 완성 → MO 재전송 대기 해제(배너 끔)
                            } else {
                                msgViewModel.markAckByTitle("~M:" + ackId, 1);   // 단문 ackState=1(V)
                                android.util.Log.d("ACK", "단문 ~A:" + ackId + " → ackState=1");
                            }
                        } catch (Exception ex) {
                            Log.e("LARGE-MSG", "~A: 파싱 실패 title=" + title + " : " + ex.getMessage());
                        }
                    } else if (title.startsWith("~Q:")) {
                        // [Step4] 서버가 "이 조각 깨졌/빠졌으니 다시 보내" → 원본에서 그 seq 재송신
                        try {
                            String rest = title.substring(3);          // "msgId:seq들" or "msgId:OK"
                            int colon = rest.indexOf(':');
                            int qMsgId = Integer.parseInt(rest.substring(0, colon).trim());
                            String seqPart = rest.substring(colon + 1).trim();
                            if ("OK".equalsIgnoreCase(seqPart)) {
                                android.util.Log.d("MO-RESEND", "~Q:OK msgId=" + qMsgId + " -> 전송 완료(보낼 것 없음)");
                                // TODO Step5: 배너 끄고 완료 표시
                            } else {
                                java.util.List<Integer> seqs = new java.util.ArrayList<>();
                                for (String s : seqPart.split(",")) {
                                    s = s.trim();
                                    if (!s.isEmpty()) seqs.add(Integer.parseInt(s));
                                }
                                android.util.Log.d("MO-RESEND", "~Q: 수신 msgId=" + qMsgId + " seqs=" + seqs);
                                // [임시 테스트] 수신 즉시 재송신 (Step5에서 수동 버튼으로 교체 예정)
                                // [Step5] 자동 X → 대기 기록만. 사용자가 배너 [다시 보내기] 눌러야 실제 재송신 (수동, 감도 보고)
                                synchronized (mPendingMoResend) { mPendingMoResend.put(qMsgId, seqs); mMoResendAt.put(qMsgId, System.currentTimeMillis()); }
                                // [Step2-auto] 자동 모드면 재시도 스케줄 시작 (10분 후 1회 → 10분마다 → 3회). 수동이면 배너가 7분 후 띄움.
                                if (isAutoResend()) { scheduleAutoMoResend(qMsgId); }
                            }
                        } catch (Exception ex) {
                            Log.e("MO-RESEND", "~Q: 파싱 실패 title=" + title + " : " + ex.getMessage());
                        }
                    } else if (title.startsWith("~D:")) {
                        // 서버가 보낸 "상대 전달 완료" → 내가 보낸 그 메시지를 "전달됨 ✓✓"로 표시
                        try {
                            int dId = Integer.parseInt(title.substring(3).trim());
                            String dText;
                            String dTo;
                            synchronized (mSentLargeMsg) {
                                dText = mSentLargeMsg.remove(dId);
                                dTo   = mSentLargeMsgTo.remove(dId);
                            }
                            if (dText != null) {
                                String to = (dTo == null || dTo.isEmpty()) ? "SERVER" : dTo;
                                android.util.Log.d("LARGE-MSG", "✅✅ 상대 전달 확인 ~D:" + dId
                                        + " to=" + to);
                                msgViewModel.markAckRelayByContent(to, dText);   // 대용량 ackState=2(VV)
                            } else {
                                msgViewModel.markAckByTitle("~M:" + dId, 2);   // 단문 ackState=2(VV)
                                android.util.Log.d("ACK", "단문 ~D:" + dId + " → ackState=2");
                            }
                        } catch (Exception ex) {
                            Log.e("LARGE-MSG", "~D: 파싱 실패 title=" + title + " : " + ex.getMessage());
                        }
                    } else {
                        // 기존 일반 채팅 그대로
                        // ⭐ 단문 ACK: 받은 제목이 ~M:msgId 이고 단문 ACK ON이면 서버에 "받았다" ACK 송신.
                        //   서버가 릴레이 판단 → 원송신자에게 ~D:(VV) 전달. 제목은 화면표시용으로 정리.
                        boolean dupShortAck = false;
                        if (title != null && title.startsWith("~M:")) {
                            try {
                                int recvMsgId = Integer.parseInt(title.substring(3).trim());
                                String dupKey = recvMsgId + "\u0001" + message;
                                long now = System.currentTimeMillis();
                                Long lastAt = mRecvShortAckDoneAt.get(dupKey);
                                if (lastAt != null && (now - lastAt) < 600000L) {
                                    dupShortAck = true;   // 같은 단문 본문 재수신(인박스 재독) -> 저장/ACK 스킵
                                    android.util.Log.d("ACK", "중복 ~M: 무시 msgId=" + recvMsgId);
                                } else {
                                    mRecvShortAckDoneAt.put(dupKey, now);
                                    boolean ackShortOn = android.preference.PreferenceManager
                                            .getDefaultSharedPreferences(MainActivity.this)
                                            .getBoolean("pref_ack_short", false);
                                    if (ackShortOn && !mPendingServerAckIds.contains(recvMsgId)) {
                                        mPendingServerAckIds.add(recvMsgId);   // 즉시 송신 금지: 배수 후 flush
                                        android.util.Log.d("ACK", "단문 MT 수신 -> 서버 ACK 지연 큐 적재 msgId=" + recvMsgId);
                                    }
                                }
                            } catch (Exception ex) {
                                Log.e("ACK", "단문 ~M: 파싱 실패 title=" + title);
                            }
                            title = "";
                        }
                        if (!dupShortAck) {
                            MsgEntity addMsg = new MsgEntity(0, false, codeNum, title, message,
                                    new Date(),
                                    new Date(System.currentTimeMillis()),
                                    new Date(System.currentTimeMillis()),
                                    false, false, false);
                            insertMsgWithDedupAndEcho(addMsg, codeNum, message);
                        }
                    }
                }

                // ★ ACK는 항상 송신 (중복이든 아니든 단말 큐에서 제거되어야 함)
                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,OK", vals[0]));
            } catch (Exception e) {
                Log.e("RECEIVE-ERR", "PARSE FAILED: " + e.getMessage(), e);
                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,FAIL", vals[0]));
            }
        } else if (packet.startsWith("MSGDEL=")) {
            String msg = packet.substring(7);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                Toast.makeText(getApplicationContext(), getString(R.string.toast_all_messages_deleted), Toast.LENGTH_LONG).show();
            }
        } else if (packet.startsWith("BROAD=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");

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
            if (vals.length > 9) sta.setLedOn(!vals[9].equals("0"));

            mBleViewModel.getDeviceStatus().postValue(sta);
            mLastBroadReceivedTime = System.currentTimeMillis();

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

    /**
     * @deprecated 직접 GATT write는 분할 전송(SplitWriter) 중 다른 송신과 충돌해
     *             "gatt writeCharacteristic fail"을 유발한다. 모든 송신은
     *             {@code BLE.INSTANCE.getWriteQueue().offer(msg)} → TytoConnectService의
     *             단일 직렬 펌프(enqueueWrite/pumpTxQueue/onWriteDone) 경로로만 보낸다.
     *             이 메서드는 호환용으로 남기되 큐로 위임만 한다.
     */
    @Deprecated
    public void bleSendMessage(String msg) {
        if (msg == null || msg.isEmpty()) {
            return;
        }
        // ★ 직접 write 금지 → 직렬 큐로 위임 (Service가 base64 인코딩 후 송신)
        Log.v("BLE Write", "(queued) " + msg);
        BLE.INSTANCE.getWriteQueue().offer(msg);
    }

    public void setConnectBleDevice(@NonNull BleDevice bleDevice) {
        // ⭐ 새 연결 시 펌웨어 플래그 무조건 리셋 (이전 전송이 멈춰서 갇힌 경우 방지)
        BLE.INSTANCE.isFirmwareUdate().postValue(false);
        try {
            BLE.INSTANCE.getReceiveData().clear();
        } catch (Exception e) {
            // ignore
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

                                    receivePacketProcess(packet);
                                    android.util.Log.e("RX_DEBUG", "ms=" + System.currentTimeMillis() + " len=" + packet.length() + " packet=[" + packet + "]");

                                    Intent packetIntent = new Intent(
                                        com.ah.acr.messagebox.service.TytoConnectService.BROADCAST_PACKET_RECEIVED);
                                    packetIntent.putExtra("packet", packet);
                                    packetIntent.setPackage(getPackageName());
                                    sendBroadcast(packetIntent);
                                } catch (Exception e) {
                                    Log.v("BLE", "Packet parse failed: " + e.getMessage());
                                    BLE.INSTANCE.getReceiveData().clear();
                                }
                            }
                        });
                    }
                });
    }

    // ============================================================
    //  [gap-fill] 미완 대용량: 필드 + 조회 (배너/재요청용)
    // ============================================================
    private final java.util.Map<Integer, Long> mLargeMsgLastAt = new java.util.HashMap<>();   // msgId별 마지막 조각 수신 시각

    /** 이 codeNum 방에 미완 대용량이 있으면 [msgId, received, total, 경과ms] 반환, 없으면 null. 가장 최근 1건. */
    public long[] getIncompleteLargeMsg(String codeNum) {
        long now = System.currentTimeMillis();
        Integer bestId = null; long bestAt = -1;
        synchronized (mLargeMsgBuf) {
            for (java.util.Map.Entry<Integer, java.util.TreeMap<Integer, String>> e : mLargeMsgBuf.entrySet()) {
                int id = e.getKey();
                String sender = mLargeMsgSender.get(id);
                if (codeNum != null && !codeNum.equals(sender)) continue;
                Integer total = mLargeMsgTotal.get(id);
                if (total == null) continue;
                int received = e.getValue().size();
                if (received >= total) continue;
                Long at = mLargeMsgLastAt.get(id);
                long atv = (at == null) ? 0 : at;
                if (atv > bestAt) { bestAt = atv; bestId = id; }
            }
            if (bestId == null) return null;
            int total = mLargeMsgTotal.get(bestId);
            int received = mLargeMsgBuf.get(bestId).size();
            long elapsed = (bestAt > 0) ? (now - bestAt) : 0;
            return new long[]{ bestId, received, total, elapsed };
        }
    }

    /** msgId의 빠진 seq 목록 (오름차순). 미완 아니면 빈 배열. */
    public java.util.List<Integer> getMissingSeqs(int msgId) {
        java.util.List<Integer> missing = new java.util.ArrayList<>();
        synchronized (mLargeMsgBuf) {
            java.util.TreeMap<Integer, String> parts = mLargeMsgBuf.get(msgId);
            Integer total = mLargeMsgTotal.get(msgId);
            if (parts == null || total == null) {
                // [fileGapMissing] 텍스트 버퍼에 없으면 아래에서 파일/사진 버퍼 확인
            } else {
                for (int s = 0; s < total; s++) if (!parts.containsKey(s)) missing.add(s);
                return missing;
            }
        }
        // [fileGapMissing] 파일/사진 버퍼(mLargeFileBuf) 확인 - 파일 MT gap-fill 지원
        if (missing.isEmpty()) {
            synchronized (mLargeFileBuf) {
                java.util.TreeMap<Integer, byte[]> fparts = mLargeFileBuf.get(msgId);
                Integer ftotal = mLargeFileTotal.get(msgId);
                if (fparts != null && ftotal != null) {
                    for (int s = 0; s < ftotal; s++) if (!fparts.containsKey(s)) missing.add(s);
                }
            }
        }
        return missing;
    }

}
