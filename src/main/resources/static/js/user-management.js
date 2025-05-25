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
    const usernameInput = document.getElementById('username');
    const userIdInput = document.getElementById('userId');
    const emailInput = document.getElementById('email');
    const enabledCheckbox = document.getElementById('enabled');
    const userFormModalLabel = document.getElementById('userFormModalLabel');
    const rolesGroupForCreate = document.getElementById('rolesGroupForCreate');

    const assignRolesUserIdInput = document.getElementById('assignRolesUserId');
    const assignRolesUsernameSpan = document.getElementById('assignRolesUsername');

    const paginationControlsContainerId = 'paginationControlsUser';
    const globalAlertContainerId = 'globalAlertContainer'; // Defined in utils.js or HTML
    const usersTableSpinnerId = 'usersTableSpinner'; // Add a spinner div in HTML if not present

    // Search elements (optional, add to HTML if needed)
    const usernameSearchInput = document.getElementById('usernameSearch');
    const emailSearchInput = document.getElementById('emailSearch');
    const searchUserBtn = document.getElementById('searchUserBtn');
    const userFilterForm = document.getElementById('userFilterForm');


    // --- State & Configuration ---
    let allRoles = [];
    let currentPageUser = 0;
    const DEFAULT_PAGE_SIZE_USER = 10; // Should match backend @PageableDefault or be configurable

    // --- API Endpoints ---
    const API_USERS_URL = '/api/system/users';
    const API_ROLES_URL = '/api/system/roles';

    // --- Initialization ---
    async function initializePage() {
        await loadAllRoles();
        await fetchUsers(); // Initial fetch on page load

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
        // Optional Search/Filter listeners
        if (searchUserBtn) {
            searchUserBtn.addEventListener('click', () => fetchUsers(0));
        }
        if (userFilterForm) {
            userFilterForm.addEventListener('reset', () => {
                // Timeout to allow form reset before fetching
                setTimeout(() => fetchUsers(0), 0);
            });
            // Optional: submit on enter in search fields
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
            allRoles = await authenticatedFetch(API_ROLES_URL, {}, globalAlertContainerId);
            if (!allRoles) allRoles = []; // Ensure it's an array
        } catch (error) {
            // Error already handled by authenticatedFetch, allRoles will be empty
            console.error("Failed to load roles list.");
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
                row.insertCell().innerHTML = `<span class="badge bg-${user.enabled ? 'success' : 'secondary'}">${user.enabled ? '是' : '否'}</span>`;
                row.insertCell().textContent = user.roles && user.roles.length > 0 ? user.roles.map(role => role.name).join(', ') : '无';

                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons', 'text-nowrap');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-user-btn" data-user-id="${user.id}" title="编辑用户"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-warning assign-roles-btn" data-user-id="${user.id}" data-username="${user.username}" title="分配角色"><i class="bi bi-person-check-fill"></i></button>
                    <button class="btn btn-sm btn-danger delete-user-btn" data-user-id="${user.id}" data-username="${user.username}" title="删除用户"><i class="bi bi-trash"></i></button>
                `;
            });
        } else {
            // Message handled by fetchUsers if pageData.content is empty
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

    // --- Event Handlers ---
    function handleOpenCreateUserModal() {
        resetForm(userForm);
        userIdInput.value = '';
        userFormModalLabel.textContent = '添加新用户';

        usernameInput.readOnly = false;
        passwordInput.required = true;
        passwordInput.placeholder = '至少6位字符';
        passwordGroup.style.display = 'block';
        if (passwordRequiredSpan) passwordRequiredSpan.style.display = 'inline';

        if (rolesGroupForCreate) rolesGroupForCreate.style.display = 'block';
        renderRoleCheckboxes(rolesCheckboxesCreateContainer, allRoles);

        userFormModal.show();
    }

    async function handleUserFormSubmit(event) {
        event.preventDefault();
        if (!validateForm(userForm)) { // 使用 utils.js 中的 validateForm
            showAlert('请检查表单中的必填项。', 'warning', globalAlertContainerId);
            return;
        }

        const saveButton = userForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const currentUserId = userIdInput.value;
        const username = usernameInput.value;
        const email = emailInput.value;
        const password = passwordInput.value;
        const enabled = enabledCheckbox.checked;

        let requestBody;
        let url = API_USERS_URL;
        let method = 'POST';

        if (currentUserId) { // Edit mode
            method = 'PUT';
            url = `${API_USERS_URL}/${currentUserId}`;
            requestBody = { email, enabled }; // UserUpdateRequest DTO
            // Password and roles are not updated here for simplicity; use separate actions.
            // If password change is desired here, add logic and DTO field.
            // if (password && password.trim() !== '') { requestBody.newPassword = password; }
        } else { // Create mode
            const selectedRoleNames = Array.from(rolesCheckboxesCreateContainer.querySelectorAll('input[name="roleNames"]:checked'))
                .map(cb => cb.value);
            if (!password) { // Should be caught by validateForm if required is set dynamically
                showAlert('创建用户时密码不能为空！', 'warning', globalAlertContainerId);
                toggleLoading(false, saveButton);
                return;
            }
            requestBody = { username, password, email, enabled, roleNames: selectedRoleNames };
        }

        try {
            await authenticatedFetch(url, { method, body: requestBody }, globalAlertContainerId);
            showAlert(currentUserId ? '用户信息更新成功！' : '用户创建成功！', 'success', globalAlertContainerId);
            fetchUsers(currentUserId ? currentPageUser : 0); // Refresh (current page for edit, first for create)
            userFormModal.hide();
        } catch (error) {
            // authenticatedFetch already showed an alert
        } finally {
            toggleLoading(false, saveButton);
        }
    }

    async function handleTableActions(event) {
        const targetButton = event.target.closest('button');
        if (!targetButton) return;

        const userId = targetButton.dataset.userId;

        if (targetButton.classList.contains('edit-user-btn')) {
            // Fetch the specific user for editing (assuming a GET /api/system/users/{id} or using username)
            // For now, we'll assume the user object needs to be fetched if not already available.
            // Let's simulate fetching user by username if ID is primary key for other ops.
            // This part might need adjustment based on your API capabilities for fetching a single user.
            try {
                const userToEdit = await authenticatedFetch(`${API_USERS_URL}/${userIdInput.value}`, {}, globalAlertContainerId); // Placeholder - adjust if API uses username
                // Or find from a locally cached list if full list is small and fetched
                // For a robust solution, a GET /api/system/users/{id} endpoint is better.
                // For now, let's assume we need to fetch the user. We'll use username if that's what `getUserByUsername` expects.
                // The current controller GET /{username}
                // We need the username from the table row.
                const usernameFromRow = targetButton.closest('tr').cells[1].textContent; // Assuming username is in the second cell
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameFromRow}`, {}, globalAlertContainerId);


                if (user) {
                    resetForm(userForm);
                    userFormModalLabel.textContent = '编辑用户';
                    userIdInput.value = user.id;
                    usernameInput.value = user.username;
                    usernameInput.readOnly = true;
                    emailInput.value = user.email || '';
                    enabledCheckbox.checked = user.enabled;

                    passwordInput.required = false;
                    passwordInput.value = '';
                    passwordInput.placeholder = '留空则不修改密码';
                    passwordGroup.style.display = 'block'; // Or 'none' if password change is a separate feature
                    if(passwordRequiredSpan) passwordRequiredSpan.style.display = 'none';

                    if (rolesGroupForCreate) rolesGroupForCreate.style.display = 'none'; // Roles via "Assign Roles"

                    userFormModal.show();
                } else {
                    showAlert('无法加载用户信息进行编辑。', 'warning', globalAlertContainerId);
                }
            } catch (error) {
                // Error handled by authenticatedFetch
            }

        } else if (targetButton.classList.contains('assign-roles-btn')) {
            const usernameToAssign = targetButton.dataset.username;
            assignRolesUserIdInput.value = userId;
            assignRolesUsernameSpan.textContent = usernameToAssign;

            try {
                // Fetch the user again to get current roles accurately
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameToAssign}`, {}, globalAlertContainerId);
                const currentUserRoleNames = user && user.roles ? user.roles.map(r => r.name) : [];

                renderRoleCheckboxes(rolesCheckboxesAssignContainer, allRoles, currentUserRoleNames);
                assignRolesModal.show();
            } catch (error) {
                // Error handled
            }

        } else if (targetButton.classList.contains('delete-user-btn')) {
            const usernameToDelete = targetButton.dataset.username;
            if (confirm(`确定要删除用户 "${usernameToDelete}" (ID: ${userId}) 吗？此操作无法撤销。`)) {
                toggleLoading(true, targetButton);
                try {
                    await authenticatedFetch(`${API_USERS_URL}/${userId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`用户 "${usernameToDelete}" 删除成功！`, 'success', globalAlertContainerId);
                    fetchUsers(currentPageUser); // Refresh current page
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
            fetchUsers(currentPageUser); // Refresh to show updated roles in table
            assignRolesModal.hide();
        } catch (error) {
            // Error handled
        } finally {
            toggleLoading(false, assignButton);
        }
    }

    // --- Initial Page Load ---
    initializePage();
});