package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.dto.DashboardStatsDto;
import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;

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
    private final EmailService emailService;
    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository,
                               AuditLogService auditLogService,
                               AttachmentService attachmentService,
                               ObjectMapper objectMapper,
                               EmailService emailService) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.auditLogService = auditLogService;
        this.attachmentService = attachmentService;
        this.objectMapper = objectMapper;
        this.emailService = emailService;
    }

    @PostConstruct
    public void init() {
        logger.info("ContractServiceImpl initialized.");
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStatistics(String username, boolean isAdmin) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(30);
        List<ContractStatus> inProcessStatuses = Arrays.asList(
                ContractStatus.PENDING_COUNTERSIGN,
                ContractStatus.PENDING_APPROVAL,
                ContractStatus.PENDING_SIGNING,
                ContractStatus.PENDING_FINALIZATION
        );

        if (isAdmin) {
            return contractRepository.getDashboardStatisticsForAdmin(today, futureDate, inProcessStatuses);
        } else {
            User currentUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new BusinessLogicException("User not found: " + username));
            return contractRepository.getDashboardStatistics(today, futureDate, inProcessStatuses, currentUser);
        }
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
                auditLogService.logAction(username, "ATTACHMENT_ADDED_ON_FINALIZE", "Contract ID " + contractId + " finalized, new attachments added: " + newAttachmentsJson);
            } else if (oldAttachmentPath != null && (newAttachmentsJson == null || newAttachmentsJson.equals("[]"))) {
                auditLogService.logAction(username, "ATTACHMENTS_REMOVED_ON_FINALIZE", "Contract ID " + contractId + " finalized, all attachments removed. Original attachments: " + oldAttachmentPath);
            } else if (oldAttachmentPath != null && newAttachmentsJson != null && !oldAttachmentPath.equals(newAttachmentsJson)) {
                auditLogService.logAction(username, "ATTACHMENTS_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " finalized, attachments updated from " + oldAttachmentPath + " to " + newAttachmentsJson);
            }
        }

        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "Contract ID " + contractId + " content updated during finalization.");
        }

        ContractProcess currentFinalizeProcess = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.FINALIZE, ContractProcessState.PENDING
        ).orElseThrow(() -> new BusinessLogicException("您不是该合同的定稿人或定稿任务不处于待处理状态。"));


        currentFinalizeProcess.setState(ContractProcessState.COMPLETED);
        currentFinalizeProcess.setComments(finalizationComments);
        currentFinalizeProcess.setProcessedAt(LocalDateTime.now());
        currentFinalizeProcess.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(currentFinalizeProcess);

        contract.setUpdatedAt(LocalDateTime.now());

        List<ContractProcess> allFinalizeProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.FINALIZE);

        boolean anyFinalizeRejected = allFinalizeProcesses.stream()
                .anyMatch(p -> p.getState() == ContractProcessState.REJECTED);

        if (anyFinalizeRejected) {
            contract.setStatus(ContractStatus.REJECTED);
            String logDetails = "A finalize process was rejected, contract status changed to Rejected.";
            auditLogService.logAction(username, "CONTRACT_FINALIZE_REJECTED", logDetails);
            contractRepository.save(contract);
            return contract;
        }

        boolean allFinalizeProcessesCompleted = allFinalizeProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED);

        if (allFinalizeProcessesCompleted) {
            contract.setStatus(ContractStatus.PENDING_APPROVAL);
            String logDetails = "Contract ID " + contractId + " (" + contract.getContractName() + ") finalized by user " + username + ", status changed to pending approval. All finalizers completed.";
            if (StringUtils.hasText(finalizationComments)) {
                logDetails += " Finalization comments: " + finalizationComments + ".";
            }
            auditLogService.logAction(username, "CONTRACT_FINALIZED", logDetails);

            // ========== 新增的邮件发送逻辑 ==========
            List<ContractProcess> approvalProcesses = contractProcessRepository
                    .findByContractAndTypeAndState(contract, ContractProcessType.APPROVAL, ContractProcessState.PENDING);

            logger.info("定稿完成，找到 {} 个待处理的审批任务，准备发送邮件通知。", approvalProcesses.size());

            for (ContractProcess approvalProcess : approvalProcesses) {
                User approver = approvalProcess.getOperator();
                sendTaskNotificationEmail(approver, "合同审批", contract);
            }
            // ========== 邮件发送逻辑结束 ==========

        } else {
            String logDetails = "Contract ID " + contractId + " (" + contract.getContractName() + ") finalized by user " + username + ", status remains pending finalization. Waiting for other finalizers.";
            if (StringUtils.hasText(finalizationComments)) {
                logDetails += " Finalization comments: " + finalizationComments + ".";
            }
            auditLogService.logAction(username, "CONTRACT_FINALIZE_PARTIAL", logDetails);
        }

        return contractRepository.save(contract);
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

        boolean anyCountersignRejected = allCountersignProcesses.stream()
                .anyMatch(p -> p.getState() == ContractProcessState.REJECTED);

        if (anyCountersignRejected) {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " A countersign was rejected, contract status changed to Rejected.";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return;
        }

        boolean allCountersignsCompletedAndApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED);

        if (allCountersignsCompletedAndApproved) {
            contract.setStatus(ContractStatus.PENDING_FINALIZATION);
            logDetails += " All countersigns completed and approved, contract enters pending finalization status.";
            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED_TO_FINALIZE", logDetails);

            // ========== FIX: Pass the 'contract' object instead of 'contract.getId()' ==========
            List<ContractProcess> finalizationProcesses = contractProcessRepository
                    .findByContractAndTypeAndState(contract, ContractProcessType.FINALIZE, ContractProcessState.PENDING);

            logger.info("会签完成，找到 {} 个待处理的定稿任务，准备发送邮件通知。", finalizationProcesses.size());

            for (ContractProcess finalizationProcess : finalizationProcesses) {
                User finalizer = finalizationProcess.getOperator();
                sendTaskNotificationEmail(finalizer, "合同定稿", contract);
            }

        } else {
            logDetails += " Current countersign approved, but other countersign processes are still pending. Contract status remains pending countersign.";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }
    /**
     * 发送任务通知邮件的私有辅助方法。
     * @param operator 接收邮件的操作员
     * @param taskType 任务类型（例如 "合同会签"）
     * @param contract 相关的合同
     */
    private void sendTaskNotificationEmail(User operator, String taskType, Contract contract) {
        if (operator != null && StringUtils.hasText(operator.getEmail())) {
            Map<String, Object> context = new HashMap<>();
            context.put("recipientName", operator.getRealName() != null ? operator.getRealName() : operator.getUsername());
            context.put("taskType", taskType);
            context.put("contractName", contract.getContractName());

            // 注意：请根据您的实际部署地址修改 "http://localhost:8080"
            String actionUrl = "http://localhost:8080/dashboard";
            context.put("actionUrl", actionUrl);

            emailService.sendHtmlMessage(
                    operator.getEmail(),
                    "【合同管理系统】您有新的待处理任务：" + taskType,
                    "email/task-notification-email",
                    context
            );
        }
    }

    @Override
    public Path getAttachmentPath(String filename) {
        return attachmentService.getAttachment(filename); //
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus(); //
        Map<String, Long> statistics = new HashMap<>(); //
        for (ContractStatus status : ContractStatus.values()) { //
            statistics.put(status.name(), 0L); //
        }
        for (Object[] result : results) { //
            if (result[0] instanceof ContractStatus && result[1] instanceof Long) { //
                ContractStatus status = (ContractStatus) result[0]; //
                Long count = (Long) result[1]; //
                statistics.put(status.name(), count); //
            }
        }
        return statistics; //
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> searchContracts(String currentUsername, boolean isAdmin, String contractName, String contractNumber, String status, Pageable pageable) {
        Specification<Contract> spec = (Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(); //

            if (StringUtils.hasText(contractName)) { //
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractName")), "%" + contractName.toLowerCase().trim() + "%")); //
            }
            if (StringUtils.hasText(contractNumber)) { //
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("contractNumber")), "%" + contractNumber.toLowerCase().trim() + "%")); //
            }
            if (StringUtils.hasText(status)) { //
                try { //
                    ContractStatus contractStatusEnum = ContractStatus.valueOf(status.toUpperCase()); //
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatusEnum)); //
                } catch (IllegalArgumentException e) { //
                    logger.warn("Invalid status value provided in contract search: {}. Ignoring this status condition.", status); //
                }
            }

            if (!isAdmin && StringUtils.hasText(currentUsername)) { //
                User currentUser = userRepository.findByUsername(currentUsername) //
                        .orElse(null); //

                if (currentUser != null) { //
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser); //

                    Subquery<Long> subquery = query.subquery(Long.class); //
                    Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class); //
                    subquery.select(contractProcessRoot.get("contract").get("id")); //

                    Predicate subqueryPredicate = criteriaBuilder.and( //
                            criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")), //
                            criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser) //
                    );
                    subquery.where(subqueryPredicate); //

                    Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery); //

                    predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInProcess)); //
                } else { //
                    logger.warn("Username '{}' provided for user-specific search, but user not found in database. Query will return no user-specific data.", currentUsername); //
                    predicates.add(criteriaBuilder.disjunction()); //
                }
            }

            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); //
            }

            query.distinct(true); //
            return criteriaBuilder.and(predicates.toArray(new Predicate[0])); //
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //

        contractsPage.getContent().forEach(contract -> { //
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); //
            if (drafter != null) { //
                Hibernate.initialize(drafter); //
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage; //
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
                .orElseThrow(() -> new BusinessLogicException("User not found: " + username)); //

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(); //
            predicates.add(cb.equal(root.get("type"), type)); //
            predicates.add(cb.equal(root.get("state"), state)); //

            if (type != ContractProcessType.EXTENSION_REQUEST || !isAdmin) { //
                predicates.add(cb.equal(root.get("operator"), currentUser)); //
            }


            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            switch (type) { //
                case COUNTERSIGN: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN)); //
                    break; //
                case APPROVAL: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL)); //
                    break; //
                case SIGNING: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING)); //
                    break; //
                case FINALIZE: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION)); //
                    break; //
                case EXTENSION_REQUEST: //
                    predicates.add(cb.or( //
                            cb.equal(contractJoin.get("status"), ContractStatus.ACTIVE), //
                            cb.equal(contractJoin.get("status"), ContractStatus.EXPIRED) //
                    )); //
                    break; //
                default: //
                    break; //
            }

            if (StringUtils.hasText(contractNameSearch)) { //
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%")); //
            }

            if (query.getResultType().equals(ContractProcess.class)) { //
                root.fetch("operator", JoinType.LEFT); //
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); //
                contractFetch.fetch("customer", JoinType.LEFT); //
                contractFetch.fetch("drafter", JoinType.LEFT); //
            }

            return cb.and(predicates.toArray(new Predicate[0])); //
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable); //

        resultPage.getContent().forEach(process -> { //
            Hibernate.initialize(process.getOperator()); //
            User operator = process.getOperator(); //
            if (operator != null) { //
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }

            Hibernate.initialize(process.getContract()); //
            if (process.getContract() != null) { //
                Hibernate.initialize(process.getContract().getCustomer()); //
                User drafter = process.getContract().getDrafter(); //
                if (drafter != null) { //
                    Hibernate.initialize(drafter.getRoles()); //
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
                }
            }
        });
        return resultPage; //
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
            // ==================== 优化点 1: 在构建查询时进行JOIN FETCH ====================
            // 只有在最终查询类型是ContractProcess时才进行fetch，以避免影响count查询
            if (query.getResultType().equals(ContractProcess.class)) {
                // 预先抓取第一层关联
                Fetch<ContractProcess, User> operatorFetch = root.fetch("operator", JoinType.LEFT);
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT);
                contractFetch.fetch("customer", JoinType.LEFT);
                Fetch<Contract, User> drafterFetch = contractFetch.fetch("drafter", JoinType.LEFT);

                // 预先抓取第二层及更深层的关联 (解决N+1的关键)
                // 抓取操作员的角色和功能
                Fetch<User, Role> operatorRolesFetch = operatorFetch.fetch("roles", JoinType.LEFT);
                operatorRolesFetch.fetch("functionalities", JoinType.LEFT);

                // 抓取起草人的角色和功能
                Fetch<User, Role> drafterRolesFetch = drafterFetch.fetch("roles", JoinType.LEFT);
                drafterRolesFetch.fetch("functionalities", JoinType.LEFT);

                // 设置查询为DISTINCT，避免因JOIN产生重复的ContractProcess记录
                query.distinct(true);
            }
            // ============================ JOIN FETCH 结束 ============================

            List<Predicate> finalPredicates = new ArrayList<>();

            // 任务状态必须是“待处理”
            finalPredicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING));

            // 连接到Contract实体以过滤合同状态
            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER);

            // 定义各类任务的条件
            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN),
                    cb.equal(root.get("operator"), currentUser)
            );
            Predicate approvalTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.APPROVAL),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL),
                    cb.equal(root.get("operator"), currentUser)
            );
            Predicate signingTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.SIGNING),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING),
                    cb.equal(root.get("operator"), currentUser)
            );
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE),
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION),
                    cb.equal(root.get("operator"), currentUser)
            );

            List<Predicate> allOrConditions = new ArrayList<>();
            allOrConditions.add(countersignTasks);
            allOrConditions.add(approvalTasks);
            allOrConditions.add(signingTasks);
            allOrConditions.add(finalizeTasks);

            // 如果是管理员，额外添加所有待审批的延期请求
            if (isAdmin) {
                Predicate allPendingExtensionRequests = cb.and(
                        cb.equal(root.get("type"), ContractProcessType.EXTENSION_REQUEST),
                        cb.equal(root.get("state"), ContractProcessState.PENDING)
                );
                allOrConditions.add(allPendingExtensionRequests);
            }

            finalPredicates.add(cb.or(allOrConditions.toArray(new Predicate[0])));

            query.orderBy(cb.desc(root.get("createdAt")));

            return cb.and(finalPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec);


        return tasks;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contract not found, ID: " + id)); //
        Hibernate.initialize(contract.getCustomer()); //
        User drafter = contract.getDrafter(); //
        if (drafter != null) { //
            Hibernate.initialize(drafter); //
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract; //
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .isPresent(); //
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

        if (!approved) {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " Contract rejected, status updated to Rejected.";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return;
        }

        List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL);

        boolean anyApprovalRejected = allApprovalProcesses.stream()
                .anyMatch(p -> p.getState() == ContractProcessState.REJECTED);

        if (anyApprovalRejected) {
            contract.setStatus(ContractStatus.REJECTED);
            logDetails += " An approval was rejected, contract status changed to Rejected.";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return;
        }

        boolean allApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED);

        if (allApprovalsCompletedAndApproved) {
            contract.setStatus(ContractStatus.PENDING_SIGNING);
            logDetails += " All approvals passed, contract enters pending signing status.";
            auditLogService.logAction(username, "CONTRACT_ALL_APPROVED_TO_SIGNING", logDetails);

            // ========== 新增的邮件发送逻辑 ==========
            // 找到所有待处理的签订任务
            List<ContractProcess> signingProcesses = contractProcessRepository
                    .findByContractAndTypeAndState(contract, ContractProcessType.SIGNING, ContractProcessState.PENDING);

            logger.info("审批完成，找到 {} 个待处理的签订任务，准备发送邮件通知。", signingProcesses.size());

            // 为每一位签订人发送邮件
            for (ContractProcess signingProcess : signingProcesses) {
                User signer = signingProcess.getOperator();
                sendTaskNotificationEmail(signer, "合同签订", contract);
            }
            // ========== 邮件发送逻辑结束 ==========

        } else {
            logDetails += " Other approval processes are still pending. Contract status remains pending approval.";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }
    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) {
        ContractProcess process = contractProcessRepository.findById(contractProcessId)
                .orElseThrow(() -> new ResourceNotFoundException("Contract process record not found, ID: " + contractProcessId)); //

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("Current user '" + username + "' not found.")); //

        // **修改开始：在方法内部获取 isAdmin 状态**
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); //
        // **修改结束**

        // 如果不是管理员，且操作员不匹配，则拒绝访问
        // 对于 EXTENSION_REQUEST 类型，管理员可以查看所有请求，不需要是指定操作员
        if (!isAdmin && process.getType() != ContractProcessType.EXTENSION_REQUEST && !process.getOperator().equals(currentUser)) { //
            throw new AccessDeniedException("您 (" + username + ") 不是此合同流程的指定操作员 (ID: " + contractProcessId + //
                    ", 操作员: " + process.getOperator().getUsername() + ")，无权操作。"); //
        }
        // 如果是 EXTENSION_REQUEST 类型，且不是管理员，则拒绝访问
        if (process.getType() == ContractProcessType.EXTENSION_REQUEST && !isAdmin) { //
            throw new AccessDeniedException("只有管理员才能处理延期请求。"); //
        }


        if (process.getType() != expectedType) { //
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() + //
                    ", 实际类型: " + process.getType().getDescription()); //
        }
        if (process.getState() != expectedState) { //
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() + //
                    ", 实际状态: " + process.getState().getDescription()); //
        }

        Hibernate.initialize(process.getContract()); //
        if (process.getContract() != null) { //
            Hibernate.initialize(process.getContract().getCustomer()); //
            User drafter = process.getContract().getDrafter(); //
            if (drafter != null) { //
                Hibernate.initialize(drafter); //
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        }
        User operator = process.getOperator(); //
        if (operator != null) { //
            Hibernate.initialize(operator.getRoles()); //
            operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return process; //
    }



    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

        Contract contract = process.getContract();
        if (contract.getStatus() != ContractStatus.PENDING_SIGNING) {
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，无法签订。必须处于待签订状态。");
        }

        process.setState(ContractProcessState.COMPLETED);
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "用户 " + username + " 完成了合同 ID " + contract.getId() +
                " (" + contract.getContractName() + ") 的签订。签订意见: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING);

        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED);

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE);
            logDetails += " 所有签订流程已完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);

            // --- 通知起草人合同已生效 ---
            User drafter = contract.getDrafter();
            sendTaskNotificationEmail(drafter, "合同已生效", contract);

            // ========== 新增的通知客户逻辑 ==========
            Customer customer = contract.getCustomer();
            if (customer != null && StringUtils.hasText(customer.getEmail())) {
                Map<String, Object> context = new HashMap<>();
                // 您可以在这里定义您的公司名称
                context.put("companyName", "您的公司名称");
                context.put("customerName", customer.getCustomerName());
                context.put("contractName", contract.getContractName());
                context.put("contractNumber", contract.getContractNumber());
                context.put("effectiveDate", LocalDate.now().toString()); // 使用当前日期作为生效日

                emailService.sendHtmlMessage(
                        customer.getEmail(),
                        "【合同生效通知】关于您与" + "您的公司名称" + "的合同：" + contract.getContractName(),
                        "customer-notification-email", // 使用新的客户通知模板
                        context
                );
                logger.info("已向客户 {} ({}) 发送合同生效通知邮件。", customer.getCustomerName(), customer.getEmail());
            } else {
                logger.warn("合同 {} (ID: {}) 的客户或客户邮箱为空，无法发送生效通知邮件。", contract.getContractName(), contract.getId());
            }
            // ========== 逻辑结束 ==========

        } else {
            logDetails += " 其他签订流程仍在进行中。合同状态保持待签订。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User '" + username + "' not found.")); //

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>(); //
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION)); //
            //predicates.add(cb.equal(root.get("drafter"), currentUser)); // 这一行是原代码的起草人过滤，需要移除或修改

            // 新增：检查当前用户是否是FINALIZE类型流程的PENDING状态的操作员
            Subquery<Long> subquery = query.subquery(Long.class); //
            Root<ContractProcess> processRoot = subquery.from(ContractProcess.class); //
            subquery.select(processRoot.get("contract").get("id")); //
            Predicate processPredicate = cb.and( //
                    cb.equal(processRoot.get("contract").get("id"), root.get("id")), //
                    cb.equal(processRoot.get("type"), ContractProcessType.FINALIZE), //
                    cb.equal(processRoot.get("state"), ContractProcessState.PENDING), //
                    cb.equal(processRoot.get("operator"), currentUser) //
            );
            subquery.where(processPredicate); //
            predicates.add(cb.exists(subquery)); // 添加子查询作为条件


            if (StringUtils.hasText(contractNameSearch)) { //
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%")); //
            }

            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); //
            }
            return cb.and(predicates.toArray(new Predicate[0])); //
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //
        contractsPage.getContent().forEach(contract -> { //
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); //
            if (drafter != null) { //
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage; //
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractForFinalization(Long contractId, String username) {
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId)); //

        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) { //
            throw new BusinessLogicException("合同当前状态为" + contract.getStatus().getDescription() + //
                    "，无法进行定稿操作。必须处于待定稿状态。"); //
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户 '" + username + "' 未找到。")); //

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); //

        // **修改开始：定稿权限校验逻辑**
        // 1. 如果是管理员，则直接允许（管理员可以处理任何定稿任务）
        if (isAdmin) { //
            logger.info("管理员 '{}' 访问合同 ID {} 进行定稿操作，允许。", username, contractId); //
        } else { //
            // 2. 如果不是管理员，则检查当前用户是否被分配为该合同的定稿操作员且流程处于 PENDING 状态
            Optional<ContractProcess> finalizeProcess = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                    contractId, username, ContractProcessType.FINALIZE, ContractProcessState.PENDING
            ); //

            if (finalizeProcess.isEmpty()) { //
                logger.warn("用户 '{}' 尝试定稿合同 ID {}，但未被分配为定稿人员或任务不处于待处理状态。访问被拒绝。", username, contractId); //
                throw new AccessDeniedException("您 (" + username + ") 未被指定为该合同的定稿人员，或该定稿任务不处于待处理状态，无权定稿。"); //
            }
            logger.info("用户 '{}' 被指定为合同 ID {} 的定稿人员，允许操作。", username, contractId); //
        }
        // **修改结束**

        User drafter = contract.getDrafter(); //
        if (drafter != null) { //
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract; //
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractCountersignOpinions(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("获取会签意见失败：合同未找到，ID: " + contractId)); //

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //

        countersignProcesses.forEach(process -> { //
            Hibernate.initialize(process.getOperator()); //
        });

        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList()); //
    }

    private Predicate buildUserContractAccessPredicate(String username, boolean isAdmin, Root<Contract> root, CriteriaQuery<?> query, CriteriaBuilder criteriaBuilder) {
        if (isAdmin) {
            return criteriaBuilder.conjunction();
        }

        User currentUser = userRepository.findByUsername(username)
                .orElse(null);

        if (currentUser == null) {
            logger.warn("为用户特定合同统计提供了用户名 '{}'，但在数据库中未找到该用户。统计将返回零。", username);
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
                        "合同 " + contract.getContractNumber() + " (ID: " + contract.getId() +
                                ") 自动更新为过期状态，原状态: " + contract.getStatus().name() + "，时间: " + today);
                logger.info("合同 {} (ID: {}) 自动更新为过期状态。", contract.getContractName(), contract.getId());
            }
        }
        return updatedCount;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingCountersignContracts(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            if (query.getResultType() != Long.class && query.getResultType() != String.class) {
                root.fetch("contract", JoinType.INNER)
                        .fetch("customer", JoinType.LEFT)
                        .fetch("drafter", JoinType.LEFT);
            }


            List<Predicate> predicates = new ArrayList<>(); //
            predicates.add(cb.equal(root.get("operator"), currentUser)); //
            predicates.add(cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN)); //
            predicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING)); //

            if (StringUtils.hasText(contractNameSearch)) { //
                predicates.add(cb.like(cb.lower(root.get("contract").get("contractName")), //
                        "%" + contractNameSearch.toLowerCase() + "%")); //
            }

            return cb.and(predicates.toArray(new Predicate[0])); //
        };

        return contractProcessRepository.findAll(spec, pageable); //
    }


    @Override
    @Transactional(readOnly = true)
    public Optional<ContractProcess> getContractProcessDetails(Long contractId, String username, ContractProcessType type, ContractProcessState state) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, type, state
        ); //
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllContractProcessesByContractAndType(Contract contract, ContractProcessType type) {
        List<ContractProcess> processes = contractProcessRepository.findByContractAndType(contract, type); //
        processes.forEach(process -> Hibernate.initialize(process.getOperator())); //
        return processes.stream()
                .sorted(Comparator.comparing(ContractProcess::getCreatedAt))
                .collect(Collectors.toList()); //
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserCountersignContract(Long contractId, String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username)); //

        Optional<ContractProcess> processOpt = contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING
        ); //
        return processOpt.isPresent(); //
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractProcessHistory(Long contractId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId)); //

        List<ContractProcess> processes = contractProcessRepository.findByContractOrderByCreatedAtDesc(contract); //

        processes.forEach(process -> { //
            Hibernate.initialize(process.getOperator()); //
            if (process.getOperator() != null) { //
                Hibernate.initialize(process.getOperator().getRoles()); //
            }
        });

        // ⭐ MODIFICATION START: Custom sorting for process history
        // 按照 ContractProcessType 的 code 升序排序，然后按照创建时间升序排序
        processes.sort(Comparator
                .comparing((ContractProcess p) -> p.getType().getCode()) // 根据类型代码排序
                .thenComparing(ContractProcess::getCreatedAt)); // 然后根据创建时间排序

        // ⭐ MODIFICATION END
        return processes; //
    }
    @Override
    @Transactional(readOnly = true)
    public long countInProcessContracts(String username, boolean isAdmin) {
        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(); //

            List<ContractStatus> inProcessStatuses = Arrays.asList( //
                    ContractStatus.PENDING_COUNTERSIGN, //
                    ContractStatus.PENDING_APPROVAL, //
                    ContractStatus.PENDING_SIGNING, //
                    ContractStatus.PENDING_FINALIZATION //
            );
            predicates.add(root.get("status").in(inProcessStatuses)); //

            if (!isAdmin) { //
                User currentUser = userRepository.findByUsername(username)
                        .orElseThrow(() -> new UsernameNotFoundException("用户未找到: " + username)); //

                Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser); //

                Subquery<Long> subquery = query.subquery(Long.class); //
                Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class); //
                subquery.select(contractProcessRoot.get("contract").get("id")); //

                Predicate subqueryPredicate = criteriaBuilder.and( //
                        criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")), //
                        criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser), //
                        criteriaBuilder.equal(contractProcessRoot.get("state"), ContractProcessState.PENDING), //
                        contractProcessRoot.get("type").in( //
                                ContractProcessType.COUNTERSIGN, //
                                ContractProcessType.APPROVAL, //
                                ContractProcessType.SIGNING, //
                                ContractProcessType.FINALIZE, //
                                ContractProcessType.EXTENSION_REQUEST //
                        )
                );
                subquery.where(subqueryPredicate); //

                Predicate isInvolvedInPendingProcess = criteriaBuilder.exists(subquery); //

                predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInPendingProcess)); //
            }

            query.distinct(true); //

            return criteriaBuilder.and(predicates.toArray(new Predicate[0])); //
        };

        return contractRepository.count(spec); //
    }

    @Override
    @Transactional
    public Contract extendContract(Long contractId, LocalDate newEndDate, String comments, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId)); //

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); //

        if (!isAdmin) { //
            throw new AccessDeniedException("只有管理员才能直接延期合同。请通过操作员延期请求流程。"); //
        }

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，无法直接延期。合同必须是“有效”或“过期”状态。"); //
        }

        if (newEndDate.isBefore(contract.getEndDate()) || newEndDate.isEqual(contract.getEndDate())) { //
            throw new BusinessLogicException("新的到期日期必须晚于原到期日期 (" + contract.getEndDate() + ")。"); //
        }
        if (newEndDate.isBefore(LocalDate.now())) { //
            throw new BusinessLogicException("新的到期日期不能是过去的日期。"); //
        }


        contract.setEndDate(newEndDate); //
        if (contract.getStatus() == ContractStatus.EXPIRED) { //
            contract.setStatus(ContractStatus.ACTIVE); //
        }
        contract.setUpdatedAt(LocalDateTime.now()); //
        Contract updatedContract = contractRepository.save(contract); //

        User adminUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("管理员用户 '" + username + "' 未找到。")); //
        ContractProcess process = new ContractProcess(); //
        process.setContract(updatedContract); //
        process.setContractNumber(updatedContract.getContractNumber()); //
        process.setType(ContractProcessType.EXTENSION); //
        process.setState(ContractProcessState.COMPLETED); //
        process.setOperator(adminUser); //
        process.setOperatorUsername(username); //
        process.setComments("管理员直接延期。原到期日期: " + contract.getEndDate() + ", 新到期日期: " + newEndDate + (StringUtils.hasText(comments) ? ". 备注: " + comments : "")); //
        // ⭐ 修改点：将管理员直接延期意见存储到新字段中 ⭐
        process.setAdminApprovalComments(comments); // 将管理员的备注存储到该字段
        process.setProcessedAt(LocalDateTime.now()); //
        process.setCompletedAt(LocalDateTime.now()); //
        contractProcessRepository.save(process); //

        auditLogService.logAction(username, "CONTRACT_EXTENDED_ADMIN",
                "管理员直接延期合同 ID: " + contractId + " (" + contract.getContractName() + ")，新到期日期: " + newEndDate); //

        return updatedContract; //
    }

    @Override
    @Transactional
    public ContractProcess requestExtendContract(Long contractId, LocalDate requestedNewEndDate, String reason, String comments, String username) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId)); //

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isContractOperator = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_CONTRACT_OPERATOR")); //

        if (!isContractOperator && !authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) { //
            throw new AccessDeniedException("只有合同操作员或管理员才能提交合同延期请求。"); //
        }

        if (contract.getStatus() != ContractStatus.ACTIVE && contract.getStatus() != ContractStatus.EXPIRED) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，无法提交延期请求。合同必须是“有效”或“过期”状态。"); //
        }

        if (requestedNewEndDate.isBefore(contract.getEndDate()) || requestedNewEndDate.isEqual(contract.getEndDate())) { //
            throw new BusinessLogicException("期望新的到期日期必须晚于原到期日期 (" + contract.getEndDate() + ")。"); //
        }
        if (requestedNewEndDate.isBefore(LocalDate.now())) { //
            throw new BusinessLogicException("期望新的到期日期不能是过去的日期。"); //
        }

        boolean hasPendingExtensionRequest = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(contractId, username, ContractProcessType.EXTENSION_REQUEST, ContractProcessState.PENDING)
                .isPresent(); //

        if (hasPendingExtensionRequest) { //
            throw new BusinessLogicException("您已为此合同提交过延期请求，请勿重复提交。"); //
        }

        User requestingUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("操作员用户 '" + username + "' 未找到。")); //

        ContractProcess process = new ContractProcess(); //
        process.setContract(contract); //
        process.setContractNumber(contract.getContractNumber()); //
        process.setType(ContractProcessType.EXTENSION_REQUEST); //
        process.setState(ContractProcessState.PENDING); //
        process.setOperator(requestingUser); //
        process.setOperatorUsername(username); //

        // ⭐ 修改点：将延期请求的详细信息存储到新字段中 ⭐
        process.setRequestedNewEndDate(requestedNewEndDate); //
        process.setRequestReason(reason); //
        process.setRequestAdditionalComments(comments); // 这里的 comments 就是附加备注

        // comments 字段可以保留原始拼接字符串作为日志或概要，但详细数据在新字段
        StringBuilder commentsBuilder = new StringBuilder(); //
        commentsBuilder.append("请求将合同到期日期从 ").append(contract.getEndDate()).append(" 延期至 ").append(requestedNewEndDate).append("。"); //
        commentsBuilder.append("原因: ").append(StringUtils.hasText(reason) ? reason : "无").append("."); //
        if (StringUtils.hasText(comments)) { //
            commentsBuilder.append(" 附加备注: ").append(comments).append("."); //
        } else { //
            commentsBuilder.append(" 附加备注: ").append("无。"); // 确保始终有这一部分
        }
        process.setComments(commentsBuilder.toString()); //

        ContractProcess savedProcess = contractProcessRepository.save(process); //

        auditLogService.logAction(username, "CONTRACT_EXTENSION_REQUESTED",
                "操作员请求延期合同 ID: " + contractId + " (" + contract.getContractName() + ")，请求延期至: " + requestedNewEndDate + "。原因: " + reason); //

        return savedProcess; //
    }

    @Override
    @Transactional
    public ContractProcess processExtensionRequest(Long processId, String username, boolean isApproved, String comments) {
        ContractProcess requestProcess = contractProcessRepository.findById(processId)
                .orElseThrow(() -> new ResourceNotFoundException("延期请求未找到，ID: " + processId)); //

        if (requestProcess.getType() != ContractProcessType.EXTENSION_REQUEST || requestProcess.getState() != ContractProcessState.PENDING) { //
            throw new BusinessLogicException("此流程 (ID: " + processId + ") 不是有效的待处理延期请求。当前类型: " + //
                    requestProcess.getType().getDescription() + ", 状态: " + requestProcess.getState().getDescription()); //
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); //
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); //

        if (!isAdmin) { //
            throw new AccessDeniedException("只有管理员才能审批延期请求。"); //
        }

        User adminUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("审批管理员用户 '" + username + "' 未找到。")); //

        Contract contract = requestProcess.getContract(); //
        if (contract == null) { //
            throw new BusinessLogicException("延期请求关联的合同不存在。流程ID: " + processId); //
        }

        // ⭐ 移除解析原始 comments 字段的逻辑，因为现在数据存在新字段中 ⭐
        // Map<String, String> parsedComments = parseExtensionRequestComments(requestProcess.getComments());
        // LocalDate requestedNewEndDate = null;
        // try {
        //     if (parsedComments.get("requestedNewEndDate") != null && !parsedComments.get("requestedNewEndDate").isEmpty()) {
        //         requestedNewEndDate = LocalDate.parse(parsedComments.get("requestedNewEndDate"), DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        //     }
        // } catch (Exception e) {
        //     logger.error("从解析后的延期请求评论中获取期望的新到期日期失败：'{}'", parsedComments.get("requestedNewEndDate"), e);
        //     throw new BusinessLogicException("延期请求内容格式错误，无法解析期望的到期日期。");
        // }

        // ⭐ 修改点：直接从 requestProcess.getRequestedNewEndDate() 获取日期 ⭐
        LocalDate requestedNewEndDate = requestProcess.getRequestedNewEndDate(); //

        if (requestedNewEndDate == null) { //
            // 如果旧数据没有requestedNewEndDate，或者通过某种方式丢失了，这里可以回退到解析旧comments
            // 但理想情况是，有了新字段后，此处不应再是null
            logger.warn("延期请求记录 (ID: {}) 中的 requestedNewEndDate 字段为 null，尝试从旧 comments 字段解析。", processId); //
            Map<String, String> oldParsedComments = parseExtensionRequestComments(requestProcess.getComments()); //
            try { //
                if (oldParsedComments.get("requestedNewEndDate") != null && !oldParsedComments.get("requestedNewEndDate").isEmpty() && !"(无)".equals(oldParsedComments.get("requestedNewEndDate"))) { //
                    requestedNewEndDate = LocalDate.parse(oldParsedComments.get("requestedNewEndDate"), DateTimeFormatter.ofPattern("yyyy-MM-dd")); //
                    logger.info("成功从旧 comments 字段解析出 requestedNewEndDate: {}", requestedNewEndDate); //
                }
            } catch (Exception e) { //
                logger.error("回退到旧 comments 字段解析 requestedNewEndDate 失败: {}", oldParsedComments.get("requestedNewEndDate"), e); //
            }
            if (requestedNewEndDate == null) { //
                throw new BusinessLogicException("延期请求中未包含有效的期望到期日期。"); //
            }
        }

        // 获取所有待审批的延期请求流程
        List<ContractProcess> allPendingExtensionRequests = contractProcessRepository.findByContractIdAndTypeAndState(
                contract.getId(), ContractProcessType.EXTENSION_REQUEST, ContractProcessState.PENDING
        ); //

        // 检查是否有任何延期请求被拒绝 (包括刚处理的，以及之前可能有的)
        boolean anyExtensionRequestRejected = allPendingExtensionRequests.stream()
                .anyMatch(p -> p.getState() == ContractProcessState.REJECTED); //

        requestProcess.setProcessedAt(LocalDateTime.now()); //
        requestProcess.setCompletedAt(LocalDateTime.now()); //
        requestProcess.setOperator(adminUser); //
        requestProcess.setOperatorUsername(username); //

        // ⭐ 修改点：将管理员的审批意见存储到新字段中 ⭐
        requestProcess.setAdminApprovalComments(comments); // 将管理员的comments存储到新字段

        // 更新 comments 字段，以反映审批结果和意见，不再包含请求的详细信息
        String newCommentsForProcessRecord = "管理员审批" + (isApproved ? "批准" : "拒绝") + "："; //
        newCommentsForProcessRecord += (StringUtils.hasText(comments) ? comments : "无。"); //
        requestProcess.setComments(newCommentsForProcessRecord); // 更新主要comments字段为审批结果和意见

        String auditLogAction; //
        String auditLogDetails; //

        if (isApproved) { //
            requestProcess.setState(ContractProcessState.APPROVED); //

            if (requestedNewEndDate.isBefore(contract.getEndDate()) || requestedNewEndDate.isEqual(contract.getEndDate())) { //
                auditLogAction = "CONTRACT_EXTENSION_APPROVAL_INVALID_DATE"; // 定义新的日志类型
                auditLogDetails = "管理员批准延期请求，但新日期早于或等于原日期，合同未延期。合同 ID: " + contract.getId() + " (" + contract.getContractName() + ")，请求新日期: " + requestedNewEndDate + "，原日期: " + contract.getEndDate() + "。审批意见: " + (comments != null ? comments : "无"); //
                // 审批通过，但日期不合法，不更新合同日期，流程仍然标记为APPROVED（表示审批已完成）
                logger.warn("管理员批准延期请求，但新日期 {} 早于或等于原日期 {}，合同 ID: {} 未延期。", requestedNewEndDate, contract.getEndDate(), contract.getId()); //
            } else if (requestedNewEndDate.isBefore(LocalDate.now())) { //
                auditLogAction = "CONTRACT_EXTENSION_APPROVAL_PAST_DATE"; // 定义新的日志类型
                auditLogDetails = "管理员批准延期请求，但新日期是过去日期，合同未延期。合同 ID: " + contract.getId() + " (" + contract.getContractName() + ")，请求新日期: " + requestedNewEndDate + "。审批意见: " + (comments != null ? comments : "无"); //
                logger.warn("管理员批准延期请求，但新日期 {} 是过去日期，合同 ID: {} 未延期。", requestedNewEndDate, contract.getId()); //
            }
            else if (anyExtensionRequestRejected) { //
                // 如果在任何延期请求被拒绝之后，即使当前的请求被批准，也不应该延期合同
                auditLogAction = "CONTRACT_EXTENSION_APPROVAL_REJECTED_PREVIOUSLY"; // 定义新的日志类型
                auditLogDetails = "管理员批准延期请求，但此前有其他延期请求被拒绝，合同未延期。合同 ID: " + contract.getId() + " (" + contract.getContractName() + ")，请求新日期: " + requestedNewEndDate + "。审批意见: " + (comments != null ? comments : "无"); //
                logger.warn("合同 ID: {} 延期请求批准，但此前有其他请求被拒绝，合同未延期。", contract.getId()); //
            }
            else { //
                contract.setEndDate(requestedNewEndDate); //
                if (contract.getStatus() == ContractStatus.EXPIRED) { //
                    contract.setStatus(ContractStatus.ACTIVE); //
                }
                contract.setUpdatedAt(LocalDateTime.now()); //
                contractRepository.save(contract); //

                auditLogAction = "CONTRACT_EXTENSION_APPROVED"; //
                auditLogDetails = "管理员批准合同 ID: " + contract.getId() + " (" + contract.getContractName() + //
                        ") 的延期请求，新到期日期: " + requestedNewEndDate + "。审批意见: " + (comments != null ? comments : "无"); //
            }

        } else { //
            requestProcess.setState(ContractProcessState.REJECTED); //
            auditLogAction = "CONTRACT_EXTENSION_REJECTED"; //
            auditLogDetails = "管理员拒绝合同 ID: " + contract.getId() + " (" + contract.getContractName() + //
                    ") 的延期请求。审批意见: " + (comments != null ? comments : "无"); //
        }

        ContractProcess savedProcess = contractProcessRepository.save(requestProcess); //
        auditLogService.logAction(username, auditLogAction, auditLogDetails); //
        logger.info("管理员 '{}' 处理延期请求 {}: {}。合同 ID: {}", username, processId, (isApproved ? "批准" : "拒绝"), contract.getId()); //

        return savedProcess; //
    }

    @Override
    public Map<String, String> parseExtensionRequestComments(String comments) {
        // ⭐ 修改点：此方法在新的数据模型下，不再是提取延期请求信息的主要方式。
        //    它现在主要用于兼容旧数据，或者在 comments 字段仍然存储拼接字符串时进行解析。
        //    如果所有数据都已迁移到新字段，此方法可以简化或移除。
        //    为了兼容性，暂时保留解析逻辑。
        Map<String, String> parsed = new HashMap<>(); //
        String requestedNewEndDate = null; //
        String reason = null; //
        String additionalComments = null; //

        if (StringUtils.hasText(comments)) { //
            // 查找期望新日期
            int dateStartIdx = comments.indexOf("延期至 "); //
            int reasonStartKeywordIdx = comments.indexOf("。原因: "); //
            int additionalCommentsStartKeywordIdx = comments.indexOf(". 附加备注: "); //

            if (dateStartIdx != -1) { //
                dateStartIdx += "延期至 ".length(); //
                int dateEndIdx = -1; //

                if (reasonStartKeywordIdx != -1 && reasonStartKeywordIdx > dateStartIdx) { //
                    dateEndIdx = reasonStartKeywordIdx; //
                }
                // else if (comments.length() > dateStartIdx) { // 如果没有原因关键字，就取到字符串末尾（不太可能）
                //     dateEndIdx = comments.length();
                // }

                if (dateEndIdx != -1) { //
                    requestedNewEndDate = comments.substring(dateStartIdx, dateEndIdx).trim(); //
                    // 移除可能的中文句号
                    if (requestedNewEndDate.endsWith("。")) { //
                        requestedNewEndDate = requestedNewEndDate.substring(0, requestedNewEndDate.length() - 1); //
                    }
                }
            }

            // 查找原因和附加备注
            if (reasonStartKeywordIdx != -1) { //
                reasonStartKeywordIdx += "。原因: ".length(); //

                if (additionalCommentsStartKeywordIdx != -1 && additionalCommentsStartKeywordIdx > reasonStartKeywordIdx) { //
                    reason = comments.substring(reasonStartKeywordIdx, additionalCommentsStartKeywordIdx).trim(); //
                    additionalComments = comments.substring(additionalCommentsStartKeywordIdx + ". 附加备注: ".length()).trim(); //
                    // 移除附加备注末尾的句号，如果存在
                    if (additionalComments.endsWith("。")) { //
                        additionalComments = additionalComments.substring(0, additionalComments.length() - 1); //
                    }
                } else { //
                    // 没有“附加备注”部分，原因取到字符串末尾
                    // 将此行从 `reason = comments.substring(reasonStartIdx).trim();`
                    // 修改为 `reason = comments.substring(reasonStartKeywordIdx).trim();`
                    reason = comments.substring(reasonStartKeywordIdx).trim(); //
                    // 移除原因末尾的句号，如果存在
                    if (reason.endsWith("。")) { //
                        reason = reason.substring(0, reason.length() - 1); //
                    }
                    additionalComments = "(无)"; // 明确表示没有附加备注
                }
            } else { //
                reason = "(无法解析原因)"; //
                additionalComments = "(无法解析备注)"; //
            }
        } else { //
            requestedNewEndDate = "(无)"; //
            reason = "(无)"; //
            additionalComments = "(无)"; //
        }

        parsed.put("requestedNewEndDate", requestedNewEndDate); //
        parsed.put("reason", reason); //
        parsed.put("additionalComments", additionalComments); //

        logger.debug("Parsed extension request comments: {}", parsed); //
        return parsed; //
    }
}