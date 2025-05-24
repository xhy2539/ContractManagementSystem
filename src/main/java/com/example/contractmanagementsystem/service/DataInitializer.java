package com.example.contractmanagementsystem.service; // 或者您选择的其他包

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.RoleRepository;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    // 定义管理员用户名和密码常量，方便管理
    // **重要提示**：在实际生产环境中，初始密码不应硬编码在此处，
    // 或者应使用更安全的机制（例如，从受保护的配置文件读取，或首次启动后强制修改）。
    // 为了测试，我们暂时硬编码。
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_DEFAULT_PASSWORD = "adminpassword"; // 您登录时需要使用的明文密码

    @Autowired
    public DataInitializer(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional // 确保所有数据库操作在一个事务中
    public void run(String... args) throws Exception {
        logger.info("DataInitializer: 开始检查并初始化基础数据...");

        // 1. 检查并创建 ROLE_ADMIN 角色
        String adminRoleName = "ROLE_ADMIN"; // Spring Security 默认角色名前缀是 "ROLE_"
        Role adminRole = roleRepository.findByName(adminRoleName).orElseGet(() -> {
            logger.info("DataInitializer: 角色 {} 不存在，正在创建...", adminRoleName);
            Role newAdminRole = new Role();
            newAdminRole.setName(adminRoleName);
            newAdminRole.setDescription("系统管理员角色");
            // 如果需要，可以在这里为管理员角色预设一些核心功能
            // Set<Functionality> adminFunctionalities = functionalityRepository.findAllByNameIn(Set.of("SOME_CORE_FUNC"));
            // newAdminRole.setFunctionalities(adminFunctionalities);
            return roleRepository.save(newAdminRole);
        });
        logger.info("DataInitializer: 角色 {} 已确保存在。", adminRole.getName());

        // 2. 检查并创建默认管理员用户
        if (!userRepository.existsByUsername(ADMIN_USERNAME)) {
            logger.info("DataInitializer: 管理员用户 {} 不存在，正在创建...", ADMIN_USERNAME);
            User adminUser = new User();
            adminUser.setUsername(ADMIN_USERNAME);
            // 使用 PasswordEncoder 加密密码
            adminUser.setPassword(passwordEncoder.encode(ADMIN_DEFAULT_PASSWORD));
            adminUser.setEmail("admin@example.com"); // 您可以修改这个邮箱
            adminUser.setEnabled(true);

            Set<Role> adminRoles = new HashSet<>();
            adminRoles.add(adminRole); // 将之前创建或获取的 ROLE_ADMIN 关联给用户
            adminUser.setRoles(adminRoles);

            userRepository.save(adminUser);
            logger.info("DataInitializer: 管理员用户 {} 创建成功。请使用用户名 '{}' 和密码 '{}' 登录。",
                    ADMIN_USERNAME, ADMIN_USERNAME, ADMIN_DEFAULT_PASSWORD);
        } else {
            logger.info("DataInitializer: 管理员用户 {} 已存在。登录密码为之前设置或已修改的密码。", ADMIN_USERNAME);
        }

        // 3. (可选) 检查并创建 ROLE_USER 角色 (如果您的系统有普通用户角色)
        String userRoleName = "ROLE_USER";
        Role userRole = roleRepository.findByName(userRoleName).orElseGet(() -> {
            logger.info("DataInitializer: 角色 {} 不存在，正在创建...", userRoleName);
            Role newUserRole = new Role();
            newUserRole.setName(userRoleName);
            newUserRole.setDescription("普通用户角色");
            return roleRepository.save(newUserRole);
        });
        logger.info("DataInitializer: 角色 {} 已确保存在。", userRole.getName());


        logger.info("DataInitializer: 基础数据初始化完成。");
    }
}