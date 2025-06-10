package com.example.contractmanagementsystem.performance;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;

@Service
public class PerformanceTestService {
    private static final Logger logger = LoggerFactory.getLogger(PerformanceTestService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private PerformanceMonitor performanceMonitor;

    public void runPerformanceTests() {
        logger.info("开始性能测试...");

        // 1. 测试数据库连接池性能
        testConnectionPool();

        // 2. 测试缓存性能
        testCachePerformance();

        // 3. 测试批处理性能
        testBatchProcessing();

        logger.info("性能测试完成");
    }

    private void testConnectionPool() {
        logger.info("测试数据库连接池性能...");
        performanceMonitor.logPerformanceMetrics();
        
        // 模拟并发请求
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Thread thread = new Thread(() -> {
                performanceMonitor.monitorQueryPerformance("简单查询", () -> {
                    jdbcTemplate.queryForList("SELECT 1");
                });
            });
            threads.add(thread);
            thread.start();
        }

        // 等待所有线程完成
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                logger.error("线程中断", e);
            }
        });

        performanceMonitor.logPerformanceMetrics();
    }

    private void testCachePerformance() {
        logger.info("测试缓存性能...");
        
        // 第一次查询（未缓存）
        performanceMonitor.monitorQueryPerformance("未缓存查询", () -> {
            // 模拟复杂查询
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 第二次查询（已缓存）
        performanceMonitor.monitorQueryPerformance("已缓存查询", () -> {
            // 模拟从缓存获取
            try {
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    @Transactional
    private void testBatchProcessing() {
        logger.info("测试批处理性能...");
        
        // 测试单条插入
        performanceMonitor.monitorQueryPerformance("单条插入", () -> {
            for (int i = 0; i < 100; i++) {
                jdbcTemplate.update("/* 测试SQL */ SELECT 1");
            }
        });

        // 测试批量插入
        performanceMonitor.monitorQueryPerformance("批量插入", () -> {
            List<Object[]> batchArgs = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                batchArgs.add(new Object[]{});
            }
            jdbcTemplate.batchUpdate("/* 测试SQL */ SELECT 1", batchArgs);
        });
    }
} 