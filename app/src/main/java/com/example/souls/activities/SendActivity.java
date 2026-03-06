package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.souls.R;
import com.example.souls.network.ApiCallback;
import com.example.souls.network.ApiManager;

import org.json.JSONObject;

/**
 * SendActivity — repurposed as Soul Lookup screen.
 *
 * Calls GET /soul/:soulId and displays the full block record.
 *
 * Response shape:
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
public class SendActivity extends AppCompatActivity {

    private EditText    etSoulId;
    private Button      btnLookup;
    private ProgressBar progressBar;
    private TextView    tvError;
    private LinearLayout llResult;

    // Result fields — map directly to block JSON
    private TextView tvResultSoulId, tvResultSoulHash, tvResultBlockHash, tvResultVerifiedAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        ImageView ivBack = findViewById(R.id.iv_back);
        ivBack.setOnClickListener(v -> onBackPressed());

        etSoulId    = findViewById(R.id.et_soul_id);
        btnLookup   = findViewById(R.id.btn_lookup);
        progressBar = findViewById(R.id.progress_bar);
        tvError     = findViewById(R.id.tv_error);
        llResult    = findViewById(R.id.ll_result);

        tvResultSoulId    = findViewById(R.id.tv_result_soul_id);
        tvResultSoulHash  = findViewById(R.id.tv_result_soul_hash);
        tvResultBlockHash = findViewById(R.id.tv_result_block_hash);
        tvResultVerifiedAt= findViewById(R.id.tv_result_verified_at);

        btnLookup.setOnClickListener(v -> lookupSoul());
    }

    private void lookupSoul() {
        String soulId = etSoulId.getText().toString().trim();
        if (TextUtils.isEmpty(soulId)) {
            showError(getString(R.string.error_soul_not_found));
            return;
        }

        hideError();
        llResult.setVisibility(View.GONE);
        setLoading(true);

        ApiManager.get().getSoulById(soulId, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject data) {
                setLoading(false);

                if (!data.optBoolean("ok", false)) {
                    showError(getString(R.string.error_soul_not_found));
                    return;
                }

                JSONObject block = data.optJSONObject("block");
                if (block == null) {
                    showError(getString(R.string.error_soul_not_found));
                    return;
                }

                JSONObject blockData = block.optJSONObject("data");

                // Populate result card from block fields
                tvResultSoulId.setText(
                        blockData != null ? blockData.optString("soulId", "—") : "—");
                tvResultSoulHash.setText(
                        blockData != null ? blockData.optString("soulHash", "—") : "—");
                tvResultBlockHash.setText(block.optString("hash", "—"));
                tvResultVerifiedAt.setText(
                        blockData != null ? blockData.optString("verifiedAt", "—") : "—");

                llResult.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                showError(getString(R.string.error_no_connection));
            }
        });
    }

    private void setLoading(boolean loading) {
        btnLookup.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        tvError.setVisibility(View.GONE);
    }
}