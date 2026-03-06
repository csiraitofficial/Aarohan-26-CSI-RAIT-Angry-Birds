package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.DeviceKeyManager;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * LoginActivity
 *
 * Entry point for returning users. Checks device registration via:
 *   GET /device/:deviceKey
 *
 *   → registered: true  → MainActivity  (Soul dashboard)
 *   → registered: false → LampConnectActivity (registration flow)
 *
 * No phone number or OTP involved — identity is tied to device key
 * and biometric fingerprint via the LAMP device.
 */
public class LoginActivity extends AppCompatActivity {

    private Button      btnContinue;
    private TextView    tvError;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        btnContinue  = findViewById(R.id.btn_continue);
        tvError      = findViewById(R.id.tv_error);
        progressBar  = findViewById(R.id.progress_bar);

        btnContinue.setOnClickListener(v -> handleLogin());
    }

    private void handleLogin() {
        hideError();
        setLoading(true);

        String deviceKey = DeviceKeyManager.getDeviceKey(this);
        SessionManager.getInstance(this).setDeviceKey(deviceKey);

        ApiManager.get().checkDevice(deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                setLoading(false);

                if (data.optBoolean("registered", false)) {
                    // Already has a Soul — store soulId and go to dashboard
                    String soulId = data.optString("soulId", "");
                    SessionManager.getInstance(LoginActivity.this).setSoulId(soulId);
                    goTo(MainActivity.class);
                } else {
                    // Not registered — start LAMP flow
                    goTo(LampConnectActivity.class);
                }
            }

            @Override
            public void onError(String message) {
                setLoading(false);

                // Fallback to local cache when offline
                if (SessionManager.getInstance(LoginActivity.this).isRegistered()) {
                    goTo(MainActivity.class);
                } else {
                    showError("Cannot reach server. Check your connection.");
                }
            }
        });
    }

    private void goTo(Class<?> destination) {
        startActivity(new Intent(this, destination));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        btnContinue.setEnabled(!loading);
        btnContinue.setText(loading ? "" : getString(R.string.btn_continue));
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}