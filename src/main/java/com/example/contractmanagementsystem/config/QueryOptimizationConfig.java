package com.example.contractmanagementsystem.config;

import org.hibernate.cfg.AvailableSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;

import java.util.Map;

@Configuration
public class QueryOptimizationConfig {

    @Bean
    public HibernatePropertiesCustomizer hibernatePropertiesCustomizer() {
        return hibernateProperties -> {
            // 批处理优化
            hibernateProperties.put(AvailableSettings.STATEMENT_BATCH_SIZE, "50");
            hibernateProperties.put(AvailableSettings.ORDER_INSERTS, "true");
            hibernateProperties.put(AvailableSettings.ORDER_UPDATES, "true");
            hibernateProperties.put(AvailableSettings.BATCH_VERSIONED_DATA, "true");

            // 查询优化
            hibernateProperties.put(AvailableSettings.DEFAULT_BATCH_FETCH_SIZE, "25");
            hibernateProperties.put(AvailableSettings.MAX_FETCH_DEPTH, "3");
            hibernateProperties.put(AvailableSettings.QUERY_PLAN_CACHE_MAX_SIZE, "2048");
            hibernateProperties.put(AvailableSettings.QUERY_PLAN_CACHE_PARAMETER_METADATA_MAX_SIZE, "128");
            
            // 连接优化
            hibernateProperties.put(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect");
            hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, "com.example.contractmanagementsystem.config.SqlStatementInspector");
            
            // 二级缓存配置
            hibernateProperties.put(AvailableSettings.USE_SECOND_LEVEL_CACHE, "true");
            hibernateProperties.put(AvailableSettings.USE_QUERY_CACHE, "true");
            hibernateProperties.put(AvailableSettings.CACHE_REGION_FACTORY, "org.hibernate.cache.jcache.JCacheRegionFactory");
            hibernateProperties.put(AvailableSettings.JAKARTA_CACHE_PROVIDER, "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider");
        };
    }
} 