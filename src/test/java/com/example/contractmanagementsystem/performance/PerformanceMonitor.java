package com.example.contractmanagementsystem.performance;

import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class PerformanceMonitor {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitor.class);

    @Autowired
    private DataSource dataSource;

    @Autowired(required = false)
    private MetricsEndpoint metricsEndpoint;

    public Map<String, Object> getPerformanceMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        // 连接池指标
        if (dataSource instanceof HikariDataSource) {
            HikariDataSource hikariDS = (HikariDataSource) dataSource;
            metrics.put("activeConnections", hikariDS.getHikariPoolMXBean().getActiveConnections());
            metrics.put("idleConnections", hikariDS.getHikariPoolMXBean().getIdleConnections());
            metrics.put("threadsAwaitingConnection", hikariDS.getHikariPoolMXBean().getThreadsAwaitingConnection());
            metrics.put("totalConnections", hikariDS.getHikariPoolMXBean().getTotalConnections());
        }

        // JVM指标
        Runtime runtime = Runtime.getRuntime();
        metrics.put("maxMemory", runtime.maxMemory() / 1024 / 1024 + "MB");
        metrics.put("usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + "MB");
        metrics.put("availableProcessors", runtime.availableProcessors());

        return metrics;
    }

    public void logPerformanceMetrics() {
        Map<String, Object> metrics = getPerformanceMetrics();
        logger.info("性能指标:");
        metrics.forEach((key, value) -> logger.info("{}: {}", key, value));
    }

    public void monitorQueryPerformance(String operationName, Runnable operation) {
        long startTime = System.nanoTime();
        operation.run();
        long endTime = System.nanoTime();
        long duration = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        logger.info("操作 [{}] 执行时间: {}ms", operationName, duration);
    }
} 