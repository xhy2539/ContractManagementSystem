-- 测试数据插入脚本
-- 用于测试智能分析功能

-- 1. 插入测试客户
INSERT INTO customers (customer_name, contact_person, contact_phone, contact_email, address) 
VALUES ('测试科技有限公司', '张三', '13800138000', 'zhangsan@test.com', '北京市朝阳区测试大街123号');

-- 2. 插入测试用户（如果不存在）
INSERT INTO users (username, password, email, real_name, enabled, created_at, updated_at) 
SELECT 'testuser', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8imdIMNUDgdOXEfJhMnj2JR5EWHki', 'test@example.com', '测试用户', true, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'testuser');

-- 3. 插入测试合同
INSERT INTO contracts (
    contract_number, contract_name, contract_type, party_a, party_b, 
    start_date, end_date, contract_amount, payment_method, 
    content, drafter_id, contract_status, created_at, updated_at
) VALUES (
    'TEST-2024-001', 
    '软件开发服务合同', 
    '服务合同', 
    '甲方测试公司', 
    '乙方开发公司',
    CURDATE(), 
    DATE_ADD(CURDATE(), INTERVAL 365 DAY), 
    100000.00, 
    '分期付款',
    '本合同是关于软件开发服务的合同。甲方委托乙方开发一套合同管理系统。合同期限为一年，总金额为10万元。支付方式为分期付款。违约责任条款：如一方违约，需承担连带责任。争议解决：双方如有争议，应友好协商解决。保密条款：双方应对商业机密严格保密。知识产权归甲方所有。不可抗力条款已包含。合同终止条件已明确。付款方式为银行转账。交付条件为验收合格后交付。验收标准按照需求文档执行。适用中华人民共和国法律。',
    (SELECT id FROM users WHERE username = 'testuser' LIMIT 1),
    'ACTIVE',
    NOW(),
    NOW()
);

-- 4. 插入另一个测试合同（缺少关键条款的）
INSERT INTO contracts (
    contract_number, contract_name, contract_type, party_a, party_b, 
    start_date, end_date, contract_amount, payment_method, 
    content, drafter_id, contract_status, created_at, updated_at
) VALUES (
    'TEST-2024-002', 
    '简单采购合同', 
    '采购合同', 
    '采购方公司', 
    '供应商公司',
    CURDATE(), 
    DATE_ADD(CURDATE(), INTERVAL 30 DAY), 
    50000.00, 
    '一次性付款',
    '本合同是关于设备采购的简单合同。采购设备包括电脑、打印机等办公设备。总价值5万元。',
    (SELECT id FROM users WHERE username = 'testuser' LIMIT 1),
    'ACTIVE',
    NOW(),
    NOW()
);

-- 5. 插入测试合同版本
INSERT INTO contract_versions (
    contract_id, version_number, content, change_type, version_description,
    created_by_username, created_at
) VALUES (
    (SELECT id FROM contracts WHERE contract_number = 'TEST-2024-001' LIMIT 1),
    1,
    '本合同是关于软件开发服务的合同。甲方委托乙方开发一套合同管理系统。',
    'CREATION',
    '初始版本创建',
    'testuser',
    NOW()
);

SELECT '测试数据插入完成' AS message; 