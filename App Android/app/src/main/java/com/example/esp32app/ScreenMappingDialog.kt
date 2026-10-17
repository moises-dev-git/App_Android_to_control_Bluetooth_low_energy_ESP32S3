package com.example.esp32app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.esp32app.protocol.BleProtocol
import com.example.esp32app.ui.theme.*
import com.example.esp32app.vision.ScreenMapping

/**
 * Calibração do monitor capturado: diz ao app onde a tela da placa HDMI fica dentro da
 * área de trabalho do PC, para MOVE_/CLICK_TEXT e _IMAGE acertarem com vários monitores.
 * Os botões de teste mandam o cursor para pontos conhecidos usando os valores digitados.
 */
@Composable
fun ScreenMappingDialog(
    initial: ScreenMapping,
    isConnected: Boolean,
    onSendCommand: (String) -> Unit,
    onSave: (ScreenMapping) -> Unit,
    onDismiss: () -> Unit
) {
    var desktopW by remember { mutableStateOf(initial.desktopWidth.toString()) }
    var desktopH by remember { mutableStateOf(initial.desktopHeight.toString()) }
    var monitorX by remember { mutableStateOf(initial.monitorX.toString()) }
    var monitorY by remember { mutableStateOf(initial.monitorY.toString()) }
    var monitorW by remember { mutableStateOf(initial.monitorWidth.toString()) }
    var monitorH by remember { mutableStateOf(initial.monitorHeight.toString()) }

    val mapping = listOf(desktopW, desktopH, monitorX, monitorY, monitorW, monitorH)
        .map { it.toIntOrNull() }
        .takeIf { values -> values.all { it != null } }
        ?.let { v -> ScreenMapping(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!, v[5]!!) }
    val error = if (mapping == null) "Preencha todos os campos com números inteiros" else mapping.validationError()
    val valid = mapping != null && error == null

    fun testPoint(nx: Float, ny: Float) {
        val m = mapping ?: return
        val (x, y) = m.toHid(nx, ny)
        onSendCommand(BleProtocol.formatQuickCommand("ABS:$x,$y"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monitor capturado") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Com vários monitores, o mouse absoluto cobre a área de trabalho inteira. " +
                        "Informe em pixels o tamanho total dela e onde fica o monitor ligado à placa HDMI, " +
                        "contando a partir do canto superior esquerdo da área. Com um só monitor, " +
                        "use o mesmo tamanho nos dois e posição 0,0.",
                    color = TextSecondary, fontSize = 12.sp
                )
                Text("Área de trabalho total", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Largura", desktopW, Modifier.weight(1f)) { desktopW = it }
                    NumberField("Altura", desktopH, Modifier.weight(1f)) { desktopH = it }
                }
                Text("Monitor capturado", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Posição X", monitorX, Modifier.weight(1f)) { monitorX = it }
                    NumberField("Posição Y", monitorY, Modifier.weight(1f)) { monitorY = it }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField("Largura", monitorW, Modifier.weight(1f)) { monitorW = it }
                    NumberField("Altura", monitorH, Modifier.weight(1f)) { monitorH = it }
                }
                Text(
                    "Ex.: principal 1920x1200 à esquerda e capturado 1920x1080 à direita → " +
                        "área 3840x1200, posição 1920,0, tamanho 1920x1080.",
                    color = TextSecondary, fontSize = 11.sp
                )
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Text("Testar no monitor capturado", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (!isConnected) {
                    Text("Conecte ao ESP32 para testar.", color = TextSecondary, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val enabled = valid && isConnected
                    OutlinedButton(enabled = enabled, onClick = { testPoint(0.03f, 0.05f) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("↖ Canto", fontSize = 12.sp)
                    }
                    OutlinedButton(enabled = enabled, onClick = { testPoint(0.5f, 0.5f) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Centro", fontSize = 12.sp)
                    }
                    OutlinedButton(enabled = enabled, onClick = { testPoint(0.97f, 0.95f) }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Canto ↘", fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { mapping?.let(onSave) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
            ) { Text("Salvar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(5)) },
        modifier = modifier,
        singleLine = true,
        label = { Text(label, fontSize = 12.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
