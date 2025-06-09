/**
 * 客户选择功能 - 最终修复版本
 * 完全独立，避免所有冲突
 */
(function() {
    'use strict';
    
    console.log("🚀 启动最终客户选择修复");
    
    // 防止重复执行
    if (window.finalCustomerFixLoaded) {
        console.log("⚠️ 已经修复过，跳过");
        return;
    }
    window.finalCustomerFixLoaded = true;
    
    let currentPage = 0;
    let pageSize = 5;
    let searchKeyword = '';
    let modal = null;
    
    // 等待页面加载完成
    function initialize() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setupFix);
        } else {
            setupFix();
        }
    }
    
    function setupFix() {
        console.log("🔧 开始设置最终修复");
        
        // 查找按钮
        const button = document.getElementById('openCustomerSelectModalBtn');
        if (!button) {
            console.error("❌ 未找到客户选择按钮");
            return;
        }
        
        // 查找模态框
        const modalElement = document.getElementById('customerSelectModal');
        if (!modalElement) {
            console.error("❌ 未找到模态框元素");
            return;
        }
        
        // 初始化Bootstrap模态框
        modal = new bootstrap.Modal(modalElement);
        
        // 完全替换按钮事件
        const newButton = button.cloneNode(true);
        button.parentNode.replaceChild(newButton, button);
        
        newButton.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log("🎯 打开客户选择");
            openCustomerModal();
        });
        
        console.log("✅ 最终修复设置完成");
    }
    
    function openCustomerModal() {
        console.log("🔧 开始打开客户模态框");
        
        // 清空搜索
        const searchInput = document.getElementById('modalCustomerSearchInput');
        if (searchInput) {
            searchInput.value = '';
        }
        searchKeyword = '';
        
        // 加载数据并显示模态框
        loadCustomerData(0, '')
            .then(() => {
                modal.show();
                setupSearchEvents();
                console.log("✅ 模态框显示成功");
            })
            .catch(error => {
                console.error("❌ 打开模态框失败:", error);
                alert('无法加载客户数据');
            });
    }
    
    function loadCustomerData(page, keyword) {
        console.log(`📡 加载数据: 页面${page}, 关键词"${keyword}"`);
        
        currentPage = page;
        searchKeyword = keyword;
        
        const spinner = document.getElementById('customerModalSpinner');
        const tbody = document.querySelector('#customerTableModal tbody');
        
        // 显示加载状态
        if (spinner) spinner.style.display = 'block';
        if (tbody) tbody.innerHTML = '<tr><td colspan="5" class="text-center">加载中...</td></tr>';
        
        const url = `/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${pageSize}&sort=customerName,asc`;
        
        return fetch(url)
            .then(response => {
                console.log(`📡 响应状态: ${response.status}`);
                return response.json();
            })
            .then(data => {
                console.log("✅ 数据加载成功:", data);
                renderCustomerTable(data.content || []);
                renderPagination(data);
                return data;
            })
            .catch(error => {
                console.error("❌ 加载失败:", error);
                if (tbody) {
                    tbody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载失败: ${error.message}</td></tr>`;
                }
                throw error;
            })
            .finally(() => {
                if (spinner) spinner.style.display = 'none';
            });
    }
    
    function renderCustomerTable(customers) {
        const tbody = document.querySelector('#customerTableModal tbody');
        if (!tbody) return;
        
        if (!customers || customers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center text-muted">没有找到客户记录</td></tr>';
            return;
        }
        
        tbody.innerHTML = '';
        
        customers.forEach(customer => {
            const row = document.createElement('tr');
            row.className = 'customer-row-final';
            row.style.cursor = 'pointer';
            
            row.innerHTML = `
                <td>${customer.customerNumber || 'N/A'}</td>
                <td><strong>${customer.customerName}</strong></td>
                <td>${customer.phoneNumber || 'N/A'}</td>
                <td>${customer.email || 'N/A'}</td>
                <td>
                    <button class="btn btn-sm btn-success select-btn-final" type="button">
                        <i class="bi bi-check"></i> 选择
                    </button>
                </td>
            `;
            
            // 整行点击
            row.addEventListener('click', function(e) {
                if (!e.target.closest('.select-btn-final')) {
                    selectCustomer(customer);
                }
            });
            
            // 按钮点击
            const selectBtn = row.querySelector('.select-btn-final');
            selectBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                selectCustomer(customer);
            });
            
            tbody.appendChild(row);
        });
        
        console.log(`✅ 渲染完成: ${customers.length} 条记录`);
    }
    
    function selectCustomer(customer) {
        console.log("🎯 选择客户:", customer.customerName);
        
        // 设置隐藏字段
        const hiddenInput = document.getElementById('selectedCustomerId');
        if (hiddenInput) {
            hiddenInput.value = customer.id;
        }
        
        // 更新显示
        const display = document.getElementById('selectedCustomerInfoPlaceholder');
        if (display) {
            display.innerHTML = `
                <span class="badge bg-success">已选择</span>
                <strong>${customer.customerName}</strong>
                <small class="text-muted">(${customer.customerNumber || 'N/A'})</small>
            `;
            display.style.backgroundColor = '#d1edff';
            display.classList.remove('is-invalid-placeholder');
        }
        
        // 更新详细信息
        updateDetailsCard(customer);
        
        // 关闭模态框
        if (modal) {
            modal.hide();
        }
        
        // 清除验证错误
        if (hiddenInput) {
            hiddenInput.classList.remove('is-invalid');
            hiddenInput.dispatchEvent(new Event('change'));
        }
        
        const errorDiv = document.getElementById('selectedCustomerIdClientFeedback');
        if (errorDiv) {
            errorDiv.style.display = 'none';
        }
        
        console.log("✅ 客户选择完成");
        showSuccessAlert(`已选择客户: ${customer.customerName}`);
    }
    
    function updateDetailsCard(customer) {
        const elements = {
            name: document.getElementById('selectedCustomerNameText'),
            number: document.getElementById('selectedCustomerNumberText'),
            phone: document.getElementById('selectedCustomerPhoneText'),
            email: document.getElementById('selectedCustomerEmailText'),
            address: document.getElementById('selectedCustomerAddressText')
        };
        
        Object.keys(elements).forEach(key => {
            const element = elements[key];
            if (element) {
                const field = key === 'number' ? 'customerNumber' : 
                            key === 'name' ? 'customerName' :
                            key === 'phone' ? 'phoneNumber' : key;
                element.textContent = customer[field] || 'N/A';
            }
        });
        
        const card = document.getElementById('selectedCustomerDetailsCard');
        if (card) {
            card.style.display = 'block';
        }
    }
    
    function setupSearchEvents() {
        const searchInput = document.getElementById('modalCustomerSearchInput');
        const searchBtn = document.getElementById('modalSearchCustomerBtn');
        
        if (searchBtn) {
            const newBtn = searchBtn.cloneNode(true);
            searchBtn.parentNode.replaceChild(newBtn, searchBtn);
            
            newBtn.addEventListener('click', function() {
                const keyword = searchInput ? searchInput.value.trim() : '';
                loadCustomerData(0, keyword);
            });
        }
        
        if (searchInput) {
            const newInput = searchInput.cloneNode(true);
            searchInput.parentNode.replaceChild(newInput, searchInput);
            
            newInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    loadCustomerData(0, this.value.trim());
                }
            });
        }
    }
    
    function renderPagination(pageData) {
        const pagination = document.getElementById('customerModalPagination');
        if (!pagination) return;
        
        if (!pageData || pageData.totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }
        
        const { totalPages, number: current, first, last } = pageData;
        pagination.innerHTML = '';
        
        // 上一页
        if (!first) {
            const prev = createPageItem('上一页', () => loadCustomerData(current - 1, searchKeyword));
            pagination.appendChild(prev);
        }
        
        // 页面信息
        const info = createPageItem(`${current + 1}/${totalPages}`, null, true);
        pagination.appendChild(info);
        
        // 下一页
        if (!last) {
            const next = createPageItem('下一页', () => loadCustomerData(current + 1, searchKeyword));
            pagination.appendChild(next);
        }
    }
    
    function createPageItem(text, handler, disabled = false) {
        const li = document.createElement('li');
        li.className = `page-item ${disabled ? 'disabled' : ''}`;
        
        const a = document.createElement('a');
        a.className = 'page-link';
        a.href = '#';
        a.textContent = text;
        
        if (handler && !disabled) {
            a.addEventListener('click', function(e) {
                e.preventDefault();
                handler();
            });
        }
        
        li.appendChild(a);
        return li;
    }
    
    function showSuccessAlert(message) {
        const alert = document.createElement('div');
        alert.className = 'alert alert-success alert-dismissible fade show position-fixed';
        alert.style.cssText = 'top: 20px; right: 20px; z-index: 9999;';
        alert.innerHTML = `
            <i class="bi bi-check-circle"></i> ${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        
        document.body.appendChild(alert);
        
        setTimeout(() => {
            if (alert.parentNode) {
                alert.parentNode.removeChild(alert);
            }
        }, 3000);
    }
    
    // 启动
    initialize();
    
})(); 