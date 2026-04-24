package com.team06.eventticketing.common.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CachedDetail {
    String service();
    String entity();
    String key();
    long ttlSeconds() default 900;
}
