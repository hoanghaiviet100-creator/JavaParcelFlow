package com.parcelflow.auth.service;

import com.parcelflow.domain.User;
import com.parcelflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Persists a permanent account lock in a transaction of its own.
 *
 * <p>Why this is a separate bean: {@code AuthService.login} and
 * {@code changePassword} are {@code @Transactional}, and both mark the account
 * locked and then throw {@link com.parcelflow.common.error.ApiException} to
 * report it. ApiException is a RuntimeException, so Spring's default rollback
 * rule discarded the very UPDATE that did the locking. The caller received
 * AUTH_ACCOUNT_PERMANENTLY_LOCKED while {@code users.is_active} stayed 1 and
 * {@code lock_reason} stayed NULL.
 *
 * <p>That was worse than a cosmetic inconsistency. LoginAttemptService.clear()
 * runs just before the PERMANENT verdict is returned, wiping the failure
 * counter, the temporary lock and the escalation flag — so with nothing written
 * to the database either, the next request found a completely clean slate. An
 * attacker got a fresh batch of attempts every time they tripped the "permanent"
 * lock, indefinitely.
 *
 * <p>{@code REQUIRES_NEW} suspends the caller's transaction and commits this
 * write on its own connection, so the caller's rollback cannot undo it. It has
 * to live in another bean: a self-invoked method never passes through the proxy
 * that applies the annotation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountLockService {

    private final UserRepository userRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void lockPermanently(Long userId, String reason) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return;
        }
        user.setIsActive(false);
        user.setLockReason(reason);
        user.setLockedAt(LocalDateTime.now());
        userRepository.save(user);
        log.warn("Account {} permanently locked: {}", user.getEmail(), reason);
    }
}
