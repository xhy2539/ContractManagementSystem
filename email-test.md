# 邮件功能测试指南

## 1. 邮件配置检查
确认 `application.properties` 中的邮件配置：
```properties
spring.mail.host=smtp.qq.com
spring.mail.port=465
spring.mail.username=hyxiong2539@qq.com
spring.mail.password=yemmiewxuszajbid
```

## 2. 测试邮件发送
在应用启动后，可以通过以下方式测试：

### 手动触发邮件测试
```java
// 在Controller中添加测试端点
@GetMapping("/test/email")
public String testEmail() {
    try {
        ContractReminder reminder = new ContractReminder();
        // 设置测试数据...
        contractReminderService.sendReminderEmail(reminder);
        return "邮件发送成功";
    } catch (Exception e) {
        return "邮件发送失败: " + e.getMessage();
    }
}
```

### 测试定时任务
```sql
-- 创建即将到期的合同数据
UPDATE contracts 
SET end_date = DATE_ADD(CURDATE(), INTERVAL 7 DAY) 
WHERE contract_number = 'TEST-2024-001';
```

然后手动触发定时任务或等待自动执行。

## 3. 邮件模板测试
- ✅ 邮件HTML格式正确
- ✅ 变量替换正常
- ✅ 链接可点击
- ✅ 移动端兼容性

## 4. 常见问题排查
1. **邮件发送失败**：检查网络连接和邮箱配置
2. **模板渲染错误**：检查Thymeleaf语法
3. **收不到邮件**：检查垃圾邮件文件夹 