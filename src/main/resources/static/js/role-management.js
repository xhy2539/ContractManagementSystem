document.addEventListener('DOMContentLoaded', function () {
    const rolesTableBody = document.querySelector('#rolesTable tbody');
    const roleFormModal = new bootstrap.Modal(document.getElementById('roleFormModal'));
    const roleForm = document.getElementById('roleForm');
    const addRoleBtn = document.getElementById('addRoleBtn');
    const functionalitiesCheckboxesContainer = document.getElementById('functionalitiesCheckboxes');

    let allFunctionalities = [];

    // --- API Endpoints ---
    const API_ROLES_URL = '/api/system/roles';
    const API_FUNCTIONALITIES_URL = '/api/system/functionalities';

    // --- Utility Functions (similar to user-management.js or a shared utility file) ---
    async function fetchData(url, options = {}) {
        try {
            const response = await fetch(url, options);
            if (!response.ok) {
                const errorData = await response.json().catch(() => ({ message: response.statusText }));
                throw new Error(`HTTP error! status: ${response.status}, message: ${errorData.message || 'Unknown error'}`);
            }
            if (response.status === 204) return null;
            return response.json();
        } catch (error) {
            console.error('Fetch error:', error);
            alert(`操作失败: ${error.message}`);
            throw error;
        }
    }

    async function loadAllFunctionalities() {
        try {
            allFunctionalities = await fetchData(API_FUNCTIONALITIES_URL);
        } catch (error) {
            // allFunctionalities remains empty
        }
    }

    function renderFunctionalityCheckboxes(container, functionalities, selectedFunctionalityNames = []) {
        container.innerHTML = '';
        if (functionalities && functionalities.length > 0) {
            functionalities.forEach(func => {
                const isChecked = selectedFunctionalityNames.includes(func.name);
                const div = document.createElement('div');
                div.classList.add('form-check', 'form-check-inline'); // Using form-check-inline for better layout
                div.innerHTML = `
                    <input class="form-check-input" type="checkbox" value="${func.name}" id="func_${func.id}" name="functionalityNames" ${isChecked ? 'checked' : ''}>
                    <label class="form-check-label" for="func_${func.id}">
                        ${func.name} (${func.num})
                    </label>
                `;
                container.appendChild(div);
            });
        } else {
            container.innerHTML = '<p class="text-muted">无可用功能。</p>';
        }
    }

    // --- Role Management Functions ---
    async function fetchRoles() {
        try {
            const roles = await fetchData(API_ROLES_URL);
            renderRolesTable(roles);
        } catch (error) {
            rolesTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载角色列表失败: ${error.message}</td></tr>`;
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
                row.insertCell().textContent = role.functionalities.map(f => f.name).join(', ') || '无';

                const actionsCell = row.insertCell();
                actionsCell.classList.add('action-buttons');
                actionsCell.innerHTML = `
                    <button class="btn btn-sm btn-info edit-role-btn" data-role-id="${role.id}"><i class="bi bi-pencil-square"></i> 编辑</button>
                    <button class="btn btn-sm btn-danger delete-role-btn" data-role-id="${role.id}" data-role-name="${role.name}"><i class="bi bi-trash"></i> 删除</button>
                `;
            });
        } else {
            rolesTableBody.innerHTML = '<tr><td colspan="5" class="text-center">无角色数据。</td></tr>';
        }
    }

    addRoleBtn.addEventListener('click', () => {
        roleForm.reset();
        document.getElementById('roleId').value = '';
        document.getElementById('roleFormModalLabel').textContent = '添加新角色';
        renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities);
        roleFormModal.show();
    });

    roleForm.addEventListener('submit', async function (event) {
        event.preventDefault();
        const roleId = document.getElementById('roleId').value;
        const name = document.getElementById('roleName').value;
        const description = document.getElementById('roleDescription').value;
        const selectedFunctionalityNames = Array.from(functionalitiesCheckboxesContainer.querySelectorAll('input[name="functionalityNames"]:checked'))
            .map(cb => cb.value);

        const roleData = {
            name,
            description,
            functionalityNames: selectedFunctionalityNames
        };

        let url = API_ROLES_URL;
        let method = 'POST';

        if (roleId) { // Edit mode
            url = `${API_ROLES_URL}/${roleId}`;
            method = 'PUT';
        }

        try {
            await fetchData(url, {
                method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(roleData)
            });
            alert(roleId ? '角色更新成功！' : '角色创建成功！');
            fetchRoles();
            roleFormModal.hide();
        } catch (error) {
            // Error alerted by fetchData
        }
    });

    rolesTableBody.addEventListener('click', async function (event) {
        const target = event.target.closest('button');
        if (!target) return;

        const roleId = target.dataset.roleId;

        if (target.classList.contains('edit-role-btn')) {
            try {
                const role = await fetchData(`${API_ROLES_URL}/${roleId}`);
                if (role) {
                    document.getElementById('roleFormModalLabel').textContent = '编辑角色';
                    document.getElementById('roleId').value = role.id;
                    document.getElementById('roleName').value = role.name;
                    document.getElementById('roleDescription').value = role.description || '';
                    const currentFunctionalityNames = role.functionalities.map(f => f.name);
                    renderFunctionalityCheckboxes(functionalitiesCheckboxesContainer, allFunctionalities, currentFunctionalityNames);
                    roleFormModal.show();
                } else {
                    alert('未找到角色信息！');
                }
            } catch (error) {
                // Error alerted
            }
        } else if (target.classList.contains('delete-role-btn')) {
            const roleName = target.dataset.roleName;
            if (confirm(`确定要删除角色 "${roleName}" (ID: ${roleId})吗？`)) {
                try {
                    await fetchData(`${API_ROLES_URL}/${roleId}`, { method: 'DELETE' });
                    alert(`角色 "${roleName}" 删除成功！`);
                    fetchRoles();
                } catch (error) {
                    // Error alerted
                }
            }
        }
    });

    // Initial load
    loadAllFunctionalities().then(() => {
        fetchRoles();
    });
});