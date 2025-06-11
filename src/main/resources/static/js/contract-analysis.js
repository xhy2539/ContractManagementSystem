// 全局变量
let analysisModal = null;

// 初始化函数
function initializeAnalysis() {
    // 初始化模态框
    const modalElement = document.getElementById('analysisResultModal');
    if (modalElement) {
        analysisModal = new bootstrap.Modal(modalElement, {
            backdrop: 'static',
            keyboard: false
        });
    }

    // 加载初始数据
    loadRecentAnalysis();
    updateLastUpdateTime();
}

// 执行快速分析
async function performQuickAnalysis(event) {
    event.preventDefault();
    
    const form = event.target;
    const contractId = form.querySelector('#contractSelect').value;
    const analysisType = form.querySelector('#analysisType').value;

    if (!contractId || !analysisType) {
        showAlert('请选择合同和分析类型', 'warning');
        return false;
    }

    try {
        // 显示模态框
        if (analysisModal) {
            analysisModal.show();
        }

        // 显示加载状态
        document.getElementById('analysisResultContent').innerHTML = `
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">分析中...</span>
                </div>
                <p class="mt-3 text-muted">正在进行智能分析，请稍候...</p>
            </div>
        `;

        // 发送分析请求
        const response = await fetch('/api/analysis/quick', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                contractId: contractId,
                analysisType: analysisType
            })
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();
        
        // 更新模态框内容
        document.getElementById('analysisResultContent').innerHTML = formatAnalysisResult(result);
        
        // 刷新分析记录列表
        await loadRecentAnalysis();
        
    } catch (error) {
        console.error('分析请求失败:', error);
        document.getElementById('analysisResultContent').innerHTML = `
            <div class="alert alert-danger" role="alert">
                <h4 class="alert-heading">分析失败</h4>
                <p>${error.message || '执行分析时发生错误，请稍后重试。'}</p>
                <hr>
                <div class="d-flex justify-content-end">
                    <button type="button" class="btn btn-outline-danger" onclick="retryAnalysis()">
                        <i class="bi bi-arrow-clockwise me-1"></i>重试
                    </button>
                </div>
            </div>
        `;
    }

    return false;
}

// 加载最近分析记录
async function loadRecentAnalysis() {
    const container = document.getElementById('recentAnalysisList');
    if (!container) {
        console.error('找不到最近分析记录容器');
        return;
    }

    try {
        container.innerHTML = `
            <div class="text-center py-4">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">加载中...</span>
                </div>
                <p class="mt-2 text-muted">正在加载分析记录...</p>
            </div>
        `;

        const response = await fetch('/api/analysis/recent');
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const data = await response.json();
        
        if (!data || data.length === 0) {
            container.innerHTML = `
                <div class="text-center py-4">
                    <i class="bi bi-inbox fs-1 text-muted"></i>
                    <p class="mt-2 text-muted">暂无分析记录</p>
                </div>
            `;
            return;
        }

        container.innerHTML = `
            <div class="table-responsive">
                <table class="table table-hover align-middle">
                    <thead>
                        <tr>
                            <th>合同名称</th>
                            <th>分析类型</th>
                            <th>风险等级</th>
                            <th>分析时间</th>
                            <th>操作</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${data.map(record => `
                            <tr>
                                <td>${record.contractName}</td>
                                <td>${getAnalysisTypeText(record.analysisType)}</td>
                                <td>
                                    <span class="badge ${getRiskLevelClass(record.riskLevel)}">
                                        ${getRiskLevelText(record.riskLevel)}
                                    </span>
                                </td>
                                <td>${formatDateTime(record.analysisTime)}</td>
                                <td>
                                    <button type="button" class="btn btn-sm btn-outline-primary" 
                                            onclick="viewAnalysisResult('${record.id}')">
                                        查看详情
                                    </button>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;

    } catch (error) {
        console.error('加载分析记录失败:', error);
        container.innerHTML = `
            <div class="alert alert-danger mb-0" role="alert">
                <div class="d-flex align-items-center">
                    <div class="flex-grow-1">
                        <i class="bi bi-exclamation-triangle me-2"></i>
                        加载分析记录失败
                    </div>
                    <button type="button" class="btn btn-sm btn-outline-danger" 
                            onclick="loadRecentAnalysis()">
                        <i class="bi bi-arrow-clockwise me-1"></i>重试
                    </button>
                </div>
            </div>
        `;
    }
}

// 查看分析结果
async function viewAnalysisResult(analysisId) {
    try {
        if (analysisModal) {
            analysisModal.show();
        }

        document.getElementById('analysisResultContent').innerHTML = `
            <div class="text-center py-5">
                <div class="spinner-border text-primary" role="status">
                    <span class="visually-hidden">加载中...</span>
                </div>
                <p class="mt-3 text-muted">正在加载分析结果...</p>
            </div>
        `;

        const response = await fetch(`/api/analysis/${analysisId}`);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const result = await response.json();
        document.getElementById('analysisResultContent').innerHTML = formatAnalysisResult(result);

    } catch (error) {
        console.error('加载分析结果失败:', error);
        document.getElementById('analysisResultContent').innerHTML = `
            <div class="alert alert-danger" role="alert">
                <h4 class="alert-heading">加载失败</h4>
                <p>无法加载分析结果，请稍后重试。</p>
                <hr>
                <div class="d-flex justify-content-end">
                    <button type="button" class="btn btn-outline-danger" 
                            onclick="viewAnalysisResult('${analysisId}')">
                        <i class="bi bi-arrow-clockwise me-1"></i>重试
                    </button>
                </div>
            </div>
        `;
    }
}

// 格式化分析结果
function formatAnalysisResult(result) {
    return `
        <div class="analysis-result">
            <div class="mb-4">
                <h6 class="fw-bold">基本信息</h6>
                <div class="table-responsive">
                    <table class="table table-sm">
                        <tr>
                            <th style="width: 120px">合同名称</th>
                            <td>${result.contractName}</td>
                        </tr>
                        <tr>
                            <th>分析类型</th>
                            <td>${getAnalysisTypeText(result.analysisType)}</td>
                        </tr>
                        <tr>
                            <th>风险等级</th>
                            <td>
                                <span class="badge ${getRiskLevelClass(result.riskLevel)}">
                                    ${getRiskLevelText(result.riskLevel)}
                                </span>
                            </td>
                        </tr>
                    </table>
                </div>
            </div>
            
            <div class="mb-4">
                <h6 class="fw-bold">分析详情</h6>
                <div class="analysis-details">
                    ${formatAnalysisDetails(result.details)}
                </div>
            </div>
        </div>
    `;
}

// 格式化分析详情
function formatAnalysisDetails(details) {
    if (!details || details.length === 0) {
        return '<div class="text-muted">无详细分析信息</div>';
    }

    return details.map(detail => `
        <div class="card mb-3">
            <div class="card-body">
                <h6 class="card-title">
                    <span class="badge ${getIssueLevelClass(detail.level)} me-2">
                        ${getIssueLevelText(detail.level)}
                    </span>
                    ${detail.title}
                </h6>
                <p class="card-text">${detail.description}</p>
                ${detail.suggestion ? `
                    <div class="mt-2">
                        <small class="text-muted">建议：${detail.suggestion}</small>
                    </div>
                ` : ''}
            </div>
        </div>
    `).join('');
}

// 辅助函数
function getIssueLevelClass(level) {
    const classMap = {
        'INFO': 'bg-info',
        'WARNING': 'bg-warning',
        'ERROR': 'bg-danger'
    };
    return classMap[level] || 'bg-secondary';
}

function getIssueLevelText(level) {
    const textMap = {
        'INFO': '提示',
        'WARNING': '警告',
        'ERROR': '错误'
    };
    return textMap[level] || level;
}

// 刷新分析记录
function refreshRecentAnalysis() {
    loadRecentAnalysis();
    updateLastUpdateTime();
}

// 更新最后更新时间
function updateLastUpdateTime() {
    const now = new Date();
    const timeStr = now.toLocaleTimeString('zh-CN', { 
        hour: '2-digit', 
        minute: '2-digit' 
    });
    document.getElementById('lastUpdate').textContent = timeStr;
}

// 获取分析类型中文文本
function getAnalysisTypeText(type) {
    const typeMap = {
        'RISK_ANALYSIS': '风险分析',
        'CLAUSE_CHECK': '条款检查',
        'COMPLIANCE_CHECK': '合规检查',
        'FULL': '全面分析'
    };
    return typeMap[type] || type;
}

// 获取风险等级中文文本
function getRiskLevelText(level) {
    const levelMap = {
        'LOW': '低风险',
        'MEDIUM': '中等风险',
        'HIGH': '高风险',
        'CRITICAL': '严重风险'
    };
    return levelMap[level] || level;
}

// 获取风险等级对应的样式类
function getRiskLevelClass(level) {
    const classMap = {
        'LOW': 'bg-success',
        'MEDIUM': 'bg-warning',
        'HIGH': 'bg-danger',
        'CRITICAL': 'bg-danger'
    };
    return classMap[level] || 'bg-secondary';
}

// 格式化日期时间
function formatDateTime(dateTimeStr) {
    const date = new Date(dateTimeStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

// 在页面加载完成后初始化
document.addEventListener('DOMContentLoaded', initializeAnalysis); 