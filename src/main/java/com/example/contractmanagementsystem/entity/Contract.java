// File: src/main/java/com/example/contractmanagementsystem/entity/Contract.java
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
// import java.util.List; // 如果您决定使用JPA TypeConverter for a List<String> (目前不使用)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contracts")
public class Contract {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String contractNumber;

    @Column(nullable = false, length = 100)
    private String contractName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Lob
    @Column(name = "content", columnDefinition="TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private ContractStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drafter_user_id")
    private User drafter;

    // 修改点：将 attachmentPath 用于存储多个附件路径 (例如 JSON 字符串)
    @Lob // 使用 @Lob 注解以支持更长的字符串，例如存储JSON数组
    @Column(name = "attachment_path", columnDefinition="TEXT") // 明确指定为TEXT类型以获得更大存储空间
    private String attachmentPath; // 将用于存储附件文件名列表的JSON字符串

    // contractContent 字段如果与 content 字段重复，请考虑是否需要保留
    // @Lob
    // private String contractContent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}