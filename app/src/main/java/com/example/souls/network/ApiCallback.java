package com.example.souls.network;

import org.json.JSONObject;

public interface ApiCallback {
    void onSuccess(JSONObject data);
    void onError(String message);
}