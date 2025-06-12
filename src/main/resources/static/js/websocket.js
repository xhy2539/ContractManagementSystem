// 建立WebSocket连接
let stompClient = null;

function connect() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);
    
    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);
        
        // 订阅合同状态更新主题
        stompClient.subscribe('/topic/contract-status', function(notification) {
            const data = JSON.parse(notification.body);
            handleContractStatusUpdate(data);
        });
    }, function(error) {
        console.log('连接出错: ' + error);
        // 5秒后尝试重新连接
        setTimeout(connect, 5000);
    });
}

function handleContractStatusUpdate(data) {
    // 更新UI显示
    const contractId = data.contractId;
    const status = data.status;
    
    // 更新状态显示
    const statusElement = document.querySelector(`[data-contract-id="${contractId}"] .contract-status`);
    if (statusElement) {
        statusElement.textContent = getStatusDescription(status);
        // 可以添加一些视觉效果来提示状态更新
        statusElement.classList.add('status-updated');
        setTimeout(() => statusElement.classList.remove('status-updated'), 3000);
    }
    
    // 可以显示一个通知
    showNotification(`合同 #${contractId} 状态已更新为: ${getStatusDescription(status)}`);
}

function getStatusDescription(status) {
    const statusMap = {
        'PENDING_APPROVAL': '待审批',
        'REJECTED': '已拒绝',
        'PENDING_SIGNING': '待签订',
        // 添加其他状态映射...
    };
    return statusMap[status] || status;
}

function showNotification(message) {
    // 如果浏览器支持原生通知
    if ("Notification" in window) {
        Notification.requestPermission().then(function(permission) {
            if (permission === "granted") {
                new Notification("合同状态更新", {
                    body: message
                });
            }
        });
    }
    
    // 也可以使用自定义的通知UI
    const notification = document.createElement('div');
    notification.className = 'custom-notification';
    notification.textContent = message;
    document.body.appendChild(notification);
    
    setTimeout(() => {
        notification.remove();
    }, 5000);
}

// 页面加载完成后连接WebSocket
document.addEventListener('DOMContentLoaded', function() {
    connect();
}); 