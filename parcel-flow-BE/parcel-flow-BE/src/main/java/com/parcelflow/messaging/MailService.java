package com.parcelflow.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Actual SMTP delivery. Only the Kafka consumer calls this — never a controller/service
 * on the request path (Phase 4).
 *
 * <p>If {@code app.mail.enabled=false} (local dev without a real SMTP server), it logs a
 * one-line notice instead of sending, and NEVER logs the body (which may contain a temp
 * password, C-4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.enabled:true}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    public void send(String to, String subject, String text) {
        if (!enabled) {
            log.info("[mail disabled] would send email to={} subject=\"{}\"", to, subject);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("Email sent to={} subject=\"{}\"", to, subject);
    }
}
