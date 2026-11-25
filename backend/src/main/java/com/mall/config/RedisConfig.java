package com.mall.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 缓存与序列化配置
 * - 启用 @Cacheable 等注解
 * - 统一 JSON 序列化（Jackson 处理 LocalDateTime 等）
 *
 * 通过 mall.redis.enabled 控制是否启用（application.yml）
 * enabled=false 时：Spring Boot 自动配置 + 本类都跳过 → 无 Redis ConnectionFactory
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(name = "mall.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    @Value("${spring.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.redis.port:6379}")
    private int redisPort;

    /**
     * Lettuce 连接工厂（自己建，因为排除了 Spring Boot 自动配置）
     */
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(redisHost, redisPort);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(cfg);
        factory.setShareNativeConnection(false); // 不强制共享连接，避免启动时立即阻塞
        return factory;
    }

    /**
     * 通用 RedisTemplate<String,Object>
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> tpl = new RedisTemplate<>();
        tpl.setConnectionFactory(factory);
        tpl.setKeySerializer(new StringRedisSerializer());
        tpl.setHashKeySerializer(new StringRedisSerializer());
        tpl.setValueSerializer(jacksonSerializer());
        tpl.setHashValueSerializer(jacksonSerializer());
        tpl.afterPropertiesSet();
        return tpl;
    }

    /**
     * StringRedisTemplate（给 CartServiceImpl / RecommendServiceImpl / RateLimiter 用）
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jacksonSerializer()));

        Map<String, RedisCacheConfiguration> perCache = new HashMap<>();
        perCache.put("product:detail", base.entryTtl(Duration.ofMinutes(10)));
        perCache.put("product:hot", base.entryTtl(Duration.ofMinutes(5)));
        perCache.put("category:list", base.entryTtl(Duration.ofMinutes(30)));
        perCache.put("ai:context", base.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * 通用 Jackson 序列化器（写入 @class 字段以支持反序列化还原原类型）
     */
    private GenericJackson2JsonRedisSerializer jacksonSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(om);
    }
}
