package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public class UserCreationRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 40, message = "用户名长度必须在4到40之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 120, message = "密码长度必须在6到120之间")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @Size(max = 50, message = "真实姓名长度不能超过50") // 新增 realName 字段
    private String realName;

    private Set<String> roleNames;

    // Getters and Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRealName() { return realName; } // Getter for realName
    public void setRealName(String realName) { this.realName = realName; } // Setter for realName

    public Set<String> getRoleNames() { return roleNames; }
    public void setRoleNames(Set<String> roleNames) { this.roleNames = roleNames; }
}