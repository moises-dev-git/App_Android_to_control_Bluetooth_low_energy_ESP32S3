package com.example.esp32app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Intent;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;
    private Handler handler = new Handler(Looper.getMainLooper());
    private static final long SCAN_PERIOD = 10000;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private ListView devicesListView;
    private ArrayAdapter<String> devicesAdapter;
    private ArrayList<String> deviceAddresses = new ArrayList<>();
    
    private Button btnEnableBluetooth;
    private static final int REQUEST_ENABLE_BT = 2;
    private boolean hasPromptedBluetooth = false;

    private ScanCallback leScanCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            
            if (device != null) {
                String deviceAddress = device.getAddress();
                String deviceName = device.getName(); 

                // Adiciona apenas se for um dispositivo novo
                if (!deviceAddresses.contains(deviceAddress)) {
                    deviceAddresses.add(deviceAddress);
                    String displayName = (deviceName != null ? deviceName : "Dispositivo Desconhecido") + "\n" + deviceAddress;
                    devicesAdapter.add(displayName);
                    devicesAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Configura o ListView para exibir os dispositivos
        devicesListView = findViewById(R.id.devicesListView);
        devicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        devicesListView.setAdapter(devicesAdapter);
        
        devicesListView.setOnItemClickListener((parent, view, position, id) -> {
            if (scanning) {
                // Para o scan antes de conectar
                scanning = false;
                if (bluetoothLeScanner != null) bluetoothLeScanner.stopScan(leScanCallback);
                handler.removeCallbacksAndMessages(null);
            }
            
            String address = deviceAddresses.get(position);
            android.content.Intent intent = new android.content.Intent(MainActivity.this, ControlActivity.class);
            intent.putExtra(ControlActivity.EXTRA_DEVICE_ADDRESS, address);
            startActivity(intent);
        });

        // Inicializa serviços de Bluetooth
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter != null) {
                bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            }
        }

        btnEnableBluetooth = findViewById(R.id.btnEnableBluetooth);
        btnEnableBluetooth.setOnClickListener(v -> {
            boolean canConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
            if (canConnect) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            } else {
                requestPermissions();
            }
        });

        // Configura o botão flutuante (FAB)
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (hasPermissions()) {
                if (bluetoothLeScanner != null) {
                    scanLeDevice();
                } else {
                    Toast.makeText(this, "Bluetooth não suportado ou desativado", Toast.LENGTH_SHORT).show();
                }
            } else {
                requestPermissions();
            }
        });

        // Solicita permissões logo que o app abre
        if (!hasPermissions()) {
            requestPermissions();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkBluetoothState();
    }

    private void checkBluetoothState() {
        if (bluetoothAdapter != null) {
            if (!bluetoothAdapter.isEnabled()) {
                btnEnableBluetooth.setVisibility(android.view.View.VISIBLE);
                devicesListView.setVisibility(android.view.View.GONE);
                
                boolean canConnect = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
                if (canConnect && !hasPromptedBluetooth) {
                    hasPromptedBluetooth = true;
                    // Solicita ligar o bluetooth
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                }
            } else {
                btnEnableBluetooth.setVisibility(android.view.View.GONE);
                devicesListView.setVisibility(android.view.View.VISIBLE);
                hasPromptedBluetooth = false;
            }
        }
    }

    private boolean hasPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
                   ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissões concedidas!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "O Bluetooth precisa de permissões para funcionar", Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void scanLeDevice() {
        if (!scanning) {
            // Limpa a lista antes de um novo Scan
            devicesAdapter.clear();
            deviceAddresses.clear();

            handler.postDelayed(() -> {
                if (scanning) {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                    Toast.makeText(MainActivity.this, "Scan Encerrado após 10s", Toast.LENGTH_SHORT).show();
                }
            }, SCAN_PERIOD);

            scanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
            Toast.makeText(MainActivity.this, "Scan Iniciado", Toast.LENGTH_SHORT).show();
        } else {
            scanning = false;
            handler.removeCallbacksAndMessages(null); // Cancela o timer de 10 segundos
            bluetoothLeScanner.stopScan(leScanCallback);
            Toast.makeText(MainActivity.this, "Scan Encerrado manualmente", Toast.LENGTH_SHORT).show();
        }
    }
}
