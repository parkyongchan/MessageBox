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
import com.ah.acr.messagebox.databinding.ActivityMainBinding;
import com.ah.acr.messagebox.tabs.BleTabFragment;
import com.ah.acr.messagebox.tabs.ChatTabFragment;
import com.ah.acr.messagebox.tabs.DevicesTabFragment;
import com.ah.acr.messagebox.tabs.MapTabFragment;
import com.ah.acr.messagebox.tabs.SettingsTabFragment;
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

    private ActivityMainBinding binding;

    private BleViewModel mBleViewModel;
    private KeyViewModel mKeyViewModel;
    private MsgViewModel msgViewModel;
    private LocationViewModel locationViewModel;

    // 테스트모드 상태
    private int mTestTapCount = 0;
    private boolean mIsTestMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ─── 권한 체크 (기존 그대로) ───
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
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
                    @Override
                    public void onDismiss(DialogInterface dialog) {
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
                    @Override
                    public void onDismiss(DialogInterface dialog) {
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
                    @Override
                    public void onDismiss(DialogInterface dialog) {
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
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        }

        // ─── BLE 선택 observer: 실제 하드웨어 연결/해제 시 setConnectBleDevice 호출 ───
        // (UI 업데이트는 setupFixedHeaderObservers() 안에서 별도로 처리됨)
        BLE.INSTANCE.getSelectedDevice().observe(this, bleDevice -> {
            if (bleDevice != null) {
                Log.v("BLE", bleDevice.toString());
                setConnectBleDevice(bleDevice);
            } else {
                Log.v("BLE", "disconnected Ble device...");
            }
        });

        // ─── BLE 쓰기 큐 observer (기존 그대로) ───
        BLE.INSTANCE.getWriteQueue().observe(this, queue -> {
            String request = queue.poll();
            bleSendMessage(request);
        });

        // ─── ViewModel 초기화 (기존 그대로) ───
        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MODE_PRIVATE);
        checkExternalStorage();

        // ─────────────────────────────────────────────
        //   Step 1: 고정 헤더/상태카드 observer
        // ─────────────────────────────────────────────
        setupFixedHeaderObservers();

        // ─────────────────────────────────────────────
        //   Step 2: 하단 탭 (채팅/장비/지도/설정/연결)
        // ─────────────────────────────────────────────
        setupBottomTabs();
    }

    // ═════════════════════════════════════════════════════════════
    //   Step 1: 고정 헤더/상태카드 관련 메서드
    // ═════════════════════════════════════════════════════════════

    /** 헤더+상태카드 observer 설정 */
    private void setupFixedHeaderObservers() {
        // DeviceInfo observer: IMEI + 위치상태 (연결 직후 약 0.5초 내 즉시 표시)
        BLE.INSTANCE.getDeviceInfo().observe(this, info -> {
            if (info != null && info.getImei() != null && !info.getImei().isEmpty()) {
                binding.headerArea.textHeaderSub.setText("IMEI  " + info.getImei());
            } else {
                binding.headerArea.textHeaderSub.setText("IMEI  -");
            }

            // 위치 상태 즉시 표시 (DeviceStatus 5초 기다리지 않고)
            if (info != null) {
                if (info.isSosStarted()) updateLocationStatus(2);
                else if (info.isTrackingMode()) updateLocationStatus(1);
                else updateLocationStatus(0);
            }
        });

        // BLE 연결 상태 (헤더 색상 변경)
        BLE.INSTANCE.getSelectedDevice().observe(this, device -> {
            if (device != null) {
                mIsTestMode = false;
                binding.statusArea.textBleStatusMain.setText("연결됨");
                binding.statusArea.textBleStatusMain.setTextColor(0xFF00E5D1);
                binding.statusArea.imgStatusBle.setColorFilter(0xFF00E5D1);
                BLE.INSTANCE.getWriteQueue().offer("BROAD=5");
            } else {
                if (mIsTestMode) return;
                binding.statusArea.textBleStatusMain.setText("미연결");
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

        // DeviceStatus observer (배터리, 신호, 메시지 수 - 5초 주기)
        mBleViewModel.getDeviceStatus().observe(this, status -> {
            if (status == null) return;
            binding.statusArea.textMainBattery.setText(String.format("%d%%", status.getBattery()));
            updateSignalBar(status.getSignal());
            binding.statusArea.textMainInbox.setText(String.valueOf(status.getInBox()));
            binding.statusArea.textMainOutbox.setText(String.valueOf(status.getOutBox()));

            // 위치 상태 (주기적 재확인)
            if (status.isSosMode()) updateLocationStatus(2);
            else if (status.isTrackingMode()) updateLocationStatus(1);
            else updateLocationStatus(0);
        });

        // 미전송 메시지 수
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

        // SOS 버튼 (헤더 우측)
        binding.headerArea.btnSosHeader.setOnClickListener(v ->
                new AlertDialog.Builder(this)
                        .setTitle("SOS 긴급 전송")
                        .setMessage("SOS 신호를 전송하시겠습니까?\n3분 간격으로 계속 전송됩니다.")
                        .setPositiveButton("전송", (d, w) ->
                                BLE.INSTANCE.getWriteQueue().offer("LOCATION=4"))
                        .setNegativeButton("취소", null)
                        .show()
        );

        // 테스트모드 (타이틀 5번 탭)
        binding.headerArea.textHeaderTitle.setOnClickListener(v -> {
            mTestTapCount++;
            if (mTestTapCount >= 5) {
                mTestTapCount = 0;
                toggleTestMode();
            }
        });
    }

    /** 신호 감도 막대 (0~5) */
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

    /** 위치 상태 (0=OFF, 1=TRACKING, 2=SOS) */
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

    /** 테스트 모드 토글 (BLE 없이 UI 확인용) */
    private void toggleTestMode() {
        if (mIsTestMode) {
            mIsTestMode = false;
            binding.statusArea.textBleStatusMain.setText("미연결");
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFF5252);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFF5252);
            binding.statusArea.textMainBattery.setText("- %");
            binding.statusArea.textMainInbox.setText("0");
            binding.statusArea.textMainOutbox.setText("0");
            binding.headerArea.textHeaderSub.setText("IMEI  -");
            updateSignalBar(0);
            updateLocationStatus(0);
            Toast.makeText(this, "🧪 테스트 모드 해제", Toast.LENGTH_SHORT).show();
        } else {
            mIsTestMode = true;
            DeviceStatus test = new DeviceStatus();
            test.setBattery(85);
            test.setSignal(3);
            test.setInBox(2);
            test.setOutBox(1);
            test.setTrackingMode(true);
            test.setSosMode(false);
            mBleViewModel.getDeviceStatus().postValue(test);
            binding.statusArea.textBleStatusMain.setText("테스트모드");
            binding.statusArea.textBleStatusMain.setTextColor(0xFFFFB300);
            binding.statusArea.imgStatusBle.setColorFilter(0xFFFFB300);
            binding.headerArea.textHeaderSub.setText("IMEI  300434061000001");
            Toast.makeText(this, "🧪 테스트 데이터 주입", Toast.LENGTH_SHORT).show();
        }
    }

    /** 하단 탭 설정 (Step 2) */
    private void setupBottomTabs() {
        // 첫 진입 = 지도 탭
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
    //   기존 메서드 (그대로 유지)
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
                            new Date(),
                            false, false, false);

                    Log.v("VER 11", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });

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
                            new Date(),
                            false, false, false);
                    Log.v("VER 12", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });

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
                            new Date(),
                            false, false, false);

                    Log.v("VER 13", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });
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
                        if (success) Log.v("MSG ADD", "메시지 저장 완료");
                        else Log.v("MSG ADD", "메시지 저장 실패");
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
                        if (success) Log.v("MSG ADD", "메시지 저장 완료");
                        else Log.v("MSG ADD", "메시지 저장 실패");
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
            String msg = packet.substring(6);
            String[] vals = msg.split(",");

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
                    @Override
                    public void onWriteSuccess(final int current, final int total, final byte[] justWrite) {
                        runOnUiThread(() -> { });
                    }

                    @Override
                    public void onWriteFailure(final BleException exception) {
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
                    @Override
                    public void onNotifySuccess() {
                        runOnUiThread(() -> {
                            Log.v("BLE", "connect success");
                            BLE.INSTANCE.getWriteQueue().offer("INFO=?");
                        });
                    }

                    @Override
                    public void onNotifyFailure(final BleException exception) {
                        runOnUiThread(() -> { });
                    }

                    @Override
                    public void onCharacteristicChanged(byte[] data) {
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