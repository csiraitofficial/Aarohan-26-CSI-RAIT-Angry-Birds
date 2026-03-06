package com.example.souls.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.souls.R;
import com.example.souls.adapters.OnboardingAdapter;
import com.example.souls.models.OnboardingModel;
import com.example.souls.utils.SessionManager;
import com.tbuonomo.viewpagerdotsindicator.DotsIndicator;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private Button btnAction;
    private DotsIndicator dotsIndicator;
    private List<OnboardingModel> pages;
    private int currentPage = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        viewPager = findViewById(R.id.view_pager);
        btnAction = findViewById(R.id.btn_action);
        dotsIndicator = findViewById(R.id.dots_indicator);
        TextView tvSkip = findViewById(R.id.tv_skip);

        setupPages();
        setupViewPager();

        tvSkip.setOnClickListener(v -> navigateToLogin());

        btnAction.setOnClickListener(v -> {
            if (currentPage < pages.size() - 1) {
                viewPager.setCurrentItem(currentPage + 1, true);
            } else {
                navigateToLogin();
            }
        });
    }

    private void setupPages() {
        pages = new ArrayList<>();
        pages.add(new OnboardingModel(
                getString(R.string.onboard_title_1),
                getString(R.string.onboard_desc_1),
                R.drawable.ic_onboard_id
        ));
        pages.add(new OnboardingModel(
                getString(R.string.onboard_title_2),
                getString(R.string.onboard_desc_2),
                R.drawable.ic_onboard_wallet
        ));
        pages.add(new OnboardingModel(
                getString(R.string.onboard_title_3),
                getString(R.string.onboard_desc_3),
                R.drawable.ic_onboard_discover
        ));
    }

    private void setupViewPager() {
        OnboardingAdapter adapter = new OnboardingAdapter(pages);
        viewPager.setAdapter(adapter);
        dotsIndicator.attachTo(viewPager);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPage = position;
                boolean isLastPage = position == pages.size() - 1;
                btnAction.setText(isLastPage ?
                        getString(R.string.btn_get_started) :
                        getString(R.string.btn_next));
            }
        });
    }

    private void navigateToLogin() {
        SessionManager.getInstance(this).setOnboarded(true);
        startActivity(new Intent(this, LoginActivity.class));
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        finish();
    }
}