package com.flux_mirror.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

class DrawingOverlayService : Service(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private lateinit var windowManager: WindowManager
    private var drawingView: ComposeView? = null

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

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createDrawingOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDrawingOverlay() {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        drawingView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@DrawingOverlayService)
            setViewTreeViewModelStoreOwner(this@DrawingOverlayService)
            setViewTreeSavedStateRegistryOwner(this@DrawingOverlayService)

            setContent {
                DrawingOverlayUI(
                    onClose = { stopSelf() }
                )
            }
        }

        windowManager.addView(drawingView, params)
        Log.d("DrawingOverlay", "✏️ Drawing overlay created!")
    }

    override fun onDestroy() {
        super.onDestroy()
        drawingView?.let {
            windowManager.removeView(it)
        }
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        store.clear()
        Log.d("DrawingOverlay", "Drawing overlay destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

data class PathData(
    val path: Path,
    val color: Color,
    val width: Float
)

@Composable
fun DrawingOverlayUI(onClose: () -> Unit) {
    var paths by remember { mutableStateOf(listOf<PathData>()) }
    var currentPath by remember { mutableStateOf(Path()) }
    var currentColor by remember { mutableStateOf(Color.Red) }
    var strokeWidth by remember { mutableStateOf(10f) }
    var isDrawing by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Drawing canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDrawing = true
                            currentPath = Path().apply {
                                moveTo(offset.x, offset.y)
                            }
                        },
                        onDrag = { change, _ ->
                            if (isDrawing) {
                                currentPath.lineTo(change.position.x, change.position.y)
                                paths = paths.dropLast(if (paths.isNotEmpty() && isDrawing) 1 else 0) +
                                        PathData(Path().apply { addPath(currentPath) }, currentColor, strokeWidth)
                            }
                        },
                        onDragEnd = {
                            isDrawing = false
                        }
                    )
                }
        ) {
            // Draw all saved paths
            paths.forEach { pathData ->
                drawPath(
                    path = pathData.path,
                    color = pathData.color,
                    style = Stroke(
                        width = pathData.width,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        // Control panel
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                .padding(8.dp)
        ) {
            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Red.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Color palette
            listOf(
                Color.Red,
                Color.Blue,
                Color.Green,
                Color.Yellow,
                Color.White,
                Color.Black
            ).forEach { color ->
                IconButton(
                    onClick = {
                        currentColor = color
                        Log.d("DrawingOverlay", "Color changed to $color")
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                color = color,
                                shape = CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Clear button
            IconButton(
                onClick = {
                    paths = emptyList()
                    Log.d("DrawingOverlay", "Canvas cleared")
                },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Gray.copy(alpha = 0.8f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear",
                    tint = Color.White
                )
            }
        }
    }
}