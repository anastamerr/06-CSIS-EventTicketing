package com.team06.eventticketing.user.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.AntPathMatcher;

public final class JwtAuthSupport {

    private JwtAuthSupport() {
    }

    public interface AuthHandler {
        AuthHandler setNext(AuthHandler next);

        void handle(AuthContext context);
    }

    public static final class AuthContext {
        private final HttpServletRequest request;
        private String token;
        private Claims claims;
        private Long uid;
        private String role;
        private Authentication authentication;

        public AuthContext(HttpServletRequest request) {
            this.request = request;
        }

        public HttpServletRequest getRequest() {
            return request;
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

        public Long getUid() {
            return uid;
        }

        public void setUid(Long uid) {
            this.uid = uid;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Authentication getAuthentication() {
            return authentication;
        }

        public void setAuthentication(Authentication authentication) {
            this.authentication = authentication;
        }
    }

    public static final class AuthException extends RuntimeException {
        private final int status;

        public AuthException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    public static final class TokenExtractionHandler implements AuthHandler {
        private AuthHandler next;

        @Override
        public AuthHandler setNext(AuthHandler next) {
            this.next = next;
            return next;
        }

        @Override
        public void handle(AuthContext context) {
            String header = context.getRequest().getHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "Missing or invalid Authorization header");
            }
            context.setToken(header.substring(7).trim());
            if (next != null) {
                next.handle(context);
            }
        }
    }

    public static final class SignatureValidationHandler implements AuthHandler {
        private AuthHandler next;
        private final JwtService jwtService;

        public SignatureValidationHandler(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public AuthHandler setNext(AuthHandler next) {
            this.next = next;
            return next;
        }

        @Override
        public void handle(AuthContext context) {
            try {
                Claims claims = jwtService.validateToken(context.getToken());
                context.setClaims(claims);
                Object uidValue = claims.get("uid");
                Object roleValue = claims.get("role");
                if (uidValue == null || roleValue == null) {
                    throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "Token missing required claims");
                }
                context.setUid(Long.parseLong(uidValue.toString()));
                context.setRole(roleValue.toString());
            } catch (Exception ex) {
                throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "Invalid or expired token");
            }
            if (next != null) {
                next.handle(context);
            }
        }
    }

    public static final class UserLoaderHandler implements AuthHandler {
        private AuthHandler next;
        private final JdbcTemplate jdbcTemplate;

        public UserLoaderHandler(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public AuthHandler setNext(AuthHandler next) {
            this.next = next;
            return next;
        }

        @Override
        public void handle(AuthContext context) {
            Long uid = context.getUid();
            if (uid == null) {
                throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "Token missing uid claim");
            }
            try {
                Map<String, Object> row = jdbcTemplate.queryForMap(
                        "SELECT id, email, role FROM users WHERE id = ?",
                        uid);
                if (row == null || row.isEmpty()) {
                    throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "User not found");
                }
                String role = row.get("role").toString();
                String email = row.get("email").toString();
                context.setRole(role);
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        email,
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_" + role)));
                context.setAuthentication(auth);
            } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
                throw new AuthException(HttpStatus.UNAUTHORIZED.value(), "User not found");
            }
            if (next != null) {
                next.handle(context);
            }
        }
    }

    public static final class RoleAuthorizationHandler implements AuthHandler {
        private AuthHandler next;
        private final AntPathMatcher pathMatcher = new AntPathMatcher();

        @Override
        public AuthHandler setNext(AuthHandler next) {
            this.next = next;
            return next;
        }

        @Override
        public void handle(AuthContext context) {
            String path = context.getRequest().getRequestURI();
            String method = context.getRequest().getMethod();
            String role = context.getRole();
            if ("PUT".equalsIgnoreCase(method) && pathMatcher.match("/api/users/*/role", path)) {
                if (!"ADMIN".equals(role)) {
                    throw new AuthException(HttpStatus.FORBIDDEN.value(), "Insufficient role");
                }
            }
            if (next != null) {
                next.handle(context);
            }
        }
    }
}
