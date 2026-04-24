package com.team06.eventticketing.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class CacheAspect {

    private final RedisCacheService redisCacheService;
    private final ObjectMapper objectMapper;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    public CacheAspect(RedisCacheService redisCacheService, ObjectMapper objectMapper) {
        this.redisCacheService = redisCacheService;
        this.objectMapper = objectMapper;
    }

    @Around("@annotation(cachedDetail)")
    public Object aroundDetailCache(ProceedingJoinPoint joinPoint, CachedDetail cachedDetail) throws Throwable {
        String detailKey = buildDetailKey(joinPoint, cachedDetail);
        JavaType javaType = objectMapper.getTypeFactory().constructType(
                ((MethodSignature) joinPoint.getSignature()).getMethod().getGenericReturnType());
        Object cached = redisCacheService.get(detailKey, javaType);
        if (cached != null) {
            return cached;
        }

        Object result = joinPoint.proceed();
        if (result != null) {
            redisCacheService.put(detailKey, result, cachedDetail.ttlSeconds());
        }
        return result;
    }

    @Around("@annotation(cachedFeature)")
    public Object aroundFeatureCache(ProceedingJoinPoint joinPoint, CachedFeature cachedFeature) throws Throwable {
        String key = cachedFeature.service() + "::" + cachedFeature.featureId() + "::"
                + redisCacheService.stableHash(joinPoint.getArgs());
        JavaType javaType = objectMapper.getTypeFactory().constructType(
                ((MethodSignature) joinPoint.getSignature()).getMethod().getGenericReturnType());
        Object cached = redisCacheService.get(key, javaType);
        if (cached != null) {
            return cached;
        }

        Object result = joinPoint.proceed();
        if (result != null) {
            redisCacheService.put(key, result, cachedFeature.ttlSeconds());
        }
        return result;
    }

    @Around("@annotation(invalidateServiceCaches)")
    public Object aroundCacheInvalidation(
            ProceedingJoinPoint joinPoint,
            InvalidateServiceCaches invalidateServiceCaches
    ) throws Throwable {
        Object result = joinPoint.proceed();
        for (String detailKeyExpression : invalidateServiceCaches.detailKeys()) {
            String key = evaluateExpression(joinPoint, detailKeyExpression);
            if (key != null && !key.isBlank()) {
                redisCacheService.delete(key);
            }
        }
        redisCacheService.deleteByPattern(
                invalidateServiceCaches.service() + "::" + invalidateServiceCaches.featurePrefix() + "F*::*");
        return result;
    }

    private String buildDetailKey(ProceedingJoinPoint joinPoint, CachedDetail cachedDetail) {
        return cachedDetail.service() + "::" + cachedDetail.entity() + "::"
                + evaluateExpression(joinPoint, cachedDetail.key());
    }

    private String evaluateExpression(ProceedingJoinPoint joinPoint, String expression) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        context.setVariable("args", args);
        String[] parameterNames = parameterNameDiscoverer.getParameterNames(method);
        if (parameterNames != null) {
            for (int index = 0; index < parameterNames.length; index++) {
                context.setVariable(parameterNames[index], args[index]);
                context.setVariable("p" + index, args[index]);
            }
        }
        Object value = expressionParser.parseExpression(expression).getValue(context);
        return value == null ? null : value.toString();
    }
}
