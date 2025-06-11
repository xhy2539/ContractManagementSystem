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
        'PENDING_FINALIZATION':'待定稿',
        'PENDING_APPROVAL': '待审批',
        'PENDING_SIGNING': '待签订',
        'ACTIVE': '有效',
        'COMPLETED': '完成',
        'EXPIRED': '过期',
        'REJECTED': '已拒绝' // 添加已拒绝状态
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

        // --- 修改点：统一调用 /reports/api/contracts/search 接口 ---
        // 该接口已在 ContractReportController 中实现，并支持根据用户权限过滤
        const endpoint = `/reports/api/contracts/search?${searchParams.toString()}`;

        // 发起请求
        fetch(endpoint, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            }
        })
            .then(response => {
                if (!response.ok) {
                    // 尝试解析错误信息体 (如果后端返回JSON错误信息)
                    return response.json().then(errData => {
                        throw new Error(`HTTP错误! 状态: ${response.status}, 信息: ${errData.message || response.statusText}`);
                    }).catch(() => {
                        // 如果错误信息体不是JSON或解析失败
                        throw new Error(`HTTP错误! 状态: ${response.status}, 信息: ${response.statusText}`);
                    });
                }
                return response.json();
            })
            .then(data => {
                renderContracts(data.content);
                renderPagination(data);
            })
            .catch(error => {
                console.error('Error:', error);
                showError(`加载数据失败: ${error.message}`);
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
                <td>${contract.contractNumber || 'N/A'}</td>
                <td>${contract.contractName || 'N/A'}</td>
                <td>${contract.customer ? contract.customer.customerName : 'N/A'}</td>
                <td>${contract.drafter ? contract.drafter.username : 'N/A'}</td>
                <td>${contract.startDate || 'N/A'}</td>
                <td>${contract.endDate || 'N/A'}</td>
                <td>${statusMap[contract.status] || contract.status || 'N/A'}</td>
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

        // 渲染页码
        const maxVisiblePages = 5; // 最多显示5个页码
        let startPage = Math.max(0, currentPage - Math.floor(maxVisiblePages / 2));
        let endPage = Math.min(totalPages - 1, startPage + maxVisiblePages - 1);

        // 调整起始页和结束页以确保始终显示 maxVisiblePages 个页码（如果总页数足够）
        if (endPage - startPage + 1 < maxVisiblePages) {
            startPage = Math.max(0, endPage - maxVisiblePages + 1);
        }

        if (startPage > 0) {
            paginationHtml += `<li class="page-item"><a class="page-link" href="#" data-page="0">1</a></li>`;
            if (startPage > 1) {
                paginationHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            paginationHtml += `
                <li class="page-item ${currentPage === i ? 'active' : ''}">
                    <a class="page-link" href="#" data-page="${i}">${i + 1}</a>
                </li>
            `;
        }

        if (endPage < totalPages - 1) {
            if (endPage < totalPages - 2) {
                paginationHtml += `<li class="page-item disabled"><span class="page-link">...</span></li>`;
            }
            paginationHtml += `<li class="page-item"><a class="page-link" href="#" data-page="${totalPages - 1}">${totalPages}</a></li>`;
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
                if (!isNaN(newPage) && newPage >= 0 && newPage < totalPages) {
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