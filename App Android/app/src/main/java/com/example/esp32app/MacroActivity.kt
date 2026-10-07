package com.example.esp32app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.esp32app.ui.theme.*

class MacroActivity : ComponentActivity() {

    private val savedMacrosState = mutableStateListOf<Macro>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        loadMacros()

        setContent {
            ESP32AppTheme {
                MacroScreenContent(
                    macros = savedMacrosState,
                    onSaveMacro = { name, content ->
                        saveMacro(name, content)
                    },
                    onDeleteMacro = { macro ->
                        deleteMacro(macro)
                    },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun loadMacros() {
        savedMacrosState.clear()
        savedMacrosState.addAll(MacroManager.getMacros(this))
    }

    private fun saveMacro(name: String, content: String) {
        if (name.isBlank() || content.isBlank()) {
            Toast.makeText(this, "Preencha o nome e o conteúdo da macro", Toast.LENGTH_SHORT).show()
            return
        }

        val existingIndex = savedMacrosState.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existingIndex >= 0) {
            savedMacrosState[existingIndex] = Macro(name, content)
        } else {
            savedMacrosState.add(Macro(name, content))
        }

        MacroManager.saveMacros(this, savedMacrosState)
        Toast.makeText(this, "Macro Salva com sucesso!", Toast.LENGTH_SHORT).show()
    }

    private fun deleteMacro(macro: Macro) {
        savedMacrosState.remove(macro)
        MacroManager.saveMacros(this, savedMacrosState)
        Toast.makeText(this, "Macro excluída!", Toast.LENGTH_SHORT).show()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroScreenContent(
    macros: List<Macro>,
    onSaveMacro: (String, String) -> Unit,
    onDeleteMacro: (Macro) -> Unit,
    onBack: () -> Unit
) {
    var macroName by remember { mutableStateOf("") }
    var macroContent by remember { mutableStateOf("") }
    var macroToDelete by remember { mutableStateOf<Macro?>(null) }

    var comboKey by remember { mutableStateOf("") }
    var ctrlChecked by remember { mutableStateOf(false) }
    var altChecked by remember { mutableStateOf(false) }
    var shiftChecked by remember { mutableStateOf(false) }
    var winChecked by remember { mutableStateOf(false) }

    fun appendShortcut(shortcut: String) {
        macroContent += if (macroContent.endsWith("\n") || macroContent.isEmpty()) shortcut else "\n$shortcut"
    }

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gerenciador de Macros", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = MaterialTheme.colorScheme.primary)
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
            // Card de Cadastro de Macro
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Editor de Macro", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = macroName,
                        onValueChange = { macroName = it },
                        label = { Text("Nome da Macro") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = macroContent,
                        onValueChange = { macroContent = it },
                        label = { Text("Conteúdo da Sequência (Comandos)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPrimary,
                            unfocusedBorderColor = DarkSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // SEÇÃO DE ATALHOS RÁPIDOS
                    Text("Atalhos Rápidos:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val quickShortcuts = listOf(
                            "CTRL+Z\n", "CTRL+C\n", "CTRL+V\n", "CTRL+SHIFT+ESC\n", "ALT+F4\n", "WIN+D\n"
                        )
                        items(quickShortcuts) { item ->
                            SuggestionChip(
                                onClick = { appendShortcut(item) },
                                label = { Text(item.trim(), fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SEÇÃO DE TECLAS F1-F12
                    Text("Teclas de Função:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val fKeys = (1..12).map { "F$it\n" }
                        items(fKeys) { item ->
                            SuggestionChip(
                                onClick = { appendShortcut(item) },
                                label = { Text(item.trim(), fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // SEÇÃO DE COMANDOS / NAVEGAÇÃO
                    Text("Ações e Edição:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val actionShortcuts = listOf(
                            "ENTER\n", "TAB\n", "ESC\n", "SPACE\n", "BACKSPACE\n", "DELETE\n",
                            "UP\n", "DOWN\n", "LEFT\n", "RIGHT\n",
                            "DELAY:500\n", "TEXT:\n", "MOVE:500,300\n", "MOUSE_LCLICK\n", "MOUSE_RCLICK\n"
                        )
                        items(actionShortcuts) { item ->
                            SuggestionChip(
                                onClick = { appendShortcut(item) },
                                label = { Text(item.trim(), fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Adicionar Combo Customizado
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = ctrlChecked, onClick = { ctrlChecked = !ctrlChecked }, label = { Text("CTRL", fontSize = 10.sp) })
                            FilterChip(selected = altChecked, onClick = { altChecked = !altChecked }, label = { Text("ALT", fontSize = 10.sp) })
                            FilterChip(selected = shiftChecked, onClick = { shiftChecked = !shiftChecked }, label = { Text("SHIFT", fontSize = 10.sp) })
                            FilterChip(selected = winChecked, onClick = { winChecked = !winChecked }, label = { Text("WIN", fontSize = 10.sp) })
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = comboKey,
                            onValueChange = { comboKey = it },
                            placeholder = { Text("Tecla", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyanPrimary,
                                unfocusedBorderColor = DarkSurfaceVariant
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (comboKey.isNotBlank()) {
                                    val mods = mutableListOf<String>()
                                    if (ctrlChecked) mods.add("CTRL")
                                    if (altChecked) mods.add("ALT")
                                    if (shiftChecked) mods.add("SHIFT")
                                    if (winChecked) mods.add("WIN")

                                    val combo = if (mods.isNotEmpty()) {
                                        mods.joinToString("+") + "+" + comboKey.trim().uppercase() + "\n"
                                    } else {
                                        comboKey.trim().uppercase() + "\n"
                                    }
                                    appendShortcut(combo)
                                    comboKey = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary)
                        ) {
                            Text("Add Combo")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            onSaveMacro(macroName, macroContent)
                            macroName = ""
                            macroContent = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanPrimary, contentColor = CyanOnPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Salvar Macro", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Lista de Macros Salvas
            Text(
                text = "Macros Salvas (${macros.size})",
                style = MaterialTheme.typography.titleMedium,
                color = TextSecondary
            )

            if (macros.isEmpty()) {
                Text(
                    text = "Nenhuma macro salva.",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            } else {
                macros.forEach { macro ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                macroName = macro.name
                                macroContent = macro.content
                            },
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Code, contentDescription = null, tint = CyanPrimary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(macro.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text(macro.content.replace("\n", " ↵ "), fontSize = 12.sp, color = TextSecondary, maxLines = 1)
                            }
                            IconButton(onClick = { macroToDelete = macro }) {
                                Icon(Icons.Default.Delete, contentDescription = "Excluir", tint = DisconnectedRed)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de confirmação de exclusão
    macroToDelete?.let { targetMacro ->
        AlertDialog(
            onDismissRequest = { macroToDelete = null },
            title = { Text("Excluir Macro") },
            text = { Text("Deseja realmente excluir a macro '${targetMacro.name}'?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteMacro(targetMacro)
                        macroToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DisconnectedRed)
                ) {
                    Text("Excluir")
                }
            },
            dismissButton = {
                TextButton(onClick = { macroToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}
