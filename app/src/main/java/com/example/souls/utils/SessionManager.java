package com.example.souls.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager
 *
 * Persists identity and session state to SharedPreferences.
 *
 * Fields map 1-to-1 with API response fields:
 *
 * GET /device/:deviceKey
 *   registered, soulId
 *
 * POST /session/start  →  GET /session/:sessionId
 *   sessionId, lampId, stage, createdAt, expiresAt, verifiedAt
 *
 * POST /soul/finalize
 *   soulId, soulHash, blockHash (= block.hash), blockIndex (= block.index)
 *
 * GET /soul/:soulId  →  block object
 *   block.index, block.timestamp, block.hash, block.previousHash, block.nonce
 *   block.data.soulId, block.data.soulHash, block.data.deviceKey,
 *   block.data.lampId, block.data.verifiedAt
 *
 * Local-only (never sent to API):
 *   userName, onboarded, firewallEnabled
 */
public class SessionManager {

    private static final String PREFS = "souls_prefs";

    // ── Session (cleared after finalize) ─────────────────────────────────────
    private static final String K_SESSION    = "sessionId";
    private static final String K_CREATED_AT = "createdAt";
    private static final String K_EXPIRES_AT = "expiresAt";

    // ── Device ────────────────────────────────────────────────────────────────
    private static final String K_DEVICE     = "deviceKey";

    // ── block.data fields ────────────────────────────────────────────────────
    private static final String K_SOUL_ID    = "soulId";
    private static final String K_SOUL_HASH  = "soulHash";
    private static final String K_LAMP_ID    = "lampId";
    private static final String K_VERIFIED_AT= "verifiedAt";
    private static final String K_FP_HASH    = "fingerprintHash";
    private static final String K_REG_SESSION= "registrationSessionId";

    // ── Block-level fields ───────────────────────────────────────────────────
    private static final String K_BLK_HASH   = "blockHash";
    private static final String K_BLK_IDX    = "blockIndex";
    private static final String K_BLK_PREV   = "previousHash";
    private static final String K_BLK_NONCE  = "blockNonce";
    private static final String K_BLK_TS     = "blockTimestamp";

    // ── Local only ───────────────────────────────────────────────────────────
    private static final String K_NAME           = "userName";
    private static final String K_ONBOARDED      = "onboarded";
    private static final String K_FIREWALL        = "firewallEnabled"; // ← ADDED

    private static SessionManager instance;
    private final SharedPreferences prefs;

    private SessionManager(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context ctx) {
        if (instance == null) instance = new SessionManager(ctx);
        return instance;
    }

    // ── Setters ───────────────────────────────────────────────────────────────

    public void setSessionId(String v)      { put(K_SESSION,     v); }
    public void setCreatedAt(String v)      { put(K_CREATED_AT,  v); }
    public void setExpiresAt(String v)      { put(K_EXPIRES_AT,  v); }

    public void setDeviceKey(String v)      { put(K_DEVICE,      v); }

    public void setSoulId(String v)         { put(K_SOUL_ID,     v); }
    public void setSoulHash(String v)       { put(K_SOUL_HASH,   v); }
    public void setLampId(String v)         { put(K_LAMP_ID,     v); }
    public void setVerifiedAt(String v)     { put(K_VERIFIED_AT, v); }
    public void setFingerprintHash(String v){ put(K_FP_HASH,     v); }
    public void setRegSessionId(String v)   { put(K_REG_SESSION, v); }

    public void setBlockHash(String v)      { put(K_BLK_HASH,    v); }
    public void setBlockIndex(int v)        { prefs.edit().putInt(K_BLK_IDX,   v).apply(); }
    public void setPreviousHash(String v)   { put(K_BLK_PREV,    v); }
    public void setBlockNonce(int v)        { prefs.edit().putInt(K_BLK_NONCE, v).apply(); }
    public void setBlockTimestamp(String v) { put(K_BLK_TS,      v); }

    public void setUserName(String v)       { put(K_NAME,        v); }
    public void setOnboarded(boolean v)     { prefs.edit().putBoolean(K_ONBOARDED, v).apply(); }

    /** Persists the user's firewall toggle choice across reboots. */
    public void setFirewallEnabled(boolean v) {                        // ← ADDED
        prefs.edit().putBoolean(K_FIREWALL, v).apply();
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getSessionId()     { return get(K_SESSION);     }
    public String getCreatedAt()     { return get(K_CREATED_AT);  }
    public String getExpiresAt()     { return get(K_EXPIRES_AT);  }

    public String getDeviceKey()     { return get(K_DEVICE);      }

    public String getSoulId()        { return get(K_SOUL_ID);     }
    public String getSoulHash()      { return get(K_SOUL_HASH);   }
    public String getLampId()        { return get(K_LAMP_ID);     }
    public String getVerifiedAt()    { return get(K_VERIFIED_AT); }
    public String getFingerprintHash(){ return get(K_FP_HASH);    }
    public String getRegSessionId()  { return get(K_REG_SESSION); }

    public String getBlockHash()     { return get(K_BLK_HASH);    }
    public int    getBlockIndex()    { return prefs.getInt(K_BLK_IDX,   -1); }
    public String getPreviousHash()  { return get(K_BLK_PREV);    }
    public int    getBlockNonce()    { return prefs.getInt(K_BLK_NONCE,  0); }
    public String getBlockTimestamp(){ return get(K_BLK_TS);      }

    public String  getUserName()     { return get(K_NAME);        }
    public boolean isOnboarded()     { return prefs.getBoolean(K_ONBOARDED, false); }

    /** Returns true if the user has enabled the VPN firewall. Defaults to false. */
    public boolean isFirewallEnabled() {                                // ← ADDED
        return prefs.getBoolean(K_FIREWALL, false);
    }

    // ── Convenience ──────────────────────────────────────────────────────────

    public boolean isRegistered() {
        String s = getSoulId();
        return s != null && !s.isEmpty();
    }

    public void persistBlock(org.json.JSONObject block) {
        if (block == null) return;
        setBlockHash(block.optString("hash", getBlockHash()));
        setBlockIndex(block.optInt("index", getBlockIndex()));
        setPreviousHash(block.optString("previousHash", getPreviousHash()));
        setBlockNonce(block.optInt("nonce", getBlockNonce()));
        setBlockTimestamp(block.optString("timestamp", getBlockTimestamp()));

        org.json.JSONObject data = block.optJSONObject("data");
        if (data != null) {
            setSoulId(data.optString("soulId",          getSoulId()));
            setSoulHash(data.optString("soulHash",       getSoulHash()));
            setLampId(data.optString("lampId",           getLampId()));
            setVerifiedAt(data.optString("verifiedAt",   getVerifiedAt()));
            setFingerprintHash(data.optString("fingerprintHash", getFingerprintHash()));
            setRegSessionId(data.optString("sessionId",  getRegSessionId()));
        }
    }

    public void clearSession() {
        prefs.edit()
                .remove(K_SESSION)
                .remove(K_CREATED_AT)
                .remove(K_EXPIRES_AT)
                .apply();
    }

    public void clearAll() {
        prefs.edit().clear().apply();
    }

    /**
     * Logout — clears registration state but preserves deviceKey, onboarded
     * flag, and firewallEnabled so the firewall stays active after re-login.
     */
    public void logout() {
        String  deviceKey      = getDeviceKey();
        boolean onboarded      = isOnboarded();
        boolean firewallActive = isFirewallEnabled();          // ← preserve
        prefs.edit().clear().apply();
        setDeviceKey(deviceKey);
        setOnboarded(onboarded);
        setFirewallEnabled(firewallActive);                    // ← restore
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void put(String key, String val) {
        prefs.edit().putString(key, val != null ? val : "").apply();
    }

    private String get(String key) {
        return prefs.getString(key, "");
    }
}