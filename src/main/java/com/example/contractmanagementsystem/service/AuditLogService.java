package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.User; // 假设需要记录操作用户实体

public interface AuditLogService {

    /**
     * 记录操作日志
     * @param username 操作用户名
     * @param action 操作类型 (例如: "CREATE_USER", "ASSIGN_CONTRACT")
     * @param details 操作详情
     */
    void logAction(String username, String action, String details);

    /**
     * 记录操作日志，关联到用户实体 (如果需要更详细的用户信息)
     * @param user 操作用户实体
     * @param action 操作类型
     * @param details 操作详情
     */
    void logAction(User user, String action, String details);
}