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
    private boolean mIsSosMode = false;

    private Handler mSyncHandler;
    private Runnable mBroadRetryRunnable;
    private Runnable mInfoRetryRunnable;
    private long mLastBroadReceivedTime = 0;
    private long mLastInfoReceivedTime = 0;
    private static final long BROAD_TIMEOUT_MS = 15000;
    private static final long INFO_TIMEOUT_MS = 8000;
    private static final long PERIODIC_SYNC_MS = 30000;

    private boolean mIsAutoReceiving = false;
    private int mLastInboxCount = 0;
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
                    })
                    .setNegativeButton(getString(R.string.btn_cancel), null)
                    .show();
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

    private final Runnable mPeriodicSyncRunnable = new Runnable() {
        @Override
        public void run() {
            if (BLE.INSTANCE.getSelectedDevice().getValue() == null) return;

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

                if (info.isSosStarted()) updateLocationStatus(2);
                else if (info.isTrackingMode()) updateLocationStatus(1);
                else updateLocationStatus(0);
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
                updateLocationStatus(0);

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

            updateTrackButtonUI(status.isTrackingMode());
            updateSosButtonUI(status.isSosMode());

            if (status.isSosMode()) updateLocationStatus(2);
            else if (status.isTrackingMode()) updateLocationStatus(1);
            else updateLocationStatus(0);

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
            updateLocationStatus(0);
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
                    Toast.makeText(this, getString(R.string.inbox_receive_complite), Toast.LENGTH_LONG).show();
                    completeAutoReceive();
                    return;
                }
                byte[] data = Base64.decode(vals[2], Base64.NO_WRAP);
                ByteBuf buffer = Unpooled.wrappedBuffer(data);
                byte ver = buffer.getByte(0);

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
                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            com.ah.acr.messagebox.service.TytoConnectService
                                    .notifyPointSavedByActivity(MainActivity.this);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(this, lat, lng, 0.0, 0.0, 0.0, null, ver);

                } else if (ver == 0x11 || ver == 0x10) {
                    int senderLen = buffer.readableBytes() - 10;
                    buffer.readByte();
                    String sender = parseAddress(buffer, senderLen);
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    Date now = new Date();
                    String myImei = ImeiStorage.getLast(this);
                    boolean isMyEcho = sender != null && myImei != null && sender.equals(myImei);

                    LocationEntity addLoc = new LocationEntity(
                            0, isMyEcho, ver, sender,
                            lat, lng, 0, 0, 0, now, now,
                            false, false, false);

                    locationViewModel.insert(addLoc, success -> {
                        if (success) {
                            com.ah.acr.messagebox.service.TytoConnectService
                                    .notifyPointSavedByActivity(MainActivity.this);
                        }
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(this, lat, lng, 0.0, 0.0, 0.0, null, ver);

                } else if (ver == 0x02) {
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
                    locationViewModel.insert(addLoc, success -> null);

                    SatTrackStateHolder.recordPoint(this, lat, lng, (double) alt, (double) speed, (double) dir, null, ver);

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
                    String myImeiUav = ImeiStorage.getLast(this);
                    boolean isMyEchoUav = sender != null && myImeiUav != null && sender.equals(myImeiUav);

                    LocationEntity addLoc = new LocationEntity(
                            0, isMyEchoUav, ver, sender,
                            lat, lng, alt, dir, speed,
                            now, now, false, false, false);

                    locationViewModel.insert(addLoc, success -> null);

                    SatTrackStateHolder.recordPoint(this, lat, lng, (double) alt, (double) speed, (double) dir, null, ver);

                } else if (ver == 0x03) {
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
                    locationViewModel.insert(addLoc, success -> null);

                    SatTrackStateHolder.recordPoint(this, lat, lng, (double) alt, (double) speed, (double) dir, date, ver);

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

                    LocalDateTime ldt = LocalDateTime.of(year, mon, day, hour, min, sec);
                    ZonedDateTime zdtUtc = ldt.atZone(ZoneId.of("UTC"));
                    Date date = Date.from(zdtUtc.toInstant());

                    String myImeiUat = ImeiStorage.getLast(this);
                    boolean isMyEchoUat = sender != null && myImeiUat != null && sender.equals(myImeiUat);

                    LocationEntity addLoc = new LocationEntity(
                            0, isMyEchoUat, ver, sender,
                            lat, lng, alt, dir, speed,
                            date, new Date(),
                            false, false, false);

                    locationViewModel.insert(addLoc, success -> null);

                    SatTrackStateHolder.recordPoint(this, lat, lng, (double) alt, (double) speed, (double) dir, date, ver);

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
                    msgViewModel.insert(addMsg, success -> null);

                } else if (ver == 0x17) {
                    buffer.readByte();

                    int addrSize = buffer.readUnsignedByte();
                    String codeNum = buffer.readCharSequence(addrSize, StandardCharsets.US_ASCII).toString().trim();

                    int titleSize = buffer.readUnsignedByte();
                    String title = buffer.readCharSequence(titleSize, StandardCharsets.UTF_8).toString().trim();

                    int memoSize = buffer.readUnsignedByte();
                    String message = buffer.readCharSequence(memoSize, StandardCharsets.UTF_8).toString().trim();

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum, title, message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    msgViewModel.insert(addMsg, success -> null);
                }

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

    public void bleSendMessage(String msg) {
        // null check (when Activity recreates, polling empty queue may return null)
        if (msg == null || msg.isEmpty()) {
            return;
        }

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
}
