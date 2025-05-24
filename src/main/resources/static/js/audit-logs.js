document.addEventListener('DOMContentLoaded', function () {
    // DOM 元素获取
    const filterForm = document.getElementById('filterForm');
    const usernameFilterInput = document.getElementById('usernameFilter');
    const actionFilterInput = document.getElementById('actionFilter');
    const startDateFilterInput = document.getElementById('startDateFilter');
    const endDateFilterInput = document.getElementById('endDateFilter');
    const searchButton = document.getElementById('searchButton');
    const exportButton = document.getElementById('exportButton');
    const resetButton = document.getElementById('resetButton');
    const auditLogTableBody = document.querySelector('#auditLogTable tbody');
    const paginationControlsContainer = document.getElementById('paginationControls');

    // 分页参数
    let currentPage = 0;
    const DEFAULT_PAGE_SIZE = 10; // 您可以根据需要调整每页显示的条数

    /**
     * 获取并显示审计日志数据
     * @param {number} page - 请求的页码 (从0开始)
     * @param {number} size - 每页的条数
     */
    function fetchAndDisplayLogs(page = 0, size = DEFAULT_PAGE_SIZE) {
        currentPage = page;
        let queryParams = `page=${page}&size=${size}&sort=timestamp,desc`; // 默认按时间戳降序

        const username = usernameFilterInput.value.trim();
        const action = actionFilterInput.value.trim();
        const startDate = startDateFilterInput.value;
        const endDate = endDateFilterInput.value;

        // 构建查询参数
        if (username) queryParams += `&username=${encodeURIComponent(username)}`;
        if (action) queryParams += `&action=${encodeURIComponent(action)}`;

        // 只有当开始和结束日期都填写时才作为查询条件
        // 注意：如果后端API只接受startDate或只接受endDate，这里的逻辑需要调整
        if (startDate && endDate) {
            queryParams += `&startDate=${startDate}`;
            queryParams += `&endDate=${endDate}`;
        } else if (startDate && !endDate) {
            // 如果只填了开始日期，可以考虑发送（或提示用户需要结束日期）
            // queryParams += `&startDate=${startDate}`;
            console.warn("仅填写了开始日期，未填写结束日期。时间范围查询可能不生效或按后端默认逻辑处理。");
        } else if (!startDate && endDate) {
            console.warn("仅填写了结束日期，未填写开始日期。时间范围查询可能不生效或按后端默认逻辑处理。");
        }


        fetch(`/api/system/audit-logs?${queryParams}`)
            .then(response => {
                if (!response.ok) {
                    // 如果是401或403，可能是会话超时或权限问题，可以考虑重定向到登录页
                    if (response.status === 401 || response.status === 403) {
                        alert("会话已超时或无权限访问，请重新登录。");
                        window.location.href = '/login'; // 跳转到登录页
                        throw new Error(`Authentication/Authorization error: ${response.status}`);
                    }
                    throw new Error(`HTTP error! status: ${response.status}, message: ${response.statusText}`);
                }
                return response.json();
            })
            .then(pageData => {
                if (pageData && pageData.content) {
                    renderTable(pageData.content);
                    renderPagination(pageData);
                } else {
                    console.error('Received unexpected data structure:', pageData);
                    auditLogTableBody.innerHTML = '<tr><td colspan="5" class="text-warning">无法解析日志数据。</td></tr>';
                }
            })
            .catch(error => {
                console.error('获取审计日志时出错:', error);
                auditLogTableBody.innerHTML = `<tr><td colspan="5" class="text-danger">加载日志失败: ${error.message}</td></tr>`;
            });
    }

    /**
     * 渲染日志表格内容
     * @param {Array<Object>} logs - 日志对象数组
     */
    function renderTable(logs) {
        auditLogTableBody.innerHTML = ''; // 清空现有行
        if (logs && logs.length > 0) {
            logs.forEach(log => {
                const row = auditLogTableBody.insertRow();
                row.insertCell().textContent = log.id !== null && log.id !== undefined ? log.id : 'N/A';
                row.insertCell().textContent = log.username || 'N/A';
                row.insertCell().textContent = log.action || 'N/A';

                const detailsCell = row.insertCell();
                detailsCell.textContent = log.details || '无详情';
                detailsCell.title = log.details || '无详情'; // 完整内容作为 tooltip
                // 样式用于处理长文本溢出
                detailsCell.style.maxWidth = '300px';
                detailsCell.style.overflow = 'hidden';
                detailsCell.style.textOverflow = 'ellipsis';
                detailsCell.style.whiteSpace = 'nowrap';

                // 格式化时间戳
                let formattedTimestamp = 'N/A';
                if (log.timestamp) {
                    try {
                        formattedTimestamp = new Date(log.timestamp).toLocaleString('zh-CN', { hour12: false });
                    } catch (e) {
                        console.warn("无法格式化时间戳: ", log.timestamp, e);
                        formattedTimestamp = log.timestamp; // 显示原始值
                    }
                }
                row.insertCell().textContent = formattedTimestamp;
            });
        } else {
            auditLogTableBody.innerHTML = '<tr><td colspan="5" class="text-center">未找到相关日志记录。</td></tr>';
        }
    }

    /**
     * 渲染分页控件
     * @param {Object} pageData - 后端返回的分页数据对象 (包含 totalPages, number, first, last 等)
     */
    function renderPagination(pageData) {
        paginationControlsContainer.innerHTML = ''; // 清空现有分页
        if (!pageData || pageData.totalPages === undefined || pageData.totalPages <= 1) {
            return; // 如果没有页码信息或只有一页，则不渲染分页控件
        }

        const { totalPages, number: currentPageIndex, first: isFirstPage, last: isLastPage } = pageData;

        // 上一页按钮
        const prevLi = document.createElement('li');
        prevLi.className = `page-item ${isFirstPage ? 'disabled' : ''}`;
        const prevLink = document.createElement('a');
        prevLink.className = 'page-link';
        prevLink.href = '#';
        prevLink.textContent = '上一页';
        if (!isFirstPage) {
            prevLink.addEventListener('click', (e) => {
                e.preventDefault();
                fetchAndDisplayLogs(currentPageIndex - 1, DEFAULT_PAGE_SIZE);
            });
        }
        prevLi.appendChild(prevLink);
        paginationControlsContainer.appendChild(prevLi);

        // 页码按钮 (这里可以实现更复杂的逻辑，比如只显示部分页码)
        // 简化版：显示所有页码
        for (let i = 0; i < totalPages; i++) {
            const pageLi = document.createElement('li');
            pageLi.className = `page-item ${i === currentPageIndex ? 'active' : ''}`;
            const pageLink = document.createElement('a');
            pageLink.className = 'page-link';
            pageLink.href = '#';
            pageLink.textContent = i + 1;
            pageLink.dataset.page = i; // 存储页码
            pageLink.addEventListener('click', (e) => {
                e.preventDefault();
                fetchAndDisplayLogs(parseInt(e.target.dataset.page), DEFAULT_PAGE_SIZE);
            });
            pageLi.appendChild(pageLink);
            paginationControlsContainer.appendChild(pageLi);
        }

        // 下一页按钮
        const nextLi = document.createElement('li');
        nextLi.className = `page-item ${isLastPage ? 'disabled' : ''}`;
        const nextLink = document.createElement('a');
        nextLink.className = 'page-link';
        nextLink.href = '#';
        nextLink.textContent = '下一页';
        if (!isLastPage) {
            nextLink.addEventListener('click', (e) => {
                e.preventDefault();
                fetchAndDisplayLogs(currentPageIndex + 1, DEFAULT_PAGE_SIZE);
            });
        }
        nextLi.appendChild(nextLink);
        paginationControlsContainer.appendChild(nextLi);
    }

    // 事件监听器绑定
    if (searchButton) {
        searchButton.addEventListener('click', () => {
            fetchAndDisplayLogs(0, DEFAULT_PAGE_SIZE); // 点击查询时，总是从第一页开始
        });
    }

    if (resetButton) {
        resetButton.addEventListener('click', () => {
            if (filterForm) filterForm.reset(); // 重置表单
            fetchAndDisplayLogs(0, DEFAULT_PAGE_SIZE); // 重置后也从第一页开始加载
        });
    }

    if (exportButton) {
        exportButton.addEventListener('click', () => {
            let exportUrl = '/api/system/audit-logs/export';
            const params = [];

            const username = usernameFilterInput.value.trim();
            const action = actionFilterInput.value.trim();
            const startDate = startDateFilterInput.value;
            const endDate = endDateFilterInput.value;

            if (username) params.push(`username=${encodeURIComponent(username)}`);
            if (action) params.push(`action=${encodeURIComponent(action)}`);
            if (startDate && endDate) { // 确保起止日期都存在
                params.push(`startDate=${startDate}`);
                params.push(`endDate=${endDate}`);
            } else if (startDate) {
                // 可以选择只发送开始日期，或提示用户补全结束日期
                params.push(`startDate=${startDate}`);
            } else if (endDate) {
                params.push(`endDate=${endDate}`);
            }


            if (params.length > 0) {
                exportUrl += `?${params.join('&')}`;
            }

            // 触发浏览器下载
            window.location.href = exportUrl;
        });
    }

    // 页面初始加载时获取第一页数据
    fetchAndDisplayLogs(currentPage, DEFAULT_PAGE_SIZE);
});