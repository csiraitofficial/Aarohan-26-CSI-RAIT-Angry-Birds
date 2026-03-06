package com.example.souls.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class PackageInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {

            String packageName = intent.getData().getSchemeSpecificPart();

            Toast.makeText(
                    context,
                    "App installed: " + packageName,
                    Toast.LENGTH_LONG
            ).show();
        }
    }
}