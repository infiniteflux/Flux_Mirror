package com.flux_mirror.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

object PermissionsHelper {

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun getRequiredPermissions(): Array<String> {
        val permissions = mutableListOf<String>(
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * Check if all required permissions are granted
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun hasAllPermissions(context: Context): Boolean {
        Log.d("PermissionsHelper", "--- Checking hasAllPermissions ---")
        val allGranted = getRequiredPermissions().all { permission ->
            val isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                Log.e("PermissionsHelper", "❌ MISSING: $permission")
            }
            isGranted
        }
        Log.d("PermissionsHelper", "All permissions granted: $allGranted")
        return allGranted
    }

    /**
     * Get list of missing permissions
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun getMissingPermissions(context: Context): List<String> {
        Log.d("PermissionsHelper", "--- Checking getMissingPermissions ---")
        return getRequiredPermissions().filter { permission ->
            val isGranted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
            if (isGranted) {
                Log.i("PermissionsHelper", "✅ GRANTED: $permission")
            } else {
                Log.e("PermissionsHelper", "❌ MISSING: $permission")
            }
            !isGranted
        }
    }

    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) ==
                PackageManager.PERMISSION_GRANTED
    }

    // ========== OVERLAY PERMISSION FUNCTIONS ==========

    /**
     * Check if overlay permission (SYSTEM_ALERT_WINDOW) is granted
     * Required for floating windows that appear over other apps
     */
    fun hasOverlayPermission(context: Context): Boolean {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            // Permission automatically granted on pre-Marshmallow
            true
        }

        Log.d("PermissionsHelper", "Overlay permission granted: $hasPermission")
        return hasPermission
    }

    /**
     * Check if app has foreground service permission (for floating service)
     * Available from Android 9 (API 28)
     */
    fun hasForegroundServicePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            hasPermission(context, Manifest.permission.FOREGROUND_SERVICE)
        } else {
            // Not required for older versions
            true
        }
    }

    /**
     * Comprehensive permission check with detailed logging
     * Useful for debugging permission issues
     */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    fun performPermissionAudit(context: Context) {
        Log.d("PermissionsHelper", "========================================")
        Log.d("PermissionsHelper", "       PERMISSION AUDIT REPORT")
        Log.d("PermissionsHelper", "========================================")

        // Runtime permissions
        Log.d("PermissionsHelper", "\n--- RUNTIME PERMISSIONS ---")
        getRequiredPermissions().forEach { permission ->
            val isGranted = hasPermission(context, permission)
            val status = if (isGranted) "✅ GRANTED" else "❌ DENIED"
            Log.d("PermissionsHelper", "$status: $permission")
        }

        // Overlay permission
        Log.d("PermissionsHelper", "\n--- SPECIAL PERMISSIONS ---")
        val overlayStatus = if (hasOverlayPermission(context)) "✅ GRANTED" else "❌ DENIED"
        Log.d("PermissionsHelper", "$overlayStatus: SYSTEM_ALERT_WINDOW (Overlay)")

        // Foreground service
        val foregroundStatus = if (hasForegroundServicePermission(context)) "✅ GRANTED" else "❌ DENIED"
        Log.d("PermissionsHelper", "$foregroundStatus: FOREGROUND_SERVICE")

        // Summary
        Log.d("PermissionsHelper", "\n--- SUMMARY ---")
        val totalRequired = getRequiredPermissions().size + 2 // +2 for overlay and foreground service
        val totalGranted = getRequiredPermissions().count { hasPermission(context, it) } +
                (if (hasOverlayPermission(context)) 1 else 0) +
                (if (hasForegroundServicePermission(context)) 1 else 0)

        Log.d("PermissionsHelper", "Granted: $totalGranted/$totalRequired")
        Log.d("PermissionsHelper", "Status: ${if (totalGranted == totalRequired) "ALL PERMISSIONS GRANTED ✅" else "MISSING PERMISSIONS ⚠️"}")
        Log.d("PermissionsHelper", "========================================\n")
    }
}