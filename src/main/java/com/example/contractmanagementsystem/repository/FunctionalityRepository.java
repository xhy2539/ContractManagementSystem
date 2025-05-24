package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Functionality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface FunctionalityRepository extends JpaRepository<Functionality, Long> {

    // 根据功能编号查找功能
    Optional<Functionality> findByNum(String num);

    // 根据功能名称查找功能
    Optional<Functionality> findByName(String name);
}