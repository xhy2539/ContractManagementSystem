// 管理模块统一模态框修复脚本
console.log("🚀 管理模块模态框修复启动");

(function() {
    'use strict';
    
    if (window.adminModalsFix) {
        console.log("管理模块修复已加载，跳过");
        return;
    }
    window.adminModalsFix = true;
    
    // 需要修复的模态框配置
    const modalConfigs = [
        {
            name: '用户管理',
            modalId: 'userFormModal',
            triggerButtons: ['addUserBtn'],
            actionButtons: ['.edit-user-btn', '.assign-roles-btn'],
            nonModalButtons: ['.delete-user-btn'],
            tableId: 'usersTable',
            jsVarName: 'userFormModal'
        },
        {
            name: '角色管理',
            modalId: 'roleFormModal',
            triggerButtons: ['addRoleBtn'],
            actionButtons: ['.edit-role-btn'],
            nonModalButtons: ['.delete-role-btn'],
            tableId: 'rolesTable',
            jsVarName: 'roleFormModal'
        },
        {
            name: '功能管理',
            modalId: 'functionalityFormModal',
            triggerButtons: ['addFunctionalityBtn'],
            actionButtons: ['.edit-func-btn'],
            nonModalButtons: ['.delete-func-btn'],
            tableId: 'functionalitiesTable',
            jsVarName: 'functionalityFormModal'
        },
        {
            name: '角色分配',
            modalId: 'assignRolesModal',
            triggerButtons: [],
            actionButtons: [],
            nonModalButtons: [],
            tableId: 'usersTable',
            jsVarName: 'assignRolesModal'
        },
        {
            name: '模板管理',
            modalId: 'templateFormModal',
            triggerButtons: ['addTemplateBtn'],
            actionButtons: ['.edit-template-btn'],
            nonModalButtons: ['.delete-template-btn'],
            tableId: 'templatesTable',
            jsVarName: 'templateFormModal'
        }
    ];
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 300);
        }
    }
    
    function setup() {
        console.log("🔧 设置管理模块模态框修复");
        
        modalConfigs.forEach(config => {
            const modalEl = document.getElementById(config.modalId);
            if (modalEl) {
                console.log(`🎯 修复 ${config.name} 模态框:`, config.modalId);
                fixModal(modalEl, config);
            }
        });
        
        console.log("✅ 管理模块模态框修复完成");
    }
    
    function fixModal(modalEl, config) {
        // 移除Bootstrap模态框的所有属性
        modalEl.removeAttribute('data-bs-backdrop');
        modalEl.removeAttribute('data-bs-keyboard');
        modalEl.setAttribute('tabindex', '-1');
        modalEl.classList.remove('fade');
        
        // 修复触发按钮
        config.triggerButtons.forEach(btnId => {
            const btn = document.getElementById(btnId);
            if (btn) {
                fixTriggerButton(btn, modalEl, config.name);
            }
        });
        
        // 修复表格中的操作按钮
        if (config.tableId && config.actionButtons.length > 0) {
            fixTableActionButtons(config.tableId, config.actionButtons, modalEl, config.name);
        }
        
        // 修复表格中的非模态框按钮(如删除按钮)
        if (config.tableId && config.nonModalButtons && config.nonModalButtons.length > 0) {
            fixTableNonModalButtons(config.tableId, config.nonModalButtons, config.name);
        }
        
        // 修复关闭按钮
        fixCloseButtons(modalEl, config.name);
        
        // 拦截并替换JavaScript中的模态框方法调用
        interceptModalMethods(modalEl, config);
    }
    
    function interceptModalMethods(modalEl, config) {
        // 为了拦截JavaScript代码中的modal.show()和modal.hide()调用
        // 我们需要在window对象上创建或修改模态框变量
        setTimeout(() => {
            // 尝试从窗口对象获取模态框变量
            const modalVarNames = [config.jsVarName, config.modalId + 'Modal', config.modalId];
            
            modalVarNames.forEach(varName => {
                if (window[varName]) {
                    console.log(`🔄 拦截模态框方法: ${varName}`);
                    const originalModal = window[varName];
                    
                    // 替换show方法
                    originalModal.show = function() {
                        console.log(`🎯 ${config.name} - JavaScript调用.show()`);
                        showFixedModal(modalEl, config.name);
                    };
                    
                    // 替换hide方法
                    originalModal.hide = function() {
                        console.log(`🎯 ${config.name} - JavaScript调用.hide()`);
                        hideFixedModal(modalEl, config.name);
                    };
                }
            });
            
            // 如果变量不存在，创建一个代理对象
            if (!window[config.jsVarName]) {
                console.log(`📝 创建模态框代理对象: ${config.jsVarName}`);
                window[config.jsVarName] = {
                    show: function() {
                        console.log(`🎯 ${config.name} - 代理对象调用.show()`);
                        showFixedModal(modalEl, config.name);
                    },
                    hide: function() {
                        console.log(`🎯 ${config.name} - 代理对象调用.hide()`);
                        hideFixedModal(modalEl, config.name);
                    },
                    _element: modalEl
                };
            }
        }, 500);
    }
    
    function fixTriggerButton(btn, modalEl, modalName) {
        // 清除原有事件
        btn.onclick = null;
        btn.removeAttribute('data-bs-toggle');
        btn.removeAttribute('data-bs-target');
        
        // 添加新的点击事件
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopImmediatePropagation();
            console.log(`🎯 ${modalName} - 触发按钮点击`);
            showFixedModal(modalEl, modalName);
        }, true);
    }
    
    function fixTableActionButtons(tableId, buttonSelectors, modalEl, modalName) {
        const table = document.getElementById(tableId);
        if (!table) return;
        
        // 使用事件委托处理表格中的动态按钮
        table.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            
            // 检查是否是需要修复的按钮
            const isTargetButton = buttonSelectors.some(selector => {
                const selectorClass = selector.replace('.', '');
                return btn.classList.contains(selectorClass);
            });
            
            if (isTargetButton) {
                console.log(`🎯 ${modalName} - 表格操作按钮点击:`, btn.className);
                
                // 阻止原有Bootstrap事件
                e.preventDefault();
                e.stopImmediatePropagation();
                
                // 延迟显示模态框，确保数据已加载
                setTimeout(() => {
                    showFixedModal(modalEl, modalName);
                }, 100);
            }
        }, true);
    }
    
    function fixTableNonModalButtons(tableId, buttonSelectors, modalName) {
        const table = document.getElementById(tableId);
        if (!table) return;
        
        // 确保非模态框按钮（如删除按钮）有正确的点击响应
        table.addEventListener('click', function(e) {
            const btn = e.target.closest('button');
            if (!btn) return;
            
            // 检查是否是非模态框按钮
            const isNonModalButton = buttonSelectors.some(selector => {
                const selectorClass = selector.replace('.', '');
                return btn.classList.contains(selectorClass);
            });
            
            if (isNonModalButton) {
                console.log(`🔧 ${modalName} - 非模态框按钮点击:`, btn.className);
                // 对于删除按钮等，让原有的事件处理逻辑正常执行
                // 只是确保点击事件正确触发，不需要阻止默认行为
            }
        }, false); // 使用冒泡阶段，优先级较低
    }
    
    function fixCloseButtons(modalEl, modalName) {
        // 修复关闭按钮
        const closeButtons = modalEl.querySelectorAll('.btn-close, [data-bs-dismiss="modal"]');
        closeButtons.forEach(btn => {
            btn.onclick = null;
            btn.removeAttribute('data-bs-dismiss');
            
            btn.addEventListener('click', function(e) {
                e.preventDefault();
                e.stopPropagation();
                console.log(`🔧 ${modalName} - 关闭按钮点击`);
                hideFixedModal(modalEl, modalName);
            }, true);
        });
    }
    
    function showFixedModal(modalEl, modalName) {
        console.log(`🔧 显示${modalName}模态框`);
        
        // 手动显示模态框
        modalEl.style.cssText = `
            display: block !important;
            position: fixed !important;
            top: 0 !important;
            left: 0 !important;
            width: 100% !important;
            height: 100% !important;
            z-index: 99999 !important;
            background-color: rgba(0,0,0,0.5) !important;
            overflow-y: auto !important;
            pointer-events: auto !important;
        `;
        modalEl.classList.add('show');
        
        // 添加body类
        document.body.classList.add('modal-open');
        document.body.style.overflow = 'hidden';
        
        // 强制模态框内容样式
        const modalDialog = modalEl.querySelector('.modal-dialog');
        if (modalDialog) {
            modalDialog.style.cssText = `
                position: relative !important;
                width: auto !important;
                margin: 1.75rem auto !important;
                pointer-events: auto !important;
                z-index: 100000 !important;
            `;
        }
        
        const modalContent = modalEl.querySelector('.modal-content');
        if (modalContent) {
            modalContent.style.cssText = `
                position: relative !important;
                background-color: white !important;
                border: 1px solid rgba(0,0,0,.2) !important;
                border-radius: 0.5rem !important;
                pointer-events: auto !important;
                z-index: 100001 !important;
                box-shadow: 0 0.5rem 1rem rgba(0,0,0,.15) !important;
            `;
            
            // 防止内容点击冒泡
            modalContent.addEventListener('click', function(e) {
                e.stopPropagation();
            }, true);
        }
        
        // 延迟添加背景点击事件
        setTimeout(() => {
            modalEl.addEventListener('click', function(e) {
                if (e.target === modalEl) {
                    console.log(`🔧 ${modalName} - 背景点击关闭`);
                    hideFixedModal(modalEl, modalName);
                }
            }, { once: true });
        }, 200);
        
        // ESC键关闭
        const escHandler = function(e) {
            if (e.key === 'Escape' && modalEl.style.display === 'block') {
                console.log(`🔧 ${modalName} - ESC键关闭`);
                hideFixedModal(modalEl, modalName);
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);
        modalEl._escHandler = escHandler;
        
        console.log(`✅ ${modalName}模态框显示成功`);
    }
    
    function hideFixedModal(modalEl, modalName) {
        console.log(`🔧 关闭${modalName}模态框`);
        
        modalEl.style.display = 'none';
        modalEl.classList.remove('show');
        
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        
        // 移除ESC键监听器
        if (modalEl._escHandler) {
            document.removeEventListener('keydown', modalEl._escHandler);
            delete modalEl._escHandler;
        }
        
        console.log(`✅ ${modalName}模态框关闭成功`);
    }
    
    // 启动修复
    init();
    
})();

console.log("📋 管理模块模态框修复脚本已加载"); 