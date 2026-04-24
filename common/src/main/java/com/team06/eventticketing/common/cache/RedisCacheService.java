package com.team06.eventticketing.common.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public <T> T get(String key, JavaType javaType) {
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null) {
                return null;
            }
            return objectMapper.readValue(payload, javaType);
        } catch (Exception exception) {
            log.warn("Redis cache read failed for key {}", key, exception);
            return null;
        }
    }

    public void put(String key, Object value, long ttlSeconds) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttlSeconds, TimeUnit.SECONDS);
        } catch (Exception exception) {
            log.warn("Redis cache write failed for key {}", key, exception);
        }
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (DataAccessException exception) {
            log.warn("Redis cache delete failed for key {}", key, exception);
        }
    }

    public void deleteByPattern(String pattern) {
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                Set<byte[]> keys = connection.keys(pattern.getBytes(StandardCharsets.UTF_8));
                if (keys != null && !keys.isEmpty()) {
                    connection.del(keys.toArray(byte[][]::new));
                }
                return null;
            });
        } catch (Exception exception) {
            log.warn("Redis wildcard delete failed for pattern {}", pattern, exception);
        }
    }

    public String stableHash(Object value) {
        try {
            if (value == null) {
                return "null";
            }
            if (value instanceof Number || value instanceof CharSequence || value instanceof Boolean) {
                return value.toString();
            }
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(objectMapper.writeValueAsBytes(value));
            StringBuilder builder = new StringBuilder();
            for (byte item : hashed) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception exception) {
            return String.valueOf(value);
        }
    }
}
