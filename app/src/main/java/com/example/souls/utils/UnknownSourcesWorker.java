package com.example.souls.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class UnknownSourcesWorker extends Worker {

    public UnknownSourcesWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {

        // You could run UnknownSourcesGuard logic here
        return Result.success();
    }
}