package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.RoleCreationRequest;
import com.example.contractmanagementsystem.dto.RoleUpdateRequest;
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/roles")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class RoleManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public RoleManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    public ResponseEntity<Role> createRole(@Valid @RequestBody RoleCreationRequest roleRequest) {
        Role newRole = new Role();
        newRole.setName(roleRequest.getName());
        newRole.setDescription(roleRequest.getDescription());
        // 注意：SystemManagementService.createRole的第二个参数是功能名称集合
        Role createdRole = systemManagementService.createRole(newRole, roleRequest.getFunctionalityNames());
        return new ResponseEntity<>(createdRole, HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Role>> getAllRoles() {
        List<Role> roles = systemManagementService.getAllRoles();
        return ResponseEntity.ok(roles);
    }

    @GetMapping("/{roleId}")
    public ResponseEntity<Role> getRoleById(@PathVariable Integer roleId) {
        Role role = systemManagementService.findRoleById(roleId);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{roleId}")
    public ResponseEntity<Role> updateRole(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateRequest roleRequest) {
        Role roleDetailsToUpdate = new Role();
        roleDetailsToUpdate.setName(roleRequest.getName());
        roleDetailsToUpdate.setDescription(roleRequest.getDescription());
        // 注意：SystemManagementService.updateRole的第三个参数是功能名称集合
        Role updatedRole = systemManagementService.updateRole(roleId, roleDetailsToUpdate, roleRequest.getFunctionalityNames());
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{roleId}")
    public ResponseEntity<Void> deleteRole(@PathVariable Integer roleId) {
        systemManagementService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}