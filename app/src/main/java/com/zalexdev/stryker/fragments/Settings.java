package com.zalexdev.stryker.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.adapters.WifiAdapter;

import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.Preferences;
import com.zalexdev.stryker.utils.Utils;

import java.util.ArrayList;
import java.util.function.Consumer;

public class Settings extends Fragment {

    private final Utils utils = new Utils();

    public static Settings newInstance() {
        return new Settings();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_settings, container, false);
        setupBackButton(root);
        Preferences preferences = Preferences.getInstance();
        SwitchMaterial switch1 = root.findViewById(R.id.swith_autoturnoff);
        SwitchMaterial switch2 = root.findViewById(R.id.swith_mtk);
        SwitchMaterial switch3 = root.findViewById(R.id.swith_hide);
        SwitchMaterial switch4 = root.findViewById(R.id.swith_hidessid);
        SwitchMaterial switch5 = root.findViewById(R.id.swith_debug);
        preferences.setSwitchUpdater(switch1, "iface_down");
        preferences.setSwitchUpdater(switch2, "pixie_mtk");
        preferences.setSwitchUpdater(switch3, "hide_mac");
        preferences.setSwitchUpdater(switch4, "hide_ssid");
        preferences.setSwitchUpdater(switch5, "debug");
        LinearLayout wlan0 = root.findViewById(R.id.wifi_wlan0);
        LinearLayout unmount = root.findViewById(R.id.unmount);
        LinearLayout delete = root.findViewById(R.id.delete);
        wlan0.setOnClickListener(v -> preferences.dialogInput("Configure commands", "wlan0_cmd_on", "wlan0_cmd_off", "wlan0_onchroot",
                "Enable monitor mode", "Disable monitor mode","Run on chroot", strings -> Preferences.getInstance().toaster("Commands saved")));
        LinearLayout wlan1 = root.findViewById(R.id.wifi_wlan1);
        wlan1.setOnClickListener(v -> preferences.dialogInput("Configure commands", "wlan1_cmd_on", "wlan1_cmd_off", "wlan1_onchroot",
                "Enable monitor mode", "Disable monitor mode","Run on chroot", strings -> Preferences.getInstance().toaster("Commands saved")));
        delete.setOnClickListener(v -> preferences.dialog("Confirmation", "Are you sure you want to delete app and chroot?","Yes","No",R.drawable.delete, new Consumer<Boolean>() {
            @Override
            public void accept(Boolean aBoolean) {
                if (aBoolean) {
                    Preferences.getInstance().toaster("Deleting app and chroot... Have a nice day :)");
                    SuUtils.unMountChroot(aBoolean1 -> {
                        if (aBoolean1) {
                            new  Thread(() -> {
                                ExecutorBuilder.runCommand("rm -rf /data/local/stryker5");
                                ExecutorBuilder.runCommand("pm uninstall com.zalexdev.stryker");
                            }).start();
                        } else {
                            Preferences.getInstance().toaster("Error unmounting chroot");
                        }
                    });
                }
            }
        }));
        unmount.setOnClickListener(v -> preferences.dialog("Confirmation", "Are you sure you want to unmount chroot?","Yes","No",R.drawable.terms, aBoolean -> {
            if (aBoolean) {
                SuUtils.unMountChroot(aBoolean1 -> {
                    if (aBoolean1) {
                        Preferences.getInstance().toaster("Chroot unmounted");
                        ExecutorBuilder.runCommand("am force-stop com.zalexdev.stryker");

                    } else {
                        Preferences.getInstance().toaster("Error unmounting chroot");
                    }
                });
            }
        }));
        return root;
    }


    private void setupBackButton(View root) {
        root.findViewById(R.id.back).setOnClickListener(v -> Preferences.getInstance().replaceFragment(new Main(), "main"));
    }







}