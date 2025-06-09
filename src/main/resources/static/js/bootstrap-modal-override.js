// Bootstrap Modal构造函数拦截脚本
// 此脚本必须在其他管理脚本之前加载
console.log("🔧 Bootstrap Modal拦截器启动");

(function() {
    'use strict';
    
    // 保存原始的Bootstrap Modal构造函数
    let OriginalModal = null;
    
    // 等待Bootstrap加载完成
    function waitForBootstrap() {
        if (window.bootstrap && window.bootstrap.Modal) {
            OriginalModal = window.bootstrap.Modal;
            console.log("✅ Bootstrap Modal已找到，开始拦截");
            interceptModalConstructor();
        } else {
            console.log("⏳ 等待Bootstrap加载...");
            setTimeout(waitForBootstrap, 100);
        }
    }
    
    function interceptModalConstructor() {
        // 创建我们自己的Modal类
        class CustomModal {
            constructor(element, options = {}) {
                console.log("🎯 拦截Modal构造:", element.id);
                this._element = element;
                this._options = options;
                this._isShown = false;
                
                // 移除Bootstrap相关属性
                element.removeAttribute('data-bs-backdrop');
                element.removeAttribute('data-bs-keyboard');
                element.setAttribute('tabindex', '-1');
                element.classList.remove('fade');
                
                // 修复关闭按钮
                this._fixCloseButtons();
            }
            
            show() {
                const modalName = this._getModalName();
                console.log(`🔧 显示${modalName}模态框 (拦截版)`);
                this._showCustomModal();
            }
            
            hide() {
                const modalName = this._getModalName();
                console.log(`🔧 关闭${modalName}模态框 (拦截版)`);
                this._hideCustomModal();
            }
            
            _getModalName() {
                const modalId = this._element.id;
                const nameMap = {
                    'userFormModal': '用户管理',
                    'roleFormModal': '角色管理', 
                    'functionalityFormModal': '功能管理',
                    'assignRolesModal': '角色分配',
                    'adminExtendModal': '管理员延期',
                    'operatorRequestExtendModal': '操作员延期请求',
                    'templateFormModal': '模板管理'
                };
                return nameMap[modalId] || modalId;
            }
            
            _fixCloseButtons() {
                // 查找所有可能的关闭按钮
                const closeButtons = this._element.querySelectorAll('.btn-close, .btn-secondary');
                closeButtons.forEach(btn => {
                    // 清除现有事件
                    btn.onclick = null;
                    btn.removeAttribute('data-bs-dismiss');
                    
                    // 检查按钮文本内容，确定是否为关闭按钮
                    const btnText = btn.textContent.trim();
                    const isCloseButton = btnText === '关闭' || btnText === '取消' || btn.classList.contains('btn-close');
                    
                    if (isCloseButton) {
                        console.log(`🔧 绑定关闭按钮: "${btnText}"`);
                        btn.addEventListener('click', (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            console.log(`👆 点击关闭按钮: "${btnText}"`);
                            this.hide();
                        }, true);
                    }
                });
                
                // 为延期模态框的取消按钮也添加事件监听器
                const cancelButtons = this._element.querySelectorAll('button[type="button"]');
                cancelButtons.forEach(btn => {
                    const btnText = btn.textContent.trim();
                    if (btnText === '取消' && !btn.classList.contains('btn-close')) {
                        console.log(`🔧 绑定延期取消按钮: "${btnText}"`);
                        btn.addEventListener('click', (e) => {
                            e.preventDefault();
                            e.stopPropagation();
                            console.log(`👆 点击延期取消按钮: "${btnText}"`);
                            this.hide();
                        }, true);
                    }
                });
            }
            
            _showCustomModal() {
                this._element.style.cssText = `
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
                this._element.classList.add('show');
                this._isShown = true;
                
                // 添加body类
                document.body.classList.add('modal-open');
                document.body.style.overflow = 'hidden';
                
                // 强制模态框内容样式
                const modalDialog = this._element.querySelector('.modal-dialog');
                if (modalDialog) {
                    modalDialog.style.cssText = `
                        position: relative !important;
                        width: auto !important;
                        margin: 1.75rem auto !important;
                        pointer-events: auto !important;
                        z-index: 100000 !important;
                    `;
                }
                
                const modalContent = this._element.querySelector('.modal-content');
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
                    this._element.addEventListener('click', (e) => {
                        if (e.target === this._element) {
                            this.hide();
                        }
                    }, { once: true });
                }, 200);
                
                // ESC键关闭
                this._escHandler = (e) => {
                    if (e.key === 'Escape' && this._isShown) {
                        this.hide();
                    }
                };
                document.addEventListener('keydown', this._escHandler);
            }
            
            _hideCustomModal() {
                this._element.style.display = 'none';
                this._element.classList.remove('show');
                this._isShown = false;
                
                document.body.classList.remove('modal-open');
                document.body.style.overflow = '';
                
                // 移除ESC键监听器
                if (this._escHandler) {
                    document.removeEventListener('keydown', this._escHandler);
                    delete this._escHandler;
                }
            }
        }
        
        // 替换Bootstrap Modal构造函数
        window.bootstrap.Modal = CustomModal;
        console.log("✅ Bootstrap Modal构造函数已替换");
    }
    
    // 开始等待Bootstrap
    waitForBootstrap();
    
})();

console.log("📋 Bootstrap Modal拦截器已加载"); 