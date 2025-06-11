// static/js/functionality-management.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const functionalitiesTableBody = document.querySelector('#functionalitiesTable tbody');
    const functionalityFormModalEl = document.getElementById('functionalityFormModal');
    const functionalityFormModal = new bootstrap.Modal(functionalityFormModalEl, {
        backdrop: false, // 禁用背景遮罩
        scroll: true    // 允许页面滚动
    });
    const functionalityForm = document.getElementById('functionalityForm');
    const addFunctionalityBtn = document.getElementById('addFunctionalityBtn');

    const functionalityIdInput = document.getElementById('functionalityId');
    const functionalityNumInput = document.getElementById('functionalityNum');
    const functionalityNameInput = document.getElementById('functionalityName');
    const functionalityUrlInput = document.getElementById('functionalityUrl');
    const functionalityDescriptionInput = document.getElementById('functionalityDescription');
    const functionalityFormModalLabel = document.getElementById('functionalityFormModalLabel');

    const paginationControlsContainerId = 'paginationControlsFunctionality';
    const globalAlertContainerId = 'globalAlertContainer';
    const functionalitiesTableSpinnerId = 'functionalitiesTableSpinner';

    // Search elements
    const funcNumSearchInput = document.getElementById('funcNumSearch');
    const funcNameSearchInput = document.getElementById('funcNameSearch');
    const funcDescriptionSearchInput = document.getElementById('funcDescriptionSearch');
    const searchFunctionalityBtn = document.getElementById('searchFunctionalityBtn');
    const functionalityFilterForm = document.getElementById('functionalityFilterForm');


    // --- State & Configuration ---
    let currentPageFunctionality = 0;
    const DEFAULT_PAGE_SIZE_FUNCTIONALITY = 10;

    // --- API Endpoints ---
    const API_FUNCTIONALITIES_URL = '/api/system/functionalities';

    // --- Initialization ---
    async function initializePage() {
        await fetchFunctionalities(); // 页面加载时获取第一页数据

        if (addFunctionalityBtn) {
            addFunctionalityBtn.addEventListener('click', handleOpenCreateFunctionalityModal);
        }
        if (functionalityForm) {
            functionalityForm.addEventListener('submit', handleFunctionalityFormSubmit);
        }
        if (functionalitiesTableBody) {
            functionalitiesTableBody.addEventListener('click', handleTableActions);
        }

        if (searchFunctionalityBtn) {
            searchFunctionalityBtn.addEventListener('click', () => fetchFunctionalities(0));
        }
        if (functionalityFilterForm) {
            functionalityFilterForm.addEventListener('reset', () => {
                setTimeout(() => fetchFunctionalities(0), 0);
            });
            [funcNumSearchInput, funcNameSearchInput, funcDescriptionSearchInput].forEach(input => {
                if (input) input.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        fetchFunctionalities(0);
                    }
                });
            });
        }

        // 添加模态框事件监听
        const modalElement = document.querySelector('.modal');
        if (modalElement) {
            modalElement.addEventListener('show.bs.modal', function () {
                document.body.style.overflow = 'auto';
                document.body.style.paddingRight = '0';
            });
            
            modalElement.addEventListener('shown.bs.modal', function () {
                document.body.style.overflow = 'auto';
                document.body.style.paddingRight = '0';
                // 移除 Bootstrap 自动添加的样式
                document.body.classList.remove('modal-open');
            });

            modalElement.addEventListener('hidden.bs.modal', function () {
                document.body.style.overflow = 'auto';
                document.body.style.paddingRight = '0';
                document.body.classList.remove('modal-open');
            });
        }

        // 初始化功能表单模态框
        const functionalityFormModalElement = document.getElementById('functionalityFormModal');
        if (functionalityFormModalElement) {
            functionalityFormModal = new bootstrap.Modal(functionalityFormModalElement, {
                backdrop: true,
                keyboard: true,
                focus: true
            });
        }
    }

    // --- Data Fetching and Rendering ---
    async function fetchFunctionalities(page = 0, size = DEFAULT_PAGE_SIZE_FUNCTIONALITY) {
        currentPageFunctionality = page;
        const numSearchVal = funcNumSearchInput ? funcNumSearchInput.value.trim() : '';
        const nameSearchVal = funcNameSearchInput ? funcNameSearchInput.value.trim() : '';
        const descriptionSearchVal = funcDescriptionSearchInput ? funcDescriptionSearchInput.value.trim() : '';

        // 修改这里的默认排序参数
        let queryParams = `page=${page}&size=${size}&sort=id,asc`; // 默认按ID升序

        if (numSearchVal) {
            queryParams += `&numSearch=${encodeURIComponent(numSearchVal)}`;
        }
        if (nameSearchVal) {
            queryParams += `&nameSearch=${encodeURIComponent(nameSearchVal)}`;
        }
        if (descriptionSearchVal) {
            queryParams += `&descriptionSearch=${encodeURIComponent(descriptionSearchVal)}`;
        }

        toggleLoading(true, null, functionalitiesTableSpinnerId);
        try {
            const pageData = await authenticatedFetch(`${API_FUNCTIONALITIES_URL}?${queryParams}`, {}, globalAlertContainerId);
            if (pageData && pageData.content) {
                renderFunctionalitiesTable(pageData.content);
                renderPaginationControls(pageData, paginationControlsContainerId, fetchFunctionalities, DEFAULT_PAGE_SIZE_FUNCTIONALITY);
            } else {
                functionalitiesTableBody.innerHTML = `<tr><td colspan="6" class="text-center">无功能数据。</td></tr>`;
                renderPaginationControls(null, paginationControlsContainerId, fetchFunctionalities, DEFAULT_PAGE_SIZE_FUNCTIONALITY);
            }
        } catch (error) {
            functionalitiesTableBody.innerHTML = `<tr><td colspan="6" class="text-center text-danger">加载功能列表失败。</td></tr>`;
            renderPaginationControls(null, paginationControlsContainerId, fetchFunctionalities, DEFAULT_PAGE_SIZE_FUNCTIONALITY);
        } finally {
            toggleLoading(false, null, functionalitiesTableSpinnerId);
        }
    }

    function renderFunctionalitiesTable(functionalities) {
        functionalitiesTableBody.innerHTML = '';
        if (functionalities && functionalities.length > 0) {
            functionalities.forEach(func => {
                const row = functionalitiesTableBody.insertRow();
                row.insertCell().textContent = func.id;
                row.insertCell().textContent = func.num;
                row.insertCell().textContent = func.name;
                row.insertCell().textContent = func.url || 'N/A';

                const descCell = row.insertCell();
                const descriptionText = func.description || 'N/A';
                descCell.textContent = descriptionText;
                if (descriptionText.length > 40) {
                    descCell.title = descriptionText;
                    descCell.textContent = descriptionText.substring(0, 37) + '...';
                }


                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons', 'text-nowrap');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-func-btn" data-func-id="${func.id}" title="编辑功能"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-danger delete-func-btn" data-func-id="${func.id}" data-func-name="${func.name}" title="删除功能"><i class="bi bi-trash"></i></button>
                `;
            });
        } else {
            if (functionalitiesTableBody.innerHTML === '') {
                functionalitiesTableBody.innerHTML = `<tr><td colspan="6" class="text-center">无功能数据。</td></tr>`;
            }
        }
    }

    // --- Event Handlers ---
    function handleOpenCreateFunctionalityModal() {
        resetForm(functionalityForm);
        functionalityIdInput.value = '';
        functionalityFormModalLabel.textContent = '添加新功能';
        document.body.classList.remove('modal-open');
        document.body.style.overflow = 'auto';
        document.body.style.paddingRight = '0';
        functionalityFormModal.show();
    }

    async function handleFunctionalityFormSubmit(event) {
        event.preventDefault();
        if (!validateForm(functionalityForm)) {
            showAlert('请检查表单中的必填项。', 'warning', globalAlertContainerId);
            return;
        }

        const saveButton = functionalityForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const currentFuncId = functionalityIdInput.value;
        const num = functionalityNumInput.value;
        const name = functionalityNameInput.value;
        const description = functionalityDescriptionInput.value;

        const funcData = {
            num,
            name,
            description
        };

        let url = API_FUNCTIONALITIES_URL;
        let method = 'POST';

        if (currentFuncId) { // Edit mode
            url = `${API_FUNCTIONALITIES_URL}/${currentFuncId}`;
            method = 'PUT';
        }

        try {
            await authenticatedFetch(url, { method, body: funcData }, globalAlertContainerId);
            showAlert(currentFuncId ? '功能更新成功！' : '功能创建成功！', 'success', globalAlertContainerId);
            fetchFunctionalities(currentFuncId ? currentPageFunctionality : 0);
            functionalityFormModal.hide();
            document.body.classList.remove('modal-open');
            document.body.style.overflow = 'auto';
            document.body.style.paddingRight = '0';
        } catch (error) {
            console.error("保存功能失败:", error);
            showAlert(currentFuncId ? '功能更新失败，详情请查看控制台。' : '功能创建失败，详情请查看控制台。', 'danger', globalAlertContainerId);
        } finally {
            toggleLoading(false, saveButton);
        }
    }

    async function handleTableActions(event) {
        const targetButton = event.target.closest('button');
        if (!targetButton) return;

        const funcId = targetButton.dataset.funcId;

        if (targetButton.classList.contains('edit-func-btn')) {
            toggleLoading(true, targetButton);
            try {
                const functionality = await authenticatedFetch(`${API_FUNCTIONALITIES_URL}/${funcId}`, {}, globalAlertContainerId);
                if (functionality) {
                    resetForm(functionalityForm);
                    functionalityFormModalLabel.textContent = '编辑功能';
                    functionalityIdInput.value = functionality.id;
                    functionalityNumInput.value = functionality.num || '';
                    functionalityNameInput.value = functionality.name;
                    if (functionalityUrlInput) {
                        functionalityUrlInput.value = functionality.url || '';
                    }
                    functionalityDescriptionInput.value = functionality.description || '';
                    document.body.classList.remove('modal-open');
                    document.body.style.overflow = 'auto';
                    document.body.style.paddingRight = '0';
                    functionalityFormModal.show();
                } else {
                    showAlert('无法加载功能信息进行编辑。', 'warning', globalAlertContainerId);
                }
            } catch (error) {
                console.error("编辑功能 - 加载功能信息失败:", error);
                showAlert('加载功能信息进行编辑失败，详情请查看控制台。', 'danger', globalAlertContainerId);
            } finally {
                toggleLoading(false, targetButton);
            }
        } else if (targetButton.classList.contains('delete-func-btn')) {
            const funcName = targetButton.dataset.funcName;
            if (confirm(`确定要删除功能 "${funcName}" (ID: ${funcId})吗？此操作可能会影响已分配此功能的角色。`)) {
                toggleLoading(true, targetButton);
                try {
                    await authenticatedFetch(`${API_FUNCTIONALITIES_URL}/${funcId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`功能 "${funcName}" 删除成功！`, 'success', globalAlertContainerId);
                    const currentRows = functionalitiesTableBody.rows.length;
                    if (currentRows === 1 && currentPageFunctionality > 0) {
                        fetchFunctionalities(currentPageFunctionality - 1);
                    } else {
                        fetchFunctionalities(currentPageFunctionality);
                    }
                } catch (error) {
                    // authenticatedFetch 已处理
                } finally {
                    toggleLoading(false, targetButton);
                }
            }
        }
    }

    // --- Initial Page Load ---
    initializePage();
});