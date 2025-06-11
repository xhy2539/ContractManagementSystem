package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank; // 确保导入
import jakarta.validation.constraints.Size;
import java.util.Set;

public class CustomerCreationRequest {
    @NotBlank(message = "客户名不能为空")
    @Size(min = 4, max = 100, message = "客户名长度必须在4到100之间")
    private String customerName;

    @NotBlank(message = "编号不能为空")
    @Size(max = 20, message = "编号长度必须在20之间")
    private String customerNumber;

    @NotBlank(message = "确认电话码不能为空")
    @Size(max = 20, message = "电话长度必须在20之间")
    private String phoneNumber;

    @NotBlank(message = "邮箱不能为空") // <--- 添加此行注解
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100")
    private String email;

    private String address;

    // Getters and Setters

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerNumber() {
        return customerNumber;
    }

    public void setCustomerNumber(String customerNumber) {
        this.customerNumber = customerNumber;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
