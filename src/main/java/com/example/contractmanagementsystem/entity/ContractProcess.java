// 文件路径: src/main/java/com/example/contractmanagementsystem/entity/ContractProcess.java
package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter; // 确保导入这个
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime; // 确保导入这个

@Getter
@Setter // <--- 确保这个注解存在
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

    @Column(name = "con_num", length = 50) // 可选冗余字段
    private String contractNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractProcessType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContractProcessState state; // 操作状态：0-未完成, 1-已完成, 2-已否决

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User operator; // 操作人

    @Column(name = "user_name", length = 40) // 可选冗余字段
    private String operatorUsername;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String comments;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) // 创建时间
    private LocalDateTime createdAt;

    @Column(name = "processed_at") // 完成或处理时间
    private LocalDateTime processedAt;

    // --- 新增字段 ---
    @Column(name = "completed_at") // 完成时间，可以为空，因为未完成时没有
    private LocalDateTime completedAt; // <--- 添加此行



}