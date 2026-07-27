package com.zalexdev.stryker.su;

import static com.zalexdev.stryker.utils.ExecutorBuilder.contains;

import android.app.Activity;
import android.util.Log;

import com.zalexdev.stryker.objects.Interface;
import com.zalexdev.stryker.utils.DebugData;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.Preferences;
import com.zalexdev.stryker.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SuUtils {
    public static final String TAG = "SuUtils";
    public static final String CHROOT_PATH = "/data/local/stryker5/release/";
    public static final String MOUNTED_CHROOT_PATH = "/data/local/stryker5/release/sdcard/Stryker5/";
    public static final String UNMOUNTED_CHROOT_PATH = "/data/local/stryker5/release/";
    public static String mountFile = Utils.fileDir + "bootroot ";
    public static String unMountFile =  Utils.fileDir + "killroot ";
    public static String execute = Utils.fileDir + "chroot_exec ";
    public static String busybox = "/data/data/com.zalexdev.stryker5/files/busybox ";
    public static String tar = "/data/data/com.zalexdev.stryker5/files/busybox tar ";
    public static String chmod = "chmod 777 ";
    public static String mkdir = "mkdir ";
    public static String rm = "rm -rf ";
    public static String cp = "cp -R ";
    public static String mv = "mv  ";

    public static boolean isRoot() {
        return contains(ExecutorBuilder.runCommand("id"), "uid=0");
    }



    public static void mountChroot(Consumer<String> lines, Consumer<Boolean> ok) {
        ExecutorBuilder.runCommand("chmod 777 " + Utils.fileDir + "*");
        ExecutorBuilder.customMegaCommand(mountFile, strings -> {
            if (lines != null) {
                for (String line : strings) {
                    lines.accept(line);
                }
            }
            if (ok != null) {
                if (contains(strings, "The Chroot has been started")) {
                    ok.accept(true);
                } else {
                    checkFileOrFolder(CHROOT_PATH+"sdcard/handshakes", ok);
                }
            }
        });
    }

    public static void unMountChroot(Consumer<Boolean> ok) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand(unMountFile);
        executorBuilder.setOnFinished((output) -> {
            Log.d(TAG, "unMountChroot: " + output);
            if (ok != null) {
                if (output.contains("[+] All done.")) {
                    ok.accept(true);
                } else {
                    ok.accept(false);
                }
            }

        });
        executorBuilder.execute();
    }

    public static void createFolder(String folder) {
        ExecutorBuilder.runCommand(mkdir + folder);
    }

    public static void copyFile(String source, String destination) {
        ExecutorBuilder.runCommand(cp + source + " " + destination);
    }

    public static void moveFile(String source, String destination) {
        ExecutorBuilder.runCommand(mv + source + " " + destination);
    }

    public static String readFile(String file) {
        StringBuilder output = new StringBuilder();
        ArrayList<String> lines = ExecutorBuilder.runCommand("cat " + file);
        for (String line : lines) {
            output.append(line).append("\n");
        }
        return output.toString();
    }

    public static void removeFile(String file) {
        ExecutorBuilder.runCommand(rm + file);
    }

    public static void getInterfaces(Activity activity, int type, Consumer<ArrayList<Interface>> output) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand("iw dev");
        executorBuilder.setChroot(true);
        executorBuilder.setActivity(activity);
        executorBuilder.setOnFinished(strings -> {
            ArrayList<Interface> interfaces = Interface.parse(strings);
            if (type == 0) {
                activity.runOnUiThread(() -> output.accept(interfaces));
            } else if (type == 1){
                //filter only type managed
                ArrayList<Interface> managed = new ArrayList<>();
                for (Interface iface : interfaces) {
                    if (iface.type.equals("managed")) {
                        managed.add(iface);
                    }
                }
                activity.runOnUiThread(() -> output.accept(managed));
            } else if (type == 2){
                //filter only type AP
                ArrayList<Interface> ap = new ArrayList<>();
                for (Interface iface : interfaces) {
                    if (iface.type.equals("AP")) {
                        ap.add(iface);
                    }
                }
                activity.runOnUiThread(() -> output.accept(ap));
            } else if (type == 3){
                //filter only type monitor
                ArrayList<Interface> monitor = new ArrayList<>();
                for (Interface iface : interfaces) {
                    if (iface.type.equals("monitor")) {
                        monitor.add(iface);
                    }
                }
                activity.runOnUiThread(() -> output.accept(monitor));
            }
        });
        executorBuilder.execute();

    }

    public static ArrayList<Interface> getInterfaces(int type) {
        ArrayList<String> output = ExecutorBuilder.runCommandChroot("iw dev");
        ArrayList<Interface> interfaces = Interface.parse(output);
        if (type == 0) {
            return interfaces;
        } else if (type == 1){
            //filter only type managed
            ArrayList<Interface> managed = new ArrayList<>();
            for (Interface iface : interfaces) {
                if (iface.type.equals("managed")) {
                    managed.add(iface);
                }
            }
            return managed;
        } else if (type == 2){
            //filter only type AP
            ArrayList<Interface> ap = new ArrayList<>();
            for (Interface iface : interfaces) {
                if (iface.type.equals("AP")) {
                    ap.add(iface);
                }
            }
            return ap;
        } else if (type == 3){
            //filter only type monitor
            ArrayList<Interface> monitor = new ArrayList<>();
            for (Interface iface : interfaces) {
                if (iface.type.equals("monitor")) {
                    monitor.add(iface);
                    Log.d(TAG, "getInterfaces Monitor: " + iface.name);
                }
            }
            return monitor;
        }else{
            return new ArrayList<>();
        }

    }

    public ArrayList<String> getFileNames(String folderName) {
        AtomicReference<ArrayList<String>> fileNames = new AtomicReference<>(new ArrayList<>());
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand("ls " + folderName);
        executorBuilder.setOnFinished(fileNames::set);
        executorBuilder.execute();
        return fileNames.get();
    }

    public void getFileNames(Activity activity, String folderName, Consumer<ArrayList<String>> output) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand("ls " + folderName);
        executorBuilder.setActivity(activity);
        executorBuilder.setOnFinished(output);
        executorBuilder.execute();

    }


    public static void checkFileOrFolder (String fileOrFolder, Consumer<Boolean> ok) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand("ls " + fileOrFolder);
        executorBuilder.setActivity(Preferences.getInstance().getActivity());
        executorBuilder.setOnFinished((output) -> {
            Log.d(TAG, "checkFileOrFolder: " + output);
            if (ok != null) {
                if (ExecutorBuilder.contains(output,fileOrFolder) || executorBuilder.exitCodeInt == 0) {
                    ok.accept(true);
                } else {
                    ok.accept(false);
                }
            }
        });
        executorBuilder.execute();
    }

    public static void setMonitorModeWlan0(Activity activity, Consumer<Boolean> ok) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand(Preferences.getInstance().getInternalMonCmd());
        executorBuilder.setChroot(Preferences.getInstance().getInternalMonCmdChroot());
        executorBuilder.setActivity(activity);
        executorBuilder.setOnFinished((output) -> {
            new Thread(() -> {
                ArrayList<Interface> mon = getInterfaces(3);
                if (!mon.isEmpty()) {
                    for (Interface iface : mon) {
                        if (iface.name.equals("wlan0")) {
                            activity.runOnUiThread(() -> ok.accept(true));
                            return;
                        }
                    }
                    activity.runOnUiThread(() -> ok.accept(false));
                } else {
                    activity.runOnUiThread(() -> ok.accept(false));
                }
            });
        });
        Log.d(TAG, "setMonitorModeWlan0: " + executorBuilder.getCommand() + " " + executorBuilder.isChroot());
        executorBuilder.execute();

    }

    public static void setMonitorModeWlan1(Activity activity,String wlan, Consumer<Boolean> ok) {
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setCommand(Preferences.getInstance().getExternalMonCmd(wlan));
        executorBuilder.setChroot(Preferences.getInstance().getExternalMonCmdChroot());
        executorBuilder.setActivity(activity);
        executorBuilder.setOnFinished((output) -> {
            new Thread(() -> {
                ArrayList<Interface> mon = getInterfaces(3);
                if (!mon.isEmpty()) {
                    for (Interface iface : mon) {
                        if (iface.name.equals("wlan1")) {
                            activity.runOnUiThread(() -> ok.accept(true));
                            return;
                        }
                    }
                    activity.runOnUiThread(() -> ok.accept(false));
                } else {
                    activity.runOnUiThread(() -> ok.accept(false));
                }
            });
        });
        Log.d(TAG, "setMonitorModeWlan1: " + executorBuilder.getCommand() + " " + executorBuilder.isChroot());
        executorBuilder.execute();
    }

    public static boolean setMonitorModeWlan0() {
        if (Preferences.getInstance().getInternalMonCmdChroot()){
            ExecutorBuilder.runCommandChroot(Preferences.getInstance().getInternalMonCmd());

        }else {
            ExecutorBuilder.runCommand(Preferences.getInstance().getInternalMonCmd());
        }
        ArrayList<Interface> mon = getInterfaces(3);
        if (!mon.isEmpty()) {
            for (Interface iface : mon) {
                if (iface.name.equals("wlan0")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean setMonitorModeWlan1(String wlan) {
        if (Preferences.getInstance().getExternalMonCmdChroot()){
            ExecutorBuilder.runCommandChroot(Preferences.getInstance().getExternalMonCmd(wlan));
        }else {
            ExecutorBuilder.runCommand(Preferences.getInstance().getExternalMonCmd(wlan));
        }
        ArrayList<Interface> mon = getInterfaces(3);
        if (!mon.isEmpty()) {
            for (Interface iface : mon) {
                Log.d(TAG, "setMonitorModeWlan1: " + iface.name);
                if (iface.name.equals(wlan)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean setManagedModeWlan0() {
        if (Preferences.getInstance().getInternalMonCmdChroot()){
            ExecutorBuilder.runCommandChroot(Preferences.getInstance().getInternalMonCmdOff());
        }else {
            ExecutorBuilder.runCommand(Preferences.getInstance().getInternalMonCmdOff());
        }
        ArrayList<Interface> mon = getInterfaces(1);
        if (!mon.isEmpty()) {
            for (Interface iface : mon) {
                if (iface.name.equals("wlan0")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean setManagedModeWlan1(String wlan) {
        if (Preferences.getInstance().getExternalMonCmdChroot()){
            ExecutorBuilder.runCommandChroot(Preferences.getInstance().getExternalMonCmdOff(wlan));
        }else {
            ExecutorBuilder.runCommand(Preferences.getInstance().getExternalMonCmdOff(wlan));
        }
        ArrayList<Interface> mon = getInterfaces(1);
        if (!mon.isEmpty()) {
            for (Interface iface : mon) {
                if (iface.name.equals(wlan)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void copyAssets() {
        new Thread(() -> {
            String[] files = null;
            try {
                files = Preferences.getInstance().getContext().getAssets().list("");
            } catch (IOException e) {
                Log.e("tag", "Failed to get asset file list.", e);
            }
            new File(Utils.fileDir).mkdirs();
            assert files != null;
            for (String filename : files) {
                // Skip the large bundled chroot - it is only used once during setup
                if (filename.equals("chroot_bundle.tar.gz")) continue;
                InputStream in = null;
                OutputStream out = null;
                try {
                    in = Preferences.getInstance().getContext().getAssets().open(filename);
                    File outFile = new File(Utils.fileDir, filename);
                    out = Files.newOutputStream(outFile.toPath());
                    copyFile(in, out);
                    in.close();
                    out.flush();
                    out.close();
                } catch (IOException e) {
                    Log.e("tag", "Failed to copy asset file: " + filename, e);
                }
            }
            ExecutorBuilder chmod = new ExecutorBuilder();
            chmod.setCommand("chmod 777 " + Utils.fileDir+"*");
            chmod.execute();
        }).start();
    }

    public static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }


}