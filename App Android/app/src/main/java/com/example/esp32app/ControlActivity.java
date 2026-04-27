package com.example.esp32app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import java.util.List;

import java.util.UUID;

@SuppressLint("MissingPermission")
public class ControlActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";

    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHARACTERISTIC_UUID_TX = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;

    private TextView tvStatus, tvTemperature;
    private EditText etText;
    private Button btnSendText, btnChrome, btnCopy, btnPaste, btnLClick, btnRClick;
    private Spinner spinnerMacros;
    private Button btnRunMacro, btnManageMacros;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control);

        tvStatus = findViewById(R.id.tvStatus);
        etText = findViewById(R.id.etText);
        btnSendText = findViewById(R.id.btnSendText);
        btnChrome = findViewById(R.id.btnChrome);
        btnCopy = findViewById(R.id.btnCopy);
        btnPaste = findViewById(R.id.btnPaste);
        btnLClick = findViewById(R.id.btnLClick);
        btnRClick = findViewById(R.id.btnRClick);
        spinnerMacros = findViewById(R.id.spinnerMacros);
        btnRunMacro = findViewById(R.id.btnRunMacro);
        btnManageMacros = findViewById(R.id.btnManageMacros);

        String deviceAddress = getIntent().getStringExtra(EXTRA_DEVICE_ADDRESS);
        if (deviceAddress == null) {
            Toast.makeText(this, "Endereço do dispositivo não encontrado", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager == null) {
            finish();
            return;
        }
        
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);

        tvStatus.setText("Conectando a " + deviceAddress + "...");
        bluetoothGatt = device.connectGatt(this, false, gattCallback);

        setupButtons();
    }

    private void setupButtons() {
        btnSendText.setOnClickListener(v -> sendCommand("TXT:" + etText.getText().toString()));
        btnChrome.setOnClickListener(v -> sendCommand("CMD:CHROME"));
        btnCopy.setOnClickListener(v -> sendCommand("CMD:COPY"));
        btnPaste.setOnClickListener(v -> sendCommand("CMD:PASTE"));
        btnLClick.setOnClickListener(v -> sendCommand("CMD:MOUSE_LCLICK"));
        btnRClick.setOnClickListener(v -> sendCommand("CMD:MOUSE_RCLICK"));
        
        btnManageMacros.setOnClickListener(v -> {
            startActivity(new Intent(this, MacroActivity.class));
        });

        btnRunMacro.setOnClickListener(v -> {
            Macro selected = (Macro) spinnerMacros.getSelectedItem();
            if (selected != null) {
                sendCommand("SEQ:" + selected.getContent());
            } else {
                Toast.makeText(this, "Nenhum macro selecionado", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<Macro> macros = MacroManager.getMacros(this);
        ArrayAdapter<Macro> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, macros);
        spinnerMacros.setAdapter(adapter);
    }

    private void enableButtons() {
        runOnUiThread(() -> {
            tvStatus.setText("Conectado! Pronto para enviar.");
            btnSendText.setEnabled(true);
            btnChrome.setEnabled(true);
            btnCopy.setEnabled(true);
            btnPaste.setEnabled(true);
            btnLClick.setEnabled(true);
            btnRClick.setEnabled(true);
            btnRunMacro.setEnabled(true);
        });
    }

    private void sendCommand(String command) {
        if (bluetoothGatt != null && rxCharacteristic != null) {
            rxCharacteristic.setValue(command);
            boolean success = bluetoothGatt.writeCharacteristic(rxCharacteristic);
            if (!success) {
                runOnUiThread(() -> Toast.makeText(this, "Falha ao iniciar envio", Toast.LENGTH_SHORT).show());
            }
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                runOnUiThread(() -> tvStatus.setText("Conectado! Descobrindo serviços..."));
                gatt.discoverServices();
            } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                runOnUiThread(() -> {
                    tvStatus.setText("Desconectado");
                    finish(); // Fechar tela e voltar
                });
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService service = gatt.getService(SERVICE_UUID);
                if (service != null) {
                    rxCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_RX);
                    
                    BluetoothGattCharacteristic txCharacteristic = service.getCharacteristic(CHARACTERISTIC_UUID_TX);
                    if (txCharacteristic != null) {
                        gatt.setCharacteristicNotification(txCharacteristic, true);
                        android.bluetooth.BluetoothGattDescriptor descriptor = txCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG);
                        if (descriptor != null) {
                            // Em Android SDKs recentes, pode ser getWriteType()
                            descriptor.setValue(android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(descriptor);
                        }
                    }

                    if (rxCharacteristic != null) {
                        enableButtons();
                    } else {
                        runOnUiThread(() -> tvStatus.setText("Característica RX não encontrada"));
                    }
                } else {
                    runOnUiThread(() -> tvStatus.setText("Serviço UART não encontrado no ESP32"));
                }
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (CHARACTERISTIC_UUID_TX.equals(characteristic.getUuid())) {
                String rx = characteristic.getStringValue(0);
                if (rx != null) {
                    runOnUiThread(() -> {
                        if (rx.startsWith("TEMP:")) {
                            tvStatus.setText("Conectado | ESP Temp: " + rx.substring(5));
                        } else {
                            //Toast.makeText(ControlActivity.this, rx, Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }
}
