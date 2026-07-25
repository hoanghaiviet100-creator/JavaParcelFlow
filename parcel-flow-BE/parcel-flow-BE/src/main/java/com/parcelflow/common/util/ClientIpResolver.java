package com.parcelflow.common.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the client IP that the throttle and the single-session policy key on.
 *
 * <p>{@code X-Forwarded-For} is written by the client. Honouring it turns both
 * controls into no-ops: vary the header per request and every request lands in
 * its own rate-limit bucket, and the "one active session per IP" check compares
 * a value the caller chose. So it is only honoured when the immediate peer is a
 * proxy the operator has explicitly named.
 *
 * <p>This replaced a hard-coded allowlist of loopback plus every RFC-1918 range.
 * That looked like a restriction but was not one in any container deployment:
 * traffic reaching the app arrives from the bridge gateway, which is always
 * inside 172.16/12, so the allowlist matched the peer every time and the header
 * was always trusted. Fifty logins carrying fifty invented {@code X-Forwarded-For}
 * values produced fifty separate buckets and zero throttling.
 *
 * <p>The default is now empty — trust nothing, use the peer address. Deployments
 * that genuinely sit behind a load balancer set {@code app.trusted-proxies} to
 * that balancer's address or CIDR, and must ensure it OVERWRITES the header
 * rather than appending to it.
 */
@Slf4j
@Component
public class ClientIpResolver {

    private final List<CidrRange> trustedProxies;

    public ClientIpResolver(@Value("${app.trusted-proxies:}") String configured) {
        this.trustedProxies = parse(configured);
        if (trustedProxies.isEmpty()) {
            log.info("No trusted proxies configured; X-Forwarded-For will be ignored "
                    + "and the peer address used directly.");
        } else {
            log.info("Trusting X-Forwarded-For from {} proxy range(s).", trustedProxies.size());
        }
    }

    public String getClientIp(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (!isTrustedProxy(peer)) {
            return peer;
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return peer;
        }
        // Left-most entry is the original client; a conforming proxy appends on the right.
        String candidate = forwarded.split(",")[0].trim();
        return candidate.isEmpty() ? peer : candidate;
    }

    private boolean isTrustedProxy(String ip) {
        if (ip == null || trustedProxies.isEmpty()) {
            return false;
        }
        byte[] address = toBytes(ip);
        if (address == null) {
            return false;
        }
        return trustedProxies.stream().anyMatch(range -> range.contains(address));
    }

    private static List<CidrRange> parse(String configured) {
        List<CidrRange> ranges = new ArrayList<>();
        if (configured == null || configured.isBlank()) {
            return ranges;
        }
        for (String entry : configured.split(",")) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            CidrRange range = CidrRange.parse(trimmed);
            if (range == null) {
                log.warn("Ignoring unparseable trusted-proxy entry: {}", trimmed);
            } else {
                ranges.add(range);
            }
        }
        return ranges;
    }

    private static byte[] toBytes(String ip) {
        try {
            // Literal addresses only — getByName would otherwise perform a DNS lookup
            // on whatever string the peer field happened to contain.
            return InetAddress.getByName(stripZone(ip)).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /** IPv6 link-local addresses may carry a "%eth0" scope suffix that the parser rejects. */
    private static String stripZone(String ip) {
        int pct = ip.indexOf('%');
        return pct < 0 ? ip : ip.substring(0, pct);
    }

    /** A single CIDR block; a bare address is treated as a /32 (or /128). */
    private record CidrRange(byte[] network, int prefixBits) {

        static CidrRange parse(String entry) {
            String[] parts = entry.split("/", 2);
            byte[] network = toBytes(parts[0].trim());
            if (network == null) {
                return null;
            }
            int maxBits = network.length * 8;
            if (parts.length == 1) {
                return new CidrRange(network, maxBits);
            }
            try {
                int bits = Integer.parseInt(parts[1].trim());
                if (bits < 0 || bits > maxBits) {
                    return null;
                }
                return new CidrRange(network, bits);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        boolean contains(byte[] candidate) {
            // An IPv4 range never contains an IPv6 address, and vice versa.
            if (candidate.length != network.length) {
                return false;
            }
            int fullBytes = prefixBits / 8;
            for (int i = 0; i < fullBytes; i++) {
                if (candidate[i] != network[i]) {
                    return false;
                }
            }
            int remainingBits = prefixBits % 8;
            if (remainingBits == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainingBits);
            return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
        }
    }
}
