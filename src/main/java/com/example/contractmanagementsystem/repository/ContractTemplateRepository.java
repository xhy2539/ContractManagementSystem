package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.ContractTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractTemplateRepository extends JpaRepository<ContractTemplate, Long> {
    Optional<ContractTemplate> findByTemplateName(String templateName);
    List<ContractTemplate> findByTemplateType(String templateType);
}