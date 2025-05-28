package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.repository.ContractRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl implements ContractService {

    @Autowired
    private ContractRepository contractRepository;

    @Override
    public Map<String, Long> getContractStatusStatistics() {
        List<Contract> allContracts = contractRepository.findAll();
        
        Map<ContractStatus, Long> statusStatistics = allContracts.stream()
                .collect(Collectors.groupingBy(
                        Contract::getStatus,
                        Collectors.counting()
                ));

        // 将枚举状态转换为字符串，并确保所有状态都有值，即使是0
        Map<String, Long> completeStatistics = new HashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            completeStatistics.put(status.name(), statusStatistics.getOrDefault(status, 0L));
        }

        return completeStatistics;
    }

    @Override
    public Page<Contract> searchContracts(String contractName, String contractNumber, String status, Pageable pageable) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 合同名称模糊查询
            if (StringUtils.hasText(contractName)) {
                predicates.add(criteriaBuilder.like(root.get("contractName"), "%" + contractName + "%"));
            }

            // 合同编号模糊查询
            if (StringUtils.hasText(contractNumber)) {
                predicates.add(criteriaBuilder.like(root.get("contractNumber"), "%" + contractNumber + "%"));
            }

            // 合同状态精确匹配
            if (StringUtils.hasText(status)) {
                try {
                    ContractStatus contractStatus = ContractStatus.valueOf(status);
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatus));
                } catch (IllegalArgumentException e) {
                    // 如果状态值无效，忽略这个条件
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return contractRepository.findAll(spec, pageable);
    }
} 