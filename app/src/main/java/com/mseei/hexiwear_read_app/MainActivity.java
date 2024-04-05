package com.mseei.hexiwear_read_app;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {
    final static String sDEVICE_NAME = "HEXIWEAR";
    //Control de las variables de interfaz
    private Button btEnableBT,btScan,btConnect, btEnterData;
    private TextView tvDevName,tvDevAddress;
    private EditText etName,etSurname, etNumber;

    //Bluetooth variables
    BluetoothLeScanner scannerBT;
    BluetoothAdapter adapterBT;
    //variables del programa
    String sBTAddr,sBTName;
    boolean bScanning;
    int iCount;
    private final Handler handler = new Handler(Looper.myLooper());
    private ActivityResultLauncher<Intent> activityResultLauncher;
    private static final int REQUEST_CALL_PHONE_PERMISSION = 1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //control access
        btEnableBT = findViewById(R.id.btEnable);
        btScan = findViewById(R.id.btScan);
        btConnect = findViewById(R.id.btConnecct);
        tvDevName = findViewById(R.id.tvDeviceName);
        tvDevAddress = findViewById(R.id.tvDevAaddress);
        btEnterData = findViewById(R.id.btUserData);
        etName = findViewById(R.id.etUserName);
        etSurname = findViewById(R.id.etUserSurname);
        etNumber = findViewById(R.id.etContactPhone);

        onClickEnableBluetooth(btEnableBT); //Activamos el bluetooth
        btScan.setEnabled(false);
        fillUserData();  //se intentan rellenar los campos de datos de usuario

        bScanning = false;
        iCount = 0;
        runOnUiThread(new Runnable() { //Escanemos para encontrar el dispositivo al que conectarnos
            @Override
            public void run() {
                if (bScanning){
                    iCount++;
                    if(iCount == 4) { //Si la cuenta llega a 4s, se inicia un nuevo escaneo
                        onClickScan(btScan);
                    }
                }else{
                    iCount =0 ;
                }
                handler.postDelayed(this,1000);
            }
        });
    }

    public void fillUserData(){ //rellenar los campos de datos de usuario
        String filePath = "/data/data/com.mseei.hexiwear_read_app/files/DatosUsuario.txt";
        try {
            String currentData = readFromFile(filePath); //intentamos obtener el texto del archivo en el que se almacenan estos datos
            Matcher matcherName = Pattern.compile("Nombre: (\\w+)").matcher(currentData); //con el uso de regex nos aseguramos que el formato de los datos escritos en el archivo es correcto
            Matcher matcherSurname = Pattern.compile("Apellido: (\\w+)").matcher(currentData);
            Matcher matcherNum = Pattern.compile("Numero de emergencia: (\\d+)").matcher(currentData);
            if(matcherName.find() && matcherSurname.find() && matcherNum.find()){ //si los 3 campos estan rellenos y con formato correcto se muestran los datos por pantalla
                etName.setText(matcherName.group(1));
                etSurname.setText(matcherSurname.group(1));
                etNumber.setText(matcherNum.group(1));
                btScan.setEnabled(true);
            }
            else{
                showToast(getString(R.string.RequiredUserData));
                btScan.setEnabled(false); //si no hay datos o si el formato no es correcto no permitimos escanear dispositivos ni seguir con el uso de la aplicacion
            }
        } catch (IOException e) {
            showToast(getString(R.string.RequiredUserData));
            btScan.setEnabled(false);
            e.printStackTrace();
        }
    }
    private void showToast(String message) { //este metodo nos permite mostrar mensajes por pantalla
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode == REQUEST_CALL_PHONE_PERMISSION) {
            // Verificar los resultados de la solicitud de permisos aquí
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                finishAffinity(); //si no se conceden los permisos se debe cerrar la aplicacion
            }
        }
    }
    public void onClickEnableBluetooth(View v){ //metodo que se lanza cuando se pulsa el boton de habilitar bluetooth
        activityResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>(){ //Activamos la conexion bluetooth si no lo esta para conectarnos al dispositivo
                    public void onActivityResult(ActivityResult result){
                        btEnableBT.setEnabled(result.getResultCode() != Activity.RESULT_OK);
                        if(result.getResultCode() == Activity.RESULT_OK){
                            btScan.setEnabled(true);
                            comprobarPermisosBT();
                        }
                    }
                }
        );
        adapterBT = BluetoothAdapter.getDefaultAdapter();
        if(adapterBT != null){
            btEnableBT.setEnabled(!adapterBT.isEnabled());
            btScan.setEnabled(adapterBT.isEnabled());
            if(!adapterBT.isEnabled()){
                Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE); //ask for permission
                activityResultLauncher.launch(enableBTIntent);
            }
            comprobarPermisosBT(); //comprobamos si tenemos permisos
        }else{
            btEnableBT.setEnabled(false);
            Toast toast = Toast.makeText(getApplicationContext(),getString(R.string.NO_BLUETOOTH),Toast.LENGTH_LONG);
        }
    }
    private boolean comprobarPermisosBT(){ //si no tenemos los permisos se solicitan
        if(ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.CALL_PHONE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, REQUEST_CALL_PHONE_PERMISSION);
            return false;
        }
        return true;
    }
    @SuppressLint("MissingPermission")
    public void onClickScan(View v){  //metodo que se activa cuando se pulsa el boton de iniciar escaneo de dispositivos
        if(bScanning){ //Si esta escaneando se busca si se obtiene el nombre y la direccion y si no se esta escaneando se activa el escaneo
            bScanning = false;
            scannerBT.stopScan(mScanCallBack);

            if(sBTName != null){
                tvDevName.setText(sBTName);
                tvDevAddress.setText(sBTAddr);
                btConnect.setEnabled(true);
            }
        }else{
            bScanning = true;
            btConnect.setEnabled(false);
            sBTName = null;
            scannerBT = adapterBT.getBluetoothLeScanner();
            scannerBT.startScan(mScanCallBack);
        }
    }

    private final ScanCallback mScanCallBack = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            @SuppressLint("MissingPermission")
            String sNameAux = device.getName();
            try {
                if (sNameAux.compareTo(sDEVICE_NAME) == 0) {
                    sBTName = sNameAux;
                    sBTAddr = device.getAddress();
                }
            }catch(NullPointerException e){}
        }
    };

    private static void writeToFile(String filePath, String data) throws IOException { //metodo para escribir en un fichero
        BufferedWriter writer = new BufferedWriter(new FileWriter(new File(filePath)));
        writer.write(data);
        writer.close();
    }

    private static String readFromFile(String filePath) throws IOException { //lee y guarda todo el texto de un fichero
        BufferedReader reader = new BufferedReader(new FileReader(new File(filePath)));
        StringBuilder content = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        reader.close();
        return content.toString();
    }

    private static String modifyFirstLine(String currentData, String newData) {  //si cambian los datos de usuario modifica solo la primera
        String[] lines = currentData.split("\\r?\\n");                     //linea que es donde se almacenan los datos de usuario sin mdificar el resto de contenido (umbrales)
        // Modificar la primera línea
        if (lines.length > 0) {
            lines[0] = newData;
        }
        // Unir las líneas de nuevo
        return String.join("\n", lines);
    }
    public void onClickConnect(View v){  //cuando se pulsa el boton conectar se nos dirige al menu de opciones para iniciar la aactividad que queramos hacer en esta aplicacion
        Intent intent = new Intent (this, Opciones.class);
        intent.putExtra("BTAddr",sBTAddr); //le pasamos la direccion del dispositvo HEXI para poder conectarnos en otras actividades
        startActivity(intent); //la activity principal no se cierra porque queremos volver a ella
    }

    public void onClickUserData(View v){  //se lanza cuando pulsamos el boton para registrar los datos de usuario
       String name = String.valueOf(etName.getText()).trim();  //Se recogen los datos escritos en la interfaz
       String surName = String.valueOf(etSurname.getText()).trim();
       Matcher matcherName = Pattern.compile("^[a-zA-Z\\s]+$").matcher(name); //comprobamos que el formato introducido sea el correcto
       Matcher matcherSurname = Pattern.compile("^[a-zA-Z\\s]+$").matcher(surName);
       String phone = String.valueOf(etNumber.getText()).trim();
       Matcher matcherPhone = Pattern.compile("^[0-9]+$").matcher(phone);
       if(matcherPhone.matches() && matcherName.matches() && matcherSurname.matches()){  //si el formato es correcto se modifica el fichero con los nuevos datos (solo es posible almacenar los datos de una persona porque es una aplicacion personal)
           String filePath = "/data/data/com.mseei.hexiwear_read_app/files/DatosUsuario.txt";
           String dataToWrite = "Nombre: " + name + " ; Apellido: " + surName + " ; Numero de emergencia: " + phone+"\n";
           try {
               File file = new File(filePath);
               if (!file.exists()) {
                   file.createNewFile();
               }
               String currentData = readFromFile(filePath);
               writeToFile(filePath, modifyFirstLine(currentData,dataToWrite));
               fillUserData();
               btScan.setEnabled(true);
           } catch (IOException e) {
               e.printStackTrace();
           }
       }
       else{
           showToast(getString(R.string.IncorrectData));
       }
    }
}