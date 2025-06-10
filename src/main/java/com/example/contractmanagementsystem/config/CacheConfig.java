package com.example.contractmanagementsystem.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cache.interceptor.KeyGenerator;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final List<String> CACHE_NAMES = Arrays.asList(
            "pendingProcesses",
            "contractStatistics", 
            "userContracts",
            "dashboardData",
            "contractDetails",
            "userRoles",
            "customerInfo",
            "templateData"
    );

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCacheNames(CACHE_NAMES);
        cacheManager.setCaffeine(caffeineCacheBuilder());
        return cacheManager;
    }

    private Caffeine<Object, Object> caffeineCacheBuilder() {
        return Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(30))
                .recordStats();
    }

    @Bean
    public KeyGenerator customKeyGenerator() {
        return (target, method, params) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(target.getClass().getSimpleName());
            sb.append(":");
            sb.append(method.getName());
            sb.append(":");
            for (Object param : params) {
                if (param != null) {
                    sb.append(param.toString());
                    sb.append("_");
                }
            }
            return sb.toString();
        };
    }

    // 为不同的缓存配置不同的过期时间和大小
    @Bean
    public CacheManager contractCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // 合同统计数据缓存配置
        cacheManager.registerCustomCache("contractStatistics",
                Caffeine.newBuilder()
                        .maximumSize(100)
                        .expireAfterWrite(Duration.ofMinutes(15))
                        .build());

        // 用户数据缓存配置
        cacheManager.registerCustomCache("userContracts",
                Caffeine.newBuilder()
                        .maximumSize(500)
                        .expireAfterWrite(Duration.ofMinutes(10))
                        .build());

        // 仪表板数据缓存配置
        cacheManager.registerCustomCache("dashboardData",
                Caffeine.newBuilder()
                        .maximumSize(200)
                        .expireAfterWrite(Duration.ofMinutes(5))
                        .build());

        // 合同详情缓存配置
        cacheManager.registerCustomCache("contractDetails",
                Caffeine.newBuilder()
                        .maximumSize(1000)
                        .expireAfterWrite(Duration.ofHours(1))
                        .build());

        return cacheManager;
    }
} 