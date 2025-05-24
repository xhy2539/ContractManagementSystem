package com.example.contractmanagementsystem.service; // 或者 com.example.contractmanagementsystem.security;

import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

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

        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName())) // Role 的 name 字段应该是 "ROLE_ADMIN", "ROLE_USER" 这样的格式
                .collect(Collectors.toSet());

        // 如果 Role 实体还有更细致的权限（Functionality），也可以在这里加载它们作为 GrantedAuthority
        // 例如，将 Functionality 的 name 或 num 作为权限字符串
        // user.getRoles().forEach(role -> {
        //     role.getFunctionalities().forEach(functionality -> {
        //         authorities.add(new SimpleGrantedAuthority(functionality.getName())); // 或者 functionality.getNum()
        //     });
        // });


        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(), // 数据库中存储的应该是已加密的密码
                user.isEnabled(),   // 用户是否启用
                true,               // accountNonExpired
                true,               // credentialsNonExpired
                true,               // accountNonLocked
                authorities
        );
    }
}