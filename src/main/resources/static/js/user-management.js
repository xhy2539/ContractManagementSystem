// static/js/user-management.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const usersTableBody = document.querySelector('#usersTable tbody');
    const userFormModalEl = document.getElementById('userFormModal');
    const userFormModal = new bootstrap.Modal(userFormModalEl);
    const assignRolesModalEl = document.getElementById('assignRolesModal');
    const assignRolesModal = new bootstrap.Modal(assignRolesModalEl);
    const userForm = document.getElementById('userForm');
    const assignRolesForm = document.getElementById('assignRolesForm');
    const addUserBtn = document.getElementById('addUserBtn');

    const rolesCheckboxesCreateContainer = document.getElementById('rolesCheckboxesCreate');
    const rolesCheckboxesAssignContainer = document.getElementById('rolesCheckboxesAssign');

    const passwordGroup = document.getElementById('passwordGroup');
    const passwordInput = document.getElementById('password');
    const passwordRequiredSpan = document.getElementById('passwordRequired');
    const passwordHelpText = document.getElementById('passwordHelp');

    const confirmPasswordGroup = document.getElementById('confirmPasswordGroup');
    const confirmPasswordInput = document.getElementById('confirmPassword');
    const confirmPasswordRequiredSpan = document.getElementById('confirmPasswordRequired');

    const usernameInput = document.getElementById('username');
    const userIdInput = document.getElementById('userId');
    const emailInput = document.getElementById('email');
    const realNameInput = document.getElementById('realName'); // 新增：获取realName输入框
    const enabledCheckbox = document.getElementById('enabled');
    const userFormModalLabel = document.getElementById('userFormModalLabel');
    const rolesGroupForCreate = document.getElementById('rolesGroupForCreate');

    const assignRolesUserIdInput = document.getElementById('assignRolesUserId');
    const assignRolesUsernameSpan = document.getElementById('assignRolesUsername');

    const paginationControlsContainerId = 'paginationControlsUser';
    const globalAlertContainerId = 'globalAlertContainer';
    const usersTableSpinnerId = 'usersTableSpinner';

    const usernameSearchInput = document.getElementById('usernameSearch');
    const emailSearchInput = document.getElementById('emailSearch');
    const searchUserBtn = document.getElementById('searchUserBtn');
    const userFilterForm = document.getElementById('userFilterForm');


    // --- State & Configuration ---
    let allRoles = [];
    let currentPageUser = 0;
    const DEFAULT_PAGE_SIZE_USER = 10;

    // --- API Endpoints ---
    const API_USERS_URL = '/api/system/users';
    const API_ROLES_URL = '/api/system/roles';

    // --- Initialization ---
    async function initializePage() {
        await loadAllRoles();
        await fetchUsers();

        if (addUserBtn) {
            addUserBtn.addEventListener('click', handleOpenCreateUserModal);
        }
        if (userForm) {
            userForm.addEventListener('submit', handleUserFormSubmit);
        }
        if (assignRolesForm) {
            assignRolesForm.addEventListener('submit', handleAssignRolesFormSubmit);
        }
        if (usersTableBody) {
            usersTableBody.addEventListener('click', handleTableActions);
        }
        if (searchUserBtn) {
            searchUserBtn.addEventListener('click', () => fetchUsers(0));
        }
        if (userFilterForm) {
            userFilterForm.addEventListener('reset', () => {
                setTimeout(() => fetchUsers(0), 0);
            });
            [usernameSearchInput, emailSearchInput].forEach(input => {
                if (input) input.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        fetchUsers(0);
                    }
                });
            });
        }
    }

    // --- Data Fetching and Rendering ---
    async function loadAllRoles() {
        try {
            const rolesData = await authenticatedFetch(API_ROLES_URL, {}, globalAlertContainerId);
            allRoles = Array.isArray(rolesData) ? rolesData : (rolesData && rolesData.content ? rolesData.content : []);
            if (!allRoles) allRoles = [];
        } catch (error) {
            console.error("Failed to load roles list.");
            allRoles = [];
        }
    }

    async function fetchUsers(page = 0, size = DEFAULT_PAGE_SIZE_USER) {
        currentPageUser = page;
        const usernameSearchVal = usernameSearchInput ? usernameSearchInput.value.trim() : '';
        const emailSearchVal = emailSearchInput ? emailSearchInput.value.trim() : '';

        let queryParams = `page=${page}&size=${size}&sort=username,asc`;

        if (usernameSearchVal) {
            queryParams += `&usernameSearch=${encodeURIComponent(usernameSearchVal)}`;
        }
        if (emailSearchVal) {
            queryParams += `&emailSearch=${encodeURIComponent(emailSearchVal)}`;
        }

        toggleLoading(true, null, usersTableSpinnerId);
        try {
            const pageData = await authenticatedFetch(`${API_USERS_URL}?${queryParams}`, {}, globalAlertContainerId);
            if (pageData && pageData.content) {
                renderUsersTable(pageData.content);
                renderPaginationControls(pageData, paginationControlsContainerId, fetchUsers, DEFAULT_PAGE_SIZE_USER);
            } else {
                usersTableBody.innerHTML = `<tr><td colspan="6" class="text-center">无用户数据。</td></tr>`;
                renderPaginationControls(null, paginationControlsContainerId, fetchUsers, DEFAULT_PAGE_SIZE_USER);
            }
        } catch (error) {
            usersTableBody.innerHTML = `<tr><td colspan="6" class="text-center text-danger">加载用户列表失败。</td></tr>`;
            renderPaginationControls(null, paginationControlsContainerId, fetchUsers, DEFAULT_PAGE_SIZE_USER);
        } finally {
            toggleLoading(false, null, usersTableSpinnerId);
        }
    }

    function renderUsersTable(users) {
        usersTableBody.innerHTML = '';
        if (users && users.length > 0) {
            users.forEach(user => {
                const row = usersTableBody.insertRow();
                row.insertCell().textContent = user.id;
                row.insertCell().textContent = user.username;
                row.insertCell().textContent = user.email || 'N/A';
                // 如果要在表格中显示真实姓名:
                // row.insertCell().textContent = user.realName || 'N/A';
                row.insertCell().innerHTML = `<span class="badge bg-${user.enabled ? 'success' : 'secondary'}">${user.enabled ? '是' : '否'}</span>`;
                row.insertCell().textContent = user.roles && user.roles.length > 0 ? user.roles.map(role => role.name).join(', ') : '无';

                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons', 'text-nowrap');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-user-btn" data-user-id="${user.id}" data-username="${user.username}" title="编辑用户"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-warning assign-roles-btn" data-user-id="${user.id}" data-username="${user.username}" title="分配角色"><i class="bi bi-person-check-fill"></i></button>
                    <button class="btn btn-sm btn-danger delete-user-btn" data-user-id="${user.id}" data-username="${user.username}" title="删除用户"><i class="bi bi-trash"></i></button>
                `;
            });
        }
    }

    function renderRoleCheckboxes(container, rolesToRender, selectedRoleNames = []) {
        container.innerHTML = '';
        if (rolesToRender && rolesToRender.length > 0) {
            rolesToRender.forEach(role => {
                const isChecked = selectedRoleNames.includes(role.name);
                const div = document.createElement('div');
                div.classList.add('form-check', 'form-check-inline');
                div.innerHTML = `
                    <input class="form-check-input" type="checkbox" value="${role.name}" id="role_checkbox_${role.id}_${container.id}" name="roleNames" ${isChecked ? 'checked' : ''}>
                    <label class="form-check-label" for="role_checkbox_${role.id}_${container.id}">
                        ${role.name}
                    </label>
                `;
                container.appendChild(div);
            });
        } else {
            container.innerHTML = '<p class="text-muted">无可用角色。</p>';
        }
    }

    function handleOpenCreateUserModal() {
        resetForm(userForm);
        userIdInput.value = '';
        userFormModalLabel.textContent = '添加新用户';

        usernameInput.readOnly = false;
        if (realNameInput) realNameInput.value = ''; // 清空真实姓名字段

        passwordInput.required = true;
        passwordInput.placeholder = '至少6位字符';
        if (passwordHelpText) passwordHelpText.textContent = '密码设置请勿过于简单,至少6位;建议使用数字、字母混合排列,区分大小写。';
        if (passwordGroup) passwordGroup.style.display = 'block';
        if (passwordRequiredSpan) passwordRequiredSpan.style.display = 'inline';

        if (confirmPasswordGroup) confirmPasswordGroup.style.display = 'block';
        if (confirmPasswordInput) {
            confirmPasswordInput.value = '';
            confirmPasswordInput.required = true;
        }
        if (confirmPasswordRequiredSpan) confirmPasswordRequiredSpan.style.display = 'inline';

        if (rolesGroupForCreate) rolesGroupForCreate.style.display = 'block';
        renderRoleCheckboxes(rolesCheckboxesCreateContainer, allRoles);

        userFormModal.show();
    }

    async function handleUserFormSubmit(event) {
        event.preventDefault();

        const currentUserId = userIdInput.value;
        const passwordVal = passwordInput.value;
        const confirmPasswordVal = confirmPasswordInput ? confirmPasswordInput.value : null;

        if (!currentUserId) {
            passwordInput.required = true;
            if (confirmPasswordInput) confirmPasswordInput.required = true;
            if (passwordVal !== confirmPasswordVal) {
                showAlert('两次输入的密码不一致！', 'warning', globalAlertContainerId);
                passwordInput.classList.add('is-invalid');
                if(confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
                return;
            } else {
                passwordInput.classList.remove('is-invalid');
                if(confirmPasswordInput) confirmPasswordInput.classList.remove('is-invalid');
            }
        } else {
            passwordInput.required = false;
            if (confirmPasswordInput) confirmPasswordInput.required = false;
            if (passwordVal && passwordVal.trim() !== '' && confirmPasswordVal && confirmPasswordVal.trim() !== '') {
                if (passwordVal !== confirmPasswordVal) {
                    showAlert('两次输入的密码不一致！', 'warning', globalAlertContainerId);
                    passwordInput.classList.add('is-invalid');
                    if(confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
                    return;
                } else {
                    passwordInput.classList.remove('is-invalid');
                    if(confirmPasswordInput) confirmPasswordInput.classList.remove('is-invalid');
                }
            }
        }

        if (!validateForm(userForm)) {
            showAlert('请检查表单中的必填项。', 'warning', globalAlertContainerId);
            return;
        }

        const saveButton = userForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const username = usernameInput.value;
        const email = emailInput.value;
        const realName = realNameInput ? realNameInput.value : ''; // 获取真实姓名
        const enabled = enabledCheckbox.checked;

        let requestBody;
        let url = API_USERS_URL;
        let method = 'POST';

        if (currentUserId) {
            method = 'PUT';
            url = `${API_USERS_URL}/${currentUserId}`;
            requestBody = { email, realName, enabled }; // 在更新请求中包含 realName
        } else {
            const selectedRoleNames = Array.from(rolesCheckboxesCreateContainer.querySelectorAll('input[name="roleNames"]:checked'))
                .map(cb => cb.value);
            if (!passwordVal) {
                showAlert('创建用户时密码不能为空！', 'warning', globalAlertContainerId);
                toggleLoading(false, saveButton);
                return;
            }
            requestBody = {
                username,
                password: passwordVal,
                confirmPassword: confirmPasswordVal,
                email,
                realName, // 在创建请求中包含 realName
                enabled,
                roleNames: selectedRoleNames
            };
        }

        try {
            await authenticatedFetch(url, { method, body: requestBody }, globalAlertContainerId);
            showAlert(currentUserId ? '用户信息更新成功！' : '用户创建成功！', 'success', globalAlertContainerId);
            fetchUsers(currentUserId ? currentPageUser : 0);
            userFormModal.hide();
        } catch (error) {
            if (error.message && error.message.includes("两次输入的密码不一致")) {
                passwordInput.classList.add('is-invalid');
                if(confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
            }
        } finally {
            toggleLoading(false, saveButton);
        }
    }

    async function handleTableActions(event) {
        const targetButton = event.target.closest('button');
        if (!targetButton) return;

        const userId = targetButton.dataset.userId;
        const usernameFromRow = targetButton.dataset.username;

        if (targetButton.classList.contains('edit-user-btn')) {
            try {
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameFromRow}`, {}, globalAlertContainerId);
                if (user) {
                    resetForm(userForm);
                    userFormModalLabel.textContent = '编辑用户';
                    userIdInput.value = user.id;
                    usernameInput.value = user.username;
                    usernameInput.readOnly = true;
                    emailInput.value = user.email || '';
                    if (realNameInput) realNameInput.value = user.realName || ''; // 填充真实姓名字段
                    enabledCheckbox.checked = user.enabled;

                    passwordInput.required = false;
                    passwordInput.value = '';
                    passwordInput.placeholder = '留空则不修改密码';
                    if (passwordHelpText) passwordHelpText.textContent = '仅在需要修改密码时填写。留空则不修改原密码。';
                    if (passwordRequiredSpan) passwordRequiredSpan.style.display = 'none';
                    if (confirmPasswordGroup) confirmPasswordGroup.style.display = 'none';
                    if (confirmPasswordInput) {
                        confirmPasswordInput.value = '';
                        confirmPasswordInput.required = false;
                    }
                    if (confirmPasswordRequiredSpan) confirmPasswordRequiredSpan.style.display = 'none';
                    if (rolesGroupForCreate) rolesGroupForCreate.style.display = 'none';

                    userFormModal.show();
                } else {
                    showAlert('无法加载用户信息进行编辑。', 'warning', globalAlertContainerId);
                }
            } catch (error) {
                // Error handled
            }
        } else if (targetButton.classList.contains('assign-roles-btn')) {
            assignRolesUserIdInput.value = userId;
            assignRolesUsernameSpan.textContent = usernameFromRow;
            try {
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameFromRow}`, {}, globalAlertContainerId);
                const currentUserRoleNames = user && user.roles ? user.roles.map(r => r.name) : [];
                renderRoleCheckboxes(rolesCheckboxesAssignContainer, allRoles, currentUserRoleNames);
                assignRolesModal.show();
            } catch (error) {
                // Error handled
            }
        } else if (targetButton.classList.contains('delete-user-btn')) {
            if (confirm(`确定要删除用户 "${usernameFromRow}" (ID: ${userId}) 吗？此操作无法撤销。`)) {
                toggleLoading(true, targetButton);
                try {
                    await authenticatedFetch(`${API_USERS_URL}/${userId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`用户 "${usernameFromRow}" 删除成功！`, 'success', globalAlertContainerId);
                    const currentRows = usersTableBody.rows.length;
                    if (currentRows === 1 && currentPageUser > 0) {
                        fetchUsers(currentPageUser - 1);
                    } else {
                        fetchUsers(currentPageUser);
                    }
                } catch (error) {
                    // Error handled
                } finally {
                    toggleLoading(false, targetButton);
                }
            }
        }
    }

    async function handleAssignRolesFormSubmit(event) {
        event.preventDefault();
        const assignButton = assignRolesForm.querySelector('button[type="submit"]');
        toggleLoading(true, assignButton);
        const userIdToAssign = assignRolesUserIdInput.value;
        const selectedRoleNames = Array.from(rolesCheckboxesAssignContainer.querySelectorAll('input[type="checkbox"]:checked'))
            .map(cb => cb.value);
        try {
            await authenticatedFetch(`${API_USERS_URL}/${userIdToAssign}/assign-roles`, {
                method: 'POST',
                body: { roleNames: selectedRoleNames }
            }, globalAlertContainerId);
            showAlert('用户角色分配成功！', 'success', globalAlertContainerId);
            fetchUsers(currentPageUser);
            assignRolesModal.hide();
        } catch (error) {
            // Error handled
        } finally {
            toggleLoading(false, assignButton);
        }
    }

    initializePage();
});