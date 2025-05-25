package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.hibernate.Hibernate; // 确保导入 Hibernate
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet; // 新增导入 HashSet
import java.util.Set;
// import java.util.stream.Collectors; // Collectors 在此版本中不再直接使用，可以移除或保留

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Autowired
    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));

        // 在事务内急切加载角色及其功能，以避免懒加载异常
        // User 实体中 Role 的 fetch type 是 EAGER，但 Role 中的 Functionality 是 LAZY
        // 因此需要显式初始化功能列表
        user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));

        Set<GrantedAuthority> authorities = new HashSet<>(); // 初始化为 HashSet

        // 1. 加载角色名作为 GrantedAuthority (例如 "ROLE_ADMIN")
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        });

        // 2. 加载角色关联的功能名称作为 GrantedAuthority (例如 "起草合同")
        user.getRoles().forEach(role -> {
            role.getFunctionalities().forEach(functionality -> {
                // 使用功能名称 (Functionality.name) 作为权限字符串
                // 确保功能名称在系统中是唯一的，并且适合作为权限标识符
                if (functionality.getName() != null && !functionality.getName().trim().isEmpty()) {
                    authorities.add(new SimpleGrantedAuthority(functionality.getName()));
                }
                // 如果您更倾向于使用功能编号 (Functionality.num) 作为权限标识符，
                // 并且确保它非空且唯一，可以将上一行替换为:
                // if (functionality.getNum() != null && !functionality.getNum().trim().isEmpty()) {
                //    authorities.add(new SimpleGrantedAuthority(functionality.getNum()));
                // }
                // 但请注意，您在HTTP脚本中为角色分配功能时使用的是功能名称，因此这里也应该使用功能名称以保持一致。
            });
        });


        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                user.isEnabled(),
                true,               // accountNonExpired
                true,               // credentialsNonExpired
                true,               // accountNonLocked
                authorities
        );
    }
}