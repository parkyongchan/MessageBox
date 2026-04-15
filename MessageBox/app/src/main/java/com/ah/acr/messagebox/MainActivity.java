package com.ah.acr.messagebox;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.ah.acr.messagebox.data.DeviceInfo;
import com.ah.acr.messagebox.data.DeviceStatus;
import com.ah.acr.messagebox.data.FirmUpdate;
import com.ah.acr.messagebox.database.InboxViewModel;
import com.ah.acr.messagebox.database.InboxViewModelFactory;
import com.ah.acr.messagebox.database.LocationEntity;
import com.ah.acr.messagebox.database.LocationViewModel;
import com.ah.acr.messagebox.database.MsgEntity;
import com.ah.acr.messagebox.database.MsgRoomDatabase;
import com.ah.acr.messagebox.database.MsgViewModel;
import com.ah.acr.messagebox.database.OutboxViewModel;
import com.ah.acr.messagebox.database.OutboxViewModelFactory;
import com.ah.acr.messagebox.databinding.ActivityMainBinding;
import com.ah.acr.messagebox.packet.HeaderInfo;
import com.ah.acr.messagebox.packet.PacketProcUtil;
import com.ah.acr.messagebox.packet.security.SharedUtil;
import com.ah.acr.messagebox.ble.BleViewModel;
import com.ah.acr.messagebox.util.Coordinates;
import com.ah.acr.messagebox.viewmodel.EventsQueue;
import com.ah.acr.messagebox.viewmodel.KeyViewModel;
import com.clj.fastble.BleManager;
import com.clj.fastble.callback.BleNotifyCallback;
import com.clj.fastble.callback.BleWriteCallback;
import com.clj.fastble.data.BleDevice;
import com.clj.fastble.exception.BleException;
import com.clj.fastble.scan.BleScanRuleConfig;
import com.clj.fastble.utils.HexUtil;
import com.ah.acr.messagebox.ble.BLE;
import com.google.android.material.snackbar.Snackbar;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import pub.devrel.easypermissions.EasyPermissions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Queue;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final UUID BLE_SERVICE_UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

    private static final int PERMISSION_BLUETOOTH_SCAN = 5;
    private static final int PERMISSION_BLUETOOTH_CONNECT = 4;
    private static final int PERMISSION_BLUETOOTH_ADVERTISE = 3;
    private static final int PERMISSION_ACCESS_FINE_LOCATION = 2;
    private static final int PERMISSION_ACCESS_COARSE_LOCATION = 1;
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;

    private BleViewModel mBleViewModel;
    private KeyViewModel mKeyViewModel;
    //private OutboxViewModel mOutboxViewModel;
    //private InboxViewModel mInboxViewModel;
    private MsgViewModel msgViewModel;
    private LocationViewModel locationViewModel;

    //private CardView bleIndicator;
    //private TextView bleText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);



        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //Android12
            //Manifest.permission.BLUETOOTH_SCAN
            //Manifest.permission.BLUETOOTH_CONNECT
            //Manifest.permission.BLUETOOTH_ADVERTISE

            if (this.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN}, PERMISSION_BLUETOOTH_SCAN );
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
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, PERMISSION_BLUETOOTH_CONNECT );
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
                        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_ADVERTISE}, PERMISSION_BLUETOOTH_ADVERTISE );
                    }
                });
                builder.show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //Android10
            //Manifest.permission.ACCESS_FINE_LOCATION
            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_ACCESS_FINE_LOCATION );
                    }
                });
                builder.show();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //Android6
            //Manifest.permission.ACCESS_COARSE_LOCATION
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.ble_permission_ble_access));
                builder.setMessage(getString(R.string.gpsNotifyMsg));
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_ACCESS_COARSE_LOCATION );
                    }
                });
                builder.show();
            }
        }




        BLE.INSTANCE.getSelectedDevice().observe(this, new Observer<BleDevice>() {
            @Override
            public void onChanged(BleDevice bleDevice) {
                if (bleDevice != null) {
                    Log.v("BL:E", bleDevice.toString());
                    setConnectBleDevice(bleDevice);

                    binding.bleIndicator.setCardBackgroundColor(Color.parseColor("#00BCD4"));
                    binding.bleText.setTextColor(Color.WHITE);

                } else {
                    Log.v("BLE",  "disconnected Ble device...");

                    binding.bleIndicator.setCardBackgroundColor(Color.parseColor("#9E9E9E"));
                    binding.bleText.setTextColor(Color.WHITE);

                }
            }
        });

        BLE.INSTANCE.getWriteQueue().observe(this, queue -> {
            String request = queue.poll();
            bleSendMessage(request);
        });




//        mOutboxViewModel = new OutboxViewModelFactory(
//                MsgRoomDatabase.Companion.getDatabase(this).outboxDao()
//        ).create(OutboxViewModel.class);
//        mInboxViewModel = new InboxViewModelFactory(
//                MsgRoomDatabase.Companion.getDatabase(this).inboxDao()
//        ).create(InboxViewModel.class);
        mKeyViewModel = new ViewModelProvider(this).get(KeyViewModel.class);
        mBleViewModel = new ViewModelProvider(this).get(BleViewModel.class);
        msgViewModel = new ViewModelProvider(this).get(MsgViewModel.class);
        locationViewModel = new ViewModelProvider(this).get(LocationViewModel.class);


//        BLE.INSTANCE.getBleLoginStatus().observe(this, new Observer<String>() {
//            @Override
//            public void onChanged(String s) {
//                if (!s.equals(BLE.BLE_LOGIN_OK) && !s.equals(BLE.BLE_LOGIN_CHANGE_OK)) {
//                    BleManager.getInstance().disconnect(BLE.INSTANCE.getSelectedDevice().getValue());
//                }
//            }
//        });

        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MODE_PRIVATE);
        checkExternalStorage();



        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);

//        binding.fab.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show();
//
//
//                SharedUtil sharedUtil = mKeyViewModel.getSharedUtil().getValue();
//                Log.v(TAG, sharedUtil.getString("privateKey"));
//            }
//        });

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
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
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

//    public void bleInit(){
//        checkPermissions();
//        setScanRule();

    boolean checkExternalStorage() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return false;
        } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)){
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
            if(vals.length > 9) info.setSosStarted(!vals[9].equals("0"));
            if(vals.length > 10) info.setTrackingMode((!vals[10].equals("0")));

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

            //BLE.INSTANCE.getWriteQueue().offer("SET=?");
            //BLE.INSTANCE.getWriteQueue().offer(String.format("SET=UAT,%s,%s", "T0003", "D0005"));

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
                //BLE.INSTANCE.isFirmwareUdate().postValue(true);
                FirmUpdate state = new FirmUpdate(1, "START");
                BLE.INSTANCE.getFirmwareUdateState().postValue(state);
            }
//            } else {
//                BLE.INSTANCE.isFirmwareUdate().postValue(false);
//            }
        } else if (packet.startsWith("UFILE=")) {
            String msg = packet.substring(6);
            String[] vals = msg.split(",");
            int idx = Integer.parseInt(vals[0]);

            if (vals[1].equals("OK")) {
                // idx 다음 전송
                FirmUpdate state = new FirmUpdate(idx, "NEXT");
                BLE.INSTANCE.getFirmwareUdateState().postValue(state);
            } else if (vals[1].equals("FAIL")) {
                if (vals[2].equals("0")) {
                    // 데이터 전송에 실패하여 종료
                    FirmUpdate state = new FirmUpdate(idx, "FAILEND");
                    BLE.INSTANCE.getFirmwareUdateState().postValue(state);
                } else {
                    // idx 다시 전송
                    FirmUpdate state = new FirmUpdate(idx, "RESEND");
                    BLE.INSTANCE.getFirmwareUdateState().postValue(state);
                }
                
            } else if (vals[1].equals("END")) {
                // 데이터 전송 완료
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
                String type = vals[0];
                String time = vals[1].replaceAll("[^0-9]", "");  // 결과: "0000"
                String dist = vals[2].replaceAll("[^0-9]", "");  // 결과: "0000"

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
                int id = Integer.parseInt(vals[0]);
                /// ------ sending....--------
                BLE.INSTANCE.getOutboxMsgStatus().postValue(packet);
            }

        } else if (packet.startsWith("DEVICESEND=")) {
            String msg = packet.substring(11);
            String[] vals = msg.split(",");

            if (vals[1].equals("OK")) {
                int id = Integer.parseInt(vals[0]);
                // ????????????
            }
        } else if (packet.startsWith("RECEIVED=")) {
            String sms = packet.substring(9);
            String[] vals = sms.split(",");

            try {
                Log.v("RECEIVE", "number of remaining  : " + vals[1]);
                // 남은 갯수가 0이면 가져오기 그만.
                if (vals[1].equals("0")) {
                    Toast.makeText(this, getString(R.string.inbox_receive_complite), Toast.LENGTH_LONG).show();
                    return;
                }

                byte[] data = Base64.decode(vals[2], Base64.NO_WRAP);
                Log.v("RECEIVE-HEX", HexUtil.formatHexString(data));
                ByteBuf buffer = Unpooled.wrappedBuffer(data);
                byte ver = buffer.getByte(0);


                if ( ver == 0x11 || ver == 0x10) { // CAR, SOS
                    int senderLen = buffer.readableBytes() - 10;
                    //byte[] sender = new byte[senderLen];
                    buffer.readByte(); //ver
                    String sender=null;
                    if (senderLen==5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen==8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
                    //buffer.readBytes(sender); //  02 01 7d 78 45  01 7d 78 45    2  025000005

                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    byte etc = buffer.readByte();

                    LocationEntity addLoc = new LocationEntity(0,true, ver,
                            sender, lat, lng, 0, 0,0, null,
                            new Date(),
                            false,
                            false,
                            false);

                    Log.v("VER 11", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });

                } else if ( ver == 0x12) { // UAV
                    int senderLen = buffer.readableBytes() - 14;
                    //byte[] sender = new byte[senderLen];
                    buffer.readByte(); //ver
                    String sender=null;
                    if (senderLen==5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen==8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
                    //buffer.readBytes(sender); //  02 01 7d 78 45  01 7d 78 45    2  025000005
                    double lat = buffer.readFloat();
                    double lng = buffer.readFloat();
                    int alt = buffer.readShort();
                    int speed = buffer.readUnsignedByte() * 2;
                    int dir = buffer.readUnsignedByte() * 2;
                    byte etc = buffer.readByte();

                    LocationEntity addLoc = new LocationEntity(0,true, ver,
                            sender, lat, lng, alt, dir,speed, null,
                            new Date(),
                            false,
                            false,
                            false);
                    Log.v("VER 12", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });

                } else if ( ver == 0x13 ) { // UAT
                    int senderLen = buffer.readableBytes() - 21;
                    //byte[] sender = new byte[senderLen];
                    buffer.readByte(); //ver
                    String sender=null;
                    if (senderLen==5) {
                        byte senderF = buffer.readByte();
                        int senderB = buffer.readInt();
                        sender = String.format("%d%09d", senderF, senderB);
                    } else if (senderLen==8) {
                        int senderF = buffer.readInt();
                        int senderB = buffer.readInt();
                        sender = String.format("%08d%07d", senderF, senderB);
                    }
                    //buffer.readBytes(sender); //  02 01 7d 78 45  01 7d 78 45    2  025000005
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

                   // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDateTime ldt = LocalDateTime.of(year, mon, day, hour, min, sec);
                    ZonedDateTime zdtUtc = null;
                    zdtUtc = ldt.atZone(ZoneId.of("UTC"));
                    Date date = Date.from(zdtUtc.toInstant());

                    LocationEntity addLoc = new LocationEntity(0,true, ver,
                            sender, lat, lng, alt, dir,speed, date,
                            new Date(),
                            false,
                            false,
                            false);

                    Log.v("VER 13", addLoc.toString());

                    locationViewModel.insert(addLoc, success -> {
                        if (success) Log.v("Location ADD", "위치 저장 완료");
                        else Log.v("Location ADD", "위치 저장 실패");
                        return null;
                    });
                    //}
                } else if ( ver == 0x16 ) { // FREE
                    byte[] header = new byte[21]; // header은 title임
                    byte[] body = new byte[data.length - 22];  // 0x06
                    System.arraycopy(data, 1, header, 0, header.length);
                    System.arraycopy(data, header.length + 1, body, 0, body.length);

                    //SharedUtil shared = mKeyViewModel.getSharedUtil().getValue();
                    //DeviceInfo deviceInfo = BLE.INSTANCE.getDeviceInfo().getValue();

                    //String codeNum = new String("1234567890".getBytes(), StandardCharsets.UTF_8);
                    String codeNum = new String(header, StandardCharsets.UTF_8);
                    String message = new String(body, StandardCharsets.UTF_8);

                    MsgEntity addMsg = new MsgEntity(0,false, codeNum.trim(), "title", message,
                            new Date(),
                            new Date(System.currentTimeMillis()),
                            new Date(System.currentTimeMillis()),
                            false,
                            false,
                            false);

                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "메시지 저장 완료");
                        else Log.v("MSG ADD", "메시지 저장 실패");
                        return null;
                    });

                } else if ( ver == 0x17 ) { // FREE
                    Log.v("MSG FREE", "Size : " + buffer.readableBytes());

                    buffer.readByte(); //ver

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
                            false,
                            false,
                            false);

                    msgViewModel.insert(addMsg, success -> {
                        if (success) Log.v("MSG ADD", "메시지 저장 완료");
                        else Log.v("MSG ADD", "메시지 저장 실패");
                        return null;
                    });
                }

                // ok.....
                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,OK", vals[0]));
            } catch (Exception e) {
                BLE.INSTANCE.getWriteQueue().offer(String.format("RECEIVED=%s,FAIL", vals[0]));

            }

        /*} else if (packet.startsWith("TESTMSG=")) {
            String msg = packet.substring(8);
            String[] vals = msg.split(",");
            if (vals[0].equals("OK")) {
                final Snackbar snackbar = Snackbar.make(binding.mainLayout, getString(R.string.ble_test_msg), Snackbar.LENGTH_LONG);
                snackbar.setAction(vals[0], new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                snackbar.dismiss();
                            }
                        });
                snackbar.show();
            } */
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

        }  else if (packet.startsWith("SN=")) {
            String msg = packet.substring(3);
            String[] vals = msg.split(",");
        } //else {
            //Log.d("SMS", packet);
        //}
    }

//    private void setScanRule() {
//        BleScanRuleConfig scanRuleConfig = new BleScanRuleConfig.Builder()
//                //.setServiceUuids(serviceUuids)
//                //.setDeviceName(true, names)
//                //.setDeviceMac(mac)
//                .setAutoConnect(false)
//                .setScanTimeOut(10000)
//                .build();
//        BleManager.getInstance().initScanRule(scanRuleConfig);
//    }


    BluetoothGattCharacteristic getWriteCharacteristic(final BleDevice bleDevice){
        BluetoothGattService service = BleManager.getInstance().getBluetoothGatt(bleDevice).getService(BLE_SERVICE_UUID);
        if (service == null) return null;
        for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()){
            int charaProp = characteristic.getProperties();
            if((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                return characteristic;
            }
        }
        return null;
    }

    public void bleSendMessage(String msg) {



        // String to Base64 + \n
        Log.v("BLE Write", msg);
        String sendMsg = String.format("%s\n", Base64.encodeToString(msg.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        //Log.v("WRITE BLE", "MSG SIZE : " + sendMsg.length());
        BleDevice bleDevice = BLE.INSTANCE.getSelectedDevice().getValue();
        if (bleDevice == null) {
            final Snackbar snackbar = Snackbar.make(binding.mainLayout, getString(R.string.ble_test_nul_device), Snackbar.LENGTH_LONG);
            snackbar.setAction("OK", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    snackbar.dismiss();
                }
            });
            snackbar.show();
            return;
        }

//        // 펌웨어 업데이트 중이면 다른 데이터를 보내지 않음.
//        if (BLE.INSTANCE.isFirmwareUdate().getValue()) {
//            return;
//        }

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
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Log.v("WRITE", "write success, current: " + current
                                //        + " total: " + total
                                //        + " justWrite: " + HexUtil.formatHexString(justWrite, true));
                            }
                        });
                    }

                    @Override
                    public void onWriteFailure(final BleException exception) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //Log.v("WRITE",  exception.toString());
                            }
                        });
                    }
                });
    }

    public void setConnectBleDevice(@NonNull BleDevice bleDevice){
        //Log.v("BLE",  "Select Ble device..." + bleDevice.getName());

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
                //Log.v("BLE","Properties : NOTIFY");
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
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Log.v("BLE","connect success");
                            BLE.INSTANCE.getWriteQueue().offer("INFO=?");
                        }
                    });
                }
                @Override
                public void onNotifyFailure(final BleException exception) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //Log.v("BLE", exception.toString());
                        }
                    });
                }
                @Override
                public void onCharacteristicChanged(byte[] data) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            BLE.INSTANCE.addReceviceData(new String(data));
                            if ( data[data.length-1] == '\n') {
                                try {
                                    List<String> read = BLE.INSTANCE.getReceiveData();
                                    String reads = String.join("", read);
                                    BLE.INSTANCE.getReceiveData().clear();

                                    receivePacketProcess(new String(Base64.decode(reads, Base64.NO_WRAP)));
                                } catch (Exception e) {
                                    //Log.e("RECEIVE", e.getMessage());
                                }
                            }
                        }
                    });
                }
            });
    }

}