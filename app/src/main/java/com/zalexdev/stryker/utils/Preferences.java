package com.zalexdev.stryker.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextUtils;

import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.Gson;

import com.google.gson.reflect.TypeToken;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.objects.NucleiHost;
import com.zalexdev.stryker.objects.NucleiVuln;
import com.zalexdev.stryker.objects.WifiNetwork;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.Setter;

public class Preferences {
    @Getter
    private final Context context;
    private final SharedPreferences preferences;
    @Getter
    private static Preferences instance;
    public static final String EXECUTE = "/data/data/com.zalexdev.stryker5/files/chroot_exec ";
    public static final String BUSYBOX = "/data/data/com.zalexdev.stryker5/files/busybox ";
    public static final String HIDDEN_MAC = "XX:XX:XX:XX:XX:XX";
    public static final String FIRST_TIME = "first_time";
    @Setter
    @Getter
    public FragmentManager fragmentManager;

    private static final String NUCLEI_HOSTS_KEY = "nuclei_hosts";
    @Setter
    private Consumer<String> hostUpdatedConsumer;

    public static void initInstance(Context context) {
        if (instance == null) {
            instance = new Preferences(context);
        }
    }

    public Preferences(Context appContext) {
        SharedPreferences preferences1;
        String mainKeyAlias = null;
        try {
            mainKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            preferences1 = EncryptedSharedPreferences.create(
                    "encryptedStorage",
                    mainKeyAlias,
                    appContext,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (GeneralSecurityException | IOException e) {
            preferences1 = PreferenceManager.getDefaultSharedPreferences(appContext);
        }
        preferences = preferences1;
        context = appContext;
    }

    public void setInt(String key, int value) {
        checkForNullKey(key);
        preferences.edit().putInt(key, value).apply();
    }

    public int getInt(String key) {
        return preferences.getInt(key, 0);
    }

    public void setListInt(String key, ArrayList<Integer> intList) {
        checkForNullKey(key);
        Integer[] myIntList = intList.toArray(new Integer[0]);
        preferences.edit().putString(key, TextUtils.join("‚‗‚", myIntList)).apply();
    }

    public ArrayList<Integer> getListInt(String key) {
        String[] myList = TextUtils.split(preferences.getString(key, ""), "‚‗‚");
        ArrayList<String> arrayToList = new ArrayList<>(Arrays.asList(myList));
        ArrayList<Integer> newList = new ArrayList<>();
        for (String item : arrayToList) newList.add(Integer.valueOf(item));
        return newList;
    }

    public void setString(String key, String value) {
        checkForNullKey(key);
        checkForNullValue(value);
        preferences.edit().putString(key, value).apply();
    }

    public String getString(String key) {
        return preferences.getString(key, "");
    }

    public void setListString(String key, ArrayList<String> stringList) {
        checkForNullKey(key);
        String[] myStringList = stringList.toArray(new String[0]);
        preferences.edit().putString(key, TextUtils.join("‚‗‚", myStringList)).apply();
    }

    public void setObject(String key, Object obj) {
        checkForNullKey(key);
        Gson gson = new Gson();
        setString(key, gson.toJson(obj));
    }

    public void setListObject(String key, ArrayList<Object> objArray) {
        checkForNullKey(key);
        Gson gson = new Gson();
        ArrayList<String> objStrings = new ArrayList<>();
        for (Object obj : objArray) {
            objStrings.add(gson.toJson(obj));
        }
        setListString(key, objStrings);
    }

    public ArrayList<String> getListString(String key) {
        return new ArrayList<>(Arrays.asList(TextUtils.split(preferences.getString(key, ""), "‚‗‚")));
    }

    public ArrayList<Object> getListObject(String key, Class<?> mClass) {
        Gson gson = new Gson();
        ArrayList<String> objStrings = getListString(key);
        ArrayList<Object> objects = new ArrayList<>();
        for (String jObjString : objStrings) {
            Object value = gson.fromJson(jObjString, mClass);
            objects.add(value);
        }
        return objects;
    }


    public <T> T getObject(String key, Class<T> classOfT) {
        String json = getString(key);
        T value = new Gson().fromJson(json, classOfT);
        if (value == null) {
            throw new NullPointerException();
        }
        return value;
    }

    public void addWifi(WifiNetwork wifi) {
        ArrayList<Object> wifiList = getListObject("wifi", WifiNetwork.class);
        wifiList.add(wifi);
        setListObject("wifi", wifiList);
    }

    public void removeWifi(WifiNetwork wifi) {
        ArrayList<Object> wifiList = getListObject("wifi", WifiNetwork.class);
        wifiList.remove(wifi);
        setListObject("wifi", wifiList);
    }

    public void removeWifi(int index) {
        ArrayList<Object> wifiList = getListObject("wifi", WifiNetwork.class);
        wifiList.remove(index);
        setListObject("wifi", wifiList);
    }

    public ArrayList<WifiNetwork> getWifiList() {
        ArrayList<Object> wifiobj = getListObject("wifi", WifiNetwork.class);
        ArrayList<WifiNetwork> wifi = new ArrayList<>();
        for (Object obj : wifiobj) {
            wifi.add((WifiNetwork) obj);
        }
        return wifi;
    }

    public void clearWifiList() {
        setListObject("wifi", new ArrayList<>());
    }





    public void setInterfaces(ArrayList<String> interfaces){
        setListString("interfaces", interfaces);
    }

    public ArrayList<String> getInterfaces(){
        return getListString("interfaces");
    }

    public boolean getBoolean(String key) {
        return preferences.getBoolean(key, false);
    }

    public ArrayList<Boolean> getListBoolean(String key) {
        ArrayList<String> myList = getListString(key);
        ArrayList<Boolean> newList = new ArrayList<>();
        for (String item : myList) {
            newList.add(Boolean.parseBoolean(item));
        }
        return newList;
    }

    public void setBoolean(String key, boolean value) {
        checkForNullKey(key);
        preferences.edit().putBoolean(key, value).apply();
    }

    public void setListBoolean(String key, ArrayList<Boolean> boolList) {
        checkForNullKey(key);
        ArrayList<String> newList = new ArrayList<>();
        for (boolean item : boolList) {
            newList.add(Boolean.toString(item));
        }
        setListString(key, newList);
    }

    public void remove(String key) {
        preferences.edit().remove(key).apply();
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public Map<String, ?> getAll() {
        return preferences.getAll();
    }

    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        preferences.registerOnSharedPreferenceChangeListener(listener);
    }

    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        preferences.unregisterOnSharedPreferenceChangeListener(listener);
    }

    public boolean objectExists(String key) {
        String gottenString = getString(key);
        return !gottenString.isEmpty();
    }

    private void checkForNullKey(String key) {
        if (key == null) {
            throw new NullPointerException();
        }
    }

    private void checkForNullValue(String value) {
        if (value == null) {
            throw new NullPointerException();
        }
    }

    public boolean isFirstTime() {
        return !getBoolean("firstTime");
    }

    public void toaster(String msg) {
        Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
        toast.show();
    }

    public void toaster(String msg, boolean onui) {
        ((Activity) context).runOnUiThread(() -> {
            Toast toast = Toast.makeText(context, msg, Toast.LENGTH_SHORT);
            toast.show();
        });
    }

    public String getDeviceId() {
        String deviceId = getString("device_id");
        if (deviceId.length() > 1) {
            return deviceId;
        } else {
            String id = Build.HARDWARE + "_" + Build.MODEL + "_" + Build.VERSION.SDK_INT + "_" + getRandomNumber();
            id = id.replace(" ", "");
            setString("device_id", id);
            return id;
        }
    }

    public boolean isLoggedIn() {
        return true;
    }

    public String getInternalMonCmd(){
        return getString("wlan0_cmd_on");
    }

    public String getExternalMonCmd(String wlan){
        return getString("wlan1_cmd_on").replace("{wlan}", wlan);
    }

    public String getInternalMonCmdOff(){
        return getString("wlan0_cmd_off");
    }

    public String getExternalMonCmdOff(String wlan){
        return getString("wlan1_cmd_off").replace("{wlan}", wlan);
    }

public boolean getInternalMonCmdChroot(){
        return getBoolean("wlan0_onchroot");
    }

    public boolean getExternalMonCmdChroot(){
        return getBoolean("wlan1_onchroot");
    }

    public void setInternalMonCmd(String cmd){
        setString("internal_mon_cmd", cmd);
    }

    public void setListenerInterface(String iface) {
        setString("listener_interface", iface);
    }

    public String getListenerInterface() {
        return getString("listener_interface");
    }

    public void setDeauthInterface(String iface) {
        setString("deauth_interface", iface);
    }

    public String getDeauthInterface() {
        return getString("deauth_interface");
    }

    public boolean isInstalled() {
        return getBoolean("installed");
    }

    public void setInstalled() {
        setBoolean("installed", true);
    }

    private int getRandomNumber() {
        return (int) (Math.random() * (9999 - 100) + 100);
    }

    public static boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state) || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state);
    }

    public static void moveNext(ViewPager mPager) {
        mPager.setCurrentItem(mPager.getCurrentItem() + 1);
    }

    public static String getStorage() {
        return Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    }

    public static void movePrevious(ViewPager mPager) {
        mPager.setCurrentItem(mPager.getCurrentItem() - 1);
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

    public Activity getActivity() {
        return (Activity) context;
    }
    public FragmentActivity getActivityFrg() {
        return (FragmentActivity ) context;
    }

    public void setSwitchUpdater(SwitchMaterial switcher, String pref_name) {
        switcher.setChecked(getBoolean(pref_name));
        switcher.setOnCheckedChangeListener((buttonView, isChecked) -> setBoolean(pref_name, isChecked));
    }

    public void setEditTextUpdater(TextInputLayout input, String pref_name) {
        EditText editText = input.getEditText();
        assert editText != null;
        editText.setText(getString(pref_name));
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                setString(pref_name, s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                input.setHelperText("Saved!");
                input.setHelperTextColor(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.green)));
                Handler handler = new Handler();
                handler.postDelayed(() -> getActivity().runOnUiThread(() -> input.setHelperText("")), 1000);
            }
        });
    }

    public boolean isHideMac() {
        return getBoolean("hide_mac");
    }

    public boolean isHideSSID() {
        return getBoolean("hide_ssid");
    }





    public void dialogChooser(String title, String pref_name, @NonNull ArrayList<String> items, Consumer<String> result){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.setIcon(R.drawable.app_settings);
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            setString(pref_name, items.get(which));
            if (result != null){
                result.accept(items.get(which));
            }
        });
        builder.show();
    }

    public void dialogChooser(int icon, String title, String pref_name, @NonNull ArrayList<String> items, Consumer<String> result){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.setIcon(icon);
        builder.setItems(items.toArray(new CharSequence[0]), (dialog, which) -> {
            setString(pref_name, items.get(which));
            if (result != null){
                result.accept(items.get(which));
            }
        });
        builder.show();
    }

    public void dialogInput(String title, String pref_name, Consumer<String> result) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.input_icon);

        // Inflate the custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.input, null);
        TextInputLayout textInputLayout = dialogView.findViewById(R.id.outlinedTextField);
        TextInputEditText textInputEditText = dialogView.findViewById(R.id.input);

        // Set the hint and initial text
        textInputEditText.setText(getString(pref_name));

        builder.setView(dialogView);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            if (textInputEditText.getText().toString().isEmpty()){
                dialog("Error", "Please fill all fields", "Ok", null, R.drawable.exploit_bg, null);
            } else {
                setString(pref_name, textInputEditText.getText().toString());
                if (result != null) {
                    result.accept(textInputEditText.getText().toString());
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    public void dialogInput(String title, String pref_name1, String pref_name2, String hint1, String hint2, Consumer<String[]> result) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.app_settings);

        // Inflate the custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.input_double, null);
        TextInputLayout textInputLayout1 = dialogView.findViewById(R.id.outlinedTextField);
        TextInputEditText textInputEditText1 = dialogView.findViewById(R.id.input);
        TextInputLayout textInputLayout2 = dialogView.findViewById(R.id.outlinedTextField1);
        TextInputEditText textInputEditText2 = dialogView.findViewById(R.id.input1);
        SwitchMaterial materialSwitch = dialogView.findViewById(R.id.switcher);
        materialSwitch.setVisibility(View.GONE);
        // Set the hints and initial texts
        textInputLayout1.setHint(hint1);
        textInputEditText1.setText(getString(pref_name1));
        textInputLayout2.setHint(hint2);
        textInputEditText2.setText(getString(pref_name2));

        builder.setView(dialogView);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            if (textInputEditText1.getText().toString().isEmpty() || textInputEditText2.getText().toString().isEmpty()){
                dialog("Error", "Please fill all fields", "Ok", null, R.drawable.exploit_bg, null);
            }else{
            setString(pref_name1, textInputEditText1.getText().toString());
            setString(pref_name2, textInputEditText2.getText().toString());
            if (result != null){
                result.accept(new String[]{textInputEditText1.getText().toString(), textInputEditText2.getText().toString()});
            }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    public void dialogInput(String title, String pref_name1, String pref_name2, String pref_switch, String hint1, String hint2, String switch_text, Consumer<String[]> result) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        builder.setCancelable(true);
        builder.setIcon(R.drawable.app_settings);

        // Inflate the custom layout
        View dialogView = LayoutInflater.from(context).inflate(R.layout.input_double, null);
        TextInputLayout textInputLayout1 = dialogView.findViewById(R.id.outlinedTextField);
        TextInputEditText textInputEditText1 = dialogView.findViewById(R.id.input);
        TextInputLayout textInputLayout2 = dialogView.findViewById(R.id.outlinedTextField1);
        TextInputEditText textInputEditText2 = dialogView.findViewById(R.id.input1);
        SwitchMaterial materialSwitch = dialogView.findViewById(R.id.switcher);
        materialSwitch.setVisibility(View.VISIBLE);
        materialSwitch.setText(switch_text);
        materialSwitch.setChecked(getBoolean(pref_switch));

        // Set the hints and initial texts
        textInputLayout1.setHint(hint1);
        textInputEditText1.setText(getString(pref_name1));
        textInputLayout2.setHint(hint2);
        textInputEditText2.setText(getString(pref_name2));

        builder.setView(dialogView);
        builder.setPositiveButton("Ok", (dialog, which) -> {
            if (textInputEditText1.getText().toString().isEmpty() || textInputEditText2.getText().toString().isEmpty()){
                dialog("Error", "Please fill all fields", "Ok", null, R.drawable.exploit_bg, null);
            }else{
                setString(pref_name1, textInputEditText1.getText().toString());
                setString(pref_name2, textInputEditText2.getText().toString());
                setBoolean(pref_switch, materialSwitch.isChecked());
                if (result != null){
                    result.accept(new String[]{textInputEditText1.getText().toString(), textInputEditText2.getText().toString()});
                }
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    public void dialog(String title, String message, String positive, String negative, int icon, Consumer<Boolean> result){
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context);
        builder.setTitle(title);
        if (icon != 0){
            builder.setIcon(icon);
        }
        if (message != null){
            builder.setMessage(message);
        }
        if (result == null){
            builder.setCancelable(true);
            if (positive == null && negative == null){
                builder.setPositiveButton("Ok", (dialog, which) -> dialog.dismiss());
            }else{
                if (positive != null){
                    builder.setPositiveButton(positive, (dialog, which) -> dialog.dismiss());
                }
                if (negative != null){
                    builder.setNegativeButton(negative, (dialog, which) -> dialog.dismiss());
                }
            }
        }else{
            if (positive != null){
                builder.setPositiveButton(positive, (dialog, which) -> result.accept(true));
            }
            if (negative != null){
                builder.setNegativeButton(negative, (dialog, which) -> result.accept(false));
            }
            if (positive == null && negative == null){
                builder.setPositiveButton("Ok", (dialog, which) -> dialog.dismiss());
            }
        }
        builder.show();
    }

    private void notifyHostUpdated(String hostId) {
        if (hostUpdatedConsumer != null) {
            ((Activity) context).runOnUiThread(() -> hostUpdatedConsumer.accept(hostId));
        }
    }

    public void saveNucleiHost(NucleiHost host) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, host.getRandom_id());
        if (index != -1) {
            hosts.set(index, host);
        } else {
            hosts.add(host);
        }
        setNucleiHosts(hosts);
        notifyHostUpdated(host.getRandom_id());
    }

    public void updateNucleiHost(NucleiHost updatedHost) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, updatedHost.getRandom_id());
        if (index != -1) {
            hosts.set(index, updatedHost);
            setNucleiHosts(hosts);
            notifyHostUpdated(updatedHost.getRandom_id());
        }
    }

    public void deleteNucleiHost(String hostId) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, hostId);
        if (index != -1) {
            hosts.remove(index);
            setNucleiHosts(hosts);
            notifyHostUpdated(hostId);
        }
    }

    public void addNucleiVuln(String hostId, NucleiVuln vuln) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, hostId);
        if (index != -1) {
            NucleiHost host = hosts.get(index);
            host.addVulnerability(vuln);
            hosts.set(index, host);
            setNucleiHosts(hosts);
            notifyHostUpdated(hostId);
        }
    }

    public void updateNucleiVuln(String hostId, NucleiVuln updatedVuln) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, hostId);
        if (index != -1) {
            NucleiHost host = hosts.get(index);
            List<NucleiVuln> vulns = host.getVulnerabilities();
            int vulnIndex = findVulnIndex(vulns, updatedVuln.getTemplateId());
            if (vulnIndex != -1) {
                vulns.set(vulnIndex, updatedVuln);
                host.setVulnerabilities(vulns);
                hosts.set(index, host);
                setNucleiHosts(hosts);
                notifyHostUpdated(hostId);
            }
        }
    }

    public void deleteNucleiVuln(String hostId, String templateId) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, hostId);
        if (index != -1) {
            NucleiHost host = hosts.get(index);
            List<NucleiVuln> vulns = host.getVulnerabilities();
            int vulnIndex = findVulnIndex(vulns, templateId);
            if (vulnIndex != -1) {
                vulns.remove(vulnIndex);
                host.setVulnerabilities(vulns);
                hosts.set(index, host);
                setNucleiHosts(hosts);
                notifyHostUpdated(hostId);
            }
        }
    }

    public NucleiHost getNucleiHostById(String hostId) {
        List<NucleiHost> hosts = getNucleiHosts();
        int index = findHostIndex(hosts, hostId);
        return index != -1 ? hosts.get(index) : null;
    }

    private List<NucleiHost> getNucleiHosts() {
        String json = getString(NUCLEI_HOSTS_KEY);
        if (json.isEmpty()) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<List<NucleiHost>>(){}.getType();
        return new Gson().fromJson(json, type);
    }

    private void setNucleiHosts(List<NucleiHost> hosts) {
        String json = new Gson().toJson(hosts);
        setString(NUCLEI_HOSTS_KEY, json);
    }

    private int findHostIndex(List<NucleiHost> hosts, String hostId) {
        for (int i = 0; i < hosts.size(); i++) {
            if (hosts.get(i).getRandom_id().equals(hostId)) {
                return i;
            }
        }
        return -1;
    }

    private int findVulnIndex(List<NucleiVuln> vulns, String templateId) {
        for (int i = 0; i < vulns.size(); i++) {
            if (vulns.get(i).getTemplateId().equals(templateId)) {
                return i;
            }
        }
        return -1;
    }


    public static void getFavicon(Context context, String websiteUrl, Consumer<Drawable> onComplete) {
        new Thread(() -> {
            Drawable favicon = downloadFavicon(context, websiteUrl);
            new Handler(Looper.getMainLooper()).post(() -> onComplete.accept(favicon));
        }).start();
    }

    private static Drawable downloadFavicon(Context context, String websiteUrl) {
        try {
            // Connect to the website and get the HTML
            Document doc = Jsoup.connect(websiteUrl).get();

            // Look for favicon in HTML
            Elements elements = doc.select("link[rel~=(?i)^(shortcut )?icon]");
            String faviconUrl = elements.isEmpty() ? websiteUrl + "/favicon.ico" : elements.first().attr("abs:href");

            // Download the favicon
            URL url = new URL(faviconUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.connect();
            InputStream input = connection.getInputStream();
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(input);

            // Convert Bitmap to Drawable
            return new BitmapDrawable(context.getResources(), bitmap);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void replaceFragment(Fragment fragment) {
        fragmentManager.beginTransaction()
                .replace(R.id.main, fragment)
                .commit();
    }

    public void replaceFragment(Fragment fragment, String s) {
        fragmentManager.beginTransaction()
                .replace(R.id.main, fragment)
                .commit();
    }

    public Fragment getVisibleFragment(){
        List<Fragment> fragments = fragmentManager.getFragments();
        if(fragments != null){
            for(Fragment fragment : fragments){
                if(fragment != null && fragment.isVisible())
                    return fragment;
            }
        }
        return null;
    }
}