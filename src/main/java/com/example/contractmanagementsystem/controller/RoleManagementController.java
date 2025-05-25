package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.RoleCreationRequest;
import com.example.contractmanagementsystem.dto.RoleUpdateRequest;
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    @PreAuthorize("hasAuthority('ROLE_CREATE')") // 使用功能编号
    public ResponseEntity<Role> createRole(@Valid @RequestBody RoleCreationRequest roleRequest) {
        Role newRole = new Role();
        newRole.setName(roleRequest.getName());
        newRole.setDescription(roleRequest.getDescription());
        // 注意：此处调用的是 createRoleWithFunctionalityNums，确保其在服务层已相应修改
        Role createdRole = systemManagementService.createRoleWithFunctionalityNums(newRole, roleRequest.getFunctionalityNums());
        return new ResponseEntity<>(createdRole, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW_LIST')") // 使用功能编号
    public ResponseEntity<Page<Role>> getAllRoles(
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String nameSearch,
            @RequestParam(required = false) String descriptionSearch) {
        Page<Role> rolesPage;
        if ((nameSearch != null && !nameSearch.isEmpty()) || (descriptionSearch != null && !descriptionSearch.isEmpty())) {
            rolesPage = systemManagementService.searchRoles(nameSearch, descriptionSearch, pageable);
        } else {
            rolesPage = systemManagementService.getAllRoles(pageable);
        }
        return ResponseEntity.ok(rolesPage);
    }

    @GetMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_VIEW_LIST') or hasAuthority('ROLE_EDIT')") // 使用功能编号
    public ResponseEntity<Role> getRoleById(@PathVariable Integer roleId) {
        Role role = systemManagementService.findRoleById(roleId);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_EDIT')") // 使用功能编号
    public ResponseEntity<Role> updateRole(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateRequest roleRequest) {
        Role roleDetailsToUpdate = new Role();
        roleDetailsToUpdate.setName(roleRequest.getName());
        roleDetailsToUpdate.setDescription(roleRequest.getDescription());
        // 注意：此处调用的是 updateRoleWithFunctionalityNums，确保其在服务层已相应修改
        Role updatedRole = systemManagementService.updateRoleWithFunctionalityNums(roleId, roleDetailsToUpdate, roleRequest.getFunctionalityNums());
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{roleId}")
    @PreAuthorize("hasAuthority('ROLE_DELETE')") // 使用功能编号
    public ResponseEntity<Void> deleteRole(@PathVariable Integer roleId) {
        systemManagementService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}