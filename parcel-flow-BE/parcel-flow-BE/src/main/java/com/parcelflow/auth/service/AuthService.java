package com.parcelflow.auth.service;

import com.parcelflow.auth.dto.*;
import com.parcelflow.common.enums.VehicleType;
import com.parcelflow.common.error.ApiException;
import com.parcelflow.common.error.ErrorCode;
import com.parcelflow.domain.Role;
import com.parcelflow.domain.ShipperProfile;
import com.parcelflow.domain.User;
import com.parcelflow.messaging.EmailEvent;
import com.parcelflow.messaging.EmailEventPublisher;
import com.parcelflow.repository.HubRepository;
import com.parcelflow.repository.RoleRepository;
import com.parcelflow.repository.ShipperProfileRepository;
import com.parcelflow.repository.UserRepository;
import com.parcelflow.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String ROLE_SHIPPER = "SHIPPER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final HubRepository hubRepository;
    private final ShipperProfileRepository shipperProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordPolicy passwordPolicy;
    private final TempPasswordGenerator tempPasswordGenerator;
    private final EmailEventPublisher emailEventPublisher;
    private final AccountLockService accountLockService;

    @Value("${app.temp-password-ttl-seconds}")
    private long tempPasswordTtlSeconds;

    @Transactional
    public CreateUserResponse createAccount(CreateUserRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("Email already registered: " + email);
        }
        if (req.getPhone() != null && !req.getPhone().isBlank()
                && userRepository.existsByPhone(req.getPhone())) {
            throw ApiException.conflict("Phone already registered: " + req.getPhone());
        }

        Role role = roleRepository.findByCode(req.getRoleCode())
                .orElseThrow(() -> ApiException.validation("Unknown role: " + req.getRoleCode()));

        Long hubId = req.getHubId();
        boolean shipper = ROLE_SHIPPER.equals(role.getCode());
        if (shipper && hubId == null) {
            throw ApiException.validation("hubId is required when role is SHIPPER");
        }
        if (hubId != null && !hubRepository.existsById(hubId)) {
            throw ApiException.validation("Unknown hub: " + hubId);
        }

        String tempPassword = tempPasswordGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(tempPasswordTtlSeconds);

        User user = User.builder()
                .fullName(req.getFullName())
                .email(email)
                .phone(req.getPhone())
                .passwordHash(passwordEncoder.encode(tempPassword))
                .roleId(role.getId())
                .hubId(hubId)
                .isActive(true)
                .mustChangePassword(true)
                .passwordExpiresAt(expiresAt)
                .build();
        user = userRepository.save(user);

        if (shipper) {
            ShipperProfile profile = ShipperProfile.builder()
                    .userId(user.getId())
                    .hubId(hubId)
                    .vehicleType(VehicleType.MOTORBIKE)
                    .maxOrdersPerDay(30)
                    .isAvailable(true)
                    .build();
            shipperProfileRepository.save(profile);
        }

        emailEventPublisher.publish(new EmailEvent(
                email,
                "Your Parcel Flow account",
                tempPasswordBody(req.getFullName(), tempPassword, expiresAt)));

        return new CreateUserResponse(user.getId(), email, user.getFullName(),
                role.getCode(), hubId, true);
    }

    @Transactional
    public LoginResponse login(LoginRequest req, String ip) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                        "Invalid email or password"));

        // Hard blocks that are safe to surface (a permanently locked or temp-locked
        // account is a legitimate 423, not an enumeration oracle: it only reveals state
        // AFTER the account already exists, which the temp-lock/permanent-lock UX requires).
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_PERMANENTLY_LOCKED,
                    "Account is permanently locked. Contact an administrator.");
        }
        if (loginAttemptService.isTemporarilyLocked(email)) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "Account is temporarily locked. Try again later.");
        }

        // C-2: verify the password BEFORE revealing must-change-password state, so an
        // attacker who does not know the password cannot probe whether an account is in
        // the temp-password state.
        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            AttemptResult result = loginAttemptService.onFailedLogin(email);
            if (result == AttemptResult.PERMANENT) {
                lockPermanently(user, "TOO_MANY_FAILED_ATTEMPTS");
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_PERMANENTLY_LOCKED,
                        "Account permanently locked after repeated failed attempts.");
            }
            if (result == AttemptResult.TEMP_LOCKED) {
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                        "Too many failed attempts. Account locked temporarily.");
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Invalid email or password");
        }

        // Password is correct. Now enforce the temp-password change gate.
        if (Boolean.TRUE.equals(user.getMustChangePassword())) {
            loginAttemptService.onSuccessfulLogin(email); // credentials were valid; clear counters
            if (isTempExpired(user)) {
                throw new ApiException(ErrorCode.AUTH_TEMP_PASSWORD_EXPIRED,
                        "Temporary password has expired. Ask an administrator to resend it.");
            }
            throw new ApiException(ErrorCode.AUTH_PASSWORD_CHANGE_REQUIRED,
                    "You must change your password before the first login.");
        }

        loginAttemptService.onSuccessfulLogin(email);

        String role = resolveRoleCode(user);
        String accessJti = UUID.randomUUID().toString();
        String refreshId = UUID.randomUUID().toString();
        sessionService.handleLogin(user.getId(), ip, accessJti, refreshId);

        String access = jwtService.generateAccessToken(user.getId(), user.getEmail(), role, accessJti);
        String refresh = jwtService.generateRefreshToken(user.getId(), refreshId);
        return new LoginResponse(access, refresh, "Bearer", jwtService.getAccessTtlSeconds(), role);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS,
                        "Invalid email or password"));

        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_PERMANENTLY_LOCKED,
                    "Account is permanently locked.");
        }
        // C-1: apply the SAME brute-force protection as /login, otherwise this endpoint
        // is an unauthenticated password-guessing oracle that bypasses the lockout entirely.
        if (loginAttemptService.isTemporarilyLocked(email)) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                    "Account is temporarily locked. Try again later.");
        }
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPasswordHash())) {
            AttemptResult result = loginAttemptService.onFailedLogin(email);
            if (result == AttemptResult.PERMANENT) {
                lockPermanently(user, "TOO_MANY_FAILED_ATTEMPTS");
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_PERMANENTLY_LOCKED,
                        "Account permanently locked after repeated failed attempts.");
            }
            if (result == AttemptResult.TEMP_LOCKED) {
                throw new ApiException(ErrorCode.AUTH_ACCOUNT_LOCKED,
                        "Too many failed attempts. Account locked temporarily.");
            }
            throw new ApiException(ErrorCode.AUTH_INVALID_CREDENTIALS, "Current password is incorrect");
        }
        if (Boolean.TRUE.equals(user.getMustChangePassword()) && isTempExpired(user)) {
            throw new ApiException(ErrorCode.AUTH_TEMP_PASSWORD_EXPIRED,
                    "Temporary password has expired. Ask an administrator to resend it.");
        }

        List<String> violations = passwordPolicy.violations(req.getNewPassword());
        if (!violations.isEmpty()) {
            throw new ApiException(ErrorCode.AUTH_PASSWORD_POLICY,
                    "Password does not meet the policy", violations);
        }

        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        user.setMustChangePassword(false);
        user.setPasswordExpiresAt(null);
        userRepository.save(user);
        loginAttemptService.onSuccessfulLogin(email);
    }

    @Transactional
    public LoginResponse refresh(RefreshTokenRequest req, String ip) {
        Claims claims;
        try {
            claims = jwtService.parse(req.getRefreshToken());
        } catch (Exception e) {
            throw new ApiException(ErrorCode.AUTH_INVALID_TOKEN, "Invalid refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new ApiException(ErrorCode.AUTH_INVALID_TOKEN, "Not a refresh token");
        }
        Long userId = jwtService.getUserId(claims);
        String providedRefreshId = jwtService.getJti(claims);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INVALID_TOKEN, "Unknown user"));
        if (Boolean.FALSE.equals(user.getIsActive())) {
            throw new ApiException(ErrorCode.AUTH_ACCOUNT_PERMANENTLY_LOCKED, "Account is permanently locked.");
        }

        String newAccessJti = UUID.randomUUID().toString();
        String newRefreshId = UUID.randomUUID().toString();
        sessionService.rotate(userId, ip, providedRefreshId, newAccessJti, newRefreshId);

        String role = resolveRoleCode(user);
        String access = jwtService.generateAccessToken(userId, user.getEmail(), role, newAccessJti);
        String refresh = jwtService.generateRefreshToken(userId, newRefreshId);
        return new LoginResponse(access, refresh, "Bearer", jwtService.getAccessTtlSeconds(), role);
    }

    @Transactional
    public void resendTemporaryPassword(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + userId));

        String tempPassword = tempPasswordGenerator.generate();
        LocalDateTime expiresAt = LocalDateTime.now().plusSeconds(tempPasswordTtlSeconds);
        user.setPasswordHash(passwordEncoder.encode(tempPassword));
        user.setMustChangePassword(true);
        user.setPasswordExpiresAt(expiresAt);
        userRepository.save(user);

        loginAttemptService.clear(user.getEmail());
        emailEventPublisher.publish(new EmailEvent(
                user.getEmail(),
                "Your new temporary password",
                tempPasswordBody(user.getFullName(), tempPassword, expiresAt)));
    }

    /** Logout: revoke the user's active session so all issued tokens stop working. */
    public void logout(Long userId) {
        sessionService.invalidate(userId);
    }

    @Transactional
    public void unlockAccount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found: " + userId));
        user.setIsActive(true);
        user.setLockReason(null);
        user.setLockedAt(null);
        userRepository.save(user);
        loginAttemptService.clear(user.getEmail());
        sessionService.invalidate(userId);
    }

    /**
     * Persist permanent-lock status + metadata to the DB.
     *
     * <p>Delegated to {@link AccountLockService}, which commits in a transaction
     * of its own. Writing it here meant the ApiException thrown immediately
     * afterwards rolled the lock straight back — see the note on that class.
     */
    private void lockPermanently(User user, String reason) {
        accountLockService.lockPermanently(user.getId(), reason);
    }

    private String resolveRoleCode(User user) {
        return roleRepository.findById(user.getRoleId())
                .map(Role::getCode)
                .orElse("UNKNOWN");
    }

    private boolean isTempExpired(User user) {
        return user.getPasswordExpiresAt() != null
                && user.getPasswordExpiresAt().isBefore(LocalDateTime.now());
    }

    private String tempPasswordBody(String fullName, String tempPassword, LocalDateTime expiresAt) {
        return "Hello " + fullName + ",\n\n"
                + "An account has been created for you on Parcel Flow.\n"
                + "Temporary password: " + tempPassword + "\n"
                + "It expires at: " + expiresAt + "\n\n"
                + "You must change this password before your first login.";
    }
}
