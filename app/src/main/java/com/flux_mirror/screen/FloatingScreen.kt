package com.flux_mirror.screen

import android.content.Context
import android.content.Intent
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
import com.flux_mirror.service.CameraPreviewService
import com.flux_mirror.service.DrawingOverlayService


@Composable
fun FloatingToolsUI(
    windowManager: WindowManager,
    layoutParams: WindowManager.LayoutParams,
    view: View
) {
    var isExpanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    Box(
        modifier = Modifier
            .wrapContentSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        android.util.Log.d("FloatingUI", "Drag started")
                    },
                    onDragEnd = {
                        android.util.Log.d("FloatingUI", "Drag ended")
                    },
                    onDragCancel = {
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
