package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode; // 确保导入
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // <--- 确保此行存在且正确
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // <--- 确保此行存在且正确
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 50) // 新增 realName 字段
    private String realName; // 用户真实姓名

    private boolean enabled = true;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.enabled = true;
        this.roles = new HashSet<>(); // 确保构造函数也初始化了集合
    }

    // 如果 UserCreationRequest 和 UserUpdateRequest 需要包含 realName，
    // 那么相应的 DTO 和 Controller/Service 层创建/更新逻辑也需要调整。
    // 例如，在 createUser 时，可以从 UserCreationRequest 获取 realName 并设置。
}