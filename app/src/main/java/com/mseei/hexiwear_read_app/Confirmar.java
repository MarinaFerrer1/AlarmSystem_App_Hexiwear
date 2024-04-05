package com.mseei.hexiwear_read_app;


import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Confirmar extends AppCompatActivity {
    //variables de movimiento
    private static final String MOVEMENT_SERVICE_UUID = "00002000-0000-1000-8000-00805f9b34fb";
    private static final String ACCELEROMETER_UUID = "00002001-0000-1000-8000-00805f9b34fb";
    private static final String GYROSCOPE_UUID = "00002002-0000-1000-8000-00805f9b34fb";
    private ArrayList<Float[]> accelerometer_data= new ArrayList<Float[]>();
    Float ac_mod,ac_min=0.0f, ac_max=0.0f;
    private ArrayList<Float[]> gyroscope_data= new ArrayList<Float[]>();
    Float gyr_mod, gyr_min=0.0f, gyr_max=0.0f;
    //Bluetooth variables
    private String sBTAddr;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    //variables de la interfaz y del programa
    private Button btStopEm;
    private Handler handler = new Handler(Looper.myLooper());
    volatile Boolean  isReading=false;
    private String emergencyNum;
    private String filePath = "/data/data/com.mseei.hexiwear_read_app/files/DatosUsuario.txt";
    private static MediaPlayer mediaPlayer,mediaPlayer1;

    private static ImageView etImage;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_confirmar);
        btStopEm = findViewById(R.id.btStopEmergencia);
        etImage = findViewById(R.id.etImage);

        Bundle extras = getIntent().getExtras();
        sBTAddr = extras.getString("BTAddr");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(sBTAddr);
        }
        connectToDevice();
        iniciarAlarma(this); //se inicia una alarma para avisar que se esta confirmando la emergencia
        ReadThresholdsNum();  //se leen los umbrales y se hace lo mismo que en emergencia porque se debe confirmar el movimiento
        startSensorReading();
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
            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    try {
                        displayData_conf(characteristic);
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
        handler.postDelayed(sensorReadingRunnable,10); //Start read
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
                handler.postDelayed(this, 400); //One read each 1s
            }
            else{
                handler.removeCallbacks(sensorReadingRunnable);
            }
        }
    };
    private void ReadThresholdsNum() {
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
            Matcher acMatcher = Pattern.compile(acRegex).matcher(currentData);
            Matcher grMatcher = Pattern.compile(grRegex).matcher(currentData);
            Matcher matcherNum = Pattern.compile("Numero de emergencia: (\\d+)").matcher(currentData);
            if(matcherNum.find()){
                emergencyNum=matcherNum.group(1);
            }
            if (grMatcher.find() && acMatcher.find()) {
                ac_min = Float.parseFloat(acMatcher.group(1));
                ac_max = Float.parseFloat(acMatcher.group(2));
                gyr_min = Float.parseFloat(grMatcher.group(1));
                gyr_max = Float.parseFloat(grMatcher.group(2));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    public static void iniciarAlarma(Context context) { //metodo que inicia la alarma
        mediaPlayer = MediaPlayer.create(context, R.raw.alarm_clock);
        mediaPlayer.start();
    }
    private static void stopAlarmSound() { // metodo que detiene la alarma
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
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
        for(Float[] coordenadas: data){
            suma += Math.sqrt((coordenadas[0]*coordenadas[0])+(coordenadas[1]*coordenadas[1])
                    +(coordenadas[2]*coordenadas[2]));
        }
        if(suma > 0.0f){
            return  (suma/data.size());
        }
        else {
            return 0.0f;
        }
    }

    private void displayData_conf(BluetoothGattCharacteristic characteristic) throws IOException {
        TelephonyManager telephonyManager;
        PhoneStateListener phoneStateListener;
        if(isReading) {
            UUID charUUID = characteristic.getUuid();
            byte[] data = characteristic.getValue();
            switch (charUUID.toString()) {
                case ACCELEROMETER_UUID:
                    if (accelerometer_data.size() < 5) {
                        accelerometer_data.add(formatAdapt(data, "AC"));
                    } else {
                        for (int i = 0; i < 4; i++) {
                            accelerometer_data.set(i, accelerometer_data.get(i + 1));
                        }
                        accelerometer_data.set(4, formatAdapt(data, "AC"));
                    }
                    break;
            case GYROSCOPE_UUID:
                if(gyroscope_data.size() < 5){
                    gyroscope_data.add(formatAdapt(data,"GR"));
                }
                else{
                    for (int i=0 ; i < 4; i++){
                        gyroscope_data.set(i, gyroscope_data.get(i+1));
                    }
                    gyroscope_data.set(4,formatAdapt(data,"GR"));
                }
                break;
                default:
                    showToast(getString(R.string.UnknowFormart));
                    break;
            }
            if (accelerometer_data.size() >= 5 && gyroscope_data.size() >= 5) {
                //showToast("AC: " +ac_mod +"GR: "+gyr_mod);
                ac_mod = calcularMedia(accelerometer_data, "AC");
                gyr_mod = calcularMedia(gyroscope_data, "GR");
                if (ac_mod > ac_min && ac_mod < ac_max &&
                        gyr_mod > gyr_min && gyr_mod < gyr_max) {
                    handler.removeCallbacks(sensorReadingRunnable);
                    startCall();
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            finish();
                            Intent intent = new Intent(Confirmar.this, Emergencia.class);
                            intent.putExtra("BTAddr", sBTAddr);
                            startActivity(intent);
                            finish();
                        }
                    });
                    finish();
                } else {
                    handler.removeCallbacks(sensorReadingRunnable);
                    noEmergency();
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void startCall() {
        stopAlarmSound();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            String phoneNumber = "tel:" + emergencyNum;
            Intent dial = new Intent(Intent.ACTION_CALL, Uri.parse(phoneNumber));
            startActivity(dial);
            finish();
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

    public void StartAlarmDisabled(){
        mediaPlayer1 = MediaPlayer.create(this, R.raw.alarma_desactivada);
        mediaPlayer1.start();
    }
    public void noEmergency(){
        stopAlarmSound();
        StartAlarmDisabled();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                etImage.setImageResource(R.drawable.ambulancia_no);
            }
        });
        Intent intent = new Intent (this, Emergencia.class);
        intent.putExtra("BTAddr",sBTAddr);
        startActivity(intent);
        finish();
    }
    @SuppressLint("MissingPermission")
    public void onClickStopEmerg(View v){   //si se para la emergencia se muestra una foto indicando que se ha parado y se vuelve a la actividad de monitorizar movimientos
        noEmergency();
    }


}
