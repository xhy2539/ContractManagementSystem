package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.ContractTemplate;
import java.util.List;
import java.util.Optional;

public interface TemplateService {
    ContractTemplate createTemplate(ContractTemplate template);
    Optional<ContractTemplate> getTemplateById(Long id);
    List<ContractTemplate> getAllTemplates();
    ContractTemplate updateTemplate(Long id, ContractTemplate templateDetails);
    void deleteTemplate(Long id);
    Optional<ContractTemplate> getTemplateByName(String templateName);
}