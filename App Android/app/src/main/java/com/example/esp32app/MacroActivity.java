package com.example.esp32app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class MacroActivity extends AppCompatActivity {

    private EditText etMacroName, etMacroContent, etComboKey;
    private CheckBox cbCtrl, cbAlt, cbShift, cbWin;
    private Button btnAddCombo;
    private ListView lvMacros;
    private List<Macro> savedMacros;
    private ArrayAdapter<Macro> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_macro);

        etMacroName = findViewById(R.id.etMacroName);
        etMacroContent = findViewById(R.id.etMacroContent);
        etComboKey = findViewById(R.id.etComboKey);
        cbCtrl = findViewById(R.id.cbCtrl);
        cbAlt = findViewById(R.id.cbAlt);
        cbShift = findViewById(R.id.cbShift);
        cbWin = findViewById(R.id.cbWin);
        btnAddCombo = findViewById(R.id.btnAddCombo);
        lvMacros = findViewById(R.id.lvMacros);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveMacro).setOnClickListener(v -> saveMacro());

        btnAddCombo.setOnClickListener(v -> addCustomCombo());

        // Habilita rolagem suave interna no EditText de conteúdo da Macro
        etMacroContent.setMovementMethod(new android.text.method.ScrollingMovementMethod());
        etMacroContent.setOnTouchListener((v, event) -> {
            v.getParent().requestDisallowInterceptTouchEvent(true);
            if ((event.getAction() & android.view.MotionEvent.ACTION_MASK) == android.view.MotionEvent.ACTION_UP) {
                v.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });

        // Botões de Navegação Rápida (Topo e Fim)
        findViewById(R.id.btnScrollTop).setOnClickListener(v -> {
            etMacroContent.setSelection(0);
            etMacroContent.scrollTo(0, 0);
        });

        findViewById(R.id.btnScrollBottom).setOnClickListener(v -> {
            if (etMacroContent.getText().length() > 0) {
                etMacroContent.setSelection(etMacroContent.getText().length());
                if (etMacroContent.getLayout() != null) {
                    int scrollY = etMacroContent.getLayout().getLineTop(etMacroContent.getLineCount()) - etMacroContent.getHeight();
                    etMacroContent.scrollTo(0, Math.max(0, scrollY));
                }
            }
        });

        setupShortcutButtons();
        loadMacros();
    }

    private void addCustomCombo() {
        String key = etComboKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this, "Informe a tecla para a combinação", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> modifiers = new ArrayList<>();
        if (cbCtrl.isChecked()) modifiers.add("CTRL");
        if (cbAlt.isChecked()) modifiers.add("ALT");
        if (cbShift.isChecked()) modifiers.add("SHIFT");
        if (cbWin.isChecked()) modifiers.add("WIN");

        StringBuilder combo = new StringBuilder();
        for (int i = 0; i < modifiers.size(); i++) {
            combo.append(modifiers.get(i)).append("+");
        }
        combo.append(key.toUpperCase());
        combo.append("\n");

        appendShortcut(combo.toString());
        etComboKey.setText("");
    }

    private void setupShortcutButtons() {
        int[] buttonIds = {
                // Atalhos Rápidos
                R.id.btnShortcutCtrlZ, R.id.btnShortcutCtrlC, R.id.btnShortcutCtrlV,
                R.id.btnShortcutCtrlShiftEsc, R.id.btnShortcutAltF4, R.id.btnShortcutWinD,

                // Edição & Controle
                R.id.btnShortcutEnter, R.id.btnShortcutTab, R.id.btnShortcutEsc,
                R.id.btnShortcutSpace, R.id.btnShortcutBackspace, R.id.btnShortcutDelete,
                R.id.btnShortcutInsert, R.id.btnShortcutHome, R.id.btnShortcutEnd,
                R.id.btnShortcutPageUp, R.id.btnShortcutPageDown,

                // Setas & Navegação
                R.id.btnShortcutUp, R.id.btnShortcutDown, R.id.btnShortcutLeft, R.id.btnShortcutRight,
                R.id.btnShortcutCapsLock,

                // Teclas de Função F1 a F12
                R.id.btnShortcutF1, R.id.btnShortcutF2, R.id.btnShortcutF3, R.id.btnShortcutF4,
                R.id.btnShortcutF5, R.id.btnShortcutF6, R.id.btnShortcutF7, R.id.btnShortcutF8,
                R.id.btnShortcutF9, R.id.btnShortcutF10, R.id.btnShortcutF11, R.id.btnShortcutF12,

                // Ações & Comandos
                R.id.btnShortcutText, R.id.btnShortcutDelay, R.id.btnShortcutMouseMove, R.id.btnShortcutMouseHuman, R.id.btnShortcutMouseLClick, R.id.btnShortcutMouseRClick
        };

        String[] shortcuts = {
                // Atalhos Rápidos
                "CTRL+Z\n", "CTRL+C\n", "CTRL+V\n",
                "CTRL+SHIFT+ESC\n", "ALT+F4\n", "WIN+D\n",

                // Edição & Controle
                "ENTER\n", "TAB\n", "ESC\n",
                "SPACE\n", "BACKSPACE\n", "DELETE\n",
                "INSERT\n", "HOME\n", "END\n",
                "PAGEUP\n", "PAGEDOWN\n",

                // Setas & Navegação
                "UP\n", "DOWN\n", "LEFT\n", "RIGHT\n",
                "CAPSLOCK\n",

                // Teclas de Função F1 a F12
                "F1\n", "F2\n", "F3\n", "F4\n",
                "F5\n", "F6\n", "F7\n", "F8\n",
                "F9\n", "F10\n", "F11\n", "F12\n",

                // Ações & Comandos
                "TEXT:\n", "DELAY:500\n", "MOVE:500,300\n", "MOVE_HUMAN:500,300,5\n", "MOUSE_LCLICK\n", "MOUSE_RCLICK\n"
        };

        for (int i = 0; i < buttonIds.length; i++) {
            final String textToInsert = shortcuts[i];
            findViewById(buttonIds[i]).setOnClickListener(v -> appendShortcut(textToInsert));
        }
    }

    private void appendShortcut(String shortcut) {
        int start = Math.max(etMacroContent.getSelectionStart(), 0);
        int end = Math.max(etMacroContent.getSelectionEnd(), 0);
        etMacroContent.getText().replace(Math.min(start, end), Math.max(start, end),
                shortcut, 0, shortcut.length());
        etMacroContent.requestFocus();
    }

    private void saveMacro() {
        String name = etMacroName.getText().toString().trim();
        String content = etMacroContent.getText().toString().trim();

        if (name.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Preencha nome e conteúdo", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean found = false;
        for (int i = 0; i < savedMacros.size(); i++) {
            if (savedMacros.get(i).getName().equalsIgnoreCase(name)) {
                savedMacros.set(i, new Macro(name, content));
                found = true;
                break;
            }
        }
        if (!found) {
            savedMacros.add(new Macro(name, content));
        }

        MacroManager.saveMacros(this, savedMacros);
        Toast.makeText(this, "Macro Salva!", Toast.LENGTH_SHORT).show();
        etMacroName.setText("");
        etMacroContent.setText("");
        loadMacros();
    }

    private void loadMacros() {
        savedMacros = MacroManager.getMacros(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, savedMacros);
        lvMacros.setAdapter(adapter);

        lvMacros.setOnItemClickListener((parent, view, position, id) -> {
            Macro m = savedMacros.get(position);
            etMacroName.setText(m.getName());
            etMacroContent.setText(m.getContent());
        });

        lvMacros.setOnItemLongClickListener((parent, view, position, id) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Excluir Macro")
                    .setMessage("Tem certeza que deseja excluir a macro: " + savedMacros.get(position).getName() + "?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        savedMacros.remove(position);
                        MacroManager.saveMacros(this, savedMacros);
                        loadMacros();
                        Toast.makeText(this, "Macro excluída", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Não", null)
                    .show();
            return true;
        });
    }
}
