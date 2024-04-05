package com.mseei.hexiwear_read_app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class Opciones extends AppCompatActivity {
    //Bluetooth varibales
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private String sBTAddr;
    //variables de interfaz
    private Button btReturn, btEmergency, btRecord;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_opciones);

        btRecord = findViewById(R.id.btRecordMode);
        btEmergency = findViewById(R.id.btEmergencia);
        btReturn = findViewById(R.id.btDisconnect);

        Bundle extras = getIntent().getExtras();
        sBTAddr = extras.getString("BTAddr"); //recogemos la direccion que se nos manda desde la actividad anterior
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sBTAddr);
        }
        connectToDevice(); //nos conectamos al dispositivo HEXIWEAR
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        bluetoothGatt = bluetoothDevice.connectGatt(this, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    showToast(getString(R.string.DeviceConnected));
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    showToast(getString(R.string.DeviceDisconnect));
                }
            }
        });
    }
    private void showToast(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
    @SuppressLint("MissingPermission")
    public void onClickDisconnect (View v) throws IOException {  //si se pulsa el boton desconectar se vuelve a la primera pantalla para volver a conectarnos
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
        Intent intent = new Intent (this, MainActivity.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish(); //terminamos esta actividad
    }
    public void onClickEmergencyMode (View v) throws IOException { //se abre la actividad de modo emergencia, que monitoriza movimientos
        Intent intent = new Intent (this, Emergencia.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish(); //Se cierra esta actividad
    }

    public void onClickRecordMode (View v) throws IOException { //se abre la actividad de grabar movimientos
        Intent intent = new Intent (this, Grabacion.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish(); //Se cierra esta actividad
    }

}
