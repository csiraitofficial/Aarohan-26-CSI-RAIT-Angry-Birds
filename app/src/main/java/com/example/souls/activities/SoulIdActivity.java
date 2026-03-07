package com.example.souls.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;
import com.example.souls.utils.IdentityPdfExporter;
import com.example.souls.utils.SessionManager;

import org.json.JSONObject;

/**
 * SoulIdActivity — full block record from GET /soul/:soulId
 *
 * New in this version:
 *   • "Export PDF Certificate" button at the bottom
 *   • Tap any hash / ID field to copy it to clipboard
 */
public class SoulIdActivity extends AppCompatActivity {

    private TextView    tvSoulId, tvSoulHash, tvBlockHash, tvPreviousHash,
            tvBlockIndex, tvBlockNonce, tvBlockTimestamp,
            tvDeviceKey, tvLampId, tvVerifiedAt,
            tvFingerprintHash, tvRegSessionId, tvError;
    private ProgressBar progressBar;
    private Button      btnExportPdf;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_soul_id);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        tvSoulId         = findViewById(R.id.tv_soul_id_full);
        tvSoulHash       = findViewById(R.id.tv_soul_hash);
        tvBlockHash      = findViewById(R.id.tv_block_hash);
        tvPreviousHash   = findViewById(R.id.tv_previous_hash);
        tvBlockIndex     = findViewById(R.id.tv_block_index);
        tvBlockNonce     = findViewById(R.id.tv_block_nonce);
        tvBlockTimestamp = findViewById(R.id.tv_timestamp);
        tvDeviceKey      = findViewById(R.id.tv_device_key);
        tvLampId         = findViewById(R.id.tv_lamp_id);
        tvVerifiedAt     = findViewById(R.id.tv_verified_at);
        tvFingerprintHash= findViewById(R.id.tv_fingerprint_hash);
        tvRegSessionId   = findViewById(R.id.tv_reg_session_id);
        tvError          = findViewById(R.id.tv_error);
        progressBar      = findViewById(R.id.progress_bar);
        btnExportPdf     = findViewById(R.id.btn_export_pdf);

        // Tap-to-copy on every hash / ID field
        makeCopyable(tvSoulId,          "Soul ID");
        makeCopyable(tvSoulHash,        "Soul Hash");
        makeCopyable(tvBlockHash,       "Block Hash");
        makeCopyable(tvPreviousHash,    "Previous Hash");
        makeCopyable(tvDeviceKey,       "Device Key");
        makeCopyable(tvFingerprintHash, "Fingerprint Hash");
        makeCopyable(tvRegSessionId,    "Registration Session ID");

        // Export PDF
        btnExportPdf.setOnClickListener(v -> exportPdf());

        // Show cached values instantly
        bindCache();

        // Fetch live from chain
        fetchBlock();
    }

    // ── Export PDF ────────────────────────────────────────────────────────────

    private void exportPdf() {
        SessionManager sm = SessionManager.getInstance(this);
        if (!sm.isRegistered()) {
            Toast.makeText(this, "No Soul ID found — complete registration first.", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(this, "Generating certificate…", Toast.LENGTH_SHORT).show();
        IdentityPdfExporter.export(this, sm);
    }

    // ── Tap-to-copy helper ────────────────────────────────────────────────────

    private void makeCopyable(TextView tv, String label) {
        tv.setOnClickListener(v -> {
            String text = tv.getText().toString();
            if (text.isEmpty() || text.equals("—")) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text));
            Toast.makeText(this, label + " copied", Toast.LENGTH_SHORT).show();
        });
    }

    // ── Bind from local cache ─────────────────────────────────────────────────

    private void bindCache() {
        SessionManager sm = SessionManager.getInstance(this);
        tvSoulId.setText(sm.getSoulId().isEmpty()           ? "—" : sm.getSoulId());
        tvSoulHash.setText(sm.getSoulHash().isEmpty()       ? "—" : sm.getSoulHash());
        tvBlockHash.setText(sm.getBlockHash().isEmpty()     ? "—" : sm.getBlockHash());
        tvPreviousHash.setText(sm.getPreviousHash().isEmpty()? "—" : sm.getPreviousHash());
        tvBlockIndex.setText(sm.getBlockIndex() >= 0        ? "Block #" + sm.getBlockIndex() : "—");
        tvBlockNonce.setText(sm.getBlockNonce() > 0         ? String.valueOf(sm.getBlockNonce()) : "—");
        tvBlockTimestamp.setText(formatDate(sm.getBlockTimestamp()));
        tvDeviceKey.setText(sm.getDeviceKey().isEmpty()     ? "—" : sm.getDeviceKey());
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

                SessionManager.getInstance(SoulIdActivity.this).persistBlock(block);
                bindCache();
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                tvError.setText(getString(R.string.soul_id_offline));
                tvError.setVisibility(View.VISIBLE);
            }
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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