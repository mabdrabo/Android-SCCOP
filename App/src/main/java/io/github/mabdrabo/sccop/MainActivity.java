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

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class MainActivity extends ActionBarActivity {

    private final String[] namesList = new String[] {"RPM", "Speed", "Temp"};
    private final String[] unitsList = new String[] {"1000/min", "Km/s", "C"};
    private String[] valuesList = new String[namesList.length];
    private Bluetooth bluetooth;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ToggleButton toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
//                bluetooth = new Bluetooth(getApplicationContext(), "SCCOP-BT");
//                if (bluetooth.isConnected)
//                    myListenForData();

                new updateOnlineDB().execute("tessttt");

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


    private class updateOnlineDB extends AsyncTask<String, Void, String> {
        private static final String TAG = "SCCOP";

        @Override
        protected String doInBackground(String... params) {
            //Here you have to make the loading / parsing tasks
            //Don't call any UI actions here. For example a Toast.show() this will couse Exceptions
            // UI stuff you have to make in onPostExecute method

            DefaultHttpClient client = new DefaultHttpClient();
//            HttpGet get = new HttpGet("http://sccop.herokuapp.com/mobile/update");
            HttpPost post = new HttpPost("http://sccop.herokuapp.com/mobile/update");

            JSONObject holder = new JSONObject();
            JSONObject projectObj = new JSONObject();
            String name = params[0];

            try {
                Log.d(TAG, "attempting to post project");
                projectObj.put("name", name);
                Log.d(TAG, "projectName: " + name);
                holder.put("project", projectObj);
                Log.e("Event JSON", "Event JSON = "+ holder.toString());
                StringEntity se = new StringEntity(holder.toString());
                post.setEntity(se);
                post.setHeader("Accept", "application/json");
                post.setHeader("Content-Type","application/json");
//                get.setHeader("Content-Type","application/json");

            } catch (UnsupportedEncodingException e) {
                Log.e("Error: UnsupportedEncodingException",""+e);
                e.printStackTrace();
            } catch (JSONException js) {
                Log.e("Error: JSONException",""+js);
                js.printStackTrace();
            }

            HttpResponse response;

            try {
                response = client.execute(post);
//                response = client.execute(get);
            } catch (ClientProtocolException e) {
                e.printStackTrace();
                Log.e("ClientProtocol",""+e);
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("IO",""+e);
                return null;
            }

            HttpEntity entity = response.getEntity();

            if (entity != null) {
                try {
                    String result = EntityUtils.toString(entity);
                    entity.consumeContent();
                    return result;
                } catch (IOException e) {
                    Log.e("IO E",""+e);
                    e.printStackTrace();
                }
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
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();
            Log.e("SCCOP", result);
        }
    }
}
