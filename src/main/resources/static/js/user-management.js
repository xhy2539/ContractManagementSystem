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
    const realNameInput = document.getElementById('realName');
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
        console.log("[UserMgmt] Initializing page...");
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
                if (usernameSearchInput) usernameSearchInput.value = '';
                if (emailSearchInput) emailSearchInput.value = '';
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
        console.log("[UserMgmt] Page initialized.");
    }

    // --- Data Fetching and Rendering ---
    async function loadAllRoles() {
        console.log("[UserMgmt] Loading all roles...");
        try {
            const rolesData = await authenticatedFetch(API_ROLES_URL + '?size=1000', {}, globalAlertContainerId);
            allRoles = Array.isArray(rolesData) ? rolesData : (rolesData && rolesData.content ? rolesData.content : []);
            if (!allRoles) allRoles = [];
            console.log("[UserMgmt] Roles loaded:", allRoles);
        } catch (error) {
            console.error("[UserMgmt] Failed to load roles list:", error);
            allRoles = [];
        }
    }

    async function fetchUsers(page = 0, size = DEFAULT_PAGE_SIZE_USER) {
        console.log(`[UserMgmt] Fetching users, page: ${page}, size: ${size}`);
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
            console.log("[UserMgmt] Users data received:", pageData);
            if (pageData && pageData.content) {
                renderUsersTable(pageData.content);
                renderPaginationControls(pageData, paginationControlsContainerId, fetchUsers, DEFAULT_PAGE_SIZE_USER);
            } else {
                usersTableBody.innerHTML = `<tr><td colspan="6" class="text-center">无用户数据。</td></tr>`;
                renderPaginationControls(null, paginationControlsContainerId, fetchUsers, DEFAULT_PAGE_SIZE_USER);
            }
        } catch (error) {
            console.error("[UserMgmt] Error fetching users:", error);
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
                    <button class="btn btn-sm btn-info edit-user-btn" data-user-id="${user.id}" data-username="${user.username}" title="编辑用户"><i class="bi bi-pencil-square"></i></button>
                    <button class="btn btn-sm btn-warning assign-roles-btn" data-user-id="${user.id}" data-username="${user.username}" title="分配角色"><i class="bi bi-person-check-fill"></i></button>
                    <button class="btn btn-sm btn-danger delete-user-btn" data-user-id="${user.id}" data-username="${user.username}" title="删除用户"><i class="bi bi-trash"></i></button>
                `;
            });
        } else {
            usersTableBody.innerHTML = `<tr><td colspan="6" class="text-center">无用户数据。</td></tr>`;
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
            container.innerHTML = '<p class="text-muted small">无可用角色。</p>';
        }
    }

    function handleOpenCreateUserModal() {
        console.log("[UserMgmt] Opening create user modal.");
        resetForm(userForm); // resetForm should also remove was-validated class
        userIdInput.value = '';
        userFormModalLabel.textContent = '添加新用户';

        usernameInput.readOnly = false;
        if (realNameInput) realNameInput.value = '';

        // For 'create' mode, email, password, confirmPassword are required
        emailInput.required = true;
        passwordInput.required = true;
        if (confirmPasswordInput) confirmPasswordInput.required = true;


        passwordInput.value = '';
        passwordInput.placeholder = '至少6位字符';
        if (passwordHelpText) passwordHelpText.textContent = '密码设置请勿过于简单,至少6位。';
        if (passwordGroup) passwordGroup.style.display = 'block';
        if (passwordRequiredSpan) passwordRequiredSpan.style.display = 'inline';

        if (confirmPasswordGroup) confirmPasswordGroup.style.display = 'block';
        if (confirmPasswordInput) {
            confirmPasswordInput.value = '';
        }
        if (confirmPasswordRequiredSpan) confirmPasswordRequiredSpan.style.display = 'inline';

        if (rolesGroupForCreate) rolesGroupForCreate.style.display = 'block';
        renderRoleCheckboxes(rolesCheckboxesCreateContainer, allRoles);

        userFormModal.show();
    }

    async function handleUserFormSubmit(event) {
        event.preventDefault();
        console.log("[UserMgmt] User form submitted.");

        const currentUserId = userIdInput.value;
        const passwordVal = passwordInput.value;
        const confirmPasswordVal = confirmPasswordInput ? confirmPasswordInput.value : null;

        // Clear previous validation states
        passwordInput.classList.remove('is-invalid');
        if (confirmPasswordInput) confirmPasswordInput.classList.remove('is-invalid');
        emailInput.classList.remove('is-invalid');
        // Reset required attributes based on mode before calling validateForm
        if (!currentUserId) { // CREATE USER
            emailInput.required = true;
            passwordInput.required = true;
            if (confirmPasswordInput) confirmPasswordInput.required = true;
        } else { // UPDATE USER
            emailInput.required = true; // Or false if email can be optional for update
            passwordInput.required = false;
            if (confirmPasswordInput) confirmPasswordInput.required = false;
        }


        // General form validation (checks all 'required' fields and other HTML5 constraints)
        if (!validateForm(userForm)) { // validateForm internally adds 'was-validated'
            console.warn("[UserMgmt] Form validation failed (validateForm).");
            showAlert('请检查表单中的必填项或修正错误。', 'warning', globalAlertContainerId);
            return;
        }
        console.log("[UserMgmt] Basic form validation (validateForm) passed.");

        // Specific password checks, especially for create mode
        if (!currentUserId) { // CREATE USER
            if (!passwordVal.trim()) { // Should have been caught by validateForm if 'required'
                console.warn("[UserMgmt] Password is empty for new user.");
                showAlert('创建用户时密码不能为空。', 'warning', globalAlertContainerId);
                passwordInput.classList.add('is-invalid');
                return;
            }
            if (confirmPasswordInput && !confirmPasswordVal.trim()) { // Should have been caught by validateForm if 'required'
                console.warn("[UserMgmt] Confirm password is empty for new user.");
                showAlert('创建用户时确认密码不能为空。', 'warning', globalAlertContainerId);
                confirmPasswordInput.classList.add('is-invalid');
                return;
            }
            if (passwordVal !== confirmPasswordVal) {
                console.warn("[UserMgmt] Passwords do not match for new user.");
                showAlert('两次输入的密码不一致！', 'warning', globalAlertContainerId);
                passwordInput.classList.add('is-invalid');
                if (confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
                return;
            }
            console.log("[UserMgmt] Password validation passed for new user.");
        } else { // UPDATE USER
            // If password fields are filled for update, they must match
            if (passwordVal.trim() && confirmPasswordInput && confirmPasswordVal) {
                if (passwordVal !== confirmPasswordVal) {
                    console.warn("[UserMgmt] Passwords do not match for update.");
                    showAlert('两次输入的密码不一致！', 'warning', globalAlertContainerId);
                    passwordInput.classList.add('is-invalid');
                    confirmPasswordInput.classList.add('is-invalid');
                    return;
                }
            } else if (passwordVal.trim() && confirmPasswordInput && !confirmPasswordVal.trim()) {
                console.warn("[UserMgmt] Confirm password empty when password filled for update.");
                showAlert('请输入确认密码以修改密码。', 'warning', globalAlertContainerId);
                if (confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
                return;
            }
            console.log("[UserMgmt] Password validation passed for update (or no change requested).");
        }


        const saveButton = userForm.querySelector('button[type="submit"]');
        toggleLoading(true, saveButton);

        const username = usernameInput.value.trim();
        const email = emailInput.value.trim();
        const realName = realNameInput ? realNameInput.value.trim() : '';
        const enabled = enabledCheckbox.checked;

        let requestBody;
        let url = API_USERS_URL;
        let method = 'POST';

        if (currentUserId) {
            method = 'PUT';
            url = `${API_USERS_URL}/${currentUserId}`;
            requestBody = { email, realName, enabled };
            console.log("[UserMgmt] Preparing UPDATE request:", requestBody);
        } else {
            const selectedRoleNames = Array.from(rolesCheckboxesCreateContainer.querySelectorAll('input[name="roleNames"]:checked'))
                .map(cb => cb.value);
            requestBody = {
                username,
                password: passwordVal,
                confirmPassword: confirmPasswordVal,
                email,
                realName,
                enabled,
                roleNames: selectedRoleNames
            };
            console.log("[UserMgmt] Preparing CREATE request:", requestBody);
        }

        try {
            console.log("[UserMgmt] Attempting to call authenticatedFetch...");
            const result = await authenticatedFetch(url, { method, body: requestBody }, globalAlertContainerId);
            // For 201 Created, backend returns the created User object.
            // For 204 No Content (e.g. some PUT ops if not returning body), result might be null.
            // For other 2xx with body, result is the parsed JSON.
            console.log("[UserMgmt] authenticatedFetch call successful. Result:", result);

            showAlert(currentUserId ? '用户信息更新成功！' : '用户创建成功！', 'success', globalAlertContainerId);
            console.log("[UserMgmt] Success alert shown.");

            fetchUsers(currentUserId ? currentPageUser : 0);
            console.log("[UserMgmt] User list refresh requested.");

            userFormModal.hide();
            console.log("[UserMgmt] User form modal hidden.");

        } catch (error) {
            console.error("[UserMgmt] Error during form submission (authenticatedFetch failed):", error);
            // authenticatedFetch should have already shown an alert for HTTP errors.
            // This catch is for any other unexpected errors or if you want to add more specific client-side error handling.
            if (error.message && error.message.toLowerCase().includes("password") && !error.message.includes("HTTP")) {
                // Example: If a non-HTTP error related to password occurs, or if backend sends a specific password error message
                // that authenticatedFetch didn't fully categorize.
                passwordInput.classList.add('is-invalid');
                if(confirmPasswordInput) confirmPasswordInput.classList.add('is-invalid');
            }
        } finally {
            toggleLoading(false, saveButton);
            console.log("[UserMgmt] Save button loading state reset.");
        }
    }

    async function handleTableActions(event) {
        const targetButton = event.target.closest('button');
        if (!targetButton) return;

        const userId = targetButton.dataset.userId;
        const usernameFromRow = targetButton.dataset.username;
        console.log(`[UserMgmt] Table action: ${targetButton.className}, UserID: ${userId}, Username: ${usernameFromRow}`);

        if (targetButton.classList.contains('edit-user-btn')) {
            try {
                toggleLoading(true, targetButton);
                console.log("[UserMgmt] Fetching user for edit:", usernameFromRow);
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameFromRow}`, {}, globalAlertContainerId);
                if (user) {
                    console.log("[UserMgmt] User data for edit:", user);
                    resetForm(userForm);
                    userFormModalLabel.textContent = '编辑用户';
                    userIdInput.value = user.id;
                    usernameInput.value = user.username;
                    usernameInput.readOnly = true;

                    emailInput.value = user.email || '';
                    emailInput.required = true; // Keep email required for edit as per previous decision

                    if (realNameInput) realNameInput.value = user.realName || '';
                    enabledCheckbox.checked = user.enabled;

                    passwordInput.required = false;
                    passwordInput.value = '';
                    passwordInput.placeholder = '留空则不修改密码';
                    if (passwordHelpText) passwordHelpText.textContent = '仅在需要修改密码时填写。';
                    if (passwordRequiredSpan) passwordRequiredSpan.style.display = 'none';

                    if (confirmPasswordGroup) confirmPasswordGroup.style.display = 'block';
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
                console.error("[UserMgmt] Error fetching user for edit:", error);
            } finally {
                toggleLoading(false, targetButton);
            }
        } else if (targetButton.classList.contains('assign-roles-btn')) {
            assignRolesUserIdInput.value = userId;
            assignRolesUsernameSpan.textContent = usernameFromRow;
            try {
                toggleLoading(true, targetButton);
                console.log("[UserMgmt] Fetching user for role assignment:", usernameFromRow);
                const user = await authenticatedFetch(`${API_USERS_URL}/${usernameFromRow}`, {}, globalAlertContainerId);
                const currentUserRoleNames = user && user.roles ? user.roles.map(r => r.name) : [];
                console.log("[UserMgmt] Current roles for assignment:", currentUserRoleNames);
                renderRoleCheckboxes(rolesCheckboxesAssignContainer, allRoles, currentUserRoleNames);
                assignRolesModal.show();
            } catch (error) {
                console.error("[UserMgmt] Error preparing role assignment modal:", error);
            } finally {
                toggleLoading(false, targetButton);
            }
        } else if (targetButton.classList.contains('delete-user-btn')) {
            if (confirm(`确定要删除用户 "${usernameFromRow}" (ID: ${userId}) 吗？此操作无法撤销。`)) {
                toggleLoading(true, targetButton);
                try {
                    console.log("[UserMgmt] Deleting user:", userId);
                    await authenticatedFetch(`${API_USERS_URL}/${userId}`, { method: 'DELETE' }, globalAlertContainerId);
                    showAlert(`用户 "${usernameFromRow}" 删除成功！`, 'success', globalAlertContainerId);
                    const currentRows = usersTableBody.rows.length;
                    if (currentRows === 1 && currentPageUser > 0) {
                        fetchUsers(currentPageUser - 1);
                    } else {
                        fetchUsers(currentPageUser);
                    }
                } catch (error) {
                    console.error("[UserMgmt] Error deleting user:", error);
                } finally {
                    toggleLoading(false, targetButton);
                }
            }
        }
    }

    async function handleAssignRolesFormSubmit(event) {
        event.preventDefault();
        console.log("[UserMgmt] Assign roles form submitted.");
        const assignButton = assignRolesForm.querySelector('button[type="submit"]');
        toggleLoading(true, assignButton);

        const userIdToAssign = assignRolesUserIdInput.value;
        const selectedRoleNames = Array.from(rolesCheckboxesAssignContainer.querySelectorAll('input[type="checkbox"]:checked'))
            .map(cb => cb.value);
        console.log(`[UserMgmt] Assigning roles to UserID ${userIdToAssign}:`, selectedRoleNames);

        try {
            await authenticatedFetch(`${API_USERS_URL}/${userIdToAssign}/assign-roles`, {
                method: 'POST',
                body: { roleNames: selectedRoleNames }
            }, globalAlertContainerId);
            showAlert('用户角色分配成功！', 'success', globalAlertContainerId);
            fetchUsers(currentPageUser);
            assignRolesModal.hide();
        } catch (error) {
            console.error("[UserMgmt] Error assigning roles:", error);
        } finally {
            toggleLoading(false, assignButton);
        }
    }

    initializePage();
});