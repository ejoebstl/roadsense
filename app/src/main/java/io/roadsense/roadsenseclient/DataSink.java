package io.roadsense.roadsenseclient;

import android.content.pm.PackageInstaller;
import android.os.AsyncTask;
import android.os.Environment;
import android.util.Log;

import com.google.gson.GsonBuilder;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import io.roadsense.roadsenseclient.common.Point3D;

/**
 * Created by emi on 08/03/16.
 */
public class DataSink {

    private static FileWriter txtWriter;
    private static FileWriter jsonWriter;
    private static long SessionID;
    private static ConnectorActivity uiLog;

    public static String LOG_FILE_NAME =
            Environment.getExternalStorageDirectory() + "/acc_data_";
    static {
        SessionID = (long)(System.currentTimeMillis() / 1000);
        try {
            txtWriter = new FileWriter(LOG_FILE_NAME + SessionID + ".txt", true);
            jsonWriter = new FileWriter(LOG_FILE_NAME + SessionID + ".json", true);
            Log.d("LOGGER", "Writing log to " + LOG_FILE_NAME);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Point3D accData = new Point3D(0, 0, 0);
    private static double lat = 0;
    private static double lon = 0;
    private static String address = "";

    public static void SetUiCallback(ConnectorActivity uiLog) {
        DataSink.uiLog = uiLog;
    }

    public synchronized static void Log(Point3D accData, String address) {
        DataSink.accData = accData;
        DataSink.address = address;
        Log();
        LogToServer();
    }
    public static void Log(double lat, double lon) {
        DataSink.lat = lat;
        DataSink.lon = lon;
    }

    private static void Log() {
        long timestamp = System.currentTimeMillis();
        try {
            String log = timestamp + "; " + lat + "; " + lon + "; " + accData.x + "; " + accData.y + "; " + accData.z + "; " + address;
            txtWriter.write(log + "\n");
            txtWriter.flush();

            jsonWriter.write(new GsonBuilder().create().toJson(LogToMap(), Map.class) + ",");
            jsonWriter.flush();

            if(uiLog != null)
                uiLog.log(log);
            Log.d("LOGGER", log);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    static boolean inProgress = false;
    static ArrayList<Map<String, Object>> buffer = new ArrayList<>();
    static Object oLock = new Object();

    private static Map<String, Object> LogToMap() {
        Map<String, Object> log = new HashMap<String, Object>();
        log.put("timestamp", System.currentTimeMillis());
        log.put("lat", lat);
        log.put("lon", lon);
        log.put("x", accData.x);
        log.put("y", accData.y);
        log.put("z", accData.z);
        log.put("session", SessionID);
        log.put("sensorid", address);
        return log;
    }

    private static void LogToServer() {

        buffer.add(LogToMap());

        if(buffer.size() > 10) {
            if (!inProgress) {
                synchronized (oLock) {
                    inProgress = true;
                    String json = new GsonBuilder().create().toJson(buffer, ArrayList.class);
                    (new AsyncRequest("http://192.168.220.201/", json)).execute();
                    buffer.clear();
                }
            }
        }
    }

    static class AsyncRequest extends AsyncTask<Void, Void, Void> {
        private String json;
        private String uri;

        public AsyncRequest(String uri, String json) {
            this.json = json;
            this.uri = uri;
        }

        protected Void doInBackground(Void... arg0) {
            Log.d("LOGGER", "Sending: " + this.json);
            while(true) {
                try {
                    HttpPost httpPost = new HttpPost(uri);
                    httpPost.setEntity(new StringEntity(json));
                    httpPost.setHeader("Accept", "application/json");
                    httpPost.setHeader("Content-type", "application/json");
                    new DefaultHttpClient().execute(httpPost);
                    synchronized (oLock) {
                        inProgress = false;
                    }
                    return null;
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                } catch (ClientProtocolException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                // Error Case:
                Log.d("LOGGER", "Retrying...");
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}
