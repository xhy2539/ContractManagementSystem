document.addEventListener('DOMContentLoaded', function() {
    // --- 元素获取 ---
    const openCustomerSelectModalBtn = document.getElementById('openCustomerSelectModalBtn');
    const customerSelectModalEl = document.getElementById('customerSelectModal');
    const addCustomerFormModalEl = document.getElementById('addCustomerFormModal');

    // 如果关键模态框不存在，则直接退出，防止后续代码出错
    if (!customerSelectModalEl || !addCustomerFormModalEl) {
        console.error("错误：未能找到 'customerSelectModal' 或 'addCustomerFormModal' 元素。脚本已终止。");
        return;
    }

    const customerSearchKeyword = document.getElementById('customerSearchKeyword');
    const customerSelectTableBody = document.getElementById('customerSelectTableBody');
    const customerSelectPagination = document.getElementById('customerSelectPagination');
    const customerModalSpinner = document.getElementById('customerModalSpinner');
    const selectedCustomerInfoPlaceholder = document.getElementById('selectedCustomerInfoPlaceholder');
    const selectedCustomerIdInput = document.getElementById('selectedCustomerId');


    // --- 状态变量 ---
    let currentPage = 1;
    const pageSize = 10;
    let searchTimeout;

    // ==============================================================================
    // --- 核心修复：手动模态框管理 ---
    // 这部分代码将取代 Bootstrap 的自动处理，以确保切换的稳定性。
    // ==============================================================================

    /**
     * 手动显示一个模态框。
     * @param {HTMLElement} modalEl 要显示的模态框元素。
     */
    function manualShowModal(modalEl) {
        if (!modalEl) return;

        // 1. 创建或显示背景遮罩
        let backdrop = document.querySelector('.modal-backdrop');
        if (!backdrop) {
            backdrop = document.createElement('div');
            backdrop.className = 'modal-backdrop fade';
            document.body.appendChild(backdrop);
        }
        // 使用微小的延迟来触发CSS过渡效果
        setTimeout(() => backdrop.classList.add('show'), 10);

        // 2. 设置body样式以防止背景滚动
        document.body.classList.add('modal-open');
        document.body.style.overflow = 'hidden';

        // 3. 显示模态框
        modalEl.style.display = 'block';
        modalEl.setAttribute('aria-modal', 'true');
        modalEl.setAttribute('role', 'dialog');
        setTimeout(() => modalEl.classList.add('show'), 10);

        console.log(`模态框 ${modalEl.id} 已手动显示。`);
    }

    /**
     * 手动隐藏一个模态框。
     * @param {HTMLElement} modalEl 要隐藏的模态框元素。
     */
    function manualHideModal(modalEl) {
        if (!modalEl) return;

        // 1. 隐藏模态框
        modalEl.classList.remove('show');

        // 2. 隐藏背景遮罩
        const backdrop = document.querySelector('.modal-backdrop');
        if (backdrop) {
            backdrop.classList.remove('show');
        }

        // 在CSS动画结束后执行清理
        setTimeout(() => {
            modalEl.style.display = 'none';
            modalEl.removeAttribute('aria-modal');
            modalEl.removeAttribute('role');

            // 检查是否还有其他模态框是打开的，如果没有，则清理body样式和背景
            const anyModalShown = document.querySelector('.modal.show');
            if (!anyModalShown) {
                if(backdrop) backdrop.remove();
                document.body.classList.remove('modal-open');
                document.body.style.overflow = '';
            }
        }, 200); // 延迟时间应与CSS过渡时间匹配

        console.log(`模态框 ${modalEl.id} 已手动隐藏。`);
    }

    /**
     * 在两个模态框之间安全地切换。
     * @param {HTMLElement} modalToHide 要隐藏的模态框。
     * @param {HTMLElement} modalToShow 要显示的模态框。
     */
    function switchModals(modalToHide, modalToShow) {
        manualHideModal(modalToHide);

        // 使用一个短暂的延迟来确保隐藏动画开始执行，然后再显示新模态框
        // 这样可以避免背景遮罩的冲突
        setTimeout(() => {
            manualShowModal(modalToShow);
        }, 250); // 这个延迟时间需要比隐藏动画的时间稍长
    }


    // ==============================================================================
    // --- 事件绑定 (一次性绑定，更高效稳定) ---
    // ==============================================================================

    // 1. 点击“选择客户”按钮，打开选择模态框
    if (openCustomerSelectModalBtn) {
        openCustomerSelectModalBtn.addEventListener('click', () => {
            manualShowModal(customerSelectModalEl);
            loadCustomers(1);
            if(customerSearchKeyword) customerSearchKeyword.focus();
        });
    }

    // 2. 在“选择客户”模态框中点击“添加新客户”
    const addNewCustomerBtn = customerSelectModalEl.querySelector('.modalAddNewCustomerBtn');
    if (addNewCustomerBtn) {
        addNewCustomerBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            switchModals(customerSelectModalEl, addCustomerFormModalEl);
        });
    }

    // 3. 在“添加客户”模态框中点击“返回选择” (依赖HTML中的 'back-to-select-btn' class)
    const backToSelectBtn = addCustomerFormModalEl.querySelector('.back-to-select-btn');
    if (backToSelectBtn) {
        backToSelectBtn.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();
            switchModals(addCustomerFormModalEl, customerSelectModalEl);
        });
    }

    // 4. 处理所有模态框的通用关闭按钮
    [customerSelectModalEl, addCustomerFormModalEl].forEach(modal => {
        const closeButtons = modal.querySelectorAll('[data-bs-dismiss="modal"], .btn-close');
        closeButtons.forEach(btn => {
            btn.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                manualHideModal(modal);
            });
        });
    });

    // 5. 搜索框输入事件
    if (customerSearchKeyword) {
        customerSearchKeyword.addEventListener('input', function() {
            clearTimeout(searchTimeout);
            searchTimeout = setTimeout(() => {
                currentPage = 1;
                loadCustomers(currentPage);
            }, 300);
        });
        customerSearchKeyword.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                currentPage = 1;
                loadCustomers(currentPage);
            }
        });
    }

    // ==============================================================================
    // --- 数据加载和渲染函数 ---
    // ==============================================================================
    async function loadCustomers(page) {
        showSpinner();
        currentPage = page; // 更新当前页状态
        try {
            const keyword = customerSearchKeyword.value.trim();
            const response = await fetch(`/customers/api/search?page=${page - 1}&size=${pageSize}&keyword=${encodeURIComponent(keyword)}`);
            if (!response.ok) {
                throw new Error(`网络错误: ${response.statusText}`);
            }
            const data = await response.json();
            renderCustomers(data.content);
            renderPagination(data);
        } catch (error) {
            console.error('加载客户数据出错:', error);
            if(customerSelectTableBody) customerSelectTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载客户列表失败，请稍后重试。</td></tr>`;
        } finally {
            hideSpinner();
        }
    }

    function renderCustomers(customers) {
        if (!customerSelectTableBody) return;
        if (!customers || customers.length === 0) {
            customerSelectTableBody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">未找到符合条件的客户</td></tr>';
            return;
        }

        customerSelectTableBody.innerHTML = customers.map(customer => `
            <tr class="customer-row" 
                data-customer-id="${customer.id}" 
                data-customer-name="${customer.customerName}" 
                data-customer-number="${customer.customerNumber || ''}"
                data-phone-number="${customer.phoneNumber || ''}"
                data-email="${customer.email || ''}">
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

        // 为新渲染的行和按钮绑定事件
        customerSelectTableBody.querySelectorAll('.customer-row').forEach(row => {
            row.addEventListener('click', function() {
                // 点击行上的任何位置都视为选择该客户
                selectCustomer({
                    id: this.dataset.customerId,
                    customerName: this.dataset.customerName,
                    customerNumber: this.dataset.customerNumber,
                    phoneNumber: this.dataset.phoneNumber,
                    email: this.dataset.email
                });
            });
        });
    }

    function renderPagination(pageData) {
        if (!customerSelectPagination) return;
        const paginationList = customerSelectPagination.querySelector('ul');
        if (!paginationList) return;

        paginationList.innerHTML = ''; // 清空旧的分页
        if (pageData.totalPages <= 1) {
            return; // 如果只有一页或没有，不显示分页
        }

        let paginationHtml = '';

        // 上一页按钮
        paginationHtml += `<li class="page-item ${pageData.first ? 'disabled' : ''}"><a class="page-link" href="#" data-page="${currentPage - 1}" aria-label="上一页"><i class="bi bi-chevron-left"></i></a></li>`;

        // 页码按钮逻辑
        const totalPages = pageData.totalPages;
        let startPage = Math.max(1, currentPage - 2);
        let endPage = Math.min(totalPages, currentPage + 2);

        if (currentPage <= 3) {
            endPage = Math.min(5, totalPages);
        }
        if (currentPage > totalPages - 3) {
            startPage = Math.max(1, totalPages - 4);
        }

        if (startPage > 1) {
            paginationHtml += `<li class="page-item"><a class="page-link" href="#" data-page="1">1</a></li>`;
            if (startPage > 2) {
                paginationHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            paginationHtml += `<li class="page-item ${i === currentPage ? 'active' : ''}"><a class="page-link" href="#" data-page="${i}">${i}</a></li>`;
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                paginationHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
            paginationHtml += `<li class="page-item"><a class="page-link" href="#" data-page="${totalPages}">${totalPages}</a></li>`;
        }

        // 下一页按钮
        paginationHtml += `<li class="page-item ${pageData.last ? 'disabled' : ''}"><a class="page-link" href="#" data-page="${currentPage + 1}" aria-label="下一页"><i class="bi bi-chevron-right"></i></a></li>`;

        paginationList.innerHTML = paginationHtml;

        // 添加分页点击事件
        paginationList.querySelectorAll('a.page-link').forEach(link => {
            // 确保只给非禁用的链接添加事件
            if (!link.parentElement.classList.contains('disabled')) {
                link.addEventListener('click', function(e) {
                    e.preventDefault();
                    const page = parseInt(this.dataset.page);
                    if (!isNaN(page)) {
                        loadCustomers(page);
                    }
                });
            }
        });
    }

    function selectCustomer(customer) {
        if (!customer) return;

        if (selectedCustomerIdInput) {
            selectedCustomerIdInput.value = customer.id;
        }

        if (selectedCustomerInfoPlaceholder) {
            selectedCustomerInfoPlaceholder.innerHTML = `
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
            selectedCustomerInfoPlaceholder.classList.remove('is-invalid-placeholder');
        }

        manualHideModal(customerSelectModalEl);
    }

    function showSpinner() {
        if (customerModalSpinner) customerModalSpinner.classList.remove('d-none');
    }
    function hideSpinner() {
        if (customerModalSpinner) customerModalSpinner.classList.add('d-none');
    }

});