package io.github.mabdrabo.sccop;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.GridView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity {

    private final String[] namesList = new String[] {"RPM", "Speed", "Temp", "Throt"};
    private final String[] unitsList = new String[] {"1000/min", "Km/s", "C", ""};
    private String[] valuesList = new String[namesList.length];
    private Bluetooth bluetooth;
    MainActivity mainActivity;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainActivity = this;
        ToggleButton toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
//                try {
//                    Method myListeningFunction = MainActivity.class.getMethod("myListenForData", null);
//                    bluetooth = new Bluetooth(mainActivity, getApplicationContext(), "SCCOP-BT", myListeningFunction);
//                } catch (NoSuchMethodException e) {
//                    e.printStackTrace();
//                    Toast.makeText(getBaseContext(), "No such method exception!", Toast.LENGTH_LONG);
//                    bluetooth = new Bluetooth(mainActivity, getApplicationContext(), "SCCOP-BT", null);
//                }
                bluetooth = new Bluetooth(mainActivity, getApplicationContext(), "SCCOP-BT", null);
                if (bluetooth.isConnected)
                    myListenForData();
            }
        });

//        Intent intent = new Intent().setClass(this, Bluetooth.class);
//        startActivity(intent);
    }


    @Override
    protected void onResume() {
        super.onResume();
        GridView gridView = (GridView) findViewById(R.id.gridView);
        gridView.setAdapter(new GridItemAdapter(this, namesList, valuesList, unitsList) {});

    }


    public void myListenForData() {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        bluetooth.stopWorker = false;
        bluetooth.readBufferPosition = 0;
        bluetooth.readBuffer = new byte[1024];
        bluetooth.workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !bluetooth.stopWorker) {
                    try {
                        int bytesAvailable = bluetooth.mmInputStream.available();
                        if(bytesAvailable > 0) {
                            byte[] packetBytes = new byte[bytesAvailable];
                            bluetooth.mmInputStream.read(packetBytes);
                            for(int i=0; i<bytesAvailable; i++) {
                                byte b = packetBytes[i];
                                if(b == delimiter) {
                                    byte[] encodedBytes = new byte[bluetooth.readBufferPosition];
                                    System.arraycopy(bluetooth.readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    bluetooth.readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {   // Final data is here
                                            Toast.makeText(getApplicationContext(), "received: "+data, Toast.LENGTH_SHORT).show();
                                            String regex = "^([0-9]+)=([0-9]+)$";
                                            Matcher matcher = Pattern.compile(regex).matcher(data);
                                            if (matcher.find()) {
                                                int id = Integer.parseInt(matcher.group(1));
                                                String value = matcher.group(2);
                                                Toast.makeText(getApplicationContext(), "ID: "+id+" VALUE: "+value, Toast.LENGTH_LONG).show();
                                                valuesList[id] = value;
                                                onResume();
                                            }
                                        }
                                    });
                                }else {
                                    bluetooth.readBuffer[bluetooth.readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex) {
                        Toast.makeText(getBaseContext(), "IO exception while listening for data!", Toast.LENGTH_LONG);
                        bluetooth.stopWorker = true;
                    }
                }
            }
        });

        bluetooth.workerThread.start();
    }
}
