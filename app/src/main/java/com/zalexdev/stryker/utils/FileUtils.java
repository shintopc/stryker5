package com.zalexdev.stryker.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.function.Consumer;

public class FileUtils {

    @SuppressLint("SdCardPath")
    public static final String basePath = "/data/data/com.zalexdev.stryker5/files/main/storage";

    public FileUtils() {
        // Ensure the base directory exists
        createFolder("");
    }

    public boolean createFolder(String folderName) {
        File folder = new File(basePath, folderName);
        return folder.exists() || folder.mkdirs();
    }

    public static boolean writeToFile(String fileName, ArrayList<String> lines) {
        File file = new File(basePath, fileName);
        if (file.exists()) {
            file.delete();
            file = new File(basePath, fileName);

        }
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            for (String line : lines) {
                fos.write((line + "\n").getBytes());
            }
            return true;
        } catch (IOException e) {
            Log.e("FileUtils", "Error writing to file: " + fileName, e);
            return false;
        }
    }

    public boolean deleteFileOrFolder(String name) {
        File fileOrFolder = new File(basePath, name);
        return deleteRecursive(fileOrFolder);
    }

    private boolean deleteRecursive(File fileOrFolder) {
        if (fileOrFolder.isDirectory()) {
            File[] children = fileOrFolder.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        return fileOrFolder.delete();
    }

    public boolean copyFileOrFolder(String sourceName, String destName) {
        File source = new File(basePath, sourceName);
        File dest = new File(basePath, destName);
        if (source.isDirectory()) {
            return copyDirectory(source, dest);
        } else {
            return copyFile(source, dest);
        }
    }

    private boolean copyFile(File source, File destination) {

        try (FileChannel src = new FileInputStream(source).getChannel();
             FileChannel dst = new FileOutputStream(destination).getChannel()) {
            dst.transferFrom(src, 0, src.size());
            return true;
        } catch (IOException e) {
            Log.e("FileUtils", "Error copying file", e);
            return false;
        }
    }

    private boolean copyDirectory(File source, File destination) {
        if (!destination.exists()) {
            destination.mkdirs();
        }
        File[] files = source.listFiles();
        if (files != null) {
            for (File file : files) {
                File newDest = new File(destination, file.getName());
                if (file.isDirectory()) {
                    copyDirectory(file, newDest);
                } else {
                    copyFile(file, newDest);
                }
            }
            return true;
        }
        return false;
    }

    public boolean moveFileOrFolder(String sourceName, String destName) {
        File source = new File(basePath, sourceName);
        File dest = new File(basePath, destName);
        return source.renameTo(dest);
    }

    public ArrayList<String> getFileNames(String folderName) {
        ArrayList<String> fileNames = new ArrayList<>();
        File folder = new File(basePath, folderName);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                fileNames.add(file.getName());
            }
        }
        return fileNames;
    }



    @SuppressLint("DefaultLocale")
    public void downloadFile(Activity activity, String fileURL, String destinationFileName, Consumer<Integer> intCallback, Consumer<String> progressCallback, Consumer<Boolean> completionCallback) {
        new Thread(() -> {
            boolean result = false;
            try {
                // Follow redirects (GitHub releases use 302 redirects)
                String currentUrl = fileURL;
                HttpURLConnection connection = null;
                for (int redirects = 0; redirects < 5; redirects++) {
                    URL url = new URL(currentUrl);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                    connection.connect();
                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || responseCode == 307 || responseCode == 308) {
                        String location = connection.getHeaderField("Location");
                        connection.disconnect();
                        if (location == null) break;
                        currentUrl = location;
                    } else {
                        break;
                    }
                }

                if (connection == null) throw new IOException("Failed to open connection");
                long fileLength = connection.getContentLengthLong();

                try (InputStream in = connection.getInputStream();
                     FileOutputStream fos = new FileOutputStream(new File(basePath, destinationFileName))) {

                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalBytesRead = 0;

                    while ((bytesRead = in.read(buffer)) != -1) {
                        totalBytesRead += bytesRead;
                        fos.write(buffer, 0, bytesRead);
                        final int progress = fileLength > 0 ? (int) ((totalBytesRead * 100) / fileLength) : 0;
                        final double mbDownloaded = totalBytesRead / (1024.0 * 1024);
                        final double mbTotal = fileLength / (1024.0 * 1024);

                        activity.runOnUiThread(() -> progressCallback.accept(
                                String.format("%d%% (%.2f MB/%.2f MB)", progress, mbDownloaded, mbTotal)
                        ));
                        activity.runOnUiThread(() -> intCallback.accept(progress));
                    }
                    result = true;
                }
                connection.disconnect();
            } catch (IOException e) {
                Log.e("FileUtils", "Error downloading file", e);
                activity.runOnUiThread(() -> progressCallback.accept("Error downloading file: " + e.getMessage()));
            }

            boolean finalResult = result;
            activity.runOnUiThread(() -> completionCallback.accept(finalResult));
        }).start();
    }
}
