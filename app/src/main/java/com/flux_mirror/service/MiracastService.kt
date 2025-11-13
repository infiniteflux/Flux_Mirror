package com.flux_mirror.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.flux_mirror.R
import com.flux_mirror.network.MiracastConnectionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MiracastService : Service() {

    companion object {
        private const val TAG = "MiracastService"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "miracast_channel"

        const val ACTION_START_MIRACAST = "ACTION_START_MIRACAST"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
        const val EXTRA_DEVICE_ADDRESS = "EXTRA_DEVICE_ADDRESS"  // MAC address
        const val EXTRA_DEVICE_NAME = "EXTRA_DEVICE_NAME"        // Display name
    }

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MiracastPresentation? = null
    private var displayManager: DisplayManager? = null
    private var audioRecord: AudioRecord? = null
    private var miracastConnectionManager: MiracastConnectionManager? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectedDeviceName: String? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 MiracastService onCreate")
        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        miracastConnectionManager = MiracastConnectionManager(this)
        createNotificationChannel()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "🚦 onStartCommand received action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_MIRACAST -> {
                Log.d(TAG, "-> ACTION_START_MIRACAST")
                startForegroundWithNotification()
                Log.d(TAG, "Service started in foreground")

                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                }

                val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
                val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)

                Log.d(TAG, "Extras: resultCode=$resultCode, deviceAddress=$deviceAddress, deviceName=$deviceName")

                if (resultCode == Activity.RESULT_OK && resultData != null && deviceAddress != null) {
                    this.connectedDeviceName = deviceName
                    Log.d(TAG, "Result OK. Launching Miracast connection...")
                    serviceScope.launch {
                        try {
                            connectAndStartProjection(resultCode, resultData, deviceAddress, deviceName)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting Miracast projection", e)
                            updateNotification("Connection failed", false)
                            stopCasting()
                        }
                    }
                } else {
                    Log.e(TAG, "Invalid parameters for Miracast service")
                    stopCasting()
                }
            }
            ACTION_STOP -> {
                Log.d(TAG, "-> ACTION_STOP")
                stopCasting()
            }
            else -> {
                Log.w(TAG, "Unknown or null action: ${intent?.action}")
            }
        }
        Log.d(TAG, "onStartCommand finished, returning START_NOT_STICKY")
        return START_NOT_STICKY
    }

    private suspend fun connectAndStartProjection(
        resultCode: Int,
        data: Intent,
        deviceAddress: String,
        deviceName: String?
    ) {
        Log.d(TAG, "🔗 Step 1: Opening Cast Screen settings for manual connection")
        updateNotification("Open Cast Screen settings to connect", false)

        // Open Cast Screen settings for user to manually connect
        // This is required because connectWifiDisplay needs system permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            val opened = miracastConnectionManager?.openCastScreenSettings() ?: false

            if (!opened) {
                Log.e(TAG, "❌ Failed to open Cast Screen settings")
                updateNotification("Please enable Cast Screen manually", false)
                delay(3000)
                stopCasting()
                return
            }

            Log.d(TAG, "⏳ Step 2: Waiting for user to connect via Cast Screen...")
            updateNotification("Connect to '${deviceName ?: "your device"}' in Cast Screen settings", false)

            // Give user time to see the notification and switch to settings
            delay(3000)

            // Wait for connection to establish (user must connect manually)
            // This runs on IO dispatcher so it won't block the main thread
            val success = withContext(Dispatchers.IO) {
                miracastConnectionManager?.waitForConnectionAsync(
                    expectedDeviceName = deviceName,
                    timeoutSeconds = 60,
                    onStatusUpdate = { status ->
                        Log.d(TAG, "Connection status: $status")
                        // Update notification on main thread
                        withContext(Dispatchers.Main) {
                            updateNotification(status, false)
                        }
                    }
                ) ?: false
            }

            if (!success) {
                Log.e(TAG, "❌ Connection timeout - user did not connect")
                updateNotification("Connection timeout - Please try again", false)
                delay(3000)
                stopCasting()
                return
            }

            Log.d(TAG, "✅ Step 3: Miracast connected! Starting media projection...")
            delay(1000) // Give a moment for the display to stabilize

            // Now start the media projection
            startMiracastProjection(resultCode, data)

        } else {
            Log.e(TAG, "❌ Miracast not supported on this Android version")
            stopCasting()
        }
    }

    private fun startMiracastProjection(resultCode: Int, data: Intent) {
        Log.d(TAG, "📹 Starting MediaProjection...")
        updateNotification("Starting screen capture...", false)

        mediaProjection = mediaProjectionManager?.getMediaProjection(resultCode, data)
        if (mediaProjection == null) {
            Log.e(TAG, "❌ MediaProjection could not be acquired")
            updateNotification("Screen capture failed", false)
            stopCasting()
            return
        }

        Log.d(TAG, "✅ Got MediaProjection")
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped callback")
                stopCasting()
            }
        }, null)

        // Find the Miracast presentation display
        serviceScope.launch(Dispatchers.IO) {
            Log.d(TAG, "🔍 Looking for presentation display...")
            var display: Display? = null

            // Wait for the presentation display to become available
            for (i in 1..15) {
                val displays = displayManager?.displays

                displays?.forEach { d ->
                    Log.d(TAG, "Display found: ${d.name}, displayId=${d.displayId}, flags=${d.flags}")

                    // Look for the presentation display (FLAG_PRESENTATION = 8)
                    if ((d.flags and Display.FLAG_PRESENTATION) != 0) {
                        display = d
                        Log.d(TAG, "✅ Found presentation display: ${d.name}")
                    }
                }

                if (display != null) {
                    Log.d(TAG, "✅ Presentation display ready after ${i}s")
                    break
                }

                Log.d(TAG, "Waiting for presentation display... ($i/15)")
                delay(1000)
            }

            if (display == null) {
                Log.e(TAG, "❌ Timeout: No presentation display found")
                Log.e(TAG, "Available displays:")
                displayManager?.displays?.forEach { d ->
                    Log.e(TAG, "  - ${d.name} (id=${d.displayId}, flags=${d.flags})")
                }
                updateNotification("Display not found", false)
                delay(2000)
                stopCasting()
                return@launch
            }

            Log.d(TAG, "📺 Using display: ${display.name}")

            // Create and show presentation on the Miracast display
            launch(Dispatchers.Main) {
                Log.d(TAG, "Creating Presentation for Miracast display...")
                updateNotification("Setting up display...", false)

                presentation = MiracastPresentation(this@MiracastService, display)
                presentation?.show()

                presentation?.surfaceReadyCallback = { surface ->
                    if (surface != null) {
                        Log.d(TAG, "✅ Surface ready, creating VirtualDisplay")
                        createVirtualDisplay(surface)
                        startAudioCapture()
                        updateNotification("Casting to ${connectedDeviceName ?: "device"}", true)
                        broadcastCastingState(true) // Notify UI that casting started
                    } else {
                        Log.e(TAG, "❌ Surface was null")
                        updateNotification("Display setup failed", false)
                        stopCasting()
                    }
                }
            }
        }
    }

    private fun createVirtualDisplay(surface: Surface) {
        Log.d(TAG, "Creating VirtualDisplay...")
        val metrics = DisplayMetrics()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.let {
                metrics.widthPixels = it.width()
                metrics.heightPixels = it.height()
            }
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
        }

        Log.d(TAG, "Screen metrics: ${metrics.widthPixels}x${metrics.heightPixels} @ ${metrics.densityDpi}dpi")

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenMirroring-Miracast",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
            surface,
            null,
            null
        )
        Log.d(TAG, "✅ VirtualDisplay created")
    }

    @SuppressLint("MissingPermission")
    private fun startAudioCapture() {
        Log.d(TAG, "🔊 Attempting to start audio capture...")
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "RECORD_AUDIO permission not granted")
            return
        }

        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        Log.d(TAG, "Audio params: $sampleRate Hz, buffer: $bufferSize")

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            Log.d(TAG, "AudioRecord created")

            audioRecord?.startRecording()
            Log.d(TAG, "✅ Audio recording started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio recording", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopCasting() {
        Log.d(TAG, "🛑 stopCasting called")

        // Cancel the coroutine scope
        serviceScope.cancel()
        Log.d(TAG, "ServiceScope cancelled")

        // Disconnect Miracast
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            miracastConnectionManager?.disconnectMiracast()
        }

        // Stop presentation
        CoroutineScope(Dispatchers.Main).launch {
            Log.d(TAG, "Dismissing presentation...")
            presentation?.dismiss()
            presentation = null
        }

        // Stop media projection
        mediaProjection?.stop()
        Log.d(TAG, "MediaProjection stopped")

        virtualDisplay?.release()
        Log.d(TAG, "VirtualDisplay released")

        audioRecord?.stop()
        Log.d(TAG, "AudioRecord stopped")
        audioRecord?.release()
        Log.d(TAG, "AudioRecord released")

        mediaProjection = null
        virtualDisplay = null
        audioRecord = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        Log.d(TAG, "Service removed from foreground")

        // Broadcast that casting stopped
        broadcastCastingState(false)

        stopSelf()
        Log.d(TAG, "stopSelf() called")
        Log.d(TAG, "All casting stopped.")
    }

    private fun broadcastCastingState(isCasting: Boolean) {
        val intent = Intent(if (isCasting) "com.flux_mirror.CASTING_STARTED" else "com.flux_mirror.CASTING_STOPPED")
        sendBroadcast(intent)
        Log.d(TAG, "Broadcast sent: ${if (isCasting) "STARTED" else "STOPPED"}")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💀 MiracastService onDestroy")
        stopCasting()
    }

    @SuppressLint("MissingPermission")
    private fun startForegroundWithNotification() {
        Log.d(TAG, "🔔 startForegroundWithNotification called")
        val notification = createNotification("Initializing Miracast...", false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        Log.d(TAG, "startForeground() complete")
    }

    private fun updateNotification(message: String, isActive: Boolean) {
        val notification = createNotification(message, isActive)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(message: String, isActive: Boolean): Notification {
        val stopIntent = Intent(this, MiracastService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Flux Mirror")
            .setContentText(message)
            .setSmallIcon(if (isActive) R.drawable.ic_launcher_foreground else R.drawable.ic_launcher_foreground)
            .addAction(R.drawable.ic_launcher_foreground, "Stop", stopPendingIntent)
            .setOngoing(isActive)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Creating notification channel $CHANNEL_ID")
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Miracast Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(TAG, "onBind called (returning null)")
        return null
    }

    private class MiracastPresentation(
        context: Context,
        display: Display
    ) : Presentation(context, display) {

        var surfaceReadyCallback: ((Surface?) -> Unit)? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            Log.d(TAG, "Presentation: onCreate for display: ${display.name}")
            val surfaceView = android.view.SurfaceView(context)
            setContentView(surfaceView)

            surfaceView.holder.addCallback(object : android.view.SurfaceHolder.Callback {
                override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                    Log.d(TAG, "Presentation: Surface created")
                    surfaceReadyCallback?.invoke(holder.surface)
                }
                override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "Presentation: surfaceChanged format=$format, w=$width, h=$height")
                }
                override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                    Log.d(TAG, "Presentation: Surface destroyed")
                    surfaceReadyCallback?.invoke(null)
                }
            })
        }
    }
}