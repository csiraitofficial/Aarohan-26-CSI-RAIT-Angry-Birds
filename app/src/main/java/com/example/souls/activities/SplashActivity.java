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
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * SplashActivity — entry point.
 *
 * Calls GET /device/:deviceKey on every launch.
 *
 * Response:
 *   { "ok": true, "registered": false }
 *   { "ok": true, "registered": true, "soulId": "SOUL-AC7EF884" }
 */
public class SplashActivity extends AppCompatActivity {

    private static final int MIN_SPLASH_MS = 1_200;

    private boolean apiDone   = false;
    private boolean timerDone = false;
    private Intent  next      = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        String deviceKey = DeviceKeyManager.getDeviceKey(this);
        SessionManager.getInstance(this).setDeviceKey(deviceKey);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            timerDone = true;
            maybeNavigate();
        }, MIN_SPLASH_MS);

        ApiManager.get().checkDevice(deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                // { "ok": true, "registered": true/false, "soulId": "..." }
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
                // Offline fallback — use cached registration state
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