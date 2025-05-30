package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank; // 确保导入
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.Set;

@Getter
public class UserCreationRequest {

    // Getters and Setters
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 40, message = "用户名长度必须在4到40之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 120, message = "密码长度必须在6到120之间")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @NotBlank(message = "邮箱不能为空") // <--- 添加此行注解
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 50, message = "真实姓名长度不能超过50")
    private String realName;

    private Set<String> roleNames;

    public void setUsername(String username) { this.username = username; }

    public void setPassword(String password) { this.password = password; }

    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public void setEmail(String email) { this.email = email; }

    public void setRealName(String realName) { this.realName = realName; }

    public void setRoleNames(Set<String> roleNames) { this.roleNames = roleNames; }
}