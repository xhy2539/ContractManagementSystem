package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.ContractDraftRequest;
import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractProcess;
import com.example.contractmanagementsystem.entity.ContractProcessState;
import com.example.contractmanagementsystem.entity.ContractProcessType;
import com.example.contractmanagementsystem.entity.ContractStatus;
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.repository.ContractProcessRepository;
import com.example.contractmanagementsystem.repository.ContractRepository;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import com.example.contractmanagementsystem.repository.UserRepository;

import jakarta.annotation.PostConstruct; // 确保导入这个注解
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils; // 使用 Spring 的 StringUtils
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
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 合同管理业务接口的实现类。
 * 负责处理合同相关的业务逻辑，包括文件上传、客户和用户查找、合同起草、查询和统计等。
 */
@Service
public class ContractServiceImpl implements ContractService {

    @Value("${file.upload-dir}")
    private String uploadDirPropertyValue; // 重命名以区分，表示这是从属性文件读取的原始值

    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ContractProcessRepository contractProcessRepository;

    private Path rootUploadPath; // 存储解析后的绝对路径，供后续使用

    // 允许的附件文件类型
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
        // 构造函数中不再处理 uploadDirPropertyValue，因为它此时可能还未被Spring注入
    }

    /**
     * Spring Bean 初始化后执行此方法，用于确保文件上传目录存在。
     * {@code @Value} 注入在此刻已完成。
     */
    @PostConstruct
    public void init() {
        if (uploadDirPropertyValue == null || uploadDirPropertyValue.trim().isEmpty()) {
            System.err.println("错误：文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
            throw new IllegalStateException("文件上传目录 'file.upload-dir' 未在 application.properties 中配置或配置为空！");
        }
        try {
            // 解析路径并标准化
            this.rootUploadPath = Paths.get(uploadDirPropertyValue).toAbsolutePath().normalize();
            if (!Files.exists(this.rootUploadPath)) {
                Files.createDirectories(this.rootUploadPath);
                System.out.println("信息：附件上传目录已创建: " + this.rootUploadPath.toString());
            } else {
                System.out.println("信息：附件上传目录已存在: " + this.rootUploadPath.toString());
            }
            // 验证目录是否可写
            if (!Files.isWritable(this.rootUploadPath)) {
                System.err.println("警告：附件上传目录不可写: " + this.rootUploadPath.toString());
                throw new IllegalStateException("附件上传目录不可写: " + this.rootUploadPath.toString() + "，请检查权限！");
            }

        } catch (IOException e) {
            System.err.println("错误：无法创建或访问附件上传目录 '" + uploadDirPropertyValue + "': " + e.getMessage());
            throw new RuntimeException("无法创建或访问附件上传目录: " + uploadDirPropertyValue, e);
        } catch (Exception e) { // 捕获其他可能的路径解析异常
            System.err.println("错误：解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败: " + e.getMessage());
            throw new RuntimeException("解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败", e);
        }
    }

    /**
     * @see ContractService#draftContract(ContractDraftRequest, MultipartFile, String)
     */
    @Override
    @Transactional
    public Contract draftContract(ContractDraftRequest request, MultipartFile attachment, String username) throws IOException {
        // 1. 验证日期
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessLogicException("合同开始日期不能晚于结束日期！");
        }

        // 2. 处理客户信息
        Customer customer;
        if (request.getCustomer() != null && request.getCustomer().getId() != null) {
            customer = customerRepository.findById(request.getCustomer().getId())
                    .orElseThrow(() -> new BusinessLogicException("指定的客户ID " + request.getCustomer().getId() + " 不存在。"));
        } else if (request.getCustomer() != null && StringUtils.hasText(request.getCustomer().getCustomerName())) {
            final Customer requestCustomerData = request.getCustomer();
            customer = customerRepository.findByCustomerName(requestCustomerData.getCustomerName())
                    .orElseGet(() -> {
                        Customer newCustomer = new Customer();
                        newCustomer.setCustomerName(requestCustomerData.getCustomerName());
                        newCustomer.setCustomerNumber("CUST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
                        newCustomer.setAddress(requestCustomerData.getAddress());
                        newCustomer.setPhoneNumber(requestCustomerData.getPhoneNumber());
                        newCustomer.setEmail(requestCustomerData.getEmail());
                        return customerRepository.save(newCustomer);
                    });
        } else {
            throw new BusinessLogicException("客户信息不完整（ID或名称必须提供一个），无法起草合同。");
        }

        // 3. 查找起草人用户
        User drafter = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessLogicException("起草人用户 '" + username + "' 不存在。"));

        // 4. 保存附件
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
            Path filePath = this.rootUploadPath.resolve(newFilename); // 使用初始化好的 rootUploadPath
            Files.copy(attachment.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            attachmentFilename = newFilename;
        }

        // 5. 构建合同实体
        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumber = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumber);
        contract.setCustomer(customer);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setStatus(ContractStatus.DRAFT);
        contract.setDrafter(drafter);
        contract.setAttachmentPath(attachmentFilename);
        contract.setCreatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

    /**
     * @see ContractService#getContractStatusStatistics()
     */
    @Override
    public Map<String, Long> getContractStatusStatistics() {
        List<Contract> allContracts = contractRepository.findAll();

        Map<ContractStatus, Long> statusStatistics = allContracts.stream()
                .collect(Collectors.groupingBy(
                        Contract::getStatus,
                        Collectors.counting()
                ));

        Map<String, Long> completeStatistics = new HashMap<>();
        for (ContractStatus status : ContractStatus.values()) {
            completeStatistics.put(status.name(), statusStatistics.getOrDefault(status, 0L));
        }

        return completeStatistics;
    }

    /**
     * @see ContractService#searchContracts(String, String, String, Pageable)
     */
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
                    ContractStatus contractStatus = ContractStatus.valueOf(status.toUpperCase()); // 转换为大写以匹配枚举名
                    predicates.add(criteriaBuilder.equal(root.get("status"), contractStatus));
                } catch (IllegalArgumentException e) {
                    // 如果状态值无效，忽略这个条件，也可以抛出异常
                    // throw new BusinessLogicException("无效的合同状态: " + status);
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        return contractRepository.findAll(spec, pageable);
    }

    /**
     * @see ContractService#getPendingProcessesForUser(String, ContractProcessType, ContractProcessState, String, Pageable)
     */
    @Override
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
                Join<ContractProcess, Contract> contractJoin = root.join("contract"); // 'contract' 是 ContractProcess 中的字段名
                predicates.add(cb.like(contractJoin.get("contractName"), "%" + contractNameSearch.trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractProcessRepository.findAll(spec, pageable);
    }

    /**
     * @see ContractService#getAttachmentPath(String)
     */
    @Override
    public Path getAttachmentPath(String filename) {
        if (!StringUtils.hasText(filename)) {
            throw new BusinessLogicException("请求的附件文件名不能为空。");
        }
        // 防范路径遍历攻击
        // 通过 Paths.get(filename) 确保文件名不包含路径分隔符，如果包含会自动解析为子路径
        // 然后 normalize() 会解析 .. 但不会逃逸到 rootUploadPath 之外，因为我们后面会检查
        Path requestedFile = Paths.get(filename);
        if (requestedFile.isAbsolute() || requestedFile.getNameCount() > 1) { // 检查是否是绝对路径或包含路径分隔符
            throw new BusinessLogicException("无效的附件文件名格式。文件名不能包含路径分隔符或为绝对路径。");
        }

        Path filePath = this.rootUploadPath.resolve(requestedFile).normalize();

        // 再次检查解析后的路径是否仍然在预期的上传目录下，这是防止 ../ 攻击的关键防御
        if (!filePath.startsWith(this.rootUploadPath)) {
            throw new BusinessLogicException("试图访问上传目录之外的文件。");
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessLogicException("附件文件不存在或不可读: " + filename);
        }
        return filePath;
    }
}