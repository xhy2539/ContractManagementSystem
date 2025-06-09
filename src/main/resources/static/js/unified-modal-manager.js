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
            this.modalInstances = new Map();
            this.isBootstrapReady = false;
            this.init();
        }
        
        init() {
            this.waitForBootstrap().then(() => {
                this.isBootstrapReady = true;
                this.overrideBootstrapModal();
                this.setupGlobalEventHandlers();
                console.log('✅ 统一模态框管理器初始化完成');
            });
        }
        
        waitForBootstrap() {
            return new Promise((resolve) => {
                const checkBootstrap = () => {
                    if (typeof bootstrap !== 'undefined' && bootstrap.Modal) {
                        resolve();
                    } else {
                        setTimeout(checkBootstrap, 100);
                    }
                };
                checkBootstrap();
            });
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
                
                // 对于合同延期相关的模态框，直接返回原生实例，不进行增强处理
                if (modalId === 'adminExtendModal' || modalId === 'operatorRequestExtendModal') {
                    console.log(`🚫 跳过统一管理器处理: ${modalId}`);
                    return new originalModal(modalEl, {
                        backdrop: true,
                        keyboard: true,
                        focus: true,
                        ...options
                    });
                }
                
                // 如果已有实例，返回现有实例
                if (self.modalInstances.has(modalId)) {
                    return self.modalInstances.get(modalId);
                }
                
                // 创建新的Bootstrap实例，确保backdrop和keyboard正确配置
                const instance = new originalModal(modalEl, {
                    backdrop: true, // 允许点击背景关闭
                    keyboard: true, // 允许ESC键关闭
                    focus: true,    // 自动聚焦
                    ...options
                });
                
                // 增强实例方法
                const enhancedInstance = self.enhanceModalInstance(instance, modalEl);
                self.modalInstances.set(modalId, enhancedInstance);
                
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
            
            instance.show = () => {
                this.beforeShow(modalEl);
                originalShow();
                this.afterShow(modalEl);
            };
            
            instance.hide = () => {
                this.beforeHide(modalEl);
                originalHide();
                this.afterHide(modalEl);
            };
            
            return instance;
        }
        
        /**
         * 显示模态框前的处理
         */
        beforeShow(modalEl) {
            // 关闭其他已打开的模态框
            this.hideAllOtherModals(modalEl.id);
            
            // 修复可能的样式问题
            this.fixModalStyles(modalEl);
            
            console.log(`🔓 准备显示模态框: ${modalEl.id}`);
        }
        
        /**
         * 显示模态框后的处理
         */
        afterShow(modalEl) {
            // 聚焦到第一个可输入元素
            setTimeout(() => {
                const firstInput = modalEl.querySelector('input, textarea, select');
                if (firstInput && !firstInput.disabled) {
                    firstInput.focus();
                }
            }, 150);
            
            console.log(`✅ 模态框已显示: ${modalEl.id}`);
        }
        
        /**
         * 隐藏模态框前的处理
         */
        beforeHide(modalEl) {
            console.log(`🔒 准备隐藏模态框: ${modalEl.id}`);
        }
        
        /**
         * 隐藏模态框后的处理
         */
        afterHide(modalEl) {
            // 清理表单数据（如果需要）
            this.resetModalForm(modalEl);
            console.log(`✅ 模态框已隐藏: ${modalEl.id}`);
        }
        
        /**
         * 关闭其他已打开的模态框
         */
        hideAllOtherModals(currentModalId) {
            this.modalInstances.forEach((instance, modalId) => {
                if (modalId !== currentModalId) {
                    try {
                        instance.hide();
                    } catch (e) {
                        console.warn(`关闭模态框 ${modalId} 时出错:`, e);
                    }
                }
            });
        }
        
        /**
         * 修复模态框样式问题
         */
        fixModalStyles(modalEl) {
            // 确保模态框有正确的z-index
            modalEl.style.zIndex = '1055';
            
            // 不要强制设置display和移除show类，让Bootstrap自己处理
            // 只确保模态框没有被其他样式覆盖
        }
        
        /**
         * 重置模态框表单
         */
        resetModalForm(modalEl) {
            const forms = modalEl.querySelectorAll('form');
            forms.forEach(form => {
                // 只重置标记了自动重置的表单
                if (form.hasAttribute('data-auto-reset')) {
                    form.reset();
                }
            });
        }
        
        /**
         * 设置全局事件处理器
         */
        setupGlobalEventHandlers() {
            // ESC键关闭模态框
            document.addEventListener('keydown', (e) => {
                if (e.key === 'Escape') {
                    this.hideTopModal();
                }
            });
            
            // 点击背景关闭模态框
            document.addEventListener('click', (e) => {
                // 检查是否点击了模态框背景（而不是模态框内容）
                if (e.target.classList.contains('modal') || e.target.classList.contains('modal-backdrop')) {
                    const modal = e.target.classList.contains('modal') ? e.target : 
                                 document.querySelector('.modal.show');
                    if (modal) {
                        const modalInstance = this.modalInstances.get(modal.id);
                        if (modalInstance) {
                            modalInstance.hide();
                        }
                    }
                }
            });
        }
        
        /**
         * 关闭最顶层的模态框
         */
        hideTopModal() {
            const visibleModal = document.querySelector('.modal.show');
            if (visibleModal) {
                const modalInstance = this.modalInstances.get(visibleModal.id);
                if (modalInstance) {
                    modalInstance.hide();
                }
            }
        }
        
        /**
         * 静态方法：显示模态框
         */
        static show(modalId) {
            const modalEl = document.getElementById(modalId);
            if (modalEl) {
                const modal = new bootstrap.Modal(modalEl);
                modal.show();
                return modal;
            }
            console.error(`模态框不存在: ${modalId}`);
            return null;
        }
        
        /**
         * 静态方法：隐藏模态框
         */
        static hide(modalId) {
            const modalEl = document.getElementById(modalId);
            if (modalEl) {
                const instance = bootstrap.Modal.getInstance(modalEl);
                if (instance) {
                    instance.hide();
                    return true;
                }
            }
            console.error(`模态框实例不存在: ${modalId}`);
            return false;
        }
        
        /**
         * 静态方法：获取模态框实例
         */
        static getInstance(modalId) {
            const modalEl = document.getElementById(modalId);
            if (modalEl) {
                return bootstrap.Modal.getInstance(modalEl);
            }
            return null;
        }
    }
    
    // 创建全局实例
    window.UnifiedModalManager = UnifiedModalManager;
    
    // 等待DOM加载完成后初始化
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => {
            new UnifiedModalManager();
        });
    } else {
        new UnifiedModalManager();
    }
    
    // 全局便捷方法
    window.showModal = function(modalId) {
        return UnifiedModalManager.show(modalId);
    };
    
    window.hideModal = function(modalId) {
        return UnifiedModalManager.hide(modalId);
    };
    
    window.getModalInstance = function(modalId) {
        return UnifiedModalManager.getInstance(modalId);
    };
    
})(); 