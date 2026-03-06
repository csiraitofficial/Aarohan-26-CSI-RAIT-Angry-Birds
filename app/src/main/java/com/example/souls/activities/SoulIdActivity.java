package com.example.souls.activities;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * SoulIdActivity — full block record from GET /soul/:soulId
 *
 * Displays every field from the live block:
 *
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
 */
public class SoulIdActivity extends AppCompatActivity {

    private TextView    tvSoulId, tvSoulHash, tvBlockHash, tvPreviousHash,
            tvBlockIndex, tvBlockNonce, tvBlockTimestamp,
            tvDeviceKey, tvLampId, tvVerifiedAt,
            tvFingerprintHash, tvRegSessionId, tvError;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_soul_id);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        tvSoulId        = findViewById(R.id.tv_soul_id_full);
        tvSoulHash      = findViewById(R.id.tv_soul_hash);
        tvBlockHash     = findViewById(R.id.tv_block_hash);
        tvPreviousHash  = findViewById(R.id.tv_previous_hash);
        tvBlockIndex    = findViewById(R.id.tv_block_index);
        tvBlockNonce    = findViewById(R.id.tv_block_nonce);
        tvBlockTimestamp= findViewById(R.id.tv_timestamp);
        tvDeviceKey     = findViewById(R.id.tv_device_key);
        tvLampId        = findViewById(R.id.tv_lamp_id);
        tvVerifiedAt      = findViewById(R.id.tv_verified_at);
        tvFingerprintHash = findViewById(R.id.tv_fingerprint_hash);
        tvRegSessionId    = findViewById(R.id.tv_reg_session_id);
        tvError           = findViewById(R.id.tv_error);
        progressBar     = findViewById(R.id.progress_bar);

        // Show cached values instantly
        bindCache();

        // Fetch live from chain
        fetchBlock();
    }

    private void bindCache() {
        SessionManager sm = SessionManager.getInstance(this);
        tvSoulId.setText(sm.getSoulId().isEmpty()           ? "—" : sm.getSoulId());
        tvSoulHash.setText(sm.getSoulHash().isEmpty()       ? "—" : sm.getSoulHash());
        tvBlockHash.setText(sm.getBlockHash().isEmpty()     ? "—" : sm.getBlockHash());
        tvPreviousHash.setText(sm.getPreviousHash().isEmpty()? "—" : sm.getPreviousHash());
        tvBlockIndex.setText(sm.getBlockIndex() >= 0        ? "Block #" + sm.getBlockIndex() : "—");
        tvBlockNonce.setText(sm.getBlockNonce() > 0         ? String.valueOf(sm.getBlockNonce()) : "—");
        tvBlockTimestamp.setText(formatDate(sm.getBlockTimestamp()));
        tvDeviceKey.setText(truncate(sm.getDeviceKey()));
        tvLampId.setText(sm.getLampId().isEmpty()           ? "—" : sm.getLampId());
        tvVerifiedAt.setText(formatDate(sm.getVerifiedAt()));
        tvFingerprintHash.setText(sm.getFingerprintHash().isEmpty() ? "—" : sm.getFingerprintHash());
        tvRegSessionId.setText(sm.getRegSessionId().isEmpty()    ? "—" : sm.getRegSessionId());
    }

    // ── GET /soul/:soulId ─────────────────────────────────────────────────────

    private void fetchBlock() {
        String soulId = SessionManager.getInstance(this).getSoulId();
        if (soulId == null || soulId.isEmpty()) {
            tvError.setText(getString(R.string.error_soul_not_found));
            tvError.setVisibility(View.VISIBLE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        ApiManager.get().getSoulById(soulId, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                progressBar.setVisibility(View.GONE);

                if (!data.optBoolean("ok", false)) {
                    tvError.setText(getString(R.string.error_soul_not_found));
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }

                JSONObject block = data.optJSONObject("block");
                if (block == null) return;

                // persistBlock() maps all block + data fields to SessionManager
                SessionManager.getInstance(SoulIdActivity.this).persistBlock(block);
                bindCache(); // re-bind with fresh data
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvError.setText(getString(R.string.soul_id_offline));
                tvError.setVisibility(View.VISIBLE);
                // Cached values already displayed by bindCache() in onCreate
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String truncate(String s) {
        if (s == null || s.isEmpty()) return "—";
        if (s.length() <= 20) return s;
        return s.substring(0, 10) + "…" + s.substring(s.length() - 6);
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
                    "dd MMM yyyy · HH:mm 'UTC'", java.util.Locale.US);
            out.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return out.format(date);
        } catch (Exception e) {
            return iso;
        }
    }
}