// 建立WebSocket连接
let stompClient = null;
let currentUsername = null; // 将从页面中获取当前用户名

function connect() {
    // 从页面中获取当前用户名
    currentUsername = document.getElementById('current-username').value;
    
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        
        // 订阅合同更新
        stompClient.subscribe('/topic/contract-updates', handleContractUpdates);
        
        // 订阅业务更新
        stompClient.subscribe('/topic/business-updates', handleBusinessUpdates);
        
        // 订阅个人任务更新
        if (currentUsername) {
            stompClient.subscribe('/topic/user/' + currentUsername + '/assignments', handlePersonalAssignments);
            stompClient.subscribe('/topic/user/' + currentUsername + '/tasks', handleTaskUpdates);
        }
        
        // 连接后立即请求最新数据
        refreshData();
        
    }, function(error) {
        console.log('连接出错: ' + error);
        setTimeout(connect, 5000);
    });
}

function handleContractUpdates(message) {
    const data = JSON.parse(message.body);
    
    switch(data.type) {
        case 'CONTRACT_STATUS_CHANGE':
            updateContractStatus(data);
            break;
        case 'CONTRACT_ASSIGNMENT':
            handleContractAssignment(data);
            break;
    }
}

function handleBusinessUpdates(message) {
    const data = JSON.parse(message.body);
    if (data.type === 'NEW_BUSINESS') {
        updateBusinessList(data);
    }
}

function handlePersonalAssignments(message) {
    const data = JSON.parse(message.body);
    if (data.assignedTo === currentUsername) {
        updateAssignmentsList(data);
    }
}

function handleTaskUpdates(message) {
    const data = JSON.parse(message.body);
    if (data.username === currentUsername) {
        refreshTaskList();
    }
}

function updateContractStatus(data) {
    // 更新合同状态显示
    const statusElement = document.querySelector(`[data-contract-id="${data.contractId}"] .contract-status`);
    if (statusElement) {
        statusElement.textContent = getStatusDescription(data.status);
        highlightElement(statusElement);
    }
    showNotification(`合同 #${data.contractId} 状态已更新为: ${getStatusDescription(data.status)}`);
}

function handleContractAssignment(data) {
    // 如果是分配给当前用户的合同
    if (data.assignedTo === currentUsername) {
        refreshTaskList();
        showNotification('您有新的合同待处理');
    }
    // 更新合同列表
    refreshContractList();
}

function updateBusinessList(data) {
    // 刷新业务列表
    refreshBusinessList();
    showNotification(`新的${data.businessType}业务已创建`);
}

function updateAssignmentsList(data) {
    // 刷新分配列表
    refreshAssignmentsList();
    showNotification('您有新的任务分配');
}

// 刷新各种列表的函数
function refreshTaskList() {
    const taskList = document.getElementById('task-list');
    if (taskList) {
        fetch('/api/tasks/my-tasks')
            .then(response => response.json())
            .then(data => updateTaskListUI(data))
            .catch(error => console.error('Error fetching tasks:', error));
    }
}

function refreshContractList() {
    const contractList = document.getElementById('contract-list');
    if (contractList) {
        fetch('/api/contracts')
            .then(response => response.json())
            .then(data => updateContractListUI(data))
            .catch(error => console.error('Error fetching contracts:', error));
    }
}

function refreshBusinessList() {
    const businessList = document.getElementById('business-list');
    if (businessList) {
        fetch('/api/business')
            .then(response => response.json())
            .then(data => updateBusinessListUI(data))
            .catch(error => console.error('Error fetching business:', error));
    }
}

function refreshAssignmentsList() {
    const assignmentsList = document.getElementById('assignments-list');
    if (assignmentsList) {
        fetch('/api/assignments')
            .then(response => response.json())
            .then(data => updateAssignmentsListUI(data))
            .catch(error => console.error('Error fetching assignments:', error));
    }
}

// 更新UI的辅助函数
function updateTaskListUI(data) {
    const taskList = document.getElementById('task-list');
    if (taskList) {
        // 根据数据更新任务列表UI
        taskList.innerHTML = data.map(task => `
            <div class="task-item" data-task-id="${task.id}">
                <h3>${task.title}</h3>
                <p>${task.description}</p>
                <span class="task-status">${task.status}</span>
            </div>
        `).join('');
    }
}

function updateContractListUI(data) {
    const contractList = document.getElementById('contract-list');
    if (contractList) {
        // 根据数据更新合同列表UI
        contractList.innerHTML = data.map(contract => `
            <div class="contract-item" data-contract-id="${contract.id}">
                <h3>${contract.title}</h3>
                <p>${contract.description}</p>
                <span class="contract-status">${getStatusDescription(contract.status)}</span>
            </div>
        `).join('');
    }
}

function updateBusinessListUI(data) {
    const businessList = document.getElementById('business-list');
    if (businessList) {
        // 根据数据更新业务列表UI
        businessList.innerHTML = data.map(business => `
            <div class="business-item" data-business-id="${business.id}">
                <h3>${business.type}</h3>
                <p>${business.description}</p>
                <span class="business-status">${business.status}</span>
            </div>
        `).join('');
    }
}

function updateAssignmentsListUI(data) {
    const assignmentsList = document.getElementById('assignments-list');
    if (assignmentsList) {
        // 根据数据更新分配列表UI
        assignmentsList.innerHTML = data.map(assignment => `
            <div class="assignment-item" data-assignment-id="${assignment.id}">
                <h3>${assignment.title}</h3>
                <p>${assignment.description}</p>
                <span class="assignment-status">${assignment.status}</span>
            </div>
        `).join('');
    }
}

function highlightElement(element) {
    element.classList.add('highlight');
    setTimeout(() => element.classList.remove('highlight'), 3000);
}

function getStatusDescription(status) {
    const statusMap = {
        'PENDING_APPROVAL': '待审批',
        'REJECTED': '已拒绝',
        'PENDING_SIGNING': '待签订',
        'SIGNED': '已签订',
        'COMPLETED': '已完成',
        'CANCELLED': '已取消'
    };
    return statusMap[status] || status;
}

function showNotification(message) {
    // 浏览器原生通知
    if ("Notification" in window) {
        Notification.requestPermission().then(function(permission) {
            if (permission === "granted") {
                new Notification("系统通知", {
                    body: message,
                    icon: '/images/notification-icon.png'
                });
            }
        });
    }
    
    // 自定义通知UI
    const notification = document.createElement('div');
    notification.className = 'custom-notification';
    notification.textContent = message;
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.classList.add('fade-out');
        setTimeout(() => notification.remove(), 500);
    }, 4500);
}

// 初始化数据刷新函数
function refreshData() {
    refreshTaskList();
    refreshContractList();
    refreshBusinessList();
    refreshAssignmentsList();
}

// 页面加载完成后连接WebSocket
document.addEventListener('DOMContentLoaded', function() {
    connect();
}); 