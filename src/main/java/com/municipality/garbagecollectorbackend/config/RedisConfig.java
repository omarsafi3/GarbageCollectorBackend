package com.municipality.garbagecollectorbackend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
public class RedisConfig {

    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        
        // Enable polymorphic type handling with a permissive type validator
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("com.municipality.garbagecollectorbackend")
                .allowIfSubType("java.util")
                .allowIfSubType("java.lang")
                .allowIfSubType("java.time")
                .build();
        
        mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        
        return mapper;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());
        
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());
        
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(5))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .disableCachingNullValues();

        // Custom TTL for different cache regions
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // Departments rarely change - cache for 30 minutes
        cacheConfigurations.put("departments", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("department", defaultConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Vehicles change more frequently - cache for 2 minutes
        cacheConfigurations.put("vehicles", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        cacheConfigurations.put("vehiclesByDepartment", defaultConfig.entryTtl(Duration.ofMinutes(2)));
        
        // Employees - cache for 5 minutes
        cacheConfigurations.put("employees", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        cacheConfigurations.put("employeesByDepartment", defaultConfig.entryTtl(Duration.ofMinutes(5)));
        
        // Bins change frequently due to fill levels - cache for 1 minute
        cacheConfigurations.put("bins", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        cacheConfigurations.put("binsByDepartment", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Active incidents - cache for 30 seconds (need real-time accuracy)
        cacheConfigurations.put("activeIncidents", defaultConfig.entryTtl(Duration.ofSeconds(30)));
        
        // Dashboard stats - cache for 1 minute
        cacheConfigurations.put("dashboardStats", defaultConfig.entryTtl(Duration.ofMinutes(1)));
        
        // Route history - cache for 10 minutes
        cacheConfigurations.put("routeHistory", defaultConfig.entryTtl(Duration.ofMinutes(10)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }
}
