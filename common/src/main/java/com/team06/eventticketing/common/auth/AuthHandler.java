package com.team06.eventticketing.common.auth;

import java.io.IOException;

public abstract class AuthHandler {

    private AuthHandler next;

    public AuthHandler setNext(AuthHandler next) {
        this.next = next;
        return next;
    }

    public final boolean handle(AuthContext context) throws IOException {
        if (!process(context)) {
            return false;
        }
        if (next == null) {
            return true;
        }
        return next.handle(context);
    }

    protected abstract boolean process(AuthContext context) throws IOException;
}
