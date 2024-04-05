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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Emergencia extends AppCompatActivity {
    //variables de movimiento
    private static final String MOVEMENT_SERVICE_UUID = "00002000-0000-1000-8000-00805f9b34fb";
    private static final String ACCELEROMETER_UUID = "00002001-0000-1000-8000-00805f9b34fb";
    private static final String GYROSCOPE_UUID = "00002002-0000-1000-8000-00805f9b34fb";

    private ArrayList<Float[]> accelerometer_data= new ArrayList<Float[]>();
    Float ac_mod, ac_min=0.0f, ac_max=0.0f;
    private ArrayList<Float[]> gyroscope_data= new ArrayList<Float[]>();
    Float gyr_mod, gyr_min=0.0f, gyr_max=0.0f;
    //Bluetooth variables
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private String sBTAddr;
    //variables de codigo e interfaz
    private Button btDisconnect;
    private Handler handler = new Handler(Looper.myLooper());
    volatile Boolean  isReading=false;
    private String filePath = "/data/data/com.mseei.hexiwear_read_app/files/DatosUsuario.txt";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergencia);
        btDisconnect = findViewById(R.id.btDisconnected);

        Bundle extras = getIntent().getExtras();
        sBTAddr = extras.getString("BTAddr");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sBTAddr);
        }
        connectToDevice();
        ReadThresholds();  //se leen los umbrales del archivo
        startSensorReading(); //se inicia la lectura de mocimientos
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
                handler.postDelayed(this, 500); //One read each 500 ms
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
    private float calcularMedia(ArrayList<Float[]> data, String type) { //calcula la media de los modulos de todas las muestras almacenadas
        float suma = 0;
        for(Float[] coordenadas: data){
            suma += Math.sqrt((coordenadas[0]*coordenadas[0])+(coordenadas[1]*coordenadas[1])
                    +(coordenadas[2]*coordenadas[2]));
        }
        return  (suma/data.size());
    }

    private void ReadThresholds() { //lectura de los umbrales fijados
        try (BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)))) {
            StringBuilder content = new StringBuilder();
            String line;
            String currentData;
            String acRegex = "AC\\s*min: (\\d+\\.\\d+); max: (\\d+\\.\\d+)";
            String grRegex = "GR\\s*min: (\\d+\\.\\d+); max: (\\d+\\.\\d+)";
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            reader.close();
            currentData=content.toString();
            Matcher acMatcher = Pattern.compile(acRegex).matcher(currentData); //se buscan las medidas de acelerometria y del giroscopio en el texto extraido del archivo de texto
            Matcher grMatcher = Pattern.compile(grRegex).matcher(currentData);
            if (grMatcher.find() && acMatcher.find()) {
                ac_min = Float.parseFloat(acMatcher.group(1));
                ac_max = Float.parseFloat(acMatcher.group(2));
                gyr_min = Float.parseFloat(grMatcher.group(1));
                gyr_max = Float.parseFloat(grMatcher.group(2));
                String Thresh = "AC\n"+" min: " +ac_min +"; max: " +ac_max +"\n"
                        +"GR\n" +" min: " +gyr_min +"; max: " +gyr_max +"\n";
                showToast(Thresh);   //mostramos por pantalla los umbrales finales que se usaran
            }
            else{
                writeTresholds((float)1.4,(float)180.5,currentData); //si no encuentra umbrales en el archivo se escriben en el archivo los umbrales por defecto
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void writeTresholds(float ac_mod, float gyr_mod, String currentData) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filePath)));
        String Thresh = "AC\n"+" min: " +(float)(ac_mod-(ac_mod*0.2)) +"; max: " +(float)(ac_mod+(ac_mod*0.4)) +"\n"
                +"GR\n" +" min: " +(float)(gyr_mod-(gyr_mod*0.2)) +"; max: " +(float)(gyr_mod+(gyr_mod*0.4)) +"\n";
        String data =currentData.split("\\r?\\n")[0] +"\n"+ Thresh;
        showToast(Thresh); //Se escriben los umbrales y se muestran los umbrales fijados
        writer.write(data);
        writer.close();
    }
    @SuppressLint("MissingPermission")
    private void displayData_mov(BluetoothGattCharacteristic characteristic) throws IOException {
        if(isReading) {
            UUID charUUID = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            switch (charUUID.toString()) {
                case ACCELEROMETER_UUID:
                    if (accelerometer_data.size() < 5) { //si hay menos de 5 muestras se añade
                        accelerometer_data.add(formatAdapt(data, "AC"));
                    } else {
                        for (int i = 0; i < 4; i++) { //si tenemos mas de 5 muestra se elimina la que entro primera y se añade la nueva
                            accelerometer_data.set(i, accelerometer_data.get(i + 1));
                        }
                        accelerometer_data.set(4, formatAdapt(data, "AC"));
                    }
                    break;
                case GYROSCOPE_UUID:
                    if (gyroscope_data.size() < 5) {
                        gyroscope_data.add(formatAdapt(data, "GR"));
                    } else {
                        for (int i = 0; i < 4; i++) {
                            gyroscope_data.set(i, gyroscope_data.get(i + 1));
                        }
                        gyroscope_data.set(4, formatAdapt(data, "GR"));
                    }
                    break;
                default:
                    showToast(getString(R.string.UnknowFormart));
                    break;
            }
            if (accelerometer_data.size() >= 5 && gyroscope_data.size() >= 5) { //una vez que se tienen 5 muestras de cada sensor se calculan las medias de los modulos
                //showToast("AC: " + ac_mod +"GR: "+ gyr_mod);
                ac_mod = calcularMedia(accelerometer_data, "AC");
                gyr_mod = calcularMedia(gyroscope_data, "GR");
                if (ac_mod > ac_min && ac_mod < ac_max &&  //si la media de las muestras esta dentro de los umbrales y lanzamos la pantalla de confirmar la emergencia
                        gyr_mod > gyr_min && gyr_mod < gyr_max) {
                    handler.removeCallbacksAndMessages(sensorReadingRunnable);
                    Intent intent = new Intent(this, Confirmar.class);
                    intent.putExtra("BTAddr", sBTAddr);
                    startActivity(intent);
                    finish();
                }
            }
        }
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

    @SuppressLint("MissingPermission")
    public void onClickDisconnectBt  (View v){ //si se pulsa desconectar se abre al menu de opciones
        if(isReading){
            isReading = false;
            handler.removeCallbacks(sensorReadingRunnable);
        }
        Intent intent = new Intent (this, Opciones.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish();
    }

    public void onClickCalibration(View v){ //si se pulsa el boton de calibrar
        //nueva clase que permite que se fijen umbrales segun el movimiento que realcie el usuario
        Intent intent = new Intent (this, Umbrales.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish();
    }

    protected void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

}
