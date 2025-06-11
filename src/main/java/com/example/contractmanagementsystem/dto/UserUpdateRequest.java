package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UserUpdateRequest {

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    @Size(max = 50, message = "真实姓名长度不能超过50") // 新增 realName 字段
    private String realName;

    // Getters and Setters
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Boolean isEnabled() { return enabled; } // 注意 getter 的命名
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public String getRealName() { return realName; } // Getter for realName
    public void setRealName(String realName) { this.realName = realName; } // Setter for realName
}