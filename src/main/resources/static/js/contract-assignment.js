// static/js/contract-assignment.js
document.addEventListener('DOMContentLoaded', function () {
    // --- DOM Element References ---
    const contractsTableBody = document.querySelector('#contractsTable tbody');
    const assignmentPanel = document.getElementById('assignmentPanel');
    const noContractSelectedPanel = document.getElementById('noContractSelectedPanel');
    const selectedContractNameSpan = document.getElementById('selectedContractName');
    const selectedContractIdInput = document.getElementById('selectedContractId');

    const availableCountersignUsersSelect = document.getElementById('availableCountersignUsers');
    const assignedCountersignUsersSelect = document.getElementById('assignedCountersignUsers');
    const availableApprovalUsersSelect = document.getElementById('availableApprovalUsers');
    const assignedApprovalUsersSelect = document.getElementById('assignedApprovalUsers');
    const availableSigningUsersSelect = document.getElementById('availableSigningUsers');
    const assignedSigningUsersSelect = document.getElementById('assignedSigningUsers');

    // 新增定稿人员的DOM引用
    const availableFinalizeUsersSelect = document.getElementById('availableFinalizeUsers');
    const assignedFinalizeUsersSelect = document.getElementById('assignedFinalizeUsers');

    const addCountersignUserBtn = document.getElementById('addCountersignUserBtn');
    const removeCountersignUserBtn = document.getElementById('removeCountersignUserBtn');
    const addApprovalUserBtn = document.getElementById('addApprovalUserBtn');
    const removeApprovalUserBtn = document.getElementById('removeApprovalUserBtn');
    const addSigningUserBtn = document.getElementById('addSigningUserBtn');
    const removeSigningUserBtn = document.getElementById('removeSigningUserBtn');

    // 新增定稿人员的按钮DOM引用
    const addFinalizeUserBtn = document.getElementById('addFinalizeUserBtn');
    const removeFinalizeUserBtn = document.getElementById('removeFinalizeUserBtn');

    const submitAssignmentBtn = document.getElementById('submitAssignmentBtn');
    const cancelAssignmentBtn = document.getElementById('cancelAssignmentBtn');

    const paginationControlsContainerId = 'paginationControlsContract';
    const globalAlertContainerId = 'globalAlertContainer';
    const contractsTableSpinnerId = 'contractsTableSpinner';

    const userFetchSpinnerGlobal = document.getElementById('userFetchSpinnerGlobal'); // 新增：全局用户加载spinner的引用

    const contractNameSearchInput = document.getElementById('contractNameSearch');
    const contractNumberSearchInput = document.getElementById('contractNumberSearch');
    const searchContractsBtn = document.getElementById('searchContractsBtn');
    const contractFilterForm = document.getElementById('contractFilterForm');

    // --- State & Configuration ---
    let allUsers = [];
    let currentSelectedContractRow = null;
    let currentSelectedContractData = null;
    let currentPageContract = 0;
    const DEFAULT_PAGE_SIZE_CONTRACT = 10;

    // --- API Endpoints ---
    const API_PENDING_CONTRACTS_URL = '/api/admin/contract-assignments/pending';
    const API_ASSIGN_PERSONNEL_URL = '/api/admin/contract-assignments';
    const API_ALL_USERS_URL = '/api/system/users';

    // --- Initialization ---
    async function initializePage() {
        await loadAllUsersForAssignment();
        await fetchPendingContracts();

        if (contractsTableBody) {
            contractsTableBody.addEventListener('click', handleContractRowClickDelegation);
        }

        if (addCountersignUserBtn) addCountersignUserBtn.addEventListener('click', () => moveOptions(availableCountersignUsersSelect, assignedCountersignUsersSelect));
        if (removeCountersignUserBtn) removeCountersignUserBtn.addEventListener('click', () => moveOptions(assignedCountersignUsersSelect, availableCountersignUsersSelect));
        if (addApprovalUserBtn) addApprovalUserBtn.addEventListener('click', () => moveOptions(availableApprovalUsersSelect, assignedApprovalUsersSelect));
        if (removeApprovalUserBtn) removeApprovalUserBtn.addEventListener('click', () => moveOptions(assignedApprovalUsersSelect, availableApprovalUsersSelect));
        if (addSigningUserBtn) addSigningUserBtn.addEventListener('click', () => moveOptions(availableSigningUsersSelect, assignedSigningUsersSelect));
        if (removeSigningUserBtn) removeSigningUserBtn.addEventListener('click', () => moveOptions(assignedSigningUsersSelect, availableSigningUsersSelect));
        // 新增定稿人员按钮的事件监听
        if (addFinalizeUserBtn) addFinalizeUserBtn.addEventListener('click', () => moveOptions(availableFinalizeUsersSelect, assignedFinalizeUsersSelect));
        if (removeFinalizeUserBtn) removeFinalizeUserBtn.addEventListener('click', () => moveOptions(assignedFinalizeUsersSelect, availableFinalizeUsersSelect));


        if (submitAssignmentBtn) {
            submitAssignmentBtn.addEventListener('click', handleSubmitAssignment);
        }
        if (cancelAssignmentBtn) {
            cancelAssignmentBtn.addEventListener('click', handleCancelAssignment);
        }

        if (searchContractsBtn) {
            searchContractsBtn.addEventListener('click', () => fetchPendingContracts(0));
        }
        if (contractFilterForm) {
            contractFilterForm.addEventListener('reset', () => {
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
        if (noContractSelectedPanel && assignmentPanel) {
            noContractSelectedPanel.style.display = 'block';
            assignmentPanel.style.display = 'none';
        }
    }

    // --- Data Fetching and Rendering ---
    async function loadAllUsersForAssignment() {
        if (userFetchSpinnerGlobal) userFetchSpinnerGlobal.style.display = 'block'; // 显示全局 spinner
        try {
            const pageData = await authenticatedFetch(`${API_ALL_USERS_URL}?page=0&size=1000&sort=username,asc`, {}, globalAlertContainerId);
            allUsers = pageData && pageData.content ? pageData.content : [];
            if (allUsers.length === 0 && pageData) {
                showAlert('系统中无用户数据，或无法加载可分配的用户列表。人员分配功能可能受限。', 'warning', globalAlertContainerId);
            }
        } catch (error) {
            console.error("为合同分配加载用户列表失败。");
            allUsers = [];
        } finally {
            if (userFetchSpinnerGlobal) userFetchSpinnerGlobal.style.display = 'none'; // 隐藏全局 spinner
        }
    }

    async function fetchPendingContracts(page = 0, size = DEFAULT_PAGE_SIZE_CONTRACT) {
        currentPageContract = page;
        const contractNameSearchVal = contractNameSearchInput ? contractNameSearchInput.value.trim() : '';
        const contractNumberSearchVal = contractNumberSearchInput ? contractNumberSearchInput.value.trim() : '';

        let queryParams = `page=${page}&size=${size}&sort=createdAt,desc`;

        if (contractNameSearchVal) {
            queryParams += `&contractNameSearch=${encodeURIComponent(contractNameSearchVal)}`;
        }
        if (contractNumberSearchVal) {
            queryParams += `&contractNumberSearch=${encodeURIComponent(contractNumberSearchVal)}`;
        }

        toggleLoading(true, searchContractsBtn, contractsTableSpinnerId);
        try {
            const pageData = await authenticatedFetch(`${API_PENDING_CONTRACTS_URL}?${queryParams}`, {}, globalAlertContainerId);
            if (pageData && pageData.content) {
                renderContractsTable(pageData.content, pageData);
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
        contractsTableBody.innerHTML = '';
        if (contracts && contracts.length > 0) {
            contractsTableBody.pageData_contracts = contracts;
            contracts.forEach((contract, index) => {
                const row = contractsTableBody.insertRow();
                row.dataset.contractIndex = index;
                row.insertCell().textContent = contract.id;
                row.insertCell().textContent = contract.contractName;
                row.insertCell().textContent = contract.contractNumber;
                row.insertCell().textContent = contract.drafter ? contract.drafter.username : 'N/A';
                let statusDisplay = contract.status;
                const statusMap = {
                    'DRAFT': '起草',
                    'PENDING_ASSIGNMENT': '待分配',
                    'PENDING_COUNTERSIGN': '待会签',
                    'PENDING_FINALIZATION':'待定稿',
                    'PENDING_APPROVAL': '待审批',
                    'PENDING_SIGNING': '待签订',
                    'ACTIVE': '有效',
                    'COMPLETED': '完成',
                    'EXPIRED': '过期',
                    'TERMINATED': '终止'
                };
                statusDisplay = statusMap[contract.status] || contract.status;
                row.insertCell().textContent = statusDisplay;
            });
        } else if (pageData && pageData.totalElements > 0) {
            contractsTableBody.innerHTML = `<tr><td colspan="5" class="text-center">当前页无数据，但总共有 ${pageData.totalElements} 条记录。</td></tr>`;
        } else {
            contractsTableBody.innerHTML = `<tr><td colspan="5" class="text-center">无待分配合同。</td></tr>`;
        }
    }

    function populateUserSelect(selectElement, usersToPopulate, initiallyAssignedUserIdsSet = new Set()) {
        selectElement.innerHTML = '';
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

    function repopulateUserSelectionLists(assignedIdsByType = { countersign: new Set(), approval: new Set(), signing: new Set(), finalize: new Set() }) { // 增加 finalize
        populateUserSelect(availableCountersignUsersSelect, allUsers, assignedIdsByType.countersign);
        populateUserSelect(assignedCountersignUsersSelect, allUsers, assignedIdsByType.countersign);
        populateUserSelect(availableApprovalUsersSelect, allUsers, assignedIdsByType.approval);
        populateUserSelect(assignedApprovalUsersSelect, allUsers, assignedIdsByType.approval);
        populateUserSelect(availableSigningUsersSelect, allUsers, assignedIdsByType.signing);
        populateUserSelect(assignedSigningUsersSelect, allUsers, assignedIdsByType.signing);
        // 新增定稿人员的选择列表填充
        populateUserSelect(availableFinalizeUsersSelect, allUsers, assignedIdsByType.finalize);
        populateUserSelect(assignedFinalizeUsersSelect, allUsers, assignedIdsByType.finalize);
    }

    function resetAssignmentPanel() {
        selectedContractIdInput.value = '';
        selectedContractNameSpan.textContent = '';
        currentSelectedContractData = null;
        if (currentSelectedContractRow) {
            currentSelectedContractRow.classList.remove('table-active');
            currentSelectedContractRow = null;
        }
        [availableCountersignUsersSelect, assignedCountersignUsersSelect,
            availableApprovalUsersSelect, assignedApprovalUsersSelect,
            availableSigningUsersSelect, assignedSigningUsersSelect,
            availableFinalizeUsersSelect, assignedFinalizeUsersSelect].forEach(sel => sel.innerHTML = ''); // 增加定稿人员列表的清空
        assignmentPanel.style.display = 'none';
        noContractSelectedPanel.style.display = 'block';
    }

    function handleContractRowClickDelegation(event) {
        const rowElement = event.target.closest('tr');
        if (!rowElement || !contractsTableBody.contains(rowElement)) return;
        const contractIndex = rowElement.dataset.contractIndex;
        if (contractIndex === undefined || !contractsTableBody.pageData_contracts) return;
        const contractData = contractsTableBody.pageData_contracts[parseInt(contractIndex)];
        if (!contractData) return;
        currentSelectedContractData = contractData;
        if (currentSelectedContractRow) {
            currentSelectedContractRow.classList.remove('table-active');
        }
        rowElement.classList.add('table-active');
        currentSelectedContractRow = rowElement;
        selectedContractIdInput.value = contractData.id;
        selectedContractNameSpan.textContent = contractData.contractName;
        repopulateUserSelectionLists();
        assignmentPanel.style.display = 'block';
        noContractSelectedPanel.style.display = 'none';
    }

    function moveOptions(sourceSelect, destSelect) {
        Array.from(sourceSelect.selectedOptions).forEach(option => {
            destSelect.appendChild(option);
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
        const finalizeUserIds = Array.from(assignedFinalizeUsersSelect.options).map(opt => parseInt(opt.value)); // 获取定稿人员ID

        if (countersignUserIds.length === 0 && approvalUserIds.length === 0 && signUserIds.length === 0 && finalizeUserIds.length === 0) { // 增加对定稿人员的检查
            showAlert('请至少为合同分配一种类型的处理人员（会签、审批、签订或定稿）。', 'warning', globalAlertContainerId);
            return;
        }
        const assignmentData = {
            countersignUserIds,
            approvalUserIds,
            signUserIds,
            finalizeUserIds // 包含定稿人员ID
        };
        toggleLoading(true, submitAssignmentBtn);
        try {
            await authenticatedFetch(`${API_ASSIGN_PERSONNEL_URL}/${contractId}/assign`, {
                method: 'POST',
                body: assignmentData
            }, globalAlertContainerId);
            showAlert(`已成功为合同 "${selectedContractNameSpan.textContent}" (ID: ${contractId}) 分配处理人员！`, 'success', globalAlertContainerId);
            fetchPendingContracts(currentPageContract);
            resetAssignmentPanel();
        } catch (error) {
            // Error handled
        } finally {
            toggleLoading(false, submitAssignmentBtn);
        }
    }

    initializePage();
});