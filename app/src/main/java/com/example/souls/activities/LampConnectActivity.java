package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * LampConnectActivity
 *
 * Handles the LAMP device registration flow:
 *   1. User taps "Scan for LAMP" (simulates BT discovery)
 *   2. App calls POST /session/start → gets sessionId
 *   3. App polls GET /session/:sessionId every 2 s
 *   4. When stage = FINGERPRINT_RECEIVED, routes to RegisterActivity
 *
 * In production, step 1 uses BluetoothLeScanner to find the LAMP device
 * and read its lampId. Here we use a hardcoded demo lampId for testing.
 */
public class LampConnectActivity extends AppCompatActivity {

    // Demo LAMP ID — in production this comes from BT broadcast / QR code
    private static final String DEMO_LAMP_ID    = "LAMP-DEMO1";
    private static final int    POLL_INTERVAL_MS = 2_000;

    private enum UiState { IDLE, CONNECTING, WAITING_FINGERPRINT, MINTING, ERROR }

    private TextView    tvStatus, tvSubtitle, tvError;
    private Button      btnScan, btnRetry;
    private ProgressBar progressBar;
    private ImageView   ivFingerprint;

    private Handler  pollHandler;
    private Runnable pollRunnable;
    private boolean  isPolling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lamp_connect);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        tvStatus      = findViewById(R.id.tv_status);
        tvSubtitle    = findViewById(R.id.tv_subtitle);
        tvError       = findViewById(R.id.tv_error);
        btnScan       = findViewById(R.id.btn_scan);
        btnRetry      = findViewById(R.id.btn_retry);
        progressBar   = findViewById(R.id.progress_bar);
        ivFingerprint = findViewById(R.id.iv_fingerprint);

        pollHandler = new Handler(Looper.getMainLooper());

        btnScan.setOnClickListener(v -> startLampSession());
        btnRetry.setOnClickListener(v -> {
            setUiState(UiState.IDLE);
            startLampSession();
        });

        setUiState(UiState.IDLE);
    }

    // ─── Step 2: Start session ────────────────────────────────────────────────

    private void startLampSession() {
        setUiState(UiState.CONNECTING);

        String deviceKey = DeviceKeyManager.getDeviceKey(this);
        SessionManager.getInstance(this).setDeviceKey(deviceKey);

        ApiManager.get().startSession(deviceKey, DEMO_LAMP_ID, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (data.optBoolean("ok", false)) {
                    String sessionId = data.optString("sessionId", "");
                    SessionManager sm = SessionManager.getInstance(LampConnectActivity.this);
                    sm.setSessionId(sessionId);
                    sm.setLampId(DEMO_LAMP_ID);
                    setUiState(UiState.WAITING_FINGERPRINT);
                    startPolling(sessionId);
                } else {
                    String error = data.optString("error", "Failed to start session");
                    if (error.contains("already")) {
                        // 409 — device already has a Soul; load it and go to dashboard
                        loadSoulAndGoHome();
                    } else {
                        showError(error);
                    }
                }
            }

            @Override
            public void onError(String message) {
                showError("Cannot reach server. Check your connection.");
            }
        });
    }

    /**
     * Device already registered — fetch Soul data then route to MainActivity.
     */
    private void loadSoulAndGoHome() {
        String deviceKey = SessionManager.getInstance(this).getDeviceKey();
        ApiManager.get().getSoulByDevice(deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (data.optBoolean("ok", false)) {
                    JSONObject block = data.optJSONObject("block");
                    if (block != null) {
                        JSONObject soul = block.optJSONObject("data");
                        if (soul != null) {
                            SessionManager sm = SessionManager.getInstance(LampConnectActivity.this);
                            sm.setSoulId(soul.optString("soulId", ""));
                            sm.setSoulHash(soul.optString("soulHash", ""));
                            sm.setLampId(soul.optString("lampId", ""));
                            sm.setVerifiedAt(soul.optString("verifiedAt", ""));
                            sm.setBlockHash(block.optString("hash", ""));
                            sm.setBlockIndex(block.optInt("index", -1));
                        }
                    }
                }
                startActivity(new Intent(LampConnectActivity.this, MainActivity.class));
                finishAffinity();
            }

            @Override
            public void onError(String message) {
                // Can't reach server but locally registered — just go home
                startActivity(new Intent(LampConnectActivity.this, MainActivity.class));
                finishAffinity();
            }
        });
    }

    // ─── Step 3 & 4: Poll session ─────────────────────────────────────────────

    private void startPolling(String sessionId) {
        isPolling = true;
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPolling) return;

                // Capture outer Runnable so ApiCallback can re-schedule it.
                // 'this' inside the callback refers to the callback, not this Runnable.
                final Runnable self = this;

                ApiManager.get().pollSession(sessionId, new ApiCallback() {
                    @Override
                    public void onSuccess(JSONObject data) {
                        if (!isPolling) return;

                        String stage     = data.optString("stage", "");
                        String expiresAt = data.optString("expiresAt", "");

                        // Check expiry — SimpleDateFormat works on API 24+
                        if (!expiresAt.isEmpty()) {
                            try {
                                long expiryMs = parseIso8601(expiresAt);
                                if (System.currentTimeMillis() > expiryMs
                                        && "WAITING_FOR_FINGERPRINT".equals(stage)) {
                                    stopPolling();
                                    showError("Session timed out. Please try again.");
                                    return;
                                }
                            } catch (ParseException ignored) {}
                        }

                        switch (stage) {
                            case "FINGERPRINT_RECEIVED":
                                stopPolling();
                                setUiState(UiState.MINTING);
                                // Route to RegisterActivity where user enters their name,
                                // then finalizes the Soul.
                                startActivity(new Intent(LampConnectActivity.this,
                                        RegisterActivity.class));
                                overridePendingTransition(R.anim.slide_in_right,
                                        R.anim.slide_out_left);
                                finish();
                                break;

                            case "COMPLETED":
                                stopPolling();
                                loadSoulAndGoHome();
                                break;

                            default:
                                // WAITING_FOR_FINGERPRINT — keep polling
                                pollHandler.postDelayed(self, POLL_INTERVAL_MS);
                                break;
                        }
                    }

                    @Override
                    public void onError(String message) {
                        if (isPolling) pollHandler.postDelayed(self, POLL_INTERVAL_MS);
                    }
                });
            }
        };
        pollHandler.post(pollRunnable);
    }

    /**
     * Parses ISO-8601 timestamps to epoch ms. API 24+ compatible (no java.time).
     * Handles: 2026-03-06T10:00:00.000Z  and  2026-03-06T10:00:00Z
     */
    private long parseIso8601(String ts) throws ParseException {
        String clean  = ts.endsWith("Z") ? ts.substring(0, ts.length() - 1) : ts;
        String pattern = clean.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS"
                : "yyyy-MM-dd'T'HH:mm:ss";
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.parse(clean).getTime();
    }

    private void stopPolling() {
        isPolling = false;
        if (pollRunnable != null) pollHandler.removeCallbacks(pollRunnable);
    }

    // ─── UI State ─────────────────────────────────────────────────────────────

    private void setUiState(UiState state) {
        tvError.setVisibility(View.GONE);
        btnRetry.setVisibility(View.GONE);

        switch (state) {
            case IDLE:
                tvStatus.setText("Find a LAMP Device");
                tvSubtitle.setText("Scan for a nearby LAMP device to begin identity registration");
                btnScan.setVisibility(View.VISIBLE);
                btnScan.setText("Scan for LAMP");
                progressBar.setVisibility(View.GONE);
                ivFingerprint.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(0.3f);
                break;

            case CONNECTING:
                tvStatus.setText("Connecting to LAMP…");
                tvSubtitle.setText("Starting a verification session");
                btnScan.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                ivFingerprint.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(0.3f);
                break;

            case WAITING_FINGERPRINT:
                tvStatus.setText("Place Finger on LAMP");
                tvSubtitle.setText("Put your finger on the LAMP device sensor. Waiting for scan…");
                btnScan.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                ivFingerprint.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(1.0f);
                break;

            case MINTING:
                tvStatus.setText("Minting your Soul…");
                tvSubtitle.setText("Your identity is being written to the blockchain");
                btnScan.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE);
                ivFingerprint.setVisibility(View.GONE);
                break;

            case ERROR:
                btnScan.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                btnRetry.setVisibility(View.VISIBLE);
                ivFingerprint.setAlpha(0.3f);
                break;
        }
    }

    private void showError(String msg) {
        setUiState(UiState.ERROR);
        tvStatus.setText("Something went wrong");
        tvSubtitle.setText("");
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}