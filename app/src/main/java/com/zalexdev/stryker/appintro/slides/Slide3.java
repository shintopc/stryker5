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
    private MaterialButton selectFileButton;
    private MaterialButton wikiButton;
    private static final int REQUEST_CODE_PICK_FILE = 1001;

    private NotificationCompat.Builder notification;
    private NotificationManager notificationManager;
    public TextView description;

    @SuppressLint({ "SdCardPath", "SetTextI18n" })
    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.slide3, container, false);
        activity = getActivity();
        context = getContext();

        createNotificationChannel();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        TextView title = view.findViewById(R.id.slide_title);
        description = view.findViewById(R.id.slide_description);
        autoInstallButton = view.findViewById(R.id.download);
        wikiButton = view.findViewById(R.id.wiki);
        selectFileButton = view.findViewById(R.id.select_file);
        lottieAnimationView = view.findViewById(R.id.lottie_anim);
        lottieAnimationView.playAnimation();

        selectFileButton.setOnClickListener(view1 -> openFilePicker());

        // Check if chroot is available via bundled asset OR in local storage
        File localChrootFile = findLocalChrootFile();
        boolean hasBundledChroot = false;

        if (localChrootFile == null) {
            try {
                String[] assets = context.getAssets().list("");
                if (assets != null) {
                    for (String name : assets) {
                        if ("chroot_bundle.tar.gz".equals(name)) {
                            hasBundledChroot = true;
                            break;
                        }
                    }
                }
            } catch (java.io.IOException e) {
                Log.w(TAG, "Asset list failed: " + e.getMessage());
            }
        }

        final File foundLocalFile = localChrootFile;
        final boolean useBundledAsset = (localChrootFile == null && hasBundledChroot);

        if (foundLocalFile != null || useBundledAsset) {
            // ── AUTOMATIC OFFLINE MODE: hide buttons and start installation ──
            wikiButton.setVisibility(View.GONE);
            selectFileButton.setVisibility(View.GONE);
            autoInstallButton.setVisibility(View.GONE);
            description.setText("Preparing chroot installation...");
            SuUtils.copyAssets();
            SuUtils.checkFileOrFolder(SuUtils.CHROOT_PATH + "VERSION_5.0", alreadyInstalled -> {
                if (alreadyInstalled) {
                    Preferences.getInstance().setInstalled();
                    Preferences.getInstance().toaster("Core already installed");
                    Preferences.getInstance().replaceFragment(new Slide4(), "Slide4");
                } else {
                    new FileUtils().createFolder("cache");
                    activity.runOnUiThread(() -> {
                        lottieAnimationView.setMinAndMaxFrame(31, 91);
                        lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
                        lottieAnimationView.playAnimation();
                        description.setText("Preparing archive file...");
                    });
                    new Thread(() -> {
                        try {
                            java.io.InputStream in;
                            if (foundLocalFile != null) {
                                Log.i(TAG, "Using local chroot file: " + foundLocalFile.getAbsolutePath());
                                activity.runOnUiThread(
                                        () -> description.setText("Using local file: " + foundLocalFile.getName()));
                                in = new java.io.FileInputStream(foundLocalFile);
                            } else {
                                Log.i(TAG, "Using bundled chroot asset");
                                in = context.getAssets().open("chroot_bundle.tar.gz",
                                        android.content.res.AssetManager.ACCESS_STREAMING);
                            }
                            java.io.File dest = new java.io.File(FileUtils.basePath + "/core.tar.gz");
                            java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                            byte[] buffer = new byte[65536];
                            int bytesRead;
                            long total = 0;
                            while ((bytesRead = in.read(buffer)) != -1) {
                                out.write(buffer, 0, bytesRead);
                                total += bytesRead;
                                final long mb = total / (1024 * 1024);
                                activity.runOnUiThread(
                                        () -> description.setText("Preparing chroot... " + mb + " MB written"));
                            }
                            in.close();
                            out.flush();
                            out.close();
                            activity.runOnUiThread(() -> {
                                description.setText("Archive ready. Extracting chroot...");
                                startInstallation();
                            });
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to prepare chroot file", e);
                            activity.runOnUiThread(() -> {
                                description.setText("Error preparing chroot: " + e.getMessage()
                                        + "\nPlease pick the file manually below.");
                                selectFileButton.setVisibility(View.VISIBLE);
                                autoInstallButton.setVisibility(View.VISIBLE);
                                autoInstallButton.setText("Download");
                                autoInstallButton.setEnabled(true);
                                setupOnlineInstallButton();
                            });
                        }
                    }).start();
                }
            });
        } else {
            // ── NO LOCAL OR BUNDLED CHROOT FOUND: show manual pick / download buttons ──
            setupOnlineInstallButton();
        }

        return view;
    }

    /** Scans common local storage paths for an existing chroot tar.gz archive. */
    private File findLocalChrootFile() {
        String[] possiblePaths = {
                "/sdcard/Download/chroot_v5b_64.tar.gz",
                "/sdcard/Download/chroot64-debian.tar.gz",
                "/sdcard/Download/chroot64.tar.gz",
                "/sdcard/Stryker5/chroot_v5b_64.tar.gz",
                "/sdcard/chroot_v5b_64.tar.gz",
                FileUtils.basePath + "/core.tar.gz"
        };
        for (String path : possiblePaths) {
            File f = new File(path);
            if (f.exists() && f.isFile() && f.length() > 10 * 1024 * 1024) { // > 10MB
                Log.d(TAG,
                        "Found existing local chroot file at: " + path + " (" + (f.length() / (1024 * 1024)) + " MB)");
                return f;
            }
        }
        return null;
    }

    /** Configures the autoInstallButton for the online (GitHub) download path. */
    private void setupOnlineInstallButton() {
        autoInstallButton.setOnClickListener(view1 -> {
            lottieAnimationView.setRepeatCount(0);
            lottieAnimationView.setAnimation(R.raw.download);
            lottieAnimationView.setMaxFrame(120);
            SimpleColorFilter colorFilter = new SimpleColorFilter(Color.parseColor("#0093e7"));
            KeyPath keyPath = new KeyPath("**");
            LottieValueCallback<ColorFilter> callback = new LottieValueCallback<>(colorFilter);
            lottieAnimationView.addValueCallback(keyPath, LottieProperty.COLOR_FILTER, callback);

            // Copy assets first, then proceed with download
            SuUtils.copyAssets(success -> {
                lottieAnimationView.playAnimation();
                lottieAnimationView.postOnAnimation(() -> lottieAnimationView.setMaxFrame(220));
                wikiButton.setVisibility(View.GONE);
                selectFileButton.setVisibility(View.GONE);
                autoInstallButton.setEnabled(false);
                autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
                FileUtils fileUtils = new FileUtils();
                fileUtils.createFolder("cache");
                SuUtils.checkFileOrFolder(SuUtils.CHROOT_PATH + "VERSION_5.0", aBoolean -> {
                    if (!aBoolean) {
                        // Try primary URL first, then fallback
                        String primaryUrl = "https://github.com/zalexdev/strykerapp/releases/download/chroot-main/chroot_v5b_64.tar.gz";
                        String fallbackUrl = "https://github.com/zalexdev/strykerapp/releases/latest/download/chroot_v5b_64.tar.gz";
                        downloadWithFallback(fileUtils, primaryUrl, fallbackUrl);
                    } else {
                        Preferences.getInstance().setInstalled();
                        Preferences.getInstance().toaster("Core already installed");
                        Preferences.getInstance().replaceFragment(new Slide4(), "Slide4");
                    }
                });
            });
        });
    } // end setupOnlineInstallButton

    private void downloadWithFallback(FileUtils fileUtils, String primaryUrl, String fallbackUrl) {
        fileUtils.downloadFile(activity,
                primaryUrl,
                "core.tar.gz",
                progress -> {
                    lottieAnimationView.setFrame(120 + progress);
                    lottieAnimationView.setRepeatCount(0);
                },
                autoInstallButton::setText,
                isOk -> {
                    if (isOk) {
                        startInstallation();
                        autoInstallButton.setText("Installing...");
                        lottieAnimationView.setMinAndMaxFrame(31, 91);
                        lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
                        lottieAnimationView.playAnimation();
                    } else {
                        // Try fallback URL
                        description.setText("Primary download failed, trying fallback...");
                        fileUtils.downloadFile(activity,
                                fallbackUrl,
                                "core.tar.gz",
                                progress -> {
                                    lottieAnimationView.setFrame(120 + progress);
                                    lottieAnimationView.setRepeatCount(0);
                                },
                                autoInstallButton::setText,
                                isOk2 -> {
                                    if (isOk2) {
                                        startInstallation();
                                        autoInstallButton.setText("Installing...");
                                        lottieAnimationView.setMinAndMaxFrame(31, 91);
                                        lottieAnimationView.setRepeatCount(LottieDrawable.INFINITE);
                                        lottieAnimationView.playAnimation();
                                    } else {
                                        description.setText(
                                                "Error downloading core. Check your internet connection and try again");
                                        ACRA.getErrorReporter()
                                                .handleSilentException(new Exception("Error downloading core"));
                                        autoInstallButton.setEnabled(true);
                                        selectFileButton.setVisibility(View.VISIBLE);
                                    }
                                });
                    }
                });
    }

    private void createNotificationChannel() {

        NotificationChannel serviceChannel = new NotificationChannel(
                context.getResources().getString(R.string.notification_channel_updater),
                context.getResources().getString(R.string.notification_channel_updater),
                NotificationManager.IMPORTANCE_DEFAULT);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(serviceChannel);
    }

    private void startInstallation() {
        SuUtils.checkFileOrFolder(SuUtils.CHROOT_PATH + "VERSION_5.0", aBoolean -> {
            if (!aBoolean) {
                installCore();
            } else {
                Preferences.getInstance().replaceFragment(new Slide4(), "Slide4");
                SuUtils.copyAssets();

            }
        });
    }

    public boolean preCoreRemoval() {
        String corePath = "/data/local/stryker5/release";
        boolean allOk = true;

        // Actual mount points to unmount
        String[] mountPoints = {"/dev/pts", "/dev/shm", "/dev", "/proc", "/sys", "/system", "/sdcard"};

        // Force lazy and force unmount the actual mount points
        for (String mp : mountPoints) {
            ExecutorBuilder.runCommand("umount -l " + corePath + mp);
            ExecutorBuilder.runCommand("umount -f " + corePath + mp);
        }
        ExecutorBuilder.runCommand("umount -l " + corePath);
        ExecutorBuilder.runCommand("umount -f " + corePath);
        ExecutorBuilder.runCommand("umount -l /data/local/stryker5");
        ExecutorBuilder.runCommand("umount -f /data/local/stryker5");

        // Check if any of them are still listed in /proc/mounts
        for (String mp : mountPoints) {
            java.util.ArrayList<String> mounts = ExecutorBuilder.runCommand("grep \"" + corePath + mp + " \" /proc/mounts");
            if (mounts != null && !mounts.isEmpty()) {
                Log.e("preCoreRemoval: ", mp + " is still mounted");
                allOk = false;
            } else {
                Log.i("preCoreRemoval: ", mp + " is unmounted");
            }
        }

        return allOk;
    }

    private void installCore() {
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
        } else {

            SuUtils.removeFile("/data/local/stryker5/");
            SuUtils.createFolder("/data/local/stryker5/");
            SuUtils.createFolder("/data/local/stryker5/release");
            SuUtils.createFolder("/sdcard/Stryker5");
            SuUtils.createFolder("/sdcard/Stryker5/.temp");
            SuUtils.createFolder("/sdcard/Stryker5/handshakes");
            SuUtils.createFolder("/sdcard/Stryker5/scripts");
            SuUtils.createFolder("/sdcard/Stryker5/wordlists");

            // Copy assets first, then proceed with extraction
            SuUtils.copyAssets(success -> {
                if (!success) {
                    Log.e(TAG, "installCore: Failed to copy some assets");
                }

                ExecutorBuilder.runCommand("chmod 777 /data/data/com.zalexdev.stryker5/files/*");
                SuUtils.unMountChroot(b -> {

                    ExecutorBuilder executorBuilder = new ExecutorBuilder();
                    executorBuilder.setActivity(activity);
                    executorBuilder.setContext(context);
                    String downloadChrootPath = FileUtils.basePath + "/core.tar.gz";
                    executorBuilder
                            .setCommand(
                                    SuUtils.busybox + "tar -xzf " + downloadChrootPath + " -C /data/local/stryker5/");
                    executorBuilder.setActivity(activity);
                    executorBuilder.setError(s -> Log.e(TAG, "tar-error: " + s));
                    executorBuilder.setChroot(false);
                    executorBuilder.setOnFinished(strings -> {
                        if (executorBuilder.exitCodeInt != 0) {
                            Log.e(TAG, "installCore: Extraction failed with code " + executorBuilder.exitCodeInt);
                            createOrUpdateNotification("Error extracting core");
                            activity.runOnUiThread(() -> {
                                description.setText("Extraction failed (code: " + executorBuilder.exitCodeInt
                                        + "). Please verify the file is valid.");
                                autoInstallButton.setEnabled(true);
                                autoInstallButton
                                        .setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
                                autoInstallButton.setText("Retry");
                                autoInstallButton.setOnClickListener(view1 -> startInstallation());
                            });
                            return;
                        }

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
                                        autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(),
                                                R.drawable.arrow_right, null));
                                        autoInstallButton.setText(getString(R.string.next));
                                        Preferences.getInstance().setInstalled();
                                        autoInstallButton.setOnClickListener(view1 -> Preferences.getInstance()
                                                .replaceFragment(new Slide4(), "Slide4"));
                                        Preferences.getInstance().setInstalled();
                                    });

                                } else {
                                    Log.e(TAG, "installCore: Error installing core");
                                    createOrUpdateNotification("Error installing core");
                                    ACRA.getErrorReporter()
                                            .handleSilentException(new Exception("Error installing core"));
                                    activity.runOnUiThread(() -> {
                                        autoInstallButton.setEnabled(true);
                                        autoInstallButton.setIcon(
                                                ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
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
            });
        }
    }

    private void createOrUpdateNotification(String content) {
        if (notification == null) {
            notification = new NotificationCompat.Builder(context,
                    context.getResources().getString(R.string.notification_channel_updater))
                    .setContentTitle("Installing core")
                    .setContentText(content)
                    .setSmallIcon(R.drawable.download)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        }
        notification.setProgress(0, 0, true);
        notification.setContentText(content);
        notificationManager.notify(1, notification.build());
    }

    private void removeNotification() {
        notificationManager.cancel(1);
    }

    private void openFilePicker() {
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(android.content.Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                | android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK && data != null) {
            android.net.Uri uri = data.getData();
            if (uri != null) {
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(uri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                }

                // Show copy-in-progress UI immediately
                if (wikiButton != null)
                    wikiButton.setVisibility(View.GONE);
                if (selectFileButton != null)
                    selectFileButton.setVisibility(View.GONE);
                autoInstallButton.setEnabled(false);
                autoInstallButton.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.info, null));
                autoInstallButton.setText("Copying file...");
                description.setText("Copying chroot archive to internal storage, please wait...");
                lottieAnimationView.setMinAndMaxFrame(31, 91);
                lottieAnimationView.setRepeatCount(com.airbnb.lottie.LottieDrawable.INFINITE);
                lottieAnimationView.playAnimation();

                // Copy file on background thread to avoid blocking the UI
                new Thread(() -> {
                    try {
                        java.io.InputStream in = requireContext().getContentResolver().openInputStream(uri);
                        if (in == null) {
                            activity.runOnUiThread(() -> description.setText("Unable to open selected file"));
                            return;
                        }
                        FileUtils fileUtils = new FileUtils();
                        fileUtils.createFolder("cache");
                        java.io.File dest = new java.io.File(FileUtils.basePath + "/core.tar.gz");
                        java.io.FileOutputStream out = new java.io.FileOutputStream(dest);
                        byte[] buffer = new byte[65536]; // 64KB buffer for faster copy
                        int bytesRead;
                        long total = 0;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                            total += bytesRead;
                            final long mb = total / (1024 * 1024);
                            activity.runOnUiThread(() -> description.setText("Copying... " + mb + " MB written"));
                        }
                        in.close();
                        out.flush();
                        out.close();

                        // File fully written — now begin installation on UI thread
                        activity.runOnUiThread(() -> {
                            autoInstallButton.setText("Installing...");
                            description.setText("File copied. Starting extraction...");
                            startInstallation();
                        });

                    } catch (Exception e) {
                        activity.runOnUiThread(() -> {
                            description.setText("Error copying file: " + e.getMessage());
                            autoInstallButton.setEnabled(true);
                            autoInstallButton.setText("Retry");
                            if (selectFileButton != null)
                                selectFileButton.setVisibility(View.VISIBLE);
                        });
                    }
                }).start();
            }
        }
    }
}