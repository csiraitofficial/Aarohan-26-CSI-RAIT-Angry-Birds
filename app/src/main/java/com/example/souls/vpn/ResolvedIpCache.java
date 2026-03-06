package com.example.souls.vpn;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * ResolvedIpCache
 *
 * Resolves known social-media domains to their current IP addresses and
 * stores them in a Set for fast O(1) lookup during VPN packet inspection.
 *
 * Refreshes every 30 minutes because CDN IPs rotate frequently.
 *
 * Usage:
 *   ResolvedIpCache.getInstance().init();   // call once from FirewallMonitorService
 *   ResolvedIpCache.getInstance().isBlocked("31.13.64.35");  // true/false
 */
public class ResolvedIpCache {

    // Domains whose IPs should be blocked for flagged/mod apps
    private static final String[] BLOCKED_DOMAINS = {
            "graph.instagram.com",
            "www.instagram.com",
            "i.instagram.com",
            "graph.facebook.com",
            "www.facebook.com",
            "api.twitter.com",
            "twitter.com",
            "api2.snapchat.com",
            "web.whatsapp.com",
            "v.whatsapp.net",
            "e2.whatsapp.net"
    };

    private static final long REFRESH_INTERVAL_MINUTES = 30;

    private static ResolvedIpCache instance;

    // Guarded by synchronized blocks on resolvedIps itself
    private final Set<String> resolvedIps = new HashSet<>();
    private ScheduledExecutorService scheduler;

    private ResolvedIpCache() {}

    public static synchronized ResolvedIpCache getInstance() {
        if (instance == null) instance = new ResolvedIpCache();
        return instance;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Call once from FirewallMonitorService.onCreate().
     * Resolves all domains immediately, then refreshes every 30 minutes.
     */
    public void init() {
        resolveAll(); // first resolution on calling thread's executor

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ResolvedIpCache-Refresh");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(
                this::resolveAll,
                REFRESH_INTERVAL_MINUTES,
                REFRESH_INTERVAL_MINUTES,
                TimeUnit.MINUTES
        );
    }

    /** Stop background refresh — call from FirewallMonitorService.onDestroy(). */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
    }

    // ── Query ─────────────────────────────────────────────────────────────────

    /**
     * Returns true if this IP belongs to a blocked social-media domain.
     * Called on the VPN packet-loop thread — must be fast.
     */
    public boolean isBlocked(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        synchronized (resolvedIps) {
            return resolvedIps.contains(ip);
        }
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    private void resolveAll() {
        Set<String> fresh = new HashSet<>();
        for (String domain : BLOCKED_DOMAINS) {
            try {
                InetAddress[] addresses = InetAddress.getAllByName(domain);
                for (InetAddress addr : addresses) {
                    fresh.add(addr.getHostAddress());
                }
            } catch (Exception ignored) {
                // DNS failure for one domain — continue with others
            }
        }

        if (!fresh.isEmpty()) {
            synchronized (resolvedIps) {
                resolvedIps.clear();
                resolvedIps.addAll(fresh);
            }
        }
        // If fresh is empty (total DNS failure) keep the previous cache intact
    }
}