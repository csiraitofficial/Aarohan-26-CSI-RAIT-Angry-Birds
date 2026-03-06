package com.example.lamp.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lamp.R;

public class ResultActivity extends AppCompatActivity {

    public static final String EXTRA_OK         = "extra_ok";
    public static final String EXTRA_SESSION_ID = "extra_session_id";
    public static final String EXTRA_MESSAGE    = "extra_message";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        boolean ok        = getIntent().getBooleanExtra(EXTRA_OK, false);
        String  sessionId = getIntent().getStringExtra(EXTRA_SESSION_ID);
        String  message   = getIntent().getStringExtra(EXTRA_MESSAGE);

        ImageView ivIcon    = findViewById(R.id.iv_result_icon);
        TextView  tvTitle   = findViewById(R.id.tv_result_title);
        TextView  tvMessage = findViewById(R.id.tv_result_message);
        TextView  tvSession = findViewById(R.id.tv_session_id);
        Button    btnAction = findViewById(R.id.btn_action);

        tvSession.setText("Session: " + (sessionId != null ? sessionId : "—"));

        if (ok) {
            ivIcon.setImageResource(R.drawable.ic_check_circle);
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#4CAF50"));
            tvTitle.setText("Verified ✓");
            tvTitle.setTextColor(android.graphics.Color.parseColor("#4CAF50"));
            tvMessage.setText(message != null ? message
                    : "User verified. The Souls app will now finalize registration.");
            btnAction.setText("New Scan");
            btnAction.setOnClickListener(v -> goToStandby());
            new Handler(Looper.getMainLooper()).postDelayed(this::goToStandby, 3_000);
        } else {
            ivIcon.setImageResource(R.drawable.ic_error_circle);
            ivIcon.setColorFilter(android.graphics.Color.parseColor("#FF5555"));
            tvTitle.setText("Verification Failed");
            tvTitle.setTextColor(android.graphics.Color.parseColor("#FF5555"));
            tvMessage.setText(message != null ? message : "Unknown error occurred.");
            btnAction.setText("Try Again");
            btnAction.setOnClickListener(v -> {
                startActivity(new Intent(this, ScanActivity.class));
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                finish();
            });
        }
    }

    private void goToStandby() {
        Intent intent = new Intent(this, StandbyActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    @Override
    public void onBackPressed() {
        goToStandby();
    }
}