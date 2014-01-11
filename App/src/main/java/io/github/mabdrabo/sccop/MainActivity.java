package io.github.mabdrabo.sccop;

import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.GridView;
import android.widget.Toast;
import android.widget.ToggleButton;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity {

    private final String[] namesList = new String[] {"RPM", "Speed", "Temp"};
    private final String[] unitsList = new String[] {"1000/min", "Km/s", "C"};
    private String[] valuesList = new String[namesList.length];
    private Bluetooth bluetooth;
    private final String API_URL = "http://sccop.herokuapp.com/api/log/add/?";
    private String USERNAME = "01005574388";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ToggleButton toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                bluetooth = new Bluetooth(getApplicationContext(), "SCCOP-BT");
                if (bluetooth.isConnected) {
                    myListenForData();
                } else {
                    Toast.makeText(getApplicationContext(), "Please make sure Bluetooth is Turned On.", Toast.LENGTH_LONG).show();
                }
            }
        });

    }


    @Override
    protected void onResume() {
        super.onResume();
        GridView gridView = (GridView) findViewById(R.id.gridView);
        gridView.setAdapter(new GridItemAdapter(this, namesList, valuesList, unitsList) {
        });
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
//                                            Toast.makeText(getApplicationContext(), "received: "+data, Toast.LENGTH_SHORT).show();
                                            String regex = "^([0-9]+)=([0-9]+)$";
                                            Matcher matcher = Pattern.compile(regex).matcher(data);
                                            if (matcher.find()) {
                                                int id = Integer.parseInt(matcher.group(1));
                                                String value = matcher.group(2);
                                                Toast.makeText(getApplicationContext(), "ID: "+id+" VALUE: "+value, Toast.LENGTH_LONG).show();
                                                valuesList[id] = value;
                                                onResume();
                                                if (id == valuesList.length-1) {
                                                    new Api().execute(namesList, valuesList);
                                                }
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


    private class Api extends AsyncTask<String[], Void, String> {
        private static final String TAG = "SCCOP";

        @Override
        protected String doInBackground(String[]... params) {
            //Here you have to make the loading / parsing tasks
            //Don't call any UI actions here. For example a Toast.show() this will couse Exceptions
            // UI stuff you have to make in onPostExecute method

            DefaultHttpClient client = new DefaultHttpClient();
            HttpGet get;
            try {
                StringBuilder urlParams = new StringBuilder();
                for (int i=0; i<params[0].length; i++) {
                    String p = params[0][i].toLowerCase() + "=" + params[1][i];
                    Log.e(TAG, "p"+i+": " + p);
                    urlParams.append(p + "&");
                }
                urlParams.append("username=" + USERNAME);
                String fullUrl = API_URL + urlParams.toString();
                Log.e(TAG, "URL: " + fullUrl);
                get = new HttpGet(fullUrl);
                get.setHeader("Content-Type","application/json");

                HttpResponse response;
                response = client.execute(get);

                if (response.getEntity() != null) {
                    try {
                        // Read the content stream
                        InputStream instream = response.getEntity().getContent();

                        // convert content stream to a String
                        String resultString= convertStreamToString(instream);
                        instream.close();
                        return resultString;

                    } catch (IOException e) {
                        Log.e(TAG, "IO E "+e);
                        e.printStackTrace();
                        return e.toString();
                    }
                }

            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "Error: UnsupportedEncodingException "+e);
                e.printStackTrace();
                return e.toString();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.e(TAG, "ClientProtocol "+e);
                return e.toString();
            } catch (IOException e) {
                e.printStackTrace();
                Log.e(TAG, "IO "+e);
                return e.toString();
            }

            return "empty";
        }

        @Override
        protected void onPreExecute() {
            // This method will called during doInBackground is in process
            // Here you can for example show a ProgressDialog
            Toast.makeText(getApplicationContext(), "processing", Toast.LENGTH_SHORT).show();
        }

        @Override
        protected void onPostExecute(String result) {
            // onPostExecute is called when doInBackground finished
            // Here you can for example fill your Listview with the content loaded in doInBackground method
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();

            Log.e("SCCOP", result);
        }
    }


    private static String convertStreamToString(InputStream is) {

        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();

        String line = null;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return sb.toString();
    }
}
