package com.zalexdev.stryker.fragments;

import static com.zalexdev.stryker.su.SuUtils.TAG;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;

import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;

import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.Preferences;
import com.zalexdev.stryker.utils.Utils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;


public class Main extends Fragment {

    private Context context;
    private static Main instance;

    public Main() {

    }

    public static Main newInstance(Context context) {
        return getInstance(context);
    }

    public static Main getInstance(Context context) {
        if (instance == null) {
            instance = new Main();
        }
        return instance;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_main, container, false);
        context = getContext();
        LinearLayout wifi_item = root.findViewById(R.id.wifi);
        LinearLayout saved_item = root.findViewById(R.id.saved_networks);
        LinearLayout app_settigns = root.findViewById(R.id.app_settigns);
        LinearLayout local = root.findViewById(R.id.local);
        LinearLayout nuclei = root.findViewById(R.id.nuclei);
        wifi_item.setOnClickListener(v -> Preferences.getInstance().replaceFragment(ScannerWiFi.getInstance(context), "wifi"));
        saved_item.setOnClickListener(v -> Preferences.getInstance().replaceFragment(SavedWiFi.newInstance(), "saved_wifi"));
        nuclei.setOnClickListener(v -> Preferences.getInstance().replaceFragment(NucleiHostFragment.newInstance(), "nuclei"));
        ImageView settings_item = root.findViewById(R.id.settings);
        app_settigns.setOnClickListener(v -> Preferences.getInstance().replaceFragment(Settings.newInstance(), "settings"));
        settings_item.setOnClickListener(v -> Preferences.getInstance().replaceFragment(Settings.newInstance(), "settings"));
        if (!Preferences.getInstance().getBoolean("magisk_notif")) {
            Preferences.getInstance().dialog("Magisk", "Stryker very often uses root for his work. Would you like us to turn off these annoying notifications about this?", "Yes, please", null, R.drawable.magisk, aBoolean -> {
                Utils.disableMagiskNotification();
                Preferences.getInstance().toaster("Magisk notifications disabled");
                Preferences.getInstance().setBoolean("magisk_notif", true);
            });
        }
        local.setOnClickListener(v -> Preferences.getInstance().replaceFragment(ScannerLocal.getInstance(context), "local"));
        TextView chroot = root.findViewById(R.id.chroot);
        LinearProgressIndicator progress = root.findViewById(R.id.progress);
        TextView size = root.findViewById(R.id.size);
        int calc = Preferences.getInstance().getInt("chroot_size_calc");
        calc++;
        Preferences.getInstance().setInt("chroot_size_calc", calc);
        if (calc == 12 || calc == 1) {
            ExecutorBuilder builder = new ExecutorBuilder();
            builder.setCommand("du -sh . | cut -f1 | sed 's/G/GB/'");
            builder.setChroot(true);
            builder.setActivity(getActivity());
            builder.setOnFinished(strings -> {
                size.setText("(" + strings.get(strings.size() - 1) + ")");
                Preferences.getInstance().setString("chroot_size", "(" + strings.get(strings.size() - 1) + ")");
                progress.setVisibility(View.GONE);
            });
            builder.execute();
            if (calc == 12) {
                Preferences.getInstance().setInt("chroot_size_calc", 2);
            }
        } else {
            progress.setVisibility(View.GONE);
        }
        if (Preferences.getInstance().getString("chroot_size").isEmpty()) {
            size.setText("Calculating...");
        } else {
            size.setText(Preferences.getInstance().getString("chroot_size"));
        }
        StringBuilder mountLog = new StringBuilder();
        SuUtils.mountChroot(line -> {
            mountLog.append(line).append("\n");
            chroot.setText(line);
        }, aBoolean -> {
            if (!aBoolean) {
                Log.e("Main", "onCreateView: Error mounting chroot");
                chroot.setText("Error mounting chroot! Details:\n" + mountLog.toString());
            } else {
                chroot.setText("All systems are cool ⚡. Chroot environment is ready");
            }
        });

        return root;
    }


}