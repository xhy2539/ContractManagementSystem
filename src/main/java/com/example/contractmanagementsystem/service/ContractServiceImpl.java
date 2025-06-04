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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors; // 确保导入 Collectors

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
        // 生成唯一合同编号，可以根据实际需求调整生成规则
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(selectedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent()); // 设置初始内容
        contract.setDrafter(drafter);

        // 处理附件路径列表
        if (!CollectionUtils.isEmpty(request.getAttachmentServerFileNames())) {
            try {
                // 将附件文件名列表序列化为 JSON 字符串存储
                String attachmentsJson = objectMapper.writeValueAsString(request.getAttachmentServerFileNames());
                contract.setAttachmentPath(attachmentsJson);
                logger.info("合同 '{}' 起草时设置附件路径: {}", contract.getContractName(), attachmentsJson);
            } catch (JsonProcessingException e) {
                logger.error("序列化附件文件名列表为JSON时出错 (合同: {}): {}", request.getContractName(), e.getMessage());
                throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
            }
        } else {
            contract.setAttachmentPath(null); // 如果没有附件，设置为 null 或空 JSON 数组 "[]"
        }

        // 合同起草后，状态变更为待分配
        contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);

        Contract savedContract = contractRepository.save(contract);
        String logDetails = "用户 " + username + " 起草了合同: " + savedContract.getContractName() +
                " (ID: " + savedContract.getId() + ")，状态变更为待分配。";
        // 审计日志中也包含附件信息
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            logDetails += " 附件: " + savedContract.getAttachmentPath();
        }
        auditLogService.logAction(username, "CONTRACT_DRAFTED_FOR_ASSIGNMENT", logDetails);
        return savedContract;
    }

    @Override
    @Transactional
    public Contract finalizeContract(Long contractId, String finalizationComments, List<String> attachmentServerFileNames, String updatedContent, String username) throws IOException {
        // 1. 获取合同并进行权限和状态检查
        Contract contract = getContractForFinalization(contractId, username); //
        User finalizer = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("执行定稿操作的用户 '" + username + "' 不存在。"));

        // 2. 处理附件更新
        String oldAttachmentPath = contract.getAttachmentPath();
        String newAttachmentsJson = null;

        if (attachmentServerFileNames != null) { // 如果传入的列表不是 null，说明前端有明确的附件更新意图
            if (!attachmentServerFileNames.isEmpty()) {
                try {
                    newAttachmentsJson = objectMapper.writeValueAsString(attachmentServerFileNames);
                } catch (JsonProcessingException e) {
                    logger.error("序列化附件文件名列表为JSON时出错 (定稿合同ID: {}): {}", contractId, e.getMessage());
                    throw new BusinessLogicException("处理附件信息时出错: " + e.getMessage());
                }
            } else {
                newAttachmentsJson = "[]"; // 明确表示没有附件
            }
            contract.setAttachmentPath(newAttachmentsJson);

            // 记录附件变化到审计日志
            if (oldAttachmentPath == null && newAttachmentsJson != null && !newAttachmentsJson.equals("[]")) {
                logger.info("合同 {} 定稿时，新增附件: {}", contractId, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENT_ADDED_ON_FINALIZE", "合同ID " + contractId + " 定稿时新增附件: " + newAttachmentsJson);
            } else if (oldAttachmentPath != null && (newAttachmentsJson == null || newAttachmentsJson.equals("[]"))) {
                logger.info("合同 {} 定稿时，移除了所有附件 (原附件: {})", contractId, oldAttachmentPath);
                auditLogService.logAction(username, "ATTACHMENTS_REMOVED_ON_FINALIZE", "合同ID " + contractId + " 定稿时移除所有附件。原附件: " + oldAttachmentPath);
            } else if (oldAttachmentPath != null && newAttachmentsJson != null && !oldAttachmentPath.equals(newAttachmentsJson)) {
                logger.info("合同 {} 定稿时，附件由 '{}' 更新为 '{}'", contractId, oldAttachmentPath, newAttachmentsJson);
                auditLogService.logAction(username, "ATTACHMENTS_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 定稿时附件由 " + oldAttachmentPath + " 更新为 " + newAttachmentsJson);
            }
        } // 如果 attachmentServerFileNames 为 null，表示前端没有提供新的附件列表，则不修改现有附件。

        // 3. 处理合同内容更新
        // 只有当传入的 updatedContent 不为空且与现有内容不同时才更新
        if (updatedContent != null && !updatedContent.equals(contract.getContent())) {
            contract.setContent(updatedContent);
            auditLogService.logAction(username, "CONTRACT_CONTENT_UPDATED_ON_FINALIZE", "合同ID " + contractId + " 内容在定稿时被更新。");
            logger.info("合同 {} 定稿时，内容被更新。", contractId);
        }

        // 4. 更新合同状态为待审批
        contract.setStatus(ContractStatus.PENDING_APPROVAL); //
        contract.setUpdatedAt(LocalDateTime.now());

        // 5. 记录定稿流程
        ContractProcess finalizationProcessRecord = new ContractProcess();
        finalizationProcessRecord.setContract(contract);
        finalizationProcessRecord.setContractNumber(contract.getContractNumber());
        finalizationProcessRecord.setType(ContractProcessType.FINALIZE); //
        finalizationProcessRecord.setState(ContractProcessState.COMPLETED); //
        finalizationProcessRecord.setOperator(finalizer);
        finalizationProcessRecord.setOperatorUsername(finalizer.getUsername());
        finalizationProcessRecord.setComments(finalizationComments);
        finalizationProcessRecord.setProcessedAt(LocalDateTime.now());
        finalizationProcessRecord.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(finalizationProcessRecord);

        // 6. 保存合同
        Contract savedContract = contractRepository.save(contract);

        // 7. 记录审计日志
        String details = "合同ID " + contractId + " (“" + contract.getContractName() + "”) 已被用户 " + username + " 定稿，状态变更为“待审批”。";
        if (StringUtils.hasText(finalizationComments)) {
            details += " 定稿意见: “" + finalizationComments + "”。";
        }
        if (StringUtils.hasText(savedContract.getAttachmentPath()) && !savedContract.getAttachmentPath().equals("[]") && !savedContract.getAttachmentPath().equals("null")) {
            details += " 当前附件: " + savedContract.getAttachmentPath() + "。";
        }
        auditLogService.logAction(username, "CONTRACT_FINALIZED", details);

        return savedContract;
    }

    @Override
    @Transactional
    public void processCountersign(Long contractProcessId, String comments, String username, boolean isApproved) {
        // 1. 获取会签流程记录并进行权限和状态检查
        ContractProcess process = getContractProcessByIdAndOperator(contractProcessId, username, ContractProcessType.COUNTERSIGN, ContractProcessState.PENDING); //

        // 2. 更新当前会签流程的状态
        process.setState(isApproved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); // 会签可以是 APPROVED 或 REJECTED
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        Contract contract = process.getContract();
        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = isApproved ? "CONTRACT_COUNTERSIGN_APPROVED" : "CONTRACT_COUNTERSIGN_REJECTED"; // 细分审计类型
        String logDetails = "用户 " + username + (isApproved ? " 批准了" : " 拒绝了") + "合同ID " +
                contract.getId() + " (“" + contract.getContractName() + "”) 的会签。意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        // 3. 根据会签结果和合同其他会签情况更新合同状态
        if (!isApproved) {
            // 如果任何一方会签拒绝，合同直接标记为拒绝
            contract.setStatus(ContractStatus.REJECTED); //
            logDetails += " 合同因会签被拒绝，状态变更为已拒绝。";
            auditLogService.logAction(username, logActionType, logDetails);
            contractRepository.save(contract);
            return; // 流程终止，不再检查其他会签
        }

        // 如果当前会签通过，检查是否所有其他会签也都已通过 (包括已完成的会签，如果 COMPLETED 状态也表示通过)
        List<ContractProcess> allCountersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //
        boolean allRelevantCountersignsApproved = allCountersignProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantCountersignsApproved) {
            // 所有会签都通过后，合同状态进入待定稿
            contract.setStatus(ContractStatus.PENDING_FINALIZATION); //
            logDetails += " 所有会签均完成且通过，合同进入待定稿状态。";
            auditLogService.logAction(username, "CONTRACT_ALL_COUNTERSIGNED_TO_FINALIZE", logDetails); // 新增审计日志类型
        } else {
            // 如果还有其他会签是 PENDING，则合同状态保持不变
            logDetails += " 当前会签已通过，但尚有其他会签流程未完成。合同状态保持待会签。";
            auditLogService.logAction(username, logActionType, logDetails);
        }
        contractRepository.save(contract);
    }


    @Override
    public Path getAttachmentPath(String filename) {
        return attachmentService.getAttachment(filename); //
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> getContractStatusStatistics() {
        List<Object[]> results = contractRepository.findContractCountByStatus(); //
        Map<String, Long> statistics = new HashMap<>();
        // 初始化所有状态的计数为0，确保图表显示完整
        for (ContractStatus status : ContractStatus.values()) { //
            statistics.put(status.name(), 0L);
        }
        for (Object[] result : results) {
            if (result[0] instanceof ContractStatus && result[1] instanceof Long) { //
                ContractStatus status = (ContractStatus) result[0]; //
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
                    ContractStatus contractStatusEnum = ContractStatus.valueOf(status.toUpperCase()); //
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatusEnum));
                } catch (IllegalArgumentException e) {
                    logger.warn("搜索合同中提供了无效的状态值: {}。将忽略此状态条件。", status);
                }
            }

            // 非管理员用户只能看到自己起草或参与的合同流程
            if (!isAdmin && StringUtils.hasText(currentUsername)) {
                User currentUser = userRepository.findByUsername(currentUsername)
                        .orElse(null);

                if (currentUser != null) {
                    Predicate isDrafter = criteriaBuilder.equal(root.get("drafter"), currentUser); //

                    // 子查询：用户是否参与了某个合同的流程 (会签、审批、签订、定稿)
                    Subquery<Long> subquery = query.subquery(Long.class);
                    Root<ContractProcess> contractProcessRoot = subquery.from(ContractProcess.class); //
                    subquery.select(contractProcessRoot.get("contract").get("id")); //

                    Predicate subqueryPredicate = criteriaBuilder.and(
                            criteriaBuilder.equal(contractProcessRoot.get("contract").get("id"), root.get("id")), //
                            criteriaBuilder.equal(contractProcessRoot.get("operator"), currentUser) //
                    );
                    subquery.where(subqueryPredicate);

                    Predicate isInvolvedInProcess = criteriaBuilder.exists(subquery);

                    predicates.add(criteriaBuilder.or(isDrafter, isInvolvedInProcess));
                } else {
                    logger.warn("为用户特定搜索提供了用户名 '{}'，但在数据库中未找到该用户。查询将不返回用户特定数据。", currentUsername);
                    predicates.add(criteriaBuilder.disjunction()); // 相当于 `false`，确保不返回任何数据
                }
            }

            // 确保在执行主查询时，通过 FETCH JOIN 来避免 N+1 问题
            // 只有当查询结果类型是 Contract.class 时才进行 fetch，避免影响 count 查询
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); //
            }

            query.distinct(true); // 避免由于 JOIN 导致的重复行
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //

        // 显式初始化懒加载的集合，以确保在 DTO 转换或 Thymeleaf 渲染时数据可用
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); //
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage;
    }


    @Override
    @Transactional(readOnly = true)
    public Page<ContractProcess> getPendingProcessesForUser(
            String username,
            ContractProcessType type, //
            ContractProcessState state, //
            String contractNameSearch,
            Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户不存在: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));
            predicates.add(cb.equal(root.get("operator"), currentUser));

            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            // 确保合同的自身状态与流程类型一致，提高数据一致性
            switch (type) {
                case COUNTERSIGN: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN)); //
                    break;
                case APPROVAL: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL)); //
                    break;
                case SIGNING: //
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING)); //
                    break;
                case FINALIZE: // 定稿流程，通常由起草人完成，合同状态为 PENDING_FINALIZATION
                    predicates.add(cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION)); //
                    break;
                default:
                    // 对于其他未明确处理的类型，不添加额外状态限制，或者抛出异常
                    break;
            }

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(contractJoin.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // 在主查询中进行 Eager Fetch，避免 N+1 查询问题
            if (query.getResultType().equals(ContractProcess.class)) { //
                root.fetch("operator", JoinType.LEFT); // Fetch 操作员 User
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); // Fetch 关联的 Contract
                contractFetch.fetch("customer", JoinType.LEFT); // Fetch Contract 的 Customer
                contractFetch.fetch("drafter", JoinType.LEFT); // Fetch Contract 的 Drafter (User)
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<ContractProcess> resultPage = contractProcessRepository.findAll(spec, pageable); //

        // 显式初始化懒加载的集合（尽管 fetch 应该已经处理了大部分）
        resultPage.getContent().forEach(process -> {
            Hibernate.initialize(process.getOperator()); //
            User operator = process.getOperator(); //
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }

            Hibernate.initialize(process.getContract()); //
            if (process.getContract() != null) {
                Hibernate.initialize(process.getContract().getCustomer()); //
                User drafter = process.getContract().getDrafter(); //
                if (drafter != null) {
                    Hibernate.initialize(drafter.getRoles()); //
                    drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
                }
            }
        });
        return resultPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getAllPendingTasksForUser(String username) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("用户未找到: " + username)); //

        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> mainPredicates = new ArrayList<>();
            mainPredicates.add(cb.equal(root.get("operator"), currentUser));
            mainPredicates.add(cb.equal(root.get("state"), ContractProcessState.PENDING)); //

            // 连接到 Contract 实体以便根据合同状态过滤
            Join<ContractProcess, Contract> contractJoin = root.join("contract", JoinType.INNER); //

            // 定义每个待处理任务类型的具体条件
            Predicate countersignTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.COUNTERSIGN), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_COUNTERSIGN) //
            );
            Predicate approvalTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.APPROVAL), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_APPROVAL) //
            );
            Predicate signingTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.SIGNING), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_SIGNING) //
            );
            // 对于定稿任务，也可能作为待处理任务显示给起草人
            Predicate finalizeTasks = cb.and(
                    cb.equal(root.get("type"), ContractProcessType.FINALIZE), //
                    cb.equal(contractJoin.get("status"), ContractStatus.PENDING_FINALIZATION) //
            );

            // 组合这些特定的任务条件
            mainPredicates.add(cb.or(countersignTasks, approvalTasks, signingTasks, finalizeTasks));

            // Eager fetching for the main query
            if (query.getResultType().equals(ContractProcess.class)) { //
                Fetch<ContractProcess, Contract> contractFetch = root.fetch("contract", JoinType.LEFT); //
                contractFetch.fetch("customer", JoinType.LEFT); //
                contractFetch.fetch("drafter", JoinType.LEFT); //
                root.fetch("operator", JoinType.LEFT); // Operator (current user)
            }
            query.orderBy(cb.desc(root.get("createdAt"))); // 按照流程创建时间倒序排序

            return cb.and(mainPredicates.toArray(new Predicate[0]));
        };

        List<ContractProcess> tasks = contractProcessRepository.findAll(spec); //
        // 显式初始化懒加载的关联实体
        tasks.forEach(task -> {
            // 确保操作员（流程执行人）的角色和功能被加载
            User operator = task.getOperator(); //
            if (operator != null) {
                Hibernate.initialize(operator.getRoles()); //
                operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
            // 确保合同的起草人及起草人的角色和功能被加载
            if (task.getContract() != null && task.getContract().getDrafter() != null) {
                User drafter = task.getContract().getDrafter(); //
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return tasks;
    }


    @Override
    @Transactional(readOnly = true)
    public Contract getContractById(Long id) {
        Contract contract = contractRepository.findById(id) //
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
        // 显式加载关联实体，以避免懒加载异常
        Hibernate.initialize(contract.getCustomer()); //
        User drafter = contract.getDrafter(); //
        if (drafter != null) {
            Hibernate.initialize(drafter);
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserApproveContract(String username, Long contractId) {
        // 检查是否存在当前用户的待审批任务
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING) //
                .isPresent();
    }


    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        // 1. 获取合同并进行状态检查 (使用带有急加载的方法)
        Contract contract = getContractById(contractId); //

        if (contract.getStatus() != ContractStatus.PENDING_APPROVAL) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行审批。必须处于“待审批”状态。");
        }

        // 2. 查找特定用户在指定合同上的待处理审批任务
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING) //
                .orElseThrow(() -> new AccessDeniedException("未找到您的待处理审批任务，或您无权操作此合同的审批。"));

        // 3. 更新当前审批流程的状态
        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED); //
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());

        String logActionType = approved ? "CONTRACT_APPROVED" : "CONTRACT_REJECTED_APPROVAL";
        String logDetails = "用户 " + username + (approved ? " 批准了" : " 拒绝了") + "合同ID " + contractId +
                " (“" + contract.getContractName() + "”)的审批。审批意见: " +
                (StringUtils.hasText(comments) ? comments : "无");

        // 4. 根据审批结果更新合同主状态
        if (approved) {
            // 检查该合同的所有审批流程是否都已完成且通过
            List<ContractProcess> allApprovalProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.APPROVAL); //
            boolean allRelevantApprovalsCompletedAndApproved = allApprovalProcesses.stream()
                    .allMatch(p -> p.getState() == ContractProcessState.APPROVED || p.getState() == ContractProcessState.COMPLETED); //

            if (allRelevantApprovalsCompletedAndApproved) {
                contract.setStatus(ContractStatus.PENDING_SIGNING); // // 所有审批通过，进入待签订状态
                logDetails += " 所有审批通过，合同进入待签订状态。";
            } else {
                // 如果存在任何一个审批被拒绝，则合同已经变成 REJECTED，这里不再处理。
                // 如果还有审批是 PENDING 的，则合同状态保持不变。
                logDetails += " 尚有其他审批流程未完成或未全部通过。合同状态保持待审批。";
            }
        } else { // 如果当前审批是拒绝
            contract.setStatus(ContractStatus.REJECTED); // // 合同直接变为拒绝状态
            logDetails += " 合同被拒绝，状态更新为已拒绝。";
        }
        contractRepository.save(contract); //
        auditLogService.logAction(username, logActionType, logDetails);
    }


    @Override
    @Transactional(readOnly = true)
    public ContractProcess getContractProcessByIdAndOperator(Long contractProcessId, String username, ContractProcessType expectedType, ContractProcessState expectedState) { //
        ContractProcess process = contractProcessRepository.findById(contractProcessId) //
                .orElseThrow(() -> new ResourceNotFoundException("合同流程记录未找到，ID: " + contractProcessId));

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("当前用户 '" + username + "' 不存在。")); //

        // 权限检查：确保当前用户是该流程记录的指定操作员
        if (!process.getOperator().equals(currentUser)) { //
            throw new AccessDeniedException("您 (" + username + ") 不是此合同流程 (ID: " + contractProcessId +
                    ", 操作员: " + process.getOperator().getUsername() + ") 的指定操作员，无权操作。"); //
        }
        // 业务逻辑检查：确保流程类型和状态符合预期
        if (process.getType() != expectedType) { //
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() +
                    ", 实际类型: " + process.getType().getDescription()); //
        }
        if (process.getState() != expectedState) { //
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() +
                    ", 实际状态: " + process.getState().getDescription()); //
        }

        // 显式加载关联实体，避免懒加载异常
        Hibernate.initialize(process.getContract()); //
        if (process.getContract() != null) {
            Hibernate.initialize(process.getContract().getCustomer()); //
            User drafter = process.getContract().getDrafter(); //
            if (drafter != null) {
                Hibernate.initialize(drafter);
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        }
        User operator = process.getOperator(); //
        if (operator != null) {
            Hibernate.initialize(operator.getRoles()); //
            operator.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return process;
    }


    @Override
    @Transactional
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        // 1. 获取签订流程记录并进行权限和状态检查
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING); //

        Contract contract = process.getContract();
        if (contract.getStatus() != ContractStatus.PENDING_SIGNING) { //
            throw new BusinessLogicException("合同当前状态为 " + contract.getStatus().getDescription() + "，不能进行签订。必须处于“待签订”状态。");
        }

        // 2. 更新当前签订流程的状态
        process.setState(ContractProcessState.COMPLETED); // // 签订流程完成
        process.setComments(signingOpinion);
        process.setProcessedAt(LocalDateTime.now());
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process); //

        contract.setUpdatedAt(LocalDateTime.now());
        String logDetails = "用户 " + username + " 完成了对合同ID " + contract.getId() +
                " (“" + contract.getContractName() + "”) 的签订。签订意见: " +
                (StringUtils.hasText(signingOpinion) ? signingOpinion : "无");

        // 3. 检查该合同的所有签订流程是否都已完成
        List<ContractProcess> allSigningProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.SIGNING); //
        boolean allRelevantSigningsCompleted = allSigningProcesses.stream()
                .allMatch(p -> p.getState() == ContractProcessState.COMPLETED); //

        if (allRelevantSigningsCompleted) {
            contract.setStatus(ContractStatus.ACTIVE); // // 所有签订流程完成，合同变为有效状态
            logDetails += " 所有签订流程完成，合同状态更新为有效。";
            auditLogService.logAction(username, "CONTRACT_ALL_SIGNED_ACTIVE", logDetails);
        } else {
            logDetails += " 尚有其他签订流程未完成。合同状态保持待签订。";
            auditLogService.logAction(username, "CONTRACT_PARTIALLY_SIGNED", logDetails);
        }
        contractRepository.save(contract); //
    }


    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingFinalizationForUser(String username, String contractNameSearch, Pageable pageable) {
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 不存在。")); //

        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 筛选状态为 PENDING_FINALIZATION 的合同
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_FINALIZATION)); //
            // 只有起草人才能定稿自己的合同
            predicates.add(cb.equal(root.get("drafter"), currentUser)); //


            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }

            // 在主查询中进行 Eager Fetch，避免 N+1 查询问题
            if (query.getResultType().equals(Contract.class)) { //
                root.fetch("customer", JoinType.LEFT); //
                root.fetch("drafter", JoinType.LEFT); // 起草人就是当前用户，也可以在这里 Fetch
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable); //
        // 显式初始化懒加载的集合
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer()); //
            User drafter = contract.getDrafter(); // // 应该就是当前用户
            if (drafter != null) {
                Hibernate.initialize(drafter.getRoles()); //
                drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Contract getContractForFinalization(Long contractId, String username) {
        // 使用 findByIdWithCustomerAndDrafter 确保 customer 和 drafter 被急加载
        Contract contract = contractRepository.findByIdWithCustomerAndDrafter(contractId) //
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // 业务逻辑检查：合同必须处于待定稿状态
        if (contract.getStatus() != ContractStatus.PENDING_FINALIZATION) { //
            throw new BusinessLogicException("合同当前状态为“" + contract.getStatus().getDescription() +
                    "”，无法进行定稿操作。必须处于“待定稿”状态。");
        }

        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("当前用户 '" + username + "' 不存在。")); //
        // 权限检查：只有合同的起草人才能进行定稿操作
        if (contract.getDrafter() == null || !contract.getDrafter().equals(currentUser)) { //
            logger.warn("用户 '{}' 尝试定稿合同 ID {}，但该合同由 '{}' 起草，或起草人为空。拒绝访问。",
                    username, contractId, (contract.getDrafter() != null ? contract.getDrafter().getUsername() : "未知")); //
            throw new AccessDeniedException("您 ("+username+") 不是此合同的起草人，无权定稿。");
        }

        // 显式初始化 drafter 的角色和功能，以确保完整性
        User drafter = contract.getDrafter(); //
        if (drafter != null) {
            Hibernate.initialize(drafter.getRoles()); //
            drafter.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities())); //
        }
        return contract;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContractProcess> getContractCountersignOpinions(Long contractId) {
        Contract contract = contractRepository.findById(contractId) //
                .orElseThrow(() -> new ResourceNotFoundException("获取会签意见失败：合同未找到，ID: " + contractId));

        List<ContractProcess> countersignProcesses = contractProcessRepository.findByContractAndType(contract, ContractProcessType.COUNTERSIGN); //

        // 急切加载操作员信息，并按处理时间排序（如果尚未处理，则最后显示）
        countersignProcesses.forEach(process -> {
            Hibernate.initialize(process.getOperator()); // 确保操作员信息被加载
            // 如果操作员还有需要急切加载的关联对象（如角色），也可以在这里处理
            // Hibernate.initialize(process.getOperator().getRoles());
        });

        // 按处理时间排序，nullsLast表示未处理的（processedAt为null）排在后面
        return countersignProcesses.stream()
                .sorted(Comparator.comparing(ContractProcess::getProcessedAt, Comparator.nullsLast(Comparator.naturalOrder()))) //
                .collect(Collectors.toList());
    }


    @Override
    @Transactional(readOnly = true)
    public long countActiveContracts() {
        return contractRepository.countByStatus(ContractStatus.ACTIVE); //
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsExpiringSoon(int days) {
        LocalDate today = LocalDate.now();
        LocalDate futureDate = today.plusDays(days);

        Specification<Contract> spec = (root, query, criteriaBuilder) -> {
            Predicate statusPredicate = criteriaBuilder.equal(root.get("status"), ContractStatus.ACTIVE); //
            Predicate endDateRangePredicate = criteriaBuilder.and(
                    criteriaBuilder.greaterThan(root.get("endDate"), today), // // 必须是今天之后才算“即将到期”
                    criteriaBuilder.lessThanOrEqualTo(root.get("endDate"), futureDate) //
            );
            return criteriaBuilder.and(statusPredicate, endDateRangePredicate);
        };
        return contractRepository.count(spec); //
    }

    @Override
    @Transactional(readOnly = true)
    public long countContractsPendingAssignment() {
        return contractRepository.countByStatus(ContractStatus.PENDING_ASSIGNMENT); //
    }
}