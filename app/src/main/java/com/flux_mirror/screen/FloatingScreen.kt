package com.flux_mirror.screen


import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.flux_mirror.permission.PermissionsHelper
import com.flux_mirror.service.CameraPreviewService
import com.flux_mirror.service.DrawingOverlayService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



@Composable
fun FloatingToolsUI(
    windowManager: WindowManager,
    layoutParams: WindowManager.LayoutParams,
    view: View
) {
    var isExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        android.util.Log.d("FloatingUI", "Drag started")
                    },
                    onDragEnd = {
                        isDragging = false
                        android.util.Log.d("FloatingUI", "Drag ended")
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y

                        layoutParams.x -= offsetX.toInt()
                        layoutParams.y += offsetY.toInt()
                        windowManager.updateViewLayout(view, layoutParams)

                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
    ) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) togetherWith
                        fadeOut(animationSpec = tween(300))
            },
            label = "floating_tools_animation"
        ) { expanded ->
            if (expanded) {
                ExpandedFloatingTools(
                    context = LocalContext.current,
                    onCollapse = { isExpanded = false }
                )
            } else {
                CollapsedFloatingButton(
                    onExpand = { isExpanded = true }
                )
            }
        }
    }
}

@Composable
fun CollapsedFloatingButton(onExpand: () -> Unit) {
    android.util.Log.d("FloatingUI", "Rendering CollapsedFloatingButton")

    IconButton(
        onClick = {
            android.util.Log.d("FloatingUI", "🎯 Collapsed button CLICKED! Expanding...")
            onExpand()
        },
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .background(Color(0xFFFF6B35), CircleShape)
    ) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = "Expand",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun ExpandedFloatingTools(context: Context, onCollapse: () -> Unit) {
    android.util.Log.d("FloatingUI", "Rendering ExpandedFloatingTools")

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .wrapContentSize()
            .padding(8.dp)
    ) {
        // Header with toggle
        Row(
            modifier = Modifier
                .width(200.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF1E3A5F))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Floating tools",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )

            Switch(
                checked = true,
                onCheckedChange = {
                    android.util.Log.d("FloatingUI", "🎯 Toggle switched! Collapsing...")
                    onCollapse()
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFFFF6B35),
                    checkedTrackColor = Color(0xFF8B5A3C)
                )
            )
        }

        // Tool icons in header
        Row(
            modifier = Modifier
                .width(200.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(
                onClick = {
                    android.util.Log.d("FloatingUI", "🖊️ PEN clicked! Starting drawing...")
                    context.startService(Intent(context, DrawingOverlayService::class.java))
                },
                modifier = Modifier.size(40.dp)
            ) {
                ToolIcon(
                    icon = Icons.Default.Edit,
                    color = Color(0xFF6B7C93)
                )
            }

            IconButton(
                onClick = {
                    android.util.Log.d("FloatingUI", "📹 CAMERA clicked! Opening camera preview...")
                    context.startService(Intent(context, CameraPreviewService::class.java))
                },
                modifier = Modifier.size(40.dp)
            ) {
                ToolIcon(
                    icon = Icons.Default.Videocam,
                    color = Color(0xFF6B7C93)
                )
            }

            IconButton(
                onClick = {
                    android.util.Log.d("FloatingUI", "📸 SCREENSHOT clicked!")
                    // Screenshot functionality would go here
                },
                modifier = Modifier.size(40.dp)
            ) {
                ToolIcon(
                    icon = Icons.Default.PhotoCamera,
                    color = Color(0xFF4A90E2)
                )
            }
        }

        // Floating action buttons
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(end = 8.dp)
        ) {
            FloatingActionIcon(
                icon = Icons.Default.Edit,
                backgroundColor = Color.Black,
                onClick = {
                    android.util.Log.d("FloatingUI", "✏️ Edit FAB clicked! Starting drawing service...")
                    try {
                        context.startService(Intent(context, DrawingOverlayService::class.java))
                        android.util.Log.d("FloatingUI", "✅ Drawing service started")
                    } catch (e: Exception) {
                        android.util.Log.e("FloatingUI", "❌ Failed to start drawing service", e)
                    }
                }
            )
            FloatingActionIcon(
                icon = Icons.Default.Videocam,
                backgroundColor = Color.Black,
                onClick = {
                    android.util.Log.d("FloatingUI", "🎥 Video FAB clicked! Starting camera service...")
                    try {
                        context.startService(Intent(context, CameraPreviewService::class.java))
                        android.util.Log.d("FloatingUI", "✅ Camera service started")
                    } catch (e: Exception) {
                        android.util.Log.e("FloatingUI", "❌ Failed to start camera service", e)
                    }
                }
            )
            FloatingActionIcon(
                icon = Icons.Default.Home,
                backgroundColor = Color.Black,
                onClick = {
                    android.util.Log.d("FloatingUI", "🏠 Home FAB clicked!")
                    // Could open main app or minimize
                }
            )

            // Collapse button
            IconButton(
                onClick = {
                    android.util.Log.d("FloatingUI", "⬇️ Collapse button clicked!")
                    onCollapse()
                },
                modifier = Modifier
                    .size(56.dp)
                    .shadow(8.dp, CircleShape)
                    .background(Color.Black, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun ToolIcon(
    icon: ImageVector,
    color: Color
) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
fun FloatingActionIcon(
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit = {}
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(56.dp)
            .shadow(8.dp, CircleShape)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }
}


//@Composable
//fun FloatingToolsControlScreen(
//    onStartFloating: () -> Unit,
//    onStopFloating: () -> Unit
//) {
//    val context = LocalContext.current
//    var isFloatingActive by remember { mutableStateOf(false) }
//    val scope = rememberCoroutineScope()
//
//    // Check overlay permission status on composition
//    DisposableEffect(Unit) {
//        val hasPermission = PermissionsHelper.hasOverlayPermission(context)
//        isFloatingActive = hasPermission
//        Log.d("FloatingToolsControl", "Initial check - Permission: $hasPermission, State: $isFloatingActive")
//        onDispose { }
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxSize()
//            .padding(20.dp),
//        horizontalAlignment = Alignment.CenterHorizontally
//    ) {
//        Spacer(modifier = Modifier.height(20.dp))
//
//        // Header Card
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = Color(0xFF1E293B)
//            ),
//            shape = RoundedCornerShape(16.dp)
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(24.dp),
//                horizontalAlignment = Alignment.CenterHorizontally
//            ) {
//                Icon(
//                    imageVector = Icons.Default.Cast,
//                    contentDescription = null,
//                    modifier = Modifier.size(64.dp),
//                    tint = Color(0xFF6366F1)
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                Text(
//                    text = "Floating Tools",
//                    fontSize = 24.sp,
//                    fontWeight = FontWeight.Bold,
//                    color = Color.White
//                )
//
//                Spacer(modifier = Modifier.height(8.dp))
//
//                Text(
//                    text = "Control your screen mirroring tools from anywhere",
//                    fontSize = 14.sp,
//                    color = Color(0xFF94A3B8),
//                    textAlign = TextAlign.Center
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        // Status Card
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = if (isFloatingActive)
//                    Color(0xFF10B981).copy(alpha = 0.1f)
//                else
//                    Color(0xFFEF4444).copy(alpha = 0.1f)
//            ),
//            shape = RoundedCornerShape(16.dp)
//        ) {
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(20.dp),
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Icon(
//                    imageVector = if (isFloatingActive)
//                        Icons.Default.CheckCircle
//                    else
//                        Icons.Default.Info,
//                    contentDescription = null,
//                    tint = if (isFloatingActive)
//                        Color(0xFF10B981)
//                    else
//                        Color(0xFFEF4444),
//                    modifier = Modifier.size(32.dp)
//                )
//
//                Spacer(modifier = Modifier.width(16.dp))
//
//                Column {
//                    Text(
//                        text = "Status",
//                        fontSize = 12.sp,
//                        color = Color(0xFF94A3B8)
//                    )
//                    Text(
//                        text = if (isFloatingActive) "Active" else "Inactive",
//                        fontSize = 18.sp,
//                        fontWeight = FontWeight.SemiBold,
//                        color = if (isFloatingActive)
//                            Color(0xFF10B981)
//                        else
//                            Color(0xFFEF4444)
//                    )
//                }
//            }
//        }
//
//        Spacer(modifier = Modifier.height(32.dp))
//
//        // Features List
//        Card(
//            modifier = Modifier.fillMaxWidth(),
//            colors = CardDefaults.cardColors(
//                containerColor = Color(0xFF1E293B)
//            ),
//            shape = RoundedCornerShape(16.dp)
//        ) {
//            Column(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(20.dp)
//            ) {
//                Text(
//                    text = "Features",
//                    fontSize = 18.sp,
//                    fontWeight = FontWeight.Bold,
//                    color = Color.White
//                )
//
//                Spacer(modifier = Modifier.height(16.dp))
//
//                FeatureItem(
//                    icon = Icons.Default.OpenInFull,
//                    title = "Draggable",
//                    description = "Move tools anywhere on screen"
//                )
//
//                FeatureItem(
//                    icon = Icons.Default.Visibility,
//                    title = "Always Visible",
//                    description = "Works during screen casting"
//                )
//
//                FeatureItem(
//                    icon = Icons.Default.TouchApp,
//                    title = "Quick Access",
//                    description = "Access tools with one tap"
//                )
//            }
//        }
//
//        Spacer(modifier = Modifier.weight(1f))
//
//        // Control Buttons
//        Button(
//            onClick = {
//                Log.d("FloatingToolsControl", "========================================")
//                Log.d("FloatingToolsControl", "🔥 BUTTON CLICKED!")
//                Log.d("FloatingToolsControl", "Current state: isFloatingActive=$isFloatingActive")
//                Log.d("FloatingToolsControl", "Has permission: ${PermissionsHelper.hasOverlayPermission(context)}")
//
//                if (isFloatingActive) {
//                    Log.d("FloatingToolsControl", "Action: STOPPING service")
//                    onStopFloating()
//                    isFloatingActive = false
//                } else {
//                    Log.d("FloatingToolsControl", "Action: STARTING service")
//                    onStartFloating()
//
//                    // Update state after permission check
//                    scope.launch {
//                        delay(1500)
//                        val newState = PermissionsHelper.hasOverlayPermission(context)
//                        isFloatingActive = newState
//                        Log.d("FloatingToolsControl", "State updated after delay: $newState")
//                    }
//                }
//                Log.d("FloatingToolsControl", "========================================")
//            },
//            modifier = Modifier
//                .fillMaxWidth()
//                .height(56.dp),
//            colors = ButtonDefaults.buttonColors(
//                containerColor = if (isFloatingActive)
//                    Color(0xFFEF4444)
//                else
//                    Color(0xFF6366F1)
//            ),
//            shape = RoundedCornerShape(12.dp)
//        ) {
//            Icon(
//                imageVector = if (isFloatingActive)
//                    Icons.Default.Close
//                else
//                    Icons.Default.PlayArrow,
//                contentDescription = null,
//                modifier = Modifier.size(24.dp)
//            )
//            Spacer(modifier = Modifier.width(8.dp))
//            Text(
//                text = if (isFloatingActive)
//                    "Stop Floating Tools"
//                else
//                    "Start Floating Tools",
//                fontSize = 16.sp,
//                fontWeight = FontWeight.SemiBold
//            )
//        }
//
//        Spacer(modifier = Modifier.height(20.dp))
//    }
//}

//@Composable
//fun FeatureItem(
//    icon: androidx.compose.ui.graphics.vector.ImageVector,
//    title: String,
//    description: String
//) {
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 12.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        Box(
//            modifier = Modifier
//                .size(48.dp)
//                .background(
//                    color = Color(0xFF6366F1).copy(alpha = 0.2f),
//                    shape = RoundedCornerShape(12.dp)
//                ),
//            contentAlignment = Alignment.Center
//        ) {
//            Icon(
//                imageVector = icon,
//                contentDescription = null,
//                tint = Color(0xFF6366F1),
//                modifier = Modifier.size(24.dp)
//            )
//        }
//
//        Spacer(modifier = Modifier.width(16.dp))
//
//        Column {
//            Text(
//                text = title,
//                fontSize = 16.sp,
//                fontWeight = FontWeight.SemiBold,
//                color = Color.White
//            )
//            Text(
//                text = description,
//                fontSize = 13.sp,
//                color = Color(0xFF94A3B8)
//            )
//        }
//    }
//}