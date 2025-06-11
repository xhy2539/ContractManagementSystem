package com.example.contractmanagementsystem.controller;

import com.example.contractmanagementsystem.entity.ContractTemplate;
import com.example.contractmanagementsystem.service.TemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller // This controller handles both API and View requests for templates
@RequestMapping("/admin/templates")
@PreAuthorize("hasRole('ROLE_ADMIN')") // Only admin can manage templates
public class TemplateManagementController {

    private final TemplateService templateService;
    private final ObjectMapper objectMapper; // For handling JSON strings (e.g., placeholderFields)

    @Autowired
    public TemplateManagementController(TemplateService templateService, ObjectMapper objectMapper) {
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    // --- View Endpoints ---

    @GetMapping
    @PreAuthorize("hasAuthority('TEMP_VIEW_LIST')") // Assuming a new functionality for template management
    public String showTemplateManagementPage(Model model) {
        return "admin/template-management"; // Create this Thymeleaf template
    }

    // --- API Endpoints for CRUD Operations ---

    @PostMapping("/api")
    @ResponseBody
    @PreAuthorize("hasAuthority('TEMP_CREATE')")
    public ResponseEntity<ContractTemplate> createTemplate(@Valid @RequestBody ContractTemplate template) {
        // Here, the ContractTemplate object directly maps to the request body.
        // If placeholderFields is sent as a List<String> from frontend, it needs to be converted to JSON string here.
        // Or if frontend sends it as JSON string, it directly maps.
        // For simplicity, assuming frontend sends it as JSON string in template.getPlaceholderFields()
        ContractTemplate createdTemplate = templateService.createTemplate(template);
        return new ResponseEntity<>(createdTemplate, HttpStatus.CREATED);
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('TEMP_VIEW_LIST') or hasAuthority('TEMP_EDIT')")
    public ResponseEntity<ContractTemplate> getTemplateById(@PathVariable Long id) {
        return templateService.getTemplateById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api")
    @ResponseBody
    @PreAuthorize("hasAuthority('TEMP_VIEW_LIST')")
    public ResponseEntity<Page<ContractTemplate>> getAllTemplates(
            @PageableDefault(size = 10, sort = "templateName", direction = Sort.Direction.ASC) Pageable pageable,
            @RequestParam(required = false) String templateNameSearch,
            @RequestParam(required = false) String templateTypeSearch) {

        // For simplicity, filtering all templates in memory.
        // In a real application, you might add search methods to TemplateRepository.
        List<ContractTemplate> allTemplates = templateService.getAllTemplates();
        List<ContractTemplate> filteredTemplates = allTemplates.stream()
                .filter(template -> {
                    boolean nameMatches = true;
                    boolean typeMatches = true;

                    if (templateNameSearch != null && !templateNameSearch.isEmpty()) {
                        nameMatches = template.getTemplateName().toLowerCase().contains(templateNameSearch.toLowerCase());
                    }
                    if (templateTypeSearch != null && !templateTypeSearch.isEmpty()) {
                        typeMatches = template.getTemplateType().toLowerCase().contains(templateTypeSearch.toLowerCase());
                    }
                    return nameMatches && typeMatches;
                })
                .collect(Collectors.toList());

        // Manually paginate the filtered list
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), filteredTemplates.size());
        List<ContractTemplate> pageContent = (start <= end ? filteredTemplates.subList(start, end) : Collections.emptyList());

        return ResponseEntity.ok(new PageImpl<>(pageContent, pageable, filteredTemplates.size()));
    }


    @PutMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('TEMP_EDIT')")
    public ResponseEntity<ContractTemplate> updateTemplate(@PathVariable Long id, @Valid @RequestBody ContractTemplate templateDetails) {
        ContractTemplate updatedTemplate = templateService.updateTemplate(id, templateDetails);
        return ResponseEntity.ok(updatedTemplate);
    }

    @DeleteMapping("/api/{id}")
    @ResponseBody
    @PreAuthorize("hasAuthority('TEMP_DELETE')")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Long id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.noContent().build();
    }
}