/**
 * 客户选择功能修复脚本
 * 解决起草合同页面无法选择客户的问题
 */

console.log("�� 客户选择修复脚本加载");

// 避免重复声明的全局变量 - 使用window对象
window.customerFixGlobals = window.customerFixGlobals || {
    currentCustomerPage: 0,
    CUSTOMER_PAGE_SIZE: 5,
    customerSearchKeyword: '',
    customerSelectModal: null,
    isInitialized: false
};

// 主修复函数
function fixCustomerSelection() {
    if (window.customerFixGlobals.isInitialized) {
        console.log("🔧 客户选择功能已经初始化过，跳过重复初始化");
        return;
    }

    console.log("🔧 开始修复客户选择功能...");
    
    // 获取所有必要的DOM元素
    const elements = {
        openButton: document.getElementById('openCustomerSelectModalBtn'),
        modalElement: document.getElementById('customerSelectModal'),
        searchInput: document.getElementById('modalCustomerSearchInput'),
        searchButton: document.getElementById('modalSearchCustomerBtn'),
        tableBody: document.querySelector('#customerTableModal tbody'),
        pagination: document.getElementById('customerModalPagination'),
        spinner: document.getElementById('customerModalSpinner'),
        alertPlaceholder: document.getElementById('customerModalAlertPlaceholder'),
        selectedIdInput: document.getElementById('selectedCustomerId'),
        infoPlaceholder: document.getElementById('selectedCustomerInfoPlaceholder'),
        detailsCard: document.getElementById('selectedCustomerDetailsCard')
    };

    // 检查必要元素是否存在
    if (!elements.openButton || !elements.modalElement) {
        console.error("❌ 客户选择相关元素缺失");
        return false;
    }

    // 清除所有现有的事件监听器
    const newOpenButton = elements.openButton.cloneNode(true);
    elements.openButton.parentNode.replaceChild(newOpenButton, elements.openButton);
    elements.openButton = newOpenButton;

    // 初始化Bootstrap模态框
    try {
        window.customerFixGlobals.customerSelectModal = new bootstrap.Modal(elements.modalElement);
        console.log("✅ Bootstrap模态框初始化成功");
    } catch (error) {
        console.error("❌ Bootstrap模态框初始化失败:", error);
        return false;
    }

    // 绑定打开模态框事件
    elements.openButton.addEventListener('click', function() {
        console.log("🔧 客户选择按钮被点击（修复版）");
        openCustomerModalFixed(elements);
    });

    // 绑定搜索功能
    if (elements.searchButton && elements.searchInput) {
        // 清除现有事件监听器
        const newSearchButton = elements.searchButton.cloneNode(true);
        const newSearchInput = elements.searchInput.cloneNode(true);
        elements.searchButton.parentNode.replaceChild(newSearchButton, elements.searchButton);
        elements.searchInput.parentNode.replaceChild(newSearchInput, elements.searchInput);
        
        newSearchButton.addEventListener('click', function() {
            const keyword = newSearchInput.value.trim();
            console.log("🔍 执行客户搜索:", keyword);
            searchCustomersFixed(keyword, elements);
        });

        newSearchInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                const keyword = this.value.trim();
                console.log("🔍 回车搜索客户:", keyword);
                searchCustomersFixed(keyword, elements);
            }
        });
    }

    window.customerFixGlobals.isInitialized = true;
    console.log("✅ 客户选择功能修复完成");
    return true;
}

// 打开客户选择模态框
function openCustomerModalFixed(elements) {
    console.log("🔧 打开客户选择模态框（修复版）");
    
    // 清空搜索框
    if (elements.searchInput) {
        elements.searchInput.value = '';
    }
    window.customerFixGlobals.customerSearchKeyword = '';
    
    // 测试网络连接并加载数据
    testNetworkConnectionFixed()
        .then(isConnected => {
            if (isConnected) {
                loadCustomerDataFixed(0, '', elements);
                window.customerFixGlobals.customerSelectModal.show();
            } else {
                showErrorFixed("网络连接异常，无法加载客户数据", elements);
            }
        })
        .catch(error => {
            console.error("❌ 网络测试失败:", error);
            showErrorFixed("网络连接测试失败: " + error.message, elements);
        });
}

// 搜索客户
function searchCustomersFixed(keyword, elements) {
    window.customerFixGlobals.customerSearchKeyword = keyword;
    loadCustomerDataFixed(0, keyword, elements);
}

// 测试网络连接
function testNetworkConnectionFixed() {
    console.log("🔧 测试网络连接...");
    
    return fetch('/customers/api/search?keyword=&page=0&size=1', {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json',
            'X-Requested-With': 'XMLHttpRequest'
        },
        credentials: 'same-origin'
    })
    .then(response => {
        console.log("📡 网络响应状态:", response.status);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }
        return response.json();
    })
    .then(data => {
        console.log("✅ 网络连接正常");
        return true;
    })
    .catch(error => {
        console.error("❌ 网络连接失败:", error);
        return false;
    });
}

// 加载客户数据
function loadCustomerDataFixed(page, keyword, elements) {
    console.log(`🔧 加载客户数据（修复版） - 页码: ${page}, 关键词: "${keyword}"`);
    
    window.customerFixGlobals.currentCustomerPage = page;
    const searchUrl = `/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${window.customerFixGlobals.CUSTOMER_PAGE_SIZE}&sort=customerName,asc`;
    
    // 显示加载状态
    showLoadingFixed(elements);
    
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
        console.log("✅ 客户数据加载成功（修复版）:", pageData);
        renderCustomerTableFixed(pageData.content || [], elements);
        renderPaginationFixed(pageData, elements);
        clearErrorsFixed(elements);
    })
    .catch(error => {
        console.error('❌ 加载客户数据失败:', error);
        showErrorFixed('加载客户数据失败: ' + error.message, elements);
        renderCustomerTableFixed([], elements);
        renderPaginationFixed(null, elements);
    })
    .finally(() => {
        hideLoadingFixed(elements);
    });
}

// 渲染客户表格
function renderCustomerTableFixed(customers, elements) {
    if (!elements.tableBody) return;
    
    elements.tableBody.innerHTML = '';
    
    if (!customers || customers.length === 0) {
        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-muted py-4">
                    <i class="bi bi-people me-2"></i>未找到匹配的客户记录
                </td>
            </tr>
        `;
        return;
    }
    
    customers.forEach(customer => {
        const row = document.createElement('tr');
        row.className = 'customer-row-fixed';
        row.style.cursor = 'pointer';
        row.innerHTML = `
            <td>${customer.customerNumber || 'N/A'}</td>
            <td>${customer.customerName}</td>
            <td>${customer.phoneNumber || 'N/A'}</td>
            <td>${customer.email || 'N/A'}</td>
            <td class="text-center">
                <button type="button" class="btn btn-sm btn-primary select-customer-btn-fixed">
                    <i class="bi bi-check-circle-fill me-1"></i>选择
                </button>
            </td>
        `;
        
        // 绑定选择事件（使用事件委托避免冲突）
        const selectBtn = row.querySelector('.select-customer-btn-fixed');
        selectBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            console.log("🔧 客户选择按钮点击（修复版）:", customer.customerName);
            selectCustomerFixed(customer, elements);
        });
        
        // 点击行也可以选择
        row.addEventListener('click', function(e) {
            if (!e.target.classList.contains('select-customer-btn-fixed')) {
                console.log("🔧 客户行点击（修复版）:", customer.customerName);
                selectCustomerFixed(customer, elements);
            }
        });
        
        elements.tableBody.appendChild(row);
    });
    
    console.log(`✅ 已渲染 ${customers.length} 个客户记录`);
}

// 选择客户
function selectCustomerFixed(customer, elements) {
    console.log("✅ 选择客户（修复版）:", customer.customerName);
    
    // 设置隐藏字段值
    if (elements.selectedIdInput) {
        elements.selectedIdInput.value = customer.id;
        console.log("🔧 设置客户ID:", customer.id);
    }
    
    // 更新显示信息
    if (elements.infoPlaceholder) {
        elements.infoPlaceholder.innerHTML = `
            <strong class="text-success">${customer.customerName}</strong> 
            <small class="text-muted">(编号: ${customer.customerNumber || 'N/A'})</small>
        `;
        elements.infoPlaceholder.classList.remove('is-invalid-placeholder');
        console.log("🔧 更新客户显示信息");
    }
    
    // 更新详细信息卡片
    if (elements.detailsCard) {
        const nameEl = document.getElementById('selectedCustomerNameText');
        const numberEl = document.getElementById('selectedCustomerNumberText');
        const phoneEl = document.getElementById('selectedCustomerPhoneText');
        const emailEl = document.getElementById('selectedCustomerEmailText');
        const addressEl = document.getElementById('selectedCustomerAddressText');
        
        if (nameEl) nameEl.textContent = customer.customerName || 'N/A';
        if (numberEl) numberEl.textContent = customer.customerNumber || 'N/A';
        if (phoneEl) phoneEl.textContent = customer.phoneNumber || 'N/A';
        if (emailEl) emailEl.textContent = customer.email || 'N/A';
        if (addressEl) addressEl.textContent = customer.address || 'N/A';
        
        elements.detailsCard.style.display = 'block';
        console.log("🔧 更新客户详细信息卡片");
    }
    
    // 隐藏模态框
    if (window.customerFixGlobals.customerSelectModal) {
        window.customerFixGlobals.customerSelectModal.hide();
        console.log("🔧 关闭客户选择模态框");
    }
    
    // 触发验证
    if (elements.selectedIdInput) {
        elements.selectedIdInput.dispatchEvent(new Event('change'));
        elements.selectedIdInput.classList.remove('is-invalid');
    }
    
    // 隐藏错误提示
    const errorFeedback = document.getElementById('selectedCustomerIdClientFeedback');
    if (errorFeedback) {
        errorFeedback.style.display = 'none';
    }
    
    console.log("✅ 客户选择完成（修复版）");
}

// 渲染分页
function renderPaginationFixed(pageData, elements) {
    if (!elements.pagination) return;
    
    elements.pagination.innerHTML = '';
    
    if (!pageData || pageData.totalPages <= 1) return;
    
    const { totalPages, number: currentPageIdx, first, last } = pageData;
    
    // 上一页
    if (!first) {
        const prevLi = document.createElement('li');
        prevLi.className = 'page-item';
        const prevA = document.createElement('a');
        prevA.className = 'page-link';
        prevA.href = '#';
        prevA.innerHTML = '&laquo; 上一页';
        prevA.addEventListener('click', (e) => {
            e.preventDefault();
            loadCustomerDataFixed(currentPageIdx - 1, window.customerFixGlobals.customerSearchKeyword, elements);
        });
        prevLi.appendChild(prevA);
        elements.pagination.appendChild(prevLi);
    }
    
    // 当前页信息
    const currentLi = document.createElement('li');
    currentLi.className = 'page-item disabled';
    const currentA = document.createElement('a');
    currentA.className = 'page-link';
    currentA.textContent = `第 ${currentPageIdx + 1} / ${totalPages} 页`;
    currentLi.appendChild(currentA);
    elements.pagination.appendChild(currentLi);
    
    // 下一页
    if (!last) {
        const nextLi = document.createElement('li');
        nextLi.className = 'page-item';
        const nextA = document.createElement('a');
        nextA.className = 'page-link';
        nextA.href = '#';
        nextA.innerHTML = '下一页 &raquo;';
        nextA.addEventListener('click', (e) => {
            e.preventDefault();
            loadCustomerDataFixed(currentPageIdx + 1, window.customerFixGlobals.customerSearchKeyword, elements);
        });
        nextLi.appendChild(nextA);
        elements.pagination.appendChild(nextLi);
    }
}

// 显示加载状态
function showLoadingFixed(elements) {
    if (elements.spinner) {
        elements.spinner.style.display = 'block';
    }
    if (elements.tableBody) {
        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center py-4">
                    <div class="spinner-border spinner-border-sm me-2" role="status"></div>
                    正在加载客户数据...
                </td>
            </tr>
        `;
    }
}

// 隐藏加载状态
function hideLoadingFixed(elements) {
    if (elements.spinner) {
        elements.spinner.style.display = 'none';
    }
}

// 显示错误信息
function showErrorFixed(message, elements) {
    console.error("❌ 显示错误:", message);
    
    if (elements.tableBody) {
        elements.tableBody.innerHTML = `
            <tr>
                <td colspan="5" class="text-center text-danger py-4">
                    <i class="bi bi-exclamation-triangle me-2"></i>
                    ${message}
                    <br><small class="text-muted">请检查网络连接或联系系统管理员</small>
                </td>
            </tr>
        `;
    }
    
    if (elements.alertPlaceholder) {
        elements.alertPlaceholder.innerHTML = `
            <div class="alert alert-danger alert-dismissible fade show" role="alert">
                <i class="bi bi-exclamation-triangle-fill me-2"></i>${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
            </div>
        `;
    }
}

// 清除错误信息
function clearErrorsFixed(elements) {
    if (elements.alertPlaceholder) {
        elements.alertPlaceholder.innerHTML = '';
    }
}

// 等待DOM加载完成后初始化
document.addEventListener('DOMContentLoaded', function() {
    console.log("🚀 客户选择修复脚本开始执行");
    
    // 延迟初始化，确保所有元素都已加载
    setTimeout(() => {
        const success = fixCustomerSelection();
        if (success) {
            console.log("✅ 客户选择功能修复完成");
        } else {
            console.error("❌ 客户选择功能修复失败");
        }
    }, 1500); // 增加延迟时间，避免与其他脚本冲突
});

// 全局错误处理
window.addEventListener('error', function(event) {
    if (event.message && event.message.includes('chrome')) {
        console.warn('🔧 Chrome扩展相关错误已忽略:', event.message);
        event.preventDefault();
        return false;
    }
}); 