package com.parcelflow.config;

import com.parcelflow.domain.Role;
import com.parcelflow.domain.User;
import com.parcelflow.repository.RoleRepository;
import com.parcelflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Dev convenience: seed an initial ADMIN so the protected user-creation endpoints are usable.
 * Disable in real environments via app.bootstrap-admin.enabled=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap-admin.enabled}")
    private boolean enabled;

    @Value("${app.bootstrap-admin.email}")
    private String adminEmail;

    @Value("${app.bootstrap-admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        if (!enabled) {
            return;
        }
        Role adminRole = roleRepository.findByCode("ADMIN").orElse(null);
        if (adminRole == null) {
            log.warn("ADMIN role not found; skipping admin bootstrap.");
            return;
        }
        String email = adminEmail.trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }
        User admin = User.builder()
                .fullName("System Administrator")
                .email(email)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .roleId(adminRole.getId())
                .isActive(true)
                .mustChangePassword(false)
                .build();
        userRepository.save(admin);
        log.warn("Bootstrap ADMIN created: {} (dev password from config). Change it in production.", email);
    }
}
