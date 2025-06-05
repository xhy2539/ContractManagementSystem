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
        // else { // 实际清空由renderTable完成 }
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

        if (startDate && endDate) {
            queryParams += `&startDate=${startDate}`;
            queryParams += `&endDate=${endDate}`;
        } else if (startDate && !endDate) {
            console.warn("筛选：仅填写了开始日期，未填写结束日期。后端可能无法按预期处理单个日期。");
            // queryParams += `&startDate=${startDate}`; // 根据后端API决定是否发送单个日期
        } else if (!startDate && endDate) {
            console.warn("筛选：仅填写了结束日期，未填写开始日期。后端可能无法按预期处理单个日期。");
            // queryParams += `&endDate=${endDate}`; // 根据后端API决定是否发送单个日期
        }

        fetch(`/api/system/audit-logs?${queryParams}`)
            .then(response => {
                if (!response.ok) {
                    if (response.status === 401 || response.status === 403) {
                        alert("会话已超时或无权限访问，请重新登录。");
                        window.location.href = '/login';
                        throw new Error(`认证或授权错误: ${response.status}`);
                    }
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
                row.insertCell().textContent = log.id != null ? log.id : 'N/A'; // 简化了null/undefined检查
                row.insertCell().textContent = log.username || 'N/A';
                row.insertCell().textContent = log.action || 'N/A';

                const detailsCell = row.insertCell();
                detailsCell.textContent = log.details || '无详情';
                detailsCell.title = log.details || '无详情';
                detailsCell.style.maxWidth = '300px'; // 这些样式可以考虑移到CSS文件中
                detailsCell.style.overflow = 'hidden';
                detailsCell.style.textOverflow = 'ellipsis';
                detailsCell.style.whiteSpace = 'nowrap';
                // detailsCell.classList.add('details-column'); // 如果HTML中有对应CSS类

                let formattedTimestamp = 'N/A';
                if (log.timestamp) {
                    try {
                        // 尝试更完整的日期时间格式，并确保时区正确（toLocaleString默认使用本地时区）
                        const date = new Date(log.timestamp);
                        if (!isNaN(date)) { // 检查日期是否有效
                            formattedTimestamp = date.toLocaleString('zh-CN', {
                                year: 'numeric', month: '2-digit', day: '2-digit',
                                hour: '2-digit', minute: '2-digit', second: '2-digit',
                                hour12: false
                            }).replace(/\//g, '-'); // 将 yyyy/MM/dd 替换为 yyyy-MM-dd
                        } else {
                            formattedTimestamp = log.timestamp; // 无效日期则显示原始值
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
            return; // 如果没有页码信息或总页数为0，则不渲染分页控件
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
        if (totalPages <= (SIBLING_COUNT * 2) + 5) { // 总页数较少时，显示所有页码（例如 SIBLING_COUNT=1时，1+1+1+1+1 + 2*1 = 7页以内全显示）
            for (let i = 0; i < totalPages; i++) {
                paginationControlsContainer.appendChild(createPageItem(i + 1, i, i === currentPageIndex));
            }
        } else {
            // 1. 渲染第一页
            paginationControlsContainer.appendChild(createPageItem(1, 0, 0 === currentPageIndex));

            // 2. 左边省略号和其前的页码 (如果需要)
            if (currentPageIndex > SIBLING_COUNT + 1) { // (当前页索引 > (兄弟数+第一页本身)) 才显示左省略号
                // (currentPageIndex > 2 for SIBLING_COUNT=1)
                paginationControlsContainer.appendChild(createPageItem(DOTS, undefined, false, true, true));
            }

            // 3. 渲染当前页及其 SIBLING_COUNT 个邻近页码
            // 计算显示的起始页码索引 (不能小于1, 因为0是第一页，已单独处理)
            let startPage = Math.max(1, currentPageIndex - SIBLING_COUNT);
            // 计算显示的结束页码索引 (不能大于totalPages-2, 因为totalPages-1是最后一页，将单独处理)
            let endPage = Math.min(totalPages - 2, currentPageIndex + SIBLING_COUNT);

            // 确保即使在边缘情况下，中间显示的数字页码（不含首尾）尽量满足 SIBLING_COUNT*2 + 1 的数量
            // 如果左边空间不足，则向右扩展
            if (currentPageIndex < SIBLING_COUNT * 2 && totalPages > SIBLING_COUNT*2+2) { // 当前页比较靠前
                endPage = Math.min(totalPages - 2, SIBLING_COUNT * 2 +1); // 例: SIBLING=1, 显示 1 (2 3 4) ... N
            }
            // 如果右边空间不足，则向左扩展
            if (currentPageIndex > totalPages - 1 - (SIBLING_COUNT * 2) && totalPages > SIBLING_COUNT*2+2) { // 当前页比较靠后
                startPage = Math.max(1, totalPages - 2 - (SIBLING_COUNT * 2) ); // 例: SIBLING=1, 显示 1 ... (N-3 N-2 N-1) N
            }


            for (let i = startPage; i <= endPage; i++) {
                paginationControlsContainer.appendChild(createPageItem(i + 1, i, i === currentPageIndex));
            }

            // 4. 右边省略号和其后的页码 (如果需要)
            if (currentPageIndex < totalPages - SIBLING_COUNT - 2) { // (当前页索引 < (总页数 - 1(最后一页) - 兄弟数 - 1)) 才显示右省略号
                // (currentPageIndex < totalPages - 3 for SIBLING_COUNT=1)
                paginationControlsContainer.appendChild(createPageItem(DOTS, undefined, false, true, true));
            }

            // 5. 渲染最后一页 (除非总页数就是1页，但这种情况已在最开始totalPages <= ...中处理)
            if (totalPages > 1) { // 避免总页数为1时重复添加
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
            let exportUrl = '/api/system/audit-logs/export';
            const params = [];

            const username = usernameFilterInput.value.trim();
            const action = actionFilterInput.value.trim();
            const startDate = startDateFilterInput.value;
            const endDate = endDateFilterInput.value;

            if (username) params.push(`username=${encodeURIComponent(username)}`);
            if (action) params.push(`action=${encodeURIComponent(action)}`);

            // 对于导出，日期范围的策略也需要明确
            if (startDate && endDate) {
                params.push(`startDate=${startDate}`);
                params.push(`endDate=${endDate}`);
            } else if (startDate) {
                params.push(`startDate=${startDate}`); // 或根据API设计决定是否允许单个日期
                console.warn("导出：仅填写了开始日期。");
            } else if (endDate) {
                params.push(`endDate=${endDate}`); // 或根据API设计决定是否允许单个日期
                console.warn("导出：仅填写了结束日期。");
            }

            if (params.length > 0) {
                exportUrl += `?${params.join('&')}`;
            }

            console.log("Exporting with URL: ", exportUrl); // 调试用
            window.location.href = exportUrl; // 触发浏览器下载
        });
    }

    // 页面初始加载时获取第一页数据
    fetchAndDisplayLogs(currentPage, DEFAULT_PAGE_SIZE);

    // 为详情列添加点击展开/提示功能 (如果需要更复杂的交互)
    // 这个部分可以保留，或者根据需要实现更复杂的交互如 Bootstrap Popover
    if (auditLogTableBody) {
        auditLogTableBody.addEventListener('click', function(event) {
            const targetCell = event.target.closest('td'); // 点击的是td
            // 假设详情列的父元素是tr，并且该td有特定的class或通过列索引判断
            // 这里简单判断父元素是否是td，并且内容和title相同（表示可能是截断的内容）
            if (targetCell && targetCell.textContent !== targetCell.title && targetCell.title) {
                // 检查是否是详情列，例如通过列索引 (假设详情列是第4列，索引为3)
                if (Array.from(targetCell.parentNode.children).indexOf(targetCell) === 3) {
                    // 简单地用 alert 显示完整内容
                    // 更佳方案是使用Bootstrap Popover/Tooltip或自定义模态框
                    alert('完整详情: \n' + targetCell.title);
                }
            }
        });
    }
});