package com.zalexdev.stryker;

import static com.zalexdev.stryker.utils.TextStyler.convert;
import static com.zalexdev.stryker.utils.TextStyler.green;
import static com.zalexdev.stryker.utils.TextStyler.red;
import static com.zalexdev.stryker.utils.TextStyler.yellow;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.StrictMode;
import android.text.Spannable;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;


import com.zalexdev.stryker.appintro.slides.Slide1;
import com.zalexdev.stryker.fragments.Main;
import com.zalexdev.stryker.su.SuUtils;


import com.zalexdev.stryker.utils.DebugData;
import com.zalexdev.stryker.utils.MyExceptionHandler;
import com.zalexdev.stryker.utils.Preferences;
import com.zalexdev.stryker.utils.UsbActionListener;


import org.acra.ACRA;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity implements UsbActionListener {

    private Preferences preferences = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Preferences.initInstance(this);
        preferences = Preferences.getInstance();
        Thread.setDefaultUncaughtExceptionHandler(new MyExceptionHandler());

        FragmentManager fm = getSupportFragmentManager();
        preferences.setFragmentManager(fm);
        StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .build());


        if (!preferences.getBoolean("first_run")) {
            Log.d(TAG, "onCreate: first run");
            preferences.replaceFragment(new Slide1());
        } else {
            if (SuUtils.isRoot()) {
                SuUtils.checkFileOrFolder("/data/local/stryker5/release/VERSION_5.0", aBoolean -> {
                    if (aBoolean) {


                        preferences.replaceFragment(Main.getInstance(this));
                        SuUtils.copyAssets();
                    } else {
                        preferences.toaster("Please install the chroot");
                        preferences.replaceFragment(new Slide1());
                    }
                });
            }else{
                preferences.toaster("Root is required");
                preferences.replaceFragment(new Slide1());
            }
        }

        TextView debug = findViewById(R.id.debug);
        if (preferences.getBoolean("debug")) {
            debug.setVisibility(TextView.VISIBLE);
        }
        startDebugger(debug);







    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        if (preferences.getVisibleFragment() != null && !preferences.getVisibleFragment().getClass().getSimpleName().equals("main")) {
            preferences.replaceFragment(Main.newInstance(this));
            return;
        }
    }



    private static final String TAG = "MainActivity";

    @Override
    public void onUsbAttached(UsbDevice device) {

    }

    @Override
    public void onUsbDetached(UsbDevice device) {

    }



    public void startDebugger(TextView debug){
        debug.setText("Debugging...");
        DebugData debugData = DebugData.getInstance();
        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!preferences.getBoolean("debug")) {
                    timer.cancel();
                    return;
                }

                // Create a thread-safe copy of the commands list
                List<String> cmds;
                synchronized (debugData) {
                    cmds = new ArrayList<>(debugData.getCmds());
                }

                Log.d(TAG, "run: " + cmds);
                if (!cmds.isEmpty()) {
                    ArrayList<String> all = new ArrayList<>();
                    for (String cmd : cmds) {
                        try {
                            String[] c = cmd.split("\\|\\|\\|");
                            if (c[1].contains("started")) {
                                all.add(green(c[0]));
                            } else if (c[1].contains("running")) {
                                all.add(yellow(c[0]));
                            } else if (c[1].contains("deleted")) {
                                all.add(red(c[0]));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    Log.d(TAG, "run: " + all);
                    runOnUiThread(() -> {
                        debug.setText("");
                        for (String s : all) {
                            appendToTerminal(s, debug);
                        }
                    });
                } else {
                    runOnUiThread(() -> debug.setText(""));
                }
            }
        }, 0, 1000);
    }

    public void appendToTerminal(String text, TextView terminal) {
        if (preferences.isHideMac()){
            Matcher m = Pattern.compile("((\\w{2}:){5}\\w{2})").matcher(text);
            while (m.find()) {
                text = text.replace(m.group(), "XX:XX:XX:XX:XX:XX");
            }
        }
        terminal.append(convert(text+"<br>"));
    }





}