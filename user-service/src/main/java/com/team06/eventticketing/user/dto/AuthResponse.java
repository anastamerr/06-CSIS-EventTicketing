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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String token;
        private Long userId;
        private String email;
        private String role;
        private User user;

        public Builder token(String token) { this.token = token; return this; }
        public Builder userId(Long userId) { this.userId = userId; return this; }
        public Builder email(String email) { this.email = email; return this; }
        public Builder role(String role) { this.role = role; return this; }
        public Builder user(User user) { this.user = user; return this; }

        public AuthResponse build() {
            return new AuthResponse(token, userId, email, role, user);
        }
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
