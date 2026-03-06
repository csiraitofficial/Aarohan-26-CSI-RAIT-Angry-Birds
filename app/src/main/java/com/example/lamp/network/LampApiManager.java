package com.example.lamp.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LampApiManager {

    private static final String TAG      = "LampApi";
    private static final String BASE_URL = "https://souls-blockchain-production.up.railway.app";
    private static final int    TIMEOUT  = 15_000;

    private static LampApiManager instance;
    private final ExecutorService executor    = Executors.newCachedThreadPool();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private LampApiManager() {}

    public static synchronized LampApiManager get() {
        if (instance == null) instance = new LampApiManager();
        return instance;
    }

    public interface Callback {
        void onSuccess(JSONObject data);
        void onError(String message);
    }

    public void verify(String sessionId, String lampId, String fingerprintHash, Callback cb) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("sessionId",       sessionId);
                body.put("lampId",          lampId);
                body.put("fingerprintHash", fingerprintHash);

                URL url = new URL(BASE_URL + "/lamp/verify");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Accept",       "application/json");

                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(bytes);
                }

                int code = conn.getResponseCode();
                java.io.InputStream stream = (code >= 200 && code < 300)
                        ? conn.getInputStream() : conn.getErrorStream();

                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }

                JSONObject json = new JSONObject(sb.toString());
                mainHandler.post(() -> cb.onSuccess(json));

            } catch (Exception e) {
                Log.e(TAG, "POST /lamp/verify failed", e);
                mainHandler.post(() -> cb.onError("Network error: " + e.getMessage()));
            }
        });
    }
}