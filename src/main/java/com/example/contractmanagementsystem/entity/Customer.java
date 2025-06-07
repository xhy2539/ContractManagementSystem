// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/java/com/example/contractmanagementsystem/entity/Customer.java
package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers", indexes = { // 在这里添加 indexes 属性
        @Index(name = "idx_customer_number", columnList = "customerNumber"), // 为 customerNumber 添加索引
        @Index(name = "idx_customer_name", columnList = "customerName"),     // 为 customerName 添加索引
        @Index(name = "idx_email", columnList = "email")                  // 为 email 添加索引 (如果经常按email搜索)
})
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String customerNumber;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(length = 200)
    private String address;

    @Column(length = 20)
    private String phoneNumber;

    @Column(length = 100)
    private String email;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}