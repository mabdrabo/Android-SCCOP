package io.github.mabdrabo.sccop;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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


public class MainActivity extends ActionBarActivity implements LocationListener {

    private final String[] namesList = new String[] {"RPM", "Speed", "Temp", "Throttle", "Fuel", "Engine"};
    private final String[] unitsList = new String[] {"1/min", "Km/s", "˚C", "%", "%", "%"};
    private String[] valuesList = new String[namesList.length];
    private Bluetooth bluetooth;
    private final String UPDATE_STATE_URL = "http://sccop.herokuapp.com/api/update/state/?";
    private final String UPDATE_LOCATION_URL = "http://sccop.herokuapp.com/api/update/location/?";
    private String USERNAME = "mabdrabo";
    private static final String TAG = "SCCOP";
    ToggleButton toggleButton;

    private LocationManager locationManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toggleButton = (ToggleButton)findViewById(R.id.toggleButton);
        toggleButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (toggleButton.isChecked()) {
                    bluetooth = new Bluetooth(getApplicationContext(), "SCCOP-BT");
                    if (bluetooth.isConnected) {
                        myListenForData();
                    } else {
                        Toast.makeText(getApplicationContext(), "Please make sure Bluetooth is Turned On.", Toast.LENGTH_LONG).show();
                        toggleButton.setChecked(false);
                    }
                } else {
                    if (bluetooth != null) {
                        bluetooth.close();
                        bluetooth = null;
                    }
                }
            }
        });
        String[] loc = getLocation();
        Log.e(TAG, loc[0] + " ,, " + loc[1]);
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
                        if (bluetooth == null)
                            break;
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
                                            String regex = "^([0-9]+)=([0-9]+)$";
                                            Matcher matcher = Pattern.compile(regex).matcher(data);
                                            if (matcher.find()) {
                                                int id = Integer.parseInt(matcher.group(1));
                                                String value = matcher.group(2);
                                                Log.e(TAG, "ID: " + id + " VALUE: " + value);
                                                valuesList[id] = value;
                                                onResume();
                                                if (id == valuesList.length-1) {
                                                    new Api().execute(namesList, valuesList, new String[]{UPDATE_STATE_URL});
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
                String fullUrl = params[2][0] + urlParams.toString();
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
//            Toast.makeText(getApplicationContext(), "processing", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "processing");
        }

        @Override
        protected void onPostExecute(String result) {
            // onPostExecute is called when doInBackground finished
            // Here you can for example fill your Listview with the content loaded in doInBackground method
//            Toast.makeText(getApplicationContext(), result, Toast.LENGTH_LONG).show();

            Log.e(TAG, result);
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


    public String[] getLocation()
    {
        String[] locationArray=new String[2];

        locationManager = (LocationManager) MainActivity.this.getSystemService(LOCATION_SERVICE);
        // getting network status
        boolean  isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean  isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // The minimum distance to change Updates in meters
        final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters

        // The minimum time between updates in milliseconds
        final long MIN_TIME_BW_UPDATES = 1000 * 60 * 1; // 1 minute

        if (isGPSEnabled) {
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    MIN_TIME_BW_UPDATES,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
            Log.d(TAG, "GPS location");
            if (locationManager != null) {
                Location location = locationManager
                        .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (location != null) {
                    Log.d(TAG, "Last known GPS location");
                    locationArray[0] = String.valueOf(location.getLatitude());
                    locationArray[1] = String.valueOf(location.getLongitude());

                }
            }
        } else {
            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                Log.d(TAG, "Network location");
                if (locationManager != null) {
                    Location location = locationManager
                            .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    if (location != null) {
                        Log.d(TAG, "Last known Network location");
                        locationArray[0] = String.valueOf(location.getLatitude());
                        locationArray[1] = String.valueOf(location.getLongitude());

                    }
                }
            }
        }
        new Api().execute(
                new String[] {"lon", "lat"},
                new String[] {""+locationArray[1], ""+locationArray[0]},
                new String[] {UPDATE_LOCATION_URL});
        return locationArray;

    }


    /**
     * Stop using location listener
     * Calling this function will stop using location updates in your app
     * */
    public void stopUsingLocationUpdates(){
        if(locationManager != null){
            locationManager.removeUpdates(MainActivity.this);
        }
    }


    @Override
    public void onLocationChanged(Location location) {
        // TODO Auto-generated method stub
        Log.e(TAG, "new location " + location.getLatitude() + " , " + location.getLongitude());
        new Api().execute(
                new String[] {"lon", "lat"},
                new String[] {""+location.getLongitude(), ""+location.getLatitude()},
                new String[] {UPDATE_LOCATION_URL});
    }

    @Override
    public void onProviderDisabled(String arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProviderEnabled(String arg0) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStatusChanged(String arg0, int arg1, Bundle arg2) {
        // TODO Auto-generated method stub

    }

}
