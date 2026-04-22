package com.ah.acr.messagebox.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.ah.acr.messagebox.data.DeviceInfo
import com.ah.acr.messagebox.data.FirmUpdate
import com.ah.acr.messagebox.viewmodel.EventsQueue
import com.clj.fastble.BleManager
import com.clj.fastble.callback.*
import com.clj.fastble.data.BleDevice
import com.clj.fastble.exception.BleException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.logging.Logger


object BLE {

    // ==================== 연결 상태 상수 ====================
    const val CONNECT_STATUS_TRYING = "trying to connect"
    const val CONNECT_STATUS_FAILED = "connection failed"
    const val CONNECT_STATUS_CONNECTED = "connected"
    const val CONNECT_STATUS_LOST = "connection lost"
    const val CONNECT_STATUS_DISCONNECTED = "disconnected"
    const val CONNECT_STATUS_RECONNECTING = "reconnecting"       // v2 신규: 자동 재연결 중

    const val BLE_LOGIN_TRY = "bleLoginTry"
    const val BLE_LOGIN_FAIL = "bleLoginFail"
    const val BLE_LOGIN_OK = "bleLoginOk"
    const val BLE_LOGIN_CHANGE_TRY = "bleLoginChangeTry"
    const val BLE_LOGIN_CHANGE_OK = "bleLoginChangeOk"
    const val BLE_LOGIN_CHANGE_FAIL = "bleLoginChangeFail"
    const val BLE_LOGIN_NONE = "none"

    // ==================== 기존 변수 ====================
    private val ble: BleManager = BleManager.getInstance()
    private var deviceList: ArrayList<BleDevice> = ArrayList()
    var bleLiveDeviceList: MutableLiveData<ArrayList<BleDevice>> = MutableLiveData()
    var scanStatus: MutableLiveData<Boolean> = MutableLiveData()
    val deviceInfo: MutableLiveData<DeviceInfo> = MutableLiveData()
    var deviceSet: MutableLiveData<String> = MutableLiveData()
    var bleLoginStatus: MutableLiveData<String> = MutableLiveData()
    var connectionStatus: MutableLiveData<String> = MutableLiveData()
    var selectedDevice: MutableLiveData<BleDevice> = MutableLiveData()
    var availableGatt: ArrayList<String> = ArrayList()
    var selDeviceGatt: ArrayList<BluetoothGattCharacteristic> = ArrayList()
    var receiveData: ArrayList<String> = ArrayList()

    var isLogon: MutableLiveData<Boolean> = MutableLiveData()
    var isFirmwareUdate: MutableLiveData<Boolean> = MutableLiveData()
    var firmwareUdateState: MutableLiveData<FirmUpdate> = MutableLiveData()

    val writeQueue = EventsQueue()
    var outboxMsgStatus: MutableLiveData<String> = MutableLiveData()

    // ==================== v2 자동 재연결 관련 변수 ====================

    /** 최대 재연결 시도 횟수 */
    private const val MAX_RECONNECT_ATTEMPTS: Int = 5

    /** 사용자가 명시적으로 disconnect 버튼을 눌렀는지 여부. true면 자동 재연결 안 함 */
    private var isUserDisconnect: Boolean = false

    /** 현재 재연결 시도 횟수 (성공 시 0으로 리셋) */
    private var reconnectAttempts: Int = 0

    /** 마지막으로 연결 성공한 장치. 재연결 대상으로 사용 */
    private var lastConnectedDevice: BleDevice? = null

    /** 재연결 스케줄러 (메인 스레드) */
    private val reconnectHandler = Handler(Looper.getMainLooper())

    /** 현재 예약된 재연결 작업 (취소용 참조) */
    private var pendingReconnectRunnable: Runnable? = null

    /** 재연결 진행 중 여부 (중복 방지) */
    private var isReconnecting: Boolean = false

    // ==================== 데이터 수신 ====================
    fun addReceviceData(data: String) {
        receiveData.add(data)
    }

    // ==================== 초기화 ====================

    /**
     * BLE 초기화
     *
     * [v1 수정사항]
     * - setConnectOverTime: 100초 → 10초
     * - setReConnectCount(3, 3000) 추가 (라이브러리 내장 재시도)
     *
     * [v2 수정사항]
     * - (변경 없음) 재연결 로직은 BLE 객체 내부 변수로 관리
     */
    fun initiate(application: Application) {
        ble.init(application)
        ble.enableLog(true)
            .setConnectOverTime(10000)       // 10초 타임아웃
            .setReConnectCount(3, 3000)      // 3회 재시도, 3초 간격
            .setSplitWriteNum(20)
            .setOperateTimeout(5000)
        ble.maxConnectCount = 1
        isLogon.postValue(false)
        isFirmwareUdate.postValue(false)
    }

    fun checkBleSupport(): Boolean {
        if (ble.isSupportBle) {
            ble.enableBluetooth()
            if (ble.isBlueEnable)
                return true
        } else {
            return false
        }
        return false
    }

    // ==================== 스캔 ====================
    fun startScan() {
        scanStatus.postValue(true)
        deviceList.clear()
        bleLiveDeviceList.postValue(deviceList)

        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                scanStatus.postValue(true)
            }

            override fun onScanning(bleDevice: BleDevice?) {
                if (bleDevice != null) {
                    if (bleDevice.name != null) {
                        deviceList.add(bleDevice)
                        bleLiveDeviceList.postValue(deviceList)
                    }
                }
            }

            override fun onScanFinished(scanResultList: MutableList<BleDevice>?) {
                deviceList.clear()
                if (selectedDevice.value?.device != null) selectedDevice.value?.let {
                    deviceList.add(it)
                }
                for (scanResult in scanResultList!!) {
                    if (scanResult.name != null)
                        deviceList.add(scanResult)
                }

                bleLiveDeviceList.postValue(deviceList)
                scanStatus.postValue(false)
            }
        })
    }

    // ==================== v2 자동 재연결 로직 ====================

    /**
     * 자동 재연결 스케줄링 (지수 백오프)
     *
     * [동작 방식]
     * - 1회 실패: 1초 후 재시도
     * - 2회 실패: 2초 후 재시도
     * - 3회 실패: 4초 후 재시도
     * - 4회 실패: 8초 후 재시도
     * - 5회 실패: 16초 후 재시도
     * - 5회 초과: 포기 (사용자가 retryConnect()로 수동 재시도 가능)
     *
     * [호출 시점]
     * - onConnectFail: 라이브러리 내장 재시도(3회)까지 모두 소진된 경우
     * - onDisConnected: 의도치 않은 연결 끊김 (isActiveDisConnected=false)
     */
    private fun scheduleReconnect() {
        // 사용자가 끊었으면 재연결 안 함
        if (isUserDisconnect) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: user disconnected")
            return
        }

        // 재연결할 장치가 없으면 안 함
        val targetDevice = lastConnectedDevice
        if (targetDevice == null) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: no last device")
            return
        }

        // 이미 재연결 예약되어 있으면 중복 안 함
        if (pendingReconnectRunnable != null) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: already scheduled")
            return
        }

        // 최대 횟수 초과 시 포기
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Logger.getLogger("BLE").info("[v2] Give up reconnect: max attempts reached")
            reconnectAttempts = 0
            isReconnecting = false
            connectionStatus.postValue(CONNECT_STATUS_FAILED)
            return
        }

        // 지수 백오프 계산: 1초, 2초, 4초, 8초, 16초
        val delayMs = 1000L * (1L shl reconnectAttempts)
        reconnectAttempts++
        isReconnecting = true

        Logger.getLogger("BLE").info(
            "[v2] Schedule reconnect #$reconnectAttempts in ${delayMs}ms"
        )

        connectionStatus.postValue(CONNECT_STATUS_RECONNECTING)

        // 재연결 작업 예약
        val runnable = Runnable {
            pendingReconnectRunnable = null
            Logger.getLogger("BLE").info("[v2] Executing reconnect attempt #$reconnectAttempts")
            connect(targetDevice)
        }
        pendingReconnectRunnable = runnable
        reconnectHandler.postDelayed(runnable, delayMs)
    }

    /**
     * 예약된 재연결 작업 취소
     *
     * [호출 시점]
     * - 사용자가 명시적으로 disconnect한 경우
     * - 연결 성공해서 재연결이 필요 없어진 경우
     */
    private fun cancelPendingReconnect() {
        pendingReconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            Logger.getLogger("BLE").info("[v2] Cancelled pending reconnect")
        }
        pendingReconnectRunnable = null
        isReconnecting = false
    }

    /**
     * 재연결 상태 완전 초기화 (연결 성공 시 호출)
     */
    private fun resetReconnectState() {
        reconnectAttempts = 0
        isReconnecting = false
        cancelPendingReconnect()
    }

    // ==================== 연결 ====================

    /**
     * BLE 연결
     *
     * [v2 변경사항]
     * - 신규 연결 시: 재연결 상태 초기화
     * - 재연결 중 호출: 플래그 유지 (scheduleReconnect -> connect 흐름)
     * - 연결 대상 장치 저장 (lastConnectedDevice)
     */
    fun connect(device: BleDevice) {
        // v2: 재연결이 아닌 신규 연결 요청 시 상태 초기화
        if (!isReconnecting) {
            isUserDisconnect = false
            reconnectAttempts = 0
            cancelPendingReconnect()
        }

        // v2: 재연결 대상으로 저장
        lastConnectedDevice = device

        BleManager.getInstance().connect(device, object : BleGattCallback() {
            override fun onStartConnect() {
                connectionStatus.postValue(CONNECT_STATUS_TRYING)
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                // v2: 사용자가 끊은 게 아니면 자동 재연결 스케줄
                // (라이브러리 내장 재시도 3회도 이미 소진된 상태)
                if (!isUserDisconnect) {
                    Logger.getLogger("BLE").info(
                        "[v2] Connect failed: ${exception.description}, scheduling reconnect"
                    )
                    scheduleReconnect()
                } else {
                    connectionStatus.postValue(CONNECT_STATUS_FAILED)
                }
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                // v2: 연결 성공 → 재연결 상태 리셋
                resetReconnectState()

                selectedDevice.postValue(device)
                connectionStatus.postValue(CONNECT_STATUS_CONNECTED)
                bleLiveDeviceList.postValue(deviceList)

                val availableServices = ble.getBluetoothGattServices(bleDevice)
                availableGatt.clear()
                selDeviceGatt.clear()
                for (service in availableServices) {
                    for (characteristics in service.characteristics) {
                        availableGatt.add(service.uuid.toString() + " : " + characteristics.uuid + "\n")
                        if (service.uuid.equals(UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")))
                            selDeviceGatt.add(characteristics)
                    }
                }
            }

            override fun onDisConnected(
                isActiveDisConnected: Boolean,
                bleDevice: BleDevice,
                gatt: BluetoothGatt,
                status: Int
            ) {
                bleLiveDeviceList.postValue(deviceList)
                selectedDevice.postValue(null)
                bleLoginStatus.postValue(BLE_LOGIN_NONE)

                // v2: 의도치 않은 끊김 (거리 이탈, 전원 OFF 등) → 자동 재연결
                // isActiveDisConnected=true: 앱이 명시적으로 disconnect 호출
                // isActiveDisConnected=false: 시스템/장치 쪽에서 끊김
                if (!isUserDisconnect && !isActiveDisConnected) {
                    Logger.getLogger("BLE").info(
                        "[v2] Unexpected disconnect, scheduling reconnect"
                    )
                    connectionStatus.postValue(CONNECT_STATUS_LOST)
                    scheduleReconnect()
                } else {
                    connectionStatus.postValue(CONNECT_STATUS_LOST)
                }
            }
        })
    }

    // ==================== 읽기/쓰기 ====================
    fun readData(bleDevice: BleDevice, uuid_service: String, uuid_characteristic_read: String) {
        ble.read(bleDevice, uuid_service, uuid_characteristic_read, object : BleReadCallback() {
            override fun onReadSuccess(data: ByteArray) {
                val strData = String(data, StandardCharsets.UTF_8)
            }
            override fun onReadFailure(exception: BleException) {
            }
        })
    }

    fun writeData(bleDevice: BleDevice, uuid_service: String, uuid_characteristic_write: String, data: ByteArray) {
        BleManager.getInstance().write(bleDevice, uuid_service, uuid_characteristic_write, data, object : BleWriteCallback() {
            override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
            }
            override fun onWriteFailure(exception: BleException) {
            }
        })
    }

    // ==================== 연결 해제 ====================

    /**
     * 사용자 요청에 의한 연결 해제
     *
     * [v1 변경사항]
     * - ble.destroy() 제거됨
     *
     * [v2 변경사항]
     * - isUserDisconnect = true 플래그로 자동 재연결 차단
     * - 예약된 재연결 작업 취소
     */
    fun disconnect(device: BleDevice) {
        // v2: 사용자 의도 플래그 세팅 (자동 재연결 차단)
        isUserDisconnect = true

        // v2: 예약된 재연결 취소
        cancelPendingReconnect()
        reconnectAttempts = 0

        isLogon.postValue(false)
        ble.disconnect(device)
        connectionStatus.postValue(CONNECT_STATUS_DISCONNECTED)
    }

    /**
     * 앱 완전 종료 시에만 호출하는 전체 정리 함수
     */
    fun destroyBle() {
        isUserDisconnect = true
        cancelPendingReconnect()
        reconnectAttempts = 0
        lastConnectedDevice = null
        ble.disconnectAllDevice()
        ble.destroy()
    }

    // ==================== v2 공개 API ====================

    /**
     * 현재 자동 재연결 진행 중인지 확인
     * UI에서 "재연결 중..." 표시할 때 사용
     */
    fun isReconnecting(): Boolean = isReconnecting

    /**
     * 현재까지의 재연결 시도 횟수
     * UI에서 "3/5회 시도 중" 같은 표시할 때 사용
     */
    fun getReconnectAttempts(): Int = reconnectAttempts

    /**
     * 최대 재연결 시도 횟수 (상수)
     * UI에서 "n/5회" 표시할 때 사용
     */
    fun getMaxReconnectAttempts(): Int = MAX_RECONNECT_ATTEMPTS

    /**
     * 자동 재연결 강제 중단
     * UI에서 "재연결 취소" 버튼 누를 때 사용
     */
    fun cancelReconnect() {
        isUserDisconnect = true
        cancelPendingReconnect()
        reconnectAttempts = 0
        connectionStatus.postValue(CONNECT_STATUS_FAILED)
    }

    /**
     * 수동 재연결 시도
     * 재연결이 5회 실패로 포기된 상태에서 "다시 시도" 버튼 누를 때 사용
     */
    fun retryConnect() {
        val device = lastConnectedDevice
        if (device != null) {
            isUserDisconnect = false
            reconnectAttempts = 0
            isReconnecting = false
            connect(device)
        }
    }

    fun stopScan() {
        ble.cancelScan()
    }
}
