// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/entity/ContractProcess.java
package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_process", indexes = { // 在这里添加 indexes 属性
        @Index(name = "idx_cp_contract_id", columnList = "contract_id"),
        @Index(name = "idx_cp_operator_id", columnList = "user_id"), // operator 的外键列名通常是 user_id
        @Index(name = "idx_cp_type", columnList = "type"),
        @Index(name = "idx_cp_state", columnList = "state"),
        @Index(name = "idx_cp_created_at", columnList = "createdAt"),
        // 组合索引，针对经常同时使用的查询条件
        @Index(name = "idx_cp_operator_type_state", columnList = "user_id, type, state")
})
public class ContractProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "con_num", length = 50)
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractProcessType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractProcessState state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User operator;

    @Column(name = "user_name", length = 40)
    private String operatorUsername;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String comments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "requested_new_end_date")
    private LocalDate requestedNewEndDate;

    @Column(name = "request_reason", length = 500)
    private String requestReason;

    @Column(name = "request_additional_comments", length = 500)
    private String requestAdditionalComments;

    @Column(name = "admin_approval_comments", length = 500)
    private String adminApprovalComments;
}