package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.entity.Functionality; // 确保 Functionality 被导入
import com.example.contractmanagementsystem.entity.Role;
import com.example.contractmanagementsystem.entity.User;
import com.example.contractmanagementsystem.repository.UserRepository;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

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

        // Eagerly load roles and their functionalities within the transaction to avoid lazy loading exceptions
        user.getRoles().forEach(role -> Hibernate.initialize(role.getFunctionalities()));

        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Load role names as GrantedAuthority (e.g., "ROLE_ADMIN")
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority(role.getName()));
        });

        // 2. Load role-associated functionality numbers (Functionality.num) as GrantedAuthority
        user.getRoles().forEach(role -> {
            if (role.getFunctionalities() != null) {
                role.getFunctionalities().forEach(functionality -> {
                    // **Core change: Use functionality number (Functionality.num) as the permission string**
                    if (functionality.getNum() != null && !functionality.getNum().trim().isEmpty()) {
                        authorities.add(new SimpleGrantedAuthority(functionality.getNum()));
                    }
                });
            }
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