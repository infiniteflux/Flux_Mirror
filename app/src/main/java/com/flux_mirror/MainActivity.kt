package com.flux_mirror

import android.content.Context
import android.provider.Settings
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flux_mirror.permission.PermissionsHelper
import com.flux_mirror.screen.SimpleTestScreen
import com.flux_mirror.screen.WebCastScreen
import com.flux_mirror.service.FloatingToolsService
import com.flux_mirror.service.MiracastService
import com.flux_mirror.service.ScreenMirroringService
import com.flux_mirror.ui.theme.ScreenMirroringTheme
import com.flux_mirror.viewmodel.DevicesViewModel

class MainActivity : ComponentActivity() {

    // Launcher for overlay permission
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (PermissionsHelper.hasOverlayPermission(this)) {
            startFloatingService()
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Perform permission audit on app start
        PermissionsHelper.performPermissionAudit(this)

        enableEdgeToEdge()
        setContent {
            ScreenMirroringTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScreenMirroringApp(
                        onStartFloating = { checkAndRequestOverlayPermission() },
                        onStopFloating = { stopFloatingService() }
                    )
                }
            }
        }
    }

    private fun checkAndRequestOverlayPermission() {
        if (!PermissionsHelper.hasOverlayPermission(this)) {
            // Request overlay permission
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            // Permission already granted
            startFloatingService()
        }
    }

    private fun startFloatingService() {
        val intent = Intent(this, FloatingToolsService::class.java)
        startService(intent)
    }

    private fun stopFloatingService() {
        val intent = Intent(this, FloatingToolsService::class.java)
        stopService(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Optional: Stop service when activity is destroyed
        // Uncomment if you want floating tools to stop when app closes
        // stopFloatingService()
    }
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenMirroringApp(
    onStartFloating: () -> Unit,
    onStopFloating: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Cast", "Web Cast", "Floating Tools")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Screen Mirroring",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E293B),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E293B)
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.Cast
                                    1 -> Icons.Default.Language
                                    else -> Icons.Default.Build
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF6366F1),
                            selectedTextColor = Color(0xFF6366F1),
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8),
                            indicatorColor = Color(0xFF6366F1).copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> LocalCastScreen()
                1 -> WebCastScreen()
                2 -> SimpleTestScreen(
                    onStartFloating = onStartFloating,
                    onStopFloating = onStopFloating
                )

//                2 -> FloatingToolsControlScreen(
//                    onStartFloating = onStartFloating,
//                    onStopFloating = onStopFloating
//                )
            }
        }
    }
}


@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
fun LocalCastScreen(
    viewModel: DevicesViewModel = viewModel()
) {
    val context = LocalContext.current
    var isCasting by remember { mutableStateOf(false) }

    // Get the selected device from ViewModel
    val selectedDevice = viewModel.selectedDevice

    var showDeviceDialog by remember { mutableStateOf(false) }

    // Permission logic
    var hasPermissions by remember {
        mutableStateOf(PermissionsHelper.hasAllPermissions(context))
    }

    val permissionsToRequest = remember {
        PermissionsHelper.getMissingPermissions(context).toTypedArray()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasPermissions = permissionsMap.values.all { it }
    }

    val nearbyDevices by viewModel.devices.collectAsStateWithLifecycle()

    val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val device = selectedDevice

        if (result.resultCode == Activity.RESULT_OK && result.data != null && device != null) {

            if (device.isWifiDisplay && device.deviceAddress != null) {
                // This is a Miracast device
                val serviceIntent = Intent(context, MiracastService::class.java).apply {
                    action = MiracastService.ACTION_START_MIRACAST
                    putExtra(MiracastService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(MiracastService.EXTRA_RESULT_DATA, result.data)
                    putExtra(MiracastService.EXTRA_DEVICE_ADDRESS, device.deviceAddress)
                    putExtra(MiracastService.EXTRA_DEVICE_NAME, device.name)
                }

                context.startForegroundService(serviceIntent)
                isCasting = true
                Toast.makeText(
                    context,
                    "Opening Cast Screen - Connect to '${device.name}'",
                    Toast.LENGTH_LONG
                ).show()

            } else {
                // This is a Web/Chromecast device
                val serviceIntent = Intent(context, ScreenMirroringService::class.java).apply {
                    action = ScreenMirroringService.ACTION_START_WEB
                    putExtra(ScreenMirroringService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenMirroringService.EXTRA_RESULT_DATA, result.data)
                }

                context.startForegroundService(serviceIntent)
                isCasting = true
            }

        } else {
            Toast.makeText(context, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
            isCasting = false
            viewModel.clearSelection()
        }
    }

    // Listen for service state changes via BroadcastReceiver
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    "com.flux_mirror.CASTING_STOPPED" -> {
                        isCasting = false
                        viewModel.clearSelection()
                    }
                    "com.flux_mirror.CASTING_STARTED" -> {
                        isCasting = true
                    }
                }
            }
        }

        val filter = android.content.IntentFilter().apply {
            addAction("com.flux_mirror.CASTING_STOPPED")
            addAction("com.flux_mirror.CASTING_STARTED")
        }

        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver already unregistered
            }
        }
    }

    // Listen to the ViewModel's Miracast flow
    LaunchedEffect(Unit) {
        viewModel.startMiracastFlow.collect { device ->
            // ViewModel told us to start Miracast for this device
            // Launch the MediaProjection permission request
            mediaProjectionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    // Start discovery only when permissions are granted
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            viewModel.startDiscovery()
        }
    }

    // Function to start web casting (non-Miracast)
    fun startWebCasting() {
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        mediaProjectionLauncher.launch(intent)
    }

    fun stopLocalCasting() {
        // Stop both services (only the active one will actually stop)
        context.startService(Intent(context, ScreenMirroringService::class.java).apply {
            action = ScreenMirroringService.ACTION_STOP
        })
        context.startService(Intent(context, MiracastService::class.java).apply {
            action = MiracastService.ACTION_STOP
        })
        isCasting = false
        viewModel.clearSelection()
    }

    if (showDeviceDialog) {
        DeviceSelectionDialog(
            devices = nearbyDevices,
            onDeviceSelected = { device ->
                showDeviceDialog = false

                if (device.isWifiDisplay && device.deviceAddress != null) {
                    // This is a Miracast device
                    viewModel.onDeviceClicked(device)
                } else {
                    // This is a Web/Chromecast device
                    viewModel.selectedDevice = device
                    startWebCasting()
                }
            },
            onDismiss = { showDeviceDialog = false }
        )
    }

    // UI
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = if (isCasting) Icons.Default.StopCircle else Icons.Default.Cast,
                    contentDescription = "Status",
                    modifier = Modifier.size(80.dp),
                    tint = if (isCasting) Color(0xFF10B981) else Color(0xFF6366F1)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isCasting) "Casting to Device" else "Ready to Cast",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (isCasting)
                        "Connected to ${selectedDevice?.name ?: "Device"}"
                    else if (nearbyDevices.isEmpty() && hasPermissions)
                        "Scanning for devices..."
                    else if (!hasPermissions)
                        "Permission required to find devices"
                    else
                        "${nearbyDevices.size} device(s) found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        Button(
            onClick = {
                if (!hasPermissions) {
                    if (permissionsToRequest.isNotEmpty()) {
                        permissionLauncher.launch(permissionsToRequest)
                    } else {
                        Toast.makeText(context, "Please grant permissions in settings", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = Uri.fromParts("package", context.packageName, null)
                        context.startActivity(intent)
                    }
                } else if (!isCasting) {
                    if (nearbyDevices.isEmpty()) {
                        Toast.makeText(
                            context,
                            "Scanning for devices... Please wait",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        showDeviceDialog = true
                    }
                } else {
                    stopLocalCasting()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isCasting) Color(0xFFEF4444) else Color(0xFF6366F1)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Icon(
                imageVector = if (isCasting) Icons.Default.Stop else Icons.Default.Cast,
                contentDescription = "Action"
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isCasting) "Stop Casting" else if (!hasPermissions) "Grant Permission" else "Cast to Device",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (nearbyDevices.isEmpty() && hasPermissions) {
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "📡 Scanning local network...",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
            )
        }

        // Show device type indicator
        if (nearbyDevices.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Found ${nearbyDevices.count { it.isWifiDisplay }} Miracast device(s) and ${nearbyDevices.count { !it.isWifiDisplay }} network device(s)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun DeviceSelectionDialog(
    devices: List<com.flux_mirror.viewmodel.Device>,
    onDeviceSelected: (com.flux_mirror.viewmodel.Device) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Device") },
        text = {
            Column {
                devices.forEach { device ->
                    TextButton(
                        onClick = { onDeviceSelected(device) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(device.name)
                                if (device.isWifiDisplay) {
                                    Text("📺", color = Color(0xFF6366F1))
                                }
                            }
                            Text(
                                text = device.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

