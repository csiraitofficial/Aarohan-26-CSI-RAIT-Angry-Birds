package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * RegisterActivity
 *
 * Reached after stage = FINGERPRINT_RECEIVED.
 * User enters a display name (local only — not sent to API).
 *
 * Calls POST /soul/finalize { sessionId, deviceKey }
 *
 * Success response:
 * {
 *   "ok": true,
 *   "soulId": "SOUL-AC7EF884",
 *   "soulHash": "a3f7b291cc88d2e4...",
 *   "blockHash": "0043d5e016523...",
 *   "blockIndex": 1
 * }
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText    etName;
    private Button      btnFinalize;
    private ProgressBar progressBar;
    private TextView    tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName      = findViewById(R.id.et_name);
        btnFinalize = findViewById(R.id.btn_finalize);
        progressBar = findViewById(R.id.progress_bar);
        tvError     = findViewById(R.id.tv_error);

        btnFinalize.setOnClickListener(v -> attemptFinalize());
    }

    private void attemptFinalize() {
        String name = etName.getText().toString().trim();
        if (TextUtils.isEmpty(name)) {
            showError(getString(R.string.error_name_required));
            return;
        }

        tvError.setVisibility(View.GONE);
        setLoading(true);

        SessionManager sm        = SessionManager.getInstance(this);
        String         sessionId = sm.getSessionId();
        String         deviceKey = sm.getDeviceKey();

        ApiManager.get().finalizeSoul(sessionId, deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                setLoading(false);

                if (data.optBoolean("ok", false)) {
                    /*
                     * Persist every field returned by POST /soul/finalize:
                     *   soulId     → block.data.soulId
                     *   soulHash   → block.data.soulHash
                     *   blockHash  → block.hash
                     *   blockIndex → block.index
                     */
                    sm.setSoulId(data.optString("soulId",     ""));
                    sm.setSoulHash(data.optString("soulHash",  ""));
                    sm.setBlockHash(data.optString("blockHash",""));
                    sm.setBlockIndex(data.optInt("blockIndex", -1));

                    // Name is local-only — not an API field
                    sm.setUserName(name);

                    // Clear sessionId + expiresAt — registration is complete
                    sm.clearSession();

                    Intent intent = new Intent(RegisterActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                    finish();

                } else {
                    // API error body: { "ok": false, "stage": "WAITING", "message": "..." }
                    //             or: { "ok": false, "error": "..." }
                    String msg = data.optString("message",
                            data.optString("error",
                                    getString(R.string.error_generic)));
                    showError(msg);
                }
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnFinalize.setEnabled(!loading);
        etName.setEnabled(!loading);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }
}