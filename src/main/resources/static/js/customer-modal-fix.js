// 客户管理和附件模态框修复脚本
console.log("🚀 客户管理模态框修复启动");

(function() {
    'use strict';
    
    if (window.customerModalFix) {
        console.log("客户模态框修复已加载，跳过");
        return;
    }
    window.customerModalFix = true;
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 100);
        }
    }
    
    function setup() {
        console.log("🔧 设置客户管理模态框修复");
        
        // 查找并修复所有相关模态框
        const modalSelectors = [
            '#editCustomerModal',
            '#addCustomerModal',
            '#attachmentListModal',
            '#customerFormModal'
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
        
        // 修复表格中的操作按钮
        fixTableButtons();
        
        console.log("✅ 客户管理模态框修复完成");
    }
    
    function fixModal(modalEl) {
        const modalId = modalEl.id;
        
        // 移除Bootstrap属性
        modalEl.removeAttribute('data-bs-backdrop');
        modalEl.removeAttribute('data-bs-keyboard');
        modalEl.classList.remove('fade');
        
        // 修复关闭按钮
        const closeButtons = modalEl.querySelectorAll('.btn-close, [data-bs-dismiss="modal"], .btn-secondary');
        closeButtons.forEach(btn => {
            btn.removeAttribute('data-bs-dismiss');
            
            // 使用直接的关闭处理函数
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                console.log(`🔧 关闭模态框: ${modalId}`);
                hideModal(modalEl);
            });
        });
        
        // 点击模态框外部区域关闭
        modalEl.addEventListener('click', function(e) {
            if (e.target === modalEl) {
                console.log(`🔧 点击外部区域，关闭模态框: ${modalId}`);
                hideModal(modalEl);
            }
        });
    }
    
    function fixTriggerButtons() {
        // 修复添加客户按钮
        const addCustomerBtns = document.querySelectorAll('button[data-bs-target="#customerFormModal"], button[data-bs-target="#addCustomerModal"]');
        const customerFormModal = document.getElementById('customerFormModal');
        const addCustomerModal = document.getElementById('addCustomerModal');
        
        addCustomerBtns.forEach(btn => {
            btn.removeAttribute('data-bs-toggle');
            btn.removeAttribute('data-bs-target');
            btn.onclick = null;
            
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                console.log('🎯 点击添加客户按钮');
                const targetModal = customerFormModal || addCustomerModal;
                if (targetModal) {
                    showModal(targetModal);
                }
            });
        });
    }
    
    function fixTableButtons() {
        // 处理客户表格中的编辑按钮
        document.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            
            const classList = btn.classList;
            console.log(`🎯 按钮点击:`, classList.toString(), btn.getAttribute('data-bs-target'));
            
            // 编辑客户按钮
            if (btn.hasAttribute('data-bs-target') && btn.getAttribute('data-bs-target') === '#editCustomerModal') {
                e.preventDefault();
                e.stopPropagation();
                console.log('🔧 编辑客户按钮点击，处理中...');
                
                // 获取客户数据
                const customerId = btn.getAttribute('data-id');
                const customerNumber = btn.getAttribute('data-number');
                const customerName = btn.getAttribute('data-name');
                const phoneNumber = btn.getAttribute('data-phone');
                const email = btn.getAttribute('data-email');
                const address = btn.getAttribute('data-address') || '';
                
                // 填充数据
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
                    inputs.id.value = customerId;
                    inputs.number.value = customerNumber;
                    inputs.name.value = customerName;
                    inputs.phone.value = phoneNumber;
                    inputs.email.value = email;
                    inputs.address.value = address;
                    
                    // 清除验证状态
                    const editForm = document.getElementById('editCustomerForm');
                    if (editForm) {
                        editForm.classList.remove('was-validated');
                        editForm.querySelectorAll('.is-invalid').forEach(el => el.classList.remove('is-invalid'));
                    }
                    
                    // 显示模态框
                    const modal = document.getElementById('editCustomerModal');
                    if (modal) {
                        console.log('🔧 显示编辑客户模态框');
                        showModal(modal);
                    }
                }
            }
            
            // 附件模态框按钮
            if (btn.hasAttribute('data-bs-target') && btn.getAttribute('data-bs-target') === '#attachmentListModal') {
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
        }, true); // 使用捕获阶段，确保优先处理
    }
    
    // 附件数据加载函数
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
        
        // 文件预览处理函数
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
    
    function showModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 显示模态框: ${modalId}`);
        
        if (window.UnifiedModalManager) {
            window.UnifiedModalManager.showModal(modalId);
            return;
        }
        
        // 如果统一管理器不可用，使用备用方案
        modalEl.style.display = 'block';
        modalEl.classList.add('show');
        modalEl.setAttribute('aria-modal', 'true');
        modalEl.setAttribute('role', 'dialog');
        
        // 设置背景
        modalEl.style.position = 'fixed';
        modalEl.style.top = '0';
        modalEl.style.left = '0';
        modalEl.style.width = '100%';
        modalEl.style.height = '100%';
        modalEl.style.zIndex = '1055';
        modalEl.style.backgroundColor = 'rgba(0,0,0,0.5)';
        modalEl.style.overflowY = 'auto';
        
        // 移除body的modal-open类和样式，允许页面滚动
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
        
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
        
        // 为第一个输入框设置焦点
        if (modalId === 'editCustomerModal' || modalId === 'customerFormModal') {
            setTimeout(() => {
                const firstInput = modalEl.querySelector('input:not([type="hidden"])');
                if (firstInput) {
                    firstInput.focus();
                }
            }, 100);
        }
        
        console.log(`✅ 模态框显示成功: ${modalId}`);
    }
    
    function hideModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 隐藏模态框: ${modalId}`);
        
        // 直接处理模态框关闭
        modalEl.style.display = 'none';
        modalEl.classList.remove('show');
        modalEl.setAttribute('aria-hidden', 'true');
        modalEl.removeAttribute('aria-modal');
        modalEl.removeAttribute('role');
        
        // 移除背景遮罩
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
        
        // 重置表单（如果存在）
        const form = modalEl.querySelector('form');
        if (form) {
            form.reset();
            form.classList.remove('was-validated');
        }
        
        // 移除所有可能的模态框状态类
        document.documentElement.classList.remove('modal-open');
        
        console.log(`✅ 模态框隐藏完成: ${modalId}`);
    }
    
    // 拦截Bootstrap Modal调用
    function interceptBootstrapModal() {
        const checkBootstrap = () => {
            if (window.bootstrap && window.bootstrap.Modal) {
                console.log("🔧 拦截Bootstrap Modal调用");
                
                const OriginalModal = window.bootstrap.Modal;
                
                window.bootstrap.Modal = function(element, options = {}) {
                    const el = typeof element === 'string' ? document.getElementById(element) : element;
                    console.log("🎯 拦截Modal构造:", el?.id || element);
                    
                    // 只拦截我们关心的模态框
                    if (el && (el.id === 'editCustomerModal' || el.id === 'addCustomerModal' || el.id === 'attachmentListModal')) {
                        return {
                            _element: el,
                            show: function() {
                                console.log("🔧 拦截Modal.show()调用");
                                showModal(el);
                            },
                            hide: function() {
                                console.log("🔧 拦截Modal.hide()调用");
                                hideModal(el);
                            }
                        };
                    } else {
                        // 对于其他模态框，使用原始Bootstrap行为
                        return new OriginalModal(element, options);
                    }
                };
                
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
    
    // 暴露到全局
    window.showCustomerModal = showModal;
    window.hideCustomerModal = hideModal;
    
    // 启动修复
    init();
    
    // 拦截Bootstrap Modal
    interceptBootstrapModal();
    
})();

console.log("📋 客户管理模态框修复脚本已加载"); 