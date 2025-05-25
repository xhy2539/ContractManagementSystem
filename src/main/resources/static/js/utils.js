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
        alertContainer.appendChild(wrapper.firstChild);
        setTimeout(() => {
            const activeAlert = wrapper.firstChild;
            if (activeAlert && bootstrap.Alert.getInstance(activeAlert)) {
                bootstrap.Alert.getInstance(activeAlert).close();
            } else if (activeAlert && activeAlert.parentNode) {
                activeAlert.parentNode.removeChild(activeAlert);
            }
        }, 7000);
        return;
    }
    alert(`${type.toUpperCase()}: ${message}`);
}

/**
 * 封装的 Fetch API 调用函数
 * @param {string} url - 请求的URL
 * @param {object} options - Fetch API的选项对象 (method, headers, body等)
 * @param {string} alertContainerId - 用于显示错误消息的容器ID (可选)
 * @returns {Promise<any>} - 解析后的JSON数据或null（对于204 No Content或空响应体）
 * @throws {Error} - 如果网络或HTTP错误发生
 */
async function authenticatedFetch(url, options = {}, alertContainerId = 'globalAlertContainer') {
    console.log(`[authenticatedFetch] Requesting URL: ${url}`, "Options:", options);
    const defaultHeaders = {
        // 'X-CSRF-TOKEN': csrfToken // 如果启用了CSRF
    };
    options.headers = { ...defaultHeaders, ...options.headers };

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
        if (!options.headers['Content-Type'] || options.headers['Content-Type'].toLowerCase() !== 'application/json') {
            options.headers['Content-Type'] = 'application/json';
        }
        options.body = JSON.stringify(options.body);
        console.log(`[authenticatedFetch] Request body (JSON stringified): ${options.body}`);
    }

    try {
        const response = await fetch(url, options);
        console.log(`[authenticatedFetch] Response status: ${response.status} for URL: ${url}`);

        if (!response.ok) { // response.ok is true if status is 200-299
            let errorMessage = `HTTP错误! 状态: ${response.status} ${response.statusText}`;
            try {
                const errorData = await response.json(); // Try to parse error response as JSON
                console.log("[authenticatedFetch] Error data from response:", errorData);
                errorMessage = errorData.message || (errorData.errors ? errorData.errors.join(', ') : (errorData.error || errorMessage));
            } catch (e) {
                console.warn("[authenticatedFetch] Could not parse error response as JSON. Using status text.", e);
                // If error response is not JSON, use the generic HTTP error
            }
            console.error('[authenticatedFetch] API 错误:', errorMessage, 'URL:', url, '选项:', options);
            showAlert(errorMessage, 'danger', alertContainerId);
            throw new Error(errorMessage); // Critical: throw error to be caught by calling function
        }

        // Handle successful responses that might not have a body (e.g., 201 Created, 204 No Content)
        if (response.status === 204) {
            console.log("[authenticatedFetch] Received 204 No Content.");
            return null; // No content to parse
        }

        const text = await response.text();
        if (!text) {
            // This can happen for 201 Created if the backend doesn't return the created resource in the body
            console.log(`[authenticatedFetch] Received successful response (${response.status}) with empty body.`);
            return response.status === 201 ? { success: true, status: response.status } : null; // Indicate success for 201
        }

        try {
            const jsonData = JSON.parse(text);
            console.log("[authenticatedFetch] Parsed JSON response:", jsonData);
            return jsonData;
        } catch (e) {
            console.error("[authenticatedFetch] Failed to parse response text as JSON, even though response was ok.", e, "Response text:", text);
            // If parsing fails but status was ok, it might be an issue with backend returning non-JSON for a success case
            // Or it could be a case like 201 Created that DID return a body, but it wasn't valid JSON.
            // Depending on API contract, you might want to throw an error or return the text.
            // For robustness, if it was a 200/201 and parsing failed, it's an issue.
            showAlert('成功响应，但无法解析返回数据。', 'warning', alertContainerId);
            throw new Error('成功响应，但无法解析返回数据。');
        }

    } catch (error) {
        // This catches network errors or errors thrown from the !response.ok block or parsing issues
        if (!error.message.includes('HTTP错误!') && !error.message.includes('无法解析返回数据')) { // Avoid double alerting for HTTP errors already handled
            console.error('[authenticatedFetch] 网络或其他 fetch 错误:', error, 'URL:', url, '选项:', options);
            showAlert(error.message || '网络请求失败，请检查您的连接。', 'danger', alertContainerId);
        }
        throw error; // Re-throw to ensure calling function's catch block is triggered
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
            if (!button.dataset.originalText) {
                button.dataset.originalText = button.innerHTML;
            }
            button.innerHTML = `<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> 加载中...`;
        } else {
            if (button.dataset.originalText) {
                button.innerHTML = button.dataset.originalText;
                // delete button.dataset.originalText; // Optional: clean up
            }
        }
    }
    if (spinner) {
        spinner.style.display = isLoading ? 'inline-block' : 'none'; // Or 'flex' etc. depending on spinner type
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
    paginationContainer.innerHTML = '';

    if (!pageData || pageData.totalPages === undefined || pageData.totalPages === 0) {
        return;
    }

    const { totalPages, number: currentPageIndex, first: isFirstPage, last: isLastPage, size } = pageData;
    const pageSizeToUse = size || defaultPageSize;

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

    const MAX_VISIBLE_PAGES = 5;
    let startPage = Math.max(0, currentPageIndex - Math.floor(MAX_VISIBLE_PAGES / 2));
    let endPage = Math.min(totalPages - 1, startPage + MAX_VISIBLE_PAGES - 1);

    if (endPage - startPage + 1 < MAX_VISIBLE_PAGES) {
        startPage = Math.max(0, endPage - MAX_VISIBLE_PAGES + 1);
    }

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
        if (startPage > 1) {
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

    if (endPage < totalPages - 1) {
        if (endPage < totalPages - 2) {
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
    formElement.querySelectorAll('.is-invalid, .is-valid').forEach(el => {
        el.classList.remove('is-invalid', 'is-valid');
    });
    formElement.classList.remove('was-validated'); // 移除Bootstrap的已验证状态
}

/**
 * 简易的客户端表单验证 (配合Bootstrap样式)
 * @param {HTMLFormElement} formElement - 要验证的表单元素
 * @returns {boolean} - 表单是否有效
 */
function validateForm(formElement) {
    let isValid = true;
    // 先移除所有现有的 is-invalid 和 is-valid，避免累积
    formElement.querySelectorAll('.is-invalid, .is-valid').forEach(el => {
        el.classList.remove('is-invalid', 'is-valid');
    });
    formElement.classList.remove('was-validated'); // 先移除，再添加，确保状态正确

    formElement.classList.add('was-validated'); // 触发Bootstrap内置的验证样式

    formElement.querySelectorAll('input[required], textarea[required], select[required]').forEach(input => {
        if (!input.value.trim()) { // 对所有输入类型检查trim后的值
            input.classList.add('is-invalid');
            let feedback = input.nextElementSibling;
            if (!feedback || !feedback.classList.contains('invalid-feedback')) {
                // 如果没有标准的feedback元素，可以在这里动态创建或依赖Bootstrap的默认行为
            }
            // feedback.textContent = input.dataset.requiredMessage || '此字段不能为空。'; // 自定义消息
            isValid = false;
        } else {
            // 只有通过了 required 检查，才标记为 valid，其他类型的验证（如pattern, minlength）由浏览器或后续JS处理
            input.classList.add('is-valid');
        }
    });
    // 检查其他HTML5约束，如 type="email", pattern, minlength, maxlength
    // formElement.checkValidity() 可以检查所有HTML5约束，但它不会自动添加 is-invalid 类
    if (!formElement.checkValidity()) {
        isValid = false;
        // 需要手动遍历并为不符合条件的字段添加 is-invalid，或者依赖 was-validated 类的效果
        // 对于更复杂的场景，通常使用专门的表单验证库
    }

    return isValid;
}