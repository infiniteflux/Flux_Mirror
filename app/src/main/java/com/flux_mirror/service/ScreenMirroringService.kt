package com.flux_mirror.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Collections
import androidx.core.graphics.createBitmap

class ScreenMirroringService : Service() {

    companion object {
        private const val TAG = "ScreenMirrorService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "screen_mirroring_channel"
        private const val WEB_PORT = 8080

        // This service ONLY handles WEB and LOCAL
        const val ACTION_START_WEB = "com.example.screenmirroring.START_WEB"
        const val ACTION_START_LOCAL = "com.example.screenmirroring.START_LOCAL"
        const val ACTION_STOP = "com.example.screenmirroring.STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_DEVICE_IP = "device_ip"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var webServer: NettyApplicationEngine? = null

    private var screenWidth = 1280
    private var screenHeight = 720
    private var screenDensity = 0

    private val webClients = Collections.synchronizedSet(mutableSetOf<DefaultWebSocketSession>())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var currentFrame: ByteArray? = null
    private var isCapturing = false

    private var imageReaderThread: HandlerThread? = null
    private var imageReaderHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        screenDensity = metrics.densityDpi

        // Start the handler thread for ImageReader
        imageReaderThread = HandlerThread("ImageReaderThread").apply { start() }
        imageReaderHandler = Handler(imageReaderThread!!.looper)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_WEB -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = getResultData(intent)

                if (resultCode == Activity.RESULT_OK && resultData != null) {
                    serviceScope.launch {
                        try {
                            // Call startForeground *before* media projection
                            startForeground(NOTIFICATION_ID, createNotification("Web Cast Active"))
                            delay(500) // Give time for service to start
                            startWebCasting(resultCode, resultData)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting web casting", e)
                            stopAllCasting()
                        }
                    }
                }
            }

            ACTION_START_LOCAL -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val resultData = getResultData(intent)
                val deviceIp = intent.getStringExtra(EXTRA_DEVICE_IP)

                if (resultCode == Activity.RESULT_OK && resultData != null && deviceIp != null) {
                    serviceScope.launch {
                        try {
                            startForeground(
                                NOTIFICATION_ID,
                                createNotification("Casting to Device")
                            )
                            delay(500)
                            startLocalCasting(resultCode, resultData, deviceIp)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error starting local casting", e)
                            stopAllCasting()
                        }
                    }
                }
            }

            ACTION_STOP -> {
                stopAllCasting()
            }
        }

        return START_NOT_STICKY
    }

    private fun getResultData(intent: Intent?): Intent? {
        if (intent == null) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
    }

    private fun startWebCasting(resultCode: Int, resultData: Intent) {
        initializeMediaProjection(resultCode, resultData)
        startWebServer()
        startScreenCapture()
        Log.d(TAG, "Web casting started successfully on port $WEB_PORT")
    }

    private fun startLocalCasting(resultCode: Int, resultData: Intent, deviceIp: String) {
        initializeMediaProjection(resultCode, resultData)
        startScreenCapture()
        serviceScope.launch {
            streamToLocalDevice(deviceIp)
        }
        Log.d(TAG, "Local casting started to $deviceIp")
    }

    private fun initializeMediaProjection(resultCode: Int, resultData: Intent) {
        val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE)
                as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                stopAllCasting()
            }
        }, null)
    }

    private fun startScreenCapture() {
        imageReader = ImageReader.newInstance(
            screenWidth,
            screenHeight,
            PixelFormat.RGBA_8888,
            2
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            if (isCapturing) {
                processImage(reader)
            }
        }, imageReaderHandler)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenMirroringDisplay",
            screenWidth,
            screenHeight,
            screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        isCapturing = true
        Log.d(TAG, "Screen capture started")
    }

    private fun processImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage()
            if (image != null) {
                val bitmap = imageToBitmap(image)
                currentFrame = bitmapToJpeg(bitmap)

                serviceScope.launch {
                    broadcastFrameToClients(currentFrame!!)
                }
            }
        } catch (e: Exception) {
            // Log.e(TAG, "Error processing image", e)
        } finally {
            image?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer: ByteBuffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = createBitmap(image.width + rowPadding / pixelStride, image.height)
        bitmap.copyPixelsFromBuffer(buffer)
        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun bitmapToJpeg(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
        return outputStream.toByteArray()
    }

    private fun startWebServer() {
        serviceScope.launch {
            try {
                webServer = embeddedServer(Netty, port = WEB_PORT) {
                    install(WebSockets)
                    routing {
                        get("/") {
                            call.respondText(getWebViewerHtml(), ContentType.Text.Html)
                        }
                        webSocket("/stream") {
                            webClients.add(this)
                            Log.d(TAG, "Client connected. Total clients: ${webClients.size}")
                            try {
                                for (frame in incoming) { /* Keep alive */
                                }
                            } catch (e: Exception) {
                                // Log.e(TAG, "WebSocket error", e)
                            } finally {
                                webClients.remove(this)
                                Log.d(TAG, "Client disconnected. Total clients: ${webClients.size}")
                            }
                        }
                    }
                }
                Log.d(TAG, "Ktor server starting...")
                webServer?.start(wait = true)
            } catch (e: Exception) {
                if (e is InterruptedException || e.cause is InterruptedException) {
                    Log.d(TAG, "Ktor server stopped.")
                } else {
                    Log.e(TAG, "Ktor server error", e)
                }
            }
        }
    }

    private suspend fun broadcastFrameToClients(frame: ByteArray) {
        val clientsToRemove = mutableListOf<DefaultWebSocketSession>()
        webClients.forEach { client ->
            try {
                client.send(Frame.Binary(true, frame))
            } catch (e: Exception) {
                clientsToRemove.add(client)
            }
        }
        webClients.removeAll(clientsToRemove)
    }

    private suspend fun streamToLocalDevice(deviceIp: String) {
        Log.d(TAG, "Local streaming to $deviceIp would start here (Not implemented)")
    }

    private fun getWebViewerHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Flux_Mirror - Live View</title>
    <style>
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            background: linear-gradient(135deg, #0f172a 0%, #1e293b 100%);
            color: #e2e8f0;
            min-height: 100vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .container {
            max-width: 1400px;
            width: 100%;
        }
        .header {
            text-align: center;
            margin-bottom: 30px;
        }
        .header h1 {
            font-size: 2.5rem;
            font-weight: 700;
            margin-bottom: 10px;
            background: linear-gradient(135deg, #6366f1 0%, #8b5cf6 100%);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }
        .status {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            padding: 8px 16px;
            background: #1e293b;
            border-radius: 20px;
            font-size: 0.9rem;
        }
        .status-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: #10b981;
            animation: pulse 2s infinite;
        }
        @keyframes pulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.5; }
        }
        .video-container {
            position: relative;
            background: #1e293b;
            border-radius: 20px;
            overflow: hidden;
            box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
        }
        #screen {
            display: block;
            max-width: 100%;
            max-height: 80vh;
            width: auto;
            height: auto;
            margin: 0 auto;
        }
        .video-container:fullscreen {
            display: flex;
            align-items: center;
            justify-content: center;
        }
        .video-container:fullscreen #screen {
            max-height: 100vh; 
        }
        .controls {
            display: flex;
            justify-content: center;
            gap: 15px;
            margin-top: 30px;
        }
        .btn {
            padding: 12px 24px;
            border: none;
            border-radius: 12px;
            font-size: 1rem;
            font-weight: 600;
            cursor: pointer;
            transition: all 0.3s;
            display: flex;
            align-items: center;
            gap: 8px;
        }
        .btn-primary {
            background: #6366f1;
            color: white;
        }
        .btn-primary:hover {
            background: #4f46e5;
            transform: translateY(-2px);
        }
        .btn-secondary {
            background: #334155;
            color: #e2e8f0;
        }
        .btn-secondary:hover {
            background: #475569;
        }
        .loading {
            text-align: center;
            padding: 60px 20px;
        }
        .loading-spinner {
            width: 50px;
            height: 50px;
            border: 4px solid #334155;
            border-top-color: #6366f1;
            border-radius: 50%;
            animation: spin 1s linear infinite;
            margin: 0 auto 20px;
        }
        @keyframes spin {
            to { transform: rotate(360deg); }
        }
        .stats {
            display: flex;
            justify-content: center;
            gap: 30px;
            margin-top: 20px;
            font-size: 0.9rem;
            color: #94a3b8;
        }
        .stat-item {
            display: flex;
            align-items: center;
            gap: 8px;
        }
        @media (max-width: 768px) {
            .header h1 {
                font-size: 1.8rem;
            }
            .controls {
                flex-direction: column;
            }
            .btn {
                width: 100%;
                justify-content: center;
            }
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>📱 Flux_Mirror</h1>
            <div class="status">
                <span class="status-dot"></span>
                <span id="status-text">Connecting...</span>
            </div>
        </div>
        
        <div class="video-container">
            <div id="loading" class="loading">
                <div class="loading-spinner"></div>
                <p>Connecting to device...</p>
            </div>
            <img id="screen" style="display: none;" alt="Screen Mirror">
        </div>
        
        <div class="controls">
            <button class="btn btn-primary" onclick="toggleFullscreen()">
                🖥️ Fullscreen
            </button>
            <button class="btn btn-secondary" onclick="takeScreenshot()">
                📸 Screenshot
            </button>
        </div>
        
        <div class="stats">
            <div class="stat-item">
                📊 FPS: <span id="fps">0</span>
            </div>
            <div class="stat-item">
                ⏱️ Latency: <span id="latency">0ms</span>
            </div>
        </div>
    </div>

    <script>
        const screen = document.getElementById('screen');
        const loading = document.getElementById('loading');
        const statusText = document.getElementById('status-text');
        const fpsDisplay = document.getElementById('fps');
        const latencyDisplay = document.getElementById('latency');
        
        let ws;
        let frameCount = 0;
        let lastFrameTime = Date.now();
        let reconnectAttempts = 0;
        const maxReconnectAttempts = 5;
        let currentBlobUrl = null;
        
        function connect() {
            const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
            const wsUrl = `${'$'}{protocol}//${'$'}{window.location.host}/stream`;
            
            ws = new WebSocket(wsUrl);
            ws.binaryType = 'arraybuffer';
            
            ws.onopen = () => {
                console.log('WebSocket connected');
                statusText.textContent = 'Connected';
                reconnectAttempts = 0;
            };
            
            ws.onmessage = (event) => {
                const blob = new Blob([event.data], { type: 'image/jpeg' });
                const url = URL.createObjectURL(blob);
                
                screen.onload = () => {
                    if (currentBlobUrl) {
                        URL.revokeObjectURL(currentBlobUrl);
                    }
                    currentBlobUrl = url;
                    loading.style.display = 'none';
                    screen.style.display = 'block';
                    
                    frameCount++;
                    const now = Date.now();
                    const elapsed = now - lastFrameTime;
                    
                    if (elapsed >= 1000) {
                        fpsDisplay.textContent = frameCount;
                        frameCount = 0;
                        lastFrameTime = now;
                    }
                };
                
                screen.src = url;
            };
            
            ws.onerror = (error) => {
                console.error('WebSocket error:', error);
                statusText.textContent = 'Connection error';
            };
            
            ws.onclose = () => {
                console.log('WebSocket disconnected');
                statusText.textContent = 'Disconnected';
                
                if (reconnectAttempts < maxReconnectAttempts) {
                    reconnectAttempts++;
                    console.log(`Reconnecting... Attempt ${'$'}{reconnectAttempts}`);
                    setTimeout(connect, 2000);
                } else {
                    statusText.textContent = 'Connection lost';
                    loading.innerHTML = '<p>Connection lost. Please refresh the page.</p>';
                }
            };
        }
        
        function toggleFullscreen() {
            if (!document.fullscreenElement) {
                document.querySelector('.video-container').requestFullscreen();
            } else {
                document.exitFullscreen();
            }
        }
        
        function takeScreenshot() {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            const img = document.getElementById('screen');
            
            canvas.width = img.naturalWidth;
            canvas.height = img.naturalHeight;
            
            try {
                ctx.drawImage(img, 0, 0);
            } catch (e) {
                console.error("Error drawing image to canvas:", e);
                return;
            }

            const link = document.createElement('a');
            link.href = canvas.toDataURL('image/jpeg', 0.9);
            link.download = `screenshot_${'$'}{Date.now()}.jpg`; 
            
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        }
        
        // Start connection
        connect();
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun stopAllCasting() {
        try {
            isCapturing = false
            webServer?.stop(1000, 2000)
            webServer = null

            virtualDisplay?.release()
            imageReader?.close()
            mediaProjection?.stop()

            webClients.clear()

            virtualDisplay = null
            imageReader = null
            mediaProjection = null

            imageReaderThread?.quitSafely()
            imageReaderThread = null
            imageReaderHandler = null

            Log.d(TAG, "All casting stopped")
            stopForeground(true)
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping casting", e)
        }
    }

    private fun createNotification(title: String): Notification {
        val stopIntent = Intent(this, ScreenMirroringService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val smallIcon = android.R.drawable.ic_media_play

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("Tap to stop")
            .setSmallIcon(smallIcon)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Mirroring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Screen mirroring service notification"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopAllCasting()
        serviceScope.cancel()
    }
}