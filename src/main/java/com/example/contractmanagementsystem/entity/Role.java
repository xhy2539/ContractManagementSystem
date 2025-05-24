package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "roles") // 对应数据字典中的 "角色(role)信息"
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true, length = 40) // 参照数据字典中的 name 字段
    private String name; // 例如 ROLE_ADMIN, ROLE_USER, ROLE_CONTRACT_OPERATOR

    @Column(length = 100) // 参照数据字典中的 description 字段
    private String description;

    // 如果角色与权限(功能操作)是多对多关系
    // @ManyToMany(fetch = FetchType.LAZY)
    // @JoinTable(name = "role_functionalities",
    //            joinColumns = @JoinColumn(name = "role_id"),
    //            inverseJoinColumns = @JoinColumn(name = "functionality_id"))
    // private Set<Functionality> functionalities = new HashSet<>();
}