package com.example.souls.utils;

import java.util.Random;

/**
 * Utility for generating Soul IDs and wallet addresses locally.
 * NOTE: In production the soulId comes from the API (POST /soul/finalize).
 */
public class SoulIdGenerator {

    private static final String HEX_CHARS = "0123456789ABCDEF";

    /** Generates a Soul ID in the format SOUL-XXXXXXXX */
    public static String generate() {
        StringBuilder sb = new StringBuilder("SOUL-");
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            sb.append(HEX_CHARS.charAt(random.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }

    /** Generates a mock wallet address (0x + 40 hex chars) */
    public static String generateWalletAddress() {
        StringBuilder sb = new StringBuilder("0x");
        Random random = new Random();
        for (int i = 0; i < 40; i++) {
            sb.append(HEX_CHARS.toLowerCase().charAt(random.nextInt(HEX_CHARS.length())));
        }
        return sb.toString();
    }
}