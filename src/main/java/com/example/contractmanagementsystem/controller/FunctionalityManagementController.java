package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.FunctionalityCreationRequest;
import com.example.contractmanagementsystem.dto.FunctionalityUpdateRequest;
import com.example.contractmanagementsystem.entity.Functionality;
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

// import java.util.List; // 如果 List 未被使用，可以移除

@RestController
@RequestMapping("/api/system/functionalities")
// 类级别的 @PreAuthorize("hasRole('ROLE_ADMIN')") 保证了只有管理员角色能访问这个控制器下的所有API。
// 我们将在方法级别添加更细致的 hasAuthority() 检查，这仍然是在 ROLE_ADMIN 的基础上进行的。
// 如果将来有其他角色也需要某些功能管理权限，可以调整这里的逻辑，例如移除类级别的注解，
// 或者在方法级别使用 or 逻辑，如 @PreAuthorize("hasRole('ROLE_ADMIN') or hasAuthority('某个功能')")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class FunctionalityManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public FunctionalityManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    // 要求用户拥有 "新增功能" 这个功能权限
    @PreAuthorize("hasAuthority('新增功能')")
    public ResponseEntity<Functionality> createFunctionality(@Valid @RequestBody FunctionalityCreationRequest funcRequest) {
        Functionality newFunctionality = new Functionality();
        newFunctionality.setNum(funcRequest.getNum());
        newFunctionality.setName(funcRequest.getName());
        newFunctionality.setUrl(funcRequest.getUrl());
        newFunctionality.setDescription(funcRequest.getDescription());

        Functionality createdFunctionality = systemManagementService.createFunctionality(newFunctionality);
        return new ResponseEntity<>(createdFunctionality, HttpStatus.CREATED);
    }

    @GetMapping
    // 要求用户拥有 "查看功能列表" 这个功能权限
    @PreAuthorize("hasAuthority('查看功能列表')")
    public ResponseEntity<Page<Functionality>> getAllFunctionalities(
            @PageableDefault(size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String numSearch,
            @RequestParam(required = false) String nameSearch,
            @RequestParam(required = false) String descriptionSearch) {

        Page<Functionality> functionalitiesPage;
        if ((numSearch != null && !numSearch.isEmpty()) ||
                (nameSearch != null && !nameSearch.isEmpty()) ||
                (descriptionSearch != null && !descriptionSearch.isEmpty())) {
            functionalitiesPage = systemManagementService.searchFunctionalities(numSearch, nameSearch, descriptionSearch, pageable);
        } else {
            functionalitiesPage = systemManagementService.getAllFunctionalities(pageable);
        }
        return ResponseEntity.ok(functionalitiesPage);
    }

    @GetMapping("/{id}")
    // 查看单个功能详情。通常，如果用户有权查看列表或修改条目，他们也应该能查看单个条目的详情。
    // 这里假设拥有 "查看功能列表" 或 "修改功能信息" 权限的用户可以查看详情。
    @PreAuthorize("hasAuthority('查看功能列表') or hasAuthority('修改功能信息')")
    public ResponseEntity<Functionality> getFunctionalityById(@PathVariable Long id) {
        Functionality functionality = systemManagementService.getFunctionalityById(id);
        return ResponseEntity.ok(functionality);
    }

    @PutMapping("/{id}")
    // 要求用户拥有 "修改功能信息" 这个功能权限
    @PreAuthorize("hasAuthority('修改功能信息')")
    public ResponseEntity<Functionality> updateFunctionality(@PathVariable Long id, @Valid @RequestBody FunctionalityUpdateRequest funcUpdateRequest) {
        Functionality functionalityDetailsToUpdate = new Functionality();
        functionalityDetailsToUpdate.setNum(funcUpdateRequest.getNum());
        functionalityDetailsToUpdate.setName(funcUpdateRequest.getName());
        functionalityDetailsToUpdate.setUrl(funcUpdateRequest.getUrl());
        functionalityDetailsToUpdate.setDescription(funcUpdateRequest.getDescription());

        Functionality updatedFunctionality = systemManagementService.updateFunctionality(id, functionalityDetailsToUpdate);
        return ResponseEntity.ok(updatedFunctionality);
    }

    @DeleteMapping("/{id}")
    // 要求用户拥有 "删除功能" 这个功能权限
    @PreAuthorize("hasAuthority('删除功能')")
    public ResponseEntity<Void> deleteFunctionality(@PathVariable Long id) {
        systemManagementService.deleteFunctionality(id);
        return ResponseEntity.noContent().build();
    }
}