<template>
  <el-container>
    <el-header class="app-header">
      <div class="header-content">
        <span class="project-title">智慧车站安全卫士</span>
        <el-menu mode="horizontal" :router="true" class="header-menu">
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/monitor">监控中心</el-menu-item>
          <el-menu-item index="/login" style="margin-left: auto;">登录</el-menu-item>
          <el-menu-item index="/register">注册</el-menu-item>
        </el-menu>
      </div>
    </el-header>
    <el-main class="app-main">
      <router-view></router-view>
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
// 这里不再需要导入 RouterLink, RouterView, HelloWorld
// 因为 Element Plus 的 el-menu 已经处理了路由跳转
</script>

<style scoped>
/* 移除默认的样式，添加新的 Element Plus 相关样式 */

.el-container {
  min-height: 100vh; /* 确保容器至少占满整个视口高度 */
}

.app-header {
  background-color: #f5f7fa; /* Element Plus 默认背景色 */
  color: #333;
  line-height: 60px; /* Element Plus 默认头部高度 */
  border-bottom: 1px solid #ebeef5; /* Element Plus 默认边框色 */
  padding: 0 20px; /* 默认内边距 */
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.08); /* 轻微阴影 */
}

.header-content {
  display: flex;
  align-items: center;
  justify-content: space-between; /* 标题和菜单左右对齐 */
  height: 100%;
}

.project-title {
  font-size: 22px; /* 项目标题字体大小 */
  font-weight: bold;
  color: #303133; /* Element Plus 默认字体颜色 */
}

.header-menu {
  border-bottom: none !important; /* 移除 el-menu 默认的底部边框 */
  background-color: transparent !important; /* 使菜单背景透明，继承头部背景 */
}

/* 调整菜单项在水平模式下的样式，确保其与整体设计融合 */
.header-menu .el-menu-item {
  height: 60px;
  line-height: 60px;
}

.app-main {
  padding: 20px; /* 主内容区域的内边距 */
  background-color: #ffffff; /* 主内容区背景色 */
}
</style>
