package com.example.contractmanagementsystem.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode; // 确保导入

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true) // <--- 确保此行存在且正确
@Entity
@Table(name = "functionalities")
public class Functionality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include // <--- 确保此行存在且正确 (id 参与 equals/hashCode)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String num;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Column(length = 100)
    private String url;

    @Column(length = 100)
    private String description;
}