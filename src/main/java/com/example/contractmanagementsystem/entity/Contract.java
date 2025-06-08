// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/entity/Contract.java
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

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contracts", indexes = { // 在这里添加 indexes 属性
        @Index(name = "idx_contract_number", columnList = "contractNumber"), // 为 contractNumber 添加索引
        @Index(name = "idx_contract_name", columnList = "contractName"),     // 为 contractName 添加索引
        @Index(name = "idx_status", columnList = "status"),               // 为 status 添加索引
        @Index(name = "idx_end_date", columnList = "endDate"),           // 为 endDate 添加索引
        @Index(name = "idx_drafter_id", columnList = "drafter_user_id")  // 为 drafter_user_id (外键) 添加索引
})
public class Contract {
    @Lob // Added for signature data storage
    @Column(name = "signature_data", columnDefinition = "LONGTEXT") // Use LONGTEXT for potentially large base64 strings
    private String signatureData;
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

    @Lob
    @Column(name = "attachment_path", columnDefinition="TEXT")
    private String attachmentPath;

    @Lob // 甲方签名数据
    @Column(name = "signature_data_party_a", columnDefinition = "LONGTEXT")
    private String signatureDataPartyA;

    @Lob // 乙方签名数据
    @Column(name = "signature_data_party_b", columnDefinition = "LONGTEXT")
    private String signatureDataPartyB;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}