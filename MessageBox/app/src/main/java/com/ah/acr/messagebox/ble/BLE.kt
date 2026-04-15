package com.ah.acr.messagebox.ble

import android.app.Application
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
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

    const val CONNECT_STATUS_TRYING = "trying to connect"
    const val CONNECT_STATUS_FAILED = "connection failed"
    const val CONNECT_STATUS_CONNECTED = "connected"
    const val CONNECT_STATUS_LOST = "connection lost"
    const val CONNECT_STATUS_DISCONNECTED = "disconnected"

    const val BLE_LOGIN_TRY = "bleLoginTry"
    const val BLE_LOGIN_FAIL = "bleLoginFail"
    const val BLE_LOGIN_OK = "bleLoginOk"
    const val BLE_LOGIN_CHANGE_TRY = "bleLoginChangeTry"
    const val BLE_LOGIN_CHANGE_OK = "bleLoginChangeOk"
    const val BLE_LOGIN_CHANGE_FAIL = "bleLoginChangeFail"
    const val BLE_LOGIN_NONE = "none"

    private val ble: BleManager = BleManager.getInstance()
    private var deviceList: ArrayList<BleDevice> = ArrayList()
    var bleLiveDeviceList: MutableLiveData<ArrayList<BleDevice>> = MutableLiveData()
    var scanStatus: MutableLiveData<Boolean> = MutableLiveData()
    val deviceInfo: MutableLiveData<DeviceInfo> = MutableLiveData()
    var deviceSet: MutableLiveData<String> = MutableLiveData()
    //var bleLogin: MutableLiveData<Boolean> = MutableLiveData()
    var bleLoginStatus: MutableLiveData<String> = MutableLiveData()
    var connectionStatus: MutableLiveData<String> = MutableLiveData()
    var selectedDevice: MutableLiveData<BleDevice> = MutableLiveData()
    var availableGatt: ArrayList<String> = ArrayList()
    var selDeviceGatt: ArrayList<BluetoothGattCharacteristic> = ArrayList()
    var receiveData: ArrayList<String> = ArrayList()

    var isLogon: MutableLiveData<Boolean> = MutableLiveData()
    var isFirmwareUdate: MutableLiveData<Boolean> = MutableLiveData()
    var firmwareUdateState: MutableLiveData<FirmUpdate> = MutableLiveData()

    //var newMsgCode: MutableLiveData<String> = MutableLiveData()

    //var receiveQueue: MutableLiveData<ArrayList<String>> = MutableLiveData()
    val writeQueue = EventsQueue()
    //var writeStatus: MutableLiveData<Boolean> = MutableLiveData()
    var outboxMsgStatus: MutableLiveData<String> = MutableLiveData()

    fun addReceviceData(data: String) {
        receiveData.add(data)
    }

    fun initiate(application: Application) {
        ble.init(application)
        ble.enableLog(true).setConnectOverTime(100000).setSplitWriteNum(20).setOperateTimeout(5000)
        ble.maxConnectCount = 1
        isLogon.postValue(false)
        isFirmwareUdate.postValue(false)
        //Logger.addLogAdapter(AndroidLogAdapter())
    }

    fun checkBleSupport(): Boolean {
        if (ble.isSupportBle) {
            //Logger.i("BLE is supported")

            ble.enableBluetooth()
            if (ble.isBlueEnable)
                return true
        } else {
            //Logger.e("BLE is not supported")
            return false
        }
        return false
    }

    fun startScan() {
        scanStatus.postValue(true)
        deviceList.clear()
        bleLiveDeviceList.postValue(deviceList)

//        if (selectedDevice.value?.device != null) selectedDevice.value?.let {
//            deviceList.add(
//                it
//            )
//        }

        BleManager.getInstance().scan(object : BleScanCallback() {
            override fun onScanStarted(success: Boolean) {
                scanStatus.postValue(true)
                //Logger.d("Scan started : $success")
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
                deviceList.clear();
                if (selectedDevice.value?.device != null) selectedDevice.value?.let {
                    deviceList.add(
                        it
                    )
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

    fun connect(device: BleDevice) {
        BleManager.getInstance().connect(device, object : BleGattCallback() {
            override fun onStartConnect() {
                connectionStatus.postValue(CONNECT_STATUS_TRYING)
                //Logger.i("Trying to connect to ${device.device}")
            }

            override fun onConnectFail(bleDevice: BleDevice, exception: BleException) {
                //Logger.e("Connection failed")
                connectionStatus.postValue(CONNECT_STATUS_FAILED)
            }

            override fun onConnectSuccess(bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                //Logger.i("Connected successfully")
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
                            selDeviceGatt.add(characteristics);
                    }
                }
                //Logger.i(availableGatt.toString())
                //selectedDevice.value?.let { readNotify(it) }
            }

            override fun onDisConnected(isActiveDisConnected: Boolean, bleDevice: BleDevice, gatt: BluetoothGatt, status: Int) {
                connectionStatus.postValue(CONNECT_STATUS_LOST)

                bleLiveDeviceList.postValue(deviceList)
                selectedDevice.postValue(null)
                bleLoginStatus.postValue(BLE_LOGIN_NONE)
                //Logger.e("Disconnected")
            }
        })
    }

    fun readData(bleDevice: BleDevice, uuid_service: String, uuid_characteristic_read: String) {

        ble.read(bleDevice, uuid_service, uuid_characteristic_read, object : BleReadCallback() {
            override fun onReadSuccess(data: ByteArray) {
                val strData = String(data, StandardCharsets.UTF_8)
                //Logger.i("READ DATA = $strData")
            }
            override fun onReadFailure(exception: BleException) {
                //Logger.e("Reading failed")
            }
        })
    }

    fun writeData(bleDevice: BleDevice, uuid_service: String, uuid_characteristic_write: String, data: ByteArray) {

        BleManager.getInstance().write(bleDevice, uuid_service, uuid_characteristic_write, data, object : BleWriteCallback() {
                override fun onWriteSuccess(current: Int, total: Int, justWrite: ByteArray) {
                    //Logger.i("WRITE current:$current, total:$total, data:$justWrite")
                }
                override fun onWriteFailure(exception: BleException) {
                    //Logger.i("WRITE FAILED$exception")
                }
            })
    }

    fun disconnect(device: BleDevice) {
        isLogon.postValue(false);
        ble.disconnect(device)
        ble.destroy()
        connectionStatus.postValue(CONNECT_STATUS_DISCONNECTED)
        //Logger.i("Device disconnected successfully")
    }

    fun stopScan() {
        ble.cancelScan()
    }

}