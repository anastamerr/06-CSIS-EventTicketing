package com.team06.eventticketing.common.auth;

import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final SecurityUserLookupService userLookupService;

    public JwtAuthenticationFilter(JwtService jwtService, SecurityUserLookupService userLookupService) {
        this.jwtService = jwtService;
        this.userLookupService = userLookupService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.endsWith("/health")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        AuthContext context = new AuthContext(request, response, resolveRequiredRole(request));
        AuthHandler head = new TokenExtractionHandler();
        head.setNext(new SignatureValidationHandler(jwtService))
                .setNext(new UserLoaderHandler(jwtService, userLookupService))
                .setNext(new RoleAuthorizationHandler());

        if (!head.handle(context)) {
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                context.getUser().email(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + context.getUser().role()))));
        filterChain.doFilter(request, response);
    }

    private String resolveRequiredRole(HttpServletRequest request) {
        String path = request.getRequestURI();
        if ("PUT".equalsIgnoreCase(request.getMethod()) && path.matches(".*/api/users/\\d+/role/?$")) {
            return "ADMIN";
        }
        if ("POST".equalsIgnoreCase(request.getMethod()) && path.matches(".*/api/events/\\d+/index/?$")) {
            return "USER";
        }
        return null;
    }
}
