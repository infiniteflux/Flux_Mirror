package com.flux_mirror.screen

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SimpleTestScreen(
    onStartFloating: () -> Unit,
    onStopFloating: () -> Unit
) {
    var clickCount by remember { mutableIntStateOf(0) }
    val TAG = "SimpleTestScreen"

    Log.d(TAG, "⚡ Screen rendering, clickCount: $clickCount")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // Click counter test
            Text(
                text = "Clicks: $clickCount",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Simple test button
            Button(
                onClick = {
                    clickCount++
                    Log.d(TAG, "✅ TEST BUTTON CLICKED! Count: $clickCount")
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8B5CF6)
                )
            ) {
                Text("CLICK ME (Test)", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 16.dp))

            // Start Floating button
            Button(
                onClick = {
                    clickCount++
                    Log.d(TAG, "🚀 START FLOATING CLICKED! Count: $clickCount")
                    onStartFloating()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF10B981)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Floating", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Stop Floating button
            Button(
                onClick = {
                    clickCount++
                    Log.d(TAG, "🛑 STOP FLOATING CLICKED! Count: $clickCount")
                    onStopFloating()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Floating", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Instructions
            Text(
                text = "If you see this and counter increases,\nclicks ARE working!",
                fontSize = 16.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center
            )
        }
    }
}