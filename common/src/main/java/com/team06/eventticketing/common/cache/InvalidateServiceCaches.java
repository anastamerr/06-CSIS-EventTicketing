package com.team06.eventticketing.common.cache;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface InvalidateServiceCaches {
    String service();
    String featurePrefix();
    String[] detailKeys() default {};
}
