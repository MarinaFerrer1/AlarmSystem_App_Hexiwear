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
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Grabacion extends AppCompatActivity { //esta actividad permite grabar movimientos y guardarlos en un archivo de txto (1: acelerómetro, 2: gyroscopo)
    //variables de movimiento
    private static final String MOVEMENT_SERVICE_UUID = "00002000-0000-1000-8000-00805f9b34fb";
    private static final String ACCELEROMETER_UUID = "00002001-0000-1000-8000-00805f9b34fb";
    private static final String GYROSCOPE_UUID = "00002002-0000-1000-8000-00805f9b34fb";

    private ArrayList<byte []> accelerometer_data = new ArrayList<byte[]>();
    private ArrayList<byte []> gyroscope_data = new ArrayList<byte[]>();
    private ArrayList<String> accelerometer_data_time = new ArrayList<String>();
    private ArrayList<String> gyroscope_data_time = new ArrayList<String>();
    //Bluetooth variables
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    //variables de la interfaz
    private Button btRecord, btStop, btDisconnect, btTag;
    private EditText etMovementTag;
    //variables del codigo
    private Handler handler = new Handler(Looper.myLooper());
    volatile Boolean  isReading=false;
    private String sBTAddr,movementTag="";
    FileOutputStream fOutDataLog;
    SimpleDateFormat dateAndTimeFormat = new SimpleDateFormat("yyyyMMdd_HH:mm:ss:SSS", Locale.UK);

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conexion);

        btRecord = findViewById(R.id.btRecord);
        btStop = findViewById(R.id.btStop);
        btDisconnect = findViewById(R.id.btRegresar);
        btTag = findViewById(R.id.btSendTag);
        etMovementTag = findViewById(R.id.etTag);
        btStop.setEnabled(false);

        Bundle extras = getIntent().getExtras();
        sBTAddr = extras.getString("BTAddr");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sBTAddr);
        }
        connectToDevice();
    }

    @SuppressLint("MissingPermission")
    private void connectToDevice() {
        bluetoothGatt = bluetoothDevice.connectGatt(this, true, new BluetoothGattCallback() {
            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    //showToast(getString(R.string.DeviceConnected));
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    showToast(getString(R.string.DeviceDisconnect));
                }
            }
            @Override   //lectura de caracteristicas del dispositivo HEXI
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    try {
                        displayData_grab(characteristic);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    showToast(getString(R.string.FailRead));
                }
            }
        });
    }

    public void onClickRead(View v){ //si se pulsa  el boton de grabación se inicia la lectura de los sensores
        btStop.setEnabled(true);
        btRecord.setEnabled(false);
        startSensorReading();
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
        btTag.setEnabled(false);
        btDisconnect.setEnabled(false);
        handler.postDelayed(sensorReadingRunnable, 0); //Start read
    }

    private final Runnable sensorReadingRunnable = new Runnable() {
        @Override
        public void run() { //se leen los 3 sensores que se han decidido (presio, acellerómetro y giroscopo)
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


    private void displayData_grab(BluetoothGattCharacteristic characteristic) throws IOException {
        UUID charUUID = characteristic.getUuid();   //se guardan los datos recogidos de los distintos sensores y tambien el instante de tiempo cuando se recoge la muestra
        String currentTime = dateAndTimeFormat.format(new Date(System.currentTimeMillis()));
        byte[] data = characteristic.getValue();
        switch (charUUID.toString()) {
            case ACCELEROMETER_UUID:
                accelerometer_data.add(data);
                accelerometer_data_time.add(currentTime);
                break;
            case GYROSCOPE_UUID:
                gyroscope_data.add(data);
                gyroscope_data_time.add(currentTime);
                break;
            default:
                showToast(getString(R.string.UnknowFormart));
                break;
        }

    }
    public void createLogFile() { //se crea un nuevo archivo con un nombre predeterminado al que podemos añadir una etiqueta para el arhcivo del movimiento
        String sDateAndTime = dateAndTimeFormat.format(new Date());
        File filePath, fileDataLog;
        String sFileName = "/" + sDateAndTime + "_DataLog_" + movementTag + ".txt";

        filePath = getExternalFilesDir(null);
        showToast("Archivo almacenado en: "+filePath.toString());
        try {
            fileDataLog = new File(filePath, sFileName);
            fOutDataLog = new FileOutputStream(fileDataLog, true);
        } catch (IOException e) {
            showToast(getString(R.string.ErrorFile));
            isReading=false;
            btTag.setEnabled(true);
            btDisconnect.setEnabled(true);
        }
    }
    public String formatAdapt(byte[] data, String type){ //adaptamos el formato de los datos leidos de los sensores
        float xfloatVal,yfloatVal,zfloatVal;
        switch(type){
            case "AC":
                if (data.length == 6) { // Assuming 3 int16 values
                    final int xintVal = ((int) data[1] << 8) | (data[0] & 0xff);
                    xfloatVal = (float) xintVal / 100;

                    final int yintVal = ((int) data[3] << 8) | (data[2] & 0xff);
                    yfloatVal = (float) yintVal / 100;

                    final int zintVal = ((int) data[5] << 8) | (data[4] & 0xff);
                    zfloatVal = (float) zintVal / 100;

                    return(" 1: " + xfloatVal + "," + yfloatVal + "," + zfloatVal +"\n");
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

                    return (" 2: " + xfloatVal + "," + yfloatVal + "," + zfloatVal +"\n");
                } else {
                    showToast(getString(R.string.InvalidGyrData));
                }
                break;
        }
        return "";
    }
    @SuppressLint("MissingPermission")
    public void onClickStop(View v) throws IOException { //Cuando se pulsa el boton de parar grabacion se escriben todos los datos en el fichero
        if(isReading){
            isReading = false;
            btTag.setEnabled(true);
            createLogFile();
            for (int i = 0; i< accelerometer_data.size(); i++){
                fOutDataLog.write((accelerometer_data_time.get(i)+formatAdapt(accelerometer_data.get(i),"AC")).getBytes());
            }
            for (int i = 0; i< gyroscope_data.size(); i++){
                fOutDataLog.write((gyroscope_data_time.get(i)+formatAdapt(gyroscope_data.get(i),"GR")).getBytes());
            }
            handler.removeCallbacks(sensorReadingRunnable);
            fOutDataLog.close();  //Se cierra el archivo
            btDisconnect.setEnabled(true);
            btRecord.setEnabled(true);
            btStop.setEnabled(false);
        }
    }

    @SuppressLint("MissingPermission")
    public void onClickReturn (View v) throws IOException { //si se pulsa el boton para volver al menu inicial se lleva al menu de opciones donde se elige el modo de la app (grabar o emergencia)
        if(isReading){
            isReading = false;
            btTag.setEnabled(true);
            handler.removeCallbacks(sensorReadingRunnable);
            fOutDataLog.close();
        }
        Intent intent = new Intent (this, Opciones.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish(); //se cierra esta actividad
    }

    public void onClickSendTag (View v) throws IOException { //si se pulsa para enviar una etiqueta de movimiento se almacena en una variable que luego se añadira al nombre del archivo (solo permite numeros y letras)
        if(!isReading){
            movementTag=String.valueOf(etMovementTag.getText()).trim();
            Matcher matcher = Pattern.compile("^[a-zA-Z0-9]+$").matcher(movementTag);
            if(!matcher.matches()){
                movementTag="";
                showToast(getString(R.string.IncorrectData));
            }
            else {
                btTag.setEnabled(false);
            }
        }
    }
}
