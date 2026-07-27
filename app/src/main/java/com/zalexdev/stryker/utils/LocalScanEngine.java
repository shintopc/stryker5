package com.zalexdev.stryker.utils;

import static android.content.Context.WIFI_SERVICE;
import static androidx.core.content.ContextCompat.getSystemService;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Xml;

import com.zalexdev.stryker.BuildConfig;
import com.zalexdev.stryker.objects.Device;
import com.zalexdev.stryker.objects.Port;
import com.zalexdev.stryker.su.SuUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.StringReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lombok.Getter;
import lombok.Setter;

public class LocalScanEngine {

    @Getter
    @Setter
    private Consumer<Integer> onProgressUpdate;
    @Getter
    @Setter
    private String interfaceName;
    @Getter
    @Setter
    private boolean isScanning = false;
    @Getter
    @Setter
    private boolean isFinished = true;
    @Getter
    @Setter
    private boolean isCancelled = false;
    @Getter
    @Setter
    private int progress = 0;
    @Getter
    @Setter
    private Consumer<Integer> onFinished;
    @Getter
    @Setter
    private Consumer<Device> onDeviceScanned;
    @Getter
    @Setter
    private Consumer<ArrayList<Device>> onNetworkPreScanned;
    @Getter
    @Setter
    private Consumer<ArrayList<Device>> onNetworkScanned;
    private Activity activity;

    public LocalScanEngine(String interfaceName) {
        this.interfaceName = interfaceName;
        activity = Preferences.getInstance().getActivity();
    }



    public String getGateway() {
        String ipRange = "";
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isLoopback() && networkInterface.isUp()) {
                    Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                    while (addresses.hasMoreElements()) {
                        InetAddress address = addresses.nextElement();
                        if (address instanceof Inet4Address) {
                            String ipAddress = address.getHostAddress();
                            short prefixLength = networkInterface.getInterfaceAddresses().get(0).getNetworkPrefixLength();
                            ipRange = ipAddress + "/" + prefixLength;
                            System.out.println("Local IP Range: " + ipRange);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return ipRange;
    }

    public static ArrayList<String> extractIPsFromXML(String xmlString) {
        ArrayList<String> ipAddresses = new ArrayList<>();
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(new StringReader(xmlString));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tagName = parser.getName();
                    Log.d("LocalScanner", "Tag: " + tagName);
                    if (tagName.equals("address")) {
                        String addrType = parser.getAttributeValue(null, "addrtype");
                        if (addrType != null && addrType.equals("ipv4")) {
                            String ipAddress = parser.getAttributeValue(null, "addr");
                            if (ipAddress != null) {
                                ipAddresses.add(ipAddress);
                            }
                        }
                    }
                }
                eventType = parser.next();
            }
        } catch (XmlPullParserException | IOException e) {
            e.printStackTrace();
        }
        return ipAddresses;
    }


    public void scanNetwork() {
        isScanning = true;
        isFinished = false;
        String gateway = getGateway();
        if (gateway.isEmpty()) {
            activity.runOnUiThread(() -> getOnFinished().accept(-3));
            return;
        }
        ArrayList<String> ips = new ArrayList<>();
        ExecutorBuilder nmap = new ExecutorBuilder();
        nmap.setActivity(activity);
        nmap.setChroot(true);
        nmap.setCommand("nmap " + gateway + " -sn -PE -n -PP -T4 --stats-every 1s -oX /sdcard/.temp/report.xml");
        nmap.setOutput(s -> {
            Matcher per = Pattern.compile("[0-9]*\\.[0-9]+%").matcher(s);
            if (per.find()) {
                onProgressUpdate.accept((int) Double.parseDouble(per.group().replace("%", "")));
            }
        });
        nmap.setOnFinished(strings -> {
            String xml = SuUtils.readFile("/sdcard/Stryker5/.temp/report.xml");
            Log.d("LocalScanner", "nmap: " + xml);
            ArrayList<String> ipAddresses = extractIPsFromXML(xml);
            for (String ipAddress : ipAddresses) {
                if (!ips.contains(ipAddress)) {
                    ips.add(ipAddress);
                }
            }
            Log.d("LocalScanner", "nmap: " + ips);
        });
        nmap.execute();
        ExecutorBuilder arp = new ExecutorBuilder();
        arp.setActivity(activity);
        arp.setChroot(true);
        arp.setCommand("arp -a");
        arp.setOnFinished(strings -> {
            for (String s : strings) {
                Matcher m = Pattern.compile("((\\w{2}:){5}\\w{2})").matcher(s);
                if (m.find()) {
                    String ip = s.substring(s.indexOf("(") + 1, s.indexOf(")"));
                    if (!ips.contains(ip)) {
                        ips.add(ip);
                    }
                }
            }
            Log.d("LocalScanner", "arp: " + ips);
        });
        arp.execute();
        ExecutorBuilder arp_scan = new ExecutorBuilder();
        arp_scan.setActivity(activity);
        arp_scan.setChroot(true);
        arp_scan.setCommand("arp-scan -I " + interfaceName + " -l");
        arp_scan.setOnFinished(strings -> {
            for (String s : strings) {
                Matcher m = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b").matcher(s);
                if (m.find()) {
                    String ip = m.group();
                    if (!ips.contains(ip)) {
                        ips.add(ip);
                    }
                }
            }
            Log.d("LocalScanner", "arp-scan: " + ips);
        });
        arp_scan.execute();
        ExecutorBuilder ping = new ExecutorBuilder();
        ping.setActivity(activity);
        ping.setChroot(true);
        ping.setCommand("fping -a -g " + gateway + " 2>/dev/null");
        if (Integer.parseInt(gateway.split("/")[1]) > 24) {
            ping.setCommand("fping -a -g " + gateway.substring(0, gateway.lastIndexOf(".")) + ".0/24 2>/dev/null");
        }
        ping.setOnFinished(strings -> {
            for (String s : strings) {
                if (!ips.contains(s)) {
                    ips.add(s);
                }
            }
            Log.d("LocalScanner", "fping: " + ips);
            ExecutorBuilder.runCommandChroot("pkill fping");
        });
        ping.execute();
        new Thread(() -> {
            while (true) {
                if (!nmap.isAlive() && !arp.isAlive() && !arp_scan.isAlive() && !ping.isAlive()) {
                    break;
                }
            }
            Log.d("LocalScanner", "All commands finished");
            Log.d("LocalScanner", ips.toString());
            isScanning = false;
            activity.runOnUiThread(() -> scanAllDevices(ips, onDeviceScanned, onNetworkScanned));
            ArrayList<Device> devices = new ArrayList<>();
            for (String ip : ips) {
                Device temp = new Device();
                temp.setIp(ip);
                devices.add(temp);
            }
            activity.runOnUiThread(() -> onNetworkPreScanned.accept(devices));
        }).start();
    }

    public static String getMacByIp(String ipAddress) {
        try {
            // Convert the IP address string to an InetAddress object
            InetAddress inetAddress = InetAddress.getByName(ipAddress);

            // Get the network interface for the IP address
            NetworkInterface networkInterface = NetworkInterface.getByInetAddress(inetAddress);
            if (networkInterface == null) {
                return null;
            }

            // Get the MAC address bytes
            byte[] macBytes = networkInterface.getHardwareAddress();
            if (macBytes == null) {
                return null;
            }

            // Convert the MAC address bytes to a string
            StringBuilder builder = new StringBuilder();
            for (byte b : macBytes) {
                builder.append(String.format("%02X:", b));
            }
            if (builder.length() > 0) {
                builder.deleteCharAt(builder.length() - 1);
            }

            return builder.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void scanDeviceByIp(String ip, Consumer<Device> onDeviceScanned) {
        WifiManager wm = (WifiManager) activity.getSystemService(WIFI_SERVICE);
        assert wm != null;
        String ipme = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());




        Device device = new Device();
        device.setIp(ip);
        device.setShim(false);
        ExecutorBuilder nmap = new ExecutorBuilder();

        nmap.setActivity(activity);
        nmap.setChroot(true);
        nmap.setCommand("nmap " + ip + " -n -Pn -O -F --max-os-tries=3 --script=banner "+"-oX /sdcard/.temp/"+ip.replace(".","_")+".xml");
        nmap.setOnFinished(strings -> {
            String xml = SuUtils.readFile("/sdcard/Stryker5/.temp/"+ip.replace(".","_")+".xml");
            Port port = new Port("0");
            try {
                XmlPullParser parser = Xml.newPullParser();
                parser.setInput(new StringReader(xml));
                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG) {
                        String tagName = parser.getName();
                        Log.d("LocalScanner", "Tag: " + tagName);
                        if (tagName.equals("address")) {
                            String addrType = parser.getAttributeValue(null, "addrtype");
                            if (addrType != null && addrType.equals("ipv4")) {
                                String ipAddress = parser.getAttributeValue(null, "addr");
                                if (ipAddress != null) {
                                    device.setIp(ipAddress);
                                }
                            } else if (addrType != null && addrType.equals("mac")) {
                                String macAddress = parser.getAttributeValue(null, "addr");
                                if (macAddress != null) {
                                    device.setMac(macAddress);
                                }
                                String vendor = parser.getAttributeValue(null, "vendor");
                                if (vendor != null) {
                                    device.setVendor(vendor);
                                }
                            }
                        } else if (tagName.equals("hostname")) {
                            String hostname = parser.getAttributeValue(null, "name");
                            if (hostname != null) {
                                device.setHostname(hostname);
                            }
                        } else if (tagName.equals("osmatch")) {
                            String osName = parser.getAttributeValue(null, "name");
                            if (osName != null) {
                                device.setOs(osName);
                            }
                        } else if (tagName.equals("port")) {
                            String portid = parser.getAttributeValue(null, "portid");
                            if (portid != null) {
                                if (!port.getPortNumber().equals("0")) {
                                    device.addPort(port);
                                    Log.d("LocalScanner", "Port added: " + port.getPortNumber()+ " " + port.getPortName() + " " + port.getBanner());
                                    port = new Port(portid);
                                }else {
                                    port = new Port(portid);
                                }
                            }
                        } else if (tagName.equals("service")) {
                            String serviceName = parser.getAttributeValue(null, "name");
                            if (serviceName != null) {
                                port.setPortName(serviceName);
                            }
                        } else if (tagName.equals("script")) {
                            String scriptId = parser.getAttributeValue(null, "id");
                            if (scriptId != null && scriptId.equals("banner")) {
                                String banner = parser.getAttributeValue(null, "output");
                                if (banner != null) {
                                    port.setBanner(banner);
                                }
                            }
                        }
                    }
                    eventType = parser.next();
                }
                Log.d("LocalScanner", "Device scanned: " + device.getIp());
                ExecutorBuilder os = new ExecutorBuilder();
                os.setActivity(activity);
                os.setChroot(true);
                os.setCommand("xprobe2  " + device.getIp());
                os.setOnFinished(strings2 -> {
                    for (String s : strings2) {
                        if (s.contains("OS:")) {
                            Matcher m = Pattern.compile("\".*\"").matcher(s);
                            if (m.find()) {
                                device.setOs(m.group().replace("\"", ""));
                                break;
                            }
                        }
                    }
                    new Thread(() -> {
                        try {
                            InetAddress address = InetAddress.getByName(device.getIp());
                            if (address.isReachable(1000)) {
                                String sub = address.getCanonicalHostName();
                                Log.d("LocalScanner", "Sub: " + sub);
                                if (sub.contains(".") && !sub.equals(device.getIp())) {
                                    sub = sub.split("\\.")[0];
                                }

                                device.setHostname(sub);
                                Utils utils = new Utils();
                                String model = utils.getDeviceByCodeNameFromDB(device.getHostname());
                                if (!model.isEmpty()) {
                                    device.setVendor(model);
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Log.d("LocalScanner", "OS: " + device.getOs());
                        activity.runOnUiThread(() -> onDeviceScanned.accept(device));
                    }).start();

                });
                os.execute();

            } catch (XmlPullParserException | IOException e) {
                e.printStackTrace();
            }
        });
        if (ip.equals(ipme)){
            device.setMac(ExecutorBuilder.runCommandChroot("ip link show wlan0 | grep ether | awk '{print $2}'").get(0));
            device.setVendor(Build.MANUFACTURER + " " + Build.MODEL);
            device.setOs("Android");
            device.setMe(true);
            activity.runOnUiThread(() -> onDeviceScanned.accept(device));
        }else{
            nmap.execute();
        }
    }

    public void scanAllDevices(ArrayList<String> ips, Consumer<Device> onDeviceScanned, Consumer<ArrayList<Device>> onNetworkScanned) {
        ArrayList<Device> devices = new ArrayList<>();
        int maxThreads = 6; // Adjust the maximum number of threads as needed
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        for (String ip : ips) {
            executor.submit(() -> scanDeviceByIp(ip, device -> {
                devices.add(device);
                Log.d("LocalScanner", "Device scanned: " + device.getIp());
                activity.runOnUiThread(() -> onDeviceScanned.accept(device));
            }));
        }
        new Thread(() -> {
            while (true) {
                if (devices.size() == ips.size()) {
                    executor.shutdown();
                    break;
                }
            }

            Log.d("LocalScanner", "All devices scanned");
            activity.runOnUiThread(() -> onNetworkScanned.accept(devices));
            activity.runOnUiThread(() -> onProgressUpdate.accept(100));
        }).start();
    }
}
