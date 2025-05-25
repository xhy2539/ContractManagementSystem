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

@RestController
@RequestMapping("/api/system/functionalities")
@PreAuthorize("hasRole('ROLE_ADMIN')")
public class FunctionalityManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public FunctionalityManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('FUNC_CREATE')") // 使用功能编号
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
    @PreAuthorize("hasAuthority('FUNC_VIEW_LIST')") // 使用功能编号
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
    @PreAuthorize("hasAuthority('FUNC_VIEW_LIST') or hasAuthority('FUNC_EDIT')") // 使用功能编号
    public ResponseEntity<Functionality> getFunctionalityById(@PathVariable Long id) {
        Functionality functionality = systemManagementService.getFunctionalityById(id);
        return ResponseEntity.ok(functionality);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FUNC_EDIT')") // 使用功能编号
    public ResponseEntity<Functionality> updateFunctionality(@PathVariable Long id, @Valid @RequestBody FunctionalityUpdateRequest funcUpdateRequest) {
        Functionality functionalityDetailsToUpdate = new Functionality();
        // 注意：通常更新时不应该允许修改唯一标识 num，除非业务确实需要。
        // 如果 num 是创建后固定的，则不应在 UpdateRequest 中包含它，或在服务层忽略它。
        // 此处假设 num 也是可更新的，但要小心其唯一性约束。
        functionalityDetailsToUpdate.setNum(funcUpdateRequest.getNum());
        functionalityDetailsToUpdate.setName(funcUpdateRequest.getName());
        functionalityDetailsToUpdate.setUrl(funcUpdateRequest.getUrl());
        functionalityDetailsToUpdate.setDescription(funcUpdateRequest.getDescription());

        Functionality updatedFunctionality = systemManagementService.updateFunctionality(id, functionalityDetailsToUpdate);
        return ResponseEntity.ok(updatedFunctionality);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FUNC_DELETE')") // 使用功能编号
    public ResponseEntity<Void> deleteFunctionality(@PathVariable Long id) {
        systemManagementService.deleteFunctionality(id);
        return ResponseEntity.noContent().build();
    }
}