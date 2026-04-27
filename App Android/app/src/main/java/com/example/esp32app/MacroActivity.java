package com.example.esp32app;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

public class MacroActivity extends AppCompatActivity {

    private EditText etMacroName, etMacroContent;
    private ListView lvMacros;
    private List<Macro> savedMacros;
    private ArrayAdapter<Macro> adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_macro);

        etMacroName = findViewById(R.id.etMacroName);
        etMacroContent = findViewById(R.id.etMacroContent);
        lvMacros = findViewById(R.id.lvMacros);

        findViewById(R.id.btnCancel).setOnClickListener(v -> finish());
        findViewById(R.id.btnSaveMacro).setOnClickListener(v -> saveMacro());

        setupShortcutButtons();
        loadMacros();
    }

    private void setupShortcutButtons() {
        int[] buttonIds = {
                R.id.btnShortcutTab, R.id.btnShortcutEnter, R.id.btnShortcutCtrlF,
                R.id.btnShortcutEsc, R.id.btnShortcutAltTab, R.id.btnShortcutCtrlTab,
                R.id.btnShortcutText, R.id.btnShortcutDelay
        };

        String[] shortcuts = {
                "TAB\n", "ENTER\n", "CTRL_F\n",
                "ESC\n", "ALT_TAB\n", "CTRL_TAB\n",
                "TEXT:", "DELAY:500\n"
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

        // overwrite existing if same name, or add new
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
        Toast.makeText(this, "Macro Salvo!", Toast.LENGTH_SHORT).show();
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
                    .setMessage("Tem certeza que deseja excluir o macro: " + savedMacros.get(position).getName() + "?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        savedMacros.remove(position);
                        MacroManager.saveMacros(this, savedMacros);
                        loadMacros();
                        Toast.makeText(this, "Macro excluído", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Não", null)
                    .show();
            return true;
        });
    }
}
