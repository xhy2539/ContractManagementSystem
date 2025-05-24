package com.example.contractmanagementsystem.dto;

import jakarta.validation.constraints.Size; // 确保导入
import jakarta.validation.constraints.NotBlank; // 如果name在更新时也是必须的
import java.util.Set;

public class RoleUpdateRequest {

    @NotBlank(message = "角色名称不能为空") // 通常角色名在更新时也是必须的
    @Size(max = 40, message = "角色名称长度不能超过40")
    private String name;

    @Size(max = 100, message = "描述长度不能超过100")
    private String description;

    private Set<String> functionalityNames;

    // Getters and Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Set<String> getFunctionalityNames() { return functionalityNames; }
    public void setFunctionalityNames(Set<String> functionalityNames) { this.functionalityNames = functionalityNames; }
}