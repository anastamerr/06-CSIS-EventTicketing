package com.team06.eventticketing.common.auth;

import io.jsonwebtoken.JwtException;
import java.io.IOException;

public class SignatureValidationHandler extends AuthHandler {

    private final JwtService jwtService;

    public SignatureValidationHandler(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected boolean process(AuthContext context) throws IOException {
        try {
            context.setClaims(jwtService.parseClaims(context.getToken()));
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            context.getResponse().sendError(401, "Invalid token");
            return false;
        }
    }
}
