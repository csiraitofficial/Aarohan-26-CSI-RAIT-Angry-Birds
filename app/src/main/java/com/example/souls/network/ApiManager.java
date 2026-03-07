package com.example.souls.network;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ApiManager
 *
 * Singleton HTTP client for the Souls blockchain API.
 * Base URL: https://souls-blockchain-production.up.railway.app
 *
 * Improvements:
 *  - 30s timeout (Railway free tier can take ~20s to cold-start)
 *  - Automatic single retry on timeout
 *  - Connectivity check before requests
 *  - Human-readable error messages (no raw Java exceptions shown to users)
 */
public class ApiManager {

    private static final String TAG      = "ApiManager";
    private static final String BASE_URL = "https://souls-blockchain-production.up.railway.app";

    // Railway free tier can take ~20s to cold-start — 30s gives it room to wake up
    private static final int TIMEOUT_MS  = 30_000;

    private static ApiManager instance;
    private static Context    appContext;

    private final ExecutorService executor    = Executors.newCachedThreadPool();
    private final Handler         mainHandler = new Handler(Looper.getMainLooper());

    private ApiManager() {}

    /**
     * Call this once in Application.onCreate() or before first use.
     * Needed for connectivity checks.
     */
    public static synchronized ApiManager get(Context ctx) {
        if (instance == null) {
            appContext = ctx.getApplicationContext();
            instance   = new ApiManager();
        }
        return instance;
    }

    /** Convenience accessor (context already initialised). */
    public static synchronized ApiManager get() {
        return instance != null ? instance : new ApiManager();
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void checkDevice(String deviceKey, ApiCallback cb) {
        doGet("/device/" + deviceKey, cb);
    }

    public void startSession(String deviceKey, String lampId, ApiCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceKey", deviceKey);
            body.put("lampId",    lampId);
            doPost("/session/start", body, cb);
        } catch (Exception e) {
            deliverError(cb, "Failed to build request.");
        }
    }

    public void pollSession(String sessionId, ApiCallback cb) {
        doGet("/session/" + sessionId, cb);
    }

    public void finalizeSoul(String sessionId, String deviceKey, ApiCallback cb) {
        try {
            JSONObject body = new JSONObject();
            body.put("sessionId", sessionId);
            body.put("deviceKey", deviceKey);
            doPost("/soul/finalize", body, cb);
        } catch (Exception e) {
            deliverError(cb, "Failed to build request.");
        }
    }

    public void getSoulByDevice(String deviceKey, ApiCallback cb) {
        doGet("/soul/by-device/" + deviceKey, cb);
    }

    public void getSoulById(String soulId, ApiCallback cb) {
        doGet("/soul/" + soulId, cb);
    }

    public void getChain(ApiCallback cb) {
        doGet("/chain", cb);
    }

    public void validateChain(ApiCallback cb) {
        doGet("/validate", cb);
    }

    // ── Internal HTTP ─────────────────────────────────────────────────────────

    private void doGet(String path, ApiCallback cb) {
        executor.execute(() -> {
            // Check connectivity first — gives a clear message before any network attempt
            if (!isConnected()) {
                deliverError(cb, "No internet connection. Please check your network and try again.");
                return;
            }
            executeGet(path, cb, true);
        });
    }

    private void executeGet(String path, ApiCallback cb, boolean allowRetry) {
        try {
            URL url = new URL(BASE_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");

            String response = readResponse(conn);
            JSONObject json  = new JSONObject(response);
            deliverSuccess(cb, json);

        } catch (SocketTimeoutException e) {
            Log.w(TAG, "GET " + path + " timed out. allowRetry=" + allowRetry);
            if (allowRetry) {
                // Retry once — server may have been cold-starting
                Log.i(TAG, "Retrying GET " + path);
                executeGet(path, cb, false);
            } else {
                deliverError(cb, "The server took too long to respond. It may be starting up — please try again in a moment.");
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "GET " + path + " — DNS failure", e);
            deliverError(cb, "Cannot reach the Souls server. Check your internet connection.");
        } catch (Exception e) {
            Log.e(TAG, "GET " + path + " failed", e);
            deliverError(cb, "Connection failed. Please check your internet and try again.");
        }
    }

    private void doPost(String path, JSONObject body, ApiCallback cb) {
        executor.execute(() -> {
            if (!isConnected()) {
                deliverError(cb, "No internet connection. Please check your network and try again.");
                return;
            }
            executePost(path, body, cb, true);
        });
    }

    private void executePost(String path, JSONObject body, ApiCallback cb, boolean allowRetry) {
        try {
            URL url = new URL(BASE_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept",       "application/json");

            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(bytes);
            }

            String response = readResponse(conn);
            JSONObject json  = new JSONObject(response);
            deliverSuccess(cb, json);

        } catch (SocketTimeoutException e) {
            Log.w(TAG, "POST " + path + " timed out. allowRetry=" + allowRetry);
            if (allowRetry) {
                Log.i(TAG, "Retrying POST " + path);
                executePost(path, body, cb, false);
            } else {
                deliverError(cb, "The server took too long to respond. It may be starting up — please try again in a moment.");
            }
        } catch (UnknownHostException e) {
            Log.e(TAG, "POST " + path + " — DNS failure", e);
            deliverError(cb, "Cannot reach the Souls server. Check your internet connection.");
        } catch (Exception e) {
            Log.e(TAG, "POST " + path + " failed", e);
            deliverError(cb, "Connection failed. Please check your internet and try again.");
        }
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

    // ── Connectivity check ────────────────────────────────────────────────────

    private boolean isConnected() {
        if (appContext == null) return true; // Can't check — assume connected
        ConnectivityManager cm = (ConnectivityManager)
                appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null
                    && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    ||  caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    ||  caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            android.net.NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    // ── Delivery helpers ──────────────────────────────────────────────────────

    private void deliverSuccess(ApiCallback cb, JSONObject json) {
        mainHandler.post(() -> cb.onSuccess(json));
    }

    private void deliverError(ApiCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg));
    }
}