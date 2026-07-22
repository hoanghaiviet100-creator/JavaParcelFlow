package com.parcelflow.auth.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class PasswordPolicy {

    public List<String> violations(String password) {
        List<String> v = new ArrayList<>();
        if (password == null || password.length() < 8) {
            v.add("Password must be at least 8 characters");
        }
        if (password == null || !password.matches(".*[A-Z].*")) {
            v.add("Password must contain an uppercase letter");
        }
        if (password == null || !password.matches(".*[a-z].*")) {
            v.add("Password must contain a lowercase letter");
        }
        if (password == null || !password.matches(".*\\d.*")) {
            v.add("Password must contain a digit");
        }
        if (password == null || !password.matches(".*[^A-Za-z0-9].*")) {
            v.add("Password must contain a special character");
        }
        return v;
    }
}
