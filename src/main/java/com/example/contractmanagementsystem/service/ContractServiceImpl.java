package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;

// Jackson imports for JSON processing
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Subquery;


import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl implements ContractService {

    private static final Logger logger = LoggerFactory.getLogger(ContractServiceImpl.class);

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final AuditLogService auditLogService;
    private final AttachmentService attachmentService;
    private final ObjectMapper objectMapper;

    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository,
                               AuditLogService auditLogService,
                               AttachmentService attachmentService,
                               ObjectMapper objectMapper) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        logger.info("ContractServiceImpl initialized.");
    }

    @Override
    @Transactional
    public Contract draftContract(ContractDraftRequest request, String username) throws IOException {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessLogicException("合同开始日期不能晚于结束日期！");
        }

        Customer selectedCustomer = customerRepository.findById(request.getSelectedCustomerId())
                .orElseThrow(() -> new ResourceNotFoundException("选择的客户不存在，ID: " + request.getSelectedCustomerId()));

        User drafter = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("起草人用户 '" + username + "' 不存在。"));

        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setDrafter(drafter);

        if (!CollectionUtils.isEmpty(request.getAttachmentServerFileNames())) {
            try {
                String attachmentsJson = objectMapper.writeValueAsString(request.getAttachmentServerFileNames());
                contract.setAttachmentPath(attachmentsJson);
                logger.info("Contract '{}' drafted with attachment path: {}", contract.getContractName(), attachmentsJson);
            } catch (JsonProcessingException e) {
                logger.error("Error serializing attachment filenames list to JSON (Contract: {}): {}", request.getContractName(), e.getMessage());
                throw new BusinessLogicException("Error processing attachment information: " + e.getMessage());
            }
        } else {
            contract.setAttachmentPath(null);
        }

        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);

        Contract savedContract = contractRepository.save(contract);
        String logDetails = "User " + username + " drafted contract: " + savedContract.getContractName() +
                " (ID: " + savedContract.getId() + "), status changed to pending assignment.";
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            logDetails += " Attachments: " + savedContract.getAttachmentPath();
        }
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", logDetails);
        return savedContract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException {
        Contract contract = getContractForFinalization(contractId, username);
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Finalizing user '" + username + "' not found."));

        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        if (attachmentServerFileNames != null) {
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                }
                catch (JsonProcessingException e) {
                    logger.error("Error serializing attachment filenames list to JSON (Finalize Contract ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("Error processing attachment information: " + e.getMessage());
                }
            } else {
                newAttachmentsJson = "[]";
            }
            contract.setAttachmentPath(newAttachmentsJson);

            if (oldAttachmentPath == null && newAttachmentsJson != null && !newAttachmentsJson.equals("[]")) {
                logger.info("Contract {} finalized, new attachments added: {}", contractId, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENT_ADDED_ON_FINALIZE", "Contract ID " + contractId + " finalized, new attachments added: " + newAttachmentsJson);
            } else if (oldAttachmentPath != null && (newAttachmentsJson == null || newAttachmentsJson.equals("[]"))) {
                logger.info("Contract {} finalized, all attachments removed (Original attachments: {})", contractId, oldAttachmentPath);
                auditLogService.logAction(username, "ATTACHMENTS_REMOVED_ON_FINALIZE", "Contract ID " + contractId + " finalized, all attachments removed. Original attachments: " + oldAttachmentPath);
            } else if (oldAttachmentPath != null && newAttachmentsJson != null && !oldAttachmentPath.equals(newAttachmentsJson)) {
                logger.info("Contract {} finalized, attachments updated from '{}' to '{}'", contractId, oldAttachmentPath, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENTS_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " finalized, attachments updated from " + oldAttachmentPath + " to " + newAttachmentsJson);
            }
        }

        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " content updated during finalization.");
            logger.info("Contract {} finalized, content updated.", contractId);
        }

        contract.setStatus(ContractStatus.PENDING_APPROVAL);
        contract.setUpdatedAt(LocalDateTime.now());

        ContractProcess finalizationProcessRecord = new ContractProcess();
        finalizationProcessRecord.setContract(contract);
        finalizationProcessRecord.setContractNumber(contract.getContractNumber());
        finalizationProcessRecord.setType(ContractProcessType.FINALIZE);
        finalizationProcessRecord.setState(ContractProcessState.COMPLETED);
        finalizationProcessRecord.setOperator(finalizer);
        finalizationProcessRecord.setOperatorUsername(finalizer.getUsername());
        finalizationProcessRecord.setComments(finalizationComments);
        finalizationProcessRecord.setProcessedAt(LocalDateTime.now());
        finalizationProcessRecord.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(finalizationProcessRecord);

        Contract savedContract = contractRepository.save(contract);

        String details = "Contract ID " + contractId + " (" + contract.getContractName() + ") finalized by user " + username + ", status changed to pending approval.";
        if (StringUtils.hasText(finalizationComments)) {
            details += " Finalization comments: " + finalizationComments + ".";
        }
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            details += " Current attachments: " + savedContract.getAttachmentPath() + ".";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }

    @Override
    @Transactional
    public void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) {
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING);

        process.setState(isApproved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_APPROVED" : "CONTRACT_COUNTERSIGN_REJECTED";
        String logDetails = "User " + username + (isApproved ? " approved" : " rejected") + " countersign for Contract ID " +
                contract.getId() + " (" + contract.getContractName() + "). Comments: " +
                (StringUtils.hasText(comments) ? comments : "None");

        if (!isApproved) {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " Contract rejected due to countersign, status changed to Rejected.";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return;
        }

        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN);
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED);

        if (allRelevantCountersignsApproved) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION);
            logDetails += " All countersigns completed and approved, contract enters pending finalization status.";

            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED_TO_FINALIZE", logDetails);
            User drafter = contract.getDrafter();
            if (drafter == null) {
                logger.error("Contract (ID: {}) countersign completed, but drafter is null, cannot create finalization task.", contract.getId());
                throw new BusinessLogicException("Contract drafter information missing, cannot proceed with finalization process.");
            }

            Optional<ContractProcess> existingFinalizeTask = contractProcessRepository
                    .findByContractIdAndOperatorUsernameAndTypeAndState(
                            contract.getId(),
                            drafter.getUsername(),
                            ContractProcessType.FINALIZE,
                            ContractProcessState.PENDING
                    );

            if (existingFinalizeTask.isEmpty()) {
                ContractProcess finalizeTask = new ContractProcess();
                finalizeTask.setContract(contract);
                finalizeTask.setContractNumber(contract.getContractNumber());
                finalizeTask.setType(ContractProcessType.FINALIZE);
                finalizeTask.setState(ContractProcessState.PENDING);
                finalizeTask.setOperator(drafter);
                finalizeTask.setOperatorUsername(drafter.getUsername());
                finalizeTask.setComments("Waiting for drafter to finalize contract content.");
                contractProcessRepository.save(finalizeTask);
                auditLogService.logAction(drafter.getUsername(), "FINALIZE_TASK_CREATED",
                        "Created pending finalization task for Contract ID " + contract.getId() + " (" + contract.getContractName() + ").");
                logger.info("Finalization task created for contract {} (ID: {}) for drafter {}.", contract.getContractName(), contract.getId(), drafter.getUsername());
            } else {
                logger.warn("Contract {} (ID: {}) already has a pending finalization task for drafter {}, skipping creation.", contract.getContractName(), contract.getId(), drafter.getUsername());
            }
        } else {
            logDetails += " Current countersign approved, but other countersign processes are still pending. Contract status remains pending countersign.";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    public Path getAttachmentPath(String filename) {
        return attachmentService.getAttachment(filename);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus();
        Map<String, Long> statistics = new HashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            statistics.put(status.name(), 0L);
        }
        for (Object[] result : results) {
            if (result[0] instanceof ContractStatus && result[1] instanceof Long) {
                ContractStatus status = (ContractStatus) result[0];
                Long count = (Long) result[1];
                statistics.put(status.name(), count);
            }
        }
        return statistics;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(String currentUsername, boolean isAdmin, String contractName, String contractNumber, String status, Pageable pageable) {
        Specification<Contract> spec = (Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(contractName)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractName")), "%" + contractName.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(contractNumber)) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractNumber")), "%" + contractNumber.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(status)) {
                try {
                    ContractStatus contractStatusEnum = ContractStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatusEnum));
                } catch (IllegalArgumentException e) {
                    logger.warn("Invalid status value provided in contract search: {}. Ignoring this status condition.", status);
                }
            }

            if (!isAdmin && StringUtils.hasText(currentUsername)) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElse(null);

                if (currentUser != null) {
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
                    subquery.select(contractProcessRoot.get("contract").get("id"));

                    Predicate subqueryPredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                            criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser)
                    );
                    subquery.where(subqueryPredicate);

                    Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

                    predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInProcess));
                } else {
                    logger.warn("Username '{}' provided for user-specific search, but user not found in database. Query will return no user-specific data.", currentUsername);
                    predicates.add(criteriaBuilder.disjunction());
                }
            }

            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }

            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);

        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return contractsPage;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingProcessesForUser(
            String username,
            ContractProcessType type,
            ContractProcessState state,
            String contractNameSearch,
            Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("User not found: " + username));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));

            if (type != ContractProcessType.EXTENSION_REQUEST || !isAdmin) {
                predicates.add(cb.equal(root.get("operator"), currentUser));
            }


            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER);

            switch (type) {
                case COUNTERSIGN:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN));
                    break;
                case APPROVAL:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL));
                    break;
                case SIGNING:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING));
                    break;
                case FINALIZE:
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION));
                    break;
                case EXTENSION_REQUEST:
                    predicates.add(cb.or(
                            cb.equal(contractJoin.get("status"), ContractStatus.ACTIVE),
                            cb.equal(contractJoin.get("status"), ContractStatus.EXPIRED)
                    ));
                    break;
                default:
                    break;
            }

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            if (query.getResultType().equals(ContractProcess.class)) {
                root.fetch("operator", JoinType.LEFT);
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                contractFetch.fetch("drafter", JoinType.LEFT);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable);

        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator());
            User operator = process.getOperator();
            if (operator != null) {
                Hibernate.initialize(operator.getRoles());
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }

            Hibernate.initialize(process.getContract());
            if (process.getContract() != null) {
                Hibernate.initialize(process.getContract().getCustomer());
                User drafter = process.getContract().getDrafter();
                if (drafter != null) {
                    Hibernate.initialize(drafter.getRoles());
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
                }
            }
        });
        return resultPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllPendingTasksForUser(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("User not found: " + username));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> finalPredicates = new ArrayList<>(); // 使用新的列表来存储最终的 Predicate

            // 1. 任务状态必须是“待处理”
            finalPredicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            // 2. 连接到Contract实体以过滤合同状态
            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER);

            // 3. 定义与用户权限无关的基本任务条件
            // 这些任务必须是当前用户作为操作员的
            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN),
                    cb.equal(root.get("operator"), currentUser) // 必须是当前用户操作的
            );
            Predicate approvalTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.APPROVAL),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL),
                    cb.equal(root.get("operator"), currentUser) // 必须是当前用户操作的
            );
            Predicate signingTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.SIGNING),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING),
                    cb.equal(root.get("operator"), currentUser) // 必须是当前用户操作的
            );
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION),
                    cb.equal(root.get("operator"), currentUser) // 必须是当前用户操作的
            );

            List<Predicate> allOrConditions = new ArrayList<>();
            allOrConditions.add(countersignTasks);
            allOrConditions.add(approvalTasks);
            allOrConditions.add(signingTasks);
            allOrConditions.add(finalizeTasks);

            if (isAdmin) {
                // 如果是管理员，除了自己操作的任务，还要包括所有待管理员审批的延期请求
                // 注意：管理员也可能自己发起了延期请求，但主要目的是审批
                // 所以我们这里获取所有待审批的延期请求，不考虑操作员是谁
                Predicate allPendingExtensionRequests = cb.and(
                        cb.equal(root.get("type"), ContractProcessType.EXTENSION_REQUEST),
                        cb.equal(root.get("state"), ContractProcessState.PENDING)
                        // 不再限制操作员，管理员可以看所有待审批的延期请求
                );
                allOrConditions.add(allPendingExtensionRequests);
            }

            // 将所有 OR 条件和固定的 state=PENDING 条件组合
            Predicate combinedTasks = cb.and(
                    cb.equal(root.get("state"), ContractProcessState.PENDING),
                    cb.or(allOrConditions.toArray(new Predicate[0]))
            );
            finalPredicates.clear(); // 清空之前的，只添加最终的组合条件
            finalPredicates.add(combinedTasks);


            // 对主查询进行急切加载
            if (query.getResultType().equals(ContractProcess.class)) {
                root.fetch("operator", JoinType.LEFT);
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                contractFetch.fetch("drafter", JoinType.LEFT);
            }
            query.orderBy(cb.desc(root.get("createdAt"))); // 按流程创建时间降序排序

            return cb.and(finalPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec);
        // 显式初始化懒加载的关联实体
        tasks.forEach(task -> {
            // 确保操作员（流程执行者）的角色和功能已加载
            User operator = task.getOperator();
            if (operator != null) {
                Hibernate.initialize(operator.getRoles());
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
            // 确保合同起草人以及起草人的角色和功能已加载
            if (task.getContract() != null && task.getContract().getDrafter() != null) {
                User drafter = task.getContract().getDrafter();
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return tasks;
    }


    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + id));
        Hibernate.initialize(contract.getCustomer());
        User drafter = contract.getDrafter();
        if (drafter != null) {
            Hibernate.initialize(drafter);
            Hibernate.initialize(drafter.getRoles());
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .isPresent();
    }


    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        Contract contract = getContractById(contractId);

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) {
            throw new BusinessLogicException("Contract current status is " + contract.getStatus().getDescription() + ", cannot be approved. Must be in pending approval status.");
        }

        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new AccessDeniedException("Your pending approval task not found, or you do not have permission to approve this contract."));

        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "User " + username + (approved ? " approved" : " rejected") + " approval for Contract ID " + contractId +
                " (" + contract.getContractName() + "). Approval comments: " +
                (StringUtils.hasText(comments) ? comments : "None");

        if (approved) {
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL);
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED);

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
                logDetails += " All approvals passed, contract enters pending signing status.";
            } else {
                logDetails += " Other approval processes are still pending or not all approved. Contract status remains pending approval.";
            }
        } else {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " Contract rejected, status updated to Rejected.";
        }
        contractRepository.save(contract);
        auditLogService.logAction(username, logActionType, logDetails);
    }


    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) {
        ContractProcess process = contractProcessRepository.findById(contractProcessId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract process record not found, ID: " + contractProcessId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("Current user '" + username + "' not found."));

        // **修改开始：在方法内部获取 isAdmin 状态**
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        // **修改结束**

        if (process.getType() != ContractProcessType.EXTENSION_REQUEST && !process.getOperator().equals(currentUser)) {
            throw new AccessDeniedException("You (" + username + ") are not the designated operator for this contract process (ID: " + contractProcessId +
                    ", Operator: " + process.getOperator().getUsername() + "), no permission to operate.");
        }
        if (process.getType() == ContractProcessType.EXTENSION_REQUEST) {
            // 对于 EXTENSION_REQUEST 类型，如果用户不是管理员，抛出 AccessDeniedException
            if (!isAdmin) {
                throw new AccessDeniedException("Only an Administrator can process Extension Requests.");
            }
            // 如果是管理员，则允许继续执行（无需判断 operator，因为管理员可以处理任何人的请求）
        }


        if (process.getType() != expectedType) {
            throw new BusinessLogicException("Contract process type mismatch. Expected type: " + expectedType.getDescription() +
                    ", Actual type: " + process.getType().getDescription());
        }
        if (process.getState() != expectedState) {
            throw new BusinessLogicException("Contract process status incorrect. Expected status: " + expectedState.getDescription() +
                    ", Actual status: " + process.getState().getDescription());
        }

        Hibernate.initialize(process.getContract());
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer());
            User drafter = process.getContract().getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        }
        User operator = process.getOperator();
        if (operator != null) {
            Hibernate.initialize(operator.getRoles());
            operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return process;
    }



    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

        Contract contract = process.getContract();
        if (contract.getStatus() != ContractStatus.PENDING_SIGNING) {
            throw new BusinessLogicException("Contract current status is " + contract.getStatus().getDescription() + ", cannot be signed. Must be in pending signing status.");
        }

        process.setState(ContractProcessState.COMPLETED);
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "User " + username + " completed signing for Contract ID " + contract.getId() +
                " (" + contract.getContractName() + "). Signing comments: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "None");

        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING);
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED);

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE);
            logDetails += " All signing processes completed, contract status updated to Active.";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " Other signing processes are still pending. Contract status remains pending signing.";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found."));

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION));
            predicates.add(cb.equal(root.get("drafter"), currentUser));


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            User drafter = contract.getDrafter();
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles());
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractForFinalization(Long contractId, String username) {
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + contractId));

        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) {
            throw new BusinessLogicException("合同当前状态为" + contract.getStatus().getDescription() +
                    "，无法进行定稿操作。必须处于待定稿状态。");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Current user '" + username + "' not found."));
        if (contract.getDrafter() == null || !contract.getDrafter().equals(currentUser)) {
            logger.warn("User '{}' attempted to finalize contract ID {}, but this contract was drafted by '{}', or drafter is null. Access denied.",
                    username, contractId, (contract.getDrafter() != null ? contract.getDrafter().getUsername() : "unknown"));
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人，无权定稿。");
        }

        User drafter = contract.getDrafter();
        if (drafter != null) {
            Hibernate.initialize(drafter.getRoles());
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractCountersignOpinions(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Failed to get countersign opinions: Contract not found, ID: " + contractId));

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN);

        countersignProcesses.forEach(process -> {
            Hibernate.initialize(process.getOperator());
        });

        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    private Predicate buildUserContractAccessPredicate(String username, boolean isAdmin, Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (isAdmin) {
            return criteriaBuilder.conjunction();
        }

        User currentUser = userRepository.findByUsername(username)
                .orElse(null);

        if (currentUser == null) {
            logger.warn("Username '{}' provided for user-specific contract statistics, but user not found in database. Statistics will return zero.", username);
            return criteriaBuilder.disjunction();
        }

        Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

        Subquery<Long> subquery = query.subquery(Long.class);
        Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
        subquery.select(contractProcessRoot.get("contract").get("id"));

        Predicate subqueryPredicate = criteriaBuilder.and(
                criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser)
        );
        subquery.where(subqueryPredicate);

        Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

        return criteriaBuilder.or(isDrafter, isInvolvedInProcess);
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE));

            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsExpiringSoon(int days, String username, boolean isAdmin) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE));
            predicates.add(criteriaBuilder.greaterThan(root.get("endDate"), today));
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), futureDate));

            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsPendingAssignment() {
        return contractRepository.countByStatus(ContractStatus.PENDING_ASSIGNMENT);
    }

    @Override
    @Transactional(readOnly = true)
    public long countExpiredContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), ContractStatus.EXPIRED));

            predicates.add(buildUserContractAccessPredicate(username, isAdmin, root, query, criteriaBuilder));

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.count(spec);
    }

    @Override
    @Transactional
    public int updateExpiredContractStatuses() {
        LocalDate today = LocalDate.now();

        Set<ContractStatus> excludedStatuses = EnumSet.of(
                ContractStatus.EXPIRED,
                ContractStatus.COMPLETED,
                ContractStatus.TERMINATED,
                ContractStatus.REJECTED
        );

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), today));
            predicates.add(criteriaBuilder.not(root.get("status").in(excludedStatuses)));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        List<Contract> contractsToExpire = contractRepository.findAll(spec);

        int updatedCount = 0;
        for (Contract contract : contractsToExpire) {
            if (contract.getStatus() != ContractStatus.EXPIRED) {
                contract.setStatus(ContractStatus.EXPIRED);
                contract.setUpdatedAt(LocalDateTime.now());
                contractRepository.save(contract);
                updatedCount++;
                auditLogService.logAction("SYSTEM", "CONTRACT_AUTO_EXPIRED",
                        "Contract " + contract.getContractNumber() + " (ID: " + contract.getId() +
                                ") automatically updated to EXPIRED status from " + contract.getStatus().name() + " on " + today);
                logger.info("Contract {} (ID: {}) automatically updated to EXPIRED status.", contract.getContractName(), contract.getId());
            }
        }
        return updatedCount;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingCountersignContracts(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Specification<ContractProcess> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != String.class) {
                root.fetch("contract", JoinType.INNER)
                        .fetch("customer", JoinType.LEFT)
                        .fetch("drafter", JoinType.LEFT);
            }


            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("operator"), currentUser));
            predicates.add(cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN));
            predicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contract").get("contractName")),
                        "%" + contractNameSearch.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return contractProcessRepository.findAll(spec, pageable);
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<ContractProcess> getContractProcessDetails(Long contractId, String username, ContractProcessType type, ContractProcessState state) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, type, state
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllContractProcessesByContractAndType(Contract contract, ContractProcessType type) {
        List<ContractProcess> processes = contractProcessRepository.findByContractAndType(contract, type);
        processes.forEach(process -> Hibernate.initialize(process.getOperator()));
        return processes.stream()
                .sorted(Comparator.comparing(ContractProcess::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserCountersignContract(Long contractId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        Optional<ContractProcess> processOpt = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
        );
        return processOpt.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractProcessHistory(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + contractId));

        List<ContractProcess> processes = contractProcessRepository.findByContractOrderByCreatedAtDesc(contract);

        processes.forEach(process -> {
            Hibernate.initialize(process.getOperator());
            if (process.getOperator() != null) {
                Hibernate.initialize(process.getOperator().getRoles());
            }
        });

        return processes;
    }
    @Override
    @Transactional(readOnly = true)
    public long countInProcessContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            List<ContractStatus> inProcessStatuses = Arrays.asList(
                    ContractStatus.PENDING_COUNTERSIGN,
                    ContractStatus.PENDING_APPROVAL,
                    ContractStatus.PENDING_SIGNING,
                    ContractStatus.PENDING_FINALIZATION
            );
            predicates.add(root.get("status").in(inProcessStatuses));

            if (!isAdmin) {
                User currentUser = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("用户未找到: " + username));

                Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser);

                Subquery<Long> subquery = query.subquery(Long.class);
                Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class);
                subquery.select(contractProcessRoot.get("contract").get("id"));

                Predicate subqueryPredicate = criteriaBuilder.and(
                        criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")),
                        criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser),
                        criteriaBuilder.equal(contractProcessRoot.get("state"), ContractProcessState.PENDING),
                        contractProcessRoot.get("type").in(
                                ContractProcessType.COUNTERSIGN,
                                ContractProcessType.APPROVAL,
                                ContractProcessType.SIGNING,
                                ContractProcessType.FINALIZE,
                                ContractProcessType.EXTENSION_REQUEST
                        )
                );
                subquery.where(subqueryPredicate);

                Predicate isInvolvedInPendingProcess = criteriaBuilder.exists(subquery);

                predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInPendingProcess));
            }

            query.distinct(true);

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return contractRepository.count(spec);
    }

    @Override
    @Transactional
    public Contract extendContract(Long contractId, LocalDate newEndDate, String comments, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("只有管理员才能直接延期合同。请通过操作员延期请求流程。");
        }

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，无法直接延期。合同必须是“有效”或“过期”状态。");
        }

        if (newEndDate.isBefore(contract.getEndDate()) || newEndDate.isEqual(contract.getEndDate())) {
            throw new BusinessLogicException("新的到期日期必须晚于原到期日期 (" + contract.getEndDate() + ")。");
        }
        if (newEndDate.isBefore(LocalDate.now())) {
            throw new BusinessLogicException("新的到期日期不能是过去的日期。");
        }


        contract.setEndDate(newEndDate);
        if (contract.getStatus() == ContractStatus.EXPIRED) {
            contract.setStatus(ContractStatus.ACTIVE);
        }
        contract.setUpdatedAt(LocalDateTime.now());
        Contract updatedContract = contractRepository.save(contract);

        User adminUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("管理员用户 '" + username + "' 未找到。"));
        ContractProcess process = new ContractProcess();
        process.setContract(updatedContract);
        process.setContractNumber(updatedContract.getContractNumber());
        process.setType(ContractProcessType.EXTENSION);
        process.setState(ContractProcessState.COMPLETED);
        process.setOperator(adminUser);
        process.setOperatorUsername(username);
        process.setComments("管理员直接延期。原到期日期: " + contract.getEndDate() + ", 新到期日期: " + newEndDate + (StringUtils.hasText(comments) ? ". 备注: " + comments : ""));
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        auditLogService.logAction(username, "CONTRACT_EXTENDED_ADMIN",
                "管理员直接延期合同 ID: " + contractId + " (" + contract.getContractName() + ")，新到期日期: " + newEndDate);

        return updatedContract;
    }

    @Override
    @Transactional
    public ContractProcess requestExtendContract(Long contractId, LocalDate requestedNewEndDate, String reason, String comments, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isContractOperator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONTRACT_OPERATOR"));

        if (!isContractOperator && !authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            throw new AccessDeniedException("只有合同操作员或管理员才能提交合同延期请求。");
        }

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，无法提交延期请求。合同必须是“有效”或“过期”状态。");
        }

        if (requestedNewEndDate.isBefore(contract.getEndDate()) || requestedNewEndDate.isEqual(contract.getEndDate())) {
            throw new BusinessLogicException("期望新的到期日期必须晚于原到期日期 (" + contract.getEndDate() + ")。");
        }
        if (requestedNewEndDate.isBefore(LocalDate.now())) {
            throw new BusinessLogicException("期望新的到期日期不能是过去的日期。");
        }

        boolean hasPendingExtensionRequest = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(contractId, username, ContractProcessType.EXTENSION_REQUEST, ContractProcessState.PENDING)
                .isPresent();

        if (hasPendingExtensionRequest) {
            throw new BusinessLogicException("您已为此合同提交过延期请求，请勿重复提交。");
        }

        User requestingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("操作员用户 '" + username + "' 未找到。"));

        ContractProcess process = new ContractProcess();
        process.setContract(contract);
        process.setContractNumber(contract.getContractNumber());
        process.setType(ContractProcessType.EXTENSION_REQUEST);
        process.setState(ContractProcessState.PENDING);
        process.setOperator(requestingUser);
        process.setOperatorUsername(username);
        // 构建comments字符串时，明确包含所有部分，即使某些部分为空
        StringBuilder commentsBuilder = new StringBuilder();
        commentsBuilder.append("请求将合同到期日期从 ").append(contract.getEndDate()).append(" 延期至 ").append(requestedNewEndDate).append("。");
        commentsBuilder.append("原因: ").append(StringUtils.hasText(reason) ? reason : "无").append(".");
        if (StringUtils.hasText(comments)) {
            commentsBuilder.append(" 附加备注: ").append(comments).append(".");
        } else {
            commentsBuilder.append(" 附加备注: ").append("无。"); // 确保始终有这一部分
        }
        process.setComments(commentsBuilder.toString());

        ContractProcess savedProcess = contractProcessRepository.save(process);

        auditLogService.logAction(username, "CONTRACT_EXTENSION_REQUESTED",
                "操作员请求延期合同 ID: " + contractId + " (" + contract.getContractName() + ")，请求延期至: " + requestedNewEndDate + "。原因: " + reason);

        return savedProcess;
    }

    @Override
    @Transactional
    public ContractProcess processExtensionRequest(Long processId, String username, boolean isApproved, String comments) {
        ContractProcess requestProcess = contractProcessRepository.findById(processId)
                .orElseThrow(() -> new ResourceNotFoundException("延期请求未找到，ID: " + processId));

        if (requestProcess.getType() != ContractProcessType.EXTENSION_REQUEST || requestProcess.getState() != ContractProcessState.PENDING) {
            throw new BusinessLogicException("此流程 (ID: " + processId + ") 不是有效的待处理延期请求。当前类型: " +
                    requestProcess.getType().getDescription() + ", 状态: " + requestProcess.getState().getDescription());
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            throw new AccessDeniedException("只有管理员才能审批延期请求。");
        }

        User adminUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("审批管理员用户 '" + username + "' 未找到。"));

        Contract contract = requestProcess.getContract();
        if (contract == null) {
            throw new BusinessLogicException("延期请求关联的合同不存在。流程ID: " + processId);
        }

        // 使用新的解析方法来获取期望新日期，避免重复的解析逻辑
        Map<String, String> parsedComments = parseExtensionRequestComments(requestProcess.getComments());
        LocalDate requestedNewEndDate = null;
        try {
            if (parsedComments.get("requestedNewEndDate") != null && !parsedComments.get("requestedNewEndDate").isEmpty()) {
                requestedNewEndDate = LocalDate.parse(parsedComments.get("requestedNewEndDate"), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            }
        } catch (Exception e) {
            logger.error("从解析后的延期请求评论中获取期望的新到期日期失败：'{}'", parsedComments.get("requestedNewEndDate"), e);
            throw new BusinessLogicException("延期请求内容格式错误，无法解析期望的到期日期。");
        }

        if (requestedNewEndDate == null) {
            throw new BusinessLogicException("延期请求中未包含有效的期望到期日期。");
        }

        requestProcess.setProcessedAt(LocalDateTime.now());
        requestProcess.setCompletedAt(LocalDateTime.now());
        requestProcess.setOperator(adminUser);
        requestProcess.setOperatorUsername(username);
        requestProcess.setComments("管理员审批意见: " + (StringUtils.hasText(comments) ? comments : "无。"));

        String auditLogAction;
        String auditLogDetails;

        if (isApproved) {
            if (requestedNewEndDate.isBefore(contract.getEndDate()) || requestedNewEndDate.isEqual(contract.getEndDate())) {
                throw new BusinessLogicException("批准的延期日期 (" + requestedNewEndDate + ") 必须晚于合同当前到期日期 (" + contract.getEndDate() + ")。");
            }
            if (requestedNewEndDate.isBefore(LocalDate.now())) {
                throw new BusinessLogicException("批准的延期日期不能是过去的日期。");
            }

            contract.setEndDate(requestedNewEndDate);
            if (contract.getStatus() == ContractStatus.EXPIRED) {
                contract.setStatus(ContractStatus.ACTIVE);
            }
            contract.setUpdatedAt(LocalDateTime.now());
            contractRepository.save(contract);

            requestProcess.setState(ContractProcessState.APPROVED);
            auditLogAction = "CONTRACT_EXTENSION_APPROVED";
            auditLogDetails = "管理员批准合同 ID: " + contract.getId() + " (" + contract.getContractName() +
                    ") 的延期请求，新到期日期: " + requestedNewEndDate + "。审批意见: " + (comments != null ? comments : "无");
        } else {
            requestProcess.setState(ContractProcessState.REJECTED);
            auditLogAction = "CONTRACT_EXTENSION_REJECTED";
            auditLogDetails = "管理员拒绝合同 ID: " + contract.getId() + " (" + contract.getContractName() +
                    ") 的延期请求。审批意见: " + (comments != null ? comments : "无");
        }

        ContractProcess savedProcess = contractProcessRepository.save(requestProcess);
        auditLogService.logAction(username, auditLogAction, auditLogDetails);
        logger.info("管理员 '{}' 处理延期请求 {}: {}。合同 ID: {}", username, processId, (isApproved ? "批准" : "拒绝"), contract.getId());

        return savedProcess;
    }

    @Override
    public Map<String, String> parseExtensionRequestComments(String comments) {
        Map<String, String> parsed = new HashMap<>();
        String requestedNewEndDate = null;
        String reason = null;
        String additionalComments = null;

        if (StringUtils.hasText(comments)) {
            // 查找期望新日期
            int dateStartIdx = comments.indexOf("延期至 ");
            int reasonStartKeywordIdx = comments.indexOf("。原因: ");
            int additionalCommentsStartKeywordIdx = comments.indexOf(". 附加备注: ");

            if (dateStartIdx != -1) {
                dateStartIdx += "延期至 ".length();
                int dateEndIdx = -1;

                if (reasonStartKeywordIdx != -1 && reasonStartKeywordIdx > dateStartIdx) {
                    dateEndIdx = reasonStartKeywordIdx;
                }
                // else if (comments.length() > dateStartIdx) { // 如果没有原因关键字，就取到字符串末尾（不太可能）
                //     dateEndIdx = comments.length();
                // }

                if (dateEndIdx != -1) {
                    requestedNewEndDate = comments.substring(dateStartIdx, dateEndIdx).trim();
                    // 移除可能的中文句号
                    if (requestedNewEndDate.endsWith("。")) {
                        requestedNewEndDate = requestedNewEndDate.substring(0, requestedNewEndDate.length() - 1);
                    }
                }
            }

            // 查找原因和附加备注
            if (reasonStartKeywordIdx != -1) {
                reasonStartKeywordIdx += "。原因: ".length();

                if (additionalCommentsStartKeywordIdx != -1 && additionalCommentsStartKeywordIdx > reasonStartKeywordIdx) {
                    reason = comments.substring(reasonStartKeywordIdx, additionalCommentsStartKeywordIdx).trim();
                    additionalComments = comments.substring(additionalCommentsStartKeywordIdx + ". 附加备注: ".length()).trim();
                    // 移除附加备注末尾的句号，如果存在
                    if (additionalComments.endsWith("。")) {
                        additionalComments = additionalComments.substring(0, additionalComments.length() - 1);
                    }
                } else {
                    // 没有“附加备注”部分，原因取到字符串末尾
                    reason = comments.substring(reasonStartKeywordIdx).trim();
                    // 移除原因末尾的句号，如果存在
                    if (reason.endsWith("。")) {
                        reason = reason.substring(0, reason.length() - 1);
                    }
                    additionalComments = "(无)"; // 明确表示没有附加备注
                }
            } else {
                reason = "(无法解析原因)";
                additionalComments = "(无法解析备注)";
            }
        } else {
            requestedNewEndDate = "(无)";
            reason = "(无)";
            additionalComments = "(无)";
        }

        parsed.put("requestedNewEndDate", requestedNewEndDate);
        parsed.put("reason", reason);
        parsed.put("additionalComments", additionalComments);

        logger.debug("Parsed extension request comments: {}", parsed);
        return parsed;
    }
}