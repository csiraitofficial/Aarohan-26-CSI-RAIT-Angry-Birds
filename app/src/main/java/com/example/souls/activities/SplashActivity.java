package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.DeviceKeyManager;
import com.example.souls.utils.SecurityScheduler;
import com.example.souls.utils.SessionManager;
import com.example.souls.utils.UnknownSourcesGuard;

import org.json.JSONObject;

/**
 * SplashActivity — entry point.
 *
 * Flow:
 *  1. ApiManager.init() — passes context so connectivity checks work.
 *  2. UnknownSourcesGuard scans all installed apps for "Install Unknown Apps" ON.
 *     If any found → blocking dialog appears.
 *  3. GET /device/:deviceKey → routes to MainActivity or LampConnectActivity.
 */
public class SplashActivity extends AppCompatActivity {

    private static final int MIN_SPLASH_MS = 1_200;

    private boolean apiDone       = false;
    private boolean timerDone     = false;
    private Intent  next          = null;
    private boolean launchStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Start background unknown-sources monitor (fires every 15 min)
        SecurityScheduler.start(this);

        UnknownSourcesGuard.checkAndProceed(this, this::beginLaunchFlow);
    }

    @Override
    protected void onResume() {
        super.onResume();
        UnknownSourcesGuard.resumeIfReturningFromSettings(this);
        SecurityScheduler.start(this);
    }

    // ── Launch flow ───────────────────────────────────────────────────────────

    private synchronized void beginLaunchFlow() {
        if (launchStarted) return;
        launchStarted = true;

        String deviceKey = DeviceKeyManager.getDeviceKey(this);
        SessionManager.getInstance(this).setDeviceKey(deviceKey);

        // Minimum splash display time
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            timerDone = true;
            maybeNavigate();
        }, MIN_SPLASH_MS);

        // Check device registration with server
        ApiManager.get().checkDevice(deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (data.optBoolean("registered", false)) {
                    SessionManager.getInstance(SplashActivity.this)
                            .setSoulId(data.optString("soulId", ""));
                    next = new Intent(SplashActivity.this, MainActivity.class);
                } else {
                    next = new Intent(SplashActivity.this, LampConnectActivity.class);
                }
                apiDone = true;
                maybeNavigate();
            }

            @Override
            public void onError(String message) {
                // Offline fallback — route based on local cache
                SessionManager sm = SessionManager.getInstance(SplashActivity.this);
                next = new Intent(SplashActivity.this,
                        sm.isRegistered() ? MainActivity.class : LampConnectActivity.class);
                apiDone = true;
                maybeNavigate();
            }
        });
    }

    private synchronized void maybeNavigate() {
        if (!apiDone || !timerDone || next == null) return;
        startActivity(next);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}