package com.mseei.hexiwear_read_app;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;

public class Umbrales extends AppCompatActivity {
    //variables de movimiento
    private static final String MOVEMENT_SERVICE_UUID = "00002000-0000-1000-8000-00805f9b34fb";
    private static final String ACCELEROMETER_UUID = "00002001-0000-1000-8000-00805f9b34fb";
    private static final String GYROSCOPE_UUID = "00002002-0000-1000-8000-00805f9b34fb";
    private ArrayList<Float[]> accelerometer_data = new ArrayList<Float[]>();
    Float ac_mod;
    private ArrayList<Float[]> gyroscope_data= new ArrayList<Float[]>();
    Float gyr_mod;
    //Bluetooth variables
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private String sBTAddr;
    //variables de la interfaz y de programa
    private Button btStart, btStop, btEmergency;
    private Handler handler = new Handler(Looper.myLooper());
    volatile Boolean  isReading=false;
    private String filePath = "/data/data/com.mseei.hexiwear_read_app/files/DatosUsuario.txt";
    private String curData;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_thresholds);

        btStart = findViewById(R.id.btStartCalibration);
        btStop = findViewById(R.id.btStopCalibration);
        btEmergency = findViewById(R.id.btReturnEmergency);
        btStop.setEnabled(false);

        Bundle extras = getIntent().getExtras();
        sBTAddr = extras.getString("BTAddr");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sBTAddr);
        }
        connectToDevice();
        try {
            checkFile(); //se comprueba si ya se tienen unos umbrales fijados en el archivo y si no los hay se escriben los umbrales por defecto
            showThresholds(); //mostramos un mensaje con los umbrales que ahora mismo estan almacenados
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        bluetoothGatt = bluetoothDevice.connectGatt(this, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                   // showToast(getString(R.string.DeviceConnected));
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    showToast(getString(R.string.DeviceDisconnect));
                }
            }
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    try {
                        displayData_mov(characteristic);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    showToast(getString(R.string.FailRead));
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
    private void startSensorReading() {
        isReading = true;
        btEmergency.setEnabled(false);
        handler.postDelayed(sensorReadingRunnable, 0); //Start read
    }

    private final Runnable sensorReadingRunnable = new Runnable() {
        @Override
        public void run() {
            if(isReading) {
                //Read each characteristic separate
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readCharacteristic(UUID.fromString(MOVEMENT_SERVICE_UUID), UUID.fromString(ACCELEROMETER_UUID));
                    }
                }).start();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        readCharacteristic(UUID.fromString(MOVEMENT_SERVICE_UUID), UUID.fromString(GYROSCOPE_UUID));
                    }
                }).start();
                handler.postDelayed(this, 50); //One read each 50 ms
            }
            else{
                handler.removeCallbacks(sensorReadingRunnable);
            }
        }
    };

    @SuppressLint("MissingPermission")
    private void readCharacteristic(UUID serviceUUID, UUID charUUID) {
        BluetoothGattService service = bluetoothGatt.getService(serviceUUID);
        if (service != null) {
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(charUUID);
            if (characteristic != null) {
                // async read
                bluetoothGatt.readCharacteristic(characteristic);
            }
            else{
                showToast(getString(R.string.NotFound));
            }
        } else {
            showToast(getString(R.string.ServiceNF));
        }
    }
    private float calcularMedia(ArrayList<Float[]> data, String type) {
        float suma = 0;
        float resta_tam = 0;
        float modCoordenada = 0;
        for(Float[] coordenadas: data){
            modCoordenada = (float)Math.sqrt((coordenadas[0]*coordenadas[0])+(coordenadas[1]*coordenadas[1])
                    +(coordenadas[2]*coordenadas[2]));
            if((type == "AC" && modCoordenada > 0.7) || (type == "GR" && modCoordenada > 10.0)){
                suma += modCoordenada;
            }
            else{
                resta_tam += 1;
            }
        }
        if(suma > 0.0f){
            return  (suma/(data.size()-resta_tam));
        }
        else {
            return 0.0f;
        }
    }
    private void displayData_mov(BluetoothGattCharacteristic characteristic) throws IOException {
        if(isReading) {
            UUID charUUID = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            switch (charUUID.toString()) {
                case ACCELEROMETER_UUID:
                        accelerometer_data.add(formatAdapt(data, "AC"));
                    break;
                case GYROSCOPE_UUID:
                        gyroscope_data.add(formatAdapt(data, "GR"));
                    break;
                default:
                    showToast(getString(R.string.UnknowFormart));
                    break;
            }
        }
    }
    private void checkFile() throws IOException {  //deben estar escritos los umbrales para los dos movimientos
        if(!readFromFile(filePath).contains("AC") || !readFromFile(filePath).contains("GR")){
            writeTresholds((float)1.4,(float)180.5);
        }
    }
    private static String readFromFile(String filePath) throws IOException { //Extrae el textp del archivo
        BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }
    private void writeTresholds(float ac_mod, float gyr_mod) throws IOException { //escribe los umbrales en el fichero
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filePath)));
        String Thresh = "AC\n"+" min: " +(float)(ac_mod-(ac_mod*0.2)) +"; max: " +(float)(ac_mod+(ac_mod*0.4)) +"\n"
                +"GR\n" +" min: " +(float)(gyr_mod-(gyr_mod*0.2)) +"; max: " +(float)(gyr_mod+(gyr_mod*0.4)) +"\n";
        String data =curData.split("\\r?\\n")[0] +"\n"+ Thresh;
        showToast(Thresh);
        writer.write(data);
        writer.close();
    }

    private void showThresholds() throws IOException {
        curData = readFromFile(filePath);
        showToast(curData.substring(curData.indexOf('\n')+1));
    }
    public Float[] formatAdapt(byte[] data, String type){
        Float xfloatVal,yfloatVal,zfloatVal;
        switch(type){
            case "AC":
                if (data.length == 6) { // Assuming 3 int16 values
                    final int xintVal = ((int) data[1] << 8) | (data[0] & 0xff);
                    xfloatVal = (float) xintVal / 100;

                    final int yintVal = ((int) data[3] << 8) | (data[2] & 0xff);
                    yfloatVal = (float) yintVal / 100;

                    final int zintVal = ((int) data[5] << 8) | (data[4] & 0xff);
                    zfloatVal = (float) zintVal / 100;

                    Float[] aux = {xfloatVal,yfloatVal,zfloatVal};
                    return aux;

                } else {
                    showToast(getString(R.string.InvalidAcData));
                }
                break;
            case "GR":
                if (data.length == 6) { // Assuming 3 int16 values
                    final int gyroXintVal = ((int) data[1] << 8) | (data[0] & 0xff);
                    xfloatVal = (float) gyroXintVal;

                    final int gyroYintVal = ((int) data[3] << 8) | (data[2] & 0xff);
                    yfloatVal = (float) gyroYintVal;

                    final int gyroZintVal = ((int) data[5] << 8) | (data[4] & 0xff);
                    zfloatVal = (float) gyroZintVal;

                    Float[] aux = {xfloatVal,yfloatVal,zfloatVal};
                    return aux;
                } else {
                    showToast(getString(R.string.InvalidGyrData));
                }
                break;
        }
        return (new Float[3]);
    }
    public void onClickStartCalibration(View v){ //cuando se inicia la calibración se inicia la lectura de sensores
        startSensorReading();
        btStop.setEnabled(true);
        btStart.setEnabled(false);
    }
    @SuppressLint("MissingPermission")
    public void onClickStopCalibration(View v) throws IOException { //se para la lectura de movimentos y se elimnan las llamadas encoladas
        if(isReading){
            ac_mod = calcularMedia(accelerometer_data, "AC");
            gyr_mod = calcularMedia(gyroscope_data, "GR");
            writeTresholds(ac_mod, gyr_mod);
            isReading = false;
            handler.removeCallbacks(sensorReadingRunnable);
            btEmergency.setEnabled(true);
            btStop.setEnabled(false);
            btStart.setEnabled(true);
        }
    }

    @SuppressLint("MissingPermission")
    public void onCLickEmergencyMode (View v) throws IOException { //si se pulsa el boton de emergencia se vuelve a la pantalla de emergencia donde se monitorizan los movimientos
        if(isReading){
            isReading = false;
            btEmergency.setEnabled(true);
            handler.removeCallbacks(sensorReadingRunnable);
        }
        Intent intent = new Intent (this, Emergencia.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish(); //si volvemos a la actividad de emergencia se cierra esta
    }

    public void onClickDefault (View v) throws IOException {
        writeTresholds((float)1.4,(float)180.5); //si se pulsa este boton se reestablecen los umbrales por defecto
    }

}
