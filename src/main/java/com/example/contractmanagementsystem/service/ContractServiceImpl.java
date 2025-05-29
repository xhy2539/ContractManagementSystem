package com.example.contractmanagementsystem.service;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import com.example.contractmanagementsystem.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ContractServiceImpl implements ContractService {

    @Value("${file.upload-dir}")
    private String uploadDirPropertyValue;

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;

    private Path rootUploadPath;

    private static final List<String> ALLOWED_FILE_TYPES = Arrays.asList(
            "application/pdf",
            "image/jpeg", "image/png", "image/gif", "image/bmp",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    @Autowired
    public ContractServiceImpl(ContractRepository contractRepository,
                               CustomerRepository customerRepository,
                               UserRepository userRepository,
                               ContractProcessRepository contractProcessRepository) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
    }

    @PostConstruct
    public void init() {
        if (uploadDirPropertyValue == null || uploadDirPropertyValue.trim().isEmpty()) {
            System.err.println("错误：文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
            throw new IllegalStateException("文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
        }
        try {
            this.rootUploadPath = Paths.get(uploadDirPropertyValue).toAbsolutePath().normalize();
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
                System.out.println("信息：附件上传目录已创建: " + this.rootUploadPath.toString());
            } else {
                System.out.println("信息：附件上传目录已存在: " + this.rootUploadPath.toString());
            }
            if (!Files.isWritable(this.rootUploadPath)) {
                System.err.println("警告：附件上传目录不可写: " + this.rootUploadPath.toString());
                throw new IllegalStateException("附件上传目录不可写: " + this.rootUploadPath.toString() + "，请检查权限！");
            }

        } catch (IOException e) {
            System.err.println("错误：无法创建或访问附件上传目录 '" + uploadDirPropertyValue + "': " + e.getMessage());
            throw new RuntimeException("无法创建或访问附件上传目录: " + uploadDirPropertyValue, e);
        } catch (Exception e) {
            System.err.println("错误：解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败: " + e.getMessage());
            throw new RuntimeException("解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败", e);
        }
    }

    @Override
    @Transactional
    public Contract draftContract(ContractDraftRequest request, MultipartFile attachment, String username) throws IOException {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessLogicException("合同开始日期不能晚于结束日期！");
        }

        Customer resolvedCustomer;
        String customerNumberFromRequest = request.getCustomerNumber().trim();
        String customerNameFromRequest = request.getCustomerName().trim();

        // DTO层面@NotBlank已保证customerNumberFromRequest和customerNameFromRequest不为空

        // 优先使用忽略大小写的客户编号查询
        Optional<Customer> customerByNumberOpt = customerRepository.findByCustomerNumberIgnoreCase(customerNumberFromRequest);

        if (customerByNumberOpt.isPresent()) {
            // 情况1：根据客户编号找到了客户
            resolvedCustomer = customerByNumberOpt.get();
            // 校验找到的客户名称与表单提交的客户名称是否一致（忽略大小写）
            if (!resolvedCustomer.getCustomerName().equalsIgnoreCase(customerNameFromRequest)) {
                throw new BusinessLogicException(
                        "客户编号 '" + customerNumberFromRequest + "' 已存在，但其登记名称 ('" +
                                resolvedCustomer.getCustomerName() + "') 与您输入的名称 ('" +
                                customerNameFromRequest + "') 不匹配。请核实信息。"
                );
            }
            // 名称一致，使用此客户
        } else {
            // 情况2：未根据客户编号找到客户 -> 准备创建新客户
            // 检查此客户名称是否已被其他【不同编号】的客户占用
            // 使用 existsByCustomerNameIgnoreCaseAndCustomerNumberNotIgnoreCase 方法
            if (customerRepository.existsByCustomerNameIgnoreCaseAndCustomerNumberNotIgnoreCase(customerNameFromRequest, customerNumberFromRequest)) {
                throw new BusinessLogicException(
                        "客户名称 '" + customerNameFromRequest + "' 已被系统中具有其他编号的客户使用。" +
                                "如果您要关联现有客户，请使用其正确的客户编号。如果要创建全新客户，请使用一个未被占用的客户名称。"
                );
            }

            // 如果执行到这里，说明：
            // 1. 输入的客户编号在数据库中不存在 (或存在但大小写不同，已被findByCustomerNumberIgnoreCase覆盖)。
            // 2. 输入的客户名称没有被任何【其他不同编号】的客户使用。
            // 因此，可以安全地创建新客户，使用用户提供的编号和名称。
            Customer newCustomer = new Customer();
            newCustomer.setCustomerNumber(customerNumberFromRequest.toUpperCase()); // 建议统一存储为大写
            newCustomer.setCustomerName(customerNameFromRequest);
            // 其他客户信息（如地址、电话、邮箱）如果需要，需从表单和DTO中获取
            // 示例: newCustomer.setAddress(request.getCustomerAddress()); (如果DTO中有这些字段)
            resolvedCustomer = customerRepository.save(newCustomer);
        }

        User drafter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("起草人用户 '" + username + "' 不存在。"));

        String attachmentFilename = null;
        if (attachment != null && !attachment.isEmpty()) {
            String contentType = attachment.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) {
                throw new BusinessLogicException("附件格式不正确。当前格式: " + contentType + "。只允许上传PDF、图片、Word文件。");
            }
            String originalFilename = attachment.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = this.rootUploadPath.resolve(newFilename);
            Files.copy(attachment.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            attachmentFilename = newFilename;
        }

        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumberGen = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumberGen);
        contract.setCustomer(resolvedCustomer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setStatus(ContractStatus.DRAFT);
        contract.setDrafter(drafter);
        contract.setAttachmentPath(attachmentFilename);
        contract.setCreatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

    @Override
    public Map<String, Long> getContractStatusStatistics() {
        List<Contract> allContracts = contractRepository.findAll();
        Map<ContractStatus, Long> statusStatistics = allContracts.stream()
                .collect(Collectors.groupingBy(Contract::getStatus, Collectors.counting()));
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
            if (StringUtils.hasText(contractName)) {
                predicates.add(criteriaBuilder.like(root.get("contractName"), "%" + contractName.trim() + "%"));
            }
            if (StringUtils.hasText(contractNumber)) {
                predicates.add(criteriaBuilder.like(root.get("contractNumber"), "%" + contractNumber.trim() + "%"));
            }
            if (StringUtils.hasText(status)) {
                try {
                    ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase());
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatus));
                } catch (IllegalArgumentException e) {
                    // 如果状态值无效，忽略此条件或抛出异常
                }
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return contractRepository.findAll(spec, pageable);
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
                .orElseThrow(() -> new BusinessLogicException("用户不存在: " + username));
        Specification<ContractProcess> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("type"), type));
            predicates.add(cb.equal(root.get("state"), state));
            predicates.add(cb.equal(root.get("operator"), currentUser));
            if (StringUtils.hasText(contractNameSearch)) {
                Join<ContractProcess, Contract> contractJoin = root.join("contract");
                predicates.add(cb.like(contractJoin.get("contractName"), "%" + contractNameSearch.trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractProcessRepository.findAll(spec, pageable);
    }

    @Override
    public Path getAttachmentPath(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessLogicException("请求的附件文件名不能为空。");
        }
        Path requestedFile = Paths.get(filename);
        if (requestedFile.isAbsolute() || requestedFile.getNameCount() > 1) {
            throw new BusinessLogicException("无效的附件文件名格式。文件名不能包含路径分隔符或为绝对路径。");
        }
        Path filePath = this.rootUploadPath.resolve(requestedFile).normalize();
        if (!filePath.startsWith(this.rootUploadPath)) {
            throw new BusinessLogicException("试图访问上传目录之外的文件。");
        }
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessLogicException("附件文件不存在或不可读: " + filename);
        }
        return filePath;
    }
    @Override
    public ContractProcess getContractProcessByIdAndOperator(Long processId, String username, ContractProcessType expectedType, ContractProcessState expectedState) {
        ContractProcess process = contractProcessRepository.findById(processId)
                .orElseThrow(() -> new BusinessLogicException("合同流程记录未找到。"));

        if (!process.getOperator().getUsername().equals(username)) {
            throw new BusinessLogicException("您无权操作此合同流程。");
        }

        if (!process.getType().equals(expectedType)) {
            throw new BusinessLogicException("合同流程类型不匹配。预期类型: " + expectedType.getDescription() + ", 实际类型: " + process.getType().getDescription());
        }

        if (!process.getState().equals(expectedState)) {
            throw new BusinessLogicException("合同流程状态不正确。预期状态: " + expectedState.getDescription() + ", 实际状态: " + process.getState().getDescription());
        }
        return process;
    }

    // --- 新增：处理合同签订逻辑 ---
    @Override
    public void signContract(Long contractProcessId, String signingOpinion, String username) {
        // 1. 获取并验证合同流程
        ContractProcess process = getContractProcessByIdAndOperator(
                contractProcessId, username, ContractProcessType.SIGNING, ContractProcessState.PENDING);

        // 2. 更新合同流程状态
        process.setState(ContractProcessState.COMPLETED);
        process.setContent(signingOpinion);
        process.setCompletedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        // 3. 更新合同状态
        Contract contract = process.getContract();
        // 在实际业务中，可能需要检查其他所有流程（会签、审批）是否都已完成
        // 这里简化为签订完成后直接将合同状态置为 ACTIVE
        contract.setStatus(ContractStatus.ACTIVE);
        contractRepository.save(contract);


    }

    @Override
    public Contract getContractById(Long id) {
        return contractRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("合同不存在，ID: " + id));
    }

    @Override
    public boolean canUserApproveContract(String username, Long contractId) {
        return contractProcessRepository.findByContractIdAndOperatorUsernameAndTypeAndState(
                contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .isPresent();
    }

    @Override
    @Transactional
    public void processApproval(Long contractId, String username, boolean approved, String comments) {
        // 1. 获取合同和处理记录
        Contract contract = getContractById(contractId);
        ContractProcess process = contractProcessRepository
                .findByContractIdAndOperatorUsernameAndTypeAndState(
                        contractId, username, ContractProcessType.APPROVAL, ContractProcessState.PENDING)
                .orElseThrow(() -> new BusinessLogicException("未找到待处理的审批任务"));

        // 2. 更新处理记录
        process.setState(approved ? ContractProcessState.APPROVED : ContractProcessState.REJECTED);
        process.setComments(comments);
        process.setProcessedAt(LocalDateTime.now());
        contractProcessRepository.save(process);

        // 3. 更新合同状态
        if (approved) {
            // 如果所有审批都通过，将合同状态改为待签订
            if (areAllApprovalsCompleted(contract)) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
                contractRepository.save(contract);
            }
        } else {
            // 如果有任何审批被拒绝，将合同状态改为已拒绝
            contract.setStatus(ContractStatus.REJECTED);
            contractRepository.save(contract);
        }
    }

    private boolean areAllApprovalsCompleted(Contract contract) {
        List<ContractProcess> approvalProcesses = contractProcessRepository
                .findByContractAndType(contract, ContractProcessType.APPROVAL);
        
        // 检查是否所有审批都已完成且通过
        return !approvalProcesses.isEmpty() && 
               approvalProcesses.stream()
                   .allMatch(process -> ContractProcessState.APPROVED.equals(process.getState()));
    }
}