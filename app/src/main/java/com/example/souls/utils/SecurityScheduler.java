package com.example.souls.utils;

import android.content.Context;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SecurityScheduler {

    public static void start(Context context) {

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        UnknownSourcesWorker.class,
                        15,
                        TimeUnit.MINUTES
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        "souls_security_monitor",
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                );
    }
}