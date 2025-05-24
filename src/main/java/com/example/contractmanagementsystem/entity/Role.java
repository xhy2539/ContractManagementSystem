package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "roles") // 对应数据字典中的 "角色(role)信息"
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40) // 参照数据字典中的 name 字段
    private String name; // 例如 ROLE_ADMIN, ROLE_USER, ROLE_CONTRACT_OPERATOR

    @Column(length = 100) // 参照数据字典中的 description 字段
    private String description;

    // 启用并完善多对多关系
    @ManyToMany(fetch = FetchType.LAZY) // LAZY: 角色加载时不立即加载其功能，需要时再加载
    @JoinTable(name = "role_functionalities", // 定义连接表名称，可以自定义
            joinColumns = @JoinColumn(name = "role_id"), // 连接表中对应 Role 实体的外键列
            inverseJoinColumns = @JoinColumn(name = "functionality_id")) // 连接表中对应 Functionality 实体的外键列
    private Set<Functionality> functionalities = new HashSet<>(); // 一个角色可以拥有多个功能 [cite: 42, 60]
}