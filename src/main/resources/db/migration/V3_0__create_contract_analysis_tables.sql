-- 合同智能分析模块数据表创建脚本
-- 版本: V3.0
-- 创建时间: 2024

-- 1. 合同分析结果表
CREATE TABLE IF NOT EXISTS contract_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    analysis_type VARCHAR(30) NOT NULL COMMENT '分析类型: RISK_ANALYSIS, CLAUSE_CHECK, LEGAL_REVIEW, FINANCIAL_ANALYSIS, COMPLIANCE_CHECK',
    risk_level VARCHAR(20) COMMENT '风险等级: LOW, MEDIUM, HIGH, CRITICAL',
    risk_score INT COMMENT '风险评分 0-100',
    findings TEXT COMMENT '分析发现的问题，JSON格式',
    recommendations TEXT COMMENT '建议措施，JSON格式',
    analyzer_version VARCHAR(20) COMMENT '分析器版本',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    
    INDEX idx_analysis_contract (contract_id),
    INDEX idx_analysis_type (analysis_type),
    INDEX idx_analysis_risk_level (risk_level),
    INDEX idx_analysis_created_at (created_at),
    
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同智能分析结果表';

-- 2. 合同版本表
CREATE TABLE IF NOT EXISTS contract_versions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    version_number INT NOT NULL COMMENT '版本号，从1开始',
    content TEXT COMMENT '该版本的合同内容',
    attachment_path TEXT COMMENT '该版本的附件路径，JSON格式',
    version_description VARCHAR(500) COMMENT '版本说明',
    created_by_user_id BIGINT COMMENT '创建此版本的用户ID',
    created_by_username VARCHAR(50) COMMENT '创建此版本的用户名',
    change_type VARCHAR(20) COMMENT '变更类型: INITIAL, DRAFT, FINALIZATION, APPROVAL, AMENDMENT, EXTENSION',
    change_summary TEXT COMMENT '变更摘要，JSON格式存储具体变更内容',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    
    INDEX idx_version_contract (contract_id),
    INDEX idx_version_number (version_number),
    INDEX idx_version_created_at (created_at),
    INDEX idx_version_contract_version (contract_id, version_number),
    
    UNIQUE KEY uk_contract_version (contract_id, version_number),
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同版本历史表';

-- 3. 合同提醒表
CREATE TABLE IF NOT EXISTS contract_reminders (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    contract_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL COMMENT '提醒的目标用户',
    reminder_type VARCHAR(30) NOT NULL COMMENT '提醒类型: CONTRACT_EXPIRING, RENEWAL_DUE, PAYMENT_DUE, MILESTONE_DUE, REVIEW_DUE, RISK_ALERT',
    reminder_date DATE NOT NULL COMMENT '提醒日期',
    target_date DATE COMMENT '目标日期（如合同到期日期）',
    days_before INT COMMENT '提前多少天提醒',
    title VARCHAR(200) COMMENT '提醒标题',
    message TEXT COMMENT '提醒内容',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '提醒状态: PENDING, SENT, READ, DISMISSED, CANCELLED',
    is_sent BOOLEAN DEFAULT FALSE COMMENT '是否已发送',
    sent_at TIMESTAMP NULL COMMENT '发送时间',
    is_read BOOLEAN DEFAULT FALSE COMMENT '是否已读',
    read_at TIMESTAMP NULL COMMENT '阅读时间',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP NOT NULL,
    
    INDEX idx_reminder_contract (contract_id),
    INDEX idx_reminder_type (reminder_type),
    INDEX idx_reminder_date (reminder_date),
    INDEX idx_reminder_status (status),
    INDEX idx_reminder_user (user_id),
    
    FOREIGN KEY (contract_id) REFERENCES contracts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同智能提醒表';

-- 4. 插入测试数据（可选）
-- 注意：在生产环境中应该删除或注释掉测试数据

-- 插入示例分析结果（如果存在合同数据）
-- INSERT INTO contract_analyses (contract_id, analysis_type, risk_level, risk_score, findings, recommendations, analyzer_version)
-- SELECT 
--     id as contract_id,
--     'RISK_ANALYSIS' as analysis_type,
--     'LOW' as risk_level,
--     20 as risk_score,
--     '[]' as findings,
--     '["建议定期审查合同条款"]' as recommendations,
--     '1.0.0' as analyzer_version
-- FROM contracts 
-- WHERE id <= 5 AND status = 'ACTIVE'
-- LIMIT 5;

-- 创建合同版本历史（为现有合同创建初始版本）
-- INSERT INTO contract_versions (contract_id, version_number, content, version_description, created_by_user_id, created_by_username, change_type)
-- SELECT 
--     c.id as contract_id,
--     1 as version_number,
--     c.content as content,
--     '初始版本' as version_description,
--     c.drafter_user_id as created_by_user_id,
--     u.username as created_by_username,
--     'INITIAL' as change_type
-- FROM contracts c
-- LEFT JOIN users u ON c.drafter_user_id = u.id
-- WHERE c.content IS NOT NULL
-- LIMIT 10;

-- 5. 数据完整性检查视图（可选）
CREATE OR REPLACE VIEW v_contract_analysis_summary AS
SELECT 
    c.id as contract_id,
    c.contract_number,
    c.contract_name,
    c.status as contract_status,
    COUNT(DISTINCT ca.id) as analysis_count,
    MAX(ca.created_at) as last_analysis_date,
    COUNT(DISTINCT cv.id) as version_count,
    MAX(cv.version_number) as latest_version,
    COUNT(DISTINCT cr.id) as reminder_count,
    COUNT(DISTINCT CASE WHEN cr.is_read = FALSE THEN cr.id END) as unread_reminder_count
FROM contracts c
LEFT JOIN contract_analyses ca ON c.id = ca.contract_id
LEFT JOIN contract_versions cv ON c.id = cv.contract_id
LEFT JOIN contract_reminders cr ON c.id = cr.contract_id
GROUP BY c.id, c.contract_number, c.contract_name, c.status;

-- 6. 性能优化建议
-- 添加复合索引以优化常见查询
ALTER TABLE contract_analyses ADD INDEX idx_contract_type_created (contract_id, analysis_type, created_at DESC);
ALTER TABLE contract_reminders ADD INDEX idx_user_status_date (user_id, status, reminder_date);

-- 7. 权限相关（如果需要）
-- 为智能分析模块添加权限记录
-- INSERT INTO functionalities (functionality_name, description, url_pattern) VALUES
-- ('CONTRACT_ANALYSIS', '合同智能分析', '/contract-analysis/**'),
-- ('CONTRACT_VERSION_COMPARE', '合同版本比对', '/contract-analysis/compare/**'),
-- ('CONTRACT_REMINDER_MANAGE', '合同提醒管理', '/contract-reminders/**');

-- 8. 触发器（自动创建合同版本）
DELIMITER $$

CREATE TRIGGER tr_contract_content_version
AFTER UPDATE ON contracts
FOR EACH ROW
BEGIN
    -- 当合同内容发生变化时，自动创建新版本
    IF OLD.content != NEW.content OR (OLD.content IS NULL AND NEW.content IS NOT NULL) THEN
        INSERT INTO contract_versions (
            contract_id, 
            version_number, 
            content, 
            version_description, 
            created_by_user_id, 
            change_type
        ) VALUES (
            NEW.id,
            (SELECT COALESCE(MAX(version_number), 0) + 1 FROM contract_versions WHERE contract_id = NEW.id),
            NEW.content,
            CONCAT('系统自动创建 - ', DATE_FORMAT(NOW(), '%Y-%m-%d %H:%i:%s')),
            NEW.drafter_user_id,
            'AMENDMENT'
        );
    END IF;
END$$

DELIMITER ;

-- 9. 数据库版本信息
INSERT INTO schema_version (version, description, applied_at) 
VALUES ('3.0', '合同智能分析模块 - 新增分析结果表、版本管理表、智能提醒表', NOW())
ON DUPLICATE KEY UPDATE applied_at = NOW();

-- 完成提示
SELECT 'Contract Analysis Module database schema created successfully!' as message; 