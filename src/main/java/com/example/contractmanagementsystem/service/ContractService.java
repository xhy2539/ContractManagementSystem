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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct; // <--- 确保导入这个注解
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;

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
import java.util.List;
// import java.util.Objects; // 如果未使用可以移除，保持代码整洁
import java.util.UUID;

@Service
public class ContractService {

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
    public ContractService(ContractRepository contractRepository,
                           CustomerRepository customerRepository,
                           UserRepository userRepository,
                           ContractProcessRepository contractProcessRepository) {
        this.contractRepository = contractRepository;
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.contractProcessRepository = contractProcessRepository;
        // 构造函数中不再处理 uploadDirPropertyValue
    }

    @PostConstruct
    public void init() {
        // 这个方法会在所有依赖注入（包括 @Value）完成后由 Spring 自动调用
        if (uploadDirPropertyValue == null || uploadDirPropertyValue.trim().isEmpty()) {
            // 如果配置缺失或为空，抛出异常或使用默认值
            // 考虑使用日志记录此错误
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
            // 验证目录是否可写
            if (!Files.isWritable(this.rootUploadPath)) {
                System.err.println("警告：附件上传目录不可写: " + this.rootUploadPath.toString());
                // 根据需求，这里可能也需要抛出异常阻止应用启动
                throw new IllegalStateException("附件上传目录不可写: " + this.rootUploadPath.toString() + "，请检查权限！");
            }

        } catch (IOException e) {
            // 考虑使用 SLF4J 等日志框架记录错误
            System.err.println("错误：无法创建或访问附件上传目录 '" + uploadDirPropertyValue + "': " + e.getMessage());
            throw new RuntimeException("无法创建或访问附件上传目录: " + uploadDirPropertyValue, e);
        } catch (Exception e) { // 捕获其他可能的路径解析异常
            System.err.println("错误：解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败: " + e.getMessage());
            throw new RuntimeException("解析附件上传目录路径 '" + uploadDirPropertyValue + "' 失败", e);
        }
    }

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
        } else if (request.getCustomer() != null && request.getCustomer().getCustomerName() != null && !request.getCustomer().getCustomerName().isEmpty()) {
            final Customer requestCustomerData = request.getCustomer(); // 避免在 lambda 中多次访问 request.getCustomer()
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
        String attachmentFilename = null; // 只存储文件名，不含路径
        if (attachment != null && !attachment.isEmpty()) {
            String contentType = attachment.getContentType();
            if (contentType == null || !ALLOWED_FILE_TYPES.contains(contentType.toLowerCase())) { // 比较时转为小写更健壮
                throw new BusinessLogicException("附件格式不正确。当前格式: " + contentType + "。只允许上传PDF、图片、Word文件。");
            }

            String originalFilename = attachment.getOriginalFilename();
            String fileExtension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                fileExtension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            // 使用UUID确保文件名唯一性
            String newFilename = UUID.randomUUID().toString() + fileExtension;
            Path filePath = this.rootUploadPath.resolve(newFilename); // 使用初始化好的 rootUploadPath
            Files.copy(attachment.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            attachmentFilename = newFilename; // 保存不带路径的文件名
        }

        // 5. 构建合同实体
        Contract contract = new Contract();
        contract.setContractName(request.getContractName());
        String contractNumber = "CON-" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        contract.setContractNumber(contractNumber);
        contract.setCustomer(customer); // 使用前面处理过的 customer 对象
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setContent(request.getContractContent());
        contract.setStatus(ContractStatus.DRAFT);
        contract.setDrafter(drafter);
        contract.setAttachmentPath(attachmentFilename); // 保存文件名或相对路径
        contract.setCreatedAt(LocalDateTime.now());

        return contractRepository.save(contract);
    }

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

            if (contractNameSearch != null && !contractNameSearch.trim().isEmpty()) {
                Join<ContractProcess, Contract> contractJoin = root.join("contract"); // 'contract' 是 ContractProcess 中的字段名
                predicates.add(cb.like(contractJoin.get("contractName"), "%" + contractNameSearch.trim() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return contractProcessRepository.findAll(spec, pageable);
    }

    public Path getAttachmentPath(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new BusinessLogicException("请求的附件文件名不能为空。");
        }
        // 防范路径遍历攻击，尽管resolve().normalize()有一定作用，但最好确保filename不包含路径分隔符
        if (filename.contains("/") || filename.contains("\\")) {
            throw new BusinessLogicException("无效的附件文件名格式。");
        }
        Path filePath = this.rootUploadPath.resolve(filename).normalize();
        // 再次检查解析后的路径是否仍然在预期的上传目录下，防止 ../ 攻击逃逸
        if (!filePath.startsWith(this.rootUploadPath)) {
            throw new BusinessLogicException("试图访问上传目录之外的文件。");
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new BusinessLogicException("附件文件不存在或不可读: " + filename);
        }
        return filePath;
    }
}