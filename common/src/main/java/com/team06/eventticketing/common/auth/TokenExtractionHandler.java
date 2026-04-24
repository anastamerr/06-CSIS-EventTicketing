package com.team06.eventticketing.common.auth;

import java.io.IOException;

public class TokenExtractionHandler extends AuthHandler {

    @Override
    protected boolean process(AuthContext context) throws IOException {
        String header = context.getRequest().getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            context.getResponse().sendError(401, "Missing or invalid Authorization header");
            return false;
        }
        context.setToken(header.substring(7).trim());
        if (context.getToken().isBlank()) {
            context.getResponse().sendError(401, "Missing token");
            return false;
        }
        return true;
    }
}
