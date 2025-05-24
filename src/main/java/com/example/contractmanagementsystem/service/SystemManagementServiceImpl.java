package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
// 假设你创建了 BusinessLogicException 用于处理删除约束等情况
// import com.example.contractmanagementsystem.exception.BusinessLogicException;
import com.example.contractmanagementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
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
    // private final AuditLogService auditLogService; // 稍后注入

    @Autowired
    public SystemManagementServiceImpl(UserRepository userRepository,
                                       RoleRepository roleRepository,
                                       FunctionalityRepository functionalityRepository,
                                       ContractRepository contractRepository,
                                       ContractProcessRepository contractProcessRepository,
                                       PasswordEncoder passwordEncoder
            /* AuditLogService auditLogService */) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.functionalityRepository = functionalityRepository;
        this.contractRepository = contractRepository;
        this.contractProcessRepository = contractProcessRepository;
        this.passwordEncoder = passwordEncoder;
        // this.auditLogService = auditLogService;
    }

    private String getCurrentUsername() {
        // 辅助方法获取当前认证用户名，用于日志记录
        // 在实际配置好Spring Security后才能正确工作
        if (SecurityContextHolder.getContext().getAuthentication() != null &&
                SecurityContextHolder.getContext().getAuthentication().isAuthenticated() &&
                !"anonymousUser".equals(SecurityContextHolder.getContext().getAuthentication().getPrincipal())) {
            return SecurityContextHolder.getContext().getAuthentication().getName();
        }
        return "SYSTEM"; // 或其他默认值，如果无法获取认证用户
    }

    // --- 分配合同 (3.7.1) ---
    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        List<Contract> contracts = contractRepository.findAll().stream()
                .filter(contract -> ContractStatus.DRAFT.equals(contract.getStatus()) ||
                        ContractStatus.PENDING_ASSIGNMENT.equals(contract.getStatus()))
                .collect(Collectors.toList());

        return contracts.stream()
                .filter(contract -> contractProcessRepository.findByContract(contract).isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        // 考虑清除旧的分配记录或进行更复杂的更新逻辑 (如果允许重新分配)
        // List<ContractProcess> existingProcesses = contractProcessRepository.findByContract(contract);
        // if (!existingProcesses.isEmpty()) {
        //     contractProcessRepository.deleteAll(existingProcesses);
        // }

        if (countersignUserIds != null) {
            for (Long userId : countersignUserIds) {
                User user = userRepository.findById(userId)
                        .orElseThrow(() -> new ResourceNotFoundException("分配会签：用户未找到，ID: " + userId));
                ContractProcess cp = new ContractProcess();
                cp.setContract(contract);
                cp.setContractNumber(contract.getContractNumber());
                cp.setOperator(user);
                cp.setOperatorUsername(user.getUsername());
                cp.setType(ContractProcessType.COUNTERSIGN);
                cp.setState(ContractProcessState.PENDING);
                contractProcessRepository.save(cp);
            }
        }

        if (approvalUserIds != null) {
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
        }

        if (signUserIds != null) {
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
        }

        // 更新合同状态 (可选，根据业务流程)
        // if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
        //    contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
        // } else if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
        //    contract.setStatus(ContractStatus.PENDING_APPROVAL);
        // } else if (signUserIds != null && !signUserIds.isEmpty()) {
        //    contract.setStatus(ContractStatus.PENDING_SIGNING);
        // }
        // contractRepository.save(contract);

        // auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT", "为合同 '" + contract.getContractName() + "' 分配处理人员");
        return true;
    }


    // --- 用户管理 (3.7.2.1) ---
    @Override
    @Transactional
    public User createUser(User user, Set<String> roleNames) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new DuplicateResourceException("用户名 '" + user.getUsername() + "' 已存在");
        }
        if (user.getEmail() != null && !user.getEmail().isEmpty() && userRepository.existsByEmail(user.getEmail())) {
            throw new DuplicateResourceException("邮箱 '" + user.getEmail() + "' 已存在");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setEnabled(true); // 默认启用

        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
                roles.add(role);
            }
        }
        // 考虑：如果 roleNames 为空，是否分配一个默认角色
        user.setRoles(roles);
        User savedUser = userRepository.save(user);
        // auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
        return savedUser;
    }

    @Override
    @Transactional(readOnly = true)
    public User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户 '" + username + "' 未找到"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional
    public User updateUser(Long userId, User userDetailsToUpdate) {
        User existingUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userDetailsToUpdate.getEmail())) {
                throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
            }
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }
        existingUser.setEnabled(userDetailsToUpdate.isEnabled());
        // 密码和角色通常通过专门的方法更新
        // existingUser.setUpdatedAt(LocalDateTime.now()); // @UpdateTimestamp 会自动处理

        User updatedUser = userRepository.save(existingUser);
        // auditLogService.logAction(getCurrentUsername(), "UPDATE_USER", "更新用户: " + updatedUser.getUsername());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 示例：检查关联 (更完善的检查可能需要自定义Repository方法)
        // List<ContractProcess> activeProcesses = contractProcessRepository.findByOperatorAndState(user, ContractProcessState.PENDING);
        // if (!activeProcesses.isEmpty()) {
        //     throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户参与了未完成的合同流程。");
        // }
        // List<Contract> draftedContracts = contractRepository.findByDrafter(user);
        // if (!draftedContracts.isEmpty()){
        //     throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户是合同的起草人。先修改合同起草人。");
        // }


        // 清除用户与角色的关联
        user.getRoles().clear();
        // userRepository.save(user); // 如果hibernate.properties.hibernate.event.spi.AutoDirtyCollectionSynchronization设置为false，则需要手动保存

        userRepository.delete(user);
        // auditLogService.logAction(getCurrentUsername(), "DELETE_USER", "删除用户: " + user.getUsername());
    }


    // --- 角色管理 (3.7.2.2) ---
    @Override
    @Transactional
    public Role createRole(Role role, Set<String> functionalityNames) {
        if (roleRepository.findByName(role.getName()).isPresent()) {
            throw new DuplicateResourceException("角色名称 '" + role.getName() + "' 已存在");
        }

        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNames != null) {
            for (String funcName : functionalityNames) {
                Functionality func = functionalityRepository.findByName(funcName)
                        .orElseThrow(() -> new ResourceNotFoundException("功能 '" + funcName + "' 未找到"));
                functionalities.add(func);
            }
        }
        role.setFunctionalities(functionalities);
        Role savedRole = roleRepository.save(role);
        // auditLogService.logAction(getCurrentUsername(), "CREATE_ROLE", "创建角色: " + savedRole.getName());
        return savedRole;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public Role findRoleByName(String roleName) {
        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
    }

    @Override
    @Transactional
    public Role updateRole(Integer roleId, Role roleDetailsToUpdate, Set<String> functionalityNames) {
        Role existingRole = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        if (!existingRole.getName().equals(roleDetailsToUpdate.getName()) &&
                roleRepository.findByName(roleDetailsToUpdate.getName()).isPresent()) {
            throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
        }

        existingRole.setName(roleDetailsToUpdate.getName());
        existingRole.setDescription(roleDetailsToUpdate.getDescription());

        Set<Functionality> functionalities = new HashSet<>();
        if (functionalityNames != null) {
            for (String funcName : functionalityNames) {
                Functionality func = functionalityRepository.findByName(funcName)
                        .orElseThrow(() -> new ResourceNotFoundException("功能 '" + funcName + "' 未找到"));
                functionalities.add(func);
            }
        }
        existingRole.setFunctionalities(functionalities);
        Role updatedRole = roleRepository.save(existingRole);
        // auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName());
        return updatedRole;
    }

    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        // 示例：检查角色是否仍被用户使用
        // long userCountWithRole = userRepository.countByRolesContains(role); // 需要在UserRepository中定义此方法
        // if (userCountWithRole > 0) {
        //     throw new BusinessLogicException("无法删除角色 '" + role.getName() + "'，仍有用户拥有此角色。");
        // }

        // 从拥有此角色的所有用户中移除此角色
        List<User> usersWithRole = userRepository.findAll(); // 效率不高，最好有针对性的查询
        for(User user : usersWithRole){
            if(user.getRoles().contains(role)){
                user.getRoles().remove(role);
                userRepository.save(user);
            }
        }

        role.getFunctionalities().clear();
        // roleRepository.save(role); // 如果需要保存解除功能关联的状态

        roleRepository.delete(role);
        // auditLogService.logAction(getCurrentUsername(), "DELETE_ROLE", "删除角色: " + role.getName());
    }


    // --- 功能操作管理 (3.7.2.3) ---
    @Override
    @Transactional
    public Functionality createFunctionality(Functionality functionality) {
        if (functionalityRepository.findByName(functionality.getName()).isPresent()){
            throw new DuplicateResourceException("功能名称 '" + functionality.getName() + "' 已存在");
        }
        if (functionality.getNum() != null && functionalityRepository.findByNum(functionality.getNum()).isPresent()) {
            throw new DuplicateResourceException("功能编号 '" + functionality.getNum() + "' 已存在");
        }
        Functionality savedFunctionality = functionalityRepository.save(functionality);
        // auditLogService.logAction(getCurrentUsername(), "CREATE_FUNCTIONALITY", "创建功能: " + savedFunctionality.getName());
        return savedFunctionality;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Functionality> getAllFunctionalities() {
        return functionalityRepository.findAll();
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
        Functionality existingFunc = getFunctionalityById(id);

        Optional<Functionality> byName = functionalityRepository.findByName(functionalityDetailsToUpdate.getName());
        if (byName.isPresent() && !byName.get().getId().equals(id)) {
            throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
        }
        Optional<Functionality> byNum = functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum());
        if (byNum.isPresent() && !byNum.get().getId().equals(id)) {
            throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
        }

        existingFunc.setName(functionalityDetailsToUpdate.getName());
        existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());
        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        // auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName());
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id);
        // 在删除功能之前，需要从所有拥有此功能的角色中移除它
        List<Role> rolesWithFunc = roleRepository.findAll(); // 效率不高，最好有针对性的查询
        for(Role role : rolesWithFunc){
            if(role.getFunctionalities().contains(func)){
                role.getFunctionalities().remove(func);
                roleRepository.save(role);
            }
        }
        functionalityRepository.delete(func);
        // auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY", "删除功能: " + func.getName());
    }


    // --- 分配权限 (给用户分配角色) (3.7.2.4) ---
    @Override
    @Transactional
    public User assignRolesToUser(Long userId, Set<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        Set<Role> newRoles = new HashSet<>();
        if (roleNames != null) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到，无法分配给用户"));
                newRoles.add(role);
            }
        }
        user.setRoles(newRoles);
        User updatedUser = userRepository.save(user);
        // auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色");
        return updatedUser;
    }
}