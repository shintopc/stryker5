package com.zalexdev.stryker.utils;

import static com.zalexdev.stryker.utils.ExecutorBuilder.contains;
import static org.osmdroid.tileprovider.util.StorageUtils.getBestWritableStorage;
import static org.osmdroid.tileprovider.util.StorageUtils.getStorage;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.zalexdev.stryker.MainActivity;

import org.acra.ACRA;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class Utils {
    private SQLiteDatabase dbCodename = null;
    private SQLiteDatabase dbAdapters = null;
    private SQLiteDatabase db = null;
    private ArrayList<String> pixieList = new ArrayList<>();

    public Utils(){
        readPixieList();
    }

    public boolean checkPixie(String name) {
        if (pixieList.isEmpty()) {
            readPixieList();
        }
        for (String line : pixieList) {
            if (line.toLowerCase().contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public String getDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd_MM_yyyy");
        return sdf.format(new Date());
    }

    public void readPixieList() {
        try {
            FileInputStream fis = new FileInputStream("/data/data/com.zalexdev.stryker5/files/routes.txt");
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                pixieList.add(line);
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
            ACRA.getErrorReporter().handleSilentException(e);
        }
    }
    public String getVendorByMacFromDB(String mac) {
        String vendor = "";
        try {
            if (db == null || !db.isOpen()) {
                db = SQLiteDatabase.openDatabase(
                    "/data/data/com.zalexdev.stryker5/files/vendors.db",
                    null,
                    SQLiteDatabase.OPEN_READONLY
                );
            }
            Cursor cursor = db.rawQuery(
                    "select MacPrefix,VendorName from macvendor where MacPrefix LIKE '%" + mac.substring(
                        0,
                        8
                    ).toUpperCase() + "%' COLLATE NOCASE", null
            );
            if (cursor.moveToFirst()) {
                vendor = cursor.getString(1);
            }
            cursor.close();
            db.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d("Utils", "getVendorByMacFromDB: " + vendor);
        return toTitleCase(vendor);
    }

    public String getDeviceByCodeNameFromDB(String codename) {
        String model = "";
        try {
            if (dbCodename == null || !dbCodename.isOpen()) {
                dbCodename = SQLiteDatabase.openDatabase(
                    "/data/data/com.zalexdev.stryker5/files/codenames.db",
                    null,
                    SQLiteDatabase.OPEN_READONLY
                );
            }
            Cursor cursor = dbCodename.rawQuery(
                    "SELECT manufacture,model FROM codename WHERE codename = '" + codename + "';",
            null
            );
            if (cursor.moveToFirst()) {
                model =
                    cursor.getString(0) + " " + cursor.getString(1).replace(cursor.getString(0), "");
            }
            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            toTitleCase(model);
        } catch (NullPointerException ignored) {
            return model;
        }
        return "";
    }

    public static final String fileDir = "/data/data/com.zalexdev.stryker5/files/";
    public static final String busybox = fileDir + "busybox ";
    public static final String tar = fileDir + "busybox tar ";
    public static final String packageName = "com.zalexdev.stryker5";
    public static String currentDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
        return sdf.format(new Date());
    }
    public static String currentTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date());
    }
    public static String currentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        return sdf.format(new Date());
    }

    public static void toast(Context context, String message) {
        ((Activity) context).runOnUiThread(() -> {
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_SHORT
            ).show();
        });
    }

    public static void toastLong(Context context, String message) {
        ((Activity) context).runOnUiThread(() -> {
            Toast.makeText(
                context,
                message,
                Toast.LENGTH_LONG
            ).show();
        });
    }



    public static boolean isStoragePermissionGranted(Context context) {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED;
    }

    public static void restartApp(Context context) {
        Intent mStartActivity = new Intent(context, MainActivity.class);
        int mPendingIntentId = 123456;
        PendingIntent mPendingIntent = PendingIntent.getActivity(
                context,
        mPendingIntentId,
        mStartActivity,
        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
        System.exit(0);
    }

    public static void requestStoragePermission(Activity activity) {
        activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
    }

    public static String toTitleCase(String givenString) throws NullPointerException {
        String[] arr = givenString.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (String s : arr) {
            if (s.length() > 1) {
                sb.append(Character.toUpperCase(s.charAt(0)))
                    .append(s.substring(1)).append(" ");
            }
        }
        return sb.toString();
    }

    public static void disableMagiskNotification() {
        Log.d("Utils", "disableMagiskNotification: "+android.os.Process.myUid());
        if (contains(ExecutorBuilder.runCommand("/data/data/com.zalexdev.stryker5/files/sqlite3 "
                + "/data/adb/magisk.db"
                + " \"UPDATE policies SET logging='0',notification='0' WHERE package_name='"
                + "com.zalexdev.stryker5"
                + "';\""), "no such"))
        {ExecutorBuilder.runCommand("/data/data/com.zalexdev.stryker5/files/sqlite3 /data/adb/magisk.db"
                + " \"UPDATE policies SET logging='0',notification='0' WHERE uid='"
                + android.os.Process.myUid()
                + "';\"");}
    }

    public static String readFileToString(Context context, String fileName) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            FileInputStream fis = context.openFileInput(fileName);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader bufferedReader = new BufferedReader(isr);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }
            fis.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return stringBuilder.toString();
    }
}