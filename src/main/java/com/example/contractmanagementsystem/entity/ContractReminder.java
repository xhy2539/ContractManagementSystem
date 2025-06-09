package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同提醒实体
 * 用于存储合同到期提醒、续签提醒等智能提醒信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_reminders", indexes = {
    @Index(name = "idx_reminder_contract", columnList = "contract_id"),
    @Index(name = "idx_reminder_type", columnList = "reminder_type"),
    @Index(name = "idx_reminder_date", columnList = "reminder_date"),
    @Index(name = "idx_reminder_status", columnList = "status"),
    @Index(name = "idx_reminder_user", columnList = "user_id")
})
public class ContractReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 提醒的目标用户

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate; // 提醒日期

    @Column(name = "target_date")
    private LocalDate targetDate; // 目标日期（如合同到期日期）

    @Column(name = "days_before")
    private Integer daysBefore; // 提前多少天提醒

    @Column(name = "title", length = 200)
    private String title; // 提醒标题

    @Lob
    @Column(name = "message", columnDefinition = "TEXT")
    private String message; // 提醒内容

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReminderStatus status;

    @Column(name = "is_sent")
    private Boolean isSent = false; // 是否已发送

    @Column(name = "sent_at")
    private LocalDateTime sentAt; // 发送时间

    @Column(name = "is_read")
    private Boolean isRead = false; // 是否已读

    @Column(name = "read_at")
    private LocalDateTime readAt; // 阅读时间

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 提醒类型枚举
     */
    public enum ReminderType {
        CONTRACT_EXPIRING("合同即将到期"),
        RENEWAL_DUE("合同续签提醒"),
        PAYMENT_DUE("付款到期提醒"),
        MILESTONE_DUE("里程碑到期提醒"),
        REVIEW_DUE("合同审查提醒"),
        RISK_ALERT("风险预警提醒");

        private final String description;

        ReminderType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 提醒状态枚举
     */
    public enum ReminderStatus {
        PENDING("待发送"),
        SENT("已发送"),
        READ("已阅读"),
        DISMISSED("已忽略"),
        CANCELLED("已取消");

        private final String description;

        ReminderStatus(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 