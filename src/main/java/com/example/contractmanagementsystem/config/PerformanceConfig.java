package com.example.contractmanagementsystem.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.listener.logging.SLF4JLogLevel;
import net.ttddyy.dsproxy.listener.logging.SLF4JQueryLoggingListener;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;

/**
 * 性能监控配置类
 * 用于监控SQL查询性能和解决N+1查询问题
 */
@Configuration
public class PerformanceConfig {
    
    /**
     * 数据源代理，用于监控SQL查询
     * 仅在开发环境启用
     */
    @Bean
    @Profile("dev")
    @ConditionalOnProperty(name = "spring.jpa.show-sql", havingValue = "true")
    public DataSource dataSourceProxy(DataSource dataSource) {
        SLF4JQueryLoggingListener loggingListener = new SLF4JQueryLoggingListener();
        loggingListener.setLogLevel(SLF4JLogLevel.INFO);
        loggingListener.setWriteDataSourceName(false);
        loggingListener.setWriteConnectionId(true);
        
        return ProxyDataSourceBuilder
                .create(dataSource)
                .name("ContractManagementSystemDS")
                .listener(loggingListener)
                .multiline()
                .countQuery()
                .build();
    }
} 