package com.parcelflow.security;

public record AuthPrincipal(Long userId, String email, String role) {
}
