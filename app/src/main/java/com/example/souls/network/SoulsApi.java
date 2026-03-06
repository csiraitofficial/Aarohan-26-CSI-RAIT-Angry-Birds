package com.example.souls.network;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Souls API Client
 * Base URL: http://localhost:3000
 *
 * All calls are synchronous — run on a background thread (e.g. Executors).
 */
public class SoulsApi {

    private static final String TAG = "SoulsApi";
    private static final String BASE_URL = "http://localhost:3000";
    private static final int TIMEOUT_MS = 10_000;

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Check Device Registration
    //    GET /device/:deviceKey
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Called on every app launch. Returns registered=true if device has a Soul.
     */
    public static JSONObject checkDevice(String deviceKey) throws Exception {
        return get("/device/" + deviceKey);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Start BT Session with LAMP
    //    POST /session/start
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once BT connection to LAMP is established. Returns sessionId.
     */
    public static JSONObject startSession(String deviceKey, String lampId) throws Exception {
        JSONObject body = new JSONObject();
        body.put("deviceKey", deviceKey);
        body.put("lampId", lampId);
        return post("/session/start", body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Poll Session Status
    //    GET /session/:sessionId
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Poll every 2 seconds. Proceed to finalize when stage = FINGERPRINT_RECEIVED.
     */
    public static JSONObject pollSession(String sessionId) throws Exception {
        return get("/session/" + sessionId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Finalize Soul Registration
    //    POST /soul/finalize
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Call once after FINGERPRINT_RECEIVED. Mints Soul on blockchain.
     */
    public static JSONObject finalizeSoul(String sessionId, String deviceKey) throws Exception {
        JSONObject body = new JSONObject();
        body.put("sessionId", sessionId);
        body.put("deviceKey", deviceKey);
        return post("/soul/finalize", body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Look Up a Soul by soulId
    //    GET /soul/:soulId
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONObject getSoul(String soulId) throws Exception {
        return get("/soul/" + soulId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Look Up a Soul by deviceKey
    //    GET /soul/by-device/:deviceKey
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONObject getSoulByDevice(String deviceKey) throws Exception {
        return get("/soul/by-device/" + deviceKey);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. View Full Blockchain
    //    GET /chain
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONObject getChain() throws Exception {
        return get("/chain");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Validate Chain Integrity
    //    GET /validate
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONObject validateChain() throws Exception {
        return get("/validate");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LAMP App Endpoint (included for completeness)
    //    POST /lamp/verify
    // ─────────────────────────────────────────────────────────────────────────

    public static JSONObject lampVerify(String sessionId, String lampId, String fingerprintHash) throws Exception {
        JSONObject body = new JSONObject();
        body.put("sessionId", sessionId);
        body.put("lampId", lampId);
        body.put("fingerprintHash", fingerprintHash);
        return post("/lamp/verify", body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static JSONObject get(String path) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        return readResponse(conn);
    }

    private static JSONObject post(String path, JSONObject body) throws Exception {
        URL url = new URL(BASE_URL + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");

        byte[] input = body.toString().getBytes(StandardCharsets.UTF_8);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input);
        }

        return readResponse(conn);
    }

    private static JSONObject readResponse(HttpURLConnection conn) throws Exception {
        int code = conn.getResponseCode();
        boolean isError = code >= 400;

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                isError ? conn.getErrorStream() : conn.getInputStream(),
                StandardCharsets.UTF_8));

        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        Log.d(TAG, "HTTP " + code + " → " + sb);
        return new JSONObject(sb.toString());
    }
}