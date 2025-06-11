package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 合同提醒实体
 * 用于存储合同到期提醒、续签提醒等智能提醒信息
 */
@Entity
@Table(name = "contract_reminders", indexes = {
    @Index(name = "idx_reminder_contract", columnList = "contract_id"),
    @Index(name = "idx_reminder_user", columnList = "user_id")
})
@Data
@NoArgsConstructor
public class ContractReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 200)
    private String title;

    @Lob
    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_type", nullable = false, length = 30)
    private ReminderType reminderType;

    @Column(name = "reminder_date", nullable = false)
    private LocalDate reminderDate;

    @Column(name = "target_date")
    private LocalDate targetDate;

    @Column(name = "days_before")
    private Integer daysBefore;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "is_sent")
    private Boolean isSent = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "is_read")
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

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