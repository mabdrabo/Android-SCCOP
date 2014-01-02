package io.github.mabdrabo.sccop;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Bluetooth {

    Context ApplicationContext;
    String deviceName;
    Method listeningFunction;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    volatile boolean stopWorker;
    boolean isConnected;

    public Bluetooth(MainActivity mainActivity, Context ApplicationContext, String deviceName, Method listeningFunction) {
        this.ApplicationContext = ApplicationContext;
        this.deviceName = deviceName;
        this.listeningFunction = listeningFunction;
        this.isConnected = false;
        if (this.search())
            if (this.connect())
                this.isConnected = true;
//                mainActivity.myListenForData();
//                if (this.listeningFunction == null)
//                    listenForData();
//                else
//                    try {
//                        this.listeningFunction.invoke(this);
//                    } catch (IllegalAccessException e) {
//                        e.printStackTrace();
//                    } catch (InvocationTargetException e) {
//                        e.printStackTrace();
//                    }
    }

//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_bluetooth);
//        this.deviceName = "SCCOP-BT";
//
//        ((Button) findViewById(R.id.open)).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                search();
//                connect();
//                listenForData();
//            }
//        });
//
//    }

    public boolean search() {
        return this.search(this.deviceName);
    }

    boolean search(String deviceName) {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null) {
            Toast.makeText(ApplicationContext, "No bluetooth adapter available", Toast.LENGTH_LONG).show();
            return false;
        }

        if(!mBluetoothAdapter.isEnabled()) {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//            this.ApplicationContext.sstartActivityForResult(enableBluetooth, 0);
        }

        if(mBluetoothAdapter.isEnabled()) {
            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
            if(pairedDevices.size() > 0) {
                for(BluetoothDevice device : pairedDevices) {
                    if(device.getName().equals(deviceName)) {
                        mmDevice = device;
                        Toast.makeText(ApplicationContext, "Bluetooth Device Found: "+device.getName(), Toast.LENGTH_SHORT).show();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    boolean connect() {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); //Standard SerialPortService ID
        try {
            mmSocket = mmDevice.createRfcommSocketToServiceRecord(uuid);
            mmSocket.connect();
            mmOutputStream = mmSocket.getOutputStream();
            mmInputStream = mmSocket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(ApplicationContext, "IO exception while connecting!", Toast.LENGTH_LONG).show();
        }
        if (mmInputStream != null && mmOutputStream != null) {
            Toast.makeText(ApplicationContext, "Connected to " + mmDevice.getName(), Toast.LENGTH_LONG).show();
            return true;
        }
        return false;
    }

    void listenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker) {
                    try {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0; i<bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if(b == delimiter) {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {   // Final data is here
                                            Toast.makeText(ApplicationContext, "received: "+data, Toast.LENGTH_SHORT).show();
                                            String regex = "^([0-9]+)=([0-9]+)$";
                                            Matcher matcher = Pattern.compile(regex).matcher(data);
                                            if (matcher.find()) {
                                                int id = Integer.parseInt(matcher.group(1));
                                                String value = matcher.group(2);
                                                Toast.makeText(ApplicationContext, "ID: "+id+" VALUE: "+value, Toast.LENGTH_LONG).show();
                                            }
//                                            Log.d("Prints", "ID: " + id + " VALUE: " + value);
                                        }
                                    });
                                }else {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex) {
                        Toast.makeText(ApplicationContext, "IO exception while listening for data!", Toast.LENGTH_LONG).show();
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }

    void send(String data) {
        data += "\n";
        try {
            mmOutputStream.write(data.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(ApplicationContext, "IO exception while sending!", Toast.LENGTH_LONG).show();
        }
        Toast.makeText(ApplicationContext, "Data Sent", Toast.LENGTH_SHORT).show();
    }

    void close() {
        stopWorker = true;
        try {
            mmOutputStream.close();
            mmInputStream.close();
            mmSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(ApplicationContext, "IO exception while closing!", Toast.LENGTH_LONG).show();
        }
        Toast.makeText(ApplicationContext, "Closed!", Toast.LENGTH_SHORT).show();
    }

}
