package com.example.contractmanagementsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PerformanceMonitorService {
    
    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitorService.class);
    
    private final CacheManager cacheManager;
    
    // 性能计数器
    private final Map<String, AtomicLong> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> totalResponseTimes = new ConcurrentHashMap<>();
    
    public PerformanceMonitorService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }
    
    /**
     * 记录请求性能
     */
    public void recordRequestTime(String endpoint, long responseTimeMs) {
        requestCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
        totalResponseTimes.computeIfAbsent(endpoint, k -> new AtomicLong(0)).addAndGet(responseTimeMs);
        
        // 如果响应时间超过1秒，记录警告
        if (responseTimeMs > 1000) {
            logger.warn("慢请求检测: {} 耗时 {}ms", endpoint, responseTimeMs);
        }
    }
    
    /**
     * 获取性能统计信息
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        for (Map.Entry<String, AtomicLong> entry : requestCounts.entrySet()) {
            String endpoint = entry.getKey();
            long count = entry.getValue().get();
            long totalTime = totalResponseTimes.get(endpoint).get();
            double avgTime = count > 0 ? (double) totalTime / count : 0;
            
            Map<String, Object> endpointStats = new HashMap<>();
            endpointStats.put("requestCount", count);
            endpointStats.put("totalResponseTime", totalTime);
            endpointStats.put("averageResponseTime", avgTime);
            
            stats.put(endpoint, endpointStats);
        }
        
        return stats;
    }
    
    /**
     * 清空缓存
     */
    public void clearAllCaches() {
        if (cacheManager != null) {
            cacheManager.getCacheNames().forEach(cacheName -> {
                if (cacheManager.getCache(cacheName) != null) {
                    cacheManager.getCache(cacheName).clear();
                }
            });
            logger.info("所有缓存已清空");
        }
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> cacheStats = new HashMap<>();
        
        if (cacheManager != null) {
            for (String cacheName : cacheManager.getCacheNames()) {
                cacheStats.put(cacheName, "缓存活跃");
            }
        }
        
        return cacheStats;
    }
    
    /**
     * 重置性能计数器
     */
    public void resetPerformanceCounters() {
        requestCounts.clear();
        totalResponseTimes.clear();
        logger.info("性能计数器已重置");
    }
} 