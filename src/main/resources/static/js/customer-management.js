// 客户管理界面的JavaScript代码
document.addEventListener('DOMContentLoaded', function() {
    'use strict';
    
    // 初始化编辑按钮事件监听
    initializeEditButtons();
    
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
                    // 填充数据到表单
                    inputs.id.value = customerId;
                    inputs.number.value = customerNumber;
                    inputs.name.value = customerName;
                    inputs.phone.value = phoneNumber;
                    inputs.email.value = email;
                    inputs.address.value = address;
                    
                    // 清除之前的验证状态
                    editForm.classList.remove('was-validated');
                    editForm.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
                    
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