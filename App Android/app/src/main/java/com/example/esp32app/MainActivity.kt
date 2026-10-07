package com.example.esp32app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.esp32app.ui.theme.*

class MainActivity : ComponentActivity() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // Compose States
    private val isScanningState = mutableStateOf(false)
    private val bluetoothEnabledState = mutableStateOf(true)
    private val discoveredDevicesState = mutableStateListOf<DiscoveredDevice>()

    private val handler = Handler(Looper.getMainLooper())
    private val SCAN_PERIOD: Long = 10000

    data class DiscoveredDevice(val name: String, val address: String, val rssi: Int)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Permissões concedidas!", Toast.LENGTH_SHORT).show()
            startBleScan()
        } else {
            Toast.makeText(this, "O Bluetooth precisa de permissões para listar o ESP32", Toast.LENGTH_LONG).show()
        }
    }

    private val leScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                val address = device.address ?: return
                val name = device.name ?: "Dispositivo Desconhecido"
                val rssi = result.rssi

                val existingIndex = discoveredDevicesState.indexOfFirst { it.address == address }
                if (existingIndex >= 0) {
                    discoveredDevicesState[existingIndex] = DiscoveredDevice(name, address, rssi)
                } else {
                    discoveredDevicesState.add(DiscoveredDevice(name, address, rssi))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        setContent {
            ESP32AppTheme {
                MainScreenContent(
                    isScanning = isScanningState.value,
                    isBtEnabled = bluetoothEnabledState.value,
                    devices = discoveredDevicesState,
                    onToggleScan = {
                        if (isScanningState.value) {
                            stopBleScan()
                        } else {
                            checkPermissionsAndScan()
                        }
                    },
                    onDeviceClick = { device ->
                        stopBleScan()
                        val intent = Intent(this@MainActivity, ControlActivity::class.java).apply {
                            putExtra(ControlActivity.EXTRA_DEVICE_ADDRESS, device.address)
                        }
                        startActivity(intent)
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkBluetoothState()
    }

    private fun checkBluetoothState() {
        bluetoothAdapter?.let {
            bluetoothEnabledState.value = it.isEnabled
        }
    }

    private fun checkPermissionsAndScan() {
        if (hasPermissions()) {
            startBleScan()
        } else {
            requestPermissions()
        }
    }

    private fun hasPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            )
        } else {
            requestPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        bluetoothAdapter?.let { adapter ->
            if (!adapter.isEnabled) {
                Toast.makeText(this, "Ative o Bluetooth primeiro", Toast.LENGTH_SHORT).show()
                return
            }
            bluetoothLeScanner = adapter.bluetoothLeScanner
        }

        if (bluetoothLeScanner == null) {
            Toast.makeText(this, "Scanner BLE indisponível", Toast.LENGTH_SHORT).show()
            return
        }

        // Verifica GPS
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val gpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true ||
                locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
        if (!gpsEnabled) {
            Toast.makeText(this, "Ative a Localização (GPS) para scan BLE!", Toast.LENGTH_LONG).show()
        }

        discoveredDevicesState.clear()
        isScanningState.value = true

        handler.postDelayed({
            if (isScanningState.value) {
                stopBleScan()
                Toast.makeText(this, "Scan encerrado (10s)", Toast.LENGTH_SHORT).show()
            }
        }, SCAN_PERIOD)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bluetoothLeScanner?.startScan(null, settings, leScanCallback)
        } catch (e: Exception) {
            bluetoothLeScanner?.startScan(leScanCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        if (isScanningState.value) {
            isScanningState.value = false
            handler.removeCallbacksAndMessages(null)
            try {
                bluetoothLeScanner?.stopScan(leScanCallback)
            } catch (ignored: Exception) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    isScanning: Boolean,
    isBtEnabled: Boolean,
    devices: List<MainActivity.DiscoveredDevice>,
    onToggleScan: () -> Unit,
    onDeviceClick: (MainActivity.DiscoveredDevice) -> Unit
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "ESP32 Controller",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { ThemeManager.toggleTheme(context) }) {
                        Icon(
                            imageVector = if (ThemeManager.isDarkModeState.value) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar Tema Modo Claro/Escuro",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onToggleScan,
                containerColor = CyanPrimary,
                contentColor = CyanOnPrimary,
                icon = {
                    Icon(
                        imageVector = if (isScanning) Icons.AutoMirrored.Filled.BluetoothSearching else Icons.Default.Refresh,
                        contentDescription = "Scan BLE"
                    )
                },
                text = {
                    Text(
                        text = if (isScanning) "Buscando..." else "Escanear ESP32",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Card Status Bluetooth
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isBtEnabled) ConnectedGreen else DisconnectedRed)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isBtEnabled) "Bluetooth Ativado" else "Bluetooth Desativado",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = CyanPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }

            if (isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = CyanPrimary,
                    trackColor = DarkSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                text = "Dispositivos Encontrados (${devices.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = TextSecondary.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isScanning) "Procurando ESP32-S3..." else "Clique em Escanear ESP32",
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(devices) { device ->
                        DeviceCard(device = device, onClick = { onDeviceClick(device) })
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    device: MainActivity.DiscoveredDevice,
    onClick: () -> Unit
) {
    val isEsp32 = device.name.contains("ESP32", ignoreCase = true)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isEsp32) DarkSurfaceHighlight else DarkSurface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = if (isEsp32) CyanPrimary else TextSecondary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = device.address,
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isEsp32) CyanPrimary else DarkSurfaceVariant,
                    contentColor = if (isEsp32) CyanOnPrimary else MaterialTheme.colorScheme.onSurface
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Conectar", fontWeight = FontWeight.Bold)
            }
        }
    }
}
