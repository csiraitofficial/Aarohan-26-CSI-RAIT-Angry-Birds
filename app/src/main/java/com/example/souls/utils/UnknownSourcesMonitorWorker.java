package com.example.souls.utils;

import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.List;

public class UnknownSourcesMonitorWorker extends Worker {

    private static final String CHANNEL_ID = "souls_security";

    public UnknownSourcesMonitorWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        if (isUnknownSourcesEnabled(getApplicationContext())) {
            showSecurityNotification();
        }

        return Result.success();
    }

    private boolean isUnknownSourcesEnabled(Context context) {

        PackageManager pm = context.getPackageManager();
        AppOpsManager aom =
                (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);

        if (aom == null) return false;

        List<ApplicationInfo> apps =
                pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo app : apps) {

            try {

                if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;

                int mode = aom.checkOpNoThrow(
                        "android:request_install_packages",
                        app.uid,
                        app.packageName
                );

                if (mode == AppOpsManager.MODE_ALLOWED) {
                    return true;
                }

            } catch (Exception ignored) {}
        }

        return false;
    }

    private void showSecurityNotification() {

        NotificationManager nm =
                (NotificationManager) getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            "Souls Security",
                            NotificationManager.IMPORTANCE_HIGH);

            nm.createNotificationChannel(channel);
        }

        Notification notification =
                new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                        .setContentTitle("Security Risk Detected")
                        .setContentText("Unknown app installation is enabled. Disable it for safety.")
                        .setSmallIcon(android.R.drawable.stat_sys_warning)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .build();

        nm.notify(101, notification);
    }
}