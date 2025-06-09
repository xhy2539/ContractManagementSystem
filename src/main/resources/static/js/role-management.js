// static/js/role-management.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const rolesTableBody = document.querySelector('#rolesTable tbody');
    const roleFormModalEl = document.getElementById('roleFormModal');
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
    const DEFAULT_PAGE_SIZE_ROLE = 10;

    // --- API Endpoints ---
    const API_ROLES_URL = '/api/system/roles';
    const API_FUNCTIONALITIES_URL = '/api/system/functionalities';

    // --- Initialization ---
    async function initializePage() {
        await loadAllFunctionalities(); // 首先加载所有可用的功能
        await fetchRoles(); // 然后加载角色列表

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
            searchRoleBtn.addEventListener('click', () => fetchRoles(0));
        }
        if (roleFilterForm) {
            roleFilterForm.addEventListener('reset', () => {
                setTimeout(() => fetchRoles(0), 0);
            });
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
            const funcData = await authenticatedFetch(API_FUNCTIONALITIES_URL + '?size=1000&sort=name,asc', {}, globalAlertContainerId);
            allFunctionalities = funcData && funcData.content ? funcData.content : (Array.isArray(funcData) ? funcData : []);
            if (!allFunctionalities) {
                allFunctionalities = [];
            }
            if (allFunctionalities.length === 0) {
                console.warn("功能列表为空或加载失败，请检查后端API: " + API_FUNCTIONALITIES_URL + " 是否正确返回数据，以及管理员是否拥有 'FUNC_VIEW_LIST' 权限。");
            }
        } catch (error) {
            console.error("加载功能列表失败 (loadAllFunctionalities):", error);
            allFunctionalities = [];
            showAlert("加载功能列表时出错，详情请查看控制台或联系管理员。", "danger", globalAlertContainerId);
        }
    }

    async function fetchRoles(page = 0, size = DEFAULT_PAGE_SIZE_ROLE) {
        currentPageRole = page;
        const nameSearchVal = roleNameSearchInput ? roleNameSearchInput.value.trim() : '';
        const descriptionSearchVal = roleDescriptionSearchInput ? roleDescriptionSearchInput.value.trim() : '';

        let queryParams = `page=${page}&size=${size}&sort=name,asc`;

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
                renderPaginationControls(null, paginationControlsContainerId, fetchRoles, DEFAULT_PAGE_SIZE_ROLE);
            }
        } catch (error) {
            rolesTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载角色列表失败。</td></tr>`;
            renderPaginationControls(null, paginationControlsContainerId, fetchRoles, DEFAULT_PAGE_SIZE_ROLE);
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

                const functionalityDisplay = role.functionalities && Array.isArray(role.functionalities)
                    ? role.functionalities.map(f => `${f.name} (${f.num || 'N/A'})`).join(', ')
                    : '无';
                row.insertCell().textContent = functionalityDisplay || '无';

                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons', 'text-nowrap');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-role-btn" data-role-id="${role.id}" title="编辑角色"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-danger delete-role-btn" data-role-id="${role.id}" data-role-name="${role.name}" title="删除角色"><i class="bi bi-trash"></i></button>
                `;
            });
        } else {
            rolesTableBody.innerHTML = `<tr><td colspan="5" class="text-center">无角色数据。</td></tr>`;
        }
    }

    function renderFunctionalityCheckboxes(container, functionalitiesToRender, selectedFunctionalityNums = []) {
        container.innerHTML = '';
        if (functionalitiesToRender && functionalitiesToRender.length > 0) {
            functionalitiesToRender.forEach(func => {
                // **核心修改：isChecked 现在比较 func.num**
                const isChecked = selectedFunctionalityNums.includes(func.num); // 使用功能编号进行比较
                const div = document.createElement('div');
                div.classList.add('form-check');
                div.innerHTML = `
                    <input class="form-check-input" type="checkbox" value="${func.num}" id="func_checkbox_${func.id}_${container.id}" name="functionalityNums" ${isChecked ? 'checked' : ''}>
                    <label class="form-check-label" for="func_checkbox_${func.id}_${container.id}">
                        ${func.name} (${func.num || 'N/A'})
                    </label>
                `;
                // **核心修改：input 的 value 是 func.num, name 是 functionalityNums**
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
        renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities, []); // 创建时默认不选择
        if (window.showModal) {
            window.showModal(roleFormModalEl);
        }
    }

    async function handleRoleFormSubmit(event) {
        event.preventDefault();
        if (!validateForm(roleForm)) {
            showAlert('请检查表单中的必填项。', 'warning', globalAlertContainerId);
            return;
        }

        const saveButton = roleForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const currentRoleId = roleIdInput.value;
        const name = roleNameInput.value;
        const description = roleDescriptionInput.value;

        // **核心修改：收集功能编号 (num)**
        const selectedFunctionalityNums = Array.from(functionalitiesCheckboxesContainer.querySelectorAll('input[name="functionalityNums"]:checked'))
            .map(cb => cb.value);

        // **核心修改：请求体中发送 functionalityNums**
        const roleData = { name, description, functionalityNums: selectedFunctionalityNums };

        let url = API_ROLES_URL;
        let method = 'POST';

        if (currentRoleId) { // Edit mode
            url = `${API_ROLES_URL}/${currentRoleId}`;
            method = 'PUT';
        }

        try {
            await authenticatedFetch(url, { method, body: roleData }, globalAlertContainerId);
            showAlert(currentRoleId ? '角色更新成功！' : '角色创建成功！', 'success', globalAlertContainerId);
            fetchRoles(currentRoleId ? currentPageRole : 0);
            if (window.hideModal) {
                window.hideModal(roleFormModalEl);
            }
        } catch (error) {
            console.error("保存角色失败:", error);
            showAlert(currentRoleId ? '角色更新失败，详情请查看控制台。' : '角色创建失败，详情请查看控制台。', 'danger', globalAlertContainerId);
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

                    // **核心修改：获取当前角色的功能编号列表**
                    const currentFunctionalityNums = role.functionalities && Array.isArray(role.functionalities)
                        ? role.functionalities.map(f => f.num) // 假设后端返回的 role 对象中 functionalities 包含 num
                        : [];
                    renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities, currentFunctionalityNums);
                    if (window.showModal) {
                        window.showModal(roleFormModalEl);
                    }
                } else {
                    showAlert('无法加载角色信息进行编辑。', 'warning', globalAlertContainerId);
                }
            } catch (error) {
                console.error("编辑角色 - 加载角色信息失败:", error);
                showAlert('加载角色信息进行编辑失败，详情请查看控制台。', 'danger', globalAlertContainerId);
            }
        } else if (targetButton.classList.contains('delete-role-btn')) {
            const roleName = targetButton.dataset.roleName;
            if (confirm(`确定要删除角色 "${roleName}" (ID: ${roleId})吗？此操作无法撤销。`)) {
                toggleLoading(true, targetButton);
                try {
                    await authenticatedFetch(`${API_ROLES_URL}/${roleId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`角色 "${roleName}" 删除成功！`, 'success', globalAlertContainerId);
                    const currentRows = rolesTableBody.rows.length;
                    if (currentRows === 1 && currentPageRole > 0) {
                        fetchRoles(currentPageRole - 1);
                    } else {
                        fetchRoles(currentPageRole);
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