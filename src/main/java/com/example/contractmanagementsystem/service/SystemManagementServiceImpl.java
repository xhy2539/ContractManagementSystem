package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.*;
import com.example.contractmanagementsystem.exception.BusinessLogicException; // 导入
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
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
            if (authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
                return ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            } else if (authentication.getPrincipal() instanceof String) {
                return (String) authentication.getPrincipal();
            }
            return authentication.getName();
        }
        return "SYSTEM";
    }

    // --- 分配合同 (3.7.1) ---
    @Override
    @Transactional(readOnly = true)
    public List<Contract> getContractsPendingAssignment() {
        return contractRepository.findAll().stream()
                .filter(contract -> (ContractStatus.DRAFT.equals(contract.getStatus()) ||
                        ContractStatus.PENDING_ASSIGNMENT.equals(contract.getStatus())) &&
                        contractProcessRepository.findByContract(contract).isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public boolean assignContractPersonnel(Long contractId, List<Long> countersignUserIds, List<Long> approvalUserIds, List<Long> signUserIds) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new ResourceNotFoundException("合同未找到，ID: " + contractId));

        boolean personnelAssigned = false;

        if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
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
            personnelAssigned = true;
        }

        if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
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
            personnelAssigned = true;
        }

        if (signUserIds != null && !signUserIds.isEmpty()) {
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
            personnelAssigned = true;
        }

        if (personnelAssigned) {
            if (countersignUserIds != null && !countersignUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_COUNTERSIGN);
            } else if (approvalUserIds != null && !approvalUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_APPROVAL);
            } else if (signUserIds != null && !signUserIds.isEmpty()) {
                contract.setStatus(ContractStatus.PENDING_SIGNING);
            } else {
                contract.setStatus(ContractStatus.PENDING_ASSIGNMENT);
            }
            contractRepository.save(contract);
            auditLogService.logAction(getCurrentUsername(), "ASSIGN_CONTRACT_PERSONNEL", "为合同 ID: " + contract.getId() + " (" + contract.getContractName() + ") 分配处理人员");
            return true;
        } else {
            return false;
        }
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
        user.setEnabled(true);

        Set<Role> roles = new HashSet<>();
        if (roleNames != null && !roleNames.isEmpty()) {
            for (String roleName : roleNames) {
                Role role = roleRepository.findByName(roleName)
                        .orElseThrow(() -> new ResourceNotFoundException("角色 '" + roleName + "' 未找到"));
                roles.add(role);
            }
        }
        user.setRoles(roles);
        User savedUser = userRepository.save(user);
        auditLogService.logAction(getCurrentUsername(), "CREATE_USER", "创建用户: " + savedUser.getUsername());
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

        if (userDetailsToUpdate.getEmail() != null && !userDetailsToUpdate.getEmail().isEmpty() &&
                !userDetailsToUpdate.getEmail().equals(existingUser.getEmail())) {
            if (userRepository.existsByEmail(userDetailsToUpdate.getEmail())) {
                throw new DuplicateResourceException("邮箱 '" + userDetailsToUpdate.getEmail() + "' 已被其他用户使用");
            }
            existingUser.setEmail(userDetailsToUpdate.getEmail());
        }
        existingUser.setEnabled(userDetailsToUpdate.isEnabled());

        User updatedUser = userRepository.save(existingUser);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_USER_INFO", "更新用户信息: " + updatedUser.getUsername());
        return updatedUser;
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户未找到，ID: " + userId));

        // 检查用户是否是任何未完成合同流程的操作员
        long pendingProcessesCount = contractProcessRepository.countByOperatorAndState(user, ContractProcessState.PENDING);
        if (pendingProcessesCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户参与了 " + pendingProcessesCount + " 个未完成的合同流程。");
        }

        // 检查用户是否是任何合同的起草人
        long draftedContractsCount = contractRepository.countByDrafter(user);
        if (draftedContractsCount > 0) {
            throw new BusinessLogicException("无法删除用户 " + user.getUsername() + "，该用户是 " + draftedContractsCount + " 个合同的起草人。请先处理这些合同。");
        }

        user.getRoles().clear(); // JPA会处理关联表
        userRepository.delete(user);
        auditLogService.logAction(getCurrentUsername(), "DELETE_USER", "删除用户: " + user.getUsername() + " (ID: " + userId + ")");
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
        auditLogService.logAction(getCurrentUsername(), "CREATE_ROLE", "创建角色: " + savedRole.getName());
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

        if (roleDetailsToUpdate.getName() != null && !existingRole.getName().equals(roleDetailsToUpdate.getName())) {
            if(roleRepository.findByName(roleDetailsToUpdate.getName()).isPresent()){
                throw new DuplicateResourceException("角色名称 '" + roleDetailsToUpdate.getName() + "' 已被其他角色使用");
            }
            existingRole.setName(roleDetailsToUpdate.getName());
        }
        if(roleDetailsToUpdate.getDescription() != null) {
            existingRole.setDescription(roleDetailsToUpdate.getDescription());
        }

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
        auditLogService.logAction(getCurrentUsername(), "UPDATE_ROLE", "更新角色: " + updatedRole.getName());
        return updatedRole;
    }

    @Override
    @Transactional
    public void deleteRole(Integer roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("角色未找到，ID: " + roleId));

        // 使用 UserRepository 中添加的 countByRolesContains 方法检查角色是否仍被用户使用
        long userCountWithRole = userRepository.countByRolesContains(role);
        if (userCountWithRole > 0) {
            throw new BusinessLogicException("无法删除角色 '" + role.getName() + "'，仍有 " + userCountWithRole + " 个用户拥有此角色。请先解除这些用户的该角色。");
        }

        // 如果没有用户拥有此角色，可以直接删除。
        // JPA 会自动处理关联表 role_functionalities 中的记录，因为 Role 是拥有方。
        // role.getFunctionalities().clear(); // 不需要显式清除，除非有特殊理由或级联问题

        roleRepository.delete(role);
        auditLogService.logAction(getCurrentUsername(), "DELETE_ROLE", "删除角色: " + role.getName() + " (ID: " + roleId + ")");
    }


    // --- 功能操作管理 (3.7.2.3) ---
    @Override
    @Transactional
    public Functionality createFunctionality(Functionality functionality) {
        if (functionality.getName() == null || functionality.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("功能名称不能为空");
        }
        if (functionalityRepository.findByName(functionality.getName()).isPresent()){
            throw new DuplicateResourceException("功能名称 '" + functionality.getName() + "' 已存在");
        }
        if (functionality.getNum() != null && !functionality.getNum().trim().isEmpty() &&
                functionalityRepository.findByNum(functionality.getNum()).isPresent()) {
            throw new DuplicateResourceException("功能编号 '" + functionality.getNum() + "' 已存在");
        }
        Functionality savedFunctionality = functionalityRepository.save(functionality);
        auditLogService.logAction(getCurrentUsername(), "CREATE_FUNCTIONALITY", "创建功能: " + savedFunctionality.getName());
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

        if (functionalityDetailsToUpdate.getName() != null &&
                !existingFunc.getName().equals(functionalityDetailsToUpdate.getName())) {
            Optional<Functionality> byName = functionalityRepository.findByName(functionalityDetailsToUpdate.getName());
            if (byName.isPresent() && !byName.get().getId().equals(id)) {
                throw new DuplicateResourceException("功能名称 '" + functionalityDetailsToUpdate.getName() + "' 已被其他功能使用");
            }
            existingFunc.setName(functionalityDetailsToUpdate.getName());
        }

        if (functionalityDetailsToUpdate.getNum() != null &&
                ! (existingFunc.getNum() != null && existingFunc.getNum().equals(functionalityDetailsToUpdate.getNum())) ) {
            Optional<Functionality> byNum = functionalityRepository.findByNum(functionalityDetailsToUpdate.getNum());
            if (byNum.isPresent() && !byNum.get().getId().equals(id)) {
                throw new DuplicateResourceException("功能编号 '" + functionalityDetailsToUpdate.getNum() + "' 已被其他功能使用");
            }
            existingFunc.setNum(functionalityDetailsToUpdate.getNum());
        }

        if(functionalityDetailsToUpdate.getDescription() != null) {
            existingFunc.setDescription(functionalityDetailsToUpdate.getDescription());
        }
        if(functionalityDetailsToUpdate.getUrl() != null) {
            existingFunc.setUrl(functionalityDetailsToUpdate.getUrl());
        }

        Functionality updatedFunctionality = functionalityRepository.save(existingFunc);
        auditLogService.logAction(getCurrentUsername(), "UPDATE_FUNCTIONALITY", "更新功能: " + updatedFunctionality.getName());
        return updatedFunctionality;
    }

    @Override
    @Transactional
    public void deleteFunctionality(Long id) {
        Functionality func = getFunctionalityById(id);

        // 检查功能是否仍被任何角色使用
        List<Role> rolesWithFunc = roleRepository.findAllByFunctionalitiesContains(func);
        if (!rolesWithFunc.isEmpty()) {
            // 业务决定：是抛出异常，还是自动解除关联
            // 这里选择自动解除关联并记录
            for(Role role : rolesWithFunc){
                role.getFunctionalities().remove(func); // 依赖 Functionality 的 equals/hashCode
                roleRepository.save(role);
            }
            auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY_CASCADE_ROLE_UPDATE",
                    "功能 '" + func.getName() + "' (ID: " + id + ") 已从 " + rolesWithFunc.size() + " 个角色中移除。");
        }

        functionalityRepository.delete(func);
        auditLogService.logAction(getCurrentUsername(), "DELETE_FUNCTIONALITY", "删除功能: " + func.getName() + " (ID: " + id + ")");
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
        auditLogService.logAction(getCurrentUsername(), "ASSIGN_ROLES_TO_USER", "为用户 '" + updatedUser.getUsername() + "' 分配角色: " + roleNames);
        return updatedUser;
    }
}