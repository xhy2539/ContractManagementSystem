package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class FunctionalityCreationRequest {

    @NotBlank(message = "功能编号不能为空")
    @Size(max = 10, message = "功能编号长度不能超过10")
    private String num;

    @NotBlank(message = "功能名称不能为空")
    @Size(max = 50, message = "功能名称长度不能超过50")
    private String name;

    @Size(max = 100, message = "URL长度不能超过100")
    private String url;

    @Size(max = 100, message = "描述长度不能超过100")
    private String description;

    // Getters and Setters
    public String getNum() {
        return num;
    }

    public void setNum(String num) {
        this.num = num;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}