// static/js/role-management.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const rolesTableBody = document.querySelector('#rolesTable tbody');
    const roleFormModalEl = document.getElementById('roleFormModal');
    const roleFormModal = new bootstrap.Modal(roleFormModalEl);
    const roleForm = document.getElementById('roleForm');
    const addRoleBtn = document.getElementById('addRoleBtn');
    const functionalitiesCheckboxesContainer = document.getElementById('functionalitiesCheckboxes');

    const roleIdInput = document.getElementById('roleId');
    const roleNameInput = document.getElementById('roleName');
    const roleDescriptionInput = document.getElementById('roleDescription');
    const roleFormModalLabel = document.getElementById('roleFormModalLabel');

    // Filter and Search Elements
    const roleFilterForm = document.getElementById('roleFilterForm');
    const roleNameSearchInput = document.getElementById('roleNameSearch');
    const roleDescriptionSearchInput = document.getElementById('roleDescriptionSearch');
    const searchRoleBtn = document.getElementById('searchRoleBtn');

    const paginationControlsContainerId = 'paginationControlsRole';
    const globalAlertContainerId = 'globalAlertContainer';
    const rolesTableSpinnerId = 'rolesTableSpinner';

    // --- State & Configuration ---
    let allFunctionalities = [];
    let currentPageRole = 0;
    const DEFAULT_PAGE_SIZE_ROLE = 10; // 与其他管理页面保持一致或自定义

    // --- API Endpoints ---
    const API_ROLES_URL = '/api/system/roles';
    const API_FUNCTIONALITIES_URL = '/api/system/functionalities';

    // --- Initialization ---
    async function initializePage() {
        await loadAllFunctionalities();
        await fetchRoles(); // 初始加载第一页

        if (addRoleBtn) {
            addRoleBtn.addEventListener('click', handleOpenCreateRoleModal);
        }
        if (roleForm) {
            roleForm.addEventListener('submit', handleRoleFormSubmit);
        }
        if (rolesTableBody) {
            rolesTableBody.addEventListener('click', handleTableActions);
        }
        if (searchRoleBtn) {
            searchRoleBtn.addEventListener('click', () => fetchRoles(0)); // 搜索时从第一页开始
        }
        if (roleFilterForm) {
            roleFilterForm.addEventListener('reset', () => {
                // 清空表单后，延迟执行以确保表单值已重置
                setTimeout(() => fetchRoles(0), 0);
            });
            // 可选：为输入框添加回车搜索
            [roleNameSearchInput, roleDescriptionSearchInput].forEach(input => {
                if (input) input.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        fetchRoles(0);
                    }
                });
            });
        }
    }

    // --- Data Fetching and Rendering ---
    async function loadAllFunctionalities() {
        try {
            // 假设 authenticatedFetch 在 utils.js 中定义并处理 token 等
            // 如果 functionalities 列表本身也很多，也应该考虑分页加载或按需加载
            const funcData = await authenticatedFetch(API_FUNCTIONALITIES_URL + '?size=1000', {}, globalAlertContainerId); // 获取足够多的功能
            allFunctionalities = funcData && funcData.content ? funcData.content : (Array.isArray(funcData) ? funcData : []);
            if (!allFunctionalities) allFunctionalities = [];
        } catch (error) {
            console.error("Failed to load functionalities list for roles.");
            allFunctionalities = [];
            // showAlert("加载功能列表失败，角色创建/编辑时的功能分配可能不完整。", "warning", globalAlertContainerId);
        }
    }

    async function fetchRoles(page = 0, size = DEFAULT_PAGE_SIZE_ROLE) {
        currentPageRole = page;
        const nameSearchVal = roleNameSearchInput ? roleNameSearchInput.value.trim() : '';
        const descriptionSearchVal = roleDescriptionSearchInput ? roleDescriptionSearchInput.value.trim() : '';

        let queryParams = `page=${page}&size=${size}&sort=name,asc`; // 默认按角色名称升序

        if (nameSearchVal) {
            queryParams += `&nameSearch=${encodeURIComponent(nameSearchVal)}`;
        }
        if (descriptionSearchVal) {
            queryParams += `&descriptionSearch=${encodeURIComponent(descriptionSearchVal)}`;
        }

        toggleLoading(true, null, rolesTableSpinnerId);
        try {
            const pageData = await authenticatedFetch(`${API_ROLES_URL}?${queryParams}`, {}, globalAlertContainerId);
            if (pageData && pageData.content) {
                renderRolesTable(pageData.content);
                renderPaginationControls(pageData, paginationControlsContainerId, fetchRoles, DEFAULT_PAGE_SIZE_ROLE);
            } else {
                rolesTableBody.innerHTML = `<tr><td colspan="5" class="text-center">无角色数据。</td></tr>`;
                renderPaginationControls(null, paginationControlsContainerId, fetchRoles, DEFAULT_PAGE_SIZE_ROLE); // 清空分页
            }
        } catch (error) {
            rolesTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载角色列表失败。</td></tr>`;
            renderPaginationControls(null, paginationControlsContainerId, fetchRoles, DEFAULT_PAGE_SIZE_ROLE); // 清空分页
        } finally {
            toggleLoading(false, null, rolesTableSpinnerId);
        }
    }

    function renderRolesTable(roles) {
        rolesTableBody.innerHTML = '';
        if (roles && roles.length > 0) {
            roles.forEach(role => {
                const row = rolesTableBody.insertRow();
                row.insertCell().textContent = role.id;
                row.insertCell().textContent = role.name;
                row.insertCell().textContent = role.description || 'N/A';

                const functionalityNames = role.functionalities && Array.isArray(role.functionalities)
                    ? role.functionalities.map(f => f.name).join(', ')
                    : '无';
                row.insertCell().textContent = functionalityNames || '无';

                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons', 'text-nowrap');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-role-btn" data-role-id="${role.id}" title="编辑角色"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-danger delete-role-btn" data-role-id="${role.id}" data-role-name="${role.name}" title="删除角色"><i class="bi bi-trash"></i></button>
                `;
            });
        } else {
            // 这个else分支现在由 fetchRoles 处理，如果 pageData.content 为空或 pageData 为空
        }
    }

    function renderFunctionalityCheckboxes(container, functionalitiesToRender, selectedFunctionalityNames = []) {
        container.innerHTML = '';
        if (functionalitiesToRender && functionalitiesToRender.length > 0) {
            functionalitiesToRender.forEach(func => {
                const isChecked = selectedFunctionalityNames.includes(func.name);
                const div = document.createElement('div');
                div.classList.add('form-check'); // 修改了样式，不再是 form-check-inline，以适应垂直排列和更长的文本
                div.innerHTML = `
                    <input class="form-check-input" type="checkbox" value="${func.name}" id="func_checkbox_${func.id}_${container.id}" name="functionalityNames" ${isChecked ? 'checked' : ''}>
                    <label class="form-check-label" for="func_checkbox_${func.id}_${container.id}">
                        ${func.name} (${func.num || 'N/A'}) </label>
                `;
                container.appendChild(div);
            });
        } else {
            container.innerHTML = '<p class="text-muted small">无可用功能或功能列表加载失败。</p>';
        }
    }

    // --- Event Handlers ---
    function handleOpenCreateRoleModal() {
        resetForm(roleForm);
        roleIdInput.value = '';
        roleFormModalLabel.textContent = '添加新角色';
        renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities);
        roleFormModal.show();
    }

    async function handleRoleFormSubmit(event) {
        event.preventDefault();
        if (!validateForm(roleForm)) { // 假设 validateForm 在 utils.js 中
            showAlert('请检查表单中的必填项。', 'warning', globalAlertContainerId);
            return;
        }

        const saveButton = roleForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const currentRoleId = roleIdInput.value;
        const name = roleNameInput.value;
        const description = roleDescriptionInput.value;
        const selectedFunctionalityNames = Array.from(functionalitiesCheckboxesContainer.querySelectorAll('input[name="functionalityNames"]:checked'))
            .map(cb => cb.value);

        const roleData = { name, description, functionalityNames };

        let url = API_ROLES_URL;
        let method = 'POST';

        if (currentRoleId) { // Edit mode
            url = `${API_ROLES_URL}/${currentRoleId}`;
            method = 'PUT';
        }

        try {
            await authenticatedFetch(url, { method, body: roleData }, globalAlertContainerId);
            showAlert(currentRoleId ? '角色更新成功！' : '角色创建成功！', 'success', globalAlertContainerId);
            fetchRoles(currentRoleId ? currentPageRole : 0); // 创建后跳到第一页，编辑后留在当前页
            roleFormModal.hide();
        } catch (error) {
            // authenticatedFetch 应该已经处理了错误消息显示
        } finally {
            toggleLoading(false, saveButton);
        }
    }

    async function handleTableActions(event) {
        const targetButton = event.target.closest('button');
        if (!targetButton) return;

        const roleId = targetButton.dataset.roleId;

        if (targetButton.classList.contains('edit-role-btn')) {
            try {
                const role = await authenticatedFetch(`${API_ROLES_URL}/${roleId}`, {}, globalAlertContainerId);
                if (role) {
                    resetForm(roleForm);
                    roleFormModalLabel.textContent = '编辑角色';
                    roleIdInput.value = role.id;
                    roleNameInput.value = role.name;
                    roleDescriptionInput.value = role.description || '';

                    const currentFunctionalityNames = role.functionalities && Array.isArray(role.functionalities)
                        ? role.functionalities.map(f => f.name)
                        : [];
                    renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities, currentFunctionalityNames);
                    roleFormModal.show();
                } else {
                    showAlert('无法加载角色信息进行编辑。', 'warning', globalAlertContainerId);
                }
            } catch (error) {
                // authenticatedFetch 已处理
            }
        } else if (targetButton.classList.contains('delete-role-btn')) {
            const roleName = targetButton.dataset.roleName;
            if (confirm(`确定要删除角色 "${roleName}" (ID: ${roleId})吗？此操作无法撤销。`)) {
                toggleLoading(true, targetButton);
                try {
                    await authenticatedFetch(`${API_ROLES_URL}/${roleId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`角色 "${roleName}" 删除成功！`, 'success', globalAlertContainerId);
                    // 检查删除后当前页是否还有数据
                    const currentRows = rolesTableBody.rows.length;
                    if (currentRows === 1 && currentPageRole > 0) { // 如果删除的是当前页最后一条且不是第一页
                        fetchRoles(currentPageRole - 1);
                    } else {
                        fetchRoles(currentPageRole); // 否则刷新当前页
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