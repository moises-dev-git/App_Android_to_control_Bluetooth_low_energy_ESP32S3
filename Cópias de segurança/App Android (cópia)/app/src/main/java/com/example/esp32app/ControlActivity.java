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

import java.util.UUID;

@SuppressLint("MissingPermission")
public class ControlActivity extends AppCompatActivity {

    public static final String EXTRA_DEVICE_ADDRESS = "device_address";

    private static final UUID SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    private static final UUID CHARACTERISTIC_UUID_RX = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");

    private BluetoothGatt bluetoothGatt;
    private BluetoothGattCharacteristic rxCharacteristic;

    private TextView tvStatus;
    private EditText etText;
    private Button btnSendText, btnChrome, btnCopy, btnPaste, btnLClick, btnRClick;

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
