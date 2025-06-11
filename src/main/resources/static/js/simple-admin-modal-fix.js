// 通用模态框管理器 - 所有模态框显示和隐藏的唯一管理者
console.log("🚀 通用模态框管理器启动");

(function() {
    'use strict';
    
    if (window.universalModalManager) {
        console.log("通用模态框管理器已加载，跳过");
        return;
    }
    window.universalModalManager = true;
    
    // 模态框实例存储
    const modalInstances = new Map();
    
    // 客户选择全局变量
    window.customerSelectGlobals = window.customerSelectGlobals || {
        currentCustomerPage: 0,
        CUSTOMER_PAGE_SIZE: 5,
        customerSearchKeyword: '',
        customerSelectModal: null,
        isInitialized: false
    };
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 100);
        }
    }
    
    function setup() {
        console.log("🔧 设置通用模态框管理器");
        
        // 查找并修复所有模态框
        const modalSelectors = [
            '#userFormModal',
            '#roleFormModal', 
            '#functionalityFormModal',
            '#assignRolesModal',
            '#templateFormModal',
            '#customerFormModal',
            '#addCustomerModal',
            '#editCustomerModal',
            '#attachmentListModal',
            '#customerSelectModal',
            '#addCustomerFormModal',
            '#fileTypeHelpModal',
            '#adminExtendModal',
            '#operatorRequestExtendModal'
        ];
        
        modalSelectors.forEach(selector => {
            const modalEl = document.querySelector(selector);
            if (modalEl) {
                console.log(`🎯 修复模态框: ${selector}`);
                fixModal(modalEl);
            }
        });
        
        // 修复触发按钮
        fixTriggerButtons();
        
        // 修复表格按钮
        fixTableButtons();
        
        // 修复客户选择功能
        fixCustomerSelection();
        
        console.log("✅ 通用模态框管理器修复完成");
    }

    function fixModal(modalEl) {
        const modalId = modalEl.id;
        
        // 移除Bootstrap属性
        modalEl.removeAttribute('data-bs-backdrop');
        modalEl.removeAttribute('data-bs-keyboard');
        modalEl.classList.remove('fade');
        
        // 修复关闭按钮
        const closeButtons = modalEl.querySelectorAll('.btn-close, button[data-bs-dismiss="modal"]');
        closeButtons.forEach(btn => {
            btn.removeAttribute('data-bs-dismiss');
            btn.onclick = null;
            
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                console.log(`🔧 关闭模态框: ${modalId}`);
                hideModal(modalEl);
            });
        });
        
        // 修复取消和关闭按钮（通过文本识别）
        const cancelButtons = modalEl.querySelectorAll('button[type="button"]');
        cancelButtons.forEach(btn => {
            const btnText = btn.textContent.trim();
            if (btnText === '关闭' || btnText === '取消') {
                btn.onclick = null;
                btn.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`🔧 点击${btnText}按钮关闭: ${modalId}`);
                    hideModal(modalEl);
                });
            }
        });
    }
    
    function fixTriggerButtons() {
        // 修复添加按钮
        const triggerButtons = [
            { id: 'addUserBtn', modalId: 'userFormModal' },
            { id: 'addRoleBtn', modalId: 'roleFormModal' },
            { id: 'addFunctionalityBtn', modalId: 'functionalityFormModal' },
            { id: 'addTemplateBtn', modalId: 'templateFormModal' },
            { id: 'addCustomerBtn', modalId: 'customerFormModal' },
            { id: 'openCustomerSelectModalBtn', modalId: 'customerSelectModal' },
            { selector: 'button[data-bs-target="#fileTypeHelpModal"]', modalId: 'fileTypeHelpModal' }
        ];
        
        triggerButtons.forEach(({ id, selector, modalId }) => {
            const btn = id ? document.getElementById(id) : document.querySelector(selector);
            const modalEl = document.getElementById(modalId);
            
            if (btn && modalEl) {
                btn.removeAttribute('data-bs-toggle');
                btn.removeAttribute('data-bs-target');
                btn.onclick = null;
                
                btn.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`🎯 点击触发按钮: ${id || selector}`);
                    
                    // 特殊处理客户选择模态框
                    if (modalId === 'customerSelectModal') {
                        openCustomerSelectModal();
                    } else {
                        showModal(modalEl);
                    }
                });
            }
        });
    }
    
    function fixTableButtons() {
        // 使用事件委托处理表格按钮
        document.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            
            const classList = btn.classList;
            console.log(`🎯 检测到按钮点击:`, classList.toString());
            
            // 编辑按钮处理
            if (classList.contains('edit-user-btn') || 
                classList.contains('edit-role-btn') || 
                classList.contains('edit-func-btn') ||
                classList.contains('edit-template-btn') ||
                classList.contains('assign-roles-btn') ||
                classList.contains('edit-customer-btn')) {
                
                console.log('🔧 编辑相关按钮点击，处理中...');
                
                // 短暂延迟后检查并修复模态框显示
                setTimeout(() => {
                    let targetModalId = null;
                    
                    if (classList.contains('edit-user-btn')) {
                        targetModalId = 'userFormModal';
                    } else if (classList.contains('assign-roles-btn')) {
                        targetModalId = 'assignRolesModal';
                    } else if (classList.contains('edit-role-btn')) {
                        targetModalId = 'roleFormModal';
                    } else if (classList.contains('edit-func-btn')) {
                        targetModalId = 'functionalityFormModal';
                    } else if (classList.contains('edit-template-btn')) {
                        targetModalId = 'templateFormModal';
                    } else if (classList.contains('edit-customer-btn')) {
                        targetModalId = 'editCustomerModal';
                    }
                    
                    if (targetModalId) {
                        const modal = document.getElementById(targetModalId);
                        if (modal) {
                            forceModalVisibility(modal);
                        }
                    }
                }, 50);
            }
            
            // 删除按钮 - 仅强制修复
            if (classList.contains('delete-user-btn') || 
                classList.contains('delete-role-btn') || 
                classList.contains('delete-func-btn') ||
                classList.contains('delete-template-btn') ||
                classList.contains('delete-customer-btn')) {
                
                setTimeout(() => {
                    const confirmModal = document.querySelector('.modal.show');
                    if (confirmModal) {
                        forceModalVisibility(confirmModal);
                    }
                }, 50);
            }
            
            // 延期按钮处理
            if (classList.contains('admin-extend-btn')) {
                console.log('🔧 管理员延期按钮点击');
                
                setTimeout(() => {
                    const modal = document.getElementById('adminExtendModal');
                    if (modal) {
                        populateAdminExtendModal(btn, modal);
                        showModal(modal);
                    }
                }, 50);
            }
            
            if (classList.contains('operator-extend-btn')) {
                console.log('🔧 操作员延期按钮点击');
                
                setTimeout(() => {
                    const modal = document.getElementById('operatorRequestExtendModal');
                    if (modal) {
                        populateOperatorRequestExtendModal(btn, modal);
                        showModal(modal);
                    }
                }, 50);
            }
            
            // 附件查看按钮处理
            if (classList.contains('view-attachments-btn') || classList.contains('attachment-view-btn')) {
                console.log('🔧 附件查看按钮点击');
                
                setTimeout(() => {
                    const modal = document.getElementById('attachmentListModal');
                    if (modal) {
                        const attachmentsJson = btn.getAttribute('data-attachments');
                        const contractName = btn.getAttribute('data-contract-name') || '未知合同';
                        loadAttachmentData(modal, btn, attachmentsJson, contractName);
                        showModal(modal);
                    }
                }, 50);
            }
            
            // 客户选择相关按钮
            if (classList.contains('modalAddNewCustomerBtn')) {
                e.preventDefault();
                e.stopPropagation();
                console.log('🔧 添加新客户按钮点击');
                
                const customerSelectModal = document.getElementById('customerSelectModal');
                const addCustomerModal = document.getElementById('addCustomerFormModal');
                
                if (customerSelectModal && addCustomerModal) {
                    hideModal(customerSelectModal);
                    setTimeout(() => showModal(addCustomerModal), 150);
                }
            }
            
            if (classList.contains('backToCustomerSelectModalBtn')) {
                e.preventDefault();
                e.stopPropagation();
                console.log('🔧 返回客户选择按钮点击');
                
                const addCustomerModal = document.getElementById('addCustomerFormModal');
                const customerSelectModal = document.getElementById('customerSelectModal');
                
                if (addCustomerModal && customerSelectModal) {
                    hideModal(addCustomerModal);
                    setTimeout(() => showModal(customerSelectModal), 150);
                }
            }
            
            // 通用关闭按钮处理
            if (classList.contains('btn-close')) {
                e.preventDefault();
                e.stopPropagation();
                
                const modal = btn.closest('.modal');
                if (modal) {
                    console.log(`🔧 通用关闭按钮点击: ${modal.id}`);
                    hideModal(modal);
                }
            }
        });
    }

    // 附件数据加载
    function loadAttachmentData(modal, button, attachmentsJson, contractName) {
        const modalBody = modal.querySelector('.modal-body');
        
        function addExistingFileToUI(serverFileName) {
            const encodedFileName = encodeURIComponent(serverFileName);
            const fileExtension = serverFileName.split('.').pop().toLowerCase();
            const isPreviewable = ['jpg', 'jpeg', 'png', 'gif', 'pdf', 'mp4', 'avi', 'mov'].includes(fileExtension);
            
            return `
                <div class="uploaded-file mb-2 p-2 border rounded d-flex align-items-center justify-content-between">
                    <div class="file-info d-flex align-items-center">
                        <i class="bi bi-file-earmark-text me-2"></i>
                        <span class="file-name">${serverFileName}</span>
                    </div>
                    <div class="file-actions">
                        <a href="/uploads/${encodedFileName}" class="btn btn-outline-primary btn-sm me-2" target="_blank">
                            <i class="bi bi-download"></i> 下载
                        </a>
                        ${isPreviewable ? `<button type="button" class="btn btn-outline-info btn-sm" onclick="handlePreviewFile('${serverFileName}')">
                            <i class="bi bi-eye"></i> 预览
                        </button>` : ''}
                    </div>
                </div>
            `;
        }
        
        try {
            const attachments = JSON.parse(attachmentsJson || '[]');
            let content = `<h6 class="mb-3">${contractName} - 附件列表</h6>`;
            
            if (attachments.length === 0) {
                content += '<p class="text-muted">暂无附件</p>';
            } else {
                content += '<div class="attachments-list">';
                attachments.forEach(filename => {
                    content += addExistingFileToUI(filename);
                });
                content += '</div>';
            }
            
            modalBody.innerHTML = content;
        } catch (e) {
            console.error('解析附件数据失败:', e);
            modalBody.innerHTML = '<p class="text-danger">加载附件列表失败</p>';
        }
    }

    // 文件预览处理
    function handlePreviewFile(serverFileName) {
        const fileExtension = serverFileName.split('.').pop().toLowerCase();
        const encodedFileName = encodeURIComponent(serverFileName);
        const fileUrl = `/uploads/${encodedFileName}`;
        
        let modalContent = '';
        if (['jpg', 'jpeg', 'png', 'gif'].includes(fileExtension)) {
            modalContent = `<img src="${fileUrl}" class="img-fluid" alt="${serverFileName}">`;
        } else if (fileExtension === 'pdf') {
            modalContent = `<embed src="${fileUrl}" type="application/pdf" width="100%" height="600px">`;
        } else if (['mp4', 'avi', 'mov'].includes(fileExtension)) {
            modalContent = `<video width="100%" height="400" controls><source src="${fileUrl}" type="video/${fileExtension}">您的浏览器不支持视频播放。</video>`;
        }
        
        const previewModal = document.createElement('div');
        previewModal.className = 'modal fade';
        previewModal.innerHTML = `
            <div class="modal-dialog modal-lg">
                <div class="modal-content">
                    <div class="modal-header">
                        <h5 class="modal-title">文件预览 - ${serverFileName}</h5>
                        <button type="button" class="btn-close"></button>
                    </div>
                    <div class="modal-body text-center">
                        ${modalContent}
                    </div>
                </div>
            </div>
        `;
        
        document.body.appendChild(previewModal);
        const bsModal = new bootstrap.Modal(previewModal);
        bsModal.show();
        
        previewModal.addEventListener('hidden.bs.modal', () => {
            document.body.removeChild(previewModal);
        });
        
        previewModal.querySelector('.btn-close').addEventListener('click', () => {
            bsModal.hide();
        });
    }

    // 客户选择功能
    function fixCustomerSelection() {
        const customerSelectModal = document.getElementById('customerSelectModal');
        if (!customerSelectModal) return;
        
        // 初始化客户搜索
        const searchInput = customerSelectModal.querySelector('#customerSearchKeyword');
        if (searchInput) {
            searchInput.addEventListener('input', function() {
                const keyword = this.value.trim();
                window.customerSelectGlobals.customerSearchKeyword = keyword;
                window.customerSelectGlobals.currentCustomerPage = 0;
                searchCustomers(keyword);
            });
        }
        
        // 绑定翻页事件
        customerSelectModal.addEventListener('click', function(e) {
            if (e.target.classList.contains('customer-page-btn')) {
                e.preventDefault();
                const page = parseInt(e.target.getAttribute('data-page'));
                window.customerSelectGlobals.currentCustomerPage = page;
                loadCustomerData(page, window.customerSelectGlobals.customerSearchKeyword);
            }
        });

        // 添加客户表单提交处理
        const addCustomerForm = document.getElementById('addCustomerFormInModal_draft');
        if (addCustomerForm) {
            addCustomerForm.addEventListener('submit', function(e) {
                e.preventDefault(); // 阻止默认提交
                console.log('🚀 处理客户添加表单提交');
                
                // 收集表单数据
                const formData = new FormData(this);
                
                // 显示加载状态
                const submitBtn = this.querySelector('#saveNewCustomerBtn_draft');
                const originalBtnText = submitBtn.innerHTML;
                if (submitBtn) {
                    submitBtn.disabled = true;
                    submitBtn.innerHTML = '<i class="spinner-border spinner-border-sm me-1"></i>保存中...';
                }
                
                // 发送到正确的API端点
                fetch('/customers/api/add', {
                    method: 'POST',
                    body: formData
                })
                .then(response => {
                    console.log('📡 添加客户响应状态:', response.status);
                    if (!response.ok) {
                        return response.json().then(errorData => {
                            throw new Error(errorData.message || `HTTP ${response.status}: ${response.statusText}`);
                        });
                    }
                    return response.json();
                })
                .then(data => {
                    console.log('✅ 客户添加成功:', data);
                    
                    // 重置表单
                    addCustomerForm.reset();
                    addCustomerForm.classList.remove('was-validated');
                    
                    // 关闭添加客户模态框
                    const addCustomerModal = document.getElementById('addCustomerFormModal');
                    hideModal(addCustomerModal);
                    
                    // 重新打开客户选择模态框并刷新数据
                    setTimeout(() => {
                        loadCustomerData(0, ''); // 刷新客户列表
                        showModal(customerSelectModal);
                        
                        // 显示成功消息
                        const alertContainer = customerSelectModal.querySelector('.modal-body');
                        if (alertContainer) {
                            const successAlert = document.createElement('div');
                            successAlert.className = 'alert alert-success alert-dismissible fade show';
                            successAlert.innerHTML = `
                                <i class="bi bi-check-circle-fill me-2"></i>
                                客户"${data.customerName}"添加成功！
                                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                            `;
                            alertContainer.insertBefore(successAlert, alertContainer.firstChild);
                            
                            // 3秒后自动关闭提示
                            setTimeout(() => {
                                if (successAlert.parentNode) {
                                    successAlert.remove();
                                }
                            }, 3000);
                        }
                    }, 300);
                })
                .catch(error => {
                    console.error('❌ 添加客户失败:', error);
                    
                    // 显示错误消息
                    const alertContainer = addCustomerForm.querySelector('#addCustomerModalAlertPlaceholder_draft');
                    if (alertContainer) {
                        alertContainer.innerHTML = `
                            <div class="alert alert-danger alert-dismissible fade show">
                                <i class="bi bi-exclamation-triangle-fill me-2"></i>
                                添加客户失败: ${error.message}
                                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
                            </div>
                        `;
                    }
                })
                .finally(() => {
                    // 恢复按钮状态
                    if (submitBtn) {
                        submitBtn.disabled = false;
                        submitBtn.innerHTML = originalBtnText;
                    }
                });
            });
        }
    }
    
    function openCustomerSelectModal() {
        console.log('🔧 打开客户选择模态框');
        const modal = document.getElementById('customerSelectModal');
        if (modal) {
            if (!window.customerSelectGlobals.isInitialized) {
                loadCustomerData(0, '');
                window.customerSelectGlobals.isInitialized = true;
            }
            showModal(modal);
        }
    }
    
    function searchCustomers(keyword) {
        console.log('🔍 搜索客户:', keyword);
        loadCustomerData(0, keyword);
    }
    
    function loadCustomerData(page, keyword) {
        console.log('🚀 开始加载客户数据 - 页面:', page, '关键词:', keyword);
        
        // 确保全局变量已初始化
        if (!window.customerSelectGlobals) {
            console.warn('⚠️ 全局变量未初始化，使用默认值');
            window.customerSelectGlobals = {
                CUSTOMER_PAGE_SIZE: 5,
                currentCustomerPage: 0,
                customerSearchKeyword: '',
                isInitialized: false
            };
        }
        
        // 修复API路径，使用正确的后端端点
        const url = `/customers/api/search?page=${page}&size=${window.customerSelectGlobals.CUSTOMER_PAGE_SIZE}&keyword=${encodeURIComponent(keyword || '')}`;
        
        console.log('🌐 请求客户数据 URL:', url);
        console.log('📊 请求参数:', { page, size: window.customerSelectGlobals.CUSTOMER_PAGE_SIZE, keyword });
        
        // 显示加载指示器
        const tableBody = document.querySelector('#customerSelectTableBody');
        if (tableBody) {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center">
                <div class="spinner-border spinner-border-sm text-primary me-2" role="status">
                    <span class="visually-hidden">加载中...</span>
                </div>
                正在加载客户数据...
            </td></tr>`;
        }
        
        fetch(url)
            .then(response => {
                console.log('📡 响应状态:', response.status, response.statusText);
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}: ${response.statusText}`);
                }
                return response.json();
            })
            .then(data => {
                console.log('✅ 客户数据加载成功:', data);
                console.log('📋 客户数据结构检查:', {
                    totalElements: data.totalElements,
                    totalPages: data.totalPages,
                    number: data.number,
                    size: data.size,
                    contentLength: data.content ? data.content.length : 0,
                    firstCustomer: data.content && data.content.length > 0 ? data.content[0] : null
                });
                renderCustomerTable(data.content);
                renderCustomerPagination(data);
            })
            .catch(error => {
                console.error('❌ 加载客户数据失败:', error);
                const tableBody = document.querySelector('#customerSelectTableBody');
                if (tableBody) {
                    tableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">
                        <i class="bi bi-exclamation-triangle-fill me-2"></i>加载客户数据失败: ${error.message}
                        <br><small class="text-muted">请检查网络连接或联系管理员</small>
                    </td></tr>`;
                }
            });
    }
    
    function renderCustomerTable(customers) {
        const tableBody = document.querySelector('#customerSelectTableBody');
        if (!tableBody) {
            console.error('❌ 找不到客户表格体元素');
            return;
        }
        
        if (!customers || customers.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">暂无客户数据</td></tr>';
            return;
        }
        
        console.log('📊 渲染客户表格，数据量:', customers.length);
        
        const rows = customers.map(customer => {
            // 兼容不同的数据字段名
            const id = customer.id || customer.customerId;
            const name = customer.customerName || '';
            const number = customer.customerNumber || customer.customerId || 'N/A';
            const phone = customer.phoneNumber || customer.contactPhone || '';
            const email = customer.email || customer.address || ''; // 从日志看address字段实际是邮箱
            const address = customer.realAddress || customer.contactPerson || customer.address || ''; // 真实地址字段
            
            return `
                <tr>
                    <td>${escapeHtml(number)}</td>
                    <td>${escapeHtml(name)}</td>
                    <td>${escapeHtml(phone)}</td>
                    <td>${escapeHtml(email)}</td>
                    <td class="text-center">
                        <button type="button" class="btn btn-primary btn-sm" 
                                onclick="selectCustomer(${id}, '${escapeHtml(name)}', '${escapeHtml(number)}', '${escapeHtml(phone)}', '${escapeHtml(email)}', '${escapeHtml(address)}')">
                            <i class="bi bi-check-circle me-1"></i>选择
                        </button>
                    </td>
                </tr>
            `;
        }).join('');
        
        tableBody.innerHTML = rows;
    }

    // 辅助函数：转义HTML
    function escapeHtml(text) {
        if (!text) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // 选择客户函数 - 全局函数，供HTML onclick调用
    window.selectCustomer = function(customerId, customerName, customerNumber, phoneNumber, email, address) {
        console.log('🔧 选择客户:', { customerId, customerName, customerNumber, phoneNumber, email, address });
        
        // 填充隐藏的客户ID字段
        const customerIdField = document.getElementById('selectedCustomerId');
        if (customerIdField) {
            customerIdField.value = customerId;
            console.log('✅ 已设置客户ID:', customerId);
        } else {
            console.error('❌ 找不到selectedCustomerId元素');
        }
        
        // 更新客户信息显示
        const placeholderDiv = document.getElementById('selectedCustomerInfoPlaceholder');
        if (placeholderDiv) {
            placeholderDiv.innerHTML = `
                <span class="text-success">
                    <i class="bi bi-check-circle-fill me-1"></i>
                    <strong>${customerName}</strong> (${customerNumber})
                </span>
            `;
            placeholderDiv.classList.remove('is-invalid-placeholder');
            console.log('✅ 已更新客户信息显示');
        } else {
            console.error('❌ 找不到selectedCustomerInfoPlaceholder元素');
        }
        
        // 更新详细信息卡片
        const detailsCard = document.getElementById('selectedCustomerDetailsCard');
        const nameText = document.getElementById('selectedCustomerNameText');
        const numberText = document.getElementById('selectedCustomerNumberText');
        const phoneText = document.getElementById('selectedCustomerPhoneText');
        const emailText = document.getElementById('selectedCustomerEmailText');
        const addressText = document.getElementById('selectedCustomerAddressText');
        
        if (nameText) {
            nameText.textContent = customerName || '';
            console.log('✅ 已设置客户名称:', customerName);
        } else {
            console.error('❌ 找不到selectedCustomerNameText元素');
        }
        
        if (numberText) {
            numberText.textContent = customerNumber || '';
            console.log('✅ 已设置客户编号:', customerNumber);
        } else {
            console.error('❌ 找不到selectedCustomerNumberText元素');
        }
        
        if (phoneText) {
            phoneText.textContent = phoneNumber || '';
            console.log('✅ 已设置客户电话:', phoneNumber);
        } else {
            console.error('❌ 找不到selectedCustomerPhoneText元素');
        }
        
        if (emailText) {
            emailText.textContent = email || '';
            console.log('✅ 已设置客户邮箱:', email);
        } else {
            console.error('❌ 找不到selectedCustomerEmailText元素');
        }
        
        if (addressText) {
            addressText.textContent = address || '';
            console.log('✅ 已设置客户地址:', address);
        } else {
            console.error('❌ 找不到selectedCustomerAddressText元素');
        }
        
        if (detailsCard) {
            detailsCard.style.display = 'block';
            console.log('✅ 已显示客户详细信息卡片');
        } else {
            console.error('❌ 找不到selectedCustomerDetailsCard元素');
        }
        
        // 清除验证错误
        const feedback = document.getElementById('selectedCustomerIdClientFeedback');
        if (feedback) {
            feedback.style.display = 'none';
            console.log('✅ 已清除验证错误');
        }
        
        // 关闭模态框
        const modal = document.getElementById('customerSelectModal');
        if (modal) {
            hideModal(modal);
        }
        
        console.log('✅ 客户选择完成');
    };
    
    function renderCustomerPagination(pageData) {
        const paginationContainer = document.querySelector('#customerSelectPagination');
        if (!paginationContainer) return;
        
        const currentPage = pageData.number;
        const totalPages = pageData.totalPages;
        
        if (totalPages <= 1) {
            paginationContainer.innerHTML = '';
            return;
        }
        
        let paginationHtml = '<nav><ul class="pagination pagination-sm justify-content-center">';
        
        // 上一页
        if (currentPage > 0) {
            paginationHtml += `<li class="page-item"><a class="page-link customer-page-btn" href="#" data-page="${currentPage - 1}">上一页</a></li>`;
        }
        
        // 页码
        for (let i = 0; i < totalPages; i++) {
            if (i === currentPage) {
                paginationHtml += `<li class="page-item active"><span class="page-link">${i + 1}</span></li>`;
            } else {
                paginationHtml += `<li class="page-item"><a class="page-link customer-page-btn" href="#" data-page="${i}">${i + 1}</a></li>`;
            }
        }
        
        // 下一页
        if (currentPage < totalPages - 1) {
            paginationHtml += `<li class="page-item"><a class="page-link customer-page-btn" href="#" data-page="${currentPage + 1}">下一页</a></li>`;
        }
        
        paginationHtml += '</ul></nav>';
        paginationContainer.innerHTML = paginationHtml;
    }

    // 延期模态框数据填充
    function populateAdminExtendModal(button, modal) {
        try {
            const contractId = button.getAttribute('data-contract-id');
            const contractNumber = button.getAttribute('data-contract-number');
            const currentEndDate = button.getAttribute('data-current-end-date');
            
            const contractIdField = modal.querySelector('#adminExtendContractId');
            const contractNumberField = modal.querySelector('#adminExtendContractNumber');
            const originalEndDateField = modal.querySelector('#adminOriginalEndDate');
            
            if (contractIdField) contractIdField.value = contractId || '';
            if (contractNumberField) contractNumberField.value = contractNumber || '';
            if (originalEndDateField) originalEndDateField.value = currentEndDate || '';
            
            // 清除表单验证状态
            modal.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
            modal.querySelectorAll('.invalid-feedback').forEach(el => el.textContent = '');
            
            console.log('✅ 管理员延期模态框数据填充完成');
        } catch (error) {
            console.error('❌ 填充管理员延期模态框数据失败:', error);
        }
    }
    
    function populateOperatorRequestExtendModal(button, modal) {
        try {
            const contractId = button.getAttribute('data-contract-id');
            const contractNumber = button.getAttribute('data-contract-number');
            const currentEndDate = button.getAttribute('data-current-end-date');
            
            const contractIdField = modal.querySelector('#operatorExtendContractId');
            const contractNumberField = modal.querySelector('#operatorExtendContractNumber');
            const originalEndDateField = modal.querySelector('#operatorOriginalEndDate');
            
            if (contractIdField) contractIdField.value = contractId || '';
            if (contractNumberField) contractNumberField.value = contractNumber || '';
            if (originalEndDateField) originalEndDateField.value = currentEndDate || '';
            
            // 清除表单验证状态
            modal.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
            modal.querySelectorAll('.invalid-feedback').forEach(el => el.textContent = '');
            
            console.log('✅ 操作员延期模态框数据填充完成');
        } catch (error) {
            console.error('❌ 填充操作员延期模态框数据失败:', error);
        }
    }

    // 核心模态框显示函数
    function showModal(modalEl) {
        if (!modalEl) {
            console.error('❌ showModal: 模态框元素为空');
            return;
        }
        
        console.log(`🎯 显示模态框: ${modalEl.id}`);
        
        // 隐藏所有其他模态框
        document.querySelectorAll('.modal.show').forEach(modal => {
            if (modal !== modalEl) {
                hideModal(modal);
            }
        });
        
        // 显示模态框
        modalEl.style.cssText = `
            display: block !important;
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            z-index: 9999 !important;
            background-color: rgba(0, 0, 0, 0.5) !important;
            overflow-y: auto !important;
            pointer-events: auto !important;
        `;
        
        modalEl.classList.add('show');
        document.body.classList.add('modal-open');
        
        // 设置模态框内容样式
        const modalDialog = modalEl.querySelector('.modal-dialog');
        const modalContent = modalEl.querySelector('.modal-content');
        
        if (modalDialog) {
            modalDialog.style.cssText = `
                position: relative !important;
                margin: 1.75rem auto !important;
                pointer-events: auto !important;
                z-index: 10000 !important;
            `;
        }
        
        if (modalContent) {
            modalContent.style.cssText = `
                position: relative !important;
                background-color: white !important;
                border-radius: 0.5rem !important;
                box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15) !important;
                pointer-events: auto !important;
                z-index: 10001 !important;
            `;
        }
        
        // 添加背景点击关闭功能
        modalEl.addEventListener('click', function(e) {
            if (e.target === modalEl) {
                hideModal(modalEl);
            }
        });
        
        // 添加ESC键关闭功能
        const escHandler = function(e) {
            if (e.key === 'Escape') {
                hideModal(modalEl);
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);
        
        console.log(`✅ 模态框显示完成: ${modalEl.id}`);
    }
    
    function hideModal(modalEl) {
        if (!modalEl) {
            console.error('❌ hideModal: 模态框元素为空');
            return;
        }
        
        console.log(`🎯 隐藏模态框: ${modalEl.id}`);
        
        modalEl.style.cssText = '';
        modalEl.classList.remove('show');
        
        // 检查是否还有其他模态框显示
        const remainingModals = document.querySelectorAll('.modal.show');
        if (remainingModals.length === 0) {
            document.body.classList.remove('modal-open');
        }
        
        console.log(`✅ 模态框隐藏完成: ${modalEl.id}`);
    }
    
    function forceModalVisibility(modalEl) {
        if (!modalEl) return;
        
        console.log(`🔧 强制模态框可见性: ${modalEl.id}`);
        
        modalEl.style.cssText = `
            display: block !important;
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            z-index: 9999 !important;
            background-color: rgba(0, 0, 0, 0.5) !important;
            overflow-y: auto !important;
            pointer-events: auto !important;
        `;
        
        const modalDialog = modalEl.querySelector('.modal-dialog');
        const modalContent = modalEl.querySelector('.modal-content');
        
        if (modalDialog) {
            modalDialog.style.cssText = `
                position: relative !important;
                margin: 1.75rem auto !important;
                pointer-events: auto !important;
                z-index: 10000 !important;
            `;
        }
        
        if (modalContent) {
            modalContent.style.cssText = `
                position: relative !important;
                background-color: white !important;
                border-radius: 0.5rem !important;
                box-shadow: 0 0.5rem 1rem rgba(0, 0, 0, 0.15) !important;
                pointer-events: auto !important;
                z-index: 10001 !important;
            `;
        }
        
        console.log(`✅ 模态框强制可见性设置完成: ${modalEl.id}`);
    }
    
    function interceptBootstrapModal() {
        // 拦截Bootstrap Modal的构造函数
        const checkBootstrap = () => {
            if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                console.log('🎯 拦截Bootstrap Modal构造函数');
                
                const originalModal = bootstrap.Modal;
                bootstrap.Modal = function(element, options) {
                    console.log('🔧 Bootstrap Modal被调用，使用通用管理器处理');
                    
                    // 不创建Bootstrap Modal实例，而是使用我们的管理器
                    const modalEl = typeof element === 'string' ? document.querySelector(element) : element;
                    if (modalEl) {
                        fixModal(modalEl);
                    }
                    
                    // 返回一个兼容的对象
                    return {
                        show: () => {
                            if (modalEl) showModal(modalEl);
                        },
                        hide: () => {
                            if (modalEl) hideModal(modalEl);
                        },
                        dispose: () => {
                            console.log('🔧 Modal dispose called');
                        }
                    };
                };
                
                // 保留原始构造函数的静态方法
                Object.setPrototypeOf(bootstrap.Modal, originalModal);
                Object.assign(bootstrap.Modal, originalModal);
            } else {
                setTimeout(checkBootstrap, 100);
            }
        };
        
        checkBootstrap();
    }

    // 暴露全局函数供其他脚本使用
    window.showModal = showModal;
    window.hideModal = hideModal;
    
    // 客户选择相关全局函数 - 这个函数被重复定义了，需要移除以避免冲突
    
    // 附件预览全局函数
    window.handlePreviewFile = handlePreviewFile;
    
    // 初始化
    init();
    
    // 拦截Bootstrap Modal
    interceptBootstrapModal();
    
})();

console.log("✅ 通用模态框管理器脚本已加载");

// 添加页面就绪后的元素检查
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', checkCustomerElements);
} else {
    checkCustomerElements();
}

function checkCustomerElements() {
    console.log('🔍 检查客户选择相关的DOM元素:');
    const elementsToCheck = [
        'selectedCustomerId',
        'selectedCustomerInfoPlaceholder', 
        'selectedCustomerDetailsCard',
        'selectedCustomerNameText',
        'selectedCustomerNumberText',
        'selectedCustomerPhoneText',
        'selectedCustomerEmailText',
        'selectedCustomerAddressText'
    ];
    
    elementsToCheck.forEach(id => {
        const element = document.getElementById(id);
        if (element) {
            console.log(`✅ 找到元素: ${id}`);
        } else {
            console.error(`❌ 找不到元素: ${id}`);
        }
    });
} 