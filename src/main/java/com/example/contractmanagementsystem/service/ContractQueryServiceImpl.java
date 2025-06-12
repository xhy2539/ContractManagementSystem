package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.util.ArrayList;
import java.util.List;

@Service
public class ContractQueryServiceImpl implements ContractQueryService {

    private final ContractRepository contractRepository;
    private final UserRepository userRepository;

    @Autowired
    public ContractQueryServiceImpl(ContractRepository contractRepository, UserRepository userRepository) {
        this.contractRepository = contractRepository;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(
            String contractName,
            String contractNumber,
            String status,
            String username,
            boolean isAdmin,
            Pageable pageable) {
        
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            // 合同名称条件
            if (StringUtils.hasText(contractName)) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("contractName")),
                    "%" + contractName.toLowerCase() + "%"
                ));
            }
            
            // 合同编号条件
            if (StringUtils.hasText(contractNumber)) {
                predicates.add(criteriaBuilder.like(
                    criteriaBuilder.lower(root.get("contractNumber")),
                    "%" + contractNumber.toLowerCase() + "%"
                ));
            }
            
            // 合同状态条件
            if (StringUtils.hasText(status)) {
                try {
                    ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatus));
                } catch (IllegalArgumentException e) {
                    // 如果状态值无效，忽略这个条件
                }
            }

            // 用户权限过滤
            if (!isAdmin && StringUtils.hasText(username)) {
                User currentUser = userRepository.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found: " + username));

                // 是起草人或参与过流程的合同
                Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

                // 子查询：查找用户参与的合同流程
                Subquery<Long> processSubquery = query.subquery(Long.class);
                var processRoot = processSubquery.from(ContractProcess.class);
                processSubquery.select(processRoot.get("contract").get("id"))
                        .where(
                            criteriaBuilder.and(
                                criteriaBuilder.equal(processRoot.get("contract"), root),
                                criteriaBuilder.equal(processRoot.get("operator"), currentUser)
                            )
                        );

                predicates.add(criteriaBuilder.or(
                    isDrafter,
                    criteriaBuilder.exists(processSubquery)
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