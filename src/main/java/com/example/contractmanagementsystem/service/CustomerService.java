package com.example.contractmanagementsystem.service;

import com.example.contractmanagementsystem.dto.CustomerCreationRequest;
import com.example.contractmanagementsystem.entity.Customer;
//import com.example.contractmanagementsystem.exception.DuplicateCustomerException;
//import com.example.contractmanagementsystem.exception.CustomerNotFoundException;
import com.example.contractmanagementsystem.repository.CustomerRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Page<Customer> searchCustomers(String keyword, Pageable pageable) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return customerRepository.findAll(pageable);
        } else {
            return customerRepository.findByCustomerNameContainingIgnoreCase(keyword, pageable);
        }
    }

    public Customer getCustomerById(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(/*() -> new CustomerNotFoundException("客户ID不存在: " + id)*/);
    }

    public Customer addCustomer(CustomerCreationRequest request) {
        // 检查客户编号是否唯一
        if (customerRepository.findByCustomerNumber(request.getCustomerNumber()).isPresent()) {
//            throw new DuplicateCustomerException("客户编号已存在: " + request.getCustomerNumber());
        }

        // 检查客户名称是否唯一
        if (customerRepository.findByCustomerName(request.getCustomerName()).isPresent()) {
//            throw new DuplicateCustomerException("客户名称已存在: " + request.getCustomerName());
        }

        // 检查邮箱是否唯一
        if (request.getEmail() != null && !request.getEmail().isEmpty() &&
                customerRepository.findByEmail(request.getEmail()).isPresent()) {
//            throw new DuplicateCustomerException("邮箱已存在: " + request.getEmail());
        }

        Customer customer = new Customer();
        BeanUtils.copyProperties(request, customer);
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(Long id, CustomerCreationRequest request) {
        Customer existing = customerRepository.findById(id)
                .orElseThrow(/*() -> new CustomerNotFoundException("客户ID不存在: " + id)*/);

        // 检查客户编号是否变更且是否冲突
        if (!request.getCustomerNumber().equals(existing.getCustomerNumber())) {
            if (customerRepository.findByCustomerNumber(request.getCustomerNumber()).isPresent()) {
//                throw new DuplicateCustomerException("客户编号已存在: " + request.getCustomerNumber());
            }
        }

        // 检查客户名称是否变更且是否冲突
        if (!request.getCustomerName().equals(existing.getCustomerName())) {
            if (customerRepository.findByCustomerName(request.getCustomerName()).isPresent()) {
//                throw new DuplicateCustomerException("客户名称已存在: " + request.getCustomerName());
            }
        }

        // 检查邮箱是否变更且是否冲突
        if (request.getEmail() != null && !request.getEmail().isEmpty() &&
                !request.getEmail().equals(existing.getEmail()) &&
                customerRepository.findByEmail(request.getEmail()).isPresent()) {
//            throw new DuplicateCustomerException("邮箱已存在: " + request.getEmail());
        }

        // 更新字段
        existing.setCustomerNumber(request.getCustomerNumber());
        existing.setCustomerName(request.getCustomerName());
        existing.setPhoneNumber(request.getPhoneNumber());
        existing.setEmail(request.getEmail());
        existing.setAddress(request.getAddress());

        return customerRepository.save(existing);
    }

    public void deleteCustomer(Long id) {
        if (!customerRepository.existsById(id)) {
//            throw new CustomerNotFoundException("客户ID不存在: " + id);
        }
        customerRepository.deleteById(id);
    }
}