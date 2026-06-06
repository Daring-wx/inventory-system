<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useAppStore } from '../store/app'
import { useUserStore } from '../store/user'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const breadcrumbs = computed(() =>
  route.matched.filter(r => r.path && r.meta?.title).map(r => ({ path: r.path, title: r.meta.title as string }))
)
function handleMenuSelect(index: string) { router.push(index) }
function handleLogout() { userStore.logout(); router.push('/login') }
</script>

<template>
  <el-container class="app-layout">
    <!-- 侧边栏 - 白底绿字 -->
    <el-aside :width="appStore.sidebarCollapsed ? '64px' : '220px'" class="app-aside">
      <div class="logo">
        <img v-if="appStore.sidebarCollapsed" src="/favicon.svg" alt="logo" class="logo-icon" />
        <span v-else class="logo-text">进销存管理系统</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="appStore.sidebarCollapsed"
        :unique-opened="true"
        @select="handleMenuSelect"
      >
        <template v-for="menu in appStore.menus" :key="menu.path">
          <el-sub-menu v-if="menu.children && (menu.path !== '/system' || userStore.isAdmin)" :index="menu.path">
            <template #title>
              <el-icon><component :is="(ElementPlusIconsVue as any)[menu.icon!]" /></el-icon>
              <span>{{ menu.title }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.path" :index="child.path">
              <el-icon><component :is="(ElementPlusIconsVue as any)[child.icon!]" /></el-icon>
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon><component :is="(ElementPlusIconsVue as any)[menu.icon!]" /></el-icon>
            <template #title>{{ menu.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <el-container>
      <!-- 顶部栏 -->
      <el-header class="app-header">
        <div class="header-left">
          <el-icon class="collapse-btn" @click="appStore.toggleSidebar" size="20">
            <component :is="appStore.sidebarCollapsed ? 'Expand' : 'Fold'" />
          </el-icon>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="crumb in breadcrumbs" :key="crumb.path">{{ crumb.title }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown @command="handleLogout">
            <span class="user-info">
              <span class="user-avatar">{{ (userStore.userInfo?.realName || '管')?.charAt(0) }}</span>
              <span class="user-name">{{ userStore.userInfo?.realName || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="app-main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.app-layout { height: 100vh; }

/* 左侧白底 */
.app-aside {
  background: #fff;
  border-right: 1px solid #e8ece8;
  transition: width 0.3s;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 20px;
  border-bottom: 1px solid #e8ece8;
}
.logo-icon { width: 32px; height: 32px; }
.logo-text { font-size: 16px; font-weight: 700; color: #2e7d32; letter-spacing: 1px; white-space: nowrap; }

/* 菜单覆盖效果图样式 */
.el-menu {
  border-right: none !important;
  flex: 1;
  padding: 8px;
}
.el-menu-item {
  height: 40px !important;
  line-height: 40px !important;
  border-radius: 6px !important;
  margin: 2px 0 !important;
  color: #666 !important;
  font-size: 14px !important;
}
.el-menu-item:hover { background: #e8f5e9 !important; color: #2e7d32 !important; }
.el-menu-item.is-active { background: #2e7d32 !important; color: #fff !important; font-weight: 500 !important; }
.el-menu-item.is-active .el-icon { color: #fff !important; }

.el-sub-menu__title { height: 40px !important; line-height: 40px !important; border-radius: 6px !important; color: #666 !important; font-size: 14px !important; }
.el-sub-menu__title:hover { background: #e8f5e9 !important; }
.el-sub-menu .el-menu { padding: 0 !important; }
.el-sub-menu .el-menu .el-menu-item { padding-left: 48px !important; font-size: 13px !important; }

/* 顶部栏 */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8ece8;
  padding: 0 24px;
  height: 56px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { cursor: pointer; color: #999; }
.collapse-btn:hover { color: #2e7d32; }
.header-right { display: flex; align-items: center; }
.user-info { cursor: pointer; display: flex; align-items: center; gap: 8px; }
.user-avatar {
  width: 30px; height: 30px; border-radius: 50%;
  background: #2e7d32; color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.user-name { font-size: 14px; color: #333; }

/* 内容区 - 浅灰绿背景 */
.app-main { background: #f5f7f5; padding: 24px; overflow-y: auto; }
</style>
