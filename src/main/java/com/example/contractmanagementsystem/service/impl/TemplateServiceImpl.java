package com.example.contractmanagementsystem.service.impl;

import com.example.contractmanagementsystem.entity.ContractTemplate;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractTemplateRepository;
import com.example.contractmanagementsystem.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TemplateServiceImpl implements TemplateService {

    private final ContractTemplateRepository templateRepository;

    @Autowired
    public TemplateServiceImpl(ContractTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    @Override
    @Transactional
    public ContractTemplate createTemplate(ContractTemplate template) {
        if (templateRepository.findByTemplateName(template.getTemplateName()).isPresent()) {
            throw new DuplicateResourceException("模板名称 '" + template.getTemplateName() + "' 已存在。");
        }
        return templateRepository.save(template);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractTemplate> getTemplateById(Long id) {
        return templateRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractTemplate> getAllTemplates() {
        return templateRepository.findAll();
    }

    @Override
    @Transactional
    public ContractTemplate updateTemplate(Long id, ContractTemplate templateDetails) {
        ContractTemplate existingTemplate = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("模板未找到，ID: " + id));

        if (!existingTemplate.getTemplateName().equals(templateDetails.getTemplateName()) &&
                templateRepository.findByTemplateName(templateDetails.getTemplateName()).isPresent()) {
            throw new DuplicateResourceException("模板名称 '" + templateDetails.getTemplateName() + "' 已被其他模板使用。");
        }

        existingTemplate.setTemplateName(templateDetails.getTemplateName());
        existingTemplate.setTemplateContent(templateDetails.getTemplateContent());
        existingTemplate.setTemplateType(templateDetails.getTemplateType());
        existingTemplate.setPlaceholderFields(templateDetails.getPlaceholderFields());

        return templateRepository.save(existingTemplate);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        if (!templateRepository.existsById(id)) {
            throw new ResourceNotFoundException("模板未找到，ID: " + id);
        }
        templateRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ContractTemplate> getTemplateByName(String templateName) {
        return templateRepository.findByTemplateName(templateName);
    }
}