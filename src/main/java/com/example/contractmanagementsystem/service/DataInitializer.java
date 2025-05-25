//package com.example.contractmanagementsystem.service;
//
//import com.example.contractmanagementsystem.entity.Functionality;
//import com.example.contractmanagementsystem.entity.Role;
//import com.example.contractmanagementsystem.entity.User;
//import com.example.contractmanagementsystem.repository.FunctionalityRepository;
//import com.example.contractmanagementsystem.repository.RoleRepository;
//import com.example.contractmanagementsystem.repository.UserRepository;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.boot.CommandLineRunner;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.Arrays;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//@Component
//public class DataInitializer implements CommandLineRunner {
//
//    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);
//
//    private final UserRepository userRepository;
//    private final RoleRepository roleRepository;
//    private final FunctionalityRepository functionalityRepository;
//    private final PasswordEncoder passwordEncoder;
//
//    public static final String ADMIN_USERNAME = "admin";
//    public static final String ADMIN_DEFAULT_PASSWORD = "adminpassword";
//    public static final String ROLE_ADMIN_NAME = "ROLE_ADMIN";
//    public static final String ROLE_USER_NAME = "ROLE_USER"; // 合同操作员
//    // public static final String ROLE_NEW_USER_NAME = "ROLE_NEW_USER"; // 如果需要为新用户定义一个特定角色
//
//    @Autowired
//    public DataInitializer(UserRepository userRepository,
//                           RoleRepository roleRepository,
//                           FunctionalityRepository functionalityRepository,
//                           PasswordEncoder passwordEncoder) {
//        this.userRepository = userRepository;
//        this.roleRepository = roleRepository;
//        this.functionalityRepository = functionalityRepository;
//        this.passwordEncoder = passwordEncoder;
//    }
//
//    @Override
//    @Transactional
//    public void run(String... args) throws Exception {
//        logger.info("DataInitializer: 开始检查并初始化基础数据...");
//
//
//        List<String[]> allFunctionalitiesData = Arrays.asList(
//                new String[]{"USER_VIEW_LIST", "查看用户列表", "/admin/users", "允许查看系统用户列表"},
//                new String[]{"USER_CREATE", "新增用户", "/admin/users/create", "允许创建新用户账户"},
//                new String[]{"USER_EDIT", "修改用户信息", "/admin/users/edit", "允许编辑现有用户信息"},
//                new String[]{"USER_DELETE", "删除用户", "/admin/users/delete", "允许删除用户账户"},
//                new String[]{"USER_ASSIGN_ROLES", "为用户分配角色", "/admin/users/assign-roles", "允许向用户分配或更改角色"}, //需求文档中编号为S_AUR_ASGN，此处用您新表中的
//                new String[]{"ROLE_VIEW_LIST", "查看角色列表", "/admin/roles", "允许查看系统角色列表"},
//                new String[]{"ROLE_CREATE", "新增角色", "/admin/roles/create", "允许创建新角色并分配功能"},
//                new String[]{"ROLE_EDIT", "修改角色信息", "/admin/roles/edit", "允许编辑现有角色信息和功能分配"},
//                new String[]{"ROLE_DELETE", "删除角色", "/admin/roles/delete", "允许删除角色"},
//                new String[]{"FUNC_VIEW_LIST", "查看功能列表", "/admin/functionalities", "允许查看系统功能列表"},
//                new String[]{"FUNC_CREATE", "新增功能", "/admin/functionalities/create", "允许创建新功能"},
//                new String[]{"FUNC_EDIT", "修改功能信息", "/admin/functionalities/edit", "允许编辑现有功能信息"},
//                new String[]{"FUNC_DELETE", "删除功能", "/admin/functionalities/delete", "允许删除功能"},
//                new String[]{"LOG_VIEW_AUDIT", "查阅审计日志", "/admin/audit-logs", "允许查看系统审计日志"},
//                new String[]{"LOG_EXPORT_AUDIT", "导出审计日志", "/admin/audit-logs/export", "允许导出审计日志"},
//                new String[]{"CONTRACT_SIGN_SUBMIT", "提交签订信息", null, "允许用户录入合同签订信息"},
//                new String[]{"QUERY_CONTRACT_INFO", "合同信息查询", "/reports/contract-status", "允许用户查询合同基本信息"},
//                new String[]{"CUSTOMER_VIEW_LIST", "查看客户列表", "/customers", "允许查看客户列表"},
//                new String[]{"CUSTOMER_CREATE", "新增客户", "/customers/new", "允许新增客户信息"},
//                new String[]{"CUSTOMER_EDIT", "修改客户信息", "/customers/edit", "允许修改客户信息"},
//                new String[]{"CUSTOMER_DELETE", "删除客户", "/customers/delete", "允许删除客户信息"},
//                new String[]{"CUSTOMER_SEARCH", "查询客户", "/customers/search", "允许查询客户信息"},
//                new String[]{"USER_VIEW_PROFILE", "查看个人资料", null, "允许用户查看自己的个人资料"},
//                new String[]{"USER_CHANGE_PASSWORD", "修改个人密码", null, "允许用户修改自己的登录密码"},
//                new String[]{"CON_ASSIGN_VIEW", "查看待分配合同", "/admin/contract-assignments", "允许查看待分配的合同列表"}, // URL修正了一下
//                new String[]{"CON_ASSIGN_DO", "分配合同处理人员", "/admin/contract-assignments/assign", "允许为合同分配处理人员"},
//                new String[]{"CON_DRAFT_NEW", "起草合同", "/contracts/draft", "允许用户起草新的合同"}, // URL修正了一下
//                new String[]{"CON_VIEW_MY", "查看本人起草合同", "/contracts/view-all", "允许用户查看自己起草的合同"}, // 假设的URL
//                new String[]{"CON_CSIGN_VIEW", "查看待会签合同", "/contracts/pending-countersign", "允许用户查看等待自己会签的合同"},
//                new String[]{"CON_CSIGN_SUBMIT", "提交会签意见", null, "允许用户提交会签意见"},
//                new String[]{"CON_FINAL_VIEW", "查看待定稿合同", null, "允许用户查看等待自己定稿的合同"}, // 假设的URL，或与起草人相关
//                new String[]{"CON_FINAL_SUBMIT", "提交定稿合同", null, "允许用户修改并提交定稿合同"},
//                new String[]{"CON_APPROVE_VIEW", "查看待审批合同", "/contracts/pending-approval", "允许用户查看等待自己审批的合同"},
//                new String[]{"CON_APPROVE_SUBMIT", "提交审批意见", null, "允许用户提交审批意见"},
//                new String[]{"CON_SIGN_VIEW", "查看待签订合同", "/contracts/pending-signing", "允许用户查看等待自己签订的合同"},
//                new String[]{"QUERY_CON_PROC_ALL", "查看所有合同流程", null, "允许管理员查看所有合同的流程状态"}, // 假设的URL或通过特定查询实现
//                new String[]{"QUERY_CON_PROC_MY", "查看我的合同流程", null, "允许用户查看自己相关的合同流程状态"}, // 类似CON_VIEW_MY
//                new String[]{"CON_MANAGE_VIEW_ALL", "查看所有合同(管理)", "/admin/contracts/all", "允许管理员查看所有合同数据"}, // 假设的URL
//                new String[]{"USER_ASSIGN_ROLES", "为用户分配角色", "/admin/users/assign-roles", "允许向用户分配或更改角色"},
//                new String[]{"CON_MANAGE_DEL", "删除合同(管理)", "/admin/contracts/delete", "允许管理员删除合同（需谨慎）"} // 假设的URL
//        );
//
//        Set<Functionality> allAvailableFunctionalities = new HashSet<>();
//        for (String[] funcData : allFunctionalitiesData) {
//            // 查找时使用功能编号(num)
//            Functionality functionality = functionalityRepository.findByNum(funcData[0]).orElseGet(() -> {
//                logger.info("DataInitializer: 功能 {} (名称: {}, 编号: {}) 不存在，正在创建...", funcData[1], funcData[1], funcData[0]);
//                Functionality newFunc = new Functionality();
//                newFunc.setNum(funcData[0]);       // 功能编号
//                newFunc.setName(funcData[1]);      // 功能名称 (显示用)
//                if (funcData.length > 2 && funcData[2] != null && !funcData[2].trim().isEmpty()) newFunc.setUrl(funcData[2]);
//                newFunc.setDescription(funcData[3]); // 功能描述
//                return functionalityRepository.save(newFunc);
//            });
//            allAvailableFunctionalities.add(functionality);
//            logger.info("DataInitializer: 功能 {} (编号: {}) 已确保存在。", functionality.getName(), functionality.getNum());
//        }
//
//        // --- 角色权限分配 ---
//
//        // 1. 管理员角色 (ROLE_ADMIN)
//        Role adminRole = roleRepository.findByName(ROLE_ADMIN_NAME).orElseGet(() -> {
//            logger.info("DataInitializer: 角色 {} 不存在，正在创建...", ROLE_ADMIN_NAME);
//            Role newAdminRole = new Role();
//            newAdminRole.setName(ROLE_ADMIN_NAME);
//            newAdminRole.setDescription("系统管理员角色，拥有所有定义的权限");
//            return roleRepository.save(newAdminRole);
//        });
//        // 管理员拥有所有已定义的功能
//        updateRoleFunctionalities(adminRole, allAvailableFunctionalities, "所有已定义");
//
//
//        // 2. 合同操作员角色 (ROLE_USER)
//        Role userRole = roleRepository.findByName(ROLE_USER_NAME).orElseGet(() -> {
//            logger.info("DataInitializer: 角色 {} (合同操作员) 不存在，正在创建...", ROLE_USER_NAME);
//            Role newUserRole = new Role();
//            newUserRole.setName(ROLE_USER_NAME);
//            newUserRole.setDescription("合同操作员角色");
//            return roleRepository.save(newUserRole);
//        });
//
//        Set<String> operatorFuncNums = Set.of(
//                "CON_DRAFT_NEW", "CON_VIEW_MY", "CON_CSIGN_VIEW", "CON_CSIGN_SUBMIT",
//                "CON_FINAL_VIEW", "CON_FINAL_SUBMIT", "CON_APPROVE_VIEW", "CON_APPROVE_SUBMIT",
//                "CON_SIGN_VIEW", "CONTRACT_SIGN_SUBMIT", "QUERY_CONTRACT_INFO", "QUERY_CON_PROC_MY",
//                "USER_VIEW_PROFILE", "USER_CHANGE_PASSWORD" // 基本用户权限
//                // 根据需求文档，客户管理通常是管理员功能，所以不在这里添加
//        );
//        Set<Functionality> operatorFunctionalities = allAvailableFunctionalities.stream()
//                .filter(f -> operatorFuncNums.contains(f.getNum()))
//                .collect(Collectors.toSet());
//        updateRoleFunctionalities(userRole, operatorFunctionalities, "合同操作员标准");
//
//
//        // --- 创建/更新管理员用户 ---
//        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
//            logger.info("DataInitializer: 管理员用户 {} 不存在，正在创建...", ADMIN_USERNAME);
//            User adminUser = new User();
//            adminUser.setUsername(ADMIN_USERNAME);
//            adminUser.setPassword(passwordEncoder.encode(ADMIN_DEFAULT_PASSWORD));
//            adminUser.setEmail("admin@example.com");
//            adminUser.setRealName("系统管理员");
//            adminUser.setEnabled(true);
//            adminUser.setRoles(Set.of(adminRole));
//            userRepository.save(adminUser);
//            logger.info("DataInitializer: 管理员用户 {} 创建成功。", ADMIN_USERNAME);
//        } else {
//            User existingAdmin = userRepository.findByUsername(ADMIN_USERNAME).get();
//            if (existingAdmin.getRoles() == null || !existingAdmin.getRoles().contains(adminRole)) {
//                if(existingAdmin.getRoles() == null) existingAdmin.setRoles(new HashSet<>());
//                existingAdmin.getRoles().add(adminRole); // 确保管理员有 ROLE_ADMIN
//                userRepository.save(existingAdmin);
//                logger.info("DataInitializer: 已确保管理员用户 {} 拥有 {} 角色。", ADMIN_USERNAME, ROLE_ADMIN_NAME);
//            } else {
//                logger.info("DataInitializer: 管理员用户 {} 已存在并拥有 {} 角色。", ADMIN_USERNAME, ROLE_ADMIN_NAME);
//            }
//        }
//
//        // 创建一个示例合同操作员 (可选)
//        String operatorUsername = "operator";
//        if (!userRepository.existsByUsername(operatorUsername)) {
//            logger.info("DataInitializer: 合同操作员 {} 不存在，正在创建...", operatorUsername);
//            User operatorUser = new User();
//            operatorUser.setUsername(operatorUsername);
//            operatorUser.setPassword(passwordEncoder.encode("operatorpassword")); // 初始密码
//            operatorUser.setEmail("operator@example.com");
//            operatorUser.setRealName("合同操作员李四");
//            operatorUser.setEnabled(true);
//            operatorUser.setRoles(Set.of(userRole)); // 分配 ROLE_USER
//            userRepository.save(operatorUser);
//            logger.info("DataInitializer: 合同操作员 {} 创建成功。", operatorUsername);
//        }
//
//
//        logger.info("DataInitializer: 基础数据初始化完成。");
//    }
//
//    private void updateRoleFunctionalities(Role role, Set<Functionality> targetFunctionalities, String roleTypeForLog) {
//        boolean needsSave = false;
//        if (role.getFunctionalities() == null) {
//            role.setFunctionalities(new HashSet<>());
//        }
//
//        // 检查是否需要更新
//        Set<Long> currentFuncIds = role.getFunctionalities().stream().map(Functionality::getId).collect(Collectors.toSet());
//        Set<Long> targetFuncIds = targetFunctionalities.stream().map(Functionality::getId).collect(Collectors.toSet());
//
//        if (!currentFuncIds.equals(targetFuncIds)) {
//            needsSave = true;
//        }
//
//        if (needsSave) {
//            role.setFunctionalities(new HashSet<>(targetFunctionalities)); // 使用新的集合副本
//            roleRepository.save(role);
//            logger.info("DataInitializer: 已更新角色 {} 的功能权限为 {} 功能集。", role.getName(), roleTypeForLog);
//        } else {
//            logger.info("DataInitializer: 角色 {} 的功能权限已是最新 ({})。", role.getName(), roleTypeForLog);
//        }
//    }
//}