package com.zalexdev.stryker.fragments;

import static com.zalexdev.stryker.su.SuUtils.mountChroot;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.adapters.WifiAdapter;

import com.zalexdev.stryker.objects.WifiNetwork;
import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.FileUtils;
import com.zalexdev.stryker.utils.Preferences;
import com.zalexdev.stryker.utils.Utils;

import org.acra.ACRA;

import java.util.ArrayList;
import java.util.Collections;

public class SavedWiFi extends Fragment {

    private RecyclerView recyclerView;
    private final Utils utils = new Utils();
    private LinearProgressIndicator progressBar;

    public static SavedWiFi newInstance() {
        return new SavedWiFi();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mountChroot(null, null);
        View root = inflater.inflate(R.layout.fragment_savedwifi, container, false);
        progressBar = root.findViewById(R.id.progress_bar);
        setupRecyclerView(root);
        setupBackButton(root);
        ImageView importin = root.findViewById(R.id.import_in);
        ImageView exportout = root.findViewById(R.id.export);
        exportout.setOnClickListener(v -> {
            ArrayList<String> data = new ArrayList<>();
            for (WifiNetwork network : Preferences.getInstance().getWifiList()) {
                Log.d("Network", network.toBase64String());
                data.add(network.toBase64String());
            }
            String date = utils.getDateString();
            FileUtils.writeToFile( "Stryker5Wifi_"+date+".txt",data);
            SuUtils.moveFile(FileUtils.basePath+"/Stryker5Wifi_"+date+".txt", "/sdcard/Stryker5/Stryker5Wifi_"+date+".txt");
        });
        importin.setOnClickListener(v -> {
            ArrayList<String> data = new ArrayList<>(ExecutorBuilder.runCommand("cat /sdcard/Stryker5/Stryker5Wifi_*.txt"));
            for (String line : data) {
                if (!line.contains("No such file or directory")){
                    Preferences.getInstance().addWifi(WifiNetwork.fromBase64String(line));
                }else{
                    Preferences.getInstance().toaster("No saved networks found. Reanme files to Stryker5Wifi_*.txt and try again.");
                }
            }
            getNetworks();
            //reanme imported files
            for (String file : ExecutorBuilder.runCommand("ls /sdcard/Stryker5/Stryker5Wifi_*.txt")) {
                SuUtils.moveFile(file, file.replace("/sdcard/Stryker5/Stryker5Wifi_", "/sdcard/Stryker5/Stryker5Imported_"));
            }
        });
        getNetworks();
        return root;
    }

    private void setupRecyclerView(View root) {
        recyclerView = root.findViewById(R.id.wifi_list);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        WifiAdapter saved = new WifiAdapter(new ArrayList<>());
        saved.setSaved(true);
        recyclerView.setAdapter(saved);
    }



    private void setupBackButton(View root) {
        root.findViewById(R.id.back).setOnClickListener(v -> Preferences.getInstance().replaceFragment(new Main(), "main"));
    }

    private void getNetworks(){
        progressBar.setVisibility(View.VISIBLE);

        WifiAdapter adapter = new WifiAdapter(Preferences.getInstance().getWifiList());
        adapter.setSaved(true);
        progressBar.setVisibility(View.GONE);
        recyclerView.setAdapter(adapter);
        recyclerView.setItemViewCacheSize(200);
        recyclerView.setNestedScrollingEnabled(false);
        progressBar.setVisibility(View.GONE);
    }


    public ArrayList<WifiNetwork> parseWifi(ArrayList<String> output) {
        System.out.println(output);
        ArrayList<WifiNetwork> networks = new ArrayList<>();

        StringBuilder myJsonString = new StringBuilder();
        for (String line : output) {
            if (!line.contains("[!] ")) {
                myJsonString.append(line);
            }
        }
        ObjectMapper om = new ObjectMapper();
        try {
            WifiNetwork[] root = om.readValue(myJsonString.toString(), WifiNetwork[].class);
            Collections.addAll(networks, root);
            for (WifiNetwork network : networks) {
                if (network.ssid == null || network.ssid.equals("null") || network.ssid.equals("") || network.ssid.equals("<length: 0>")) {
                    network.ssid = "Hidden SSID";
                }
                if (network.ssid.contains("\\x")) {
                    Log.e("SSID", network.ssid);
                    Log.e("SSID", decodeString(network.ssid));
                    Log.e("BSSID", network.bssid);
                    network.ssid = decodeString(network.ssid);
                }else if (network.ssid.contains("\\u")) {
                    network.ssid = decodeString(network.ssid);
                }


                    if (network.manufacturer != null &&  network.manufacturer.length() < 3) {
                        network.manufacturer = "Unknown";
                    }

            }
        } catch (Exception e) {
            ACRA.getErrorReporter().handleException(e);

        }


        ArrayList<WifiNetwork> uniqueNetworks = new ArrayList<>();
        ArrayList<String> uniqueSSIDs = new ArrayList<>();

        for (WifiNetwork network : networks) {
            if (!uniqueSSIDs.contains(network.ssid)) {
                uniqueSSIDs.add(network.ssid);

                uniqueNetworks.add(network);
            }
        }





        Log.d("Networks "+uniqueNetworks.size(), uniqueNetworks.toString());
        return networks;
    }

    public String decodeString(String encodedString) {
        return ExecutorBuilder.runCommand("echo -e \"SSID: " + encodedString + "\"").toString().replace("SSID: ", "").replace("[","").replace("]","").replace("\n","");
    }


}