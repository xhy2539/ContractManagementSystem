package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
// import java.util.Set; // 如果一个客户可以有多个合同

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "customers") // 对应数据字典中的 "客户(customer)基本信息"
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20) // 参照数据字典中的 num 字段
    private String customerNumber; // 客户编号

    @Column(nullable = false, length = 100) // 参照数据字典中的 name 字段
    private String customerName; // 客户名称

    @Column(length = 200) // 参照数据字典中的 address 字段
    private String address; // 地址

    @Column(length = 20) // 参照数据字典中的 tel 字段
    private String phoneNumber; // 电话

    @Column(length = 100)
    private String email; // 假设需要邮箱

    // 如果一个客户可以有多个合同
    // @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    // private Set<Contract> contracts;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}