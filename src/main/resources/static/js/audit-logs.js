// File: xhy2539/contractmanagementsystem/ContractManagementSystem-xhy/src/main/resources/static/js/audit-logs.js
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
    let currentPage = 0; // 后端通常页码从0开始
    const DEFAULT_PAGE_SIZE = 10; // 您可以根据需要调整每页显示的条数

    /**
     * 显示加载状态
     * @param {boolean} isLoading - 是否正在加载
     */
    function toggleLoadingState(isLoading) {
        if (isLoading) {
            auditLogTableBody.innerHTML = '<tr><td colspan="5" class="text-center"><div class="spinner-border spinner-border-sm" role="status"><span class="visually-hidden">正在加载...</span></div> 正在加载日志...</td></tr>';
            paginationControlsContainer.innerHTML = ''; // 清空分页，避免旧控件可点
        }
    }

    /**
     * 获取并显示审计日志数据
     * @param {number} page - 请求的页码 (从0开始)
     * @param {number} size - 每页的条数
     */
    function fetchAndDisplayLogs(page = 0, size = DEFAULT_PAGE_SIZE) {
        currentPage = page; // 更新当前页状态
        toggleLoadingState(true); // 显示加载状态

        let queryParams = `page=${page}&size=${size}&sort=timestamp,desc`; // 默认按时间戳降序

        const username = usernameFilterInput.value.trim();
        const action = actionFilterInput.value.trim();
        const startDate = startDateFilterInput.value;
        const endDate = endDateFilterInput.value;

        // 构建查询参数
        if (username) queryParams += `&username=${encodeURIComponent(username)}`;
        if (action) queryParams += `&action=${encodeURIComponent(action)}`;

        // 对于日期，只有当两者都有效时才发送到后端，或者根据后端API设计灵活发送
        if (startDate) {
            queryParams += `&startDate=${startDate}`;
        }
        if (endDate) {
            queryParams += `&endDate=${endDate}`;
        }

        fetch(`/api/system/audit-logs?${queryParams}`)
            .then(response => {
                if (!response.ok) {
                    if (response.status === 401 || response.status === 403) {
                        alert("会话已超时或无权限访问，请重新登录。");
                        window.location.href = '/login';
                        throw new Error(`认证或授权错误: ${response.status}`);
                    }
                    return response.json().then(errData => {
                        throw new Error(`HTTP错误! 状态: ${response.status}, 信息: ${errData.message || response.statusText}`);
                    }).catch(() => {
                        throw new Error(`HTTP错误! 状态: ${response.status}, 信息: ${response.statusText}`);
                    });
                }
                return response.json();
            })
            .then(pageData => {
                if (pageData && pageData.content) {
                    renderTable(pageData.content);
                    renderPagination(pageData);
                } else {
                    console.error('接收到的数据结构不符合预期:', pageData);
                    auditLogTableBody.innerHTML = '<tr><td colspan="5" class="text-warning text-center">无法解析日志数据或数据为空。</td></tr>';
                    paginationControlsContainer.innerHTML = ''; // 清空分页
                }
            })
            .catch(error => {
                console.error('获取审计日志时出错:', error);
                auditLogTableBody.innerHTML = `<tr><td colspan="5" class="text-danger text-center">加载日志失败: ${error.message}</td></tr>`;
                paginationControlsContainer.innerHTML = ''; // 清空分页
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
                row.insertCell().textContent = log.id != null ? log.id : 'N/A';
                row.insertCell().textContent = log.username || 'N/A';
                row.insertCell().textContent = log.action || 'N/A';

                const detailsCell = row.insertCell();
                detailsCell.textContent = log.details || '无详情';
                detailsCell.title = log.details || '无详情';
                detailsCell.style.maxWidth = '300px';
                detailsCell.style.overflow = 'hidden';
                detailsCell.style.textOverflow = 'ellipsis';
                detailsCell.style.whiteSpace = 'nowrap';

                let formattedTimestamp = 'N/A';
                if (log.timestamp) {
                    try {
                        const date = new Date(log.timestamp);
                        if (!isNaN(date)) {
                            formattedTimestamp = date.toLocaleString('zh-CN', {
                                year: 'numeric', month: '2-digit', day: '2-digit',
                                hour: '2-digit', minute: '2-digit', second: '2-digit',
                                hour12: false
                            }).replace(/\//g, '-');
                        } else {
                            formattedTimestamp = log.timestamp;
                        }
                    } catch (e) {
                        console.warn("无法格式化时间戳: ", log.timestamp, e);
                        formattedTimestamp = log.timestamp;
                    }
                }
                row.insertCell().textContent = formattedTimestamp;
            });
        } else {
            auditLogTableBody.innerHTML = '<tr><td colspan="5" class="text-center">未找到相关日志记录。</td></tr>';
        }
    }

    /**
     * 渲染分页控件 (优化版)
     * @param {Object} pageData - 后端返回的分页数据对象 (包含 totalPages, number (currentPageIndex), first, last 等)
     */
    function renderPagination(pageData) {
        paginationControlsContainer.innerHTML = ''; // 清空现有分页
        if (!pageData || pageData.totalPages === undefined || pageData.totalPages <= 0) {
            return;
        }

        const { totalPages, number: currentPageIndex, first: isFirstPage, last: isLastPage } = pageData;

        const createPageItem = (text, pageNumIfClickable, isActive = false, isDisabled = false, isEllipsis = false) => {
            const li = document.createElement('li');
            li.className = `page-item ${isActive ? 'active' : ''} ${isDisabled ? 'disabled' : ''}`;

            const elementTag = (isEllipsis || (isDisabled && pageNumIfClickable === undefined)) ? 'span' : 'a';
            const link = document.createElement(elementTag);
            link.className = 'page-link';

            if (elementTag === 'a') {
                link.href = '#';
            }
            link.textContent = text;

            if (elementTag === 'a' && pageNumIfClickable !== undefined && !isDisabled) {
                link.addEventListener('click', (e) => {
                    e.preventDefault();
                    fetchAndDisplayLogs(pageNumIfClickable, DEFAULT_PAGE_SIZE);
                });
            }
            li.appendChild(link);
            return li;
        };

        // 首页按钮
        paginationControlsContainer.appendChild(createPageItem('首页', 0, false, isFirstPage));
        // 上一页按钮
        paginationControlsContainer.appendChild(createPageItem('上一页', currentPageIndex - 1, false, isFirstPage));

        const SIBLING_COUNT = 1; // 当前页左右各显示1个页码 (例如: ... 3 4 [5] 6 7 ...)
        const DOTS = '...';

        // 渲染页码
        if (totalPages <= (SIBLING_COUNT * 2) + 5) {
            for (let i = 0; i < totalPages; i++) {
                paginationControlsContainer.appendChild(createPageItem(i + 1, i, i === currentPageIndex));
            }
        } else {
            // 1. 渲染第一页
            paginationControlsContainer.appendChild(createPageItem(1, 0, 0 === currentPageIndex));

            // 2. 左边省略号和其前的页码 (如果需要)
            if (currentPageIndex > SIBLING_COUNT + 1) {
                paginationControlsContainer.appendChild(createPageItem(DOTS, undefined, false, true, true));
            }

            // 3. 渲染当前页及其 SIBLING_COUNT 个邻近页码
            let startPage = Math.max(1, currentPageIndex - SIBLING_COUNT);
            let endPage = Math.min(totalPages - 2, currentPageIndex + SIBLING_COUNT);

            if (currentPageIndex < SIBLING_COUNT * 2 && totalPages > SIBLING_COUNT*2+2) {
                endPage = Math.min(totalPages - 2, SIBLING_COUNT * 2 +1);
            }
            if (currentPageIndex > totalPages - 1 - (SIBLING_COUNT * 2) && totalPages > SIBLING_COUNT*2+2) {
                startPage = Math.max(1, totalPages - 2 - (SIBLING_COUNT * 2) );
            }


            for (let i = startPage; i <= endPage; i++) {
                paginationControlsContainer.appendChild(createPageItem(i + 1, i, i === currentPageIndex));
            }

            // 4. 右边省略号和其后的页码 (如果需要)
            if (currentPageIndex < totalPages - SIBLING_COUNT - 2) {
                paginationControlsContainer.appendChild(createPageItem(DOTS, undefined, false, true, true));
            }

            // 5. 渲染最后一页 (除非总页数就是1页，但这种情况已在最开始totalPages <= ...中处理)
            if (totalPages > 1) {
                paginationControlsContainer.appendChild(createPageItem(totalPages, totalPages - 1, (totalPages - 1) === currentPageIndex));
            }
        }

        // 添加下一页按钮
        paginationControlsContainer.appendChild(createPageItem('下一页', currentPageIndex + 1, false, isLastPage));
        // 添加末页按钮
        paginationControlsContainer.appendChild(createPageItem('末页', totalPages - 1, false, isLastPage));
    }


    // 事件监听器绑定
    if (searchButton) {
        searchButton.addEventListener('click', () => {
            fetchAndDisplayLogs(0, DEFAULT_PAGE_SIZE); // 点击查询时，总是从第一页开始
        });
    }

    if (resetButton) {
        resetButton.addEventListener('click', () => {
            if (filterForm) filterForm.reset();
            usernameFilterInput.value = ''; // 确保清除
            actionFilterInput.value = '';
            startDateFilterInput.value = '';
            endDateFilterInput.value = '';
            fetchAndDisplayLogs(0, DEFAULT_PAGE_SIZE); // 重置后也从第一页开始加载
        });
    }

    if (exportButton) {
        exportButton.addEventListener('click', () => {
            // 显示导出按钮loading状态
            const originalButtonText = exportButton.innerHTML;
            exportButton.disabled = true;
            exportButton.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status"></span>导出中...';

            let exportUrl = '/api/system/audit-logs/export';
            const params = [];

            const username = usernameFilterInput.value.trim();
            const action = actionFilterInput.value.trim();
            const startDate = startDateFilterInput.value;
            const endDate = endDateFilterInput.value;

            if (username) params.push(`username=${encodeURIComponent(username)}`);
            if (action) params.push(`action=${encodeURIComponent(action)}`);

            // 对于导出，日期范围的策略也需要明确
            if (startDate) {
                params.push(`startDate=${startDate}`);
            }
            if (endDate) {
                params.push(`endDate=${endDate}`);
            }

            if (params.length > 0) {
                exportUrl += `?${params.join('&')}`;
            }

            console.log("Exporting with URL: ", exportUrl);

            // 使用fetch方式下载文件，避免页面跳转和loading状态问题
            fetch(exportUrl)
                .then(response => {
                    if (!response.ok) {
                        throw new Error(`导出失败: ${response.status} ${response.statusText}`);
                    }
                    return response.blob();
                })
                .then(blob => {
                    // 创建下载链接
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    
                    // 从响应头或默认设置文件名
                    const now = new Date();
                    const timestamp = now.toISOString().replace(/[:.-]/g, '').slice(0, 14);
                    a.download = `audit-logs-export-${timestamp}.csv`;
                    
                    document.body.appendChild(a);
                    a.click();
                    document.body.removeChild(a);
                    window.URL.revokeObjectURL(url);
                    
                    console.log('文件下载成功');
                })
                .catch(error => {
                    console.error('导出失败:', error);
                    alert(`导出失败: ${error.message}`);
                })
                .finally(() => {
                    // 恢复按钮状态
                    exportButton.disabled = false;
                    exportButton.innerHTML = originalButtonText;
                });
        });
    }

    // 页面初始加载时获取第一页数据
    fetchAndDisplayLogs(currentPage, DEFAULT_PAGE_SIZE);

    // 示例：为详情列添加点击展开/提示功能 (如果需要更复杂的交互)
    const tableBody = document.querySelector('#auditLogTable tbody');
    if (tableBody) {
        tableBody.addEventListener('click', function(event) {
            const targetCell = event.target.closest('td');
            if (targetCell) {
                if (Array.from(targetCell.parentNode.children).indexOf(targetCell) === 3) { // 假设详情列是第四列
                    if (targetCell.scrollWidth > targetCell.clientWidth) { // 如果内容溢出
                        alert('完整详情: \n' + targetCell.textContent); // 使用textContent获取实际内容
                    }
                }
            }
        });
    }
});