// 客户管理界面的JavaScript代码
document.addEventListener('DOMContentLoaded', function() {
    'use strict';
    
    // 初始化编辑按钮事件监听
    initializeEditButtons();
    
    // 初始化表单提交事件
    const editForm = document.getElementById('editCustomerForm');
    if (editForm) {
        // 监听输入变化，移除错误状态
        editForm.querySelectorAll('input').forEach(input => {
            input.addEventListener('input', function() {
                this.classList.remove('is-valid', 'is-invalid', 'has-error');
                const feedback = this.nextElementSibling;
                if (feedback && feedback.classList.contains('invalid-feedback')) {
                    feedback.style.display = 'none';
                }
            });
        });

        editForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            // 获取表单数据
            const formData = new FormData(this);
            
            // 发送请求
            fetch(this.action, {
                method: 'POST',
                body: formData,
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            })
            .then(response => {
                const contentType = response.headers.get('content-type');
                if (contentType && contentType.includes('application/json')) {
                    return response.json().then(data => {
                        if (!response.ok) {
                            throw new Error(typeof data === 'string' ? data : data.message || '更新失败');
                        }
                        return data;
                    });
                } else {
                    return response.text().then(text => {
                        if (!response.ok) {
                            throw new Error(text);
                        }
                        return text;
                    });
                }
            })
            .then(() => {
                // 成功后刷新页面
                window.location.reload();
            })
            .catch(error => {
                // 处理错误
                let errorMessage = error.message;
                if (errorMessage.includes('客户编号已存在')) {
                    // 显示错误消息
                    const numberInput = document.getElementById('editCustomerNumber');
                    numberInput.classList.remove('is-valid');
                    numberInput.classList.add('is-invalid', 'has-error');
                    const feedback = numberInput.nextElementSibling;
                    if (feedback) {
                        feedback.textContent = '该客户编号已存在，请使用其他编号';
                        feedback.style.display = 'block';
                    }
                    // 保持模态框打开
                    const modal = bootstrap.Modal.getInstance(document.getElementById('editCustomerModal'));
                    if (modal) {
                        modal.show();
                    }
                } else {
                    // 显示一般错误消息
                    const errorDiv = document.createElement('div');
                    errorDiv.className = 'alert alert-danger alert-dismissible fade show';
                    errorDiv.innerHTML = `
                        <strong>更新失败：</strong> ${errorMessage}
                        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                    `;
                    
                    // 在模态框内显示错误
                    const modalBody = document.querySelector('#editCustomerModal .modal-body');
                    if (modalBody) {
                        modalBody.insertBefore(errorDiv, modalBody.firstChild);
                    }
                    
                    // 保持模态框打开
                    const modal = bootstrap.Modal.getInstance(document.getElementById('editCustomerModal'));
                    if (modal) {
                        modal.show();
                    }
                }
            });
        });
    }
    
    function initializeEditButtons() {
        // 使用事件委托，监听整个文档的点击事件
        document.addEventListener('click', function(e) {
            const editBtn = e.target.closest('button[data-bs-target="#editCustomerModal"]');
            if (!editBtn) return;
            
            e.preventDefault();
            e.stopPropagation();
            
            // 获取客户数据
            const customerId = editBtn.getAttribute('data-id');
            const customerNumber = editBtn.getAttribute('data-number');
            const customerName = editBtn.getAttribute('data-name');
            const phoneNumber = editBtn.getAttribute('data-phone');
            const email = editBtn.getAttribute('data-email');
            const address = editBtn.getAttribute('data-address') || '';
            
            // 填充表单数据
            const editForm = document.getElementById('editCustomerForm');
            if (editForm) {
                const inputs = {
                    id: document.getElementById('editCustomerId'),
                    number: document.getElementById('editCustomerNumber'),
                    name: document.getElementById('editCustomerName'),
                    phone: document.getElementById('editCustomerPhone'),
                    email: document.getElementById('editCustomerEmail'),
                    address: document.getElementById('editCustomerAddress')
                };
                
                // 检查所有必需的输入框是否存在
                let allInputsFound = true;
                for (const [key, input] of Object.entries(inputs)) {
                    if (!input) {
                        console.error(`找不到输入框: ${key}`);
                        allInputsFound = false;
                    }
                }
                
                if (allInputsFound) {
                    // 清除之前的错误提示
                    const modalBody = document.querySelector('#editCustomerModal .modal-body');
                    if (modalBody) {
                        const existingAlert = modalBody.querySelector('.alert');
                        if (existingAlert) {
                            existingAlert.remove();
                        }
                    }
                    
                    // 填充数据到表单
                    inputs.id.value = customerId;
                    inputs.number.value = customerNumber;
                    inputs.name.value = customerName;
                    inputs.phone.value = phoneNumber;
                    inputs.email.value = email;
                    inputs.address.value = address;
                    
                    // 清除之前的验证状态
                    editForm.classList.remove('was-validated');
                    editForm.querySelectorAll('input').forEach(el => {
                        el.classList.remove('is-valid', 'is-invalid', 'has-error');
                        const feedback = el.nextElementSibling;
                        if (feedback && feedback.classList.contains('invalid-feedback')) {
                            feedback.style.display = 'none';
                        }
                    });
                    
                    // 显示模态框
                    const editModal = document.getElementById('editCustomerModal');
                    if (editModal) {
                        const modal = new bootstrap.Modal(editModal);
                        modal.show();
                    }
                }
            }
        });
    }
}); 