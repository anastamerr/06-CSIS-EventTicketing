package com.team06.eventticketing.common.auth;

import java.io.IOException;

public class RoleAuthorizationHandler extends AuthHandler {

    @Override
    protected boolean process(AuthContext context) throws IOException {
        if (context.getRequiredRole() == null) {
            return true;
        }
        String actualRole = context.getUser() == null ? null : context.getUser().role();
        if (context.getRequiredRole().equals(actualRole)) {
            return true;
        }
        context.getResponse().setStatus(403);
        context.getResponse().getWriter().write("Forbidden");
        return false;
    }
}
