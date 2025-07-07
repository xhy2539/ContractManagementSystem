import './assets/main.css'

import { createApp } from 'vue'
import { createPinia } from 'pinia'

// 导入 Element Plus
import ElementPlus from 'element-plus'
// 导入 Element Plus 的样式文件
import 'element-plus/dist/index.css'

import App from './App.vue'
import router from './router'

const app = createApp(App)

app.use(createPinia()) // 使用 Pinia 状态管理
app.use(router)       // 使用 Vue Router 路由

// 使用 Element Plus
app.use(ElementPlus)

app.mount('#app') // 将 Vue 应用挂载到 #app 元素
