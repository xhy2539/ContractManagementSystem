package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "functionalities")
public class Functionality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10) // 参照数据字典中的 num 字段
    private String num; // 功能编号

    @Column(nullable = false, unique = true, length = 50) // 参照数据字典中的 name 字段
    private String name; // 功能名称，例如 "起草合同", "审批合同"

    @Column(length = 100) // 参照数据字典中的 URL 字段
    private String url; // 对应的操作URL (如果适用)

    @Column(length = 100) // 参照数据字典中的 description 字段
    private String description; // 功能描述
}