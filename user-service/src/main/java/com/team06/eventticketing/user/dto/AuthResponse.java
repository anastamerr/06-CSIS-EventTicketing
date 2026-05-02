package com.team06.eventticketing.user.dto;

import com.team06.eventticketing.common.auth.JwtConfigurationManager;
import com.team06.eventticketing.user.model.User;

public class AuthResponse {

    private final String token;
    private final long expiresIn;
    private final Long userId;
    private final String email;
    private final String role;
    private final User user;

    public AuthResponse(String token, Long userId, String email, String role, User user) {
        this.token = token;
        this.expiresIn = JwtConfigurationManager.getInstance().getExpirationMs();
        this.userId = userId;
        this.email = email;
        this.role = role;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getRole() {
        return role;
    }

    public User getUser() {
        return user;
    }
}
