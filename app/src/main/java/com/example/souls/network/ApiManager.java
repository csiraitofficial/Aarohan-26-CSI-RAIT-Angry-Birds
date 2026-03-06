package com.example.souls.network;

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

/**
 * ApiManager
 *
 * Singleton HTTP client for the Souls blockchain API.
 * Base URL: https://souls-blockchain-production.up.railway.app
 *
 * All calls run on a background thread; callbacks are delivered on the main thread.
 */
public class ApiManager {

    private static final String TAG      = "ApiManager";
    private static final String BASE_URL = "https://souls-blockchain-production.up.railway.app";
    private static final int    TIMEOUT  = 15_000; // 15 s

    private static ApiManager instance;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private ApiManager() {}

    public static synchronized ApiManager get() {
        if (instance == null) instance = new ApiManager();
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Check device registration
    //    GET /device/:deviceKey
    // ─────────────────────────────────────────────────────────────────────────
    public void checkDevice(String deviceKey, ApiCallback cb) {
        String path = "/device/" + deviceKey;
        doGet(path, cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Start BT session
    //    POST /session/start  { deviceKey, lampId }
    // ─────────────────────────────────────────────────────────────────────────
    public void startSession(String deviceKey, String lampId, ApiCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceKey", deviceKey);
            body.put("lampId",    lampId);
            doPost("/session/start", body, cb);
        } catch (Exception e) {
            deliverError(cb, "Failed to build request: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Poll session status
    //    GET /session/:sessionId
    // ─────────────────────────────────────────────────────────────────────────
    public void pollSession(String sessionId, ApiCallback cb) {
        doGet("/session/" + sessionId, cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Finalize Soul registration
    //    POST /soul/finalize  { sessionId, deviceKey }
    // ─────────────────────────────────────────────────────────────────────────
    public void finalizeSoul(String sessionId, String deviceKey, ApiCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("sessionId", sessionId);
            body.put("deviceKey", deviceKey);
            doPost("/soul/finalize", body, cb);
        } catch (Exception e) {
            deliverError(cb, "Failed to build request: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Look up Soul by device key
    //    GET /soul/by-device/:deviceKey
    // ─────────────────────────────────────────────────────────────────────────
    public void getSoulByDevice(String deviceKey, ApiCallback cb) {
        doGet("/soul/by-device/" + deviceKey, cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Look up Soul by Soul ID
    //    GET /soul/:soulId
    // ─────────────────────────────────────────────────────────────────────────
    public void getSoulById(String soulId, ApiCallback cb) {
        doGet("/soul/" + soulId, cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. View full blockchain
    //    GET /chain
    // ─────────────────────────────────────────────────────────────────────────
    public void getChain(ApiCallback cb) {
        doGet("/chain", cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Validate chain integrity
    //    GET /validate
    // ─────────────────────────────────────────────────────────────────────────
    public void validateChain(ApiCallback cb) {
        doGet("/validate", cb);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Internal HTTP helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void doGet(String path, ApiCallback cb) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(TIMEOUT);
                conn.setReadTimeout(TIMEOUT);
                conn.setRequestProperty("Accept", "application/json");

                String response = readResponse(conn);
                JSONObject json = new JSONObject(response);
                deliverSuccess(cb, json);

            } catch (Exception e) {
                Log.e(TAG, "GET " + path + " failed", e);
                deliverError(cb, "Network error: " + e.getMessage());
            }
        });
    }

    private void doPost(String path, JSONObject body, ApiCallback cb) {
        executor.execute(() -> {
            try {
                URL url = new URL(BASE_URL + path);
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

                String response = readResponse(conn);
                JSONObject json = new JSONObject(response);
                deliverSuccess(cb, json);

            } catch (Exception e) {
                Log.e(TAG, "POST " + path + " failed", e);
                deliverError(cb, "Network error: " + e.getMessage());
            }
        });
    }

    /** Reads body from both success (2xx) and error (4xx/5xx) responses. */
    private String readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        java.io.InputStream stream = (code >= 200 && code < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        if (stream == null) return "{}";

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private void deliverSuccess(ApiCallback cb, JSONObject json) {
        mainHandler.post(() -> cb.onSuccess(json));
    }

    private void deliverError(ApiCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg));
    }
}