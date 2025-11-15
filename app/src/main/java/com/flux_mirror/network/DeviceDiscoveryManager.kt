package com.flux_mirror.network
import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaRouter as SystemMediaRouter
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.net.InetAddress

class DeviceDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "DeviceDiscovery"
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private val systemMediaRouter: SystemMediaRouter by lazy {
        context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as SystemMediaRouter
    }

    private val displayManager: DisplayManager? by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private var systemMediaRouterCallback: SystemMediaRouter.SimpleCallback? = null

    private val p2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var p2pChannel: WifiP2pManager.Channel? = null

    private var multicastLock: WifiManager.MulticastLock? = null

    data class DiscoveredDevice(
        val name: String,
        val type: String = "Unknown",
        val isCastable: Boolean = false,
        val host: InetAddress? = null,
        val port: Int = 0,
        val routeId: String? = null,
        val deviceAddress: String? = null,
        val isWifiDisplay: Boolean = false
    )

    @SuppressLint("MissingPermission")
    fun discoverDevices(): Flow<DiscoveredDevice> = callbackFlow {
        acquireMulticastLock()
        val discoveredDevices = mutableSetOf<String>()

        // 1. Start NSD Discovery
        val nsdJob = launch {
            try {
                discoverNsdDevices().collect { device ->
                    if (discoveredDevices.add(device.name)) {
                        trySend(device)
                        Log.d(TAG, "✅ NSD device: ${device.name} (${device.type})")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "NSD discovery error", e)
            }
        }

        // 2. Initialize P2P Channel
        p2pChannel = p2pManager?.initialize(context, Looper.getMainLooper(), null)

        // 3. Set up MediaRouter callback FIRST (before P2P scan)
        systemMediaRouterCallback = object : SystemMediaRouter.SimpleCallback() {
            override fun onRouteAdded(router: SystemMediaRouter, route: SystemMediaRouter.RouteInfo) {
                handleRouteDetected(route, discoveredDevices) { device ->
                    trySend(device)
                }
            }

            override fun onRouteChanged(router: SystemMediaRouter, route: SystemMediaRouter.RouteInfo) {
                handleRouteDetected(route, discoveredDevices) { device ->
                    trySend(device)
                }
            }

            override fun onRouteRemoved(router: SystemMediaRouter, route: SystemMediaRouter.RouteInfo) {
                val routeName = route.name?.toString() ?: return
                Log.d(TAG, "❌ Route removed: $routeName")
                discoveredDevices.remove(routeName)
            }
        }

        // Register MediaRouter callback with ACTIVE SCAN
        systemMediaRouterCallback?.let { callback ->
            systemMediaRouter.addCallback(
                SystemMediaRouter.ROUTE_TYPE_LIVE_VIDEO,
                callback,
                SystemMediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            Log.d(TAG, "📡 MediaRouter callback registered")
        }

        // 4. Scan existing routes immediately
        launch {
            delay(100) // Small delay to let callback register
            scanExistingRoutes(discoveredDevices) { device ->
                trySend(device)
            }
        }

        // 5. Enable WiFi Display scanning and get hidden devices
        val wifiDisplayDevices = enableWifiDisplayScanning()
        wifiDisplayDevices.forEach { device ->
            if (discoveredDevices.add(device.name)) {
                trySend(device)
                Log.d(TAG, "✅ WiFi Display device from status: ${device.name}")
            }
        }

        // 6. Start P2P discovery to wake up WiFi Direct hardware
        if (p2pManager == null || p2pChannel == null) {
            Log.e(TAG, "❌ Failed to initialize WiFi P2P Manager")
        } else {
            p2pManager?.discoverPeers(p2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "✅ WiFi P2P hardware scan initiated")

                    // Give hardware time to wake up, then scan again
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(TAG, "🔄 Re-scanning for WiFi Display devices...")
                        scanExistingRoutes(discoveredDevices) { device ->
                            trySend(device)
                        }
                    }, 2000)
                }

                override fun onFailure(reason: Int) {
                    val reasonStr = when (reason) {
                        WifiP2pManager.P2P_UNSUPPORTED -> "P2P not supported"
                        WifiP2pManager.BUSY -> "System busy"
                        WifiP2pManager.ERROR -> "Internal error"
                        else -> "Unknown error ($reason)"
                    }
                    Log.e(TAG, "❌ WiFi P2P scan failed: $reasonStr")
                }
            })
        }

        // 7. Log WiFi Display status
        logWifiDisplayStatus()

        awaitClose {
            nsdJob.cancel()
            p2pManager?.stopPeerDiscovery(p2pChannel, null)
            systemMediaRouterCallback?.let { systemMediaRouter.removeCallback(it) }
            releaseMulticastLock()
            Log.d(TAG, "🛑 Discovery stopped")
        }
    }

    private fun handleRouteDetected(
        route: SystemMediaRouter.RouteInfo,
        discoveredDevices: MutableSet<String>,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ) {
        val routeName = route.name?.toString() ?: return
        val routeDescription = route.description?.toString() ?: ""

        // Check if this is a display route (Miracast/WiFi Display)
        val isDisplayRoute = (route.supportedTypes and SystemMediaRouter.ROUTE_TYPE_LIVE_VIDEO != 0)

        Log.d(TAG, "🔍 Route detected: name='$routeName', " +
                "types=${route.supportedTypes}, " +
                "isDisplay=$isDisplayRoute, " +
                "description='$routeDescription', " +
                "status=${route.status}")

        // Filter out non-display routes
        if (!isDisplayRoute) {
            Log.d(TAG, "⏭️ Skipping non-display route: $routeName")
            return
        }

        // Skip system default routes
        val skipNames = listOf("phone", "speaker", "default", "bluetooth")
        if (skipNames.any { routeName.contains(it, ignoreCase = true) }) {
            Log.d(TAG, "⏭️ Skipping system route: $routeName")
            return
        }

        // Add the device
        if (discoveredDevices.add(routeName)) {
            val device = DiscoveredDevice(
                name = routeName,
                type = "Miracast/WiFi Display",
                isCastable = true,
                routeId = routeName,
                isWifiDisplay = true
            )
            onDeviceFound(device)
            Log.d(TAG, "✅ WiFi Display found: $routeName")
        }
    }

    private fun scanExistingRoutes(
        discoveredDevices: MutableSet<String>,
        onDeviceFound: (DiscoveredDevice) -> Unit
    ) {
        try {
            val routeCount = systemMediaRouter.routeCount
            Log.d(TAG, "📊 Scanning $routeCount existing routes...")

            for (i in 0 until routeCount) {
                try {
                    val route = systemMediaRouter.getRouteAt(i)
                    handleRouteDetected(route, discoveredDevices, onDeviceFound)
                } catch (e: Exception) {
                    Log.e(TAG, "Error scanning route $i", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning existing routes", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableWifiDisplayScanning(): List<DiscoveredDevice> {
        val foundDevices = mutableListOf<DiscoveredDevice>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                displayManager?.let { dm ->
                    // Try to enable WiFi Display scanning via reflection
                    scanWifiDisplaysViaReflection(dm)

                    // Get WiFi Display status and extract devices
                    getWifiDisplayStatusViaReflection(dm)

                    // Return the devices we found
                    foundDevices.addAll(getWifiDisplayDevices())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enabling WiFi Display scanning", e)
        }
        return foundDevices
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun scanWifiDisplaysViaReflection(displayManager: DisplayManager) {
        try {
            val method = displayManager.javaClass.getMethod("scanWifiDisplays")
            method.invoke(displayManager)
            Log.d(TAG, "✅ WiFi Display scan triggered via DisplayManager")
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "scanWifiDisplays method not available")
        } catch (e: Exception) {
            Log.w(TAG, "Could not trigger WiFi Display scan: ${e.message}")
        }
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private fun getWifiDisplayStatusViaReflection(displayManager: DisplayManager) {
        try {
            val statusMethod = displayManager.javaClass.getMethod("getWifiDisplayStatus")
            val status = statusMethod.invoke(displayManager)

            if (status != null) {
                // Use reflection to read WifiDisplayStatus fields
                val statusClass = status.javaClass

                try {
                    val scanStateField = statusClass.getMethod("getScanState")
                    val activeDisplayStateField = statusClass.getMethod("getActiveDisplayState")
                    val displaysField = statusClass.getMethod("getDisplays")

                    val scanState = scanStateField.invoke(status) as? Int ?: -1
                    val activeDisplayState = activeDisplayStateField.invoke(status) as? Int ?: -1
                    val displays = displaysField.invoke(status) as? Array<*>

                    Log.d(TAG, "📺 WiFi Display Status: " +
                            "scanState=$scanState, " +
                            "activeDisplayState=$activeDisplayState, " +
                            "displays=${displays?.size ?: 0}")

                    // IMPORTANT: Extract devices from WifiDisplayStatus directly
                    // since MediaRouter is not exposing them
                    extractWifiDisplayDevices(displays)

                } catch (e: Exception) {
                    Log.d(TAG, "📺 WiFi Display Status retrieved (fields not accessible)")
                }
            }
        } catch (e: NoSuchMethodException) {
            Log.w(TAG, "getWifiDisplayStatus method not available")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get WiFi Display status: ${e.message}")
        }
    }

    private fun extractWifiDisplayDevices(displays: Array<*>?) {
        if (displays == null || displays.isEmpty()) {
            Log.d(TAG, "No WiFi Display devices in WifiDisplayStatus")
            return
        }

        Log.d(TAG, "📺 Found ${displays.size} WiFi Display(s) in system status")

        displays.forEach { display ->
            try {
                val displayClass = display?.javaClass ?: return@forEach

                // Get device name
                val getDeviceName = displayClass.getMethod("getDeviceName")
                val deviceName = getDeviceName.invoke(display) as? String ?: "Unknown Display"

                // Get device address (MAC address)
                val getDeviceAddress = displayClass.getMethod("getDeviceAddress")
                val deviceAddress = getDeviceAddress.invoke(display) as? String

                // Check if available/remembered
                val canConnect = displayClass.getMethod("canConnect")
                val isAvailable = canConnect.invoke(display) as? Boolean ?: false

                val isRemembered = try {
                    val remembered = displayClass.getMethod("isRemembered")
                    remembered.invoke(display) as? Boolean ?: false
                } catch (e: Exception) {
                    false
                }

                Log.d(TAG, "📱 WiFi Display Device: " +
                        "name='$deviceName', " +
                        "address='$deviceAddress', " +
                        "canConnect=$isAvailable, " +
                        "isRemembered=$isRemembered")

                // Store this for later use
                if (isAvailable || isRemembered) {
                    wifiDisplayDevices[deviceAddress ?: deviceName] = WifiDisplayInfo(
                        name = deviceName,
                        address = deviceAddress,
                        canConnect = isAvailable,
                        isRemembered = isRemembered
                    )
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error extracting WiFi Display info: ${e.message}")
            }
        }
    }

    // Store discovered WiFi Display devices
    private val wifiDisplayDevices = mutableMapOf<String, WifiDisplayInfo>()

    data class WifiDisplayInfo(
        val name: String,
        val address: String?,
        val canConnect: Boolean,
        val isRemembered: Boolean
    )

    // Public method to get all WiFi Display devices
    fun getWifiDisplayDevices(): List<DiscoveredDevice> {
        return wifiDisplayDevices.values.map { info ->
            DiscoveredDevice(
                name = info.name,
                type = "Miracast/WiFi Display",
                isCastable = info.canConnect,
                deviceAddress = info.address,
                isWifiDisplay = true,
                routeId = info.address ?: info.name
            )
        }
    }

    private fun logWifiDisplayStatus() {
        try {
            // Check WiFi state
            val wifiEnabled = wifiManager.isWifiEnabled
            Log.d(TAG, "📶 WiFi enabled: $wifiEnabled")

            // Check P2P support
            val p2pSupported = context.packageManager.hasSystemFeature(
                "android.hardware.wifi.direct"
            )
            Log.d(TAG, "📡 WiFi Direct supported: $p2pSupported")

            // Log MediaRouter info
            val routeCount = systemMediaRouter.routeCount
            Log.d(TAG, "📊 Total MediaRouter routes: $routeCount")

        } catch (e: Exception) {
            Log.e(TAG, "Error logging WiFi Display status", e)
        }
    }

    private fun discoverNsdDevices(): Flow<DiscoveredDevice> = callbackFlow {
        val services = listOf(
            "_googlecast._tcp." to "Chromecast",
            "_airplay._tcp." to "Apple TV",
            "_raop._tcp." to "AirPlay Audio",
            "_wfd._tcp." to "WiFi Display (mDNS)"
        )

        val discoveryListeners = mutableListOf<NsdManager.DiscoveryListener>()

        for ((serviceType, deviceType) in services) {
            val discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onStartDiscoveryFailed(type: String?, errorCode: Int) {
                    Log.e(TAG, "Discovery start failed for $serviceType: $errorCode")
                    try {
                        nsdManager.stopServiceDiscovery(this)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping failed discovery", e)
                    }
                }

                override fun onStopDiscoveryFailed(type: String?, errorCode: Int) {
                    Log.e(TAG, "Discovery stop failed for $serviceType: $errorCode")
                }

                override fun onDiscoveryStarted(type: String?) {
                    Log.d(TAG, "✅ NSD discovery started for $serviceType")
                }

                override fun onDiscoveryStopped(type: String?) {
                    Log.d(TAG, "🛑 NSD discovery stopped for $serviceType")
                }

                override fun onServiceFound(serviceInfo: NsdServiceInfo?) {
                    serviceInfo?.let { info ->
                        Log.d(TAG, "🔍 NSD service found: ${info.serviceName} (type: $serviceType)")
                        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(resolveInfo: NsdServiceInfo?, errorCode: Int) {
                                Log.e(TAG, "Resolve failed for ${resolveInfo?.serviceName}: $errorCode")
                            }

                            override fun onServiceResolved(resolvedInfo: NsdServiceInfo?) {
                                resolvedInfo?.let { resolved ->
                                    val device = DiscoveredDevice(
                                        name = cleanDeviceName(resolved.serviceName),
                                        host = resolved.host,
                                        port = resolved.port,
                                        type = deviceType,
                                        isCastable = true
                                    )
                                    trySend(device)
                                    Log.d(TAG, "✅ NSD device resolved: ${device.name}")
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(serviceInfo: NsdServiceInfo?) {
                    Log.d(TAG, "❌ NSD service lost: ${serviceInfo?.serviceName}")
                }
            }

            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                discoveryListeners.add(discoveryListener)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting NSD for $serviceType", e)
            }
        }

        awaitClose {
            discoveryListeners.forEach { listener ->
                try {
                    nsdManager.stopServiceDiscovery(listener)
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping NSD", e)
                }
            }
        }
    }

    private fun cleanDeviceName(name: String): String {
        return name.replace(".local", "")
            .replace("._tcp", "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
    }

    private fun acquireMulticastLock() {
        try {
            multicastLock = wifiManager.createMulticastLock("ScreenMirrorDiscovery")
            multicastLock?.setReferenceCounted(true)
            multicastLock?.acquire()
            Log.d(TAG, "✅ Multicast lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire multicast lock", e)
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "✅ Multicast lock released")
            }
            multicastLock = null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release multicast lock", e)
        }
    }

}