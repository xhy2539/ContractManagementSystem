package com.example.contractmanagementsystem.config;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SqlStatementInspector implements StatementInspector {
    private static final Logger logger = LoggerFactory.getLogger(SqlStatementInspector.class);
    private static final long SLOW_QUERY_THRESHOLD = 1000; // 1秒

    @Override
    public String inspect(String sql) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 记录所有SQL查询
            logger.debug("执行SQL: {}", sql);
            
            // 检测潜在的性能问题
            checkForPerformanceIssues(sql);
            
            return sql;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            if (executionTime > SLOW_QUERY_THRESHOLD) {
                logger.warn("检测到慢查询 ({}ms): {}", executionTime, sql);
            }
        }
    }

    private void checkForPerformanceIssues(String sql) {
        // 检查SELECT *
        if (sql.toLowerCase().contains("select *")) {
            logger.warn("检测到SELECT *，建议指定具体列: {}", sql);
        }

        // 检查是否缺少WHERE子句
        if (sql.toLowerCase().contains("delete from") || sql.toLowerCase().contains("update")) {
            if (!sql.toLowerCase().contains("where")) {
                logger.warn("检测到没有WHERE子句的DELETE/UPDATE: {}", sql);
            }
        }

        // 检查大表JOIN
        if (countOccurrences(sql.toLowerCase(), "join") > 3) {
            logger.warn("检测到多表JOIN，可能影响性能: {}", sql);
        }

        // 检查IN子句中的元素数量
        if (sql.contains("IN (")) {
            String inClause = sql.substring(sql.indexOf("IN ("));
            int commaCount = countOccurrences(inClause.substring(0, inClause.indexOf(")")), ",");
            if (commaCount > 500) {
                logger.warn("IN子句中的元素过多 ({}个): {}", commaCount + 1, sql);
            }
        }
    }

    private int countOccurrences(String str, String substr) {
        return (str.length() - str.replace(substr, "").length()) / substr.length();
    }
} 