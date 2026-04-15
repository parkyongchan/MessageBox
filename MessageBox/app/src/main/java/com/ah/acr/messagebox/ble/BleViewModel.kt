package com.ah.acr.messagebox.ble

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.ah.acr.messagebox.data.DeviceInfo
import com.ah.acr.messagebox.data.DeviceStatus
import com.ah.acr.messagebox.util.PermissionChecker
import com.clj.fastble.BleManager
import pub.devrel.easypermissions.EasyPermissions


class BleViewModel(application: Application) : AndroidViewModel(application) {

    var buttonText:MutableLiveData<String> = MutableLiveData("start scanning")
    var pBarVisibility: MutableLiveData<Boolean> = MutableLiveData(false)

    val deviceStatus: MutableLiveData<DeviceStatus> = MutableLiveData()



    fun checkScanStatus() {
        BLE.scanStatus.observeForever {
            if(it) {
                buttonText.postValue("stop scanning")
                pBarVisibility.postValue(true)
            } else {
                buttonText.postValue("start scanning")
                pBarVisibility.postValue(false)
            }
        }

        if(buttonText.value == "start scanning")
            initiate()
        else
            stopScanning()
    }


    private fun initiate() {
        BLE.initiate(getApplication())
        if(BLE.checkBleSupport())
            checkLocationPermission()
    }

    private fun checkLocationPermission() {
        val list: List<String> = getBleRequiredPermissions()
        list.forEach { permission ->
            val hasPermission = EasyPermissions.hasPermissions(getApplication(), permission)
            if (!hasPermission) return
        }
        BLE.startScan()

    }



    fun getBleRequiredPermissions(): List<String> {
        val list: MutableList<String> = ArrayList()
        //BLE required permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            list.add(Manifest.permission.BLUETOOTH_SCAN)
            list.add(Manifest.permission.BLUETOOTH_CONNECT)
            list.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //Android10
            list.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //Android6
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        return list
    }

    fun allBlePermissionsGranted(context: Context): Boolean {
        requireNotNull(context) { "Context is null" }
        return scanPermissionGranted(context) && connectionPermissionGranted(context)
    }

    /**
     * Check if scan-permission has been granted
     */
    fun scanPermissionGranted(context: Context): Boolean {
        requireNotNull(context) { "Context is null" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { //Android12
            //BLUETOOTH_SCAN: enable this central device to scan peripheral devices
            //BLUETOOTH_CONNECT: used to get peripheral device name (BluetoothDevice#getName())
            PermissionChecker.isPermissionGranted(context, Manifest.permission.BLUETOOTH_SCAN) &&
                    PermissionChecker.isPermissionGranted(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { //Android10
            PermissionChecker.isPermissionGranted(context, Manifest.permission.ACCESS_FINE_LOCATION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) { //Android6
            PermissionChecker.isPermissionGranted(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ||
                    PermissionChecker.isPermissionGranted(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    )
        } else {
            true
        }
    }

    /**
     * Check if connection-permission has been granted
     */
    fun connectionPermissionGranted(context: Context): Boolean {
        requireNotNull(context) { "Context is null" }
        //Android12(api31) or higher, BLUETOOTH_CONNECT permission is necessary
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || PermissionChecker.isPermissionGranted(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }

    private fun stopScanning() {
        BLE.stopScan()
    }

}