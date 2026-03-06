package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * MainActivity — Soul dashboard.
 *
 * On resume, refreshes from GET /soul/by-device/:deviceKey and GET /validate.
 *
 * GET /soul/by-device response shape:
 * {
 *   "ok": true,
 *   "block": {
 *     "index": 1,
 *     "timestamp": "2026-03-06T10:01:45.000Z",
 *     "hash": "0043d5e0...",
 *     "previousHash": "00f5bf47...",
 *     "nonce": 522,
 *     "data": {
 *       "type": "SOUL_REGISTRATION",
 *       "soulId": "SOUL-AC7EF884",
 *       "soulHash": "a3f7b291...",
 *       "deviceKey": "DK-...",
 *       "lampId": "LAMP-DEMO1",
 *       "verifiedAt": "2026-03-06T10:01:32.000Z"
 *     }
 *   }
 * }
 *
 * GET /validate response:
 * { "valid": true, "message": "Chain is intact ✓" }
 */
public class MainActivity extends AppCompatActivity {

    private TextView tvName, tvSoulId, tvBlockHash, tvBlockIndex, tvVerifiedAt, tvChainStatus;
    private ImageView ivProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvName        = findViewById(R.id.tv_name);
        tvSoulId      = findViewById(R.id.tv_soul_id);
        tvBlockHash   = findViewById(R.id.tv_block_hash);
        tvBlockIndex  = findViewById(R.id.tv_block_index);
        tvVerifiedAt  = findViewById(R.id.tv_verified_at);
        tvChainStatus = findViewById(R.id.tv_chain_status);
        ivProfile     = findViewById(R.id.iv_profile);

        // existing
        ivProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

// ADD THIS
        findViewById(R.id.iv_settings).setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
        tvSoulId.setOnClickListener(v ->
                startActivity(new Intent(this, SoulIdActivity.class)));

        bindCache();
        refreshFromChain();
        checkChainValidity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCache();
    }

    // ── Bind from local cache (instant display) ───────────────────────────────

    private void bindCache() {
        SessionManager sm = SessionManager.getInstance(this);

        String name = sm.getUserName();
        tvName.setText((name != null && !name.isEmpty()) ? "Welcome, " + name : "Welcome");

        tvSoulId.setText(sm.getSoulId().isEmpty()    ? "Pending…"  : sm.getSoulId());
        tvBlockHash.setText(truncate(sm.getBlockHash()));
        tvBlockIndex.setText(sm.getBlockIndex() >= 0 ? "Block #" + sm.getBlockIndex() : "—");
        tvVerifiedAt.setText(formatDate(sm.getVerifiedAt()));
    }

    // ── Live refresh: GET /soul/by-device/:deviceKey ──────────────────────────

    private void refreshFromChain() {
        String deviceKey = SessionManager.getInstance(this).getDeviceKey();
        ApiManager.get().getSoulByDevice(deviceKey, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                if (!data.optBoolean("ok", false)) return;
                JSONObject block = data.optJSONObject("block");
                if (block == null) return;

                // persistBlock() maps every block field to SessionManager
                SessionManager.getInstance(MainActivity.this).persistBlock(block);
                bindCache();
            }

            @Override
            public void onError(String message) {
                // Silent — cached data already shown
            }
        });
    }

    // ── GET /validate ─────────────────────────────────────────────────────────

    private void checkChainValidity() {
        ApiManager.get().validateChain(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                // { "valid": true,  "message": "Chain is intact ✓" }
                // { "valid": false, "message": "⚠ Chain tampered!" }
                boolean valid = data.optBoolean("valid", false);
                String  msg   = data.optString("message",
                        valid ? "Chain intact" : "Chain tampered");
                tvChainStatus.setText(msg);
                tvChainStatus.setTextColor(android.graphics.Color.parseColor(
                        valid ? "#4CAF50" : "#FF5555"));
            }

            @Override
            public void onError(String message) {
                tvChainStatus.setText("Chain status unavailable");
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String s) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= 16) return s;
        return s.substring(0, 8) + "…" + s.substring(s.length() - 4);
    }

    private String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            String clean   = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            String pattern = clean.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS"
                    : "yyyy-MM-dd'T'HH:mm:ss";
            java.text.SimpleDateFormat in = new java.text.SimpleDateFormat(
                    pattern, java.util.Locale.US);
            in.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            java.util.Date date = in.parse(clean);
            java.text.SimpleDateFormat out = new java.text.SimpleDateFormat(
                    "dd MMM yyyy, HH:mm 'UTC'", java.util.Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(date);
        } catch (Exception e) {
            return iso;
        }
    }
}