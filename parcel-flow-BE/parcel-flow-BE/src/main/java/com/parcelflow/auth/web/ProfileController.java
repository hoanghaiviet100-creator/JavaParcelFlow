package com.parcelflow.auth.web;

import com.parcelflow.auth.dto.LoginResponse; // Hoặc DTO chứa profile của bạn
import com.parcelflow.common.api.ApiResponse;
import com.parcelflow.security.AuthPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth") // Đặt đúng prefix này
@RequiredArgsConstructor
public class ProfileController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Object>> getCurrentUser(@AuthenticationPrincipal AuthPrincipal principal) {
        // Trả về thông tin user từ principal
        return ResponseEntity.ok(ApiResponse.success(principal, "Profile retrieved successfully"));
    }
}