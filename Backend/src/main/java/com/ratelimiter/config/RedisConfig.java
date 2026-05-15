package com.ratelimiter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

/**
 * Redis infrastructure configuration.
 *
 * Key decisions:
 * ──────────────
 * 1. StringRedisSerializer for both key and value → human-readable keys in Redis CLI
 * 2. DefaultRedisScript loaded once at startup → SHA cached by Spring, no re-send overhead
 * 3. Script returns List<Long> — Lua arrays map to Java List in Spring Data Redis
 */
@Slf4j
@Configuration
public class RedisConfig {

    /**
     * RedisTemplate configured with String serializers.
     *
     * Why String serializers?
     * - Redis keys are always strings (rate_limit:userId)
     * - Lua script handles all value logic; Java never directly reads/writes hash fields
     * - Avoids JdkSerializationRedisSerializer's binary overhead and version coupling
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializers for keys and values
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.setDefaultSerializer(stringSerializer);

        template.afterPropertiesSet();
        log.info("RedisTemplate configured with StringRedisSerializer");
        return template;
    }

    /**
     * Pre-loads the Lua script at startup.
     *
     * Spring Data Redis will:
     *  1. Hash the script with SHA-1 on first call (EVALSHA)
     *  2. Fall back to EVAL if the script is not cached in Redis
     *
     * Return type is List<Long> because Lua returns a two-element table:
     *   { allowed (0 or 1), remainingTokens }
     */
    @Bean
    public DefaultRedisScript<List> tokenBucketScript() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/token_bucket.lua"))
        );
        script.setResultType(List.class);
        log.info("Lua token bucket script loaded from classpath:scripts/token_bucket.lua");
        return script;
    }
}
