// 通用模态框管理器 - 所有模态框显示和隐藏的唯一管理者
console.log("🚀 通用模态框管理器启动 v2.0");

(function() {
    'use strict';
    
    if (window.universalModalManager) {
        console.log("通用模态框管理器已加载，跳过");
        return;
    }
    window.universalModalManager = true;
    
    // Bootstrap Modal 实例存储
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
        console.log("🔧 设置通用模态框管理器 v2.0");
        
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
        
        console.log("✅ 通用模态框管理器修复完成 v2.0");
    }

    // 获取或创建Bootstrap Modal实例
    function getBootstrapModalInstance(modalEl) {
        if (!modalEl || !modalEl.id) {
            console.error('❌ 模态框元素无效或缺少ID');
            return null;
        }
        
        const modalId = modalEl.id;
        
        // 从缓存中获取实例
        if (modalInstances.has(modalId)) {
            return modalInstances.get(modalId);
        }
        
        // 创建新的Bootstrap Modal实例
        try {
            const modalInstance = new bootstrap.Modal(modalEl, {
                backdrop: 'static',
                keyboard: false,
                focus: true
            });
            
            modalInstances.set(modalId, modalInstance);
            console.log(`✅ 创建Bootstrap Modal实例: ${modalId}`);
            return modalInstance;
        } catch (error) {
            console.error(`❌ 创建Bootstrap Modal实例失败: ${modalId}`, error);
            return null;
        }
    }
    
    function fixModal(modalEl) {
        const modalId = modalEl.id;
        
        // 移除Bootstrap属性，但保留基本结构
        modalEl.removeAttribute('data-bs-backdrop');
        modalEl.removeAttribute('data-bs-keyboard');
        
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
        const url = `/baseData/customers/search?page=${page}&size=${window.customerSelectGlobals.CUSTOMER_PAGE_SIZE}&keyword=${encodeURIComponent(keyword || '')}`;
        
        fetch(url)
            .then(response => response.json())
            .then(data => {
                console.log('✅ 客户数据加载成功:', data);
                renderCustomerTable(data.content);
                renderCustomerPagination(data);
            })
            .catch(error => {
                console.error('❌ 加载客户数据失败:', error);
                const tableBody = document.querySelector('#customerSelectTableBody');
                if (tableBody) {
                    tableBody.innerHTML = '<tr><td colspan="4" class="text-center text-danger">加载客户数据失败</td></tr>';
                }
            });
    }
    
    function renderCustomerTable(customers) {
        const tableBody = document.querySelector('#customerSelectTableBody');
        if (!tableBody) return;
        
        if (!customers || customers.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted">暂无客户数据</td></tr>';
            return;
        }
        
        const rows = customers.map(customer => `
            <tr>
                <td>${customer.customerName || ''}</td>
                <td>${customer.contactPerson || ''}</td>
                <td>${customer.contactPhone || ''}</td>
                <td>
                    <button type="button" class="btn btn-primary btn-sm" 
                            onclick="selectCustomer(${customer.customerId}, '${customer.customerName}', '${customer.contactPerson}', '${customer.contactPhone}', '${customer.address || ''}')">
                        选择
                    </button>
                </td>
            </tr>
        `).join('');
        
        tableBody.innerHTML = rows;
    }
    
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

    // 核心模态框显示函数 - 使用Bootstrap原生API + 强制CSS覆盖
    function showModal(modalEl) {
        if (!modalEl) {
            console.error('❌ showModal: 模态框元素为空');
            return;
        }
        
        console.log(`🎯 显示模态框: ${modalEl.id}`);
        
        // 获取Bootstrap Modal实例
        const bsModal = getBootstrapModalInstance(modalEl);
        if (!bsModal) {
            console.error(`❌ 无法获取Bootstrap Modal实例: ${modalEl.id}`);
            return;
        }
        
        // 使用Bootstrap原生show方法
        bsModal.show();
        
        // 强制CSS覆盖解决z-index和pointer-events冲突
        setTimeout(() => {
            forceModalVisibility(modalEl);
        }, 50);
    }
    
    // 核心模态框隐藏函数 - 使用Bootstrap原生API
    function hideModal(modalEl) {
        if (!modalEl) {
            console.error('❌ hideModal: 模态框元素为空');
            return;
        }
        
        console.log(`🎯 隐藏模态框: ${modalEl.id}`);
        
        // 获取Bootstrap Modal实例
        const bsModal = getBootstrapModalInstance(modalEl);
        if (!bsModal) {
            console.error(`❌ 无法获取Bootstrap Modal实例: ${modalEl.id}`);
            return;
        }
        
        // 使用Bootstrap原生hide方法
        bsModal.hide();
        
        // 清理强制样式
        setTimeout(() => {
            cleanupModalStyles(modalEl);
        }, 50);
    }
    
    // 强制模态框可见性 - 处理z-index和pointer-events冲突
    function forceModalVisibility(modalEl) {
        if (!modalEl) return;
        
        console.log(`🔧 强制模态框可见性: ${modalEl.id}`);
        
        // 强制模态框本身的样式
        modalEl.style.setProperty('z-index', '9999', 'important');
        modalEl.style.setProperty('pointer-events', 'auto', 'important');
        modalEl.style.setProperty('display', 'block', 'important');
        
        // 强制模态框内容的样式
        const modalDialog = modalEl.querySelector('.modal-dialog');
        if (modalDialog) {
            modalDialog.style.setProperty('z-index', '10000', 'important');
            modalDialog.style.setProperty('pointer-events', 'auto', 'important');
        }
        
        const modalContent = modalEl.querySelector('.modal-content');
        if (modalContent) {
            modalContent.style.setProperty('z-index', '10001', 'important');
            modalContent.style.setProperty('pointer-events', 'auto', 'important');
        }
        
        // 调整backdrop的z-index
        const backdrop = document.querySelector('.modal-backdrop');
        if (backdrop) {
            backdrop.style.setProperty('z-index', '9998', 'important');
        }
        
        console.log(`✅ 模态框强制可见性设置完成: ${modalEl.id}`);
    }
    
    // 清理模态框强制样式
    function cleanupModalStyles(modalEl) {
        if (!modalEl) return;
        
        console.log(`🧹 清理模态框样式: ${modalEl.id}`);
        
        // 移除强制样式
        modalEl.style.removeProperty('z-index');
        modalEl.style.removeProperty('pointer-events');
        modalEl.style.removeProperty('display');
        
        const modalDialog = modalEl.querySelector('.modal-dialog');
        if (modalDialog) {
            modalDialog.style.removeProperty('z-index');
            modalDialog.style.removeProperty('pointer-events');
        }
        
        const modalContent = modalEl.querySelector('.modal-content');
        if (modalContent) {
            modalContent.style.removeProperty('z-index');
            modalContent.style.removeProperty('pointer-events');
        }
    }

    // 暴露全局函数供其他脚本使用
    window.showModal = showModal;
    window.hideModal = hideModal;
    
    // 客户选择相关全局函数
    window.selectCustomer = function(customerId, customerName, contactPerson, contactPhone, address) {
        console.log('🔧 选择客户:', { customerId, customerName, contactPerson, contactPhone, address });
        
        // 查找各种可能的字段结构
        const fields = [
            // draft-contract.html 结构
            { id: 'customerId', value: customerId },
            { id: 'customerName', value: customerName },
            { id: 'contactPerson', value: contactPerson },
            { id: 'contactPhone', value: contactPhone },
            { id: 'address', value: address },
            
            // 其他页面可能的结构
            { id: 'contractCustomerId', value: customerId },
            { id: 'contractCustomerName', value: customerName },
            { id: 'selectedCustomerName', value: customerName }
        ];
        
        // 填充字段数据
        fields.forEach(({ id, value }) => {
            const field = document.getElementById(id);
            if (field && value !== null && value !== undefined) {
                field.value = value;
                
                // 清除验证状态
                field.classList.remove('is-invalid');
                const feedback = field.parentElement.querySelector('.invalid-feedback');
                if (feedback) feedback.textContent = '';
                
                // 触发change事件
                const changeEvent = new Event('change', { bubbles: true });
                field.dispatchEvent(changeEvent);
                
                console.log(`✅ 填充字段 ${id}: ${value}`);
            }
        });
        
        // 关闭客户选择模态框
        const customerSelectModal = document.getElementById('customerSelectModal');
        if (customerSelectModal) {
            hideModal(customerSelectModal);
        }
        
        console.log('✅ 客户选择完成');
    };
    
    // 附件预览全局函数
    window.handlePreviewFile = handlePreviewFile;
    
    // 初始化
    init();
    
})();

console.log("✅ 通用模态框管理器脚本已加载"); 