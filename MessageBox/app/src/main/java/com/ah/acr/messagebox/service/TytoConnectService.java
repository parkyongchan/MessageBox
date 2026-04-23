package com.ah.acr.messagebox.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.ah.acr.messagebox.MainActivity;
import com.ah.acr.messagebox.R;

/**
 * ⭐ v4 Phase B-1: TYTO Connect Foreground Service
 *
 * 목적:
 * - 앱 백그라운드/화면 꺼짐에서도 BLE 연결 유지
 * - Echo back 수신을 놓치지 않고 처리
 * - 세션 안정성 보장
 *
 * 설계 원칙:
 * - Single Source of Truth: BLE 상태는 Service가 주인
 * - Android 14+ 호환: type="connectedDevice" 명시
 * - Graceful: 알림 숨김 허용, 권한 없으면 안내
 *
 * Phase B-1 범위:
 * - Service 뼈대 + Notification만
 * - BLE 이관은 Phase B-2에서
 */
public class TytoConnectService extends Service {

    private static final String TAG = "TytoConnectService";

    // ═════════════════════════════════════════════════════════
    //   Notification 상수
    // ═════════════════════════════════════════════════════════

    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "tyto_connect_service";
    private static final String CHANNEL_NAME = "TYTO Connect Service";
    private static final String CHANNEL_DESC =
            "백그라운드에서 위성 장비 연결을 유지합니다";

    // ═════════════════════════════════════════════════════════
    //   Broadcast Actions (UI와 통신)
    //   → Phase B-3에서 사용, 지금은 상수만 정의
    // ═════════════════════════════════════════════════════════

    public static final String BROADCAST_STATE_CHANGED =
            "com.ah.acr.messagebox.STATE_CHANGED";
    public static final String BROADCAST_ECHO_RECEIVED =
            "com.ah.acr.messagebox.ECHO_RECEIVED";
    public static final String BROADCAST_SESSION_STOPPED =
            "com.ah.acr.messagebox.SESSION_STOPPED";

    // ⭐ v4 Phase B-2-3: Activity → Service 패킷 전달용 Broadcast
    public static final String BROADCAST_PACKET_RECEIVED =
            "com.ah.acr.messagebox.PACKET_RECEIVED";

    // ⭐ v4 Phase B-2-5: Activity가 Echo 저장했음을 Service에 알림
    public static final String BROADCAST_ACTIVITY_POINT_SAVED =
            "com.ah.acr.messagebox.ACTIVITY_POINT_SAVED";

    // ═════════════════════════════════════════════════════════
    //   Service 시작/종료 명령
    // ═════════════════════════════════════════════════════════

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP = "ACTION_STOP";

    // ═════════════════════════════════════════════════════════
    //   상태 관리
    // ═════════════════════════════════════════════════════════

    // Service 실행 여부 (외부에서 쉽게 확인)
    public static volatile boolean isServiceRunning = false;

    // 세션 상태 (Phase B-2에서 활용)
    private boolean mIsTracking = false;
    private boolean mIsSos = false;
    private int mSessionPointCount = 0;
    private long mSessionStartTime = 0;

    // Notification Manager
    private NotificationManager mNotificationManager;

    // ⭐ v4 Phase B-2-3: Activity로부터 패킷 수신용 BroadcastReceiver
    private android.content.BroadcastReceiver mPacketReceiver;

    // ⭐ v4 Phase B-2-4-B: Activity 존재 여부 추적
    // Activity가 활성 → Service는 저장 skip (중복 방지)
    // Activity 없음 → Service가 백그라운드 저장
    private static volatile boolean sIsActivityAlive = false;

    /**
     * MainActivity에서 lifecycle 변화 알림
     */
    public static void setActivityAlive(boolean alive) {
        sIsActivityAlive = alive;
        Log.v("TytoConnectService", "📍 Activity alive = " + alive);
    }

    /**
     * ⭐ v4 Phase B-2-5: Activity가 Echo back 저장했을 때 호출
     * Service의 포인트 카운트를 동기화 (Notification 업데이트용)
     *
     * 배경:
     * - Activity 있을 때 → MainActivity가 저장 → Service skip
     * - 하지만 Service의 mSessionPointCount는 증가 안 됨
     * - Notification의 "0pt"가 그대로
     *
     * 해결:
     * - MainActivity가 LocationEntity 저장 후 이 메서드 호출
     * - Service는 카운트 ++ 후 Notification 업데이트
     */
    public static void notifyPointSavedByActivity(Context context) {
        // Service 실행 중일 때만 의미 있음
        if (!isServiceRunning) return;

        // Broadcast로 Service에 알림 (Singleton 인스턴스 접근은 복잡함)
        Intent intent = new Intent(BROADCAST_ACTIVITY_POINT_SAVED);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }


    // ═════════════════════════════════════════════════════════
    //   Helper: 외부에서 호출하는 편의 메서드
    // ═════════════════════════════════════════════════════════

    /**
     * Service 시작 (Android 14+ 호환)
     */
    public static void start(Context context) {
        Intent intent = new Intent(context, TytoConnectService.class);
        intent.setAction(ACTION_START);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
        Log.v(TAG, "Service 시작 요청");
    }

    /**
     * Service 중지
     */
    public static void stop(Context context) {
        Intent intent = new Intent(context, TytoConnectService.class);
        intent.setAction(ACTION_STOP);
        context.startService(intent);
        Log.v(TAG, "Service 중지 요청");
    }


    // ═════════════════════════════════════════════════════════
    //   Lifecycle
    // ═════════════════════════════════════════════════════════

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(TAG, "onCreate");

        mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createNotificationChannel();

        // ⭐ v4 Phase B-2-1: BLE 연결 상태 관찰 시작
        setupBleObservers();
    }


    // ═════════════════════════════════════════════════════════
    //   ⭐ v4 Phase B-2-1: BLE 상태 관찰
    // ═════════════════════════════════════════════════════════

    private void setupBleObservers() {
        try {
            // BLE 연결 상태 → Notification 업데이트
            com.ah.acr.messagebox.ble.BLE.INSTANCE.getSelectedDevice().observeForever(
                    device -> {
                        Log.v(TAG, "BLE device 상태: " +
                                (device != null ? "connected" : "disconnected"));
                        updateNotification();
                    }
            );

            // BLE 연결 status 변화 → 로그 + Notification
            com.ah.acr.messagebox.ble.BLE.INSTANCE.getConnectionStatus().observeForever(
                    status -> {
                        Log.v(TAG, "BLE 연결 status: " + status);
                        updateNotification();
                    }
            );

            // ⭐ v4 Phase B-2-3: 패킷 수신은 Broadcast 방식 사용
            // (observeForever 방식은 메인 스레드 경합으로 폐기)
            registerPacketReceiver();

            Log.v(TAG, "✅ BLE observer 등록됨 (패킷은 Broadcast)");
        } catch (Exception e) {
            Log.v(TAG, "BLE observer 등록 실패: " + e.getMessage());
        }
    }


    // ═════════════════════════════════════════════════════════
    //   ⭐ v4 Phase B-2-2-A: 패킷 수신 (로그만, 처리 X)
    //   실제 처리는 여전히 MainActivity.receivePacketProcess()가 담당
    //   여기서는 "Service도 패킷 받는다"를 검증
    // ═════════════════════════════════════════════════════════

    // ═════════════════════════════════════════════════════════
    //   ⭐ v4 Phase B-2-4-A: 백그라운드에서도 상태 추적
    //   BROAD 패킷 받을 때마다 Service의 상태 업데이트
    //   → Notification에 실시간 반영
    // ═════════════════════════════════════════════════════════

    // BROAD에서 파싱한 최신 상태
    private int mLastBattery = -1;
    private int mLastInbox = -1;
    private int mLastSignal = -1;
    private long mLastBroadTime = 0;
    private int mBroadCount = 0;

    private void handleReceivedPacket(String packet) {
        // 패킷 종류 분류
        String type;
        if (packet.startsWith("BROAD=")) {
            type = "📡 BROAD";
            parseBroad(packet);
        } else if (packet.startsWith("INFO=")) {
            type = "ℹ INFO";
        } else if (packet.startsWith("RECEIVED=")) {
            type = "📥 RECEIVED";
            // ⭐ v4 Phase B-2-4-B: Activity 없을 때만 Service가 저장
            // (중복 저장 방지)
            if (!sIsActivityAlive) {
                Log.v(TAG, "🔋 Activity 없음 → Service가 백그라운드로 저장");
                processReceivedInBackground(packet);
            } else {
                Log.v(TAG, "👁 Activity 있음 → MainActivity가 저장 담당 (Service skip)");
            }
        } else if (packet.startsWith("LOCATION=")) {
            type = "📍 LOCATION";
        } else {
            type = "❓ OTHER";
        }

        // 패킷 앞부분만 로그 (너무 길면 잘라냄)
        String preview = packet.length() > 80
                ? packet.substring(0, 80) + "..."
                : packet;

        Log.v(TAG, "🔔 Service 수신: " + type + " | " + preview);
    }


    // ═════════════════════════════════════════════════════════
    //   ⭐ v4 Phase B-2-4-B: 백그라운드 RECEIVED 처리
    //   MainActivity.receivePacketProcess의 RECEIVED 분기 이관
    //   Echo back 판별 + LocationEntity 저장 + SatTrackStateHolder
    // ═════════════════════════════════════════════════════════

    private void processReceivedInBackground(String packet) {
        try {
            String sms = packet.substring(9); // "RECEIVED=" 제거
            String[] vals = sms.split(",");

            Log.v(TAG, "RECEIVED remaining: " + vals[1]);

            if (vals[1].equals("0")) {
                // 받기 완료
                return;
            }

            byte[] data = android.util.Base64.decode(vals[2],
                    android.util.Base64.NO_WRAP);
            io.netty.buffer.ByteBuf buffer =
                    io.netty.buffer.Unpooled.wrappedBuffer(data);
            byte ver = buffer.getByte(0);

            // CAR / SOS 모드 (0x00, 0x01)
            if (ver == 0x00 || ver == 0x01) {
                buffer.readByte();
                double lat = buffer.readFloat();
                double lng = buffer.readFloat();
                byte etc = buffer.readByte();

                String myImei = com.ah.acr.messagebox.util.ImeiStorage.getLast(this);
                java.util.Date now = new java.util.Date();

                com.ah.acr.messagebox.database.LocationEntity addLoc =
                        new com.ah.acr.messagebox.database.LocationEntity(
                                0, true, ver,
                                myImei, lat, lng, 0, 0, 0, now,
                                now, false, false, false);

                Log.v(TAG, "🔋 BG LOC SEND 0x" +
                        String.format("%02X", ver) +
                        " myImei=" + myImei + " lat=" + lat + " lng=" + lng);

                insertLocationEntity(addLoc, "내 위치 저장 (BG, 0x" +
                        String.format("%02X", ver) + ")");

                recordSatTrackPoint(lat, lng, ver);

            } else if (ver == 0x11 || ver == 0x10) {
                // ⭐ Phase 5-B: Echo back 판별
                int senderLen = buffer.readableBytes() - 10;
                buffer.readByte();
                String sender = parseAddress(buffer, senderLen);
                double lat = buffer.readFloat();
                double lng = buffer.readFloat();
                byte etc = buffer.readByte();

                java.util.Date now = new java.util.Date();

                String myImei = com.ah.acr.messagebox.util.ImeiStorage.getLast(this);
                boolean isMyEcho = sender != null
                        && myImei != null
                        && sender.equals(myImei);

                com.ah.acr.messagebox.database.LocationEntity addLoc =
                        new com.ah.acr.messagebox.database.LocationEntity(
                                0,
                                isMyEcho,
                                ver,
                                sender,
                                lat, lng,
                                0, 0, 0,
                                now, now,
                                false, false, false);

                if (isMyEcho) {
                    Log.v(TAG, "🔋 BG LOC ECHO 0x" +
                            String.format("%02X", ver) +
                            " ⭐ 내 Echo back! sender=" + sender +
                            " lat=" + lat + " lng=" + lng);
                } else {
                    Log.v(TAG, "🔋 BG LOC RECV 0x" +
                            String.format("%02X", ver) +
                            " 상대방 수신 sender=" + sender +
                            " lat=" + lat + " lng=" + lng);
                }

                String label = isMyEcho ? "내 Echo 저장 (BG)" : "수신 위치 저장 (BG)";
                insertLocationEntity(addLoc,
                        label + " (0x" + String.format("%02X", ver) + ")");

                recordSatTrackPoint(lat, lng, ver);
            }
            // UAV 모드 (0x02, 0x12) 는 일단 제외 (드물게 사용)

        } catch (Exception e) {
            Log.v(TAG, "⚠ BG RECEIVED 처리 실패: " + e.getMessage());
        }
    }

    /**
     * LocationEntity를 DB에 저장 (백그라운드 스레드)
     */
    private void insertLocationEntity(
            com.ah.acr.messagebox.database.LocationEntity entity,
            String logLabel) {
        // Room DB는 메인 스레드 접근 금지 → 백그라운드 스레드
        new Thread(() -> {
            try {
                // ⭐ v4 Phase B-2-4-B: Kotlin 헬퍼 사용
                // MsgRoomDatabase + LocationDao (suspend fun) 래핑
                long id = LocationDbHelper.insertFromService(
                        getApplicationContext(), entity);

                if (id > 0) {
                    Log.v(TAG, "🔋 Location ADD: " + logLabel + " (id=" + id + ")");
                } else {
                    Log.v(TAG, "⚠ Location 저장 실패: " + logLabel);
                }

                // 세션 포인트 카운트 업데이트
                if (mIsTracking || mIsSos) {
                    mSessionPointCount++;
                    // Notification 업데이트 (메인 스레드로)
                    new android.os.Handler(android.os.Looper.getMainLooper())
                            .post(() -> updateNotification());
                }

                // Activity 재활성화 시 UI 새로고침 위해 Broadcast
                android.content.Intent echoIntent = new android.content.Intent(
                        BROADCAST_ECHO_RECEIVED);
                echoIntent.setPackage(getPackageName());
                sendBroadcast(echoIntent);

            } catch (Exception e) {
                Log.v(TAG, "⚠ DB 저장 중 오류: " + e.getMessage());
            }
        }).start();
    }

    /**
     * SatTrackStateHolder에 포인트 기록
     */
    private void recordSatTrackPoint(double lat, double lng, byte ver) {
        try {
            com.ah.acr.messagebox.database.SatTrackStateHolder.recordPoint(
                    this, lat, lng, 0.0, 0.0, 0.0, null, ver);
        } catch (Exception e) {
            Log.v(TAG, "⚠ SatTrack 기록 실패: " + e.getMessage());
        }
    }

    /**
     * IMEI 주소 파싱 (MainActivity.parseAddress와 동일)
     */
    private String parseAddress(io.netty.buffer.ByteBuf buffer, int senderLen) {
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

    /**
     * BROAD 패킷 파싱 + 상태 업데이트 + Notification 갱신
     * 형식: BROAD=battery,inbox,unsent,signal,gpsTime,gpsLat,gpsLng,sos,tracking
     */
    private void parseBroad(String packet) {
        try {
            String msg = packet.substring(6); // "BROAD=" 제거
            String[] vals = msg.split(",");

            if (vals.length >= 4) {
                // ⭐ v4 Phase B-2-4-B: 배터리 변환
                int rawBattery = Integer.parseInt(vals[0]);
                mLastBattery = convertBatteryToPercent(rawBattery);

                mLastInbox = Integer.parseInt(vals[1]);
                mLastSignal = Integer.parseInt(vals[3]);
                mLastBroadTime = System.currentTimeMillis();
                mBroadCount++;

                // 세션 상태 (tracking, sos)
                boolean prevTracking = mIsTracking;
                boolean prevSos = mIsSos;

                if (vals.length > 7) mIsSos = !vals[7].equals("0");
                if (vals.length > 8) mIsTracking = !vals[8].equals("0");

                // 세션 상태 변화 감지
                if (mIsTracking != prevTracking || mIsSos != prevSos) {
                    Log.v(TAG, "⭐ 세션 상태 변화: tracking=" + mIsTracking +
                            " sos=" + mIsSos);
                    if (mIsTracking || mIsSos) {
                        if (mSessionStartTime == 0) {
                            mSessionStartTime = System.currentTimeMillis();
                            mSessionPointCount = 0;
                        }
                    } else {
                        mSessionStartTime = 0;
                        mSessionPointCount = 0;
                    }
                }

                // Notification 업데이트
                updateNotification();
            }
        } catch (Exception e) {
            Log.v(TAG, "BROAD 파싱 실패: " + e.getMessage());
        }
    }

    /**
     * ⭐ v4 Phase B-2-4-B: 배터리 mV → % 변환
     * TYTO2 장비는 배터리 전압(mV)을 전송하는 것으로 추정
     *
     * 일반적인 Li-ion 배터리:
     * - 4200mV (4.2V) = 100% (완충)
     * - 3300mV (3.3V) = 0% (저전압 차단)
     *
     * 변환 공식: (mV - 3300) * 100 / 900
     *
     * @param raw BROAD에서 받은 원시값 (mV 또는 이미 %)
     * @return 0~100 범위의 배터리 퍼센트
     */
    private int convertBatteryToPercent(int raw) {
        // 1000 이상이면 mV로 판단 (배터리 % 값이 1000% 넘을 수 없음)
        if (raw > 1000) {
            int percent = (raw - 3300) * 100 / 900;
            // 0~100 범위로 제한
            return Math.max(0, Math.min(100, percent));
        }
        // 이미 % 값이면 그대로
        return raw;
    }


    // ═════════════════════════════════════════════════════════
    //   ⭐ v4 Phase B-2-3: Broadcast 방식 패킷 수신
    //   LiveData observeForever의 메인 스레드 경합 이슈 해결
    // ═════════════════════════════════════════════════════════

    private void registerPacketReceiver() {
        mPacketReceiver = new android.content.BroadcastReceiver() {
            @Override
            public void onReceive(android.content.Context context, Intent intent) {
                if (intent == null) return;

                String action = intent.getAction();
                if (BROADCAST_PACKET_RECEIVED.equals(action)) {
                    String packet = intent.getStringExtra("packet");
                    if (packet != null) {
                        handleReceivedPacket(packet);
                    }
                } else if (BROADCAST_ACTIVITY_POINT_SAVED.equals(action)) {
                    // ⭐ v4 Phase B-2-5: Activity가 저장 완료 알림
                    // Service의 포인트 카운트 동기화
                    if (mIsTracking || mIsSos) {
                        mSessionPointCount++;
                        Log.v(TAG, "📊 Activity 저장 완료 → pt=" + mSessionPointCount);
                        updateNotification();
                    }
                }
            }
        };

        android.content.IntentFilter filter = new android.content.IntentFilter();
        filter.addAction(BROADCAST_PACKET_RECEIVED);
        filter.addAction(BROADCAST_ACTIVITY_POINT_SAVED);

        // Android 13+ : RECEIVER_NOT_EXPORTED 필수
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mPacketReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(mPacketReceiver, filter);
        }

        Log.v(TAG, "📡 Packet Receiver 등록 완료");
    }

    private void unregisterPacketReceiver() {
        if (mPacketReceiver != null) {
            try {
                unregisterReceiver(mPacketReceiver);
                Log.v(TAG, "📡 Packet Receiver 해제");
            } catch (Exception e) {
                Log.v(TAG, "Receiver 해제 실패 (이미 해제됨?): " + e.getMessage());
            }
            mPacketReceiver = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG, "onStartCommand: " +
                (intent != null ? intent.getAction() : "null"));

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopForegroundService();
            return START_NOT_STICKY;
        }

        // Foreground Service 시작
        Notification notification = buildNotification();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ : type 명시 필수
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            );
        } else {
            // Android 13 이하 : type 없이
            startForeground(NOTIFICATION_ID, notification);
        }

        isServiceRunning = true;
        Log.v(TAG, "✅ Foreground Service 시작됨");

        // START_STICKY: 시스템이 죽여도 자동 재시작
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.v(TAG, "onDestroy");
        isServiceRunning = false;

        // ⭐ v4 Phase B-2-3: Receiver 해제
        unregisterPacketReceiver();

        // ⭐ v4 Phase B-2-1: Service 종료 시에만 BLE 정리
        // Activity와 달리 Service는 앱 강제 종료 시에만 onDestroy 호출됨
        // 이렇게 하면 Activity 종료/화면 끄기에서도 BLE 유지 가능
        try {
            com.ah.acr.messagebox.ble.BLE.INSTANCE.destroyBle();
            Log.v(TAG, "✅ BLE 정리 완료");
        } catch (Exception e) {
            Log.v(TAG, "BLE 정리 중 오류: " + e.getMessage());
        }

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // bind 방식 사용 안 함
    }


    // ═════════════════════════════════════════════════════════
    //   Service 종료 로직
    // ═════════════════════════════════════════════════════════

    private void stopForegroundService() {
        Log.v(TAG, "Service 종료 중...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
        stopSelf();
    }


    // ═════════════════════════════════════════════════════════
    //   Notification 관리
    // ═════════════════════════════════════════════════════════

    /**
     * Android 8.0+ 필수: Notification Channel 생성
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW // 소리 없음
            );
            channel.setDescription(CHANNEL_DESC);
            channel.setShowBadge(false); // 앱 아이콘에 뱃지 없음
            mNotificationManager.createNotificationChannel(channel);
            Log.v(TAG, "Notification Channel 생성됨");
        }
    }

    /**
     * 현재 상태에 맞는 Notification 생성
     */
    private Notification buildNotification() {
        String title;
        String content;

        // ⭐ v4 Phase B-2-1: BLE 연결 상태 확인
        boolean bleConnected = false;
        try {
            bleConnected = com.ah.acr.messagebox.ble.BLE.INSTANCE
                    .getSelectedDevice().getValue() != null;
        } catch (Exception ignored) {
        }

        if (mIsSos) {
            title = "🆘 TYTO Connect - SOS";
            content = formatSessionInfo();
        } else if (mIsTracking) {
            title = "🛰 TYTO Connect - TRACK";
            content = formatSessionInfo();
        } else if (bleConnected) {
            title = "📡 TYTO Connect";
            // ⭐ v4 Phase B-2-4-A: BROAD에서 파싱한 실시간 상태 표시
            if (mLastBattery >= 0) {
                StringBuilder sb = new StringBuilder();
                sb.append("🔋 ").append(mLastBattery).append("%");
                if (mLastSignal >= 0) sb.append(" · 📶 ").append(mLastSignal);
                if (mLastInbox >= 0) sb.append(" · ✉ ").append(mLastInbox);
                sb.append(" · 📡 ").append(mBroadCount);
                content = sb.toString();
            } else {
                content = "Connected & monitoring";
            }
        } else {
            title = "📡 TYTO Connect";
            content = "Waiting for device...";
        }

        // 앱 탭 시 열릴 Intent
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(
                Intent.FLAG_ACTIVITY_SINGLE_TOP |
                Intent.FLAG_ACTIVITY_CLEAR_TOP
        );

        int pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pendingFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent, pendingFlags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher) // TODO: 전용 알림 아이콘으로 교체
                .setContentIntent(pendingIntent)
                .setOngoing(true) // 스와이프로 지울 수 없음
                .setShowWhen(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build();
    }

    /**
     * 세션 정보 포매팅
     */
    private String formatSessionInfo() {
        StringBuilder sb = new StringBuilder();

        // 세션 시간
        if (mSessionStartTime > 0) {
            long elapsed = System.currentTimeMillis() - mSessionStartTime;
            long seconds = elapsed / 1000;
            long hours = seconds / 3600;
            long minutes = (seconds % 3600) / 60;
            long secs = seconds % 60;
            sb.append(String.format("%02d:%02d:%02d", hours, minutes, secs));
            sb.append(" · ");
        }

        sb.append(mSessionPointCount).append("pt");

        // 배터리/신호 추가
        if (mLastBattery >= 0) {
            sb.append(" · 🔋").append(mLastBattery).append("%");
        }
        if (mLastSignal >= 0) {
            sb.append(" · 📶").append(mLastSignal);
        }

        return sb.toString();
    }

    /**
     * Notification 업데이트 (상태 변경 시 호출)
     */
    private void updateNotification() {
        if (mNotificationManager != null) {
            mNotificationManager.notify(NOTIFICATION_ID, buildNotification());
        }
    }


    // ═════════════════════════════════════════════════════════
    //   Phase B-2에서 구현 예정
    //   - BLE 연결 관리
    //   - receivePacketProcess()
    //   - LocationEntity 저장
    //   - SatTrackStateHolder 연동
    // ═════════════════════════════════════════════════════════
}
