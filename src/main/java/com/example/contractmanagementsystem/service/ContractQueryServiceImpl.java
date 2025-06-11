package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.repository.ContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractQueryServiceImpl implements ContractQueryService {

    private final ContractRepository contractRepository;

    @Autowired
    public ContractQueryServiceImpl(ContractRepository contractRepository) {
        this.contractRepository = contractRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(String contractName, String contractNumber, Pageable pageable) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (contractName != null && !contractName.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("contractName")),
                    "%" + contractName.toLowerCase() + "%"
                ));
            }
            
            if (contractNumber != null && !contractNumber.trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("contractNumber")),
                    "%" + contractNumber.toLowerCase() + "%"
                ));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        
        return contractRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsByStatus(String status, Pageable pageable) {
        if (status == null || status.trim().isEmpty()) {
            return contractRepository.findAll(pageable);
        }
        
        try {
            ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
            return contractRepository.findByStatus(contractStatus, pageable);
        } catch (IllegalArgumentException e) {
            // 如果状态值无效，返回空页
            return Page.empty(pageable);
        }
    }
} 