package com.zalexdev.stryker.appintro.slides;

import static com.zalexdev.stryker.su.SuUtils.TAG;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.airbnb.lottie.LottieProperty;
import com.airbnb.lottie.SimpleColorFilter;
import com.airbnb.lottie.model.KeyPath;
import com.airbnb.lottie.value.LottieValueCallback;
import com.google.android.material.button.MaterialButton;

import com.zalexdev.stryker.R;

import com.zalexdev.stryker.su.SuUtils;
import com.zalexdev.stryker.utils.ExecutorBuilder;
import com.zalexdev.stryker.utils.FileUtils;
import com.zalexdev.stryker.utils.Preferences;

import org.acra.ACRA;

import java.io.File;

public class Slide3 extends Fragment {

    private Activity activity;
    private Context context;

    private LottieAnimationView lottieAnimationView;
    private MaterialButton autoInstallButton;

    private NotificationCompat.Builder notification;
    private NotificationManager notificationManager;
    public TextView description;

    @SuppressLint({"SdCardPath", "SetTextI18n"})
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.slide3, container, false);
        activity = getActivity();
        context = getContext();

        createNotificationChannel();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);


        TextView title = view.findViewById(R.id.slide_title);
        description = view.findViewById(R.id.slide_description);
        autoInstallButton = view.findViewById(R.id.download);
        MaterialButton wiki = view.findViewById(R.id.wiki);
        lottieAnimationView = view.findViewById(R.id.lottie_anim);
        lottieAnimationView.playAnimation();

        autoInstallButton.setOnClickListener(view1 -> {
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.setAnimation(R.raw.download);
            lottieAnimationView.setMaxFrame(120);
            SimpleColorFilter colorFilter = new SimpleColorFilter(Color.parseColor("#0093e7"));
            KeyPath keyPath = new KeyPath("**");
            LottieValueCallback<ColorFilter> callback = new LottieValueCallback<>(colorFilter);
            lottieAnimationView.addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback);
            SuUtils.copyAssets();
            lottieAnimationView.playAnimation();
            lottieAnimationView.postOnAnimation(() -> lottieAnimationView.setMaxFrame(220));
            wiki.setVisibility(View.GONE);
            autoInstallButton.setEnabled(false);
            autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
            FileUtils fileUtils = new FileUtils();
            fileUtils.createFolder("cache");
            SuUtils.checkFileOrFolder(SuUtils.CHROOT_PATH+"VERSION_5.0", aBoolean -> {
                SuUtils.copyAssets();
                if (!aBoolean){
                    fileUtils.downloadFile(activity, "https://cdn.strykerdefence.com/media/5b8.tar.gz", "core.tar.gz",
                            progress -> {
                                lottieAnimationView.setFrame(120 + progress);
                                lottieAnimationView.setRepeatCount(0);
                            },
                            autoInstallButton::setText,
                            isOk -> {
                                if (isOk){
                                    startInstallation();
                                    autoInstallButton.setText("Installing...");
                                    lottieAnimationView.setMinAndMaxFrame(31,91);
                                    lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
                                    lottieAnimationView.playAnimation();
                                }else {
                                    description.setText("Error downloading core. Check your internet connection and try again");
                                    ACRA.getErrorReporter().handleSilentException(new Exception("Error downloading core"));
                                }


                            }
                    );
                }else {
                    Preferences.getInstance().setInstalled();
                    Preferences.getInstance().toaster("Core already installed");
                    Preferences.getInstance().replaceFragment(new Slide4(), "Slide4");
                }
            });
        });


        return view;
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                context.getResources().getString(R.string.notification_channel_updater),
                context.getResources().getString(R.string.notification_channel_updater),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private void startInstallation(){
        SuUtils.checkFileOrFolder(SuUtils.CHROOT_PATH+"VERSION_5.0", aBoolean -> {
            if (!aBoolean){
                installCore();
            }else {
                Preferences.getInstance().replaceFragment(new Slide4(), "Slide4");
                SuUtils.copyAssets();

            }
        });
    }

    public boolean preCoreRemoval() {
        String corePath = "/data/local/stryker5/release";
        boolean allOk = true;

        // Paths to check
        String[] folders = {"/dev/block", "/sys/module", "/proc/cmdline", "/sdcard/Android"};

        // Force lazy unmount everything, even if they're not mounted
        for (String folder : folders) {
            ExecutorBuilder.runCommand("umount -l " + corePath + folder);
        }

        // Now double check if they're really unmounted
        for (String folder : folders) {
            File file = new File(corePath + folder);
            boolean isUnmounted = !file.exists();

            if (isUnmounted) {
                Log.i("preCoreRemoval: ", folder + " is unmounted");
            } else {
                Log.e("preCoreRemoval: ", folder + " is still mounted");
                allOk = false;
            }
        }

        if (allOk) {
            Log.i("preCoreRemoval: ", "Everything is unmounted");
        }

        return allOk;
    }

    private void installCore(){
        if (!preCoreRemoval()) {
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.setAnimation(R.raw.warn);
            lottieAnimationView.playAnimation();
            description.setText("Error unmounting core. Please reboot phone try again");
            autoInstallButton.setEnabled(true);
            autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.refresh, null));
            autoInstallButton.setText("Reboot");
            autoInstallButton.setOnClickListener(view1 -> {
                ExecutorBuilder.runCommand("reboot");
            });
        }else {

            SuUtils.removeFile("/data/local/stryker5/");
            SuUtils.createFolder("/data/local/stryker5/");
            SuUtils.createFolder("/data/local/stryker5/release");
            SuUtils.createFolder("/sdcard/Stryker5");
            SuUtils.createFolder("/sdcard/Stryker5/.temp");
            SuUtils.createFolder("/sdcard/Stryker5/handshakes");
            SuUtils.createFolder("/sdcard/Stryker5/scripts");
            SuUtils.createFolder("/sdcard/Stryker5/wordlists");
            SuUtils.copyAssets();

            ExecutorBuilder.runCommand("chmod 777 /data/data/com.zalexdev.stryker5/files/*");
            SuUtils.unMountChroot(b -> {


                ExecutorBuilder executorBuilder = new ExecutorBuilder();
                executorBuilder.setActivity(activity);
                executorBuilder.setContext(context);
                String downloadChrootPath = FileUtils.basePath + "/core.tar.gz";
                executorBuilder.setCommand(SuUtils.busybox + "tar -xvf " + downloadChrootPath + " -C /data/local/stryker5/");
                executorBuilder.setActivity(activity);
                executorBuilder.setError(s -> description.setText(s));
                executorBuilder.setChroot(false);
                executorBuilder.setOutput(s -> description.setText(s));
                executorBuilder.setOnFinished(strings -> {
                    ExecutorBuilder e = new ExecutorBuilder();
                    e.setCommand("echo 5.0 > /VERSION_5.0");
                    e.setChroot(true);
                    e.setOnFinished(strings1 -> {
                        Log.d(TAG, "accept: " + strings1);
                        SuUtils.checkFileOrFolder("/data/local/stryker5/release/VERSION_5.0", aBoolean -> {
                            if (aBoolean) {
                                SuUtils.mountChroot(null, s -> Log.d(TAG, "installCore: " + s));
                                Log.d(TAG, "installCore: Core installed");

                                createOrUpdateNotification("Core installed");
                                lottieAnimationView.setMinAndMaxFrame(220, 268);
                                lottieAnimationView.setRepeatCount(0);
                                lottieAnimationView.playAnimation();
                                removeNotification();
                                SuUtils.copyAssets();

                                activity.runOnUiThread(() -> {
                                    description.setText("Chroot installed");
                                    Preferences.getInstance().setInstalled();
                                    autoInstallButton.setEnabled(true);
                                    autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.arrow_right, null));
                                    autoInstallButton.setText(getString(R.string.next));
                                    Preferences.getInstance().setInstalled();
                                    autoInstallButton.setOnClickListener(view1 -> Preferences.getInstance().replaceFragment(new Slide4(), "Slide4"));
                                    Preferences.getInstance().setInstalled();
                                });

                            } else {
                                Log.e(TAG, "installCore: Error installing core");
                                createOrUpdateNotification("Error installing core");
                                ACRA.getErrorReporter().handleSilentException(new Exception("Error installing core"));
                                activity.runOnUiThread(() -> {
                                    autoInstallButton.setEnabled(true);
                                    autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
                                    autoInstallButton.setText("Retry");
                                    autoInstallButton.setOnClickListener(view1 -> startInstallation());
                                });

                            }
                        });

                    });
                    e.execute();
                });
                executorBuilder.execute();
            });

        }
    }



    private void createOrUpdateNotification(String content) {
        if (notification == null) {
            notification = new NotificationCompat.Builder(context, context.getResources().getString(R.string.notification_channel_updater))
                    .setContentTitle("Installing core")
                    .setContentText(content)
                    .setSmallIcon(R.drawable.download)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        }
        notification.setProgress(0,0,true);
        notification.setContentText(content);
        notificationManager.notify(1, notification.build());
    }

    private void removeNotification(){
        notificationManager.cancel(1);
    }
}