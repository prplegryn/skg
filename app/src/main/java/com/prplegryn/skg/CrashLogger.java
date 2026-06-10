package com.prplegryn.skg;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

final class CrashLogger {
    private static final String LOG_DIR = "skglog";
    private static final long WATCHDOG_INTERVAL_MS = 1_000L;
    private static final long FREEZE_THRESHOLD_MS = 8_000L;
    private static final long FREEZE_LOG_COOLDOWN_MS = 30_000L;
    private static final AtomicBoolean installed = new AtomicBoolean(false);

    private static Context appContext;
    private static Thread.UncaughtExceptionHandler previousHandler;

    private CrashLogger() {
    }

    static void install(Context context) {
        if (!installed.compareAndSet(false, true)) {
            return;
        }
        appContext = context.getApplicationContext();
        previousHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                write("crash", buildCrashReport(thread, throwable));
            } catch (Throwable ignored) {
            }
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, throwable);
            } else {
                android.os.Process.killProcess(android.os.Process.myPid());
                System.exit(10);
            }
        });
        startMainThreadWatchdog();
    }

    private static void startMainThreadWatchdog() {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        AtomicLong lastBeat = new AtomicLong(SystemClock.uptimeMillis());
        AtomicLong lastFreezeLog = new AtomicLong(0L);
        AtomicBoolean frozen = new AtomicBoolean(false);

        Runnable beat = new Runnable() {
            @Override
            public void run() {
                lastBeat.set(SystemClock.uptimeMillis());
                frozen.set(false);
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
            }
        };
        mainHandler.post(beat);

        Thread watchdog = new Thread(() -> {
            while (true) {
                SystemClock.sleep(2_000L);
                long now = SystemClock.uptimeMillis();
                long blockedFor = now - lastBeat.get();
                boolean canLog = now - lastFreezeLog.get() >= FREEZE_LOG_COOLDOWN_MS;
                if (blockedFor >= FREEZE_THRESHOLD_MS && (frozen.compareAndSet(false, true) || canLog)) {
                    lastFreezeLog.set(now);
                    write("freeze", buildFreezeReport(blockedFor));
                }
            }
        }, "skg-main-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private static String buildCrashReport(Thread thread, Throwable throwable) {
        StringBuilder builder = baseReport("Crash");
        builder.append("Thread: ").append(thread.getName()).append('\n');
        builder.append("Thread id: ").append(thread.getId()).append('\n');
        builder.append('\n');
        builder.append("Exception:\n");
        builder.append(stackTrace(throwable)).append('\n');
        builder.append("All threads:\n");
        appendAllThreads(builder);
        return builder.toString();
    }

    private static String buildFreezeReport(long blockedForMs) {
        StringBuilder builder = baseReport("Main thread freeze");
        builder.append("Blocked for: ").append(blockedForMs).append(" ms\n\n");

        Thread mainThread = Looper.getMainLooper().getThread();
        builder.append("Main thread stack:\n");
        appendStack(builder, mainThread.getStackTrace());
        builder.append('\n');

        builder.append("All threads:\n");
        appendAllThreads(builder);
        return builder.toString();
    }

    private static StringBuilder baseReport(String type) {
        StringBuilder builder = new StringBuilder(16_384);
        builder.append("SKG ").append(type).append(" Log\n");
        builder.append("Time: ").append(timestampForBody()).append('\n');
        builder.append("Package: ").append(appContext.getPackageName()).append('\n');
        appendAppVersion(builder);
        builder.append("Device: ").append(Build.MANUFACTURER).append(' ')
                .append(Build.MODEL).append('\n');
        builder.append("Android: ").append(Build.VERSION.RELEASE)
                .append(" API ").append(Build.VERSION.SDK_INT).append('\n');
        builder.append("Fingerprint: ").append(Build.FINGERPRINT).append('\n');
        builder.append('\n');
        return builder;
    }

    private static void appendAppVersion(StringBuilder builder) {
        try {
            PackageManager manager = appContext.getPackageManager();
            PackageInfo info = manager.getPackageInfo(appContext.getPackageName(), 0);
            builder.append("Version: ").append(info.versionName)
                    .append(" (").append(info.versionCode).append(")\n");
        } catch (PackageManager.NameNotFoundException ignored) {
        }
    }

    private static String stackTrace(Throwable throwable) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream stream = new PrintStream(bytes);
        throwable.printStackTrace(stream);
        stream.flush();
        return bytes.toString();
    }

    private static void appendAllThreads(StringBuilder builder) {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            builder.append('"').append(thread.getName()).append('"')
                    .append(" id=").append(thread.getId())
                    .append(" state=").append(thread.getState())
                    .append('\n');
            appendStack(builder, entry.getValue());
            builder.append('\n');
        }
    }

    private static void appendStack(StringBuilder builder, StackTraceElement[] stack) {
        if (stack.length == 0) {
            builder.append("    <empty>\n");
            return;
        }
        for (StackTraceElement element : stack) {
            builder.append("    at ").append(element).append('\n');
        }
    }

    private static void write(String prefix, String body) {
        String fileName = prefix + "_" + timestampForFile() + ".log";
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && writeWithMediaStore(fileName, data)) {
            return;
        }
        if (writeWithPublicDownloads(fileName, data)) {
            return;
        }
        writeWithAppExternal(fileName, data);
    }

    private static boolean writeWithMediaStore(String fileName, byte[] data) {
        Uri uri = null;
        ContentResolver resolver = appContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + LOG_DIR);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        try {
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                return false;
            }
            try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) {
                    return false;
                }
                output.write(data);
            }
            values.clear();
            values.put(MediaStore.MediaColumns.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return true;
        } catch (RuntimeException | IOException ignored) {
            if (uri != null) {
                try {
                    resolver.delete(uri, null, null);
                } catch (RuntimeException ignoredDelete) {
                }
            }
            return false;
        }
    }

    private static boolean writeWithPublicDownloads(String fileName, byte[] data) {
        File directory = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                LOG_DIR
        );
        return writeFile(new File(directory, fileName), data);
    }

    private static void writeWithAppExternal(String fileName, byte[] data) {
        File root = appContext.getExternalFilesDir(null);
        if (root != null) {
            writeFile(new File(new File(root, LOG_DIR), fileName), data);
        }
    }

    private static boolean writeFile(File file, byte[] data) {
        File parent = file.getParentFile();
        if (parent == null || (!parent.isDirectory() && !parent.mkdirs())) {
            return false;
        }
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(data);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    private static String timestampForFile() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
    }

    private static String timestampForBody() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(new Date());
    }
}
