/**
 * 客户选择功能最终修复版本
 * 完全独立运行，避免所有冲突
 */

(function() {
    'use strict';
    
    // 防止重复执行
    if (window.customerSelectionFixed) {
        console.log("🔧 客户选择已修复，跳过重复执行");
        return;
    }
    
    console.log("🚀 启动客户选择最终修复方案");
    
    // 全局状态
    let currentPage = 0;
    let pageSize = 5;
    let searchKeyword = '';
    let modal = null;
    
    // 等待DOM完全加载
    function waitForDOM() {
        return new Promise((resolve) => {
            if (document.readyState === 'loading') {
                document.addEventListener('DOMContentLoaded', resolve);
            } else {
                resolve();
            }
        });
    }
    
    // 等待元素出现
    function waitForElement(selector, timeout = 5000) {
        return new Promise((resolve, reject) => {
            const element = document.querySelector(selector);
            if (element) {
                resolve(element);
                return;
            }
            
            let timeElapsed = 0;
            const interval = setInterval(() => {
                const element = document.querySelector(selector);
                if (element) {
                    clearInterval(interval);
                    resolve(element);
                } else {
                    timeElapsed += 100;
                    if (timeElapsed >= timeout) {
                        clearInterval(interval);
                        reject(new Error(`元素 ${selector} 在 ${timeout}ms 内未找到`));
                    }
                }
            }, 100);
        });
    }
    
    // 主修复函数
    async function fixCustomerSelection() {
        try {
            console.log("🔧 开始最终修复...");
            
            await waitForDOM();
            
            // 等待必要元素
            const openButton = await waitForElement('#openCustomerSelectModalBtn');
            const modalElement = await waitForElement('#customerSelectModal');
            
            console.log("✅ 找到必要元素");
            
            // 初始化模态框
            modal = new bootstrap.Modal(modalElement);
            console.log("✅ 模态框初始化成功");
            
            // 完全替换按钮事件
            replaceButtonEvents(openButton);
            
            // 标记已修复
            window.customerSelectionFixed = true;
            console.log("✅ 客户选择功能最终修复完成");
            
        } catch (error) {
            console.error("❌ 最终修复失败:", error);
        }
    }
    
    // 替换按钮事件
    function replaceButtonEvents(openButton) {
        // 克隆按钮以移除所有事件监听器
        const newButton = openButton.cloneNode(true);
        openButton.parentNode.replaceChild(newButton, openButton);
        
        // 绑定新的点击事件
        newButton.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log("🔧 客户选择按钮点击（最终修复版）");
            openModal();
        });
        
        console.log("✅ 按钮事件替换完成");
    }
    
    // 打开模态框
    function openModal() {
        console.log("🔧 打开客户选择模态框（最终修复版）");
        
        // 清空搜索框
        const searchInput = document.getElementById('modalCustomerSearchInput');
        if (searchInput) {
            searchInput.value = '';
        }
        searchKeyword = '';
        
        // 加载客户数据
        loadCustomers(0, '')
            .then(() => {
                modal.show();
                console.log("✅ 模态框已显示");
                
                // 绑定搜索事件
                bindSearchEvents();
            })
            .catch(error => {
                console.error("❌ 加载客户数据失败:", error);
                alert('无法加载客户数据，请检查网络连接');
            });
    }
    
    // 绑定搜索事件
    function bindSearchEvents() {
        const searchInput = document.getElementById('modalCustomerSearchInput');
        const searchButton = document.getElementById('modalSearchCustomerBtn');
        
        if (searchButton) {
            // 移除旧事件
            const newSearchButton = searchButton.cloneNode(true);
            searchButton.parentNode.replaceChild(newSearchButton, searchButton);
            
            newSearchButton.addEventListener('click', function() {
                const keyword = searchInput ? searchInput.value.trim() : '';
                console.log("🔍 搜索客户:", keyword);
                loadCustomers(0, keyword);
            });
        }
        
        if (searchInput) {
            // 移除旧事件
            const newSearchInput = searchInput.cloneNode(true);
            searchInput.parentNode.replaceChild(newSearchInput, searchInput);
            
            newSearchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    const keyword = this.value.trim();
                    console.log("🔍 回车搜索:", keyword);
                    loadCustomers(0, keyword);
                }
            });
        }
    }
    
    // 加载客户数据
    function loadCustomers(page, keyword) {
        console.log(`🔧 加载客户数据 - 页码: ${page}, 关键词: "${keyword}"`);
        
        currentPage = page;
        searchKeyword = keyword;
        
        const spinner = document.getElementById('customerModalSpinner');
        const tableBody = document.querySelector('#customerTableModal tbody');
        const alertDiv = document.getElementById('customerModalAlertPlaceholder');
        
        // 显示加载状态
        if (spinner) spinner.style.display = 'block';
        if (tableBody) tableBody.innerHTML = '<tr><td colspan="5" class="text-center">正在加载客户数据...</td></tr>';
        if (alertDiv) alertDiv.innerHTML = '';
        
        const url = `/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${pageSize}&sort=customerName,asc`;
        
        return fetch(url, {
            method: 'GET',
            headers: {
                'Accept': 'application/json',
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            console.log(`📡 API响应: ${response.status}`);
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("✅ 客户数据加载成功（最终版）:", data);
            renderCustomers(data.content || []);
            renderPagination(data);
            return data;
        })
        .catch(error => {
            console.error('❌ 加载客户数据失败:', error);
            if (tableBody) {
                tableBody.innerHTML = `
                    <tr>
                        <td colspan="5" class="text-center text-danger py-3">
                            <i class="bi bi-exclamation-triangle me-2"></i>
                            加载失败: ${error.message}
                            <br><small>请检查网络连接或联系管理员</small>
                        </td>
                    </tr>
                `;
            }
            throw error;
        })
        .finally(() => {
            if (spinner) spinner.style.display = 'none';
        });
    }
    
    // 渲染客户列表
    function renderCustomers(customers) {
        const tableBody = document.querySelector('#customerTableModal tbody');
        if (!tableBody) return;
        
        tableBody.innerHTML = '';
        
        if (!customers || customers.length === 0) {
            tableBody.innerHTML = `
                <tr>
                    <td colspan="5" class="text-center text-muted py-3">
                        <i class="bi bi-people me-2"></i>未找到匹配的客户记录
                    </td>
                </tr>
            `;
            return;
        }
        
        customers.forEach(customer => {
            const row = document.createElement('tr');
            row.className = 'customer-row-final';
            row.style.cursor = 'pointer';
            
            row.innerHTML = `
                <td class="customer-number">${customer.customerNumber || 'N/A'}</td>
                <td class="customer-name"><strong>${customer.customerName}</strong></td>
                <td class="customer-phone">${customer.phoneNumber || 'N/A'}</td>
                <td class="customer-email">${customer.email || 'N/A'}</td>
                <td class="text-center">
                    <button type="button" class="btn btn-sm btn-success select-customer-final" data-customer-id="${customer.id}">
                        <i class="bi bi-check-circle-fill me-1"></i>选择此客户
                    </button>
                </td>
            `;
            
            // 绑定选择事件 - 使用事件委托
            row.addEventListener('click', function(e) {
                if (!e.target.closest('.select-customer-final')) {
                    // 点击行的其他地方也可以选择
                    selectCustomer(customer);
                }
            });
            
            // 绑定按钮点击事件
            const selectBtn = row.querySelector('.select-customer-final');
            selectBtn.addEventListener('click', function(e) {
                e.stopPropagation();
                console.log("🎯 选择客户按钮点击:", customer.customerName);
                selectCustomer(customer);
            });
            
            tableBody.appendChild(row);
        });
        
        console.log(`✅ 渲染完成：${customers.length} 个客户记录`);
    }
    
    // 选择客户
    function selectCustomer(customer) {
        console.log("✅ 选择客户（最终版）:", customer);
        
        const selectedIdInput = document.getElementById('selectedCustomerId');
        const infoPlaceholder = document.getElementById('selectedCustomerInfoPlaceholder');
        const detailsCard = document.getElementById('selectedCustomerDetailsCard');
        
        // 设置隐藏字段
        if (selectedIdInput) {
            selectedIdInput.value = customer.id;
            console.log("🔧 客户ID已设置:", customer.id);
        }
        
        // 更新显示信息
        if (infoPlaceholder) {
            infoPlaceholder.innerHTML = `
                <span class="badge bg-success me-2">已选择</span>
                <strong>${customer.customerName}</strong> 
                <small class="text-muted">(${customer.customerNumber || 'N/A'})</small>
            `;
            infoPlaceholder.classList.remove('is-invalid-placeholder');
            infoPlaceholder.style.backgroundColor = '#d1edff';
            console.log("🔧 显示信息已更新");
        }
        
        // 更新详情卡片
        if (detailsCard) {
            updateDetailsCard(customer, detailsCard);
        }
        
        // 关闭模态框
        if (modal) {
            modal.hide();
            console.log("🔧 模态框已关闭");
        }
        
        // 触发表单验证事件
        if (selectedIdInput) {
            selectedIdInput.dispatchEvent(new Event('change', { bubbles: true }));
            selectedIdInput.classList.remove('is-invalid');
        }
        
        // 隐藏错误提示
        const errorFeedback = document.getElementById('selectedCustomerIdClientFeedback');
        if (errorFeedback) {
            errorFeedback.style.display = 'none';
        }
        
        console.log("🎉 客户选择成功完成！");
        
        // 显示成功提示
        showSuccessMessage(`已成功选择客户：${customer.customerName}`);
    }
    
    // 更新详情卡片
    function updateDetailsCard(customer, detailsCard) {
        const elements = {
            name: document.getElementById('selectedCustomerNameText'),
            number: document.getElementById('selectedCustomerNumberText'),
            phone: document.getElementById('selectedCustomerPhoneText'),
            email: document.getElementById('selectedCustomerEmailText'),
            address: document.getElementById('selectedCustomerAddressText')
        };
        
        if (elements.name) elements.name.textContent = customer.customerName || 'N/A';
        if (elements.number) elements.number.textContent = customer.customerNumber || 'N/A';
        if (elements.phone) elements.phone.textContent = customer.phoneNumber || 'N/A';
        if (elements.email) elements.email.textContent = customer.email || 'N/A';
        if (elements.address) elements.address.textContent = customer.address || 'N/A';
        
        detailsCard.style.display = 'block';
        console.log("🔧 详情卡片已更新");
    }
    
    // 渲染分页
    function renderPagination(pageData) {
        const pagination = document.getElementById('customerModalPagination');
        if (!pagination || !pageData || pageData.totalPages <= 1) return;
        
        pagination.innerHTML = '';
        const { totalPages, number: currentPageIdx, first, last } = pageData;
        
        // 上一页
        if (!first) {
            const prevItem = createPaginationItem('&laquo; 上一页', () => {
                loadCustomers(currentPageIdx - 1, searchKeyword);
            });
            pagination.appendChild(prevItem);
        }
        
        // 当前页信息
        const currentItem = createPaginationItem(
            `第 ${currentPageIdx + 1} / ${totalPages} 页`, 
            null, 
            true
        );
        pagination.appendChild(currentItem);
        
        // 下一页
        if (!last) {
            const nextItem = createPaginationItem('下一页 &raquo;', () => {
                loadCustomers(currentPageIdx + 1, searchKeyword);
            });
            pagination.appendChild(nextItem);
        }
    }
    
    // 创建分页项
    function createPaginationItem(text, clickHandler, disabled = false) {
        const li = document.createElement('li');
        li.className = `page-item ${disabled ? 'disabled' : ''}`;
        
        const a = document.createElement('a');
        a.className = 'page-link';
        a.href = '#';
        a.innerHTML = text;
        
        if (clickHandler && !disabled) {
            a.addEventListener('click', function(e) {
                e.preventDefault();
                clickHandler();
            });
        }
        
        li.appendChild(a);
        return li;
    }
    
    // 显示成功消息
    function showSuccessMessage(message) {
        const alertDiv = document.createElement('div');
        alertDiv.className = 'alert alert-success alert-dismissible fade show position-fixed';
        alertDiv.style.cssText = 'top: 20px; right: 20px; z-index: 9999; min-width: 300px;';
        alertDiv.innerHTML = `
            <i class="bi bi-check-circle-fill me-2"></i>${message}
            <button type="button" class="btn-close" data-bs-dismiss="alert"></button>
        `;
        
        document.body.appendChild(alertDiv);
        
        // 3秒后自动移除
        setTimeout(() => {
            if (alertDiv.parentNode) {
                alertDiv.parentNode.removeChild(alertDiv);
            }
        }, 3000);
    }
    
    // 启动修复
    fixCustomerSelection();
    
})();

console.log("�� 客户选择最终修复脚本已加载"); 