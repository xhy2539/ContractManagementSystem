// 超级强力客户选择修复 - 完全接管控制
console.log("🚀 超级强力客户选择启动");

(function() {
    'use strict';
    
    if (window.superForceCustomer) {
        console.log("已修复，跳过");
        return;
    }
    window.superForceCustomer = true;
    
    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setTimeout(setup, 200);
        }
    }
    
    function setup() {
        console.log("🔧 设置超级强力修复");
        
        const btn = document.getElementById('openCustomerSelectModalBtn');
        const modalEl = document.getElementById('customerSelectModal');
        
        if (!btn || !modalEl) {
            console.error("❌ 元素未找到");
            return;
        }
        
        // 完全干掉原始事件
        btn.onclick = null;
        btn.removeAttribute('data-bs-toggle');
        btn.removeAttribute('data-bs-target');
        
        // 强制覆盖点击事件
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            e.stopImmediatePropagation();
            console.log("🎯 强力 - 打开客户选择");
            openForceModal();
        }, true);
        
        // 移除Bootstrap模态框的所有属性
        modalEl.removeAttribute('data-bs-backdrop');
        modalEl.removeAttribute('data-bs-keyboard');
        modalEl.setAttribute('tabindex', '-1');
        modalEl.classList.remove('fade');
        
        console.log("✅ 超级强力修复完成");
    }
    
    function openForceModal() {
        console.log("🔧 开始强力显示模态框");
        
        // 先加载数据
        loadForceData()
            .then(() => {
                console.log("🔧 数据加载完成，强力显示模态框");
                showForceModal();
                setupForceEvents();
            })
            .catch(error => {
                console.error("❌ 失败:", error);
                alert('加载客户数据失败，请重试');
            });
    }
    
    function showForceModal() {
        const modalEl = document.getElementById('customerSelectModal');
        
        // 完全手动显示，强制样式
        modalEl.style.cssText = `
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
        modalEl.classList.add('show');
        
        // 添加body类
        document.body.classList.add('modal-open');
        document.body.style.overflow = 'hidden';
        
        // 强制模态框内容样式
        const modalDialog = modalEl.querySelector('.modal-dialog');
        if (modalDialog) {
            modalDialog.style.cssText = `
                position: relative !important;
                width: auto !important;
                margin: 1.75rem auto !important;
                max-width: 800px !important;
                pointer-events: auto !important;
                z-index: 100000 !important;
            `;
        }
        
        const modalContent = modalEl.querySelector('.modal-content');
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
        }
        
        console.log("✅ 强力模态框显示成功");
    }
    
    function closeForceModal() {
        console.log("🔧 强力关闭模态框");
        
        const modalEl = document.getElementById('customerSelectModal');
        
        if (modalEl) {
            modalEl.style.display = 'none';
            modalEl.classList.remove('show');
        }
        
        document.body.classList.remove('modal-open');
        document.body.style.overflow = '';
        
        console.log("✅ 强力模态框关闭成功");
    }
    
    function loadForceData() {
        console.log("📡 强力加载客户数据");
        
        const tbody = document.querySelector('#customerTableModal tbody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">正在加载...</td></tr>';
        }
        
        return fetch('/customers/api/search?page=0&size=5&sort=customerName,asc')
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log("✅ 强力数据加载成功:", data);
                renderForceTable(data.content || []);
                return data;
            });
    }
    
    function renderForceTable(customers) {
        const tbody = document.querySelector('#customerTableModal tbody');
        if (!tbody) {
            console.error("❌ 找不到tbody");
            return;
        }
        
        tbody.innerHTML = '';
        
        if (!customers || customers.length === 0) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">没有客户记录</td></tr>';
            return;
        }
        
        // 存储完整的客户数据，供后续使用
        window.currentCustomersData = customers;
        
        customers.forEach((customer, index) => {
            const tr = document.createElement('tr');
            
            // 强制行样式
            tr.style.cssText = `
                cursor: pointer !important;
                position: relative !important;
                z-index: 100002 !important;
                pointer-events: auto !important;
                background-color: white !important;
            `;
            
            tr.innerHTML = `
                <td style="pointer-events: auto !important; padding: 8px !important;">${customer.customerNumber || 'N/A'}</td>
                <td style="pointer-events: auto !important; padding: 8px !important;"><strong>${customer.customerName}</strong></td>
                <td style="pointer-events: auto !important; padding: 8px !important;">${customer.phoneNumber || 'N/A'}</td>
                <td style="pointer-events: auto !important; padding: 8px !important;">${customer.email || 'N/A'}</td>
                <td style="pointer-events: auto !important; padding: 8px !important;">
                    <button class="btn btn-sm btn-success force-select-btn" 
                            style="pointer-events: auto !important; z-index: 100005 !important; position: relative !important;"
                            data-id="${customer.id}" 
                            data-name="${customer.customerName}" 
                            data-number="${customer.customerNumber || 'N/A'}"
                            data-phone="${customer.phoneNumber || 'N/A'}"
                            data-email="${customer.email || 'N/A'}"
                            data-address="${customer.address || 'N/A'}">
                        选择
                    </button>
                </td>
            `;
            
            // 强制行点击事件 - 仅在非按钮区域
            tr.addEventListener('click', function(e) {
                // 检查是否点击的是按钮或按钮区域
                if (e.target.closest('.force-select-btn')) {
                    console.log("🎯 点击的是按钮区域，由按钮处理");
                    return; // 让按钮事件处理
                }
                
                console.log("🎯 强力点击表格行，目标:", e.target.tagName);
                e.preventDefault();
                e.stopPropagation();
                console.log("🎯 强力点击行选择客户:", customer.customerName);
                selectForceCustomer(customer);
            }, false);
            
            // 悬停效果
            tr.addEventListener('mouseenter', function() {
                console.log("🎯 鼠标进入行:", customer.customerName);
                this.style.backgroundColor = '#f8f9fa !important';
            });
            
            tr.addEventListener('mouseleave', function() {
                this.style.backgroundColor = 'white !important';
            });
            
            tbody.appendChild(tr);
            
            // 单独处理按钮事件 - 延迟绑定确保DOM完成
            setTimeout(() => {
                const btn = tr.querySelector('.force-select-btn');
                if (btn) {
                    // 确保按钮的高优先级
                    btn.style.zIndex = '100010';
                    btn.style.position = 'relative';
                    
                    // 移除所有可能的事件
                    btn.onclick = null;
                    
                    // 添加强制按钮事件 - 使用多种方式确保触发
                    btn.addEventListener('click', function(e) {
                        console.log("🎯 强力按钮点击事件触发！");
                        e.preventDefault();
                        e.stopImmediatePropagation();
                        console.log("🎯 强力按钮选择客户:", customer.customerName);
                        selectForceCustomer(customer);
                    }, true);
                    
                    // 额外的鼠标事件确保响应
                    btn.addEventListener('mousedown', function(e) {
                        console.log("🎯 按钮鼠标按下事件");
                        e.stopPropagation();
                    }, true);
                    
                    btn.addEventListener('mouseup', function(e) {
                        console.log("🎯 按钮鼠标释放事件");
                        e.stopPropagation();
                        // 作为备用，如果点击事件没触发
                        setTimeout(() => {
                            console.log("🎯 备用选择客户:", customer.customerName);
                            selectForceCustomer(customer);
                        }, 50);
                    }, true);
                }
            }, 100);
        });
        
        console.log(`✅ 强力渲染完成: ${customers.length} 条记录`);
    }
    
    function setupForceEvents() {
        console.log("🔧 设置强力事件");
        
        // 强制搜索按钮
        const searchBtn = document.getElementById('modalSearchCustomerBtn');
        if (searchBtn) {
            // 清除原有事件
            searchBtn.onclick = null;
            searchBtn.addEventListener('click', function(e) {
                console.log("🔍 强力点击搜索按钮");
                e.preventDefault();
                e.stopPropagation();
                const input = document.getElementById('modalCustomerSearchInput');
                const keyword = input ? input.value.trim() : '';
                console.log("🔍 强力搜索:", keyword);
                searchForceCustomers(keyword);
            }, true);
        }
        
        // 强制搜索输入框
        const searchInput = document.getElementById('modalCustomerSearchInput');
        if (searchInput) {
            searchInput.onkeypress = null;
            searchInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    console.log("🔍 强力回车键搜索");
                    e.preventDefault();
                    const keyword = this.value.trim();
                    console.log("🔍 强力回车搜索:", keyword);
                    searchForceCustomers(keyword);
                }
            }, true);
        }
        
        // 强制ESC键关闭
        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') {
                console.log("🔧 强力ESC键被按下");
                const modal = document.getElementById('customerSelectModal');
                if (modal && modal.style.display === 'block') {
                    console.log("🔧 强力ESC键关闭模态框");
                    closeForceModal();
                }
            }
        }, true);
        
        // 强制关闭按钮
        const closeBtn = document.querySelector('#customerSelectModal .btn-close');
        if (closeBtn) {
            closeBtn.onclick = null;
            closeBtn.addEventListener('click', function(e) {
                console.log("🔧 强力点击关闭按钮");
                e.preventDefault();
                e.stopPropagation();
                closeForceModal();
            }, true);
        }
        
        // 延迟添加背景点击事件，确保按钮事件优先
        setTimeout(() => {
            const modalEl = document.getElementById('customerSelectModal');
            if (modalEl) {
                // 背景点击关闭 - 低优先级
                modalEl.addEventListener('click', function(e) {
                    console.log("🎯 点击模态框背景，目标:", e.target);
                    
                    // 检查是否点击的是按钮
                    if (e.target.closest('.force-select-btn')) {
                        console.log("🔧 点击的是按钮，不关闭模态框");
                        return;
                    }
                    
                    // 检查是否点击的是模态框内容区域
                    const modalContent = modalEl.querySelector('.modal-content');
                    if (modalContent && modalContent.contains(e.target)) {
                        console.log("🔧 点击的是模态框内容，不关闭");
                        return;
                    }
                    
                    // 只有点击真正的背景才关闭
                    if (e.target === modalEl) {
                        console.log("🔧 确认点击背景，关闭模态框");
                        closeForceModal();
                    }
                }, false); // 使用冒泡阶段，优先级低
            }
        }, 200);
        
        console.log("✅ 强力事件设置完成");
    }
    
    function searchForceCustomers(keyword) {
        console.log("🔍 强力执行搜索:", keyword);
        
        const tbody = document.querySelector('#customerTableModal tbody');
        if (tbody) {
            tbody.innerHTML = '<tr><td colspan="5" class="text-center">搜索中...</td></tr>';
        }
        
        fetch(`/customers/api/search?keyword=${encodeURIComponent(keyword)}&page=0&size=5&sort=customerName,asc`)
            .then(response => response.json())
            .then(data => {
                console.log("✅ 强力搜索结果:", data);
                renderForceTable(data.content || []);
            })
            .catch(error => {
                console.error("❌ 强力搜索失败:", error);
                if (tbody) {
                    tbody.innerHTML = '<tr><td colspan="5" class="text-center text-danger">搜索失败</td></tr>';
                }
            });
    }
    
    function selectForceCustomer(customer) {
        // 如果传入的是ID（向后兼容旧调用）
        if (typeof customer === 'string' || typeof customer === 'number') {
            const customerId = customer;
            console.log("🔍 通过ID查找客户:", customerId);
            
            // 从当前数据中查找客户
            if (window.currentCustomersData) {
                const foundCustomer = window.currentCustomersData.find(c => c.id.toString() === customerId.toString());
                if (foundCustomer) {
                    console.log("✅ 找到客户数据:", foundCustomer);
                    updateCustomerDisplay(foundCustomer);
                    return;
                }
            }
            
            // 如果没找到，使用基本信息
            console.warn("⚠️ 未找到完整客户数据，使用基本信息");
            const basicCustomer = {
                id: customerId,
                customerName: arguments[1] || 'N/A',
                customerNumber: arguments[2] || 'N/A',
                phoneNumber: 'N/A',
                email: 'N/A',
                address: 'N/A'
            };
            updateCustomerDisplay(basicCustomer);
            return;
        }
        
        // 如果传入的是完整客户对象
        console.log("✅ 强力选择客户开始:", customer);
        updateCustomerDisplay(customer);
    }
    
    function getCustomerFromTable(customerId) {
        // 优先从内存中的数据获取
        if (window.currentCustomersData) {
            const customer = window.currentCustomersData.find(c => c.id.toString() === customerId.toString());
            if (customer) {
                console.log("✅ 从内存数据获取客户信息:", customer);
                return customer;
            }
        }
        
        // 从表格DOM中获取客户信息（作为后备）
        const rows = document.querySelectorAll('#customerTableModal tbody tr');
        for (let row of rows) {
            const btn = row.querySelector('.force-select-btn');
            if (btn && btn.dataset.id === customerId.toString()) {
                const customer = {
                    id: customerId,
                    customerNumber: btn.dataset.number || 'N/A',
                    customerName: btn.dataset.name || 'N/A',
                    phoneNumber: btn.dataset.phone || 'N/A',
                    email: btn.dataset.email || 'N/A',
                    address: btn.dataset.address || 'N/A'
                };
                console.log("✅ 从表格DOM获取客户信息:", customer);
                return customer;
            }
        }
        
        console.warn("⚠️ 未找到客户信息");
        return null;
    }
    
    function updateCustomerDisplay(customer) {
        try {
            console.log("🔧 开始更新客户显示信息");
            
            // 设置隐藏字段
            const hiddenInput = document.getElementById('selectedCustomerId');
            if (hiddenInput) {
                hiddenInput.value = customer.id;
                console.log("✅ 强力设置客户ID:", customer.id);
            }
            
            // 更新主显示区域
            const display = document.getElementById('selectedCustomerInfoPlaceholder');
            if (display) {
                display.innerHTML = `
                    <span class="badge bg-success me-2">已选择</span>
                    <strong>${customer.customerName}</strong> 
                    <small class="text-muted">(${customer.customerNumber})</small>
                `;
                display.style.backgroundColor = '#d1edff';
                display.classList.remove('is-invalid-placeholder');
                console.log("✅ 强力更新主显示");
            }
            
            // 更新详情卡片 - 确保所有字段都更新
            const nameEl = document.getElementById('selectedCustomerNameText');
            const numberEl = document.getElementById('selectedCustomerNumberText');
            const phoneEl = document.getElementById('selectedCustomerPhoneText');
            const emailEl = document.getElementById('selectedCustomerEmailText');
            const addressEl = document.getElementById('selectedCustomerAddressText');
            
            if (nameEl) {
                nameEl.textContent = customer.customerName;
                console.log("✅ 更新客户名称:", customer.customerName);
            }
            if (numberEl) {
                numberEl.textContent = customer.customerNumber || 'N/A';
                console.log("✅ 更新客户编号:", customer.customerNumber);
            }
            if (phoneEl) {
                phoneEl.textContent = customer.phoneNumber || 'N/A';
                console.log("✅ 更新客户电话:", customer.phoneNumber);
            }
            if (emailEl) {
                emailEl.textContent = customer.email || 'N/A';
                console.log("✅ 更新客户邮箱:", customer.email);
            }
            if (addressEl) {
                addressEl.textContent = customer.address || 'N/A';
                console.log("✅ 更新客户地址:", customer.address);
            }
            
            // 显示详情卡片
            const card = document.getElementById('selectedCustomerDetailsCard');
            if (card) {
                card.style.display = 'block';
                console.log("✅ 显示客户详情卡片");
            }
            
            // 清除错误
            if (hiddenInput) {
                hiddenInput.classList.remove('is-invalid');
                hiddenInput.dispatchEvent(new Event('change'));
            }
            
            const errorDiv = document.getElementById('selectedCustomerIdClientFeedback');
            if (errorDiv) {
                errorDiv.style.display = 'none';
            }
            
            // 关闭模态框
            console.log("🔧 强力准备关闭模态框...");
            closeForceModal();
            
            console.log("🎉 强力客户选择成功完成！");
            
            // 显示成功消息
            showForceSuccess(`已选择客户: ${customer.customerName}`);
            
            // 额外的信息显示验证
            setTimeout(() => {
                validateCustomerDisplay(customer);
            }, 500);
            
        } catch (error) {
            console.error("❌ 强力选择客户失败:", error);
            alert('选择客户失败，请重试');
        }
    }
    
    function validateCustomerDisplay(customer) {
        console.log("🔍 验证客户信息显示状态");
        
        const card = document.getElementById('selectedCustomerDetailsCard');
        const nameEl = document.getElementById('selectedCustomerNameText');
        const numberEl = document.getElementById('selectedCustomerNumberText');
        const phoneEl = document.getElementById('selectedCustomerPhoneText');
        const emailEl = document.getElementById('selectedCustomerEmailText');
        const addressEl = document.getElementById('selectedCustomerAddressText');
        
        if (!card || card.style.display === 'none') {
            console.warn("⚠️ 客户详情卡片未显示，强制显示");
            if (card) card.style.display = 'block';
        }
        
        // 检查每个字段是否正确设置
        const checks = [
            { element: nameEl, value: customer.customerName, name: '客户名称' },
            { element: numberEl, value: customer.customerNumber, name: '客户编号' },
            { element: phoneEl, value: customer.phoneNumber, name: '客户电话' },
            { element: emailEl, value: customer.email, name: '客户邮箱' },
            { element: addressEl, value: customer.address, name: '客户地址' }
        ];
        
        checks.forEach(check => {
            if (check.element) {
                if (!check.element.textContent || check.element.textContent === '') {
                    console.warn(`⚠️ ${check.name}未正确设置，重新设置`);
                    check.element.textContent = check.value || 'N/A';
                } else {
                    console.log(`✅ ${check.name}显示正常:`, check.element.textContent);
                }
            } else {
                console.error(`❌ ${check.name}元素未找到`);
            }
        });
        
        console.log("🎯 客户信息显示验证完成");
    }
    
    function showForceSuccess(message) {
        const alert = document.createElement('div');
        alert.style.cssText = `
            position: fixed !important;
            top: 20px !important;
            right: 20px !important;
            z-index: 999999 !important;
            background: #d4edda !important;
            color: #155724 !important;
            border: 1px solid #c3e6cb !important;
            border-radius: 4px !important;
            padding: 12px 16px !important;
            min-width: 300px !important;
            box-shadow: 0 4px 6px rgba(0,0,0,0.1) !important;
            pointer-events: auto !important;
        `;
        alert.innerHTML = `✅ ${message}`;
        
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

console.log("�� 超级强力客户选择脚本已加载"); 