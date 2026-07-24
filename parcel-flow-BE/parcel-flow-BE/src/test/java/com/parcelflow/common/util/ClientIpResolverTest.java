package com.parcelflow.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * X-Forwarded-For is attacker-controlled. These pin down when it is believed.
 *
 * <p>The regression being guarded: the resolver used to trust loopback plus every
 * RFC-1918 range unconditionally. In a container deployment the peer is always
 * the bridge gateway inside 172.16/12, so the header was trusted on every
 * request and the per-IP throttle could be defeated by varying it.
 */
class ClientIpResolverTest {

    private MockHttpServletRequest request(String peer, String forwarded) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr(peer);
        if (forwarded != null) {
            req.addHeader("X-Forwarded-For", forwarded);
        }
        return req;
    }

    @Test
    void ignoresForwardedHeader_whenNoProxyIsConfigured() {
        ClientIpResolver resolver = new ClientIpResolver("");
        assertThat(resolver.getClientIp(request("172.18.0.1", "203.0.113.9")))
                .isEqualTo("172.18.0.1");
        assertThat(resolver.getClientIp(request("127.0.0.1", "203.0.113.9")))
                .isEqualTo("127.0.0.1");
        assertThat(resolver.getClientIp(request("10.0.0.5", "203.0.113.9")))
                .isEqualTo("10.0.0.5");
    }

    @Test
    void dockerBridgeGateway_isNotTrustedByDefault() {
        ClientIpResolver resolver = new ClientIpResolver("");
        // Fifty invented client IPs must all collapse onto the one real peer,
        // so they share a single rate-limit bucket.
        for (int i = 1; i <= 50; i++) {
            assertThat(resolver.getClientIp(request("172.17.0.1", "203.0.113." + i)))
                    .isEqualTo("172.17.0.1");
        }
    }

    @Test
    void honoursForwardedHeader_fromAConfiguredProxy() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.getClientIp(request("10.1.2.3", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
        // Left-most entry is the original client.
        assertThat(resolver.getClientIp(request("10.1.2.3", "203.0.113.9, 10.1.2.3")))
                .isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresForwardedHeader_fromAPeerOutsideTheConfiguredRange() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.getClientIp(request("192.168.1.7", "203.0.113.9")))
                .isEqualTo("192.168.1.7");
        assertThat(resolver.getClientIp(request("11.0.0.1", "203.0.113.9")))
                .isEqualTo("11.0.0.1");
    }

    @Test
    void supportsBareAddressesAndMultipleRanges() {
        ClientIpResolver resolver = new ClientIpResolver("192.168.1.5, 172.16.0.0/12");
        assertThat(resolver.getClientIp(request("192.168.1.5", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.getClientIp(request("192.168.1.6", "203.0.113.9")))
                .isEqualTo("192.168.1.6");
        assertThat(resolver.getClientIp(request("172.20.0.1", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
        // 172.32 is outside 172.16/12 — the classic off-by-one on that block.
        assertThat(resolver.getClientIp(request("172.32.0.1", "203.0.113.9")))
                .isEqualTo("172.32.0.1");
    }

    @Test
    void fallsBackToPeer_whenHeaderIsAbsentOrEmpty() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.getClientIp(request("10.1.2.3", null))).isEqualTo("10.1.2.3");
        assertThat(resolver.getClientIp(request("10.1.2.3", "   "))).isEqualTo("10.1.2.3");
    }

    @Test
    void malformedConfigurationIsIgnored_ratherThanTrustingEverything() {
        ClientIpResolver resolver = new ClientIpResolver("not-an-ip, 10.0.0.0/99, 10.0.0.0/8");
        assertThat(resolver.getClientIp(request("10.1.2.3", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
        assertThat(resolver.getClientIp(request("192.168.1.1", "203.0.113.9")))
                .isEqualTo("192.168.1.1");
    }

    @Test
    void ipv6PeerDoesNotMatchAnIpv4Range() {
        ClientIpResolver resolver = new ClientIpResolver("10.0.0.0/8");
        assertThat(resolver.getClientIp(request("::1", "203.0.113.9"))).isEqualTo("::1");
    }
}
