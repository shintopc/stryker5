package com.zalexdev.stryker.utils;

import static com.zalexdev.stryker.su.SuUtils.TAG;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.TextView;

import com.zalexdev.stryker.su.SuUtils;

import org.acra.ACRA;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Timer;
import java.util.function.Consumer;

import lombok.Getter;
import lombok.Setter;

public class ExecutorBuilder {
    @Getter
    private String command = null;
    private ArrayList<String> commands;
    @Getter
    private boolean isChroot = false;
    public int exitCodeInt = -1;
    @Getter
    @Setter
    public String notificationId = "";
    @Setter
    public boolean noLog = false;
    private Consumer<String> output = null;
    private Consumer<String> error = null;
    private Consumer<ArrayList<String>> outputList = null;
    private Consumer<Integer> exitCode = null;
    private Process process;
    private Context context = null;
    private Activity activity = null;
    private TextView terminalView = null;
    @Getter
    private int timeout = 0;
    private final DebugData debugData = DebugData.getInstance();

    public ExecutorBuilder setCommand(String command) {
        this.command = command;

        return this;
    }

    public ExecutorBuilder setCommands(ArrayList<String> commands) {
        if (commands != null) {
            this.commands = commands;
        }
        return this;
    }

    public boolean getNoLog() {
        return noLog;
    }

    public ExecutorBuilder setChroot(boolean chroot) {
        isChroot = chroot;
        return this;
    }

    public ExecutorBuilder setOutput(Consumer<String> output) {
        this.output = output;
        return this;
    }

    public ExecutorBuilder setTimeout(int timeout) {
        this.timeout = timeout;
        return this;
    }

    public ExecutorBuilder setOnFinished(Consumer<ArrayList<String>> outputList) {
        this.outputList = outputList;
        return this;
    }

    public ExecutorBuilder setError(Consumer<String> error) {
        this.error = error;
        return this;
    }

    public boolean isAlive() {
        return exitCodeInt == -1;
    }

    public ExecutorBuilder setExitCode(Consumer<Integer> exitCode) {
        this.exitCode = exitCode;
        exitCodeInt = -1;
        return this;
    }

    public ExecutorBuilder setActivity(Activity activity) {
        this.activity = activity;
        context = activity;
        return this;
    }

    public ExecutorBuilder setContext(Context context) {
        this.context = context;
        activity = (Activity) context;
        return this;
    }

    public ExecutorBuilder setTerminal(TextView textView) {
        terminalView = textView;
        return this;
    }

    public ExecutorBuilder kill() {
        if (process != null) {
            process.destroy();
            debugData.delCmd(command);
            exitCodeInt = -1;
        }
        return this;
    }

    public void execute() {
        Log.d(TAG, "execute: " + command);
        debugData.addCmd(command);
        HandlerThread handlerThread = new HandlerThread("SuThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        if (timeout != 0) {
            Timer timer = new Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (process != null) {
                        process.destroy();
                        Log.d(TAG, "run: Process killed after timeout");
                    }
                }
            }, timeout * 1000L);
        }
        handler.post(() -> {
            ArrayList<String> output = new ArrayList<>();
            try {
                process = Runtime.getRuntime().exec("su --mount-master");
                if (isChroot) {
                    process.getOutputStream().write((SuUtils.execute + " sh\n").getBytes());
                    process.getOutputStream().flush();
                    process.getOutputStream().write(("export TMPDIR=/tmp\n").getBytes());
                    process.getOutputStream().flush();
                }
                if (command != null) {
                    process.getOutputStream().write((command + "\n").getBytes());
                    process.getOutputStream().flush();
                } else if (commands != null) {
                    for (String command : commands) {
                        process.getOutputStream().write((command + "\n").getBytes());
                        process.getOutputStream().flush();
                    }
                }
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().close();
                debugData.runCmd(command);
                    String[] line = new String[1];
                    BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    new Thread(() -> {
                        try {
                            while ((line[0] = errorStream.readLine()) != null) {
                                if (error != null) {
                                    if (activity != null) {
                                        String finalLine = line[0];
                                        activity.runOnUiThread(() -> {
                                            error.accept(finalLine);
                                            if (!noLog) {
                                                Log.e(TAG, "error: " + finalLine);
                                            }

                                            if (terminalView != null) {
                                                SpannableString spannableString = new SpannableString(finalLine + "\n");
                                                spannableString.setSpan(new ForegroundColorSpan(-0x10000), 0, finalLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                                terminalView.append(spannableString);
                                                smoothScroll(terminalView);
                                            }
                                        });
                                    } else {
                                        if (!noLog) {
                                            Log.e(TAG, "error: " + line[0]);
                                        }
                                        error.accept(line[0]);
                                    }
                                }
                            }
                        } catch (Exception e) {

                            String finalLine = e.getMessage();
                            if (error != null) {
                                if (activity != null) {
                                    activity.runOnUiThread(() -> {
                                        if (!noLog) {
                                            Log.e(TAG, "error: " + finalLine);
                                        }
                                        if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                                            error.accept(finalLine);
                                        }
                                        if (terminalView != null && finalLine != null) {
                                            SpannableString spannableString = new SpannableString(finalLine + "\n");
                                            spannableString.setSpan(new ForegroundColorSpan(-0x10000), 0, finalLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            terminalView.append(spannableString);
                                            smoothScroll(terminalView);
                                        }
                                    });
                                } else {
                                    if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                                        error.accept(finalLine);
                                    }

                                }
                            }
                        }
                    }).start();
                    BufferedReader outputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    while ((line[0] = outputStream.readLine()) != null) {
                        output.add(line[0]);
                        if (!noLog) {
                            Log.e(TAG, "output: " + line[0]);
                        }
                        if (this.output != null) {
                            if (activity != null) {
                                String finalLine = line[0];
                                activity.runOnUiThread(() -> {
                                    this.output.accept(finalLine);
                                    if (!noLog) {
                                        Log.d(TAG, "output: " + finalLine);
                                    }
                                    if (terminalView != null) {
                                        terminalView.append(finalLine + "\n");
                                        smoothScroll(terminalView);
                                    }
                                });
                            } else {
                                this.output.accept(line[0]);
                            }
                        }
                    }

                process.waitFor();
                exitCodeInt = process.exitValue();
                Log.d(TAG, "exitCode: " + exitCodeInt);
                process.destroy();
                debugData.delCmd(command);
                if (!noLog) {
                    if (outputList != null) {
                        if (activity != null) {
                            activity.runOnUiThread(() -> outputList.accept(output));
                        } else {
                            outputList.accept(output);
                        }
                    }
                }else {
                    if (outputList != null) {
                        outputList.accept(output);
                    }
                }
                if (exitCode != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> exitCode.accept(exitCodeInt));
                    } else {
                        exitCode.accept(exitCodeInt);
                    }
                }
            } catch (Exception e) {
                ACRA.getErrorReporter().handleSilentException(new Exception(e));
                String finalLine = e.getMessage();
                Log.e(TAG, "error: " + finalLine);
                if (error != null) {
                    if (activity != null) {
                        Log.e(TAG, "error: " + finalLine);
                        activity.runOnUiThread(() -> {
                            if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                                error.accept(finalLine);
                            }
                        });
                    } else {
                        if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                            error.accept(finalLine);
                        }
                    }
                }
                if (exitCode != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> exitCode.accept(-1));
                    } else {
                        exitCode.accept(-1);
                    }
                }
                if (outputList != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> outputList.accept(new ArrayList<>()));
                    } else {
                        output.add(finalLine);
                        outputList.accept(output);
                    }
                }
            }
        });
    }

    public void easyExecute() {
        Log.d(TAG, "execute: " + command);
        debugData.addCmd(command);
        HandlerThread handlerThread = new HandlerThread("SuThread");
        handlerThread.start();
        Handler handler = new Handler(handlerThread.getLooper());
        if (timeout != 0) {
            Timer timer = new Timer();
            timer.schedule(new java.util.TimerTask() {
                @Override
                public void run() {
                    if (process != null) {
                        process.destroy();
                        Log.d(TAG, "run: Process killed after timeout");
                    }
                }
            }, timeout * 1000L);
        }
        handler.post(() -> {

            try {
                process = Runtime.getRuntime().exec("su --mount-master");
                if (isChroot) {
                    process.getOutputStream().write((SuUtils.execute + " sh\n").getBytes());
                    process.getOutputStream().flush();
                    process.getOutputStream().write(("export TMPDIR=/tmp\n").getBytes());
                    process.getOutputStream().flush();
                }
                if (command != null) {
                    process.getOutputStream().write((command + "\n").getBytes());
                    process.getOutputStream().flush();
                } else if (commands != null) {
                    for (String command : commands) {
                        process.getOutputStream().write((command + "\n").getBytes());
                        process.getOutputStream().flush();
                    }
                }
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().close();
                debugData.runCmd(command);
                String[] line = new String[1];
                BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                new Thread(() -> {
                    try {
                        while ((line[0] = errorStream.readLine()) != null) {
                            if (error != null) {
                                if (activity != null) {
                                    String finalLine = line[0];
                                    activity.runOnUiThread(() -> {
                                        error.accept(finalLine);

                                        if (terminalView != null) {
                                            SpannableString spannableString = new SpannableString(finalLine + "\n");
                                            spannableString.setSpan(new ForegroundColorSpan(-0x10000), 0, finalLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                            terminalView.append(spannableString);
                                            smoothScroll(terminalView);
                                        }
                                    });
                                } else {
                                    error.accept(line[0]);
                                }
                            }
                        }
                    } catch (Exception e) {

                        String finalLine = e.getMessage();
                        if (error != null) {
                            if (activity != null) {
                                activity.runOnUiThread(() -> {
                                    if (terminalView != null && finalLine != null) {
                                        SpannableString spannableString = new SpannableString(finalLine + "\n");
                                        spannableString.setSpan(new ForegroundColorSpan(-0x10000), 0, finalLine.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                                        terminalView.append(spannableString);
                                        smoothScroll(terminalView);
                                    }
                                });
                            } else {
                                if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                                    error.accept(finalLine);
                                }

                            }
                        }
                    }
                }).start();
                BufferedReader outputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
                while ((line[0] = outputStream.readLine()) != null) {

                    if (this.output != null) {
                        if (activity != null) {
                            String finalLine = line[0];
                            activity.runOnUiThread(() -> {
                                this.output.accept(finalLine);

                                if (terminalView != null) {
                                    terminalView.append(finalLine + "\n");
                                    smoothScroll(terminalView);
                                }
                            });
                        } else {
                            this.output.accept(line[0]);
                        }
                    }
                }

                process.waitFor();
                exitCodeInt = process.exitValue();
                Log.d(TAG, "exitCode: " + exitCodeInt);
                process.destroy();
                debugData.delCmd(command);
                if (!noLog) {
                    if (outputList != null) {
                        if (activity != null) {
                            activity.runOnUiThread(() -> outputList.accept(new ArrayList<>()));
                        } else {
                            outputList.accept(new ArrayList<>());
                        }
                    }
                }else {
                    if (outputList != null) {
                        outputList.accept(new ArrayList<>());
                    }
                }
                if (exitCode != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> exitCode.accept(exitCodeInt));
                    } else {
                        exitCode.accept(exitCodeInt);
                    }
                }
            } catch (Exception e) {
                ACRA.getErrorReporter().handleSilentException(new Exception(e));
                String finalLine = e.getMessage();
                Log.e(TAG, "error: " + finalLine);
                if (error != null) {
                    if (activity != null) {
                        Log.e(TAG, "error: " + finalLine);
                        activity.runOnUiThread(() -> {
                            if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                                error.accept(finalLine);
                            }
                        });
                    } else {
                        if (!Objects.requireNonNull(Objects.requireNonNull(e.getMessage()).toLowerCase()).contains("read interrupted")) {
                            error.accept(finalLine);
                        }
                    }
                }
                if (exitCode != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> exitCode.accept(-1));
                    } else {
                        exitCode.accept(-1);
                    }
                }
                if (outputList != null) {
                    if (activity != null) {
                        activity.runOnUiThread(() -> outputList.accept(new ArrayList<>()));
                    } else {
                        outputList.accept(new ArrayList<>());
                    }
                }
            }
        });
    }
    public void smoothScroll(TextView outputText) {
        int scrollAmount = outputText.getLayout().getLineTop(outputText.getLineCount()) - outputText.getHeight();
        outputText.scrollTo(0, Math.max(scrollAmount, 0));
    }

    public static ArrayList<String> runCommand(String command) {
        DebugData debugData = DebugData.getInstance();
        debugData.addCmd(command);
        try {
            Process process = Runtime.getRuntime().exec("su --mount-master");
            process.getOutputStream().write((command + "\n").getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            debugData.runCmd(command);
            ArrayList<String> output = new ArrayList<>();
            BufferedReader outputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = outputStream.readLine()) != null) {
                output.add(line);
                Log.d(TAG, "runCommand: " + line);
            }
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorStream.readLine()) != null) {
                output.add(line);
                Log.e(TAG, "runCommand: " + line);
            }
            process.waitFor();
            process.destroy();
            debugData.delCmd(command);
            return output;
        } catch (Exception e) {
            Log.e(TAG, "runCommand: " + e.getMessage());

            return new ArrayList<>();
        }
    }

    public static void customMegaCommand(String command, Consumer<ArrayList<String>> finished) {

        new Thread(() -> {
            ArrayList<String> result = new ArrayList<>();
            Process process = null;
            try {
                process = Runtime.getRuntime().exec("su -mm");
            } catch (IOException e) {
                ACRA.getErrorReporter().handleSilentException(new Exception(e));
            }
            try {

                OutputStream stdin = Objects.requireNonNull(process).getOutputStream();
                InputStream stderr = process.getErrorStream();
                InputStream stdout = process.getInputStream();
                stdin.write((command + '\n').getBytes());
                stdin.write(("").getBytes());
                stdin.flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                stdin.close();
                BufferedReader br = new BufferedReader(new InputStreamReader(stdout));

                String line;
                while ((line = br.readLine()) != null) {
                    Log.d("Debug: ", "line: " + line);
                    result.add(line);
                }
                br.close();
                BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
                String lineerror;
                while ((lineerror = br2.readLine()) != null) {
                    Log.d("Debug: ", "line: " + lineerror);
                    result.add(lineerror);
                }
                br2.close();
            } catch (IOException e) {
                Log.d("Debug: ", "An IOException was caught: " + e.getMessage());

            }
            try {
                assert process != null;
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
            process.destroy();

            Preferences.getInstance().getActivity().runOnUiThread(() -> finished.accept(result));

        }).start();

    }

    public static void customMegaCommand(String command, Consumer<String> lines, Consumer<ArrayList<String>> finished) {

        new Thread(() -> {
            ArrayList<String> result = new ArrayList<>();
            Process process = null;
            try {
                process = Runtime.getRuntime().exec("su -mm");
            } catch (IOException e) {
                ACRA.getErrorReporter().handleSilentException(new Exception(e));
            }
            try {

                OutputStream stdin = Objects.requireNonNull(process).getOutputStream();
                InputStream stderr = process.getErrorStream();
                InputStream stdout = process.getInputStream();
                stdin.write((command + '\n').getBytes());
                stdin.write(("").getBytes());
                stdin.flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                process.getOutputStream().write("exit\n".getBytes());
                process.getOutputStream().flush();
                stdin.close();
                BufferedReader br = new BufferedReader(new InputStreamReader(stdout));

                String line;
                while ((line = br.readLine()) != null) {
                    Log.d("Debug: ", "line: " + line);
                    lines.accept(line);
                    result.add(line);
                }
                br.close();
                BufferedReader br2 = new BufferedReader(new InputStreamReader(stderr));
                String lineerror;
                while ((lineerror = br2.readLine()) != null) {
                    Log.d("Debug: ", "line: " + lineerror);
                    lines.accept(lineerror);
                    result.add(lineerror);
                }
                br2.close();
            } catch (IOException e) {
                Log.d("Debug: ", "An IOException was caught: " + e.getMessage());

            }
            try {
                assert process != null;
                process.waitFor();
            } catch (InterruptedException e) {
                e.printStackTrace();

            }
            process.destroy();

            Preferences.getInstance().getActivity().runOnUiThread(() -> finished.accept(result));

        }).start();

    }

    public static ArrayList<String> runCommandChroot(String command) {
        DebugData debugData = DebugData.getInstance();
        try {
            Process process = Runtime.getRuntime().exec("su --mount-master");
            process.getOutputStream().write((SuUtils.execute + " sh\n").getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().write((command + "\n").getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().write("exit\n".getBytes());
            process.getOutputStream().flush();
            debugData.runCmd(command);
            ArrayList<String> output = new ArrayList<>();
            BufferedReader outputStream = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = outputStream.readLine()) != null) {
                output.add(line);
                Log.d(TAG, "runCommand: " + line);
            }
            BufferedReader errorStream = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            while ((line = errorStream.readLine()) != null) {
                output.add(line);
                Log.e(TAG, "runCommand: " + line);
            }
            process.waitFor();
            process.destroy();
            debugData.delCmd(command);
            return output;
        } catch (Exception e) {
            Log.e(TAG, "runCommand: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public static boolean contains(ArrayList<String> strings, String s){
        for (String string : strings){
            if (string.toLowerCase().contains(s.toLowerCase())){
                return true;
            }
        }
        return false;
    }



}