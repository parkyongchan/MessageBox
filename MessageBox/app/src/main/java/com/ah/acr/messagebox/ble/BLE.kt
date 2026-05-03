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
    const val CONNECT_STATUS_RECONNECTING = "reconnecting"

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
    private const val MAX_RECONNECT_ATTEMPTS: Int = 5
    private var isUserDisconnect: Boolean = false
    private var reconnectAttempts: Int = 0
    private var lastConnectedDevice: BleDevice? = null
    private val reconnectHandler = Handler(Looper.getMainLooper())
    private var pendingReconnectRunnable: Runnable? = null
    private var isReconnecting: Boolean = false

    // ⭐ v3 신규: GATT 정리 후 재연결까지 대기 시간
    // Android가 GATT 클라이언트 슬롯을 회수하는 데 시간이 걸림.
    // 너무 빨리 재연결하면 "이전 GATT 객체 점유 중" 상태에서 또 시도 → 실패
    private const val GATT_CLEANUP_DELAY_MS: Long = 500L

    // ==================== 데이터 수신 ====================
    fun addReceviceData(data: String) {
        receiveData.add(data)
    }

    // ==================== 초기화 ====================

    /**
     * BLE 초기화
     *
     * [v3 변경사항]
     * - setReConnectCount(0, 0)으로 라이브러리 내장 재시도 비활성화
     * - 라이브러리 재시도 + 자체 scheduleReconnect 충돌 방지
     */
    fun initiate(application: Application) {
        ble.init(application)
        ble.enableLog(true)
            .setConnectOverTime(10000)
            .setReConnectCount(0, 0)         // ⭐ v3: 라이브러리 재시도 비활성화 (자체 로직만 사용)
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

    // ==================== ⭐ v3 신규: GATT 강제 정리 ====================

    /**
     * BluetoothGatt 객체를 강제로 close.
     *
     * [왜 필요한가]
     * - com.clj.fastble의 disconnect()는 gatt.close()를 항상 호출하지 않음
     * - close 안 된 GATT는 시스템 GATT 클라이언트 슬롯을 점유 (최대 6~7개)
     * - 슬롯 누적 시 새 연결 자체가 시스템 레벨에서 거부됨
     * - 핸드폰 재부팅 시 슬롯 회수 → 재부팅으로만 해결되는 증상의 원인
     *
     * [동작]
     * - 모든 BleDevice의 BluetoothGatt 객체를 찾아서 close 호출
     * - 예외 발생해도 무시 (best-effort)
     */
    private fun forceCloseGatt(device: BleDevice?) {
        if (device == null) return
        try {
            val gatt = ble.getBluetoothGatt(device)
            if (gatt != null) {
                try {
                    gatt.disconnect()
                } catch (e: Exception) {
                    Logger.getLogger("BLE").warning("[v3] gatt.disconnect failed: ${e.message}")
                }
                try {
                    gatt.close()
                    Logger.getLogger("BLE").info("[v3] gatt.close() success")
                } catch (e: Exception) {
                    Logger.getLogger("BLE").warning("[v3] gatt.close failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Logger.getLogger("BLE").warning("[v3] forceCloseGatt failed: ${e.message}")
        }
    }

    // ==================== v2 자동 재연결 로직 ====================

    /**
     * 자동 재연결 스케줄링 (지수 백오프)
     *
     * [v3 변경사항]
     * - 재연결 전 forceCloseGatt() 호출
     * - GATT_CLEANUP_DELAY_MS만큼 추가 지연 (시스템이 슬롯 회수할 시간)
     */
    private fun scheduleReconnect() {
        if (isUserDisconnect) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: user disconnected")
            return
        }

        val targetDevice = lastConnectedDevice
        if (targetDevice == null) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: no last device")
            return
        }

        if (pendingReconnectRunnable != null) {
            Logger.getLogger("BLE").info("[v2] Skip reconnect: already scheduled")
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            Logger.getLogger("BLE").info("[v2] Give up reconnect: max attempts reached")
            reconnectAttempts = 0
            isReconnecting = false
            connectionStatus.postValue(CONNECT_STATUS_FAILED)
            return
        }

        // 지수 백오프: 1초, 2초, 4초, 8초, 16초
        // ⭐ v3: GATT 정리 시간 추가 (500ms)
        val delayMs = 1000L * (1L shl reconnectAttempts) + GATT_CLEANUP_DELAY_MS
        reconnectAttempts++
        isReconnecting = true

        Logger.getLogger("BLE").info(
            "[v3] Schedule reconnect #$reconnectAttempts in ${delayMs}ms (incl. GATT cleanup)"
        )

        connectionStatus.postValue(CONNECT_STATUS_RECONNECTING)

        // ⭐ v3: 즉시 GATT 강제 정리 (시스템이 슬롯을 회수할 시간을 확보)
        forceCloseGatt(targetDevice)

        val runnable = Runnable {
            pendingReconnectRunnable = null
            Logger.getLogger("BLE").info("[v3] Executing reconnect attempt #$reconnectAttempts")
            connect(targetDevice)
        }
        pendingReconnectRunnable = runnable
        reconnectHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelPendingReconnect() {
        pendingReconnectRunnable?.let {
            reconnectHandler.removeCallbacks(it)
            Logger.getLogger("BLE").info("[v2] Cancelled pending reconnect")
        }
        pendingReconnectRunnable = null
        isReconnecting = false
    }

    private fun resetReconnectState() {
        reconnectAttempts = 0
        isReconnecting = false
        cancelPendingReconnect()
    }

    // ==================== 연결 ====================

    fun connect(device: BleDevice) {
        if (!isReconnecting) {
            isUserDisconnect = false
            reconnectAttempts = 0
            cancelPendingReconnect()
        }

        lastConnectedDevice = device

        BleManager.getInstance().connect(device, object : BleGattCallback() {
            override fun onStartConnect() {
                connectionStatus.postValue(CONNECT_STATUS_TRYING)
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                // ⭐ v3: 연결 실패 시에도 GATT 정리 (실패한 연결의 GATT가 남아있을 수 있음)
                forceCloseGatt(bleDevice)

                if (!isUserDisconnect) {
                    Logger.getLogger("BLE").info(
                        "[v3] Connect failed: ${exception.description}, scheduling reconnect"
                    )
                    scheduleReconnect()
                } else {
                    connectionStatus.postValue(CONNECT_STATUS_FAILED)
                }
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
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
                // ⭐ v3 핵심 수정: GATT 객체 강제 close
                // 이게 누락되면 시스템 GATT 슬롯이 점유된 채로 남아 재연결 실패의 직접 원인
                try {
                    gatt.close()
                    Logger.getLogger("BLE").info("[v3] onDisConnected: gatt.close() success")
                } catch (e: Exception) {
                    Logger.getLogger("BLE").warning("[v3] onDisConnected: gatt.close failed: ${e.message}")
                }

                bleLiveDeviceList.postValue(deviceList)
                selectedDevice.postValue(null)
                bleLoginStatus.postValue(BLE_LOGIN_NONE)

                if (!isUserDisconnect && !isActiveDisConnected) {
                    Logger.getLogger("BLE").info(
                        "[v3] Unexpected disconnect (status=$status), scheduling reconnect"
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
     * [v3 변경사항]
     * - GATT 강제 정리 추가
     */
    fun disconnect(device: BleDevice) {
        isUserDisconnect = true
        cancelPendingReconnect()
        reconnectAttempts = 0

        isLogon.postValue(false)

        // ⭐ v3: GATT 강제 정리 → 라이브러리 disconnect 호출
        forceCloseGatt(device)
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

        // ⭐ v3: 마지막 장치 GATT 정리
        forceCloseGatt(lastConnectedDevice)
        lastConnectedDevice = null

        ble.disconnectAllDevice()
        ble.destroy()
    }

    // ==================== v2 공개 API ====================

    fun isReconnecting(): Boolean = isReconnecting
    fun getReconnectAttempts(): Int = reconnectAttempts
    fun getMaxReconnectAttempts(): Int = MAX_RECONNECT_ATTEMPTS

    fun cancelReconnect() {
        isUserDisconnect = true
        cancelPendingReconnect()
        reconnectAttempts = 0

        // ⭐ v3: 취소 시에도 GATT 정리
        forceCloseGatt(lastConnectedDevice)

        connectionStatus.postValue(CONNECT_STATUS_FAILED)
    }

    fun retryConnect() {
        val device = lastConnectedDevice
        if (device != null) {
            isUserDisconnect = false
            reconnectAttempts = 0
            isReconnecting = false

            // ⭐ v3: 수동 재시도 시에도 GATT 정리 후 연결
            forceCloseGatt(device)
            reconnectHandler.postDelayed({
                connect(device)
            }, GATT_CLEANUP_DELAY_MS)
        }
    }

    fun stopScan() {
        ble.cancelScan()
    }
}
