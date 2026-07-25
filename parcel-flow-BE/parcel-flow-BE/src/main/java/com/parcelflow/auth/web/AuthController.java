package com.parcelflow.auth.web;

import com.parcelflow.auth.dto.ChangePasswordRequest;
import com.parcelflow.auth.dto.LoginRequest;
import com.parcelflow.auth.dto.LoginResponse;
import com.parcelflow.auth.dto.RefreshTokenRequest;
import com.parcelflow.auth.service.AuthService;
import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.common.util.ClientIpResolver;
import com.parcelflow.security.AuthPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                            HttpServletRequest httpRequest) {
        String ip = clientIpResolver.getClientIp(httpRequest);
        LoginResponse response = authService.login(request, ip);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponse>> refresh(@Valid @RequestBody RefreshTokenRequest request,
                                                             HttpServletRequest httpRequest) {
        String ip = clientIpResolver.getClientIp(httpRequest);
        LoginResponse response = authService.refresh(request, ip);
        return ResponseEntity.ok(ApiResponse.success(response, "Token refreshed"));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Password changed successfully"));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        if (principal != null) {
            authService.logout(principal.userId());
        }
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Logged out"));
    }
}
