package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.CustomerCreationRequest;
import com.example.contractmanagementsystem.entity.Customer;
import com.example.contractmanagementsystem.exception.DuplicateResourceException;
import com.example.contractmanagementsystem.exception.ResourceNotFoundException;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
// import java.util.ArrayList; // 移除，因为未使用
// import java.util.List; // 移除，因为未使用
// import java.util.Optional; // 移除，因为未使用，或者根据后续逻辑保留

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional(readOnly = true)
    public Page<Customer> searchCustomers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAll(pageable);
        } else {
            // **修正点1：正确声明和使用 searchTerm**
            String searchTerm = keyword.toLowerCase().trim(); // searchTerm 变量声明
            Specification<Customer> spec = (root, query, criteriaBuilder) -> {
                Predicate namePredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("customerName")), "%" + searchTerm + "%");
                Predicate numberPredicate = criteriaBuilder.like(criteriaBuilder.lower(root.get("customerNumber")), "%" + searchTerm + "%");
                return criteriaBuilder.or(namePredicate, numberPredicate);
            };
            // **修正点2：确保 CustomerRepository 继承了 JpaSpecificationExecutor**
            return customerRepository.findAll(spec, pageable);
        }
    }

    @Transactional(readOnly = true)
    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("客户ID不存在: " + id));
    }

    @Transactional
    public Customer addCustomer(CustomerCreationRequest request) {
        String customerNumberTrimmed = request.getCustomerNumber().trim();
        String customerNameTrimmed = request.getCustomerName().trim();
        String emailTrimmed = (request.getEmail() != null) ? request.getEmail().trim().toLowerCase() : null;


        customerRepository.findByCustomerNumberIgnoreCase(customerNumberTrimmed)
                .ifPresent(c -> {
                    throw new DuplicateResourceException("客户编号已存在: " + request.getCustomerNumber());
                });

        customerRepository.findByCustomerNameContainingIgnoreCase(customerNameTrimmed, Pageable.unpaged()).stream()
                .filter(c -> c.getCustomerName().equalsIgnoreCase(customerNameTrimmed))
                .findFirst()
                .ifPresent(c -> {
                    throw new DuplicateResourceException("客户名称已存在: " + request.getCustomerName());
                });


        if (emailTrimmed != null && !emailTrimmed.isEmpty()) {
            customerRepository.findByEmail(emailTrimmed) // 假设 findByEmail 也应该是忽略大小写的，或者数据库层面保证唯一性
                    .ifPresent(c -> {
                        throw new DuplicateResourceException("邮箱已存在: " + request.getEmail());
                    });
        }

        Customer customer = new Customer();
        BeanUtils.copyProperties(request, customer);
        customer.setCustomerNumber(customerNumberTrimmed.toUpperCase());
        customer.setCustomerName(customerNameTrimmed);
        if (emailTrimmed != null) {
            customer.setEmail(emailTrimmed);
        }
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Long id, CustomerCreationRequest request) {
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("尝试更新的客户ID不存在: " + id));

        String newCustomerNumber = request.getCustomerNumber().trim().toUpperCase();
        if (!newCustomerNumber.equalsIgnoreCase(existing.getCustomerNumber())) {
            customerRepository.findByCustomerNumberIgnoreCase(newCustomerNumber)
                    .ifPresent(c -> {
                        throw new DuplicateResourceException("更新失败，客户编号已存在: " + request.getCustomerNumber());
                    });
            existing.setCustomerNumber(newCustomerNumber);
        }

        String newCustomerName = request.getCustomerName().trim();
        if (!newCustomerName.equalsIgnoreCase(existing.getCustomerName())) {
            customerRepository.findByCustomerNameContainingIgnoreCase(newCustomerName, Pageable.unpaged()).stream()
                    .filter(c -> c.getCustomerName().equalsIgnoreCase(newCustomerName) && !c.getId().equals(id))
                    .findFirst()
                    .ifPresent(c -> {
                        throw new DuplicateResourceException("更新失败，客户名称已存在: " + request.getCustomerName());
                    });
            existing.setCustomerName(newCustomerName);
        }

        if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            // 检查邮箱是否实际更改，并且新邮箱是否与现有邮箱（忽略大小写）不同
            if (existing.getEmail() == null || !newEmail.equalsIgnoreCase(existing.getEmail())) {
                customerRepository.findByEmail(newEmail) // 假设 findByEmail 应该是忽略大小写的
                        .filter(c -> !c.getId().equals(id))
                        .ifPresent(c -> {
                            throw new DuplicateResourceException("更新失败，邮箱已存在: " + request.getEmail());
                        });
                existing.setEmail(newEmail);
            }
        } else {
            existing.setEmail(null);
        }

        existing.setPhoneNumber(request.getPhoneNumber());
        existing.setAddress(request.getAddress());

        return customerRepository.save(existing);
    }

    @Transactional
    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
            throw new ResourceNotFoundException("尝试删除的客户ID不存在: " + id);
        }
        customerRepository.deleteById(id);
    }
}