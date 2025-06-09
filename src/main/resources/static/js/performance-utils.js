/**
 * 性能优化工具函数
 */

// 防抖函数 - 减少频繁的搜索请求
function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

// 节流函数 - 限制函数执行频率
function throttle(func, limit) {
    let inThrottle;
    return function() {
        const args = arguments;
        const context = this;
        if (!inThrottle) {
            func.apply(context, args);
            inThrottle = true;
            setTimeout(() => inThrottle = false, limit);
        }
    }
}

// 优化的AJAX请求函数
function optimizedFetch(url, options = {}) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), 10000); // 10秒超时
    
    return fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
            'Content-Type': 'application/json',
            ...options.headers
        }
    }).finally(() => {
        clearTimeout(timeoutId);
    });
}

// 表格加载优化
function showTableLoading(tableId) {
    const table = document.getElementById(tableId);
    if (table) {
        const tbody = table.querySelector('tbody');
        if (tbody) {
            tbody.innerHTML = `
                <tr>
                    <td colspan="100%" class="text-center py-4">
                        <div class="spinner-border spinner-border-sm" role="status">
                            <span class="visually-hidden">加载中...</span>
                        </div>
                        <span class="ms-2">正在加载数据...</span>
                    </td>
                </tr>
            `;
        }
    }
}

// 虚拟滚动优化（用于大数据列表）
class VirtualScroller {
    constructor(container, itemHeight, renderItem) {
        this.container = container;
        this.itemHeight = itemHeight;
        this.renderItem = renderItem;
        this.visibleStart = 0;
        this.visibleEnd = 0;
        this.data = [];
        
        this.setupScrollListener();
    }
    
    setData(data) {
        this.data = data;
        this.render();
    }
    
    setupScrollListener() {
        this.container.addEventListener('scroll', throttle(() => {
            this.updateVisibleRange();
            this.render();
        }, 16)); // ~60fps
    }
    
    updateVisibleRange() {
        const scrollTop = this.container.scrollTop;
        const containerHeight = this.container.clientHeight;
        
        this.visibleStart = Math.floor(scrollTop / this.itemHeight);
        this.visibleEnd = Math.min(
            this.visibleStart + Math.ceil(containerHeight / this.itemHeight) + 1,
            this.data.length
        );
    }
    
    render() {
        const visibleData = this.data.slice(this.visibleStart, this.visibleEnd);
        const html = visibleData.map((item, index) => 
            this.renderItem(item, this.visibleStart + index)
        ).join('');
        
        this.container.innerHTML = html;
        this.container.style.paddingTop = `${this.visibleStart * this.itemHeight}px`;
        this.container.style.paddingBottom = `${(this.data.length - this.visibleEnd) * this.itemHeight}px`;
    }
}

// 简化的加载状态管理
const LoadingManager = {
    show(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.classList.add('loading');
            element.style.opacity = '0.6';
            element.style.pointerEvents = 'none';
        }
    },
    
    hide(elementId) {
        const element = document.getElementById(elementId);
        if (element) {
            element.classList.remove('loading');
            element.style.opacity = '1';
            element.style.pointerEvents = 'auto';
        }
    }
};

// 缓存管理
const CacheManager = {
    cache: new Map(),
    maxSize: 50,
    
    set(key, value, ttl = 300000) { // 默认5分钟TTL
        if (this.cache.size >= this.maxSize) {
            const firstKey = this.cache.keys().next().value;
            this.cache.delete(firstKey);
        }
        
        this.cache.set(key, {
            value,
            expiry: Date.now() + ttl
        });
    },
    
    get(key) {
        const item = this.cache.get(key);
        if (!item) return null;
        
        if (Date.now() > item.expiry) {
            this.cache.delete(key);
            return null;
        }
        
        return item.value;
    },
    
    clear() {
        this.cache.clear();
    }
};

// 导出工具函数
window.PerformanceUtils = {
    debounce,
    throttle,
    optimizedFetch,
    showTableLoading,
    VirtualScroller,
    LoadingManager,
    CacheManager
}; 