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
@Table(name = "contract_templates")
public class ContractTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String templateName; // 模板名称

    @Lob
    @Column(name = "template_content", columnDefinition = "TEXT")
    private String templateContent; // 模板内容，可以是富文本HTML

    @Column(length = 50)
    private String templateType; // 模板类型 (例如: 销售合同, 采购合同, 服务合同)

    @Lob
    @Column(name = "placeholder_fields", columnDefinition = "TEXT")
    private String placeholderFields; // 占位符列表，例如 {"customerName", "contractAmount"}，存储为JSON字符串
}