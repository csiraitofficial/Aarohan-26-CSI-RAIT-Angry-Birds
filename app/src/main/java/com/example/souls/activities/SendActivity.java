package com.example.souls.activities;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;

import org.json.JSONObject;

/**
 * SendActivity — "Verify a Person" screen.
 *
 * Calls GET /soul/:soulId and shows a clear ✅ VERIFIED HUMAN or ❌ NOT FOUND result.
 * Users can copy the looked-up Soul ID or share the verification result.
 */
public class SendActivity extends AppCompatActivity {

    private EditText    etSoulId;
    private Button      btnVerify;
    private ProgressBar progressBar;
    private TextView    tvError;

    // Verification result banner
    private LinearLayout llVerifiedBanner;
    private LinearLayout llNotFoundBanner;

    // Detail card (shown only on success)
    private LinearLayout llResult;
    private TextView tvResultSoulId, tvResultSoulHash, tvResultBlockHash, tvResultVerifiedAt;
    private Button btnShareResult, btnCopyId;

    // Tracks the last verified soul ID for sharing
    private String lastVerifiedSoulId = "";
    private String lastVerifiedAt     = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        etSoulId      = findViewById(R.id.et_soul_id);
        btnVerify     = findViewById(R.id.btn_lookup);
        progressBar   = findViewById(R.id.progress_bar);
        tvError       = findViewById(R.id.tv_error);

        llVerifiedBanner = findViewById(R.id.ll_verified_banner);
        llNotFoundBanner = findViewById(R.id.ll_not_found_banner);

        llResult          = findViewById(R.id.ll_result);
        tvResultSoulId    = findViewById(R.id.tv_result_soul_id);
        tvResultSoulHash  = findViewById(R.id.tv_result_soul_hash);
        tvResultBlockHash = findViewById(R.id.tv_result_block_hash);
        tvResultVerifiedAt= findViewById(R.id.tv_result_verified_at);
        btnShareResult    = findViewById(R.id.btn_share_result);
        btnCopyId         = findViewById(R.id.btn_copy_id);

        btnVerify.setOnClickListener(v -> verifySoul());

        // Allow pressing Done on keyboard to trigger verify
        etSoulId.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                verifySoul();
                return true;
            }
            return false;
        });

        btnShareResult.setOnClickListener(v -> shareResult());
        btnCopyId.setOnClickListener(v -> copyToClipboard(lastVerifiedSoulId));
    }

    // ── Core lookup ───────────────────────────────────────────────────────────

    private void verifySoul() {
        String input = etSoulId.getText().toString().trim().toUpperCase();

        if (TextUtils.isEmpty(input)) {
            showError("Please enter a Soul ID");
            return;
        }

        // Accept bare hex (e.g. "AC7EF884") or prefixed (e.g. "SOUL-AC7EF884")
        if (!input.startsWith("SOUL-")) {
            input = "SOUL-" + input;
            etSoulId.setText(input);
        }

        // Dismiss keyboard
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etSoulId.getWindowToken(), 0);

        // Reset UI
        hideError();
        hideBanners();
        llResult.setVisibility(View.GONE);
        setLoading(true);

        final String soulId = input;
        ApiManager.get().getSoulById(soulId, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                setLoading(false);

                if (!data.optBoolean("ok", false)) {
                    showNotFound();
                    return;
                }

                JSONObject block = data.optJSONObject("block");
                if (block == null) {
                    showNotFound();
                    return;
                }

                JSONObject blockData = block.optJSONObject("data");
                String foundSoulId   = blockData != null ? blockData.optString("soulId",   "—") : "—";
                String soulHash      = blockData != null ? blockData.optString("soulHash",  "—") : "—";
                String blockHash     = block.optString("hash", "—");
                String verifiedAt    = blockData != null ? blockData.optString("verifiedAt","—") : "—";

                lastVerifiedSoulId = foundSoulId;
                lastVerifiedAt     = formatDate(verifiedAt);

                showVerified();

                tvResultSoulId.setText(foundSoulId);
                tvResultSoulHash.setText(soulHash);
                tvResultBlockHash.setText(blockHash);
                tvResultVerifiedAt.setText(formatDate(verifiedAt));
                llResult.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(message);
            }
        });
    }

    // ── Result actions ────────────────────────────────────────────────────────

    private void shareResult() {
        String text =
                "✅ Verified Human — Souls Blockchain\n\n"
                        + "Soul ID:     " + lastVerifiedSoulId + "\n"
                        + "Verified At: " + lastVerifiedAt + "\n\n"
                        + "Verified using the Souls app — proof of humanity on-chain.";

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(shareIntent, "Share Verification"));
    }

    private void copyToClipboard(String text) {
        if (TextUtils.isEmpty(text) || text.equals("—")) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("Soul ID", text));
        Toast.makeText(this, "Soul ID copied", Toast.LENGTH_SHORT).show();
    }

    // ── UI state helpers ──────────────────────────────────────────────────────

    private void showVerified() {
        llVerifiedBanner.setVisibility(View.VISIBLE);
        llNotFoundBanner.setVisibility(View.GONE);
    }

    private void showNotFound() {
        llNotFoundBanner.setVisibility(View.VISIBLE);
        llVerifiedBanner.setVisibility(View.GONE);
        llResult.setVisibility(View.GONE);
    }

    private void hideBanners() {
        llVerifiedBanner.setVisibility(View.GONE);
        llNotFoundBanner.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        btnVerify.setEnabled(!loading);
        btnVerify.setText(loading ? "Verifying…" : "Verify Person");
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }

    // ── Date formatter ────────────────────────────────────────────────────────

    private String formatDate(String iso) {
        if (iso == null || iso.isEmpty() || iso.equals("—")) return "—";
        try {
            String clean   = iso.endsWith("Z") ? iso.substring(0, iso.length() - 1) : iso;
            String pattern = clean.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSS"
                    : "yyyy-MM-dd'T'HH:mm:ss";
            java.text.SimpleDateFormat in  = new java.text.SimpleDateFormat(pattern, java.util.Locale.US);
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