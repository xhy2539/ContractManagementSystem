package com.example.contractmanagementsystem.repository;

import com.example.contractmanagementsystem.entity.Contract;
import com.example.contractmanagementsystem.entity.ContractVersion;
import com.example.contractmanagementsystem.entity.ContractVersion.ChangeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ContractVersionRepository extends JpaRepository<ContractVersion, Long> {

    /**
     * 根据合同查找所有版本，按版本号降序排列
     */
    List<ContractVersion> findByContractOrderByVersionNumberDesc(Contract contract);

    /**
     * 根据合同ID查找所有版本，按版本号降序排列
     */
    @Query("SELECT cv FROM ContractVersion cv WHERE cv.contract.id = :contractId ORDER BY cv.versionNumber DESC")
    List<ContractVersion> findByContractIdOrderByVersionNumberDesc(@Param("contractId") Long contractId);

    /**
     * 根据合同查找最新版本
     */
    Optional<ContractVersion> findFirstByContractOrderByVersionNumberDesc(Contract contract);

    /**
     * 根据合同ID查找最新版本
     */
    @Query("SELECT cv FROM ContractVersion cv WHERE cv.contract.id = :contractId ORDER BY cv.versionNumber DESC LIMIT 1")
    Optional<ContractVersion> findLatestByContractId(@Param("contractId") Long contractId);

    /**
     * 根据合同和版本号查找特定版本
     */
    Optional<ContractVersion> findByContractAndVersionNumber(Contract contract, Integer versionNumber);

    /**
     * 根据合同ID和版本号查找特定版本
     */
    Optional<ContractVersion> findByContractIdAndVersionNumber(Long contractId, Integer versionNumber);

    /**
     * 获取合同的下一个版本号
     */
    @Query("SELECT COALESCE(MAX(cv.versionNumber), 0) + 1 FROM ContractVersion cv WHERE cv.contract.id = :contractId")
    Integer getNextVersionNumber(@Param("contractId") Long contractId);

    /**
     * 查找指定合同的指定变更类型的版本
     */
    List<ContractVersion> findByContractAndChangeTypeOrderByVersionNumberDesc(Contract contract, ChangeType changeType);

    /**
     * 分页查询合同版本历史
     */
    Page<ContractVersion> findByContractOrderByVersionNumberDesc(Contract contract, Pageable pageable);

    /**
     * 统计合同的版本数量
     */
    @Query("SELECT COUNT(cv) FROM ContractVersion cv WHERE cv.contract.id = :contractId")
    Long countByContractId(@Param("contractId") Long contractId);

    /**
     * 删除指定合同的所有版本
     */
    void deleteByContract(Contract contract);

    /**
     * 查找两个版本之间的所有版本（用于版本比对）
     */
    @Query("SELECT cv FROM ContractVersion cv WHERE cv.contract.id = :contractId AND cv.versionNumber BETWEEN :fromVersion AND :toVersion ORDER BY cv.versionNumber ASC")
    List<ContractVersion> findVersionsBetween(@Param("contractId") Long contractId, @Param("fromVersion") Integer fromVersion, @Param("toVersion") Integer toVersion);

    /**
     * 根据合同查找所有版本记录，按创建时间倒序排序
     */
    List<ContractVersion> findByContractOrderByCreatedAtDesc(Contract contract);
} 