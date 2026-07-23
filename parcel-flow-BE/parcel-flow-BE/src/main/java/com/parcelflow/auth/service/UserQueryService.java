package com.parcelflow.auth.service;

import com.parcelflow.auth.dto.UserResponse;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.domain.Role;
import com.parcelflow.domain.User;
import com.parcelflow.repository.RoleRepository;
import com.parcelflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Read side of account administration.
 *
 * <p>Kept apart from {@link AuthService}, which owns the write side (creating
 * accounts, rotating temporary passwords, locking and unlocking). Nothing here
 * mutates state, so every method is a read-only transaction.
 */
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);

        // Resolve every role once for the page rather than per row: users.role_id
        // is a plain column, not a JPA association, so mapping row by row would
        // issue one query per user.
        Map<Long, String> roleCodes = roleCodesFor(users.getContent());

        return users.map(u -> toResponse(u, roleCodes.get(u.getRoleId())));
    }

    @Transactional(readOnly = true)
    public UserResponse getById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("User not found: " + id));
        String roleCode = roleRepository.findById(user.getRoleId())
                .map(Role::getCode)
                .orElse(null);
        return toResponse(user, roleCode);
    }

    private Map<Long, String> roleCodesFor(List<User> users) {
        List<Long> roleIds = users.stream().map(User::getRoleId).distinct().toList();
        return roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Role::getCode, (a, b) -> a));
    }

    private UserResponse toResponse(User u, String roleCode) {
        return new UserResponse(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getPhone(),
                roleCode,
                u.getHubId(),
                Boolean.TRUE.equals(u.getIsActive()),
                Boolean.TRUE.equals(u.getMustChangePassword()),
                u.getPasswordExpiresAt(),
                u.getLockReason(),
                u.getLockedAt(),
                u.getCreatedAt());
    }
}
