package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalDate; // 引入 LocalDate

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
    private String content; // 某些流程可能有的内容，例如定稿时的合同内容

    @Lob
    @Column(columnDefinition = "TEXT")
    private String comments; // 通用评论字段，可能用于概括性意见，或兼容旧数据

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) // 创建时间
    private LocalDateTime createdAt;

    @Column(name = "processed_at") // 完成或处理时间
    private LocalDateTime processedAt;

    // --- 新增字段 ---
    @Column(name = "completed_at") // 完成时间，可以为空，因为未完成时没有
    private LocalDateTime completedAt;


    @Column(name = "requested_new_end_date")
    private LocalDate requestedNewEndDate; // 操作员请求的新的到期日期

    @Column(name = "request_reason", length = 500) // 延期原因可以长一点
    private String requestReason; // 操作员请求延期的原因

    @Column(name = "request_additional_comments", length = 500) // 附加备注可以长一点
    private String requestAdditionalComments; // 操作员请求的附加备注

    @Column(name = "admin_approval_comments", length = 500) // 管理员审批的意见
    private String adminApprovalComments;

    // 可以在这里添加一个方便的构造函数，但确保ORM工具可以正确映射
    // 例如：
     public ContractProcess(Contract contract, ContractProcessType type, ContractProcessState state, User operator, String comments) {
         this.contract = contract;
         this.contractNumber = contract.getContractNumber(); // 如果需要自动填充
         this.type = type;
         this.state = state;
         this.operator = operator;
         this.operatorUsername = operator.getUsername(); // 如果需要自动填充
         this.comments = comments;
         // createdAt 会由 @CreationTimestamp 自动设置
     }
}