package com.team06.eventticketing.common.auth;

import java.io.IOException;

public class UserLoaderHandler extends AuthHandler {

    private final JwtService jwtService;
    private final SecurityUserLookupService userLookupService;

    public UserLoaderHandler(JwtService jwtService, SecurityUserLookupService userLookupService) {
        this.jwtService = jwtService;
        this.userLookupService = userLookupService;
    }

    @Override
    protected boolean process(AuthContext context) throws IOException {
        Long userId = jwtService.extractUserId(context.getClaims());
        String email = context.getClaims().getSubject();
        return userLookupService.findByIdAndEmail(userId, email)
                .map(user -> {
                    context.setUser(user);
                    return true;
                })
                .orElseGet(() -> {
                    try {
                        context.getResponse().sendError(401, "User no longer exists");
                    } catch (IOException ignored) {
                    }
                    return false;
                });
    }
}
