package com.example.contractmanagementsystem.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 40, message = "用户名长度必须在4到40个字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 120, message = "密码长度必须在6到120个字符之间")
    private String password;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    // 这个字段将用于指定用户是想注册为普通用户还是合同操作员
    @NotBlank(message = "角色类型不能为空")
    private String roleType;
}