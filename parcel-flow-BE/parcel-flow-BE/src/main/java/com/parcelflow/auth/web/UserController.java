package com.parcelflow.auth.web;

import com.parcelflow.auth.dto.CreateUserRequest;
import com.parcelflow.auth.dto.CreateUserResponse;
import com.parcelflow.auth.service.AuthService;
import com.parcelflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final AuthService authService;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateUserResponse>> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse response = authService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created. Temporary password sent by email."));
    }

    @PostMapping("/{id}/resend-temp-password")
    public ResponseEntity<ApiResponse<Void>> resendTempPassword(@PathVariable Long id) {
        authService.resendTemporaryPassword(id);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Temporary password resent"));
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlock(@PathVariable Long id) {
        authService.unlockAccount(id);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, "Account unlocked"));
    }
}
