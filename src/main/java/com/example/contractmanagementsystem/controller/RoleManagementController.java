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

// import java.util.List; // List的导入可以移除，因为主要返回 Page<Role>

@RestController
@RequestMapping("/api/system/roles")
// 类级别的 @PreAuthorize 确保只有 ROLE_ADMIN 可以访问这些API
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class RoleManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public RoleManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    // 根据您的功能列表，没有直接的 "新增角色" 功能名称分配给ROLE_ADMIN。
    // 通常，如果管理员有权修改和删除角色，他们也应该有权创建角色。
    // 如果您希望对此进行更细粒度的控制，您需要：
    // 1. 在数据库中创建一个名为 "新增角色" (或类似) 的 Functionality。
    // 2. 通过HTTP脚本或直接在DataInitializer中将其分配给 ROLE_ADMIN。
    // 3. 然后在这里使用 @PreAuthorize("hasAuthority('新增角色')")。
    // 目前，由于类级别有 hasRole('ROLE_ADMIN')，此操作对管理员是允许的。
    // 如果要添加细粒度权限，假设您已创建并分配了 "新增角色" 功能：
    // @PreAuthorize("hasAuthority('新增角色')") // 示例：如果未来添加此功能权限
    public ResponseEntity<Role> createRole(@Valid @RequestBody RoleCreationRequest roleRequest) {
        Role newRole = new Role();
        newRole.setName(roleRequest.getName());
        newRole.setDescription(roleRequest.getDescription());
        Role createdRole = systemManagementService.createRole(newRole, roleRequest.getFunctionalityNames());
        return new ResponseEntity<>(createdRole, HttpStatus.CREATED);
    }

    @GetMapping
    // 与 createRole 类似，您的功能列表中没有 "查看角色列表"。
    // 管理员通过 hasRole('ROLE_ADMIN') 可以访问。
    // 如果要添加细粒度权限，假设您已创建并分配了 "查看角色列表" 功能：
    // @PreAuthorize("hasAuthority('查看角色列表')") // 示例：如果未来添加此功能权限
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
    // 查看单个角色详情。
    // 权限逻辑可以类似于查看功能列表或修改角色信息。
    // @PreAuthorize("hasAuthority('查看角色列表') or hasAuthority('修改角色信息')") // 示例
    public ResponseEntity<Role> getRoleById(@PathVariable Integer roleId) {
        Role role = systemManagementService.findRoleById(roleId);
        return ResponseEntity.ok(role);
    }

    @PutMapping("/{roleId}")
    // 要求用户拥有 "修改角色信息" 这个功能权限
    @PreAuthorize("hasAuthority('修改角色信息')")
    public ResponseEntity<Role> updateRole(@PathVariable Integer roleId, @Valid @RequestBody RoleUpdateRequest roleRequest) {
        Role roleDetailsToUpdate = new Role();
        roleDetailsToUpdate.setName(roleRequest.getName());
        roleDetailsToUpdate.setDescription(roleRequest.getDescription());
        Role updatedRole = systemManagementService.updateRole(roleId, roleDetailsToUpdate, roleRequest.getFunctionalityNames());
        return ResponseEntity.ok(updatedRole);
    }

    @DeleteMapping("/{roleId}")
    // 要求用户拥有 "删除角色" 这个功能权限
    @PreAuthorize("hasAuthority('删除角色')")
    public ResponseEntity<Void> deleteRole(@PathVariable Integer roleId) {
        systemManagementService.deleteRole(roleId);
        return ResponseEntity.noContent().build();
    }
}