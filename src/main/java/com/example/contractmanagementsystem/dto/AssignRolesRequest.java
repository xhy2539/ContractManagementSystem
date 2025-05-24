package com.example.contractmanagementsystem.dto;

import java.util.Set;

public class AssignRolesRequest {
    private Set<String> roleNames;

    // Getters and Setters
    public Set<String> getRoleNames() { return roleNames; }
    public void setRoleNames(Set<String> roleNames) { this.roleNames = roleNames; }
}