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

import java.util.List;

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
    public ResponseEntity<Functionality> createFunctionality(@Valid @RequestBody FunctionalityCreationRequest funcRequest) {
        Functionality newFunctionality = new Functionality();
        newFunctionality.setNum(funcRequest.getNum());
        newFunctionality.setName(funcRequest.getName());
        newFunctionality.setUrl(funcRequest.getUrl());
        newFunctionality.setDescription(funcRequest.getDescription());

        Functionality createdFunctionality = systemManagementService.createFunctionality(newFunctionality);
        return new ResponseEntity<>(createdFunctionality, HttpStatus.CREATED);
    }

    /**
     * 获取所有功能列表（支持分页和搜索）
     * @param pageable 分页和排序参数
     * @param numSearch 可选的功能编号搜索关键词
     * @param nameSearch 可选的功能名称搜索关键词
     * @param descriptionSearch 可选的功能描述搜索关键词
     * @return 功能分页数据
     */
    @GetMapping
    public ResponseEntity<Page<Functionality>> getAllFunctionalities(
            // 修改这里的 sort 参数
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
    public ResponseEntity<Functionality> getFunctionalityById(@PathVariable Long id) {
        Functionality functionality = systemManagementService.getFunctionalityById(id);
        return ResponseEntity.ok(functionality);
    }

    @PutMapping("/{id}")
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
    public ResponseEntity<Void> deleteFunctionality(@PathVariable Long id) {
        systemManagementService.deleteFunctionality(id);
        return ResponseEntity.noContent().build();
    }
}