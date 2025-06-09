// 简化版管理模块模态框修复脚本
console.log("🚀 简化版管理模块模态框修复启动");

(function() {
    'use strict';
    
    if (window.simpleAdminModalFix) {
        console.log("简化版修复已加载，跳过");
        return;
    }
    window.simpleAdminModalFix = true;
    
    // 模态框实例存储
    const modalInstances = new Map();
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 100);
        }
    }
    
    function setup() {
        console.log("🔧 设置简化版管理模块模态框修复");
        
        // 查找并修复所有管理模态框
        const modalSelectors = [
            '#userFormModal',
            '#roleFormModal', 
            '#functionalityFormModal',
            '#assignRolesModal',
            '#templateFormModal'
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
        
        console.log("✅ 简化版管理模块模态框修复完成");
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
            { id: 'addTemplateBtn', modalId: 'templateFormModal' }
        ];
        
        triggerButtons.forEach(({ id, modalId }) => {
            const btn = document.getElementById(id);
            const modalEl = document.getElementById(modalId);
            
            if (btn && modalEl) {
                btn.removeAttribute('data-bs-toggle');
                btn.removeAttribute('data-bs-target');
                btn.onclick = null;
                
                btn.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`🎯 点击添加按钮: ${id}`);
                    showModal(modalEl);
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
            'templatesTable'
        ];
        
        tables.forEach(tableId => {
            const table = document.getElementById(tableId);
            if (!table) return;
            
            // 使用事件委托处理表格按钮点击
            table.addEventListener('click', function(e) {
                const btn = e.target.closest('button');
                if (!btn) return;
                
                const classList = btn.classList;
                console.log(`🎯 表格按钮点击:`, classList.toString());
                
                // 编辑按钮 - 阻止默认行为，先触发数据加载，再显示模态框
                if (classList.contains('edit-user-btn') || 
                    classList.contains('edit-role-btn') || 
                    classList.contains('edit-func-btn') ||
                    classList.contains('edit-template-btn') ||
                    classList.contains('assign-roles-btn')) {
                    
                    console.log('🔧 编辑相关按钮点击，处理中...');
                    
                    // 先不阻止默认行为，让原有的数据加载逻辑执行
                    // 然后监听模态框的显示尝试
                    
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
                // 删除按钮保持原有行为
                else if (classList.contains('delete-user-btn') || 
                        classList.contains('delete-role-btn') || 
                        classList.contains('delete-func-btn') ||
                        classList.contains('delete-template-btn')) {
                    console.log('🗑️ 删除按钮点击，保持原有行为');
                    // 不阻止默认行为，让原有的删除逻辑执行
                }
            }, false); // 使用冒泡阶段，让原有事件先执行
        });
        
        // 定期检查并重新绑定（处理动态加载的内容）
        setInterval(() => {
            tables.forEach(tableId => {
                const table = document.getElementById(tableId);
                if (table && table.querySelector('button')) {
                    // 检查是否有新的按钮需要处理
                    const buttons = table.querySelectorAll('button');
                    buttons.forEach(btn => {
                        if (!btn._fixedBySimple) {
                            btn._fixedBySimple = true;
                            console.log('🔧 发现新按钮，已标记处理');
                        }
                    });
                }
            });
        }, 2000);
    }
    
    function showModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 显示模态框: ${modalId}`);
        
        // 显示模态框
        modalEl.style.display = 'block';
        modalEl.classList.add('show');
        modalEl.style.paddingRight = '17px'; // 滚动条补偿
        
        // 设置背景
        modalEl.style.position = 'fixed';
        modalEl.style.top = '0';
        modalEl.style.left = '0';
        modalEl.style.width = '100%';
        modalEl.style.height = '100%';
        modalEl.style.zIndex = '1050';
        modalEl.style.backgroundColor = 'rgba(0,0,0,0.5)';
        modalEl.style.overflowY = 'auto';
        
        // 设置body样式，但不禁止滚动
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
    
    // 拦截Bootstrap Modal调用
    function interceptBootstrapModal() {
        // 等待Bootstrap加载
        const checkBootstrap = () => {
            if (window.bootstrap && window.bootstrap.Modal) {
                console.log("🔧 拦截Bootstrap Modal调用");
                
                // 保存原始构造函数
                const OriginalModal = window.bootstrap.Modal;
                
                // 创建拦截构造函数
                window.bootstrap.Modal = function(element, options = {}) {
                    console.log("🎯 拦截Modal构造:", element.id || element);
                    
                    // 返回一个代理对象
                    return {
                        _element: typeof element === 'string' ? document.getElementById(element) : element,
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
    
    // 暴露到全局，供原有代码调用
    window.showAdminModal = showModal;
    window.hideAdminModal = hideModal;
    
    // 启动修复
    init();
    
    // 拦截Bootstrap Modal
    interceptBootstrapModal();
    
})();

console.log("📋 简化版管理模块模态框修复脚本已加载"); 