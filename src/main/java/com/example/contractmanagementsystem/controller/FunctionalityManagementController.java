package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.dto.FunctionalityCreationRequest;
import com.example.contractmanagementsystem.dto.FunctionalityUpdateRequest;
import com.example.contractmanagementsystem.entity.Functionality;
import com.example.contractmanagementsystem.service.SystemManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system/functionalities")
@PreAuthorize("hasRole('ROLE_ADMIN')") // 假设只有管理员可以管理功能
public class FunctionalityManagementController {

    private final SystemManagementService systemManagementService;

    @Autowired
    public FunctionalityManagementController(SystemManagementService systemManagementService) {
        this.systemManagementService = systemManagementService;
    }

    // POST /api/system/functionalities - 创建新功能
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

    // GET /api/system/functionalities - 获取所有功能列表
    @GetMapping
    public ResponseEntity<List<Functionality>> getAllFunctionalities() {
        List<Functionality> functionalities = systemManagementService.getAllFunctionalities();
        return ResponseEntity.ok(functionalities);
    }

    // GET /api/system/functionalities/{id} - 根据ID获取功能
    @GetMapping("/{id}")
    public ResponseEntity<Functionality> getFunctionalityById(@PathVariable Long id) {
        Functionality functionality = systemManagementService.getFunctionalityById(id);
        return ResponseEntity.ok(functionality);
    }

    // PUT /api/system/functionalities/{id} - 更新功能信息
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

    // DELETE /api/system/functionalities/{id} - 删除功能
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFunctionality(@PathVariable Long id) {
        systemManagementService.deleteFunctionality(id);
        return ResponseEntity.noContent().build(); // 204 No Content
    }
}