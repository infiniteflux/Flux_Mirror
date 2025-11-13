package com.flux_mirror.network

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi

class MiracastConnectionManager(private val context: Context) {

    companion object {
        private const val TAG = "MiracastConnection"
    }

    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /**
     * Open Android's Cast Screen settings
     * User must manually connect from there since connectWifiDisplay requires system permission
     */
    fun openCastScreenSettings(): Boolean {
        return try {
            Log.d(TAG, "📱 Opening Cast Screen settings...")

            val intent = Intent(Settings.ACTION_CAST_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)

            Log.d(TAG, "✅ Cast Screen settings opened")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open Cast Screen settings", e)

            // Fallback: Try WiFi Display settings
            try {
                val intent = Intent("android.settings.WIFI_DISPLAY_SETTINGS").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                Log.d(TAG, "✅ WiFi Display settings opened (fallback)")
                true
            } catch (e2: Exception) {
                Log.e(TAG, "❌ Failed to open WiFi Display settings", e2)
                false
            }
        }
    }

    /**
     * Check if currently connected to a Miracast device
     * @return Pair<Boolean, String?> - (isConnected, deviceName)
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun getConnectionStatus(): Pair<Boolean, String?> {
        return try {
            val statusMethod = displayManager.javaClass.getMethod("getWifiDisplayStatus")
            val status = statusMethod.invoke(displayManager)

            if (status != null) {
                val activeDisplayStateMethod = status.javaClass.getMethod("getActiveDisplayState")
                val activeState = activeDisplayStateMethod.invoke(status) as? Int ?: 0

                // Active states: 0=not active, 1=connecting, 2=connected, 3=disconnecting
                val isConnected = (activeState == 2)
                val isConnecting = (activeState == 1)

                // Get active display name if connected
                var deviceName: String? = null
                if (isConnected || isConnecting) {
                    try {
                        val activeDisplayMethod = status.javaClass.getMethod("getActiveDisplay")
                        val activeDisplay = activeDisplayMethod.invoke(status)

                        if (activeDisplay != null) {
                            val getNameMethod = activeDisplay.javaClass.getMethod("getDeviceName")
                            deviceName = getNameMethod.invoke(activeDisplay) as? String
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get active display name", e)
                    }
                }

                Log.d(TAG, "📊 Connection status: state=$activeState, connected=$isConnected, device=$deviceName")
                return Pair(isConnected, deviceName)
            }

            Pair(false, null)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error getting connection status", e)
            Pair(false, null)
        }
    }

    /**
     * Wait for Miracast connection to establish
     * THIS MUST BE CALLED FROM A BACKGROUND THREAD (IO Dispatcher)
     * @param expectedDeviceName The device name we expect to connect to (optional)
     * @param timeoutSeconds Maximum time to wait
     * @param onStatusUpdate Callback for status updates
     * @return true if connected successfully
     */
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    suspend fun waitForConnectionAsync(
        expectedDeviceName: String? = null,
        timeoutSeconds: Int = 60,
        onStatusUpdate: suspend (String) -> Unit = {}
    ): Boolean {
        Log.d(TAG, "⏳ Waiting for connection to establish (timeout: ${timeoutSeconds}s)...")

        val startTime = System.currentTimeMillis()
        val timeoutMs = timeoutSeconds * 1000L

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            val (isConnected, deviceName) = getConnectionStatus()

            if (isConnected) {
                if (expectedDeviceName == null || deviceName?.contains(expectedDeviceName, ignoreCase = true) == true) {
                    Log.d(TAG, "🎉 Successfully connected to: $deviceName")
                    onStatusUpdate("Connected to $deviceName")
                    return true
                } else {
                    Log.d(TAG, "Connected to $deviceName, but waiting for $expectedDeviceName")
                }
            }

            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            onStatusUpdate("Waiting for connection... (${elapsed}s)")

            // Use kotlinx.coroutines.delay instead of Thread.sleep
            kotlinx.coroutines.delay(1000) // Non-blocking delay
        }

        Log.e(TAG, "❌ Connection timeout after ${timeoutSeconds}s")
        onStatusUpdate("Connection timeout - Please connect manually from Cast Screen settings")
        return false
    }

    /**
     * Disconnect from Miracast using reflection
     * Note: This may also require system permission on some devices
     */
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    fun disconnectMiracast(): Boolean {
        return try {
            Log.d(TAG, "🔌 Attempting to disconnect Miracast")

            val disconnectMethod = displayManager.javaClass.getMethod("disconnectWifiDisplay")
            disconnectMethod.invoke(displayManager)

            Log.d(TAG, "✅ Disconnect request sent")
            true

        } catch (e: SecurityException) {
            Log.w(TAG, "⚠️ Disconnect requires system permission (expected on most devices)")
            // This is expected - disconnectWifiDisplay also requires system permission
            // The connection will be closed when user disconnects from Settings
            true
        } catch (e: NoSuchMethodException) {
            Log.e(TAG, "❌ disconnectWifiDisplay method not available", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to disconnect Miracast", e)
            false
        }
    }

}