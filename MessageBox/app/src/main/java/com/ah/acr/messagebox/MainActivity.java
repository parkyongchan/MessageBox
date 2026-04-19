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
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;
import android.view.View;
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

    // Test data IMEIs
    private static final String TEST_IMEI_TRACK = "TEST-001";
    private static final String TEST_IMEI_SOS = "TEST-002";
    private static final String TEST_IMEI_MSG = "1111111111111111";  // ⭐ NEW

    private ActivityMainBinding binding;

    private BleViewModel mBleViewModel;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;
    private LocationViewModel locationViewModel;

    private int mTestTapCount = 0;
    private boolean mIsTestMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

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

        BLE.INSTANCE.getSelectedDevice().observe(this, bleDevice -> {
            if (bleDevice != null) {
                Log.v("BLE", bleDevice.toString());
                setConnectBleDevice(bleDevice);
            } else {
                Log.v("BLE", "disconnected Ble device...");
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
        setupBottomTabs();
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
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            } else {
                if (mIsTestMode) return;
                binding.statusArea.textBleStatusMain.setText("Disconnected");
                binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
                binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
                binding.statusArea.textMainBattery.setText("- %");
                binding.statusArea.textMainInbox.setText("0");
                binding.statusArea.textMainOutbox.setText("0");
                binding.headerArea.textHeaderSub.setText("IMEI  -");
                updateSignalBar(0);
                updateLocationStatus(0);
            }
        });

        mBleViewModel.getDeviceStatus().observe(this, status -> {
            if (status == null) return;
            binding.statusArea.textMainBattery.setText(String.format("%d%%", status.getBattery()));
            updateSignalBar(status.getSignal());
            binding.statusArea.textMainInbox.setText(String.valueOf(status.getInBox()));
            binding.statusArea.textMainOutbox.setText(String.valueOf(status.getOutBox()));

            if (status.isSosMode()) updateLocationStatus(2);
            else if (status.isTrackingMode()) updateLocationStatus(1);
            else updateLocationStatus(0);
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

        binding.headerArea.btnSosHeader.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("SOS Emergency")
                        .setMessage("Send SOS signal?\nIt will be sent every 3 minutes.")
                        .setPositiveButton("Send", (d, w) ->
                                BLE.INSTANCE.getWriteQueue().offer("LOCATION=4"))
                        .setNegativeButton("Cancel", null)
                        .show()
        );

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

    /** Test mode toggle - inject/delete locations + messages */
    private void toggleTestMode() {
        if (mIsTestMode) {
            // ═══ OFF ═══
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

            deleteTestLocationData();
            deleteTestMessages();  // ⭐ NEW
            Toast.makeText(this, "🧪 Test mode OFF (data cleared)", Toast.LENGTH_SHORT).show();
        } else {
            // ═══ ON ═══
            mIsTestMode = true;
            DeviceStatus test = new DeviceStatus();
            test.setBattery(85);
            test.setSignal(3);
            test.setInBox(10);  // ⭐ 10 test messages
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
            insertTestMessages();  // ⭐ NEW
            Toast.makeText(this, "🧪 Test data injected (20 locations + 10 messages)",
                    Toast.LENGTH_SHORT).show();
        }
    }


    // ═════════════════════════════════════════════════════════════
    //   Test Location Data
    // ═════════════════════════════════════════════════════════════

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


    // ═════════════════════════════════════════════════════════════
    //   ⭐ Test Messages (NEW)
    // ═════════════════════════════════════════════════════════════

    /**
     * Insert 10 test messages from external IMEI 1111111111111111.
     * Mix of Korean + English, varied titles/bodies, spread over past 12 hours.
     */
    private void insertTestMessages() {
        String[][] testMsgs = {
                // {title, body}
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

        // Hours back from now for each message
        int[] hoursBack = {0, 1, 2, 3, 4, 5, 6, 8, 10, 12};

        for (int i = 0; i < testMsgs.length; i++) {
            cal.setTime(now);
            cal.add(Calendar.HOUR_OF_DAY, -hoursBack[i]);
            cal.add(Calendar.MINUTE, -(i * 7));  // Add some minute variation
            Date sentTime = cal.getTime();

            // First 3 messages = unread, rest = read (for UI variety)
            boolean isRead = i >= 3;

            MsgEntity msg = new MsgEntity(
                    0,                          // id (auto-gen)
                    false,                      // isSendMsg = false (received)
                    TEST_IMEI_MSG,              // codeNum = sender IMEI
                    testMsgs[i][0],             // title
                    testMsgs[i][1],             // msg body
                    sentTime,                   // createAt
                    sentTime,                   // receiveAt
                    sentTime,                   // sendDeviceAt
                    isRead,                     // isRead
                    false,                      // isSend
                    false                       // isDeviceSend
            );
            msgViewModel.insert(msg, success -> {
                if (success) Log.v("TEST_MSG", "Test message " + (testMsgs.length) + " saved");
                return null;
            });
        }

        Log.v("TEST_MODE", "Inserted 10 test messages from " + TEST_IMEI_MSG);
    }


    /** Delete test messages (match by IMEI) */
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
    //   Existing methods
    // ═════════════════════════════════════════════════════════════

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
        BleManager.getInstance().disconnectAllDevice();
        BleManager.getInstance().destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
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

            BLE.INSTANCE.getDeviceInfo().setValue(info);

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
            }
        } else if (packet.startsWith("CHANGELOGIN=")) {
            String msg = packet.substring(12);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                BLE.INSTANCE.getBleLoginStatus().postValue(BLE.BLE_LOGIN_CHANGE_OK);
                BLE.INSTANCE.isLogon().postValue(true);
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
                    return;
                }
                byte[] data = Base64.decode(vals[2], Base64.NO_WRAP);
                Log.v("RECEIVE-HEX", HexUtil.formatHexString(data));
                ByteBuf buffer = Unpooled.wrappedBuffer(data);
                byte ver = buffer.getByte(0);

                if (ver == 0x11 || ver == 0x10) {
                    int senderLen = buffer.readableBytes() - 10;
                    buffer.readByte();
                    String sender = null;
                    if (senderLen == 5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen == 8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            sender, lat, lng, 0, 0, 0, null,
                            new Date(), false, false, false);
                    Log.v("VER 11", addLoc.toString());
                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "Location saved");
                        else Log.v("Location ADD", "Location save failed");
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, 0.0, 0.0, 0.0, null, ver
                    );

                } else if (ver == 0x12) {
                    int senderLen = buffer.readableBytes() - 14;
                    buffer.readByte();
                    String sender = null;
                    if (senderLen == 5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen == 8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            sender, lat, lng, alt, dir, speed, null,
                            new Date(), false, false, false);
                    Log.v("VER 12", addLoc.toString());
                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "Location saved");
                        else Log.v("Location ADD", "Location save failed");
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, null, ver
                    );

                } else if (ver == 0x13) {
                    int senderLen = buffer.readableBytes() - 21;
                    buffer.readByte();
                    String sender = null;
                    if (senderLen == 5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen == 8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
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

                    LocationEntity addLoc = new LocationEntity(0, true, ver,
                            sender, lat, lng, alt, dir, speed, date,
                            new Date(), false, false, false);
                    Log.v("VER 13", addLoc.toString());
                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "Location saved");
                        else Log.v("Location ADD", "Location save failed");
                        return null;
                    });

                    SatTrackStateHolder.recordPoint(
                            this, lat, lng, (double) alt, (double) speed,
                            (double) dir, date, ver
                    );

                } else if (ver == 0x16) {
                    byte[] header = new byte[21];
                    byte[] body = new byte[data.length - 22];
                    System.arraycopy(data, 1, header, 0, header.length);
                    System.arraycopy(data, header.length + 1, body, 0, body.length);

                    String codeNum = new String(header, StandardCharsets.UTF_8);
                    String message = new String(body, StandardCharsets.UTF_8);

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum.trim(), "title", message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "Message saved");
                        else Log.v("MSG ADD", "Message save failed");
                        return null;
                    });
                } else if (ver == 0x17) {
                    Log.v("MSG FREE", "Size : " + buffer.readableBytes());
                    buffer.readByte();
                    int size = buffer.readUnsignedByte();
                    String codeNum = buffer.readCharSequence(size, StandardCharsets.US_ASCII).toString();
                    size = buffer.readUnsignedByte();
                    String title = buffer.readCharSequence(size, StandardCharsets.UTF_8).toString();
                    size = buffer.readUnsignedByte();
                    String message = buffer.readCharSequence(size, StandardCharsets.UTF_8).toString();

                    MsgEntity addMsg = new MsgEntity(0, false, codeNum.trim(), title.trim(), message.trim(),
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false, false, false);
                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "Message saved");
                        else Log.v("MSG ADD", "Message save failed");
                        return null;
                    });
                }

                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,OK", vals[0]));
            } catch (Exception e) {
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

            if (vals.length > 4) {
                sta.setGpsTime(vals[4]);
                sta.setGpsLat(vals[5]);
                sta.setGpsLng(vals[6]);
                sta.setSosMode(!vals[7].equals("0"));
                sta.setTrackingMode(!vals[8].equals("0"));
            }
            mBleViewModel.getDeviceStatus().setValue(sta);
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
                                    receivePacketProcess(new String(Base64.decode(reads, Base64.NO_WRAP)));
                                } catch (Exception e) {
                                    // Log.e("RECEIVE", e.getMessage());
                                }
                            }
                        });
                    }
                });
    }
}
