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
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 40) // 参照数据字典中的 userName (操作人)
    private String username; // 操作用户名

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id") // 可选，关联到 User 实体
    private User user;

    @Column(nullable = false, length = 255)
    private String action; // 例如: "CREATE_CONTRACT", "UPDATE_USER_ROLE"

    @Lob
    @Column(name = "details", columnDefinition="TEXT") // 参照数据字典中的 content 字段
    private String details; // 操作详情，例如具体修改的内容

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false) // 参照数据字典中的 time 字段
    private LocalDateTime timestamp; // 操作时间
}