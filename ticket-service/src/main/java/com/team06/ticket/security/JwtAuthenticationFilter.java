package com.team06.ticket.security;

import com.team06.ticket.security.JwtAuthSupport.AuthContext;
import com.team06.ticket.security.JwtAuthSupport.AuthException;
import com.team06.ticket.security.JwtAuthSupport.AuthHandler;
import com.team06.ticket.security.JwtAuthSupport.RoleAuthorizationHandler;
import com.team06.ticket.security.JwtAuthSupport.SignatureValidationHandler;
import com.team06.ticket.security.JwtAuthSupport.TokenExtractionHandler;
import com.team06.ticket.security.JwtAuthSupport.UserLoaderHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final AuthHandler handlerChain;
    private final JwtService jwtService;
    private final JdbcTemplate jdbcTemplate;

    public JwtAuthenticationFilter(JwtService jwtService, JdbcTemplate jdbcTemplate) {
        this.jwtService = jwtService;
        this.jdbcTemplate = jdbcTemplate;
        this.handlerChain = buildChain();
    }

    private AuthHandler buildChain() {
        TokenExtractionHandler extraction = new TokenExtractionHandler();
        SignatureValidationHandler validation = new SignatureValidationHandler(jwtService);
        UserLoaderHandler loader = new UserLoaderHandler(jdbcTemplate);
        RoleAuthorizationHandler authorization = new RoleAuthorizationHandler();

        extraction.setNext(validation).setNext(loader).setNext(authorization);
        return extraction;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/")
                || path.matches("/api/.*/health")
                || path.equals("/actuator/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        AuthContext context = new AuthContext(request);
        try {
            handlerChain.handle(context);
            Authentication authentication = context.getAuthentication();
            if (authentication != null) {
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(request, response);
        } catch (AuthException ex) {
            response.setStatus(ex.getStatus());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"" + ex.getMessage() + "\"}");
        }
    }
}
