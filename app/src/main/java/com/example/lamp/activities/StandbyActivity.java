package com.example.lamp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lamp.R;
import com.example.lamp.utils.FingerprintHelper;
import com.example.lamp.utils.LampConfig;

public class StandbyActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_standby);

        TextView tvLampId    = findViewById(R.id.tv_lamp_id);
        TextView tvLampName  = findViewById(R.id.tv_lamp_name);
        TextView tvBioStatus = findViewById(R.id.tv_bio_status);
        Button   btnStart    = findViewById(R.id.btn_start_scan);

        tvLampId.setText(LampConfig.LAMP_ID);
        tvLampName.setText(LampConfig.LAMP_DISPLAY_NAME);

        updateBioStatus(tvBioStatus);

        btnStart.setOnClickListener(v -> {
            startActivity(new Intent(this, ScanActivity.class));
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateBioStatus(findViewById(R.id.tv_bio_status));
    }

    private void updateBioStatus(TextView tvBioStatus) {
        if (FingerprintHelper.isAvailable(this)) {
            tvBioStatus.setText("● Fingerprint sensor ready");
            tvBioStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
        } else {
            tvBioStatus.setText("⚠ Fingerprint sensor unavailable");
            tvBioStatus.setTextColor(android.graphics.Color.parseColor("#FF5555"));
        }
    }
}