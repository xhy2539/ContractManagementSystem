// 客户选择模态框相关代码
document.addEventListener('DOMContentLoaded', function() {
    console.log('DOM加载完成，开始初始化客户选择模态框');
    
    const openCustomerSelectModalBtn = document.getElementById('openCustomerSelectModalBtn');
    const customerSelectModal = document.getElementById('customerSelectModal');
    const customerSearchKeyword = document.getElementById('customerSearchKeyword');
    const customerSelectTableBody = document.getElementById('customerSelectTableBody');
    const customerSelectPagination = document.getElementById('customerSelectPagination');
    const customerModalSpinner = document.getElementById('customerModalSpinner');
    const addCustomerFormModal = document.getElementById('addCustomerFormModal');
    
    console.log('模态框元素检查：', {
        customerSelectModal: !!customerSelectModal,
        addCustomerFormModal: !!addCustomerFormModal
    });

    let currentPage = 1;
    let pageSize = 10;
    let totalPages = 1;
    let searchTimeout;

    // 初始化Bootstrap模态框，禁用滚动锁定
    const bsCustomerSelectModal = new bootstrap.Modal(customerSelectModal, {
        backdrop: true,
        keyboard: true,
        focus: true,
        // 禁用Bootstrap默认的滚动锁定
        scroll: true
    });

    // 手动处理模态框的滚动行为
    const originalPaddingRight = document.body.style.paddingRight;
    const originalOverflow = document.body.style.overflow;

    // 监听模态框显示事件
    customerSelectModal.addEventListener('show.bs.modal', function () {
        // 保存原始样式
        document.body.dataset.originalPaddingRight = originalPaddingRight;
        document.body.dataset.originalOverflow = originalOverflow;

        // 获取选择客户按钮的位置
        const openButton = document.getElementById('openCustomerSelectModalBtn');
        if (openButton) {
            const buttonRect = openButton.getBoundingClientRect();
            const modalDialog = customerSelectModal.querySelector('.modal-dialog');
            if (modalDialog) {
                // 计算滚动位置
                const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                // 设置模态框的位置，考虑页面滚动位置
                const topPosition = buttonRect.bottom + scrollTop + 20; // 在按钮下方20px处显示
                modalDialog.style.marginTop = '0';
                modalDialog.style.top = `${topPosition}px`;
                modalDialog.style.marginBottom = '1.75rem';
            }
        }
    });

    // 监听模态框完全显示后的事件
    customerSelectModal.addEventListener('shown.bs.modal', function () {
        console.log('模态框显示，准备绑定按钮事件');
        bindAddNewCustomerButton();
        customerSearchKeyword.focus();
        loadCustomers(1);
        
        // 确保模态框内容区域可以滚动
        const modalBody = customerSelectModal.querySelector('.modal-body');
        if (modalBody) {
            modalBody.style.overflow = 'auto';
        }

        // 移除Bootstrap添加的样式
        document.body.style.overflow = '';
        document.body.style.paddingRight = '';
    });

    // 监听模态框隐藏事件
    customerSelectModal.addEventListener('hidden.bs.modal', function () {
        // 恢复原始样式
        document.body.style.paddingRight = document.body.dataset.originalPaddingRight || '';
        document.body.style.overflow = document.body.dataset.originalOverflow || '';
    });

    // 监听窗口大小改变和滚动事件
    const updateModalPosition = function() {
        if (customerSelectModal.classList.contains('show')) {
            const openButton = document.getElementById('openCustomerSelectModalBtn');
            const modalDialog = customerSelectModal.querySelector('.modal-dialog');
            if (openButton && modalDialog) {
                const buttonRect = openButton.getBoundingClientRect();
                const scrollTop = window.pageYOffset || document.documentElement.scrollTop;
                const topPosition = buttonRect.bottom + scrollTop + 20; // 在按钮下方20px处显示
                modalDialog.style.top = `${topPosition}px`;
            }
        }
    };

    window.addEventListener('resize', updateModalPosition);
    window.addEventListener('scroll', updateModalPosition);

    // 搜索框输入事件
    if (customerSearchKeyword) {
        customerSearchKeyword.addEventListener('input', function() {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                currentPage = 1;
                loadCustomers(currentPage);
            }, 300);
        });

        // 回车键触发搜索
        customerSearchKeyword.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                currentPage = 1;
                loadCustomers(currentPage);
            }
        });
    }

    // 加载客户数据
    async function loadCustomers(page) {
        try {
            showSpinner();
            const keyword = customerSearchKeyword.value.trim();
            const response = await fetch(`/customers/api/search?page=${page}&size=${pageSize}&keyword=${encodeURIComponent(keyword)}`);
            
            if (!response.ok) {
                throw new Error('加载客户数据失败');
            }

            const data = await response.json();
            renderCustomers(data.content);
            renderPagination(data.totalPages);
            hideSpinner();
        } catch (error) {
            console.error('加载客户数据出错:', error);
            showModalAlert('加载客户数据失败，请重试', 'danger');
            hideSpinner();
        }
    }

    // 渲染客户列表
    function renderCustomers(customers) {
        if (!customerSelectTableBody) return;
        
        customerSelectTableBody.innerHTML = customers.map(customer => `
            <tr class="customer-row" data-customer-id="${customer.id}">
                <td>${customer.customerNumber || '-'}</td>
                <td>${customer.customerName}</td>
                <td>${customer.phoneNumber || '-'}</td>
                <td>${customer.email || '-'}</td>
                <td class="text-center">
                    <button type="button" class="btn btn-sm btn-primary select-customer-btn">
                        <i class="bi bi-check-circle me-1"></i>选择
                    </button>
                </td>
            </tr>
        `).join('');

        // 为每一行添加点击事件
        const rows = customerSelectTableBody.querySelectorAll('.customer-row');
        rows.forEach(row => {
            row.addEventListener('click', function(e) {
                // 如果点击的是选择按钮，不需要处理
                if (e.target.closest('.select-customer-btn')) return;
                
                // 获取该行的选择按钮并触发点击
                const selectBtn = this.querySelector('.select-customer-btn');
                if (selectBtn) selectBtn.click();
            });

            // 为选择按钮添加事件
            const selectBtn = row.querySelector('.select-customer-btn');
            if (selectBtn) {
                selectBtn.addEventListener('click', function() {
                    const customerId = row.dataset.customerId;
                    selectCustomer({
                        id: customerId,
                        customerNumber: row.cells[0].textContent,
                        customerName: row.cells[1].textContent,
                        phoneNumber: row.cells[2].textContent,
                        email: row.cells[3].textContent
                    });
                });
            }
        });
    }

    // 渲染分页
    function renderPagination(totalPages) {
        if (!customerSelectPagination) return;
        
        const paginationList = customerSelectPagination.querySelector('ul');
        if (!paginationList) return;

        let paginationHtml = '';
        
        // 上一页按钮
        paginationHtml += `
            <li class="page-item ${currentPage === 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${currentPage - 1}" aria-label="上一页">
                    <i class="bi bi-chevron-left"></i>
                </a>
            </li>
        `;

        // 页码按钮
        for (let i = 1; i <= totalPages; i++) {
            if (i === 1 || i === totalPages || (i >= currentPage - 2 && i <= currentPage + 2)) {
                paginationHtml += `
                    <li class="page-item ${i === currentPage ? 'active' : ''}">
                        <a class="page-link" href="#" data-page="${i}">${i}</a>
                    </li>
                `;
            } else if (i === currentPage - 3 || i === currentPage + 3) {
                paginationHtml += `
                    <li class="page-item disabled">
                        <span class="page-link">...</span>
                    </li>
                `;
            }
        }

        // 下一页按钮
        paginationHtml += `
            <li class="page-item ${currentPage === totalPages ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${currentPage + 1}" aria-label="下一页">
                    <i class="bi bi-chevron-right"></i>
                </a>
            </li>
        `;

        paginationList.innerHTML = paginationHtml;

        // 添加分页点击事件
        paginationList.querySelectorAll('.page-link').forEach(link => {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                const page = parseInt(this.dataset.page);
                if (!isNaN(page) && page !== currentPage) {
                    currentPage = page;
                    loadCustomers(currentPage);
                }
            });
        });
    }

    // 选择客户
    function selectCustomer(customer) {
        if (!customer) return;

        // 更新隐藏字段
        document.getElementById('customerId').value = customer.id;
        
        // 更新显示信息
        const customerInfoPlaceholder = document.getElementById('selectedCustomerInfoPlaceholder');
        if (customerInfoPlaceholder) {
            customerInfoPlaceholder.innerHTML = `
                <div class="d-flex flex-column">
                    <strong class="text-primary">${customer.customerName}</strong>
                    <small class="text-muted">
                        <i class="bi bi-person-badge me-1"></i>${customer.customerNumber || '-'}
                        <span class="mx-2">|</span>
                        <i class="bi bi-telephone me-1"></i>${customer.phoneNumber || '-'}
                        <span class="mx-2">|</span>
                        <i class="bi bi-envelope me-1"></i>${customer.email || '-'}
                    </small>
                </div>
            `;
            customerInfoPlaceholder.classList.remove('is-invalid-placeholder');
        }

        // 隐藏模态框
        bsCustomerSelectModal.hide();
    }

    // 显示/隐藏加载动画
    function showSpinner() {
        if (customerModalSpinner) {
            customerModalSpinner.classList.remove('d-none');
        }
    }

    function hideSpinner() {
        if (customerModalSpinner) {
            customerModalSpinner.classList.add('d-none');
        }
    }

    // 显示模态框提示信息
    function showModalAlert(message, type = 'danger') {
        const alertPlaceholder = document.getElementById('customerModalAlertPlaceholder');
        if (!alertPlaceholder) return;

        const alert = document.createElement('div');
        alert.className = `alert alert-${type} alert-dismissible fade show`;
        alert.innerHTML = `
            <i class="bi bi-exclamation-triangle-fill me-2"></i>${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        `;

        alertPlaceholder.innerHTML = '';
        alertPlaceholder.appendChild(alert);

        // 3秒后自动消失
        setTimeout(() => {
            alert.classList.remove('show');
            setTimeout(() => alert.remove(), 150);
        }, 3000);
    }

    // 添加样式
    const style = document.createElement('style');
    style.textContent = `
        .modal-dialog {
            transition: all 0.3s ease-out !important;
            position: relative !important;
        }
        .modal.fade .modal-dialog {
            transform: translate(0, -5px);
        }
        .modal.show .modal-dialog {
            transform: none;
        }
    `;
    document.head.appendChild(style);

    // 初始化添加客户模态框
    const bsAddCustomerFormModal = new bootstrap.Modal(addCustomerFormModal);

    // 绑定添加新用户按钮事件的函数
    function bindAddNewCustomerButton() {
        console.log('正在绑定添加新用户按钮事件');
        const addNewCustomerBtns = document.querySelectorAll('.modalAddNewCustomerBtn');
        console.log('找到的按钮数量:', addNewCustomerBtns.length);
        
        addNewCustomerBtns.forEach(btn => {
            console.log('正在处理按钮:', btn);
            
            // 移除所有已存在的点击事件监听器
            const newBtn = btn.cloneNode(true);
            btn.parentNode.replaceChild(newBtn, btn);
            
            // 添加新的点击事件监听器
            newBtn.onclick = function(e) {
                console.log('添加新用户按钮被点击 - onclick');
                e.preventDefault();
                e.stopPropagation();
                
                // 手动处理模态框切换
                if (customerSelectModal && addCustomerFormModal) {
                    console.log('准备切换模态框');
                    
                    // 隐藏客户选择模态框
                    const bsCustomerSelectModal = bootstrap.Modal.getInstance(customerSelectModal);
                    if (bsCustomerSelectModal) {
                        console.log('使用Bootstrap API隐藏客户选择模态框');
                        bsCustomerSelectModal.hide();
                    }
                    
                    // 显示添加客户模态框
                    setTimeout(() => {
                        console.log('准备显示添加客户模态框');
                        const bsAddCustomerFormModal = new bootstrap.Modal(addCustomerFormModal, {
                            backdrop: true,
                            keyboard: true,
                            focus: true
                        });
                        bsAddCustomerFormModal.show();
                    }, 300);
                } else {
                    console.error('模态框元素未找到:', {
                        customerSelectModal: !!customerSelectModal,
                        addCustomerFormModal: !!addCustomerFormModal
                    });
                }
            };
            
            // 同时添加addEventListener
            newBtn.addEventListener('click', function(e) {
                console.log('添加新用户按钮被点击 - addEventListener');
            });
        });
    }

    // 确保在DOM加载完成后绑定事件
    document.addEventListener('DOMContentLoaded', function() {
        console.log('DOM加载完成，初始绑定添加新用户按钮事件');
        bindAddNewCustomerButton();
    });

    // 监听模态框显示事件，重新绑定按钮事件
    customerSelectModal.addEventListener('shown.bs.modal', function() {
        console.log('客户选择模态框显示事件触发，重新绑定按钮事件');
        bindAddNewCustomerButton();
    });
});

// 添加新客户的全局处理函数
window.handleAddNewCustomer = function(event) {
    console.log('添加新客户按钮被点击（通过全局函数）');
    event.preventDefault();
    event.stopPropagation();
    
    const customerSelectModal = document.getElementById('customerSelectModal');
    const addCustomerFormModal = document.getElementById('addCustomerFormModal');
    
    if (customerSelectModal && addCustomerFormModal) {
        console.log('准备切换模态框');
        
        // 隐藏客户选择模态框
        const bsCustomerSelectModal = bootstrap.Modal.getInstance(customerSelectModal);
        if (bsCustomerSelectModal) {
            console.log('使用Bootstrap API隐藏客户选择模态框');
            bsCustomerSelectModal.hide();
        }
        
        // 显示添加客户模态框
        setTimeout(() => {
            console.log('准备显示添加客户模态框');
            const bsAddCustomerFormModal = new bootstrap.Modal(addCustomerFormModal, {
                backdrop: true,
                keyboard: true,
                focus: true
            });
            bsAddCustomerFormModal.show();
        }, 300);
    } else {
        console.error('模态框元素未找到:', {
            customerSelectModal: !!customerSelectModal,
            addCustomerFormModal: !!addCustomerFormModal
        });
    }
}; 