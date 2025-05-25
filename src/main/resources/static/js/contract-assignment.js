// static/js/contract-assignment.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const contractsTableBody = document.querySelector('#contractsTable tbody');
    const assignmentPanel = document.getElementById('assignmentPanel');
    const noContractSelectedPanel = document.getElementById('noContractSelectedPanel');
    const selectedContractNameSpan = document.getElementById('selectedContractName');
    const selectedContractIdInput = document.getElementById('selectedContractId');

    // User selection <select> elements
    const availableCountersignUsersSelect = document.getElementById('availableCountersignUsers');
    const assignedCountersignUsersSelect = document.getElementById('assignedCountersignUsers');
    const availableApprovalUsersSelect = document.getElementById('availableApprovalUsers');
    const assignedApprovalUsersSelect = document.getElementById('assignedApprovalUsers');
    const availableSigningUsersSelect = document.getElementById('availableSigningUsers');
    const assignedSigningUsersSelect = document.getElementById('assignedSigningUsers');

    // User selection buttons
    const addCountersignUserBtn = document.getElementById('addCountersignUserBtn');
    const removeCountersignUserBtn = document.getElementById('removeCountersignUserBtn');
    const addApprovalUserBtn = document.getElementById('addApprovalUserBtn');
    const removeApprovalUserBtn = document.getElementById('removeApprovalUserBtn');
    const addSigningUserBtn = document.getElementById('addSigningUserBtn');
    const removeSigningUserBtn = document.getElementById('removeSigningUserBtn');

    // Form action buttons
    const submitAssignmentBtn = document.getElementById('submitAssignmentBtn');
    const cancelAssignmentBtn = document.getElementById('cancelAssignmentBtn');

    // Pagination and alert containers
    const paginationControlsContainerId = 'paginationControlsContract'; // 来自 contract-assignment.html
    const globalAlertContainerId = 'globalAlertContainer'; // 假设在 layout.html 或 utils.js 中定义和处理
    const contractsTableSpinnerId = 'contractsTableSpinner'; // 确保HTML中有此ID的spinner元素

    // Search elements (确保HTML中有对应的ID, 如 contract-assignment.html 中建议添加的)
    const contractNameSearchInput = document.getElementById('contractNameSearch');
    const contractNumberSearchInput = document.getElementById('contractNumberSearch');
    const searchContractsBtn = document.getElementById('searchContractsBtn');
    const contractFilterForm = document.getElementById('contractFilterForm');


    // --- State & Configuration ---
    let allUsers = []; // 用于存储所有用户信息（分配人员时使用）
    let currentSelectedContractRow = null; // 当前在表格中选中的合同行
    let currentSelectedContractData = null; // 当前选中合同的完整数据
    let currentPageContract = 0; // 合同列表的当前页码
    const DEFAULT_PAGE_SIZE_CONTRACT = 10; // 与后端分页设置一致

    // --- API Endpoints ---
    const API_PENDING_CONTRACTS_URL = '/api/admin/contract-assignments/pending';
    const API_ASSIGN_PERSONNEL_URL = '/api/admin/contract-assignments'; // 基础URL, POST到 /{contractId}/assign
    const API_ALL_USERS_URL = '/api/system/users'; // 用于获取所有用户以供选择

    // --- Initialization ---
    async function initializePage() {
        // 首先加载所有用户数据，因为分配时需要这些数据填充可选列表
        await loadAllUsersForAssignment();
        // 然后加载待分配的合同列表
        await fetchPendingContracts();

        // 事件监听器
        if (contractsTableBody) {
            // 事件委托，监听表格体的点击事件，而不是给每一行单独添加监听器
            contractsTableBody.addEventListener('click', handleContractRowClickDelegation);
        }

        // 用户选择按钮事件
        if (addCountersignUserBtn) addCountersignUserBtn.addEventListener('click', () => moveOptions(availableCountersignUsersSelect, assignedCountersignUsersSelect));
        if (removeCountersignUserBtn) removeCountersignUserBtn.addEventListener('click', () => moveOptions(assignedCountersignUsersSelect, availableCountersignUsersSelect));
        if (addApprovalUserBtn) addApprovalUserBtn.addEventListener('click', () => moveOptions(availableApprovalUsersSelect, assignedApprovalUsersSelect));
        if (removeApprovalUserBtn) removeApprovalUserBtn.addEventListener('click', () => moveOptions(assignedApprovalUsersSelect, availableApprovalUsersSelect));
        if (addSigningUserBtn) addSigningUserBtn.addEventListener('click', () => moveOptions(availableSigningUsersSelect, assignedSigningUsersSelect));
        if (removeSigningUserBtn) removeSigningUserBtn.addEventListener('click', () => moveOptions(assignedSigningUsersSelect, availableSigningUsersSelect));

        if (submitAssignmentBtn) {
            submitAssignmentBtn.addEventListener('click', handleSubmitAssignment);
        }
        if (cancelAssignmentBtn) {
            cancelAssignmentBtn.addEventListener('click', handleCancelAssignment);
        }

        // 搜索/筛选事件监听器 (如果HTML中添加了搜索表单)
        if (searchContractsBtn) {
            searchContractsBtn.addEventListener('click', () => fetchPendingContracts(0));
        }
        if (contractFilterForm) {
            contractFilterForm.addEventListener('reset', () => {
                // 清空搜索框的值
                if(contractNameSearchInput) contractNameSearchInput.value = '';
                if(contractNumberSearchInput) contractNumberSearchInput.value = '';
                setTimeout(() => fetchPendingContracts(0), 0);
            });
            [contractNameSearchInput, contractNumberSearchInput].forEach(input => {
                if(input) input.addEventListener('keypress', (e) => {
                    if (e.key === 'Enter') {
                        e.preventDefault();
                        fetchPendingContracts(0);
                    }
                });
            });
        }
    }

    // --- Data Fetching and Rendering ---
    async function loadAllUsersForAssignment() {
        // 获取所有用户，用于分配。如果用户量巨大，应考虑分页或搜索加载用户。
        // 当前后端 /api/system/users 支持分页。这里我们取前1000个用户作为示例。
        // 实际应用中，可能需要更完善的用户选择机制。
        const userFetchSpinnerId = 'userFetchSpinner'; // 可以在分配面板附近添加一个临时spinner
        toggleLoading(true, null, userFetchSpinnerId);
        try {
            const pageData = await authenticatedFetch(`${API_ALL_USERS_URL}?page=0&size=1000&sort=username,asc`, {}, globalAlertContainerId);
            allUsers = pageData && pageData.content ? pageData.content : [];
            if (allUsers.length === 0 && pageData) { // 即使pageData存在但content为空
                showAlert('系统中无用户数据，或无法加载可分配的用户列表。人员分配功能可能受限。', 'warning', globalAlertContainerId);
            }
        } catch (error) {
            console.error("为合同分配加载用户列表失败。");
            allUsers = [];
            // authenticatedFetch 应该已经显示了错误
        } finally {
            toggleLoading(false, null, userFetchSpinnerId);
        }
    }

    async function fetchPendingContracts(page = 0, size = DEFAULT_PAGE_SIZE_CONTRACT) {
        currentPageContract = page;
        const contractNameSearchVal = contractNameSearchInput ? contractNameSearchInput.value.trim() : '';
        const contractNumberSearchVal = contractNumberSearchInput ? contractNumberSearchInput.value.trim() : '';

        let queryParams = `page=${page}&size=${size}&sort=createdAt,desc`; // 默认按创建时间降序

        if (contractNameSearchVal) {
            queryParams += `&contractNameSearch=${encodeURIComponent(contractNameSearchVal)}`;
        }
        if (contractNumberSearchVal) {
            queryParams += `&contractNumberSearch=${encodeURIComponent(contractNumberSearchVal)}`;
        }

        toggleLoading(true, searchContractsBtn, contractsTableSpinnerId); // 禁用搜索按钮并显示spinner
        try {
            const pageData = await authenticatedFetch(`${API_PENDING_CONTRACTS_URL}?${queryParams}`, {}, globalAlertContainerId);
            if (pageData && pageData.content) {
                renderContractsTable(pageData.content, pageData); // 传递整个pageData给渲染函数
                renderPaginationControls(pageData, paginationControlsContainerId, fetchPendingContracts, DEFAULT_PAGE_SIZE_CONTRACT);
            } else {
                contractsTableBody.innerHTML = `<tr><td colspan="5" class="text-center">无待分配合同。</td></tr>`;
                renderPaginationControls(null, paginationControlsContainerId, fetchPendingContracts, DEFAULT_PAGE_SIZE_CONTRACT);
            }
        } catch (error) {
            contractsTableBody.innerHTML = `<tr><td colspan="5" class="text-center text-danger">加载待分配合同列表失败。</td></tr>`;
            renderPaginationControls(null, paginationControlsContainerId, fetchPendingContracts, DEFAULT_PAGE_SIZE_CONTRACT);
        } finally {
            toggleLoading(false, searchContractsBtn, contractsTableSpinnerId);
        }
    }

    function renderContractsTable(contracts, pageData) {
        contractsTableBody.innerHTML = ''; // 清空现有行
        if (contracts && contracts.length > 0) {
            // 将合同数据存储起来，以便点击行时能获取完整对象
            contractsTableBody.pageData_contracts = contracts; // 存储在DOM元素上，或者使用一个全局变量

            contracts.forEach((contract, index) => {
                const row = contractsTableBody.insertRow();
                row.dataset.contractIndex = index; // 使用索引来查找存储的合同对象

                row.insertCell().textContent = contract.id;
                row.insertCell().textContent = contract.contractName;
                row.insertCell().textContent = contract.contractNumber;
                row.insertCell().textContent = contract.drafter ? contract.drafter.username : 'N/A';

                // 将枚举状态转换为更友好的中文显示 (如果后端没有提供描述)
                let statusDisplay = contract.status;
                // 假设 ContractStatus 枚举在JS中不可用，这里可以硬编码或从某处获取映射
                const statusMap = {
                    'DRAFT': '起草',
                    'PENDING_ASSIGNMENT': '待分配',
                    // 添加其他状态...
                };
                statusDisplay = statusMap[contract.status] || contract.status;
                row.insertCell().textContent = statusDisplay;
            });
        } else if (pageData && pageData.totalElements > 0) {
            contractsTableBody.innerHTML = `<tr><td colspan="5" class="text-center">当前页无数据，但总共有 ${pageData.totalElements} 条记录。</td></tr>`;
        } else {
            // 无数据消息已在 fetchPendingContracts 中处理
        }
    }

    /**
     * 填充用户选择的<select>元素。
     * @param {HTMLSelectElement} selectElement 要填充的<select>元素。
     * @param {Array<User>} usersToPopulate 用于填充的用户数组。
     * @param {Set<string>} initiallyAssignedUserIdsSet (可选) 已分配到此选择框的用户ID集合 (字符串类型)。
     */
    function populateUserSelect(selectElement, usersToPopulate, initiallyAssignedUserIdsSet = new Set()) {
        selectElement.innerHTML = ''; // 清空现有选项
        if (usersToPopulate && usersToPopulate.length > 0) {
            usersToPopulate.forEach(user => {
                const isAssigned = initiallyAssignedUserIdsSet.has(user.id.toString());
                if ((selectElement.id.startsWith('available') && !isAssigned) || (selectElement.id.startsWith('assigned') && isAssigned)) {
                    const option = document.createElement('option');
                    option.value = user.id;
                    option.textContent = `${user.username} (ID: ${user.id})`;
                    selectElement.appendChild(option);
                }
            });
        }
    }

    /**
     * 重置并重新填充所有用户选择列表。
     * "可选用户"列表将包含所有用户中未出现在对应"已选用户"列表中的用户。
     * "已选用户"列表将根据传入的已分配ID集合来填充。
     * @param {Object} assignedIdsByType - 一个对象，键是类型（'countersign', 'approval', 'signing'），值是该类型已分配用户ID的Set。
     */
    function repopulateUserSelectionLists(assignedIdsByType = { countersign: new Set(), approval: new Set(), signing: new Set() }) {
        populateUserSelect(availableCountersignUsersSelect, allUsers, assignedIdsByType.countersign);
        populateUserSelect(assignedCountersignUsersSelect, allUsers, assignedIdsByType.countersign);

        populateUserSelect(availableApprovalUsersSelect, allUsers, assignedIdsByType.approval);
        populateUserSelect(assignedApprovalUsersSelect, allUsers, assignedIdsByType.approval);

        populateUserSelect(availableSigningUsersSelect, allUsers, assignedIdsByType.signing);
        populateUserSelect(assignedSigningUsersSelect, allUsers, assignedIdsByType.signing);
    }

    function resetAssignmentPanel() {
        selectedContractIdInput.value = '';
        selectedContractNameSpan.textContent = '';
        currentSelectedContractData = null; // 清除选中的合同数据
        if (currentSelectedContractRow) {
            currentSelectedContractRow.classList.remove('table-active');
            currentSelectedContractRow = null;
        }
        // 清空所有用户选择列表
        [availableCountersignUsersSelect, assignedCountersignUsersSelect,
            availableApprovalUsersSelect, assignedApprovalUsersSelect,
            availableSigningUsersSelect, assignedSigningUsersSelect].forEach(sel => sel.innerHTML = '');

        assignmentPanel.style.display = 'none';
        noContractSelectedPanel.style.display = 'block';
    }

    // --- Event Handlers ---
    function handleContractRowClickDelegation(event) {
        const rowElement = event.target.closest('tr');
        if (!rowElement || !contractsTableBody.contains(rowElement)) return; //确保点击在表格体内

        const contractIndex = rowElement.dataset.contractIndex;
        if (contractIndex === undefined || !contractsTableBody.pageData_contracts) return;

        const contractData = contractsTableBody.pageData_contracts[parseInt(contractIndex)];
        if (!contractData) return;

        currentSelectedContractData = contractData; // 存储选中的合同数据

        if (currentSelectedContractRow) {
            currentSelectedContractRow.classList.remove('table-active');
        }
        rowElement.classList.add('table-active');
        currentSelectedContractRow = rowElement;

        selectedContractIdInput.value = contractData.id;
        selectedContractNameSpan.textContent = contractData.contractName;

        // 重置并填充用户选择列表
        // 假设新选择合同时，总是清空已选人员（因为是为新合同分配）
        // 如果需要编辑已有的分配，这里的逻辑会更复杂，需要先获取该合同已分配的人员
        repopulateUserSelectionLists();

        assignmentPanel.style.display = 'block';
        noContractSelectedPanel.style.display = 'none';
    }

    function moveOptions(sourceSelect, destSelect) {
        Array.from(sourceSelect.selectedOptions).forEach(option => {
            destSelect.appendChild(option); // 移动选项会从源select中移除
        });
    }

    function handleCancelAssignment() {
        resetAssignmentPanel();
    }

    async function handleSubmitAssignment() {
        const contractId = selectedContractIdInput.value;
        if (!contractId) {
            showAlert('请先从左侧列表中选择一个合同！', 'warning', globalAlertContainerId);
            return;
        }

        const countersignUserIds = Array.from(assignedCountersignUsersSelect.options).map(opt => parseInt(opt.value));
        const approvalUserIds = Array.from(assignedApprovalUsersSelect.options).map(opt => parseInt(opt.value));
        const signUserIds = Array.from(assignedSigningUsersSelect.options).map(opt => parseInt(opt.value));

        if (countersignUserIds.length === 0 && approvalUserIds.length === 0 && signUserIds.length === 0) {
            showAlert('提醒：您没有为当前合同分配任何处理人员。确定要这样提交吗？', 'info', globalAlertContainerId);
            // 不直接返回，让用户决定是否提交空的分配。后端服务应该能处理这种情况。
            // 如果业务上不允许空分配，可以在这里 return 或由后端校验。
        }

        const assignmentData = {
            countersignUserIds,
            approvalUserIds,
            signUserIds
        };

        toggleLoading(true, submitAssignmentBtn);
        try {
            await authenticatedFetch(`${API_ASSIGN_PERSONNEL_URL}/${contractId}/assign`, {
                method: 'POST',
                body: assignmentData
            }, globalAlertContainerId);

            showAlert(`已成功为合同 "${selectedContractNameSpan.textContent}" (ID: ${contractId}) 分配处理人员！`, 'success', globalAlertContainerId);
            fetchPendingContracts(currentPageContract); // 刷新当前页的合同列表（该合同应不再显示）
            resetAssignmentPanel(); // 重置分配面板
        } catch (error) {
            // authenticatedFetch 已经显示了错误信息
            // submitAssignmentBtn 的加载状态会在 finally 中解除
        } finally {
            toggleLoading(false, submitAssignmentBtn);
        }
    }

    // --- Initial Page Load ---
    initializePage();
});