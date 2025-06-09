-- 为智能分析模块添加功能权限配置
-- V3.1 版本迁移：插入智能分析相关功能

-- 1. 插入智能分析相关功能
INSERT INTO functionalities (num, name, url, description) VALUES
('ANALYSIS_VIEW', '智能分析查看', '/contract-analysis', '查看合同智能分析主页'),
('ANALYSIS_RISK_PERFORM', '执行风险分析', '/contract-analysis/risk-analysis/**', '执行合同风险分析'),
('ANALYSIS_CLAUSE_PERFORM', '执行条款检查', '/contract-analysis/clause-check/**', '执行合同条款检查'),
('ANALYSIS_COMPLIANCE_PERFORM', '执行合规检查', '/contract-analysis/compliance-check/**', '执行合同合规检查'),
('ANALYSIS_FULL_PERFORM', '执行全面分析', '/contract-analysis/full-analysis/**', '执行合同全面分析'),
('ANALYSIS_VERSION_COMPARE', '版本比对分析', '/contract-analysis/version-compare/**', '执行合同版本比对'),
('ANALYSIS_HISTORY_VIEW', '查看分析历史', '/contract-analysis/history/**', '查看合同分析历史记录'),
('ANALYSIS_STATISTICS_VIEW', '查看风险统计', '/contract-analysis/statistics/**', '查看风险等级统计'),
('ANALYSIS_HIGH_RISK_VIEW', '查看高风险合同', '/contract-analysis/high-risk/**', '查看高风险合同列表'),
('ANALYSIS_BATCH_PERFORM', '批量分析', '/contract-analysis/batch/**', '执行批量合同分析'),
('REMINDER_VIEW', '查看提醒', '/contract-analysis/reminders/**', '查看合同提醒'),
('REMINDER_CREATE', '创建提醒', '/contract-analysis/reminders/create/**', '创建合同提醒'),
('REMINDER_MANAGE', '管理提醒', '/contract-analysis/reminders/manage/**', '管理合同提醒设置');

-- 2. 为管理员角色分配智能分析功能权限
-- 获取管理员角色ID和新功能ID，然后建立关联关系
INSERT INTO role_functionalities (role_id, functionality_id)
SELECT r.id, f.id
FROM roles r, functionalities f
WHERE r.name = 'ROLE_ADMIN'
AND f.num IN (
    'ANALYSIS_VIEW',
    'ANALYSIS_RISK_PERFORM',
    'ANALYSIS_CLAUSE_PERFORM',
    'ANALYSIS_COMPLIANCE_PERFORM',
    'ANALYSIS_FULL_PERFORM',
    'ANALYSIS_VERSION_COMPARE',
    'ANALYSIS_HISTORY_VIEW',
    'ANALYSIS_STATISTICS_VIEW',
    'ANALYSIS_HIGH_RISK_VIEW',
    'ANALYSIS_BATCH_PERFORM',
    'REMINDER_VIEW',
    'REMINDER_CREATE',
    'REMINDER_MANAGE'
);

-- 3. 为合同操作员角色分配基础智能分析功能权限（如果存在该角色）
INSERT INTO role_functionalities (role_id, functionality_id)
SELECT r.id, f.id
FROM roles r, functionalities f
WHERE r.name = 'ROLE_CONTRACT_OPERATOR'
AND f.num IN (
    'ANALYSIS_VIEW',
    'ANALYSIS_RISK_PERFORM',
    'ANALYSIS_CLAUSE_PERFORM',
    'ANALYSIS_COMPLIANCE_PERFORM',
    'ANALYSIS_VERSION_COMPARE',
    'ANALYSIS_HISTORY_VIEW',
    'ANALYSIS_STATISTICS_VIEW',
    'REMINDER_VIEW'
)
AND EXISTS (SELECT 1 FROM roles WHERE name = 'ROLE_CONTRACT_OPERATOR');

-- 4. 创建智能分析专用角色（可选）
INSERT INTO roles (name, description) VALUES ('ROLE_ANALYSIS_SPECIALIST', '合同智能分析专员');

-- 为智能分析专员角色分配相关权限
INSERT INTO role_functionalities (role_id, functionality_id)
SELECT r.id, f.id
FROM roles r, functionalities f
WHERE r.name = 'ROLE_ANALYSIS_SPECIALIST'
AND f.num IN (
    'ANALYSIS_VIEW',
    'ANALYSIS_RISK_PERFORM',
    'ANALYSIS_CLAUSE_PERFORM',
    'ANALYSIS_COMPLIANCE_PERFORM',
    'ANALYSIS_FULL_PERFORM',
    'ANALYSIS_VERSION_COMPARE',
    'ANALYSIS_HISTORY_VIEW',
    'ANALYSIS_STATISTICS_VIEW',
    'ANALYSIS_HIGH_RISK_VIEW',
    'ANALYSIS_BATCH_PERFORM',
    'REMINDER_VIEW',
    'REMINDER_CREATE',
    'REMINDER_MANAGE',
    'CON_VIEW_MY'  -- 查看合同的基础权限
);

-- 5. 更新现有审计日志记录
INSERT INTO audit_logs (username, action, details, ip_address, user_agent, created_at)
VALUES ('SYSTEM', 'SETUP_ANALYSIS_FEATURES', '系统初始化：配置智能分析模块功能权限', '127.0.0.1', 'Migration Script', NOW()); 