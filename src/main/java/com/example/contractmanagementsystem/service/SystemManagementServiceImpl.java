package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SystemManagementServiceImpl implements SystemManagementService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final FunctionalityRepository functionalityRepository;
    private final ContractRepository contractRepository;
    private final ContractProcessRepository contractProcessRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Autowired
    public SystemManagementServiceImpl(UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       FunctionalityRepository functionalityRepository,
                                       ContractRepository contractRepository,
                                       ContractProcessRepository contractProcessRepository,
                                       PasswordEncoder passwordEncoder,
                                       AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.functionalityRepository = functionalityRepository;
        this.contractRepository = contractRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    private String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() &&
                !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                return ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            } else if (principal instanceof String) {
                return (String) principal;
            }
            return authentication.getName(); // Fallback
        }
        return "SYSTEM_UNKNOWN";
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Contract> getContractsPendingAssignment(Pageable pageable, String contractNameSearch, String contractNumberSearch) {
        Specification<Contract> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            // 筛选状态为 PENDING_ASSIGNMENT 的合同
            predicates.add(cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT));

            // 根据需求文档，待分配的合同是那些刚起草完成，等待管理员分配处理人员的合同 (p6, 3.7.1)
            // 此时通常还没有任何流程记录。如果 PENDING_ASSIGNMENT 状态严格意味着没有流程，
            // 则此状态过滤已足够。

            if (StringUtils.hasText(contractNameSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractName")), "%" + contractNameSearch.toLowerCase().trim() + "%"));
            }
            if (StringUtils.hasText(contractNumberSearch)) {
                predicates.add(cb.like(cb.lower(root.get("contractNumber")), "%" + contractNumberSearch.toLowerCase().trim() + "%"));
            }

            // 预加载关联数据以便在列表中显示
            if (query.getResultType().equals(Contract.class)) {
                root.fetch("customer", JoinType.LEFT);
                root.fetch("drafter", JoinType.LEFT);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        Page<Contract> contractsPage = contractRepository.findAll(spec, pageable);
        // 初始化懒加载的集合
        contractsPage.getContent().forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            Hibernate.initialize(contract.getDrafter());
            if (contract.getDrafter() != null) {
                Hibernate.initialize(contract.getDrafter().getRoles());
            }
        });
        return contractsPage;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        // 此方法如果仍在前端使用，也应更新为查询 PENDING_ASSIGNMENT 状态
        Specification<Contract> spec = (root, query, cb) ->
                cb.and(
                        cb.equal(root.get("status"), ContractStatus.PENDING_ASSIGNMENT)
                        // 可以选择性地添加 cb.isEmpty(root.get("contractProcesses")) 如果需要
                );
        List<Contract> contracts = contractRepository.findAll(spec);
        contracts.forEach(contract -> {
            Hibernate.initialize(contract.getCustomer());
            Hibernate.initialize(contract.getDrafter());
            // 按需初始化其他懒加载集合
        });
        return contracts;
    }


    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // 验证合同是否处于“待分配”状态
        if (contract.getStatus() != ContractStatus.PENDING_ASSIGNMENT) {
            throw new BusinessLogicException("合同 " + contract.getContractNumber() + " 当前状态为 " +
                    contract.getStatus().getDescription() + "，不能进行人员分配。必须处于“待分配”状态。");
        }

        // 根据需求文档3.7.1 (p23, "数据验证")：“会签、审批、签订人员需全部指定。”
        if (countersignUserIds == null || countersignUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名会签人员。");
        }
        if (approvalUserIds == null || approvalUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名审批人员。");
        }
        if (signUserIds == null || signUserIds.isEmpty()) {
            throw new BusinessLogicException("必须至少指定一名签订人员。");
        }

        // 为所有指定人员创建 ContractProcess 记录，初始状态均为 PENDING
        for (Long userId : countersignUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("分配会签：用户未找到，ID: " + userId));
            ContractProcess cp = new ContractProcess();
            cp.setContract(contract);
            cp.setContractNumber(contract.getContractNumber()); // 可选冗余
            cp.setOperator(user);
            cp.setOperatorUsername(user.getUsername()); // 可选冗余
            cp.setType(ContractProcessType.COUNTERSIGN);
            cp.setState(ContractProcessState.PENDING);
            contractProcessRepository.save(cp);
        }

        for (Long userId : approvalUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("分配审批：用户未找到，ID: " + userId));
            ContractProcess cp = new ContractProcess();
            cp.setContract(contract);
            cp.setContractNumber(contract.getContractNumber());
            cp.setOperator(user);
            cp.setOperatorUsername(user.getUsername());
            cp.setType(ContractProcessType.APPROVAL);
            cp.setState(ContractProcessState.PENDING);
            contractProcessRepository.save(cp);
        }

        for (Long userId : signUserIds) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("分配签订：用户未找到，ID: " + userId));
            ContractProcess cp = new ContractProcess();
            cp.setContract(contract);
            cp.setContractNumber(contract.getContractNumber());
            cp.setOperator(user);
            cp.setOperatorUsername(user.getUsername());
            cp.setType(ContractProcessType.SIGNING);
            cp.setState(ContractProcessState.PENDING);
            contractProcessRepository.save(cp);
        }

        // 人员分配完成后，合同进入“待会签”状态
        contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
        contract.setUpdatedAt(LocalDateTime.now()); // 更新合同的最后修改时间
        contractRepository.save(contract);

        auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL_STRICT",
                "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配了会签、审批及签订人员，合同进入待会签状态。");
        return true;
    }

    // --- 用户管理 ---
    @Override
    @Transactional
    public User createUser(User user, Set<String> roleNames) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + user.getUsername() + "' 已存在");
        }
        if (user.getEmail() != null && !user.getEmail().trim().isEmpty() && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + user.getEmail() + "' 已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        // user.setEnabled(true); // 实体中默认为 true

        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                roles.add(role);
            }
        }
        user.setRoles(roles);
        User savedUser = userRepository.save(user);

        if (savedUser.getRoles() != null) {
            savedUser.getRoles().forEach(role -> {
                Hibernate.initialize(role.getFunctionalities());
            });
        }
        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
        if (user != null && user.getRoles() != null) {
            user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        return user;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        List<User> users = userRepository.findAll();
        users.forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return users;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> getAllUsers(Pageable pageable) {
        Page<User> usersPage = userRepository.findAll(pageable);
        usersPage.getContent().forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return usersPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> searchUsers(String username, String email, Pageable pageable) {
        Specification<User> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (username != null && !username.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("username")), "%" + username.toLowerCase() + "%"));
            }
            if (email != null && !email.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), "%" + email.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<User> usersPage = userRepository.findAll(spec, pageable);
        usersPage.getContent().forEach(user -> {
            if (user.getRoles() != null) {
                user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
            }
        });
        return usersPage;
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userDetailsToUpdate) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 更新邮箱，并检查唯一性
        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().isEmpty() &&
                !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            userRepository.findByEmail(userDetailsToUpdate.getEmail())
                    .filter(userWithEmail -> !userWithEmail.getId().equals(userId)) // 排除当前用户自身
                    .ifPresent(u -> {
                        throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
                    });
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }

        // 更新启用状态
        // DTO 中的 isEnabled() 对应实体中的 isEnabled()
        if (userDetailsToUpdate.isEnabled() != existingUser.isEnabled()) {
            existingUser.setEnabled(userDetailsToUpdate.isEnabled());
        }
        // 更新真实姓名
        if (userDetailsToUpdate.getRealName() != null) {
            existingUser.setRealName(userDetailsToUpdate.getRealName());
        }
        // 注意：密码不在此处更新，应有单独的修改密码接口
        // 角色分配也不在此处更新

        User updatedUser = userRepository.save(existingUser);
        if (updatedUser.getRoles() != null) {
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "UPDATE_USER_INFO", "更新用户信息: " + updatedUser.getUsername());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 检查用户是否参与未完成的合同流程
        long pendingProcessesCount = contractProcessRepository.countByOperatorAndState(user, ContractProcessState.PENDING);
        if (pendingProcessesCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户参与了 " + pendingProcessesCount + " 个未完成的合同流程。");
        }
        // 检查用户是否为任何合同的起草人
        long draftedContractsCount = contractRepository.countByDrafter(user);
        if (draftedContractsCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户是 " + draftedContractsCount + " 个合同的起草人。请先处理这些合同。");
        }
        userRepository.delete(user);
        auditLogService.logAction(getCurrentUsername(), "DELETE_USER", "删除用户: " + user.getUsername() + " (ID: " + userId + ")");
    }

    // --- 角色管理 ---
    @Override
    @Transactional
    public Role createRoleWithFunctionalityNums(Role role, Set<String> functionalityNums) {
        if (roleRepository.findByName(role.getName()).isPresent()) {
            throw new DuplicateResourceException("角色名称 '" + role.getName() + "' 已存在");
        }

        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNums != null && !functionalityNums.isEmpty()) {
            for (String funcNum : functionalityNums) {
                Functionality func = functionalityRepository.findByNum(funcNum)
                        .orElseThrow(() -> new ResourceNotFoundException("功能编号为 '" + funcNum + "' 的功能未找到，无法添加到角色"));
                functionalities.add(func);
            }
        }
        role.setFunctionalities(functionalities);
        Role savedRole = roleRepository.save(role);
        if (savedRole.getFunctionalities() != null) {
            Hibernate.initialize(savedRole.getFunctionalities());
        }
        auditLogService.logAction(getCurrentUsername(), "CREATE_ROLE", "创建角色: " + savedRole.getName() + " 并分配功能编号: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return savedRole;
    }

    @Override
    @Transactional
    public Role updateRoleWithFunctionalityNums(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNums) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        if (roleDetailsToUpdate.getName() != null && !existingRole.getName().equals(roleDetailsToUpdate.getName())) {
            roleRepository.findByName(roleDetailsToUpdate.getName())
                    .filter(r -> !r.getId().equals(roleId)) // 确保不是与自身比较
                    .ifPresent(r -> {
                        throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
                    });
            existingRole.setName(roleDetailsToUpdate.getName());
        }
        if (roleDetailsToUpdate.getDescription() != null) {
            existingRole.setDescription(roleDetailsToUpdate.getDescription());
        }

        Set<Functionality> newFunctionalities = new HashSet<>();
        if (functionalityNums != null && !functionalityNums.isEmpty()) {
            for (String funcNum : functionalityNums) {
                Functionality func = functionalityRepository.findByNum(funcNum)
                        .orElseThrow(() -> new ResourceNotFoundException("功能编号为 '" + funcNum + "' 的功能未找到，无法更新角色权限"));
                newFunctionalities.add(func);
            }
        }
        existingRole.setFunctionalities(newFunctionalities); // 完全替换旧的功能集合

        Role updatedRole = roleRepository.save(existingRole);
        if (updatedRole.getFunctionalities() != null) {
            Hibernate.initialize(updatedRole.getFunctionalities());
        }
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName() + " 并更新功能编号为: " + (functionalityNums != null ? String.join(", ", functionalityNums) : "无"));
        return updatedRole;
    }


    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        List<Role> roles = roleRepository.findAll();
        roles.forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
        return roles;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Role> getAllRoles(Pageable pageable) {
        Page<Role> rolesPage = roleRepository.findAll(pageable);
        rolesPage.getContent().forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
        return rolesPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Role> searchRoles(String name, String description, Pageable pageable) {
        Specification<Role> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (name != null && !name.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }
            query.distinct(true); // Ensure distinct roles if functionalities join causes duplicates
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        Page<Role> rolesPage = roleRepository.findAll(spec, pageable);
        rolesPage.getContent().forEach(role -> {
            if (role.getFunctionalities() != null) Hibernate.initialize(role.getFunctionalities());
        });
        return rolesPage;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
        if (role != null && role.getFunctionalities() != null) {
            Hibernate.initialize(role.getFunctionalities());
        }
        return role;
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleById(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));
        if (role != null && role.getFunctionalities() != null) {
            Hibernate.initialize(role.getFunctionalities());
        }
        return role;
    }


    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        // 检查是否有用户仍拥有此角色
        long userCountWithRole = userRepository.countByRolesContains(role);
        if (userCountWithRole > 0) {
            throw new BusinessLogicException("无法删除角色 '" + role.getName() + "'，仍有 " + userCountWithRole + " 个用户拥有此角色。请先解除这些用户的该角色。");
        }
        roleRepository.delete(role);
        auditLogService.logAction(getCurrentUsername(), "DELETE_ROLE", "删除角色: " + role.getName() + " (ID: " + roleId + ")");
    }

    // --- 功能操作管理 ---
    @Override
    @Transactional
    public Functionality createFunctionality(Functionality functionality) {
        if (functionality.getName() == null || functionality.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("功能名称不能为空");
        }
        if (functionality.getNum() == null || functionality.getNum().trim().isEmpty()) {
            throw new IllegalArgumentException("功能编号不能为空");
        }
        functionalityRepository.findByName(functionality.getName()).ifPresent(f -> {
            throw new DuplicateResourceException("功能名称 '" + functionality.getName() + "' 已存在");
        });
        functionalityRepository.findByNum(functionality.getNum()).ifPresent(f -> {
            throw new DuplicateResourceException("功能编号 '" + functionality.getNum() + "' 已存在");
        });
        Functionality savedFunctionality = functionalityRepository.save(functionality);
        auditLogService.logAction(getCurrentUsername(), "CREATE_FUNCTIONALITY", "创建功能: " + savedFunctionality.getName() + " (编号: " + savedFunctionality.getNum() + ")");
        return savedFunctionality;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Functionality> getAllFunctionalities() {
        return functionalityRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Functionality> getAllFunctionalities(Pageable pageable) {
        return functionalityRepository.findAll(pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Functionality> searchFunctionalities(String num, String name, String description, Pageable pageable) {
        Specification<Functionality> spec = (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (num != null && !num.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("num")), "%" + num.toLowerCase() + "%"));
            }
            if (name != null && !name.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), "%" + name.toLowerCase() + "%"));
            }
            if (description != null && !description.isEmpty()) {
                predicates.add(criteriaBuilder.like(criteriaBuilder.lower(root.get("description")), "%" + description.toLowerCase() + "%"));
            }
            query.distinct(true);
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
        return functionalityRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public Functionality getFunctionalityById(Long id) {
        return functionalityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("功能未找到，ID: " + id));
    }

    @Override
    @Transactional
    public Functionality updateFunctionality(Long id, Functionality functionalityDetailsToUpdate) {
        Functionality existingFunc = getFunctionalityById(id); // 会在找不到时抛出异常

        // 检查功能编号是否更改以及是否冲突
        if (functionalityDetailsToUpdate.getNum() != null &&
                !functionalityDetailsToUpdate.getNum().equals(existingFunc.getNum())) {
            functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum())
                    .filter(f -> !f.getId().equals(id)) // 排除自身
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
                    });
            existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        }

        // 检查功能名称是否更改以及是否冲突
        if (functionalityDetailsToUpdate.getName() != null &&
                !functionalityDetailsToUpdate.getName().equals(existingFunc.getName())) {
            functionalityRepository.findByName(functionalityDetailsToUpdate.getName())
                    .filter(f -> !f.getId().equals(id)) // 排除自身
                    .ifPresent(f -> {
                        throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
                    });
            existingFunc.setName(functionalityDetailsToUpdate.getName());
        }

        // 更新其他字段
        existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());

        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName() + " (编号: " + updatedFunctionality.getNum() + ")");
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id); // 会在找不到时抛出异常

        // 在删除功能前，需要先从所有引用了此功能的角色中移除它
        List<Role> rolesWithFunc = roleRepository.findAllByFunctionalitiesContains(func);
        if (!rolesWithFunc.isEmpty()) {
            for(Role role : rolesWithFunc){
                role.getFunctionalities().remove(func); // 从角色的功能集合中移除
            }
            roleRepository.saveAll(rolesWithFunc); // 保存这些角色的更改
        }

        functionalityRepository.delete(func);
        auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY", "删除功能: " + func.getName() + " (ID: " + id + ")");
    }

    // --- 分配权限 (给用户分配角色) ---
    @Override
    @Transactional
    public User assignRolesToUser(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        Set<Role> newRoles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                newRoles.add(role);
            }
        }
        user.setRoles(newRoles); // 直接设置新的角色集合，会覆盖旧的
        User updatedUser = userRepository.save(user);

        // 初始化懒加载的集合
        if (updatedUser != null && updatedUser.getRoles() != null) {
            updatedUser.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));
        }
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + (roleNames != null ? String.join(", ", roleNames) : "无"));
        return updatedUser;
    }
}