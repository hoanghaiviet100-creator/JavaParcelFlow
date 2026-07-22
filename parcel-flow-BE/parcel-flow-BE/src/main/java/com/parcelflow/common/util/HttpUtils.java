package com.parcelflow.common.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the real client IP safely.
 *
 * <p>C-3 fix: {@code X-Forwarded-For} is client-controllable. We only honour it when the
 * immediate peer ({@link HttpServletRequest#getRemoteAddr()}) is a trusted reverse proxy /
 * load balancer (loopback or RFC-1918 private ranges by default). Otherwise a client could
 * spoof its IP and defeat the single-session / one-IP policy.
 *
 * <p>In production, put the app strictly behind a proxy that OVERWRITES (not appends)
 * X-Forwarded-For, and keep the app port unreachable from the public internet.
 */
public final class HttpUtils {

    private HttpUtils() {
    }

    public static String getClientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (isTrustedProxy(peer)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The left-most entry is the original client (proxy appends on the right).
                String candidate = forwarded.split(",")[0].trim();
                if (!candidate.isEmpty()) {
                    return candidate;
                }
            }
        }
        return peer;
    }

    /** Loopback + common private ranges. Adjust for your infra if proxies sit elsewhere. */
    private static boolean isTrustedProxy(String ip) {
        if (ip == null) {
            return false;
        }
        // IPv6 loopback / IPv4 loopback
        if (ip.equals("::1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("127.")) {
            return true;
        }
        // RFC-1918 private ranges (Docker/K8s/VPC internal proxies)
        if (ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        if (ip.startsWith("172.")) {
            String[] parts = ip.split("\\.");
            if (parts.length > 1) {
                try {
                    int second = Integer.parseInt(parts[1]);
                    return second >= 16 && second <= 31;
                } catch (NumberFormatException ignored) {
                    return false;
                }
            }
        }
        return false;
    }
}
