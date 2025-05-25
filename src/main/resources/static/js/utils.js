// static/js/utils.js

/**
 * 显示一个Bootstrap样式的警告消息。
 * @param {string} message - 要显示的消息。
 * @param {string} type - 警告类型 ('success', 'danger', 'warning', 'info')，默认为 'info'。
 * @param {string} containerId - 警告消息容器的ID (可选, 如果不提供，则使用 alert())。
 */
function showAlert(message, type = 'info', containerId = 'globalAlertContainer') {
    const alertContainer = document.getElementById(containerId);
    if (alertContainer) {
        const wrapper = document.createElement('div');
        wrapper.innerHTML = `
            <div class="alert alert-${type} alert-dismissible fade show" role="alert" style="margin-top: 10px;">
                ${message}
                <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
            </div>
        `;
        // 追加新的警报，而不是替换，这样可以显示多个警报
        alertContainer.appendChild(wrapper.firstChild);

        // 5秒后自动移除此特定警报 (可选)
        setTimeout(() => {
            const activeAlert = wrapper.firstChild; // 获取实际的alert div
            if (activeAlert && bootstrap.Alert.getInstance(activeAlert)) {
                bootstrap.Alert.getInstance(activeAlert).close();
            } else if (activeAlert && activeAlert.parentNode) { // 如果没有BS实例但元素存在
                activeAlert.parentNode.removeChild(activeAlert);
            }
        }, 7000); // 延长到7秒
        return;
    }
    // Fallback to standard alert if no container or container not found
    alert(`${type.toUpperCase()}: ${message}`);
}

/**
 * 封装的 Fetch API 调用函数
 * @param {string} url - 请求的URL
 * @param {object} options - Fetch API的选项对象 (method, headers, body等)
 * @param {string} alertContainerId - 用于显示错误消息的容器ID (可选)
 * @returns {Promise<any>} - 解析后的JSON数据或null（对于204 No Content）
 * @throws {Error} - 如果网络或HTTP错误发生
 */
async function authenticatedFetch(url, options = {}, alertContainerId = 'globalAlertContainer') {
    const defaultHeaders = {
        // 'Content-Type': 'application/json', // 会根据 options.body 类型自动设置
        // 'X-CSRF-TOKEN': csrfToken // 如果启用了CSRF，在这里添加
    };
    options.headers = { ...defaultHeaders, ...options.headers };

    // 如果body是对象且不是FormData，则自动JSON.stringify并设置Content-Type
    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        if (!options.headers['Content-Type'] || options.headers['Content-Type'].toLowerCase() !== 'application/json') {
            options.headers['Content-Type'] = 'application/json';
        }
        options.body = JSON.stringify(options.body);
    }


    try {
        const response = await fetch(url, options);

        if (!response.ok) {
            let errorMessage = `HTTP错误! 状态: ${response.status} ${response.statusText}`;
            try {
                const errorData = await response.json();
                errorMessage = errorData.message || (errorData.errors ? errorData.errors.join(', ') : (errorData.error || errorMessage));
            } catch (e) {
                // 响应体不是JSON或解析失败，使用原始HTTP错误信息
            }
            console.error('API 错误:', errorMessage, 'URL:', url, '选项:', options);
            showAlert(errorMessage, 'danger', alertContainerId);
            throw new Error(errorMessage); // 抛出错误以便调用者可以捕获
        }

        if (response.status === 204) { // No Content
            return null;
        }
        // 尝试解析JSON，如果响应体为空，则返回null
        const text = await response.text();
        if (!text) {
            return null;
        }
        return JSON.parse(text);

    } catch (error) {
        // 如果是网络错误等 (fetch本身抛出的异常), 并且不是我们上面已处理的 HTTP 错误
        if (!error.message.includes('HTTP错误!')) {
            console.error('网络或其他 fetch 错误:', error, 'URL:', url, '选项:', options);
            showAlert(error.message || '网络请求失败，请检查您的连接。', 'danger', alertContainerId);
        }
        throw error; // 重新抛出，让调用者知道发生了错误
    }
}

/**
 * 切换加载状态指示器。
 * @param {boolean} isLoading - 是否显示加载状态。
 * @param {string | HTMLElement} buttonOrId - 触发操作的按钮 (元素或ID) (可选, 用于禁用/启用按钮)。
 * @param {string | HTMLElement} spinnerOrId - 加载指示器的元素 (元素或ID) (可选)。
 */
function toggleLoading(isLoading, buttonOrId = null, spinnerOrId = null) {
    const button = (typeof buttonOrId === 'string') ? document.getElementById(buttonOrId) : buttonOrId;
    const spinner = (typeof spinnerOrId === 'string') ? document.getElementById(spinnerOrId) : spinnerOrId;

    if (button) {
        button.disabled = isLoading;
        if (isLoading) {
            // 可选：保存原始文本并在结束后恢复
            if (!button.dataset.originalText) {
                button.dataset.originalText = button.innerHTML;
            }
            button.innerHTML = `<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> 加载中...`;
        } else {
            if (button.dataset.originalText) {
                button.innerHTML = button.dataset.originalText;
                // delete button.dataset.originalText; // 清理
            }
        }
    }
    if (spinner) {
        spinner.style.display = isLoading ? 'inline-block' : 'none';
    }
}


/**
 * 渲染分页控件。
 * @param {object} pageData - 后端返回的分页数据对象。
 * @param {string} containerId - 分页控件容器的ID。
 * @param {function} fetchFunction - 点击页码时调用的函数 (接收 page 和 size 作为参数)。
 * @param {number} defaultPageSize - 每页默认条数。
 */
function renderPaginationControls(pageData, containerId, fetchFunction, defaultPageSize) {
    const paginationContainer = document.getElementById(containerId);
    if (!paginationContainer) {
        console.warn(`Pagination container with id '${containerId}' not found.`);
        return;
    }
    paginationContainer.innerHTML = ''; // 清空

    if (!pageData || pageData.totalPages === undefined || pageData.totalPages === 0) { // 如果总页数为0也清空
        return;
    }

    const { totalPages, number: currentPageIndex, first: isFirstPage, last: isLastPage, size } = pageData;
    const pageSizeToUse = size || defaultPageSize;


    // 上一页
    const prevLi = document.createElement('li');
    prevLi.className = `page-item ${isFirstPage ? 'disabled' : ''}`;
    const prevLink = document.createElement('a');
    prevLink.className = 'page-link';
    prevLink.href = '#';
    prevLink.setAttribute('aria-label', 'Previous');
    prevLink.innerHTML = '<span aria-hidden="true">&laquo;</span>';
    if (!isFirstPage) {
        prevLink.addEventListener('click', (e) => {
            e.preventDefault();
            fetchFunction(currentPageIndex - 1, pageSizeToUse);
        });
    }
    prevLi.appendChild(prevLink);
    paginationContainer.appendChild(prevLi);

    // 页码逻辑 (更智能的显示，例如：首页 ... prev current next ... 尾页)
    const MAX_VISIBLE_PAGES = 5; // 最多显示的页码按钮数（不包括上一页/下一页/首页/尾页）
    let startPage = Math.max(0, currentPageIndex - Math.floor(MAX_VISIBLE_PAGES / 2));
    let endPage = Math.min(totalPages - 1, startPage + MAX_VISIBLE_PAGES - 1);

    if (endPage - startPage + 1 < MAX_VISIBLE_PAGES) {
        startPage = Math.max(0, endPage - MAX_VISIBLE_PAGES + 1);
    }

    // 首页按钮
    if (startPage > 0) {
        const firstPageLi = document.createElement('li');
        firstPageLi.className = 'page-item';
        const firstPageLink = document.createElement('a');
        firstPageLink.className = 'page-link';
        firstPageLink.href = '#';
        firstPageLink.textContent = '1';
        firstPageLink.addEventListener('click', (e) => {
            e.preventDefault();
            fetchFunction(0, pageSizeToUse);
        });
        firstPageLi.appendChild(firstPageLink);
        paginationContainer.appendChild(firstPageLi);
        if (startPage > 1) { // 如果第一页和页码起点之间还有间隔
            const ellipsisLi = document.createElement('li');
            ellipsisLi.className = 'page-item disabled';
            ellipsisLi.innerHTML = '<span class="page-link">...</span>';
            paginationContainer.appendChild(ellipsisLi);
        }
    }

    for (let i = startPage; i <= endPage; i++) {
        const pageLi = document.createElement('li');
        pageLi.className = `page-item ${i === currentPageIndex ? 'active' : ''}`;
        const pageLink = document.createElement('a');
        pageLink.className = 'page-link';
        pageLink.href = '#';
        pageLink.textContent = i + 1;
        pageLink.dataset.page = i;
        pageLink.addEventListener('click', (e) => {
            e.preventDefault();
            fetchFunction(parseInt(e.target.dataset.page), pageSizeToUse);
        });
        pageLi.appendChild(pageLink);
        paginationContainer.appendChild(pageLi);
    }

    // 尾页按钮
    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) { // 如果最后一页和页码终点之间还有间隔
            const ellipsisLi = document.createElement('li');
            ellipsisLi.className = 'page-item disabled';
            ellipsisLi.innerHTML = '<span class="page-link">...</span>';
            paginationContainer.appendChild(ellipsisLi);
        }
        const lastPageLi = document.createElement('li');
        lastPageLi.className = 'page-item';
        const lastPageLink = document.createElement('a');
        lastPageLink.className = 'page-link';
        lastPageLink.href = '#';
        lastPageLink.textContent = totalPages;
        lastPageLink.addEventListener('click', (e) => {
            e.preventDefault();
            fetchFunction(totalPages - 1, pageSizeToUse);
        });
        lastPageLi.appendChild(lastPageLink);
        paginationContainer.appendChild(lastPageLi);
    }


    // 下一页
    const nextLi = document.createElement('li');
    nextLi.className = `page-item ${isLastPage ? 'disabled' : ''}`;
    const nextLink = document.createElement('a');
    nextLink.className = 'page-link';
    nextLink.href = '#';
    nextLink.setAttribute('aria-label', 'Next');
    nextLink.innerHTML = '<span aria-hidden="true">&raquo;</span>';
    if (!isLastPage) {
        nextLink.addEventListener('click', (e) => {
            e.preventDefault();
            fetchFunction(currentPageIndex + 1, pageSizeToUse);
        });
    }
    nextLi.appendChild(nextLink);
    paginationContainer.appendChild(nextLi);
}

/**
 * 清空表单字段并移除 Bootstrap 验证状态。
 * @param {HTMLFormElement} formElement - 要重置的表单元素。
 */
function resetForm(formElement) {
    if (formElement && typeof formElement.reset === 'function') {
        formElement.reset();
    }
    // 移除Bootstrap的验证类 (如果使用了)
    formElement.querySelectorAll('.is-invalid, .is-valid').forEach(el => {
        el.classList.remove('is-invalid', 'is-valid');
    });
    formElement.classList.remove('was-validated');
}

/**
 * 简易的客户端表单验证 (配合Bootstrap样式)
 * @param {HTMLFormElement} formElement - 要验证的表单元素
 * @returns {boolean} - 表单是否有效
 */
function validateForm(formElement) {
    let isValid = true;
    formElement.classList.add('was-validated'); // 触发Bootstrap内置的验证样式

    // 检查所有必填字段
    formElement.querySelectorAll('[required]').forEach(input => {
        if (!input.value.trim()) {
            input.classList.add('is-invalid');
            // 确保错误消息容器存在 (Bootstrap 通常会自动处理，但可以自定义)
            let feedback = input.nextElementSibling;
            if (!feedback || !feedback.classList.contains('invalid-feedback')) {
                feedback = document.createElement('div');
                feedback.classList.add('invalid-feedback');
                input.parentNode.insertBefore(feedback, input.nextSibling);
            }
            feedback.textContent = input.dataset.requiredMessage || '此字段不能为空。'; // 可以通过 data-required-message 自定义消息
            isValid = false;
        } else {
            input.classList.remove('is-invalid');
            input.classList.add('is-valid');
        }
    });
    // 可以添加更多自定义验证规则，例如密码匹配、邮箱格式等
    return isValid;
}