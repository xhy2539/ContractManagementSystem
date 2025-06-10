/**
 * 统一模态框管理器 - 解决初始化方式混合冲突
 * 提供统一的模态框操作API，自动处理Bootstrap冲突
 */
(function() {
    'use strict';
    
    // 防止重复加载
    if (window.UnifiedModalManager) {
        console.log('统一模态框管理器已加载');
        return;
    }
    
    console.log('🚀 统一模态框管理器启动');
    
    class UnifiedModalManager {
        constructor() {
            this.activeModals = new Set();
            this.bypassModals = new Set(['adminExtendModal', 'operatorRequestExtendModal', 'addCustomerFormModal']);
            console.log('🚀 初始化统一模态框管理器');
            
            // 监听所有模态框的隐藏事件
            document.addEventListener('hidden.bs.modal', (event) => {
                const modalId = event.target.id;
                this.activeModals.delete(modalId);
                this.cleanupBackdrops();
            });
        }
        
        /**
         * 获取模态框配置
         */
        getModalConfig(modalEl, options = {}) {
            // 从data属性读取配置
            const backdropAttr = modalEl.getAttribute('data-bs-backdrop');
            const keyboardAttr = modalEl.getAttribute('data-bs-keyboard');
            
            // 转换为正确的类型
            const backdrop = backdropAttr === 'static' ? 'static' : 
                           backdropAttr === 'false' ? false : true;
            
            const keyboard = keyboardAttr === 'false' ? false : true;
            
            return {
                backdrop: backdrop,
                keyboard: keyboard,
                focus: true,
                ...options
            };
        }
        
        /**
         * 重写Bootstrap Modal构造函数，统一管理所有模态框
         */
        overrideBootstrapModal() {
            const originalModal = bootstrap.Modal;
            const self = this;
            
            bootstrap.Modal = function(element, options) {
                const modalEl = typeof element === 'string' ? document.querySelector(element) : element;
                const modalId = modalEl.id;
                
                // 对于特定模态框，直接返回原生实例，不进行增强处理
                if (self.bypassModals.has(modalId)) {
                    console.log(`🚫 跳过统一管理器处理: ${modalId}`);
                    return new originalModal(modalEl, {
                        backdrop: modalEl.getAttribute('data-bs-backdrop') === 'static' ? 'static' : true,
                        keyboard: modalEl.getAttribute('data-bs-keyboard') === 'false' ? false : true,
                        focus: true,
                        ...options
                    });
                }
                
                // 如果已有实例，返回现有实例
                if (self.activeModals.has(modalId)) {
                    return self.activeModals.get(modalId);
                }
                
                // 创建新的Bootstrap实例
                const config = self.getModalConfig(modalEl, options);
                console.log(`📝 模态框配置 [${modalId}]:`, config);
                
                const instance = new originalModal(modalEl, config);
                
                // 增强实例方法
                const enhancedInstance = self.enhanceModalInstance(instance, modalEl);
                self.activeModals.add(modalId);
                
                console.log(`📝 注册模态框实例: ${modalId}`);
                return enhancedInstance;
            };
            
            // 保持原有的静态方法
            Object.setPrototypeOf(bootstrap.Modal, originalModal);
            Object.assign(bootstrap.Modal, originalModal);
        }
        
        /**
         * 增强模态框实例
         */
        enhanceModalInstance(instance, modalEl) {
            const originalShow = instance.show.bind(instance);
            const originalHide = instance.hide.bind(instance);
            const self = this;
            
            instance.show = () => {
                self.beforeShow(modalEl);
                originalShow();
                self.afterShow(modalEl);
            };
            
            instance.hide = () => {
                self.beforeHide(modalEl);
                originalHide();
                self.afterHide(modalEl);
            };
            
            return instance;
        }
        
        /**
         * 显示模态框前的处理
         */
        beforeShow(modalEl) {
            const modalId = modalEl.id;
            console.log(`🔓 准备显示模态框: ${modalId}`);
            
            // 修复可能的样式问题
            this.fixModalStyles(modalEl);
            
            // 移除其他模态框的背景遮罩
            document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
                if (backdrop.getAttribute('data-modal-id') !== modalId) {
                    backdrop.remove();
                }
            });
        }
        
        /**
         * 显示模态框后的处理
         */
        afterShow(modalEl) {
            const modalId = modalEl.id;
            
            // 确保只有一个背景遮罩
            const existingBackdrop = document.querySelector('.modal-backdrop');
            if (existingBackdrop) {
                existingBackdrop.setAttribute('data-modal-id', modalId);
            }
            
            // 聚焦到第一个可输入元素
            setTimeout(() => {
                const firstInput = modalEl.querySelector('input:not([type="hidden"]), select, textarea');
                if (firstInput && !firstInput.disabled) {
                    firstInput.focus();
                }
            }, 150);
            
            console.log(`✅ 模态框已显示: ${modalId}`);
        }
        
        /**
         * 隐藏模态框前的处理
         */
        beforeHide(modalEl) {
            const modalId = modalEl.id;
            console.log(`🔒 准备隐藏模态框: ${modalId}`);
            
            // 从活动模态框列表中移除
            this.activeModals.delete(modalId);
        }
        
        /**
         * 隐藏模态框后的处理
         */
        afterHide(modalEl) {
            const modalId = modalEl.id;
            
            // 移除对应的背景遮罩
            const backdrop = document.querySelector(`.modal-backdrop[data-modal-id="${modalId}"]`);
            if (backdrop) {
                backdrop.remove();
            }
            
            // 如果没有活动的模态框，清理body样式
            if (this.activeModals.size === 0) {
                document.body.classList.remove('modal-open');
                document.body.style.paddingRight = '';
                document.body.style.overflow = '';
            }
            
            console.log(`✅ 模态框已隐藏: ${modalId}`);
        }
        
        /**
         * 修复模态框样式问题
         */
        fixModalStyles(modalEl) {
            // 确保模态框有正确的z-index
            const baseZIndex = 1050;
            const activeModalsCount = this.activeModals.size;
            modalEl.style.zIndex = (baseZIndex + (activeModalsCount * 2)).toString();
            
            // 确保背景遮罩在模态框下方
            const backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) {
                backdrop.style.zIndex = (baseZIndex + (activeModalsCount * 2) - 1).toString();
            }
        }
        
        /**
         * 设置全局事件处理器
         */
        setupGlobalEventHandlers() {
            // ESC键关闭最上层模态框
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    const topModalId = Array.from(this.activeModals).pop();
                    if (topModalId) {
                        const modalEl = document.getElementById(topModalId);
                        if (modalEl && modalEl.getAttribute('data-bs-keyboard') !== 'false') {
                            this.hideModal(topModalId);
                        }
                    }
                }
            });
            
            // 点击背景关闭模态框
            document.addEventListener('click', (e) => {
                if (e.target.classList.contains('modal')) {
                    const modalId = e.target.id;
                    const modalEl = document.getElementById(modalId);
                    if (modalEl && modalEl.getAttribute('data-bs-backdrop') !== 'static') {
                        this.hideModal(modalId);
                    }
                }
            });
        }
        
        /**
         * 显示模态框
         */
        showModal(modalId) {
            console.log(`🔓 尝试显示模态框: ${modalId}`);
            const modalEl = document.getElementById(modalId);
            
            if (!modalEl) {
                console.error(`❌ 模态框不存在: ${modalId}`);
                return false;
            }
            
            const modal = bootstrap.Modal.getInstance(modalEl) || new bootstrap.Modal(modalEl);
            modal.show();
            return true;
        }
        
        /**
         * 隐藏模态框
         */
        hideModal(modalId) {
            console.log(`🔒 尝试隐藏模态框: ${modalId}`);
            const modalEl = document.getElementById(modalId);
            
            if (!modalEl) {
                console.error(`❌ 模态框不存在: ${modalId}`);
                return false;
            }
            
            const modal = bootstrap.Modal.getInstance(modalEl);
            if (modal) {
                modal.hide();
                return true;
            }
            
            return false;
        }
        
        /**
         * 手动隐藏模态框
         */
        hideModalManually(modalEl) {
            modalEl.style.display = 'none';
            modalEl.setAttribute('aria-hidden', 'true');
            modalEl.removeAttribute('aria-modal');
            modalEl.removeAttribute('role');
            document.body.classList.remove('modal-open');
            
            const backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) backdrop.remove();
        }
        
        /**
         * 清理背景遮罩
         */
        cleanupBackdrops() {
            // 如果没有活动的模态框，移除所有背景遮罩
            if (this.activeModals.size === 0) {
                document.querySelectorAll('.modal-backdrop').forEach(backdrop => {
                    backdrop.remove();
                });
                document.body.classList.remove('modal-open');
                document.body.style.paddingRight = '';
                document.body.style.overflow = '';
            }
        }
    }
    
    // 创建单例实例
    const modalManager = new UnifiedModalManager();
    
    // 重写Bootstrap Modal
    modalManager.overrideBootstrapModal();
    
    // 设置全局事件处理器
    modalManager.setupGlobalEventHandlers();
    
    // 暴露到全局
    window.UnifiedModalManager = modalManager;
})(); 