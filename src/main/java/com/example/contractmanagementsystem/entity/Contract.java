// 文件路径: src/main/java/com/example/contractmanagementsystem/entity/Contract.java
package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*; // 导入所有 JPA 相关的注解
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contracts") // 指定数据库中对应的表名为 'contracts'
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String contractNumber;

    @Column(nullable = false, length = 100)
    private String contractName;

    // --- 关键修改开始 ---
    // 这是与 Customer 实体建立多对一关系的正确方式
    @ManyToOne(fetch = FetchType.LAZY) // 多个合同可以关联同一个客户（多对一）
    // fetch = FetchType.LAZY 表示关联的 Customer 在需要时才会被加载
    @JoinColumn(name = "customer_id", nullable = false) // 指定外键列名为 'customer_id'，此列不可为空
    private Customer customer; // 注意：这里的类型是 Customer 实体，而不是 String
    // --- 关键修改结束 ---

    @Column(name = "start_date", nullable = false) // 合同开始日期
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false) // 合同结束日期
    private LocalDate endDate;

    @Lob // 标记为大对象，通常用于存储大量文本或二进制数据
    @Column(name = "content", columnDefinition="TEXT") // 合同内容，映射为 TEXT 类型
    private String content;

    @Enumerated(EnumType.STRING) // 枚举类型存储为字符串（'PENDING', 'ACTIVE' 等）
    @Column(length = 30) // 状态字段长度，根据枚举值的最长长度调整
    private ContractStatus status; // 合同状态

    @ManyToOne(fetch = FetchType.LAZY) // 与起草人用户建立多对一关系
    @JoinColumn(name = "drafter_user_id") // 外键列名为 'drafter_user_id'
    private User drafter; // 合同起草人

    @Column(length = 255) // 附件路径，最大长度255
    private String attachmentPath;

    @Lob // 标记为大对象
    private String contractContent; // 另一个合同内容字段，如果与 'content' 功能重复，请考虑移除一个

    // 如果有合同金额，可以启用以下字段
    // @Column(precision = 19, scale = 4) // 精度19，小数位4
    // private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // --- 移除这些行，它们是导致错误的根源 ---
    // private String customer; // 这是之前错误的 String 类型字段
    // public void setClient(String customer) { ... } // 这是为错误字段准备的 setter
    // --- 移除结束 ---
}