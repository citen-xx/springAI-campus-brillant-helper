import Vue from 'vue'
import Router from 'vue-router'

Vue.use(Router)

/* Layout */
import Layout from '@/layout'

// 公共路由
export const constantRoutes = [
  {
    path: '/redirect',
    component: Layout,
    hidden: true,
    children: [
      {
        path: '/redirect/:path(.*)',
        component: () => import('@/views/redirect')
      }
    ]
  },
  {
    path: '/login',
    component: () => import('@/views/login'),
    hidden: true
  },
  {
    path: '/register',
    component: () => import('@/views/register'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error/404'),
    hidden: true
  },
  {
    path: '/401',
    component: () => import('@/views/error/401'),
    hidden: true
  },
  {
    path: '',
    component: Layout,
    redirect: 'index',
    children: [
      {
        path: 'index',
        component: () => import('@/views/index'),
        name: 'Index',
        meta: { title: '首页', icon: 'dashboard', affix: true }
      }
    ]
  },
  {
    path: '/ai',
    component: Layout,
    redirect: '/ai/workbench',
    alwaysShow: true,
    children: [
      {
        path: 'workbench',
        component: () => import('@/views/ai/workbench'),
        name: 'AiWorkbench',
        meta: { title: 'AI工作台', icon: 'dashboard' }
      },
      {
        path: 'chat',
        component: () => import('@/views/ai/chat'),
        name: 'AiChat',
        meta: { title: '智能问答', icon: 'message' }
      },
      {
        path: 'knowledge',
        component: () => import('@/views/system/knowledge/index'),
        name: 'AiKnowledge',
        meta: { title: '知识文档', icon: 'documentation' }
      },
      {
        path: 'qa',
        component: () => import('@/views/system/qa/index'),
        name: 'AiQa',
        meta: { title: '问答库', icon: 'dict' }
      },
      {
        path: 'student',
        component: () => import('@/views/system/student/index'),
        name: 'AiStudent',
        meta: { title: '学生管理', icon: 'user' }
      },
      {
        path: 'edu-tools',
        component: () => import('@/views/ai/edu-tools'),
        name: 'EduTools',
        meta: { title: '教务工具', icon: 'education' }
      }
    ]
  },
  {
    path: '/lock',
    component: () => import('@/views/lock'),
    hidden: true,
    meta: { title: '锁定屏幕' }
  },
  {
    path: '/user',
    component: Layout,
    hidden: true,
    redirect: 'noredirect',
    children: [
      {
        path: 'profile',
        component: () => import('@/views/system/user/profile/index'),
        name: 'Profile',
        meta: { title: '个人中心', icon: 'user' }
      }
    ]
  }
]

// 动态路由，基于用户权限动态去加载
export const dynamicRoutes = [
  {
    path: '/system/user-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:user:edit'],
    children: [
      {
        path: 'role/:userId(\\d+)',
        component: () => import('@/views/system/user/authRole'),
        name: 'AuthRole',
        meta: { title: '分配角色', activeMenu: '/system/user' }
      }
    ]
  },
  {
    path: '/system/role-auth',
    component: Layout,
    hidden: true,
    permissions: ['system:role:edit'],
    children: [
      {
        path: 'user/:roleId(\\d+)',
        component: () => import('@/views/system/role/authUser'),
        name: 'AuthUser',
        meta: { title: '分配用户', activeMenu: '/system/role' }
      }
    ]
  },
  {
    path: '/system/dict-data',
    component: Layout,
    hidden: true,
    permissions: ['system:dict:list'],
    children: [
      {
        path: 'index/:dictId(\\d+)',
        component: () => import('@/views/system/dict/data'),
        name: 'Data',
        meta: { title: '字典数据', activeMenu: '/system/dict' }
      }
    ]
  }
]

// 防止连续点击多次路由报错
const routerPush = Router.prototype.push
const routerReplace = Router.prototype.replace

Router.prototype.push = function push(location) {
  return routerPush.call(this, location).catch(err => err)
}

Router.prototype.replace = function replace(location) {
  return routerReplace.call(this, location).catch(err => err)
}

export default new Router({
  mode: 'history',
  scrollBehavior: () => ({ y: 0 }),
  routes: constantRoutes
})
