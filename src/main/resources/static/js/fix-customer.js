// 客户选择功能 - 极简修复版本
console.log("🚀 客户选择极简修复启动");

(function() {
    'use strict';
    
    if (window.customerFixedSimple) {
        console.log("已修复，跳过");
        return;
    }
    window.customerFixedSimple = true;
    
    let modal = null;
    
    // 等待页面加载
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 500); // 延迟以确保所有脚本加载完成
        }
    }
    
    function setup() {
        console.log("🔧 开始极简修复");
        
        const button = document.getElementById('openCustomerSelectModalBtn');
        const modalEl = document.getElementById('customerSelectModal');
        
        if (!button || !modalEl) {
            console.error("❌ 找不到必要元素");
            return;
        }
        
        // 简单的模态框初始化
        try {
            modal = new bootstrap.Modal(modalEl);
        } catch (e) {
            console.warn("Bootstrap模态框初始化失败:", e);
        }
        
        // 完全替换按钮
        replaceButton(button);
        
        console.log("✅ 极简修复完成");
    }
    
    function replaceButton(oldButton) {
        const newButton = document.createElement('button');
        newButton.className = oldButton.className;
        newButton.id = oldButton.id;
        newButton.type = 'button';
        newButton.style.cssText = oldButton.style.cssText || 'min-width: 110px;';
        newButton.innerHTML = oldButton.innerHTML;
        
        // 替换按钮
        oldButton.parentNode.replaceChild(newButton, oldButton);
        
        // 绑定点击事件
        newButton.onclick = function(e) {
            e.preventDefault();
            e.stopPropagation();
            console.log("🎯 极简版 - 打开客户选择");
            openCustomerModal();
        };
        
        console.log("🔧 按钮已替换并绑定事件");
    }
    
    function openCustomerModal() {
        console.log("🔧 开始加载客户数据");
        
        // 直接加载数据到表格
        loadCustomers()
            .then(() => {
                console.log("🔧 数据加载完成，显示模态框");
                showModal();
            })
            .catch(error => {
                console.error("❌ 加载失败:", error);
                alert('加载客户数据失败，请重试');
            });
    }
    
    function loadCustomers() {
        const tbody = document.querySelector('#customerTableModal tbody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">加载中...</td></tr>';
        }
        
        return fetch('/customers/api/search?page=0&size=5&sort=customerName,asc')
            .then(response => response.json())
            .then(data => {
                console.log("✅ 客户数据:", data);
                renderSimpleTable(data.content || []);
                return data;
            });
    }
    
    function renderSimpleTable(customers) {
        const tbody = document.querySelector('#customerTableModal tbody');
        if (!tbody) {
            console.error("❌ 找不到表格body");
            return;
        }
        
        // 清空表格
        tbody.innerHTML = '';
        
        if (!customers || customers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">没有客户数据</td></tr>';
            return;
        }
        
        // 渲染客户
        customers.forEach((customer, index) => {
            const tr = document.createElement('tr');
            tr.style.cursor = 'pointer';
            tr.innerHTML = `
                <td>${customer.customerNumber || 'N/A'}</td>
                <td><strong>${customer.customerName}</strong></td>
                <td>${customer.phoneNumber || 'N/A'}</td>
                <td>${customer.email || 'N/A'}</td>
                <td>
                    <button class="btn btn-sm btn-success" onclick="window.selectCustomerSimple(${customer.id}, '${customer.customerName}', '${customer.customerNumber || 'N/A'}')">
                        选择
                    </button>
                </td>
            `;
            
            // 行点击事件
            tr.onclick = function(e) {
                if (!e.target.closest('button')) {
                    console.log("🎯 点击行选择:", customer.customerName);
                    window.selectCustomerSimple(customer.id, customer.customerName, customer.customerNumber || 'N/A');
                }
            };
            
            tbody.appendChild(tr);
        });
        
        console.log(`✅ 极简渲染完成: ${customers.length} 条记录`);
    }
    
    function showModal() {
        const modalEl = document.getElementById('customerSelectModal');
        
        if (modal) {
            try {
                modal.show();
                console.log("✅ Bootstrap模态框显示成功");
            } catch (e) {
                console.warn("Bootstrap显示失败，手动显示:", e);
                manualShow(modalEl);
            }
        } else {
            manualShow(modalEl);
        }
        
        // 设置简单搜索
        setupSimpleSearch();
    }
    
    function manualShow(modalEl) {
        if (modalEl) {
            modalEl.style.display = 'block';
            modalEl.classList.add('show');
            document.body.classList.add('modal-open');
            
            // 添加背景
            let backdrop = document.querySelector('.modal-backdrop');
            if (!backdrop) {
                backdrop = document.createElement('div');
                backdrop.className = 'modal-backdrop fade show';
                document.body.appendChild(backdrop);
            }
            
            console.log("✅ 手动显示模态框成功");
        }
    }
    
    function setupSimpleSearch() {
        const searchBtn = document.getElementById('modalSearchCustomerBtn');
        const searchInput = document.getElementById('modalCustomerSearchInput');
        
        if (searchBtn) {
            searchBtn.onclick = function() {
                const keyword = searchInput ? searchInput.value.trim() : '';
                console.log("🔍 搜索:", keyword);
                searchCustomers(keyword);
            };
        }
        
        if (searchInput) {
            searchInput.onkeypress = function(e) {
                if (e.key === 'Enter') {
                    const keyword = this.value.trim();
                    console.log("🔍 回车搜索:", keyword);
                    searchCustomers(keyword);
                }
            };
        }
        
        console.log("🔍 简单搜索设置完成");
    }
    
    function searchCustomers(keyword) {
        const tbody = document.querySelector('#customerTableModal tbody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">搜索中...</td></tr>';
        }
        
        fetch(`/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=0&size=5&sort=customerName,asc`)
            .then(response => response.json())
            .then(data => {
                console.log("✅ 搜索结果:", data);
                renderSimpleTable(data.content || []);
            })
            .catch(error => {
                console.error("❌ 搜索失败:", error);
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-danger">搜索失败</td></tr>';
                }
            });
    }
    
    // 全局选择函数 - 最简单的实现
    window.selectCustomerSimple = function(id, name, number) {
        console.log("🎯 选择客户 (极简版):", name);
        
        try {
            // 设置隐藏字段
            const hiddenInput = document.getElementById('selectedCustomerId');
            if (hiddenInput) {
                hiddenInput.value = id;
                console.log("✅ 设置ID:", id);
            }
            
            // 更新显示
            const display = document.getElementById('selectedCustomerInfoPlaceholder');
            if (display) {
                display.innerHTML = `<span class="badge bg-success me-2">已选择</span><strong>${name}</strong> <small>(${number})</small>`;
                display.style.backgroundColor = '#d1edff';
                display.classList.remove('is-invalid-placeholder');
                console.log("✅ 更新显示");
            }
            
            // 更新详情卡片
            updateDetails(id, name, number);
            
            // 关闭模态框
            closeModal();
            
            // 清除错误
            if (hiddenInput) {
                hiddenInput.classList.remove('is-invalid');
                hiddenInput.dispatchEvent(new Event('change'));
            }
            
            const errorDiv = document.getElementById('selectedCustomerIdClientFeedback');
            if (errorDiv) {
                errorDiv.style.display = 'none';
            }
            
            console.log("🎉 客户选择完成!");
            
            // 显示成功消息
            showSuccess(`已选择客户: ${name}`);
            
        } catch (error) {
            console.error("❌ 选择客户时出错:", error);
            alert('选择客户失败，请重试');
        }
    };
    
    function updateDetails(id, name, number) {
        // 更新详情卡片的简单版本
        const nameEl = document.getElementById('selectedCustomerNameText');
        const numberEl = document.getElementById('selectedCustomerNumberText');
        
        if (nameEl) nameEl.textContent = name;
        if (numberEl) numberEl.textContent = number;
        
        const card = document.getElementById('selectedCustomerDetailsCard');
        if (card) card.style.display = 'block';
        
        console.log("✅ 详情已更新");
    }
    
    function closeModal() {
        const modalEl = document.getElementById('customerSelectModal');
        
        if (modal) {
            try {
                modal.hide();
                console.log("✅ Bootstrap关闭成功");
                return;
            } catch (e) {
                console.warn("Bootstrap关闭失败:", e);
            }
        }
        
        // 手动关闭
        if (modalEl) {
            modalEl.style.display = 'none';
            modalEl.classList.remove('show');
            document.body.classList.remove('modal-open');
            
            const backdrop = document.querySelector('.modal-backdrop');
            if (backdrop) backdrop.remove();
            
            console.log("✅ 手动关闭成功");
        }
    }
    
    function showSuccess(message) {
        const alert = document.createElement('div');
        alert.className = 'alert alert-success position-fixed';
        alert.style.cssText = 'top: 20px; right: 20px; z-index: 99999; min-width: 300px;';
        alert.innerHTML = `<i class="bi bi-check-circle-fill me-2"></i>${message}`;
        
        document.body.appendChild(alert);
        
        setTimeout(() => {
            if (alert.parentNode) {
                alert.remove();
            }
        }, 3000);
    }
    
    // 启动
    init();
    
})();

console.log("�� 极简客户选择修复脚本已加载"); 