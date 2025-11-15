package com.flux_mirror.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.common.util.concurrent.ListenableFuture

class CameraPreviewService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var cameraView: ComposeView? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        Log.d("CameraPreview", "📹 CameraPreviewService onCreate() called")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!checkCameraPermission()) {
            Log.e("CameraPreview", "❌ Camera permission not granted!")
            Log.e("CameraPreview", "Please grant CAMERA permission in app settings")
            stopSelf()
            return
        }

        Log.d("CameraPreview", "✅ Camera permission granted, creating preview...")

        try {
            createCameraPreview()
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
            Log.d("CameraPreview", "✅ Camera preview created successfully")
        } catch (e: Exception) {
            Log.e("CameraPreview", "❌ Failed to create camera preview", e)
            stopSelf()
        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun createCameraPreview() {
        val params = WindowManager.LayoutParams(
            300, // width in pixels
            400, // height in pixels
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 20
            y = 200
        }

        cameraView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@CameraPreviewService)
            setViewTreeViewModelStoreOwner(this@CameraPreviewService)
            setViewTreeSavedStateRegistryOwner(this@CameraPreviewService)

            setContent {
                CameraPreviewUI(
                    context = this@CameraPreviewService,
                    lifecycleOwner = this@CameraPreviewService,
                    windowManager = windowManager,
                    layoutParams = params,
                    view = this,
                    onClose = { stopSelf() }
                )
            }
        }

        windowManager.addView(cameraView, params)
        Log.d("CameraPreview", "📹 Camera preview created!")
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraView?.let {
            windowManager.removeView(it)
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        Log.d("CameraPreview", "Camera preview destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun CameraPreviewUI(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    windowManager: WindowManager,
    layoutParams: WindowManager.LayoutParams,
    view: android.view.View,
    onClose: () -> Unit
) {
    var isFrontCamera by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    val previewView = remember { PreviewView(context) }

    LaunchedEffect(isFrontCamera) {
        startCamera(context, lifecycleOwner, previewView, isFrontCamera)
    }

    Box(
        modifier = Modifier
            .size(300.dp, 400.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX += dragAmount.x
                    offsetY += dragAmount.y

                    layoutParams.x -= offsetX.toInt()
                    layoutParams.y += offsetY.toInt()
                    windowManager.updateViewLayout(view, layoutParams)

                    offsetX = 0f
                    offsetY = 0f
                }
            }
    ) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )

        // Controls overlay
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Flip camera button
            IconButton(
                onClick = { isFrontCamera = !isFrontCamera },
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Flip Camera",
                    tint = Color.White
                )
            }

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.Red.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

private fun startCamera(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    previewView: PreviewView,
    isFrontCamera: Boolean
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

    cameraProviderFuture.addListener({
        try {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val cameraSelector = if (isFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview
            )

            Log.d("CameraPreview", "Camera started: ${if (isFrontCamera) "Front" else "Back"}")
        } catch (e: Exception) {
            Log.e("CameraPreview", "Camera start failed", e)
        }
    }, ContextCompat.getMainExecutor(context))
}