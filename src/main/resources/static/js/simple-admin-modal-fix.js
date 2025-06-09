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
        // 使用事件委托处理表格按钮，定期重新绑定以处理动态内容
        const tables = [
            'usersTable',
            'rolesTable', 
            'functionalitiesTable',
            'templatesTable',
            'customersTable'
        ];
        
        // 为整个文档添加事件委托，捕获所有按钮点击
        document.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            
            const classList = btn.classList;
            console.log(`🎯 检测到按钮点击:`, classList.toString());
            
            // 编辑按钮 - 阻止默认行为，先触发数据加载，再显示模态框
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
                            // 如果模态框没有显示，手动显示
                            if (modal.style.display !== 'block') {
                                console.log(`🔧 手动显示模态框: ${targetModalId}`);
                                showModal(modal);
                            }
                        }
                    }
                }, 200);
            }
            // 附件按钮特殊处理
            else if (btn.hasAttribute('data-bs-target') && btn.getAttribute('data-bs-target') === '#attachmentListModal') {
                e.preventDefault();
                e.stopPropagation();
                console.log('🔧 附件按钮点击，处理中...');
                
                // 获取附件数据
                const attachmentsJson = btn.getAttribute('data-attachments');
                const contractName = btn.getAttribute('data-contract-name');
                
                setTimeout(() => {
                    const modal = document.getElementById('attachmentListModal');
                    if (modal) {
                        console.log('🔧 显示附件模态框，合同:', contractName);
                        
                        // 手动触发附件数据加载
                        loadAttachmentData(modal, btn, attachmentsJson, contractName);
                        
                        // 显示模态框
                        showModal(modal);
                    }
                }, 100);
            }
            // 延期按钮特殊处理
            else if (btn.hasAttribute('data-bs-target')) {
                const target = btn.getAttribute('data-bs-target');
                if (target === '#adminExtendModal' || target === '#operatorRequestExtendModal') {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`🔧 延期按钮点击: ${target}`);
                    
                    const modal = document.querySelector(target);
                    if (modal) {
                        // 获取数据并填充模态框
                        if (target === '#adminExtendModal') {
                            populateAdminExtendModal(btn, modal);
                        } else if (target === '#operatorRequestExtendModal') {
                            populateOperatorRequestExtendModal(btn, modal);
                        }
                        
                        setTimeout(() => {
                            showModal(modal);
                        }, 100);
                    }
                }
            }
            // 删除按钮保持原有行为
            else if (classList.contains('delete-user-btn') || 
                    classList.contains('delete-role-btn') || 
                    classList.contains('delete-func-btn') ||
                    classList.contains('delete-template-btn') ||
                    classList.contains('delete-customer-btn')) {
                console.log('🗑️ 删除按钮点击，保持原有行为');
                // 不阻止默认行为，让原有的删除逻辑执行
            }
        }, true); // 使用捕获阶段，确保优先处理
    }
    
    // ==================== 附件模态框逻辑 ====================
    
    function loadAttachmentData(modal, button, attachmentsJson, contractName) {
        console.log('🔧 开始加载附件数据:', contractName, attachmentsJson);
        
        const attachmentListContainer = modal.querySelector('#attachmentListContainer');
        const modalContractName = modal.querySelector('#modalContractName');
        
        if (!attachmentListContainer || !modalContractName) {
            console.error('找不到附件容器元素');
            return;
        }
        
        modalContractName.textContent = contractName || '未知合同';
        attachmentListContainer.innerHTML = '<div class="text-center"><div class="spinner-border spinner-border-sm"></div> 正在加载附件...</div>';
        
        // 添加文件到UI的函数
        function addExistingFileToUI(serverFileName) {
            const uniqueId = `file-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
            const fileItemHTML = `
                <div class="file-list-item" id="${uniqueId}">
                    <div class="file-info">
                        <span class="file-name" title="${serverFileName}">${serverFileName}</span>
                    </div>
                    <div class="file-actions">
                        <button type="button" class="btn btn-sm btn-outline-info preview-btn" 
                                title="预览文件" 
                                data-filename="${serverFileName}">
                            <i class="bi bi-eye"></i> 预览
                        </button>
                    </div>
                </div>`;
            attachmentListContainer.insertAdjacentHTML('beforeend', fileItemHTML);
            
            // 为预览按钮添加事件监听器
            const newElement = document.getElementById(uniqueId);
            if (newElement) {
                const previewBtn = newElement.querySelector('.preview-btn');
                if (previewBtn) {
                    previewBtn.addEventListener('click', function() {
                        const fileName = this.getAttribute('data-filename');
                        console.log('🔧 点击预览按钮:', fileName);
                        handlePreviewFile(fileName);
                    });
                }
            }
        }
        
        // 处理附件JSON数据
        try {
            if (attachmentsJson && attachmentsJson !== '[]') {
                const attachmentFiles = JSON.parse(attachmentsJson);
                attachmentListContainer.innerHTML = '';
                
                if (Array.isArray(attachmentFiles) && attachmentFiles.length > 0) {
                    console.log(`🔧 加载 ${attachmentFiles.length} 个附件文件`);
                    attachmentFiles.forEach((fileName) => {
                        console.log(`🔧 添加附件: ${fileName}`);
                        addExistingFileToUI(fileName);
                    });
                } else {
                    attachmentListContainer.innerHTML = '<p class="text-muted small">无附件。</p>';
                }
            } else {
                attachmentListContainer.innerHTML = '<p class="text-muted small">无附件。</p>';
            }
        } catch (e) {
            console.error('解析附件JSON失败:', e);
            attachmentListContainer.innerHTML = '<p class="text-danger small">加载附件列表时出错。</p>';
        }
    }
    
    function handlePreviewFile(serverFileName) {
        if (!serverFileName) {
            console.error('预览失败：文件名为空');
            return;
        }
        
        console.log('🔧 开始预览文件:', serverFileName);
        const isPreviewable = /\.(pdf|jpe?g|png|gif|bmp|txt)$/i.test(serverFileName);
        const downloadUrl = `/api/attachments/download/${encodeURIComponent(serverFileName)}`;
        
        if (isPreviewable) {
            window.open(downloadUrl, '_blank');
        } else {
            const userAgreedToDownload = confirm(
                `文件 "${serverFileName}" 可能不支持在浏览器中直接预览。\n\n点击"确定"将尝试下载该文件。`
            );
            if (userAgreedToDownload) {
                const link = document.createElement('a');
                link.href = downloadUrl;
                link.setAttribute('download', serverFileName);
                document.body.appendChild(link);
                link.click();
                document.body.removeChild(link);
            }
        }
    }
    
    // ==================== 客户选择模态框逻辑 ====================
    
    function fixCustomerSelection() {
        const customerSelectModal = document.getElementById('customerSelectModal');
        if (!customerSelectModal) {
            console.log("未找到客户选择模态框，跳过初始化");
            return;
        }
        
        console.log("🔧 初始化客户选择功能");
        
        // 绑定搜索功能
        const searchInput = document.getElementById('modalCustomerSearchInput');
        const searchButton = document.getElementById('modalSearchCustomerBtn');
        
        if (searchButton && searchInput) {
            searchButton.addEventListener('click', function() {
                const keyword = searchInput.value.trim();
                console.log("🔍 执行客户搜索:", keyword);
                searchCustomers(keyword);
            });

            searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    const keyword = this.value.trim();
                    console.log("🔍 回车搜索客户:", keyword);
                    searchCustomers(keyword);
                }
            });
        }
    }
    
    function openCustomerSelectModal() {
        console.log("🔧 打开客户选择模态框");
        
        const modal = document.getElementById('customerSelectModal');
        const searchInput = document.getElementById('modalCustomerSearchInput');
        
        if (!modal) {
            console.error("找不到客户选择模态框");
            return;
        }
        
        // 清空搜索框
        if (searchInput) {
            searchInput.value = '';
        }
        window.customerSelectGlobals.customerSearchKeyword = '';
        
        // 加载客户数据
        loadCustomerData(0, '');
        
        // 显示模态框
        showModal(modal);
    }
    
    function searchCustomers(keyword) {
        window.customerSelectGlobals.customerSearchKeyword = keyword;
        loadCustomerData(0, keyword);
    }
    
    function loadCustomerData(page, keyword) {
        console.log(`🔧 加载客户数据 - 页码: ${page}, 关键词: "${keyword}"`);
        
        window.customerSelectGlobals.currentCustomerPage = page;
        const searchUrl = `/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${window.customerSelectGlobals.CUSTOMER_PAGE_SIZE}&sort=customerName,asc`;
        
        const tableBody = document.querySelector('#customerTableModal tbody');
        const pagination = document.getElementById('customerModalPagination');
        const spinner = document.getElementById('customerModalSpinner');
        const alertPlaceholder = document.getElementById('customerModalAlertPlaceholder');
        
        // 显示加载状态
        if (spinner) spinner.style.display = 'block';
        if (tableBody) tableBody.innerHTML = '';
        if (pagination) pagination.innerHTML = '';
        
        fetch(searchUrl, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json',
                'X-Requested-With': 'XMLHttpRequest'
            },
            credentials: 'same-origin'
        })
        .then(response => {
            console.log(`📡 API响应状态: ${response.status}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(pageData => {
            console.log("✅ 客户数据加载成功:", pageData);
            renderCustomerTable(pageData.content || []);
            renderCustomerPagination(pageData);
            if (alertPlaceholder) alertPlaceholder.innerHTML = '';
        })
        .catch(error => {
            console.error('❌ 加载客户数据失败:', error);
            if (alertPlaceholder) {
                alertPlaceholder.innerHTML = `<div class="alert alert-danger">加载客户数据失败: ${error.message}</div>`;
            }
            renderCustomerTable([]);
            renderCustomerPagination(null);
        })
        .finally(() => {
            if (spinner) spinner.style.display = 'none';
        });
    }
    
    function renderCustomerTable(customers) {
        const tableBody = document.querySelector('#customerTableModal tbody');
        if (!tableBody) return;
        
        if (customers.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">没有找到客户数据</td></tr>';
            return;
        }
        
        const rows = customers.map(customer => `
            <tr>
                <td>${customer.customerName || ''}</td>
                <td>${customer.legalRepresentative || ''}</td>
                <td>${customer.address || ''}</td>
                <td>${customer.contactInformation || ''}</td>
                <td>
                    <button type="button" class="btn btn-primary btn-sm" 
                            onclick="selectCustomer(${customer.id}, '${(customer.customerName || '').replace(/'/g, "\\'")}')">
                        选择
                    </button>
                </td>
            </tr>
        `).join('');
        
        tableBody.innerHTML = rows;
    }
    
    function renderCustomerPagination(pageData) {
        const pagination = document.getElementById('customerModalPagination');
        if (!pagination || !pageData) return;
        
        const currentPage = pageData.number || 0;
        const totalPages = pageData.totalPages || 0;
        
        if (totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }
        
        let paginationHTML = '<nav><ul class="pagination pagination-sm justify-content-center">';
        
        // 上一页
        if (currentPage > 0) {
            paginationHTML += `<li class="page-item">
                <a class="page-link" href="#" onclick="loadCustomerData(${currentPage - 1}, '${window.customerSelectGlobals.customerSearchKeyword}')">上一页</a>
            </li>`;
        }
        
        // 页码
        for (let i = 0; i < totalPages; i++) {
            const activeClass = i === currentPage ? 'active' : '';
            paginationHTML += `<li class="page-item ${activeClass}">
                <a class="page-link" href="#" onclick="loadCustomerData(${i}, '${window.customerSelectGlobals.customerSearchKeyword}')">${i + 1}</a>
            </li>`;
        }
        
        // 下一页
        if (currentPage < totalPages - 1) {
            paginationHTML += `<li class="page-item">
                <a class="page-link" href="#" onclick="loadCustomerData(${currentPage + 1}, '${window.customerSelectGlobals.customerSearchKeyword}')">下一页</a>
            </li>`;
        }
        
        paginationHTML += '</ul></nav>';
        pagination.innerHTML = paginationHTML;
    }
    
    // ==================== 延期模态框逻辑 ====================
    
    function populateAdminExtendModal(button, modal) {
        console.log("🔧 填充管理员延期模态框数据");
        
        const contractId = button.getAttribute('data-contract-id');
        const contractNumber = button.getAttribute('data-contract-number');
        const contractName = button.getAttribute('data-contract-name');
        const currentEndDate = button.getAttribute('data-current-end-date');
        
        // 填充数据到模态框
        const contractIdInput = modal.querySelector('input[name="contractId"]');
        const contractNumberSpan = modal.querySelector('#adminExtendContractNumber');
        const contractNameSpan = modal.querySelector('#adminExtendContractName');
        const currentEndDateSpan = modal.querySelector('#adminExtendCurrentEndDate');
        const newEndDateInput = modal.querySelector('input[name="newEndDate"]');
        const reasonTextarea = modal.querySelector('textarea[name="reason"]');
        
        if (contractIdInput) contractIdInput.value = contractId || '';
        if (contractNumberSpan) contractNumberSpan.textContent = contractNumber || '';
        if (contractNameSpan) contractNameSpan.textContent = contractName || '';
        if (currentEndDateSpan) currentEndDateSpan.textContent = currentEndDate || '';
        if (newEndDateInput) newEndDateInput.value = '';
        if (reasonTextarea) reasonTextarea.value = '';
    }
    
    function populateOperatorRequestExtendModal(button, modal) {
        console.log("🔧 填充操作员延期请求模态框数据");
        
        const contractId = button.getAttribute('data-contract-id');
        const contractNumber = button.getAttribute('data-contract-number');
        const contractName = button.getAttribute('data-contract-name');
        const currentEndDate = button.getAttribute('data-current-end-date');
        
        // 填充数据到模态框
        const contractIdInput = modal.querySelector('input[name="contractId"]');
        const contractNumberSpan = modal.querySelector('#operatorExtendContractNumber');
        const contractNameSpan = modal.querySelector('#operatorExtendContractName');
        const currentEndDateSpan = modal.querySelector('#operatorExtendCurrentEndDate');
        const requestedEndDateInput = modal.querySelector('input[name="requestedEndDate"]');
        const requestReasonTextarea = modal.querySelector('textarea[name="requestReason"]');
        
        if (contractIdInput) contractIdInput.value = contractId || '';
        if (contractNumberSpan) contractNumberSpan.textContent = contractNumber || '';
        if (contractNameSpan) contractNameSpan.textContent = contractName || '';
        if (currentEndDateSpan) currentEndDateSpan.textContent = currentEndDate || '';
        if (requestedEndDateInput) requestedEndDateInput.value = '';
        if (requestReasonTextarea) requestReasonTextarea.value = '';
    }
    
    // ==================== 核心模态框显示/隐藏逻辑 ====================
    
    function showModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 显示模态框: ${modalId}`);
        
        // 显示模态框
        modalEl.style.display = 'block';
        modalEl.classList.add('show');
        modalEl.style.paddingRight = '17px'; // 滚动条补偿
        
        // 设置高优先级样式
        modalEl.style.position = 'fixed';
        modalEl.style.top = '0';
        modalEl.style.left = '0';
        modalEl.style.width = '100%';
        modalEl.style.height = '100%';
        modalEl.style.zIndex = '1055';
        modalEl.style.backgroundColor = 'rgba(0,0,0,0.5)';
        modalEl.style.overflowY = 'auto';
        modalEl.style.pointerEvents = 'auto';
        
        // 确保模态框内容的交互性
        const modalDialog = modalEl.querySelector('.modal-dialog');
        const modalContent = modalEl.querySelector('.modal-content');
        
        if (modalDialog) {
            modalDialog.style.zIndex = '1056';
            modalDialog.style.pointerEvents = 'auto';
        }
        
        if (modalContent) {
            modalContent.style.zIndex = '1057';
            modalContent.style.pointerEvents = 'auto';
            
            // 为模态框内容添加点击事件，防止冒泡
            modalContent.onclick = function(e) {
                e.stopPropagation();
            };
        }
        
        // 设置body样式，但保持页面可滚动
        document.body.classList.add('modal-open');
        // 不设置 overflow: hidden，保持页面可滚动
        
        // 背景点击关闭
        modalEl.addEventListener('click', function(e) {
            if (e.target === modalEl) {
                hideModal(modalEl);
            }
        });
        
        // ESC键关闭
        const escHandler = function(e) {
            if (e.key === 'Escape') {
                hideModal(modalEl);
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);
        modalEl._escHandler = escHandler;
        
        console.log(`✅ 模态框显示成功: ${modalId}`);
    }
    
    function hideModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 隐藏模态框: ${modalId}`);
        
        modalEl.style.display = 'none';
        modalEl.classList.remove('show');
        modalEl.style.paddingRight = '';
        
        document.body.classList.remove('modal-open');
        // 恢复body滚动
        document.body.style.overflow = '';
        
        // 移除ESC监听器
        if (modalEl._escHandler) {
            document.removeEventListener('keydown', modalEl._escHandler);
            delete modalEl._escHandler;
        }
        
        console.log(`✅ 模态框隐藏成功: ${modalId}`);
    }
    
    // ==================== Bootstrap Modal 拦截 ====================
    
    function interceptBootstrapModal() {
        // 等待Bootstrap加载
        const checkBootstrap = () => {
            if (window.bootstrap && window.bootstrap.Modal) {
                console.log("🔧 拦截Bootstrap Modal调用");
                
                // 保存原始构造函数
                const OriginalModal = window.bootstrap.Modal;
                
                // 创建拦截构造函数
                window.bootstrap.Modal = function(element, options = {}) {
                    const el = typeof element === 'string' ? document.getElementById(element) : element;
                    const elId = el?.id || '';
                    
                    console.log("🎯 拦截Modal构造:", elId);
                    
                    // 返回通用代理对象
                    return {
                        _element: el,
                        show: function() {
                            console.log("🔧 拦截Modal.show()调用");
                            if (this._element) {
                                showModal(this._element);
                            }
                        },
                        hide: function() {
                            console.log("🔧 拦截Modal.hide()调用");
                            if (this._element) {
                                hideModal(this._element);
                            }
                        }
                    };
                };
                
                // 保持静态方法
                Object.keys(OriginalModal).forEach(key => {
                    window.bootstrap.Modal[key] = OriginalModal[key];
                });
                
                console.log("✅ Bootstrap Modal拦截完成");
            } else {
                setTimeout(checkBootstrap, 100);
            }
        };
        
        checkBootstrap();
    }
    
    // ==================== 全局函数暴露 ====================
    
    // 暴露到全局，供原有代码调用
    window.showModal = showModal;
    window.hideModal = hideModal;
    window.loadCustomerData = loadCustomerData;
    window.selectCustomer = function(customerId, customerName) {
        console.log(`🔧 选择客户: ${customerId} - ${customerName}`);
        
        // 填充表单
        const customerIdInput = document.getElementById('selectedCustomerId');
        const customerNameInput = document.getElementById('customerName');
        const infoPlaceholder = document.getElementById('selectedCustomerInfoPlaceholder');
        
        if (customerIdInput) customerIdInput.value = customerId;
        if (customerNameInput) customerNameInput.value = customerName;
        if (infoPlaceholder) {
            infoPlaceholder.innerHTML = `<div class="alert alert-success">已选择客户: <strong>${customerName}</strong></div>`;
        }
        
        // 关闭模态框
        const modal = document.getElementById('customerSelectModal');
        if (modal) {
            hideModal(modal);
        }
    };
    
    // 启动管理器
    init();
    
    // 拦截Bootstrap Modal
    interceptBootstrapModal();
    
})();

console.log("�� 通用模态框管理器脚本已加载"); 