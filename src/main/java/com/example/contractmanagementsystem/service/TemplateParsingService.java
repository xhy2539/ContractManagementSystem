package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.Customer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateParsingService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日");

    /**
     * 解析合同内容中的占位符，使用合同和客户信息进行替换
     * @param content 包含占位符的原始内容
     * @param contract 合同对象
     * @return 解析后的内容
     */
    public String parseContractContent(String content, Contract contract) {
        if (!StringUtils.hasText(content) || contract == null) {
            return content;
        }

        // 先将HTML转换为纯文本格式
        String parsedContent = htmlToPlainText(content);
        Customer customer = contract.getCustomer();

        // 合同基本信息占位符
        parsedContent = replacePlaceholder(parsedContent, "合同编号", contract.getContractNumber());
        parsedContent = replacePlaceholder(parsedContent, "合同名称", contract.getContractName());
        
        // 日期信息
        if (contract.getStartDate() != null) {
            parsedContent = replacePlaceholder(parsedContent, "合同开始日期", contract.getStartDate().format(DATE_FORMATTER));
            parsedContent = replacePlaceholder(parsedContent, "签订日期", contract.getStartDate().format(DATE_FORMATTER));
        }
        if (contract.getEndDate() != null) {
            parsedContent = replacePlaceholder(parsedContent, "合同结束日期", contract.getEndDate().format(DATE_FORMATTER));
        }

        // 常见合同占位符
        parsedContent = replacePlaceholder(parsedContent, "甲方公司名称", "您的公司名称"); // 可以从系统配置中获取
        parsedContent = replacePlaceholder(parsedContent, "甲方住所", "您的公司地址"); // 可以从系统配置中获取
        parsedContent = replacePlaceholder(parsedContent, "甲方联系方式", "您的公司联系方式"); // 可以从系统配置中获取
        parsedContent = replacePlaceholder(parsedContent, "合同份数", "2");
        parsedContent = replacePlaceholder(parsedContent, "甲方执有份数", "1");
        parsedContent = replacePlaceholder(parsedContent, "争议解决方式", "人民法院");
        
        // 产品信息 - 可以根据实际业务需求添加
        parsedContent = replacePlaceholder(parsedContent, "产品名称", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "产品规格型号", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "产品数量", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "产品单位", "件");
        parsedContent = replacePlaceholder(parsedContent, "产品单价", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "产品总价", "待填写");
        
        // 交货信息
        parsedContent = replacePlaceholder(parsedContent, "交货时间", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "交货地点", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "运输方式及费用承担", "待填写");
        
        // 付款信息
        parsedContent = replacePlaceholder(parsedContent, "付款时间", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "付款金额", "待填写");
        parsedContent = replacePlaceholder(parsedContent, "付款方式", "待填写");

        // 客户信息占位符
        if (customer != null) {
            parsedContent = replacePlaceholder(parsedContent, "乙方公司名称", customer.getCustomerName());
            parsedContent = replacePlaceholder(parsedContent, "客户名称", customer.getCustomerName());
            parsedContent = replacePlaceholder(parsedContent, "客户编号", customer.getCustomerNumber());
            parsedContent = replacePlaceholder(parsedContent, "乙方住所", customer.getAddress());
            parsedContent = replacePlaceholder(parsedContent, "客户地址", customer.getAddress());
            parsedContent = replacePlaceholder(parsedContent, "乙方联系方式", customer.getPhoneNumber());
            parsedContent = replacePlaceholder(parsedContent, "客户联系方式", customer.getPhoneNumber());
            parsedContent = replacePlaceholder(parsedContent, "客户电话", customer.getPhoneNumber());
            parsedContent = replacePlaceholder(parsedContent, "客户邮箱", customer.getEmail());
        }

        // 起草人信息
        if (contract.getDrafter() != null) {
            parsedContent = replacePlaceholder(parsedContent, "起草人", contract.getDrafter().getUsername());
            if (StringUtils.hasText(contract.getDrafter().getRealName())) {
                parsedContent = replacePlaceholder(parsedContent, "起草人姓名", contract.getDrafter().getRealName());
            }
        }

        // 合同状态
        if (contract.getStatus() != null) {
            parsedContent = replacePlaceholder(parsedContent, "合同状态", contract.getStatus().getDescription());
        }

        // 创建时间
        if (contract.getCreatedAt() != null) {
            parsedContent = replacePlaceholder(parsedContent, "创建时间", 
                contract.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")));
        }

        return parsedContent;
    }

    /**
     * 替换单个占位符
     * @param content 内容
     * @param placeholder 占位符名称（不包含大括号）
     * @param value 替换值
     * @return 替换后的内容
     */
    private String replacePlaceholder(String content, String placeholder, String value) {
        if (!StringUtils.hasText(content) || !StringUtils.hasText(placeholder)) {
            return content;
        }
        
        String actualValue = StringUtils.hasText(value) ? value : "";
        
        // 使用正则表达式匹配 {{占位符}} 格式
        String regex = "\\{\\{\\s*" + Pattern.quote(placeholder) + "\\s*\\}\\}";
        return content.replaceAll(regex, Matcher.quoteReplacement(actualValue));
    }

    /**
     * 检查内容是否包含未解析的占位符
     * @param content 内容
     * @return 如果包含未解析的占位符返回true
     */
    public boolean hasUnresolvedPlaceholders(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        Pattern pattern = Pattern.compile("\\{\\{[^}]+\\}\\}");
        return pattern.matcher(content).find();
    }

    /**
     * 获取内容中所有未解析的占位符
     * @param content 内容
     * @return 未解析的占位符列表
     */
    public java.util.List<String> getUnresolvedPlaceholders(String content) {
        java.util.List<String> placeholders = new java.util.ArrayList<>();
        if (!StringUtils.hasText(content)) {
            return placeholders;
        }
        
        Pattern pattern = Pattern.compile("\\{\\{([^}]+)\\}\\}");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String placeholder = matcher.group(1).trim();
            if (!placeholders.contains(placeholder)) {
                placeholders.add(placeholder);
            }
        }
        return placeholders;
    }

    /**
     * 将HTML内容转换为纯文本格式
     * @param htmlContent HTML内容
     * @return 纯文本内容
     */
    private String htmlToPlainText(String htmlContent) {
        if (!StringUtils.hasText(htmlContent)) {
            return htmlContent;
        }

        String plainText = htmlContent;

        // 处理换行标签 - 先处理<br>标签
        plainText = plainText.replaceAll("<br[^>]*>", "\n");
        plainText = plainText.replaceAll("<br/>", "\n");
        plainText = plainText.replaceAll("<br>", "\n");

        // 处理段落标签 - 段落之间添加双换行
        plainText = plainText.replaceAll("<p[^>]*>", "");
        plainText = plainText.replaceAll("</p>", "\n\n");

        // 处理HTML实体
        plainText = plainText.replaceAll("&nbsp;", " ");
        plainText = plainText.replaceAll("&lt;", "<");
        plainText = plainText.replaceAll("&gt;", ">");
        plainText = plainText.replaceAll("&amp;", "&");
        plainText = plainText.replaceAll("&quot;", "\"");
        plainText = plainText.replaceAll("&#39;", "'");

        // 移除其他HTML标签
        plainText = plainText.replaceAll("<[^>]+>", "");

        // 清理多余的空白和空行
        plainText = plainText.replaceAll("\\n{3,}", "\n\n"); // 多个空行合并为两个
        plainText = plainText.replaceAll("^\\s+", ""); // 删除开头的空白
        plainText = plainText.replaceAll("\\s+$", ""); // 删除结尾的空白
        
        // 处理连续的空格
        plainText = plainText.replaceAll("[ ]{2,}", " "); // 多个空格合并为一个

        return plainText;
    }
} 