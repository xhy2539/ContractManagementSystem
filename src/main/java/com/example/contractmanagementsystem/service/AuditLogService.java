// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/service/AuditLogService.java
package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.AuditLog;
import com.example.contractmanagementsystem.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditLogService {

    void logAction(String username, String action, String details);

    void logAction(User user, String action, String details);

    /**
     * 根据多个可选条件搜索审计日志。
     * @param username 用户名 (可选)
     * @param action 操作类型 (可选)
     * @param startDate 开始时间 (可选)
     * @param endDate 结束时间 (可选)
     * @param pageable 分页和排序信息
     * @return 符合条件的审计日志分页数据
     */
    Page<AuditLog> searchLogs(String username, String action, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);

    /**
     * 根据多个可选条件搜索所有审计日志 (用于导出)。
     * @param username 用户名 (可选)
     * @param action 操作类型 (可选)
     * @param startDate 开始时间 (可选)
     * @param endDate 结束时间 (可选)
     * @return 符合条件的审计日志列表
     */
    List<AuditLog> findAllLogsByCriteria(String username, String action, LocalDateTime startDate, LocalDateTime endDate);
}