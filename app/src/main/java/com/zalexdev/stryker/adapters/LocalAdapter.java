package com.zalexdev.stryker.adapters;

import static com.zalexdev.stryker.utils.TextStyler.convert;
import static com.zalexdev.stryker.utils.TextStyler.info;

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

import com.facebook.shimmer.ShimmerFrameLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textview.MaterialTextView;
import com.zalexdev.stryker.R;
import com.zalexdev.stryker.objects.Device;
import com.zalexdev.stryker.objects.Port;
import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.FileUtils;
import com.zalexdev.stryker.utils.Preferences;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LocalAdapter extends RecyclerView.Adapter<LocalAdapter.LocalViewHolder> {

    private List<Device> localItemList;

    private final Preferences preferences;

    private final String[] services = "adam6500 asterisk afp cisco cisco-enable cobaltstrike cvs ftp ftps http-proxy http-proxy-urlenum icq imap irc ldap2 memcached mongodb mssql mysql nntp oracle-listener oracle-sid pcanywhere pcnfs pop3 postgres radmin2 rdp redis rexec rlogin rpcap rsh rtsp s7-300 sip smb smb2 smtp smtp-enum snmp socks5 ssh sshkey svn teamspeak telnet vmauthd vnc xmpp".split(" ");


    public LocalAdapter(List<Device> wifiItemList) {
        //sort by last octet
        wifiItemList.sort((o1, o2) -> {
            String[] octets1 = o1.getIp().split("\\.");
            String[] octets2 = o2.getIp().split("\\.");
            return Integer.parseInt(octets1[3]) - Integer.parseInt(octets2[3]);
        });
        this.localItemList = wifiItemList;
        preferences = Preferences.getInstance();
        
    }

    

    @NonNull
    @Override
    public LocalViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_local, parent, false);
        return new LocalViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LocalViewHolder holder, int position) {
        Device item = localItemList.get(position);
        holder.ip.setText(item.getIp());

        if (item.isMe()){
            holder.ip.setText(item.getIp()+" (This device)");
            holder.cardView.setCardBackgroundColor(AppCompatResources.getColorStateList(preferences.getContext(), R.color.green));
            holder.cardView.setOnClickListener(v -> Snackbar.make(v, "This is your device. Just roll it around if you want to see more details :)", Snackbar.LENGTH_LONG).show());
        }else if (!item.isShim()){
            holder.cardView.setOnClickListener(v -> displayDialog(item));
        }
        if (preferences.isHideMac()){
            holder.mac.setText("XX:XX:XX:XX:XX:XX");
        }else {
            holder.mac.setText(item.getMac());
        }
        holder.vendor.setText(item.getVendor());
        holder.ports.setText("Ports: "+item.getPorts().size());

        holder.img.setImageResource(item.getImage());
        if (item.isShim()) {
            holder.shim.startShimmerAnimation();
        }
        if (item.isIscutted()){
            holder.netcut.setVisibility(View.VISIBLE);
        }else{
            holder.netcut.setVisibility(View.GONE);
        }

        for (Port p : item.getPorts()) {
            if (p.getPortNum() == 80 || p.getPortNum() == 8080 || p.getPortNum() == 443 || p.getPortNum() == 8443 || p.getPortNum() == 8081) {
                holder.web.setVisibility(View.VISIBLE);
            }
            if (p.getPortNum() == 22 || p.getPortNum() == 23){
                holder.ssh.setVisibility(View.VISIBLE);
            }
            if (p.getPortNum() == 445 || p.getPortNum() == 21 ){
                holder.smb.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return localItemList.size();
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return position;
    }

    class LocalViewHolder extends RecyclerView.ViewHolder {
        private final TextView ip;
        private final TextView mac;
        private final TextView vendor;
        private final TextView ports;
        private final ShimmerFrameLayout shim;
        private final ImageView img;
        private final ImageView web;
        private final ImageView netcut;
        private ImageView ssh;
        private final ImageView smb;
        private final MaterialCardView cardView;


        LocalViewHolder(View itemView) {
            super(itemView);
            ip = itemView.findViewById(R.id.ip);
            mac = itemView.findViewById(R.id.mac);
            vendor = itemView.findViewById(R.id.vendor);
            ports = itemView.findViewById(R.id.ports);
            web = itemView.findViewById(R.id.web);
            ssh = itemView.findViewById(R.id.ssh);
            smb = itemView.findViewById(R.id.smb);
            cardView = itemView.findViewById(R.id.card);
            img = itemView.findViewById(R.id.image);
            netcut = itemView.findViewById(R.id.cut);
            shim = itemView.findViewById(R.id.shim);

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





        public void appendToTerminal(String text, TextView terminal) {
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

    public void updateList(List<Device> list){
        localItemList = list;
        sortList();
    }

    public void clearList(){
        localItemList.clear();
        notifyDataSetChanged();
    }

    public void removeItem(int position){
        localItemList.remove(position);
        notifyItemRemoved(position);
    }

    public void addItem(Device device){
        localItemList.add(device);
        notifyItemInserted(localItemList.size()-1);
    }

    public void updateItem(Device device, int position){
        localItemList.set(position, device);
        notifyItemChanged(position);
    }

    public void updateItemByIp(Device device){
        for (int i = 0; i < localItemList.size(); i++) {
            if (localItemList.get(i).getIp().equals(device.getIp())){
                localItemList.set(i, device);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void updateItemByMac(Device device){
        for (int i = 0; i < localItemList.size(); i++) {
            if (localItemList.get(i).getMac().equals(device.getMac())){
                localItemList.set(i, device);
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void sortList(){
        localItemList.sort((o1, o2) -> {
            String[] octets1 = o1.getIp().split("\\.");
            String[] octets2 = o2.getIp().split("\\.");
            return Integer.parseInt(octets1[3]) - Integer.parseInt(octets2[3]);
        });
        notifyDataSetChanged();
    }

    void displayDialog(Device item) {
        Dialog dialog = new Dialog(preferences.getContext());
        dialog.setContentView(R.layout.dialog_local);
        dialog.setCancelable(true);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        TextView name = dialog.findViewById(R.id.name);
        TextView bssid = dialog.findViewById(R.id.bssid);
        TextView port = dialog.findViewById(R.id.port);
        TextView manufacture = dialog.findViewById(R.id.manufacture);
        ImageView img = dialog.findViewById(R.id.device_img);
        LinearLayout brute = dialog.findViewById(R.id.bruteforce);
        LinearLayout megacut = dialog.findViewById(R.id.megacut_on);
        LinearLayout megacutoff = dialog.findViewById(R.id.megacut_off);
        name.setText(item.getIp());
        if (preferences.isHideMac()){
            bssid.setText("XX:XX:XX:XX:XX:XX");
        } else {
            bssid.setText(item.getMac());
        }
        img.setImageResource(item.getImage());
        port.setText("Ports: "+item.getPorts().size());
        manufacture.setText(item.getVendor());
        img.setOnClickListener(v -> displayPortDialog(item));
        brute.setOnClickListener(v -> requirementsDialogHydra(item));
        megacut.setOnClickListener(v -> {
            requirementsDialogMegacut(item, 0, localItemList.indexOf(item));
            Snackbar.make(v, "No internet for this guy :)", Snackbar.LENGTH_SHORT).show();
        });
        megacutoff.setOnClickListener(v -> {
            requirementsDialogMegacut(item, 1, localItemList.indexOf(item));
            Snackbar.make(v, "Ok ok turning on things...", Snackbar.LENGTH_SHORT).show();
        });


        dialog.show();
    }

    void displayPortDialog(Device item) {
        Dialog dialog = new Dialog(preferences.getContext());
        dialog.setContentView(R.layout.dialog_port);
        dialog.setCancelable(true);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView port_text = dialog.findViewById(R.id.ports_text);
        RecyclerView recyclerView = dialog.findViewById(R.id.ports);
        recyclerView.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(preferences.getContext()));
        LocalPortsAdapter adapter = new LocalPortsAdapter(item.getPorts());
        recyclerView.setAdapter(adapter);
        port_text.setText("Ports: "+item.getPorts().size());
        dialog.show();
    }

    void requirementsDialogHydra(Device item){
        ExecutorBuilder check = new ExecutorBuilder();
        check.setChroot(true);
        check.setCommand("ls /usr/bin/hydra");
        check.setActivity(preferences.getActivity());
        check.setOnFinished(strings -> {
            if (strings.isEmpty() || !strings.get(0).equals("/usr/bin/hydra")) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(preferences.getContext());
                builder.setTitle("Requirements");
                builder.setMessage("This feature requires additional files to be downloaded. Do you want to download them now?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    displayInstallingReqHydra();
                    dialog.dismiss();
                });
                builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                builder.show();
            } else {
                bruteforceDialog(item);
            }
        });
        check.execute();

    }
    public String getGateway() {
        return localItemList.get(0).getIp();
    }


    void requirementsDialogMegacut(Device item, int t, int p){
        ExecutorBuilder check = new ExecutorBuilder();
        check.setChroot(true);
        check.setCommand("ls /megacut/megacut.py");
        check.setActivity(preferences.getActivity());
        check.setOnFinished(strings -> {
            if (strings.isEmpty() || !strings.get(0).equals("/megacut/megacut.py")) {
                MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(preferences.getContext());
                builder.setTitle("Requirements");
                builder.setMessage("This feature requires additional files to be downloaded. Do you want to download them now?");
                builder.setPositiveButton("Yes", (dialog, which) -> {
                    displayInstallingReqMegacut();
                    dialog.dismiss();
                });
                builder.setNegativeButton("No", (dialog, which) -> dialog.dismiss());
                builder.show();
            } else {

                if (t == 0){
                    ExecutorBuilder executorBuilder = new ExecutorBuilder();
                    executorBuilder.setChroot(true);
                    executorBuilder.setCommand("python /megacut/megacut.py " +getGateway() + " " + item.getIp() +" -k");
                    executorBuilder.setActivity(preferences.getActivity());
                    executorBuilder.setOutput(s -> Log.d("Megacut", s));
                    executorBuilder.execute();
                    item.setCutted(true);
                    updateItem(item, p);
                }else{
                    ExecutorBuilder executorBuilder = new ExecutorBuilder();
                    executorBuilder.setChroot(true);
                    executorBuilder.setCommand("python /megacut/megacut.py " +getGateway() + " " + item.getIp()+" -r");
                    executorBuilder.setActivity(preferences.getActivity());
                    executorBuilder.setOutput(s -> Log.d("Megacut", s));
                    executorBuilder.execute();
                    item.setCutted(false);
                    updateItem(item, p);
                }
            }
        });
        check.execute();

    }

    void displayInstallingReqHydra() {
        Dialog dialog = new Dialog(preferences.getContext());
        dialog.setContentView(R.layout.dialog_req);
        dialog.setCancelable(true);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView desc = dialog.findViewById(R.id.desc);
        MaterialButton ok = dialog.findViewById(R.id.ok);
        MaterialButton cancel = dialog.findViewById(R.id.cancel);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
        });
        LinearProgressIndicator progress = dialog.findViewById(R.id.progress);
        progress.setIndeterminate(false);
        FileUtils fileUtils = new FileUtils();
        fileUtils.downloadFile(preferences.getActivity(), "https://strykerdefence.com/chroot/wordlists", "wordlists.zip", progress::setProgress,
                s -> {
                }, aBoolean -> {
            progress.setIndeterminate(true);
                    if (aBoolean) {
                        desc.setText("Wordlists downloaded successfully.");
                        SuUtils.createFolder("/data/local/stryker5/release/hydra");
                        SuUtils.moveFile(FileUtils.basePath+"/wordlists.zip", "/data/local/stryker5/release/hydra/wordlists.zip");
                        ExecutorBuilder executorBuilder = new ExecutorBuilder();
                        executorBuilder.setChroot(true);
                        executorBuilder.setCommand("unzip /hydra/wordlists.zip -d /hydra");
                        executorBuilder.setActivity(preferences.getActivity());
                        executorBuilder.setOutput(s -> {
                            Log.d("Unzip", s);
                            desc.setText("Unzipping wordlists: "+s);
                        });
                        executorBuilder.setOnFinished(strings -> SuUtils.checkFileOrFolder("/data/local/stryker5/release/hydra/usernames", aBoolean1 -> {
                            if (aBoolean1) {
                                desc.setText("Wordlists unzipped successfully.");

                                ExecutorBuilder hydra = new ExecutorBuilder();
                                hydra.setChroot(true);
                                hydra.setCommand("apk add hydra");
                                hydra.setActivity(preferences.getActivity());
                                hydra.setOutput(s -> {
                                    Log.d("Hydra", s);
                                    desc.setText(s);
                                });
                                hydra.setOnFinished(strings1 -> {
                                    if (hydra.exitCodeInt == 0) {
                                        desc.setText("Hydra installed successfully.");
                                        ok.setEnabled(true);
                                        ok.setText("Done");
                                        cancel.setVisibility(View.GONE);
                                        progress.setVisibility(View.INVISIBLE);
                                    }else {
                                        desc.setText("Error installing hydra.");
                                    }
                                });
                                hydra.execute();
                            }else {
                                desc.setText("Error unzipping wordlists.");
                            }
                        }));
                        executorBuilder.execute();
                    }else {
                        desc.setText("Error downloading wordlists.");
                    }
                });
        dialog.show();
    }

    void displayInstallingReqMegacut() {
        Dialog dialog = new Dialog(preferences.getContext());
        dialog.setContentView(R.layout.dialog_req);
        dialog.setCancelable(true);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        TextView desc = dialog.findViewById(R.id.desc);
        MaterialButton ok = dialog.findViewById(R.id.ok);
        MaterialButton cancel = dialog.findViewById(R.id.cancel);
        ok.setOnClickListener(v -> {
            dialog.dismiss();
        });
        LinearProgressIndicator progress = dialog.findViewById(R.id.progress);
        progress.setIndeterminate(true);
        ExecutorBuilder executorBuilder = new ExecutorBuilder();
        executorBuilder.setChroot(true);
        executorBuilder.setCommand("git clone https://github.com/stryker-project/megacut");
        executorBuilder.setActivity(preferences.getActivity());
        executorBuilder.setOutput(s -> {
            Log.d("Megacut", s);
            desc.setText(s);
        });
        executorBuilder.setOnFinished(strings -> {
            if (executorBuilder.exitCodeInt == 0) {
                desc.setText("Megacut downloaded successfully.");
                ExecutorBuilder pip = new ExecutorBuilder();
                pip.setChroot(true);
                pip.setCommand("pip install -r /megacut/requirements.txt --break-system-packages");
                pip.setActivity(preferences.getActivity());
                pip.setOutput(s -> {
                    Log.d("Megacut", s);
                    desc.setText(s);
                });
                pip.setOnFinished(strings1 -> {
                    if (pip.exitCodeInt == 0) {
                        desc.setText("Megacut installed successfully.");
                        ok.setEnabled(true);
                        ok.setText("Done");
                        cancel.setVisibility(View.GONE);
                        progress.setVisibility(View.INVISIBLE);
                    }else {
                        desc.setText("Error installing megacut.");
                    }
                });
                pip.execute();
            }else {
                desc.setText("Error installing megacut.");
            }
        });
        executorBuilder.execute();
        ok.setEnabled(false);

        dialog.show();
    }

    public void appendToTerminal(String text, TextView terminal) {
        Log.d("LocalAdapter", "appendToTerminal: "+text);
        if (preferences.isHideMac()){
            Matcher m = Pattern.compile("((\\w{2}:){5}\\w{2})").matcher(text);
            while (m.find()) {
                text = text.replace(m.group(), "XX:XX:XX:XX:XX:XX");
            }
        }
        if (terminal.getText().toString().split("\n").length > 150){
            terminal.setText("");
        }
        terminal.append(convert(text+"<br>"));
    }

    void bruteforceDialog(Device item){
        Dialog dialog = new Dialog(preferences.getContext());
        dialog.setContentView(R.layout.dialog_local_bruteforce);
        dialog.setCancelable(true);
        Objects.requireNonNull(dialog.getWindow()).setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ImageView img = dialog.findViewById(R.id.device_img);
        TextView ip = dialog.findViewById(R.id.ip);
        TextView mac = dialog.findViewById(R.id.mac);
        MaterialAutoCompleteTextView port = dialog.findViewById(R.id.port);
        MaterialAutoCompleteTextView service = dialog.findViewById(R.id.service);
        MaterialAutoCompleteTextView users = dialog.findViewById(R.id.users);
        MaterialAutoCompleteTextView pass = dialog.findViewById(R.id.pass);
        port.setSimpleItems(item.portsArrayToString().split(","));
        service.setSimpleItems(services);
        MaterialButton start = dialog.findViewById(R.id.start);
        MaterialTextView terminal = dialog.findViewById(R.id.terminalView);
        MaterialCardView terminal_card = dialog.findViewById(R.id.output_card);
        img.setImageResource(item.getImage());
        ip.setText(item.getIp());
        mac.setText(item.getMac());
        LinearLayout conf = dialog.findViewById(R.id.conf);
        ArrayList<String> usernames = ExecutorBuilder.runCommandChroot("ls /hydra/usernames");
        String[] userArray = new String[usernames.size()];
        usernames.toArray(userArray);
        users.setSimpleItems(userArray);
        ArrayList<String> passwords = ExecutorBuilder.runCommandChroot("ls /hydra/passwords");
        String[] passArray = new String[passwords.size()];
        passwords.toArray(passArray);
        pass.setSimpleItems(passArray);
        appendToTerminal(info("Service Bruteforce attack powered by Hydra-HTC | Stryker v5.0B"), terminal);
        start.setOnClickListener(v -> {
            if (port.getText().toString().isEmpty() || service.getText().toString().isEmpty() || users.getText().toString().isEmpty() || pass.getText().toString().isEmpty()){
                Snackbar.make(v, "Please fill all fields", Snackbar.LENGTH_SHORT).show();
            }else {
                start.setText("Cancel");
                terminal_card.setVisibility(View.VISIBLE);
                conf.setVisibility(View.GONE);
                ExecutorBuilder executorBuilder = new ExecutorBuilder();
                executorBuilder.setChroot(true);
                executorBuilder.setCommand("hydra -V -L /hydra/usernames/"+users.getText().toString()+" -P /hydra/passwords/"+pass.getText().toString()+" "+service.getText().toString()+"://"+item.getIp()+":"+port.getText().toString());
                appendToTerminal(info("Starting bruteforce attack on "+item.getIp()+" with service "+service.getText().toString()+" on port "+port.getText().toString()), terminal);
                executorBuilder.setActivity(preferences.getActivity());
                executorBuilder.setOutput(s -> appendToTerminal(s, terminal));
                executorBuilder.setOnFinished(strings -> {
                    appendToTerminal(info("Bruteforce attack finished"), terminal);
                    start.setText("Close");
                    start.setOnClickListener(v1 -> dialog.dismiss());
                });
                executorBuilder.execute();
                start.setOnClickListener(v1 -> {
                    executorBuilder.kill();
                    ExecutorBuilder.runCommandChroot("pkill hydra");
                    start.setText("Close");
                    start.setOnClickListener(v2 -> dialog.dismiss());
                });
            }
        });
        dialog.show();
    }
}
