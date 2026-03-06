package com.example.lamp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lamp.R;
import com.example.lamp.network.LampApiManager;
import com.example.lamp.utils.FingerprintHelper;
import com.example.lamp.utils.LampConfig;

import org.json.JSONObject;

public class ScanActivity extends AppCompatActivity {

    private enum UiState { ENTER_SESSION, SCANNING, CALLING_API, DONE }

    private EditText    etSessionId;
    private Button      btnScan;
    private ProgressBar progressBar;
    private TextView    tvStatus, tvError, tvLampId;
    private ImageView   ivFingerprint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        etSessionId   = findViewById(R.id.et_session_id);
        btnScan       = findViewById(R.id.btn_scan_finger);
        progressBar   = findViewById(R.id.progress_bar);
        tvStatus      = findViewById(R.id.tv_status);
        tvError       = findViewById(R.id.tv_error);
        tvLampId      = findViewById(R.id.tv_lamp_id_label);
        ivFingerprint = findViewById(R.id.iv_fingerprint);

        tvLampId.setText("LAMP ID: " + LampConfig.LAMP_ID);
        setState(UiState.ENTER_SESSION);
        btnScan.setOnClickListener(v -> handleScanTap());
    }

    private void handleScanTap() {
        String sessionId = etSessionId.getText().toString().trim().toUpperCase();

        if (TextUtils.isEmpty(sessionId)) {
            showError("Please enter the Session ID from the user's phone.");
            return;
        }
        if (sessionId.length() < 8) {
            showError("Session ID looks too short. Please check and try again.");
            return;
        }

        hideError();
        setState(UiState.SCANNING);

        FingerprintHelper.scan(this, LampConfig.LAMP_ID, new FingerprintHelper.FingerprintCallback() {
            @Override
            public void onSuccess(String fingerprintHash) {
                setState(UiState.CALLING_API);
                callLampVerify(sessionId, fingerprintHash);
            }

            @Override
            public void onError(String errorMessage) {
                setState(UiState.ENTER_SESSION);
                showError("Fingerprint error: " + errorMessage);
            }

            @Override
            public void onCancelled() {
                setState(UiState.ENTER_SESSION);
            }
        });
    }

    private void callLampVerify(String sessionId, String fingerprintHash) {
        LampApiManager.get().verify(sessionId, LampConfig.LAMP_ID, fingerprintHash,
                new LampApiManager.Callback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        setState(UiState.DONE);
                        Intent intent = new Intent(ScanActivity.this, ResultActivity.class);
                        intent.putExtra(ResultActivity.EXTRA_OK, data.optBoolean("ok", false));
                        intent.putExtra(ResultActivity.EXTRA_SESSION_ID, sessionId);
                        intent.putExtra(ResultActivity.EXTRA_MESSAGE,
                                data.optString("message", data.optString("error", "Unknown response")));
                        startActivity(intent);
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                        finish();
                    }

                    @Override
                    public void onError(String message) {
                        setState(UiState.ENTER_SESSION);
                        showError("Cannot reach server. Check connection.");
                    }
                });
    }

    private void setState(UiState state) {
        switch (state) {
            case ENTER_SESSION:
                tvStatus.setText("Enter Session ID");
                etSessionId.setEnabled(true);
                btnScan.setEnabled(true);
                btnScan.setText("Scan Fingerprint");
                progressBar.setVisibility(View.GONE);
                ivFingerprint.setAlpha(0.4f);
                break;
            case SCANNING:
                tvStatus.setText("Place finger on sensor…");
                etSessionId.setEnabled(false);
                btnScan.setEnabled(false);
                btnScan.setText("");
                progressBar.setVisibility(View.GONE);
                ivFingerprint.setAlpha(1.0f);
                break;
            case CALLING_API:
                tvStatus.setText("Verifying with blockchain…");
                etSessionId.setEnabled(false);
                btnScan.setEnabled(false);
                btnScan.setText("");
                progressBar.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(1.0f);
                break;
            case DONE:
                progressBar.setVisibility(View.GONE);
                break;
        }
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }
}