package com.example.esp32app

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.esp32app.protocol.BleProtocol
import com.example.esp32app.protocol.MacroStep
import com.example.esp32app.ui.theme.*
import kotlinx.coroutines.*
import java.util.*

@SuppressLint("MissingPermission")
class ControlActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_ADDRESS = "device_address"

        private val SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CHARACTERISTIC_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CHARACTERISTIC_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // Fragmented Execution & Coroutines State
    private var writeCompletionDeferred: CompletableDeferred<Boolean>? = null
    private var macroExecutionJob: Job? = null

    // Compose State
    private val connectionStatusState = mutableStateOf("Conectando...")
    private val isConnectedState = mutableStateOf(false)
    private val espTempState = mutableStateOf<String?>(null)
    private val savedMacrosState = mutableStateListOf<Macro>()
    private val isExecutingMacroState = mutableStateOf(false)
    private val macroProgressState = mutableStateOf("")

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            writeCompletionDeferred?.complete(status == BluetoothGatt.GATT_SUCCESS)
        }
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                runOnUiThread { connectionStatusState.value = "Conectado! Configurando GATT..." }
                val mtuRequested = gatt?.requestMtu(512) ?: false
                if (!mtuRequested) {
                    gatt?.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                gatt?.close()
                runOnUiThread {
                    connectionStatusState.value = "Desconectado"
                    isConnectedState.value = false
                    finish()
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            runOnUiThread { connectionStatusState.value = "Conectado! Descobrindo serviços..." }
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt?.getService(SERVICE_UUID)
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX)

                    val txCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX)
                    if (txCharacteristic != null) {
                        gatt.setCharacteristicNotification(txCharacteristic, true)
                        val descriptor = txCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                        if (descriptor != null) {
                            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                            gatt.writeDescriptor(descriptor)
                        }
                    }

                    if (rxCharacteristic != null) {
                        runOnUiThread {
                            connectionStatusState.value = "Pronto para enviar comandos"
                            isConnectedState.value = true
                        }
                    } else {
                        runOnUiThread { connectionStatusState.value = "Característica RX não encontrada" }
                    }
                } else {
                    runOnUiThread { connectionStatusState.value = "Serviço UART não encontrado" }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            if (characteristic != null && CHARACTERISTIC_UUID_TX == characteristic.uuid) {
                val rx = characteristic.getStringValue(0)
                if (rx != null && rx.startsWith("TEMP:")) {
                    runOnUiThread {
                        espTempState.value = rx.substring(5)
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)

        val deviceAddress = intent.getStringExtra(EXTRA_DEVICE_ADDRESS)
        if (deviceAddress == null) {
            Toast.makeText(this, "Endereço do dispositivo não informado", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val bluetoothAdapter = bluetoothManager?.adapter
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)

        if (device != null) {
            connectionStatusState.value = "Conectando a $deviceAddress..."
            bluetoothGatt = device.connectGatt(this, false, gattCallback)
        } else {
            Toast.makeText(this, "Erro ao obter dispositivo remoto", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            ESP32AppTheme {
                ControlScreenContent(
                    deviceAddress = deviceAddress,
                    statusText = connectionStatusState.value,
                    isConnected = isConnectedState.value,
                    espTemp = espTempState.value,
                    macros = savedMacrosState,
                    isExecutingMacro = isExecutingMacroState.value,
                    macroProgress = macroProgressState.value,
                    onSendCommand = { command -> sendCommand(command) },
                    onExecuteMacro = { macro -> executeMacroSequence(macro) },
                    onCancelMacro = { cancelMacroExecution() },
                    onManageMacros = {
                        startActivity(Intent(this@ControlActivity, MacroActivity::class.java))
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        savedMacrosState.clear()
        savedMacrosState.addAll(MacroManager.getMacros(this))
    }

    private fun sendCommand(command: String) {
        val gatt = bluetoothGatt
        val rx = rxCharacteristic
        if (gatt != null && rx != null) {
            rx.value = command.toByteArray(Charsets.UTF_8)
            val success = gatt.writeCharacteristic(rx)
            if (!success) {
                runOnUiThread { Toast.makeText(this, "Falha ao iniciar envio", Toast.LENGTH_SHORT).show() }
            }
        } else {
            Toast.makeText(this, "Não conectado ao ESP32", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun sendSinglePacket(packet: String): Boolean {
        val gatt = bluetoothGatt ?: return false
        val rx = rxCharacteristic ?: return false

        val deferred = CompletableDeferred<Boolean>()
        writeCompletionDeferred = deferred

        rx.value = packet.toByteArray(Charsets.UTF_8)
        val started = gatt.writeCharacteristic(rx)
        if (!started) {
            writeCompletionDeferred = null
            return false
        }

        return try {
            withTimeout(1500) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            true // tolerância para eventuais atrasos de callback do dispositivo
        } finally {
            writeCompletionDeferred = null
        }
    }

    private fun executeMacroSequence(macro: Macro) {
        if (!isConnectedState.value) {
            Toast.makeText(this, "Não conectado ao ESP32", Toast.LENGTH_SHORT).show()
            return
        }

        val steps = BleProtocol.splitMacroIntoSteps(macro.content)
        if (steps.isEmpty()) {
            Toast.makeText(this, "A macro selecionada está vazia", Toast.LENGTH_SHORT).show()
            return
        }

        macroExecutionJob?.cancel()
        macroExecutionJob = lifecycleScope.launch {
            isExecutingMacroState.value = true
            val total = steps.size
            try {
                for ((index, step) in steps.withIndex()) {
                    macroProgressState.value = "Passo ${index + 1} de $total"
                    when (step) {
                        is MacroStep.Delay -> {
                            delay(step.millis)
                        }
                        is MacroStep.Command -> {
                            val ok = sendSinglePacket(step.payload)
                            if (!ok) {
                                delay(40)
                                sendSinglePacket(step.payload)
                            }
                            // Intervalo para o processador do ESP32 executar a ação USB HID
                            delay(40)
                        }
                    }
                }
                macroProgressState.value = "Macro concluída com sucesso!"
                delay(1800)
                macroProgressState.value = ""
            } catch (_: CancellationException) {
                macroProgressState.value = "Execução cancelada"
                delay(1200)
                macroProgressState.value = ""
            } catch (e: Exception) {
                macroProgressState.value = "Erro no envio"
                delay(1500)
                macroProgressState.value = ""
            } finally {
                isExecutingMacroState.value = false
            }
        }
    }

    private fun cancelMacroExecution() {
        macroExecutionJob?.cancel()
        isExecutingMacroState.value = false
        macroProgressState.value = "Cancelando..."
        lifecycleScope.launch {
            delay(1000)
            macroProgressState.value = ""
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        macroExecutionJob?.cancel()
        bluetoothGatt?.disconnect()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlScreenContent(
    deviceAddress: String,
    statusText: String,
    isConnected: Boolean,
    espTemp: String?,
    macros: List<Macro>,
    isExecutingMacro: Boolean,
    macroProgress: String,
    onSendCommand: (String) -> Unit,
    onExecuteMacro: (Macro) -> Unit,
    onCancelMacro: () -> Unit,
    onManageMacros: () -> Unit,
    onBack: () -> Unit
) {
    var textToSend by remember { mutableStateOf("") }
    var comboKey by remember { mutableStateOf("") }
    var ctrlChecked by remember { mutableStateOf(false) }
    var altChecked by remember { mutableStateOf(false) }
    var shiftChecked by remember { mutableStateOf(false) }
    var winChecked by remember { mutableStateOf(false) }

    var selectedMacroIndex by remember { mutableStateOf(0) }
    var macroDropdownExpanded by remember { mutableStateOf(false) }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Painel ESP32", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(deviceAddress, fontSize = 12.sp, color = TextSecondary)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    IconButton(onClick = { ThemeManager.toggleTheme(context) }) {
                        Icon(
                            imageVector = if (ThemeManager.isDarkModeState.value) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Alternar Tema",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card de Status da Conexão
            Card(
                modifier = Modifier.fillMaxWidth(),
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
                            .background(if (isConnected) ConnectedGreen else WarningYellow)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = statusText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                        espTemp?.let { temp ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Temperatura ESP: $temp",
                                color = CyanPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // --- AÇÕES RÁPIDAS (GRID DE BOTÕES) ---
            Text(
                text = "Ações Rápidas",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    text = "Chrome",
                    icon = Icons.Default.Language,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    onClick = { onSendCommand(BleProtocol.formatQuickCommand("CHROME")) }
                )
                QuickActionButton(
                    text = "Copiar",
                    icon = Icons.Default.ContentCopy,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    onClick = { onSendCommand(BleProtocol.formatQuickCommand("COPY")) }
                )
                QuickActionButton(
                    text = "Colar",
                    icon = Icons.Default.ContentPaste,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    onClick = { onSendCommand(BleProtocol.formatQuickCommand("PASTE")) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionButton(
                    text = "Clique Esq.",
                    icon = Icons.Default.Mouse,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    onClick = { onSendCommand(BleProtocol.formatQuickCommand("MOUSE_LCLICK")) }
                )
                QuickActionButton(
                    text = "Clique Dir.",
                    icon = Icons.Default.AdsClick,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected,
                    onClick = { onSendCommand(BleProtocol.formatQuickCommand("MOUSE_RCLICK")) }
                )
            }

            // --- ENVIAR TEXTO DIRETO ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Enviar Texto Digitado",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textToSend,
                        onValueChange = { textToSend = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Digite o texto aqui...", color = TextSecondary) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant
                        )
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (textToSend.isNotEmpty()) {
                                onSendCommand(BleProtocol.formatTextCommand(textToSend))
                                textToSend = ""
                            }
                        },
                        enabled = isConnected && textToSend.isNotEmpty(),
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enviar Texto", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // --- ENVIAR ATALHO / COMBO CUSTOMIZADO ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Atalho de Teclas Customizado",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = ctrlChecked,
                            onClick = { ctrlChecked = !ctrlChecked },
                            label = { Text("CTRL") }
                        )
                        FilterChip(
                            selected = altChecked,
                            onClick = { altChecked = !altChecked },
                            label = { Text("ALT") }
                        )
                        FilterChip(
                            selected = shiftChecked,
                            onClick = { shiftChecked = !shiftChecked },
                            label = { Text("SHIFT") }
                        )
                        FilterChip(
                            selected = winChecked,
                            onClick = { winChecked = !winChecked },
                            label = { Text("WIN") }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = comboKey,
                            onValueChange = { comboKey = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Ex: C, Z, F5", color = TextSecondary) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Button(
                            onClick = {
                                if (comboKey.isNotBlank()) {
                                    val mods = mutableListOf<String>()
                                    if (ctrlChecked) mods.add("CTRL")
                                    if (altChecked) mods.add("ALT")
                                    if (shiftChecked) mods.add("SHIFT")
                                    if (winChecked) mods.add("WIN")

                                    onSendCommand(BleProtocol.formatKeyCombo(mods, comboKey))
                                }
                            },
                            enabled = isConnected && comboKey.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                        ) {
                            Text("Enviar Combo", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- MACROS ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Executar Macro Salva",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                        TextButton(onClick = onManageMacros) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gerenciar", color = CyanPrimary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (macros.isEmpty()) {
                        Text(
                            text = "Nenhuma macro cadastrada. Clique em Gerenciar para criar uma.",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                    } else {
                        Box {
                            OutlinedButton(
                                onClick = { macroDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                            ) {
                                val currentMacroName = if (selectedMacroIndex < macros.size) macros[selectedMacroIndex].name else "Selecione"
                                Text(currentMacroName, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = macroDropdownExpanded,
                                onDismissRequest = { macroDropdownExpanded = false }
                            ) {
                                macros.forEachIndexed { index, macro ->
                                    DropdownMenuItem(
                                        text = { Text(macro.name) },
                                        onClick = {
                                            selectedMacroIndex = index
                                            macroDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        if (isExecutingMacro || macroProgress.isNotEmpty()) {
                            Text(
                                text = if (isExecutingMacro) "Status: $macroProgress" else macroProgress,
                                color = if (isExecutingMacro) CyanPrimary else ConnectedGreen,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (isExecutingMacro) {
                            Button(
                                onClick = onCancelMacro,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancelar Execução da Macro", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = {
                                    if (selectedMacroIndex < macros.size) {
                                        val selected = macros[selectedMacroIndex]
                                        onExecuteMacro(selected)
                                    }
                                },
                                enabled = isConnected && macros.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Executar Sequência da Macro", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkSurfaceHighlight,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = null, tint = CyanPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}
