package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 合同版本实体
 * 用于存储合同的历史版本，支持版本比对功能
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "contract_versions", indexes = {
    @Index(name = "idx_version_contract", columnList = "contract_id"),
    @Index(name = "idx_version_number", columnList = "version_number"),
    @Index(name = "idx_version_created_at", columnList = "created_at"),
    @Index(name = "idx_version_contract_version", columnList = "contract_id, version_number")
})
public class ContractVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    private Contract contract;

    @Column(name = "version_number", nullable = false)
    private Integer versionNumber; // 版本号，从1开始

    @Lob
    @Column(name = "content", columnDefinition = "TEXT")
    private String content; // 该版本的合同内容

    @Lob
    @Column(name = "attachment_path", columnDefinition = "TEXT")
    private String attachmentPath; // 该版本的附件路径JSON

    @Column(name = "version_description", length = 500)
    private String versionDescription; // 版本说明

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdBy; // 创建此版本的用户

    @Column(name = "created_by_username", length = 50)
    private String createdByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "change_type", length = 20)
    private ChangeType changeType; // 变更类型

    @Lob
    @Column(name = "change_summary", columnDefinition = "TEXT")
    private String changeSummary; // 变更摘要，JSON格式存储具体变更内容

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        INITIAL("初始版本"),
        DRAFT("起草修改"),
        FINALIZATION("定稿修改"),
        APPROVAL("审批修改"),
        AMENDMENT("合同修正"),
        EXTENSION("延期修改");

        private final String description;

        ChangeType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
} 