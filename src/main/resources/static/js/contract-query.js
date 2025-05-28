document.addEventListener('DOMContentLoaded', function() {
    const searchForm = document.getElementById('searchForm');
    const contractTableBody = document.getElementById('contractTableBody');
    const pagination = document.getElementById('pagination');
    
    let currentPage = 0;
    const pageSize = 10;

    // 状态映射表
    const statusMap = {
        'DRAFT': '起草',
        'PENDING_ASSIGNMENT': '待分配',
        'PENDING_COUNTERSIGN': '待会签',
        'PENDING_APPROVAL': '待审批',
        'PENDING_SIGNING': '待签订',
        'ACTIVE': '有效',
        'COMPLETED': '完成',
        'EXPIRED': '过期',
        'TERMINATED': '终止'
    };

    // 初始加载
    loadContracts();

    // 表单提交事件
    searchForm.addEventListener('submit', function(e) {
        e.preventDefault();
        currentPage = 0;
        loadContracts();
    });

    // 表单重置事件
    searchForm.addEventListener('reset', function() {
        setTimeout(() => {
            currentPage = 0;
            loadContracts();
        }, 0);
    });

    // 加载合同数据
    function loadContracts() {
        const formData = new FormData(searchForm);
        const searchParams = new URLSearchParams();
        
        // 添加查询参数
        formData.forEach((value, key) => {
            if (value) searchParams.append(key, value);
        });
        
        // 添加分页参数
        searchParams.append('page', currentPage);
        searchParams.append('size', pageSize);
        
        // 根据状态选择不同的API端点
        const status = formData.get('status');
        const endpoint = status 
            ? `/api/contracts/query/by-status?${searchParams.toString()}`
            : `/api/contracts/query/search?${searchParams.toString()}`;

        // 发起请求
        fetch(endpoint, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
        .then(response => {
            if (!response.ok) {
                throw new Error('网络响应错误');
            }
            return response.json();
        })
        .then(data => {
            renderContracts(data.content);
            renderPagination(data);
        })
        .catch(error => {
            console.error('Error:', error);
            showError('加载数据失败，请稍后重试');
        });
    }

    // 渲染合同数据
    function renderContracts(contracts) {
        contractTableBody.innerHTML = '';
        
        if (contracts.length === 0) {
            contractTableBody.innerHTML = '<tr><td colspan="8" class="text-center">没有找到匹配的合同</td></tr>';
            return;
        }

        contracts.forEach(contract => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${contract.contractNumber}</td>
                <td>${contract.contractName}</td>
                <td>${contract.customer ? contract.customer.customerName : 'N/A'}</td>
                <td>${contract.drafter ? contract.drafter.username : 'N/A'}</td>
                <td>${contract.startDate || 'N/A'}</td>
                <td>${contract.endDate || 'N/A'}</td>
                <td>${statusMap[contract.status] || contract.status}</td>
                <td>
                    <button class="btn btn-sm btn-info" onclick="viewContract(${contract.id})">
                        <i class="bi bi-eye"></i> 查看
                    </button>
                </td>
            `;
            contractTableBody.appendChild(row);
        });
    }

    // 渲染分页控件
    function renderPagination(pageData) {
        const totalPages = pageData.totalPages;
        
        if (totalPages <= 1) {
            pagination.innerHTML = '';
            return;
        }

        let paginationHtml = `
            <li class="page-item ${currentPage === 0 ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${currentPage - 1}">上一页</a>
            </li>
        `;

        for (let i = 0; i < totalPages; i++) {
            paginationHtml += `
                <li class="page-item ${currentPage === i ? 'active' : ''}">
                    <a class="page-link" href="#" data-page="${i}">${i + 1}</a>
                </li>
            `;
        }

        paginationHtml += `
            <li class="page-item ${currentPage === totalPages - 1 ? 'disabled' : ''}">
                <a class="page-link" href="#" data-page="${currentPage + 1}">下一页</a>
            </li>
        `;

        pagination.innerHTML = paginationHtml;

        // 添加分页事件监听
        pagination.querySelectorAll('.page-link').forEach(link => {
            link.addEventListener('click', function(e) {
                e.preventDefault();
                const newPage = parseInt(this.dataset.page);
                if (newPage >= 0 && newPage < totalPages) {
                    currentPage = newPage;
                    loadContracts();
                }
            });
        });
    }

    // 显示错误信息
    function showError(message) {
        // 这里可以实现一个toast或者其他提示组件
        alert(message);
    }
});

// 查看合同详情
function viewContract(contractId) {
    window.location.href = `/contracts/${contractId}/detail`;
} 