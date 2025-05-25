package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.RoleCreationRequest;
import com.example.contractmanagementsystem.dto.RoleUpdateRequest;
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page; // 新增导入
import org.springframework.data.domain.Pageable; // 新增导入
import org.springframework.data.domain.Sort; // 新增导入
import org.springframework.data.web.PageableDefault; // 新增导入
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// import java.util.List; // List的导入可以移除，因为主要返回 Page<Role>

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

    /**
     * 获取所有角色列表（支持分页和搜索）
     * @param pageable 分页和排序参数 (例如 ?page=0&size=10&sort=name,asc)
     * @param nameSearch 可选的角色名称搜索关键词
     * @param descriptionSearch 可选的角色描述搜索关键词
     * @return 角色分页数据
     */
    @GetMapping
    public ResponseEntity<Page<Role>> getAllRoles(
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String nameSearch,
            @RequestParam(required = false) String descriptionSearch) {
        Page<Role> rolesPage;
        if ((nameSearch != null && !nameSearch.isEmpty()) || (descriptionSearch != null && !descriptionSearch.isEmpty())) {
            rolesPage = systemManagementService.searchRoles(nameSearch, descriptionSearch, pageable);
        } else {
            rolesPage = systemManagementService.getAllRoles(pageable); // 调用支持分页的Service方法
        }
        return ResponseEntity.ok(rolesPage);
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