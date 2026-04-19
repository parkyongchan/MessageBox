package com.ah.acr.messagebox.service

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import pub.devrel.easypermissions.EasyPermissions


/**
 * Permission management for location tracking
 *
 * Java-callable via @JvmStatic annotations
 */
object LocationPermissionHelper {

    // Request codes - const val for Java switch-case compatibility
    const val REQUEST_CODE_LOCATION = 1001
    const val REQUEST_CODE_BACKGROUND_LOCATION = 1002
    const val REQUEST_CODE_NOTIFICATIONS = 1003


    /**
     * Check basic location permission
     */
    @JvmStatic
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }


    /**
     * Check background location permission (Android 10+)
     */
    @JvmStatic
    fun hasBackgroundLocationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    /**
     * Check notification permission (Android 13+)
     */
    @JvmStatic
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }


    /**
     * Check all required permissions
     */
    @JvmStatic
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasLocationPermission(context) &&
                hasBackgroundLocationPermission(context) &&
                hasNotificationPermission(context)
    }


    /**
     * Check if GPS is enabled
     */
    @JvmStatic
    fun isGpsEnabled(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }


    // ═══════════════════════════════════════════════════════
    // Permission Requests (EasyPermissions)
    // ═══════════════════════════════════════════════════════

    /**
     * Request basic location permission (Fragment version)
     */
    @JvmStatic
    fun requestLocationPermission(fragment: Fragment) {
        if (hasLocationPermission(fragment.requireContext())) return

        EasyPermissions.requestPermissions(
            fragment,
            "Location permission is required for GPS tracking.",
            REQUEST_CODE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }


    /**
     * Request basic location permission (Activity version)
     */
    @JvmStatic
    fun requestLocationPermission(activity: Activity) {
        if (hasLocationPermission(activity)) return

        EasyPermissions.requestPermissions(
            activity,
            "Location permission is required for GPS tracking.",
            REQUEST_CODE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }


    /**
     * Request background location permission (Android 10+)
     */
    @JvmStatic
    fun requestBackgroundLocationPermission(fragment: Fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (hasBackgroundLocationPermission(fragment.requireContext())) return

        EasyPermissions.requestPermissions(
            fragment,
            "Background location access is needed to keep tracking when the screen is off.",
            REQUEST_CODE_BACKGROUND_LOCATION,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        )
    }


    /**
     * Request notification permission (Android 13+)
     */
    @JvmStatic
    fun requestNotificationPermission(fragment: Fragment) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (hasNotificationPermission(fragment.requireContext())) return

        ActivityCompat.requestPermissions(
            fragment.requireActivity(),
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATIONS
        )
    }


    /**
     * Get the next permission that needs to be requested
     */
    @JvmStatic
    fun getNextPermissionToRequest(context: Context): String? {
        return when {
            !hasLocationPermission(context) -> Manifest.permission.ACCESS_FINE_LOCATION
            !hasBackgroundLocationPermission(context) -> Manifest.permission.ACCESS_BACKGROUND_LOCATION
            !hasNotificationPermission(context) -> Manifest.permission.POST_NOTIFICATIONS
            else -> null
        }
    }


    /**
     * Get rationale message for a permission
     */
    @JvmStatic
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION ->
                "Location permission is required for GPS tracking."
            Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                "Background location access is needed for reliable tracking."
            Manifest.permission.POST_NOTIFICATIONS ->
                "Notification permission is needed to show tracking status."
            else -> "Permission required."
        }
    }
}