// 延期模态框修复脚本
console.log("🚀 延期模态框修复启动");

(function() {
    'use strict';
    
    if (window.extendModalFix) {
        console.log("延期模态框修复已加载，跳过");
        return;
    }
    window.extendModalFix = true;
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 100);
        }
    }
    
    function setup() {
        console.log("🔧 设置延期模态框修复");
        
        // 修复延期模态框
        const extendModalIds = ['adminExtendModal', 'operatorRequestExtendModal'];
        
        extendModalIds.forEach(modalId => {
            const modalEl = document.getElementById(modalId);
            if (modalEl) {
                console.log(`🎯 修复延期模态框: ${modalId}`);
                fixExtendModal(modalEl);
            }
        });
        
        // 拦截Bootstrap Modal调用
        interceptBootstrapModal();
        
        console.log("✅ 延期模态框修复完成");
    }
    
    function fixExtendModal(modalEl) {
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
                console.log(`🔧 关闭延期模态框: ${modalId}`);
                hideExtendModal(modalEl);
            });
        });
        
        // 修复取消按钮（通过文本识别）
        const cancelButtons = modalEl.querySelectorAll('button[type="button"]');
        cancelButtons.forEach(btn => {
            const btnText = btn.textContent.trim();
            if (btnText === '关闭' || btnText === '取消') {
                btn.onclick = null;
                btn.addEventListener('click', function(e) {
                    e.preventDefault();
                    e.stopPropagation();
                    console.log(`🔧 点击${btnText}按钮关闭延期模态框: ${modalId}`);
                    hideExtendModal(modalEl);
                });
            }
        });
    }
    
    function showExtendModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 显示延期模态框: ${modalId}`);
        
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
        
        // 设置body样式，保持页面可滚动
        document.body.classList.add('modal-open');
        // 不设置 overflow: hidden，保持页面可滚动
        
        // 背景点击关闭
        modalEl.addEventListener('click', function(e) {
            if (e.target === modalEl) {
                hideExtendModal(modalEl);
            }
        });
        
        // ESC键关闭
        const escHandler = function(e) {
            if (e.key === 'Escape') {
                hideExtendModal(modalEl);
                document.removeEventListener('keydown', escHandler);
            }
        };
        document.addEventListener('keydown', escHandler);
        modalEl._escHandler = escHandler;
        
        console.log(`✅ 延期模态框显示成功: ${modalId}`);
    }
    
    function hideExtendModal(modalEl) {
        const modalId = modalEl.id;
        console.log(`🔧 隐藏延期模态框: ${modalId}`);
        
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
        
        console.log(`✅ 延期模态框隐藏成功: ${modalId}`);
    }
    
    // 拦截Bootstrap Modal调用
    function interceptBootstrapModal() {
        // 等待Bootstrap加载
        const checkBootstrap = () => {
            if (window.bootstrap && window.bootstrap.Modal) {
                console.log("🔧 拦截延期模态框的Bootstrap Modal调用");
                
                // 保存原始构造函数
                const OriginalModal = window.bootstrap.Modal;
                
                // 创建拦截构造函数
                window.bootstrap.Modal = function(element, options = {}) {
                    const el = typeof element === 'string' ? document.getElementById(element) : element;
                    const elId = el?.id || '';
                    
                    console.log("🎯 拦截Modal构造:", elId);
                    
                    // 检查是否是延期模态框
                    if (elId === 'adminExtendModal' || elId === 'operatorRequestExtendModal') {
                        console.log("🔧 检测到延期模态框，使用自定义实现");
                        
                        // 返回延期模态框的代理对象
                        return {
                            _element: el,
                            show: function() {
                                console.log("🔧 拦截延期Modal.show()调用");
                                if (this._element) {
                                    showExtendModal(this._element);
                                }
                            },
                            hide: function() {
                                console.log("🔧 拦截延期Modal.hide()调用");
                                if (this._element) {
                                    hideExtendModal(this._element);
                                }
                            }
                        };
                    } else {
                        // 非延期模态框，使用原始Bootstrap Modal
                        return new OriginalModal(element, options);
                    }
                };
                
                // 保持静态方法
                Object.keys(OriginalModal).forEach(key => {
                    if (typeof OriginalModal[key] !== 'function') {
                        window.bootstrap.Modal[key] = OriginalModal[key];
                    }
                });
                
                console.log("✅ 延期模态框Bootstrap Modal拦截完成");
            } else {
                setTimeout(checkBootstrap, 100);
            }
        };
        
        checkBootstrap();
    }
    
    // 暴露到全局，供原有代码调用
    window.showExtendModal = showExtendModal;
    window.hideExtendModal = hideExtendModal;
    
    // 启动修复
    init();
    
})();

console.log("📋 延期模态框修复脚本已加载"); 