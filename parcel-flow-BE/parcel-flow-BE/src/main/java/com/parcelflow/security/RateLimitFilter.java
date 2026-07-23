package com.parcelflow.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.util.HttpUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Per-IP throttle on the two unauthenticated abuse surfaces.
 *
 * <p>Login and public tracking are reachable without a token, so nothing else
 * bounds how fast one source can hammer them. Login guessing has the per-account
 * lockout, but that does not stop credential-stuffing spread thin across many
 * accounts, and public tracking had no limit at all — order codes could be
 * enumerated as fast as the network allowed. This is the coarse volume backstop
 * for both.
 *
 * <p>Ordered ahead of the JWT filter (which runs inside the Spring Security
 * chain) so an over-limit request is turned away before any real work. Only the
 * exact paths that need it are checked; everything else passes straight through.
 *
 * <p>Limits are env-tunable. Defaults are generous enough for a human and for
 * the project's own test bursts, but a tight enumeration loop trips them; lower
 * them in production via RATE_LIMIT_* to taste.
 */
@Slf4j
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private final int loginLimit;
    private final int loginWindow;
    private final int trackingLimit;
    private final int trackingWindow;

    public RateLimitFilter(RateLimitService rateLimitService,
                           ObjectMapper objectMapper,
                           @Value("${app.rate-limit.login.max-requests:30}") int loginLimit,
                           @Value("${app.rate-limit.login.window-seconds:60}") int loginWindow,
                           @Value("${app.rate-limit.tracking.max-requests:60}") int trackingLimit,
                           @Value("${app.rate-limit.tracking.window-seconds:60}") int trackingWindow) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
        this.loginLimit = loginLimit;
        this.loginWindow = loginWindow;
        this.trackingLimit = trackingLimit;
        this.trackingWindow = trackingWindow;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String bucket = bucketFor(request);
        if (bucket == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int limit = bucket.equals("login") ? loginLimit : trackingLimit;
        int window = bucket.equals("login") ? loginWindow : trackingWindow;
        String ip = HttpUtils.getClientIp(request);

        if (!rateLimitService.tryAcquire(bucket, ip, limit, window)) {
            log.warn("Rate limit exceeded on {} from {}", bucket, ip);
            writeTooManyRequests(response, request.getRequestURI(), window);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** The bucket a request belongs to, or null when it is not rate-limited. */
    private String bucketFor(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.equals("/api/v1/auth/login")) {
            return "login";
        }
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/tracking/")) {
            return "tracking";
        }
        return null;
    }

    private void writeTooManyRequests(HttpServletResponse response, String path, int window)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Retry-After", String.valueOf(window));
        ApiResponse<Void> body = ApiResponse.failure(
                "RATE_LIMITED",
                "Too many requests. Please slow down and try again shortly.",
                List.of(),
                path);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
