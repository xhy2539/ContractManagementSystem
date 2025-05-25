package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// 修改 EqualsAndHashCode 注解
@EqualsAndHashCode(of = {"username"}) // 或者 @EqualsAndHashCode(of = {"id", "username"}) 如果两者都重要
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // @EqualsAndHashCode.Include // 如果使用 of = {"username"}，这里可以移除或保留（如果id也重要）
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    // username 现在会通过 @EqualsAndHashCode(of = {"username"}) 参与
    private String username;

    @Column(nullable = false, length = 120)
    private String password;

    @Column(unique = true, length = 100)
    private String email;

    @Column(length = 50)
    private String realName;

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
        this.roles = new HashSet<>();
    }
}