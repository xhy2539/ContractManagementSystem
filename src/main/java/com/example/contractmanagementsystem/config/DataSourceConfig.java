package com.example.contractmanagementsystem.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.actuate.metrics.jdbc.DataSourcePoolMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    private static final Logger logger = LoggerFactory.getLogger(DataSourceConfig.class);

    @Value("${spring.datasource.url}")
    private String dataSourceUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    // 从配置文件读取连接池配置
    @Value("${spring.datasource.hikari.maximum-pool-size:20}")
    private int maximumPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:5}")
    private int minimumIdle;

    @Value("${spring.datasource.hikari.idle-timeout:300000}")
    private long idleTimeout;

    @Value("${spring.datasource.hikari.connection-timeout:20000}")
    private long connectionTimeout;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(dataSourceUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);

        // 连接池基本配置
        config.setPoolName("ContractManagementHikariCP");
        config.setMaximumPoolSize(maximumPoolSize);
        config.setMinimumIdle(minimumIdle);
        config.setIdleTimeout(idleTimeout);
        config.setMaxLifetime(1800000); // 30分钟
        config.setConnectionTimeout(connectionTimeout);
        
        // 连接测试配置
        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        
        // 性能优化配置
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");

        // 启用连接池监控
        config.setMetricRegistry(new com.codahale.metrics.MetricRegistry());
        config.setRegisterMbeans(true);

        HikariDataSource dataSource = new HikariDataSource(config);
        
        // 添加连接池使用情况的日志记录
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(300000); // 每5分钟记录一次
                    int active = dataSource.getHikariPoolMXBean().getActiveConnections();
                    int idle = dataSource.getHikariPoolMXBean().getIdleConnections();
                    int total = dataSource.getHikariPoolMXBean().getTotalConnections();
                    logger.info("连接池状态 - 活动连接: {}, 空闲连接: {}, 总连接: {}", active, idle, total);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "connection-pool-monitor").start();

        return dataSource;
    }

    // 添加Metrics监控
    @Bean
    public DataSourcePoolMetrics dataSourcePoolMetrics(DataSource dataSource, MeterRegistry registry) {
        return new DataSourcePoolMetrics(dataSource, "main", registry);
    }
} 