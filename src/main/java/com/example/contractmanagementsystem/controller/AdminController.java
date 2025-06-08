package com.example.contractmanagementsystem.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasRole('ROLE_ADMIN')") // 确保只有管理员可以访问这些页面
public class AdminController {

    // 审计日志页面 (为保持一致性，从LoginController移至此处)
    @GetMapping("/audit-logs")
    public String auditLogsPage() {
        return "admin/audit-logs";
    }

    @GetMapping("/users")
    public String userManagementPage() {
        return "admin/user-management"; // 新的HTML页面
    }

    @GetMapping("/roles")
    public String roleManagementPage() {
        return "admin/role-management"; // 新的HTML页面
    }

    @GetMapping("/functionalities")
    public String functionalityManagementPage() {
        return "admin/functionality-management"; // 新的HTML页面
    }

    @GetMapping("/contract-assignments")
    public String contractAssignmentPage() {
        return "admin/contract-assignment"; // 新的HTML页面
    }

//    @GetMapping("/templates") // 新增：模板管理页面
//    public String templateManagementPage() {
//        return "admin/template-management";
//    }

}