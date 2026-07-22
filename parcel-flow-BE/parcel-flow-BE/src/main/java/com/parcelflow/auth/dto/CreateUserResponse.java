package com.parcelflow.auth.dto;

public record CreateUserResponse(
        Long id,
        String email,
        String fullName,
        String roleCode,
        Long hubId,
        boolean mustChangePassword) {
}
