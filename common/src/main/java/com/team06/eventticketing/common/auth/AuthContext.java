package com.team06.eventticketing.common.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthContext {

    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final String requiredRole;
    private String token;
    private Claims claims;
    private SecurityUserRecord user;

    public AuthContext(HttpServletRequest request, HttpServletResponse response, String requiredRole) {
        this.request = request;
        this.response = response;
        this.requiredRole = requiredRole;
    }

    public HttpServletRequest getRequest() {
        return request;
    }

    public HttpServletResponse getResponse() {
        return response;
    }

    public String getRequiredRole() {
        return requiredRole;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Claims getClaims() {
        return claims;
    }

    public void setClaims(Claims claims) {
        this.claims = claims;
    }

    public SecurityUserRecord getUser() {
        return user;
    }

    public void setUser(SecurityUserRecord user) {
        this.user = user;
    }
}
