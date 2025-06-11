// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/entity/AuditLog.java
package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs", indexes = { // 在这里添加 indexes 属性
        @Index(name = "idx_audit_username", columnList = "username"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_timestamp", columnList = "timestamp"),
        // 组合索引，针对同时按用户和时间范围查询
        @Index(name = "idx_audit_username_timestamp", columnList = "username, timestamp")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40)
    private String username;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 255)
    private String action;

    @Lob
    @Column(name = "details", columnDefinition="TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private LocalDateTime timestamp;

    public AuditLog(String username, String action, String details) {
        this.username = username;
        this.action = action;
        this.details = details;
        this.timestamp = LocalDateTime.now();
    }
}