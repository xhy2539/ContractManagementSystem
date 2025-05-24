package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal; // 如果有金额相关的字段
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contracts") // 对应数据字典中的 "合同(contract)基本信息"
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20) // 参照数据字典中的 num 字段
    private String contractNumber; // 合同编号

    @Column(nullable = false, length = 100) // 参照数据字典中的 name 字段
    private String contractName; // 合同名称

    // 假设与客户是多对一的关系，一个合同属于一个客户
    @ManyToOne(fetch = FetchType.LAZY) // LAZY: 延迟加载客户信息
    @JoinColumn(name = "customer_id") // 参照数据字典中与客户的关系
    private Customer customer; // 客户实体，需要单独创建 Customer.java

    @Column(name = "start_date") // 参照数据字典中的 beginTime
    private LocalDate startDate; // 开始时间

    @Column(name = "end_date") // 参照数据字典中的 endTime
    private LocalDate endDate; // 结束时间

    @Lob // 对于较长的文本内容
    @Column(name = "content", columnDefinition="TEXT") // 参照数据字典中的 content 字段
    private String content; // 合同内容

    // 假设合同状态用一个枚举或字符串表示
    @Column(length = 20)
    private String status; // 例如: DRAFT, PENDING_APPROVAL, APPROVED, ACTIVE, EXPIRED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafter_user_id") // 参照数据字典中的 userName (起草人)
    private User drafter; // 起草人 (User 实体)

    // 如果有合同金额
    // @Column(precision = 19, scale = 4) // 根据实际精度要求调整
    // private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}