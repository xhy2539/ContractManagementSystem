package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.AuditLog;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.AuditLogRepository;
import com.example.contractmanagementsystem.repository.UserRepository; // 用于通过用户名查找User对象
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository; // 可选，如果logAction(User user,...)方法需要通过username查找

    @Autowired
    public AuditLogServiceImpl(AuditLogRepository auditLogRepository, UserRepository userRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void logAction(String username, String action, String details) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUsername(username);
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now()); // AuditLog实体中的@CreationTimestamp会自动处理，这里也可以手动设置

        // 如果需要关联User实体，可以通过username查找
        // userRepository.findByUsername(username).ifPresent(auditLog::setUser);

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional
    public void logAction(User user, String action, String details) {
        AuditLog auditLog = new AuditLog();
        if (user != null) {
            auditLog.setUsername(user.getUsername());
            auditLog.setUser(user);
        } else {
            auditLog.setUsername("SYSTEM_UNKNOWN"); // 或者其他默认值
        }
        auditLog.setAction(action);
        auditLog.setDetails(details);
        auditLog.setTimestamp(LocalDateTime.now()); // 同上

        auditLogRepository.save(auditLog);
    }
}