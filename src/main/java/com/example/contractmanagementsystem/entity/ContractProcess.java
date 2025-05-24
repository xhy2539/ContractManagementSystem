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
@Table(name = "contract_process")
public class ContractProcess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "con_num", length = 20) // 可选冗余字段
    private String contractNumber;

    @Enumerated(EnumType.ORDINAL) // 或 EnumType.STRING
    @Column(nullable = false)
    private ContractProcessType type; // 操作类型：1-会签, 2-审批, 3-签订

    @Enumerated(EnumType.ORDINAL) // 或 EnumType.STRING
    @Column(nullable = false)
    private ContractProcessState state; // 操作状态：0-未完成, 1-已完成, 2-已否决

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User operator; // 操作人

    @Column(name = "user_name", length = 40) // 可选冗余字段
    private String operatorUsername;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content; // 操作内容，例如会签意见、审批意见

    @Column(name = "time")
    private LocalDateTime operationTime; // 具体操作完成的时间

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt; // 分配记录创建时间
}