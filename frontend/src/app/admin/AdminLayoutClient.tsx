"use client";

import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  DashboardOutlined,
  TrophyOutlined,
  TeamOutlined,
  SafetyCertificateOutlined,
  LogoutOutlined,
  HomeOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  GlobalOutlined,
  SolutionOutlined,
  SettingOutlined,
  ApartmentOutlined,
  BankOutlined
} from '@ant-design/icons';
import type { MenuProps } from 'antd';
import { Layout, Menu, theme, Avatar, Dropdown, Button, Result, Breadcrumb, Space } from 'antd';
import { Navigate, useLocation, useNavigate } from './_legacy/router';
import Link from 'next/link';
import { logout, readStoredUser, refreshAuth } from '@/utils/auth';
import { canManageQqBot } from '@/security/qqBotAccess';
import TeamBrand, { TeamLogo } from '../components/TeamBrand';

const { Header, Content, Footer, Sider } = Layout;

type MenuItem = Required<MenuProps>['items'][number];

const ALLOWED_ROLES = ['supper_admin', 'super_admin', 'stu_admin', 'department', 'school_admin'];

/**
 * 后台准入判断。身份状态与角色都以服务端返回并缓存到本地的快照为准，
 * 该快照会在收到 auth-state-change 推送时被 refreshAuth 覆盖。
 */
const isRoleAllowed = (user: AdminUser | null | undefined) =>
  !!user && ALLOWED_ROLES.includes(user.role || '') && user.accountStatus === 'normal';

interface AdminUser {
  userId?: number;
  role?: string;
  userAccount?: string;
  accountStatus?: string;
  permissions?: string[];
  realName?: string;
  userName?: string;
  avatarUrl?: string;
}

function getItem(
  label: React.ReactNode,
  key: React.Key,
  icon?: React.ReactNode,
  children?: MenuItem[],
): MenuItem {
  const isRoute = typeof key === 'string' && key.startsWith('/');
  const finalLabel = isRoute ? (
    <Link href={key} style={{ color: 'inherit', textDecoration: 'none', display: 'block', width: '100%' }}>
      {label}
    </Link>
  ) : label;
  return { key, icon, children, label: finalLabel } as MenuItem;
}

const AdminLayoutClient = ({ children }: { children: React.ReactNode }) => {
  const [collapsed, setCollapsed] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { token: { colorBgContainer } } = theme.useToken();
  const [user, setUser] = useState<AdminUser | null>(null);
  const [isAuthorized, setIsAuthorized] = useState<boolean | null>(null);
  const [countdown, setCountdown] = useState(3);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const updateViewport = () => setIsMobile(window.innerWidth < 992);
    updateViewport();
    window.addEventListener('resize', updateViewport);

    const storedUser = readStoredUser() as AdminUser | null;
    if (!storedUser?.userId) {
      navigate('/login');
      return;
    }
    setUser(storedUser);
    setIsAuthorized(isRoleAllowed(storedUser));

    // 复用全局鉴权同步：与 ClientRuntime 的请求合并去重，并且带 X-Auth-Sync 头，
    // 即使被限流也不会把管理员重定向到 /busy。
    void refreshAuth().then((fresh) => {
      const current = (fresh ?? readStoredUser()) as AdminUser | null;
      if (!current) {
        navigate('/login');
        return;
      }
      setUser(current);
      setIsAuthorized(isRoleAllowed(current));
    });

    return () => {
      window.removeEventListener('resize', updateViewport);
    };
  }, [navigate]);

  // 权限被实时撤销（管理员改角色/权限，或身份状态变化）时也要走同一套倒计时，
  // 否则界面会停在"将在 3 秒后返回首页"却永远不返回。
  useEffect(() => {
    if (isAuthorized !== false) {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
      return;
    }

    setCountdown(3);
    timerRef.current = setInterval(() => {
      setCountdown(prev => {
        if (prev <= 1) {
          if (timerRef.current) clearInterval(timerRef.current);
          timerRef.current = null;
          navigate('/');
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [isAuthorized, navigate]);

  useEffect(() => {
    const syncAuthorization = () => {
      const currentUser = readStoredUser() as AdminUser | null;
      setUser(currentUser);
      setIsAuthorized(currentUser ? isRoleAllowed(currentUser) : false);
    };

    window.addEventListener('auth-state-change', syncAuthorization);
    return () => window.removeEventListener('auth-state-change', syncAuthorization);
  }, []);

  const isSuperAdmin = user?.role === 'supper_admin' || user?.role === 'super_admin';
  const isSchoolAdmin = user?.role === 'school_admin';
  const isAdminUser = isSuperAdmin && user?.userAccount === 'admin';
  const permissions = useMemo(() => user?.permissions || [], [user?.permissions]);

  const hasPerm = useMemo(() => (key: string) => {
    if (isAdminUser) return true;
    return permissions.includes(key);
  }, [isAdminUser, permissions]);

  const menuItems = useMemo(() => {
    if (isSchoolAdmin) {
      return [
        getItem('学校信息', '/admin/school/profile', <BankOutlined />),
        getItem('学院组织', '/admin/school/organization', <ApartmentOutlined />),
        getItem('学生管理', '/admin/school/students', <UserOutlined />),
        getItem('队伍管理', '/admin/school/teams', <TeamOutlined />),
        getItem('报名管理', '/admin/school/signups', <SolutionOutlined />),
        getItem('返回前台', '/', <HomeOutlined />),
      ];
    }

    const items: MenuItem[] = [
      getItem('仪表盘', '/admin/dashboard', <DashboardOutlined />),
    ];

    if (hasPerm('manage_sys')) {
      items.push(getItem('内容展示', 'content-display', <GlobalOutlined />, [
        getItem('首页背景轮播图', '/admin/content/showcase-background'),
        getItem('校队荣誉墙设置', '/admin/content/honors'),
        getItem('校队简介', '/admin/content/association'),
        getItem('基本规则', '/admin/content/rules'),
        getItem('校队成员', '/admin/content/members'),
      ]));
    }

    const systemItems: MenuItem[] = [];
    if (hasPerm('manage_sys')) {
      systemItems.push(
        getItem('是否开放注册', '/admin/system/register'),
        getItem('邮箱发送设置', '/admin/system/email'),
      );
    }
    if (hasPerm('manage_api')) systemItems.push(getItem('接口管理', '/admin/app-key'));
    if (hasPerm('manage_sys')) systemItems.push(getItem('对象存储服务设置', '/admin/system/storage'));
    if (canManageQqBot(user)) systemItems.push(getItem('QQ 机器人管理', '/admin/qq-bot'));
    if (systemItems.length > 0) items.push(getItem('系统配置', 'system-config', <SettingOutlined />, systemItems));

    const contentItems: MenuItem[] = [];
    if (hasPerm('manage_notice')) contentItems.push(getItem('公告管理', '/admin/notice'));
    if (hasPerm('manage_training')) contentItems.push(getItem('培训管理', '/admin/training'));
    if (hasPerm('manage_category')) contentItems.push(getItem('赛事类别管理', '/admin/contest/category'));
    if (hasPerm('manage_contest')) contentItems.push(getItem('赛事列表', '/admin/contest'));
    if (isSuperAdmin) contentItems.push(getItem('批量邮件', '/admin/bulk-email'));
    if (contentItems.length > 0) items.push(getItem('内容发布', 'content-publish', <TrophyOutlined />, contentItems));

    const userSubItems: MenuItem[] = [
      getItem('数据面板', '/admin/data-panel'),
    ];
    if (hasPerm('manage_user')) {
      userSubItems.push(getItem('实名审核', '/admin/auth-audit'));
      userSubItems.push(getItem('用户列表', '/admin/users'));
    }
    if (hasPerm('manage_oj')) userSubItems.push(getItem('OJ账号', '/admin/oj-accounts'));
    if (hasPerm('manage_user') || hasPerm('manage_sys')) userSubItems.push(getItem('历史战绩归属', '/admin/history-awards'));
    if (hasPerm('manage_permission')) {
      userSubItems.push(getItem('管理员管理', '/admin/roles'));
      userSubItems.push(getItem('权限管理', '/admin/permissions'));
    }
    items.push(getItem('用户管理', 'user-management', <TeamOutlined />, userSubItems));

    const certSubItems: MenuItem[] = [
      getItem('证书审核', '/admin/certificate'),
      getItem('证书统计', '/admin/certificate-stats'),
    ];
    if (hasPerm('manage_cert_grant')) certSubItems.push(getItem('证书发放', '/admin/certificate-grant'));
    certSubItems.push(getItem('荣誉权重', '/admin/certificate/weights'));
    items.push(getItem('证书管理', 'certificate-management', <SafetyCertificateOutlined />, certSubItems));

    const orgItems: MenuItem[] = [];
    if (hasPerm('manage_org')) orgItems.push(getItem('架构管理', '/admin/org'));
    if (isSuperAdmin) orgItems.push(getItem('组织信息管理', '/admin/org/info'));
    if (hasPerm('manage_term')) orgItems.push(getItem('学期设置', '/admin/term'));
    if (orgItems.length > 0) items.push(getItem('组织管理', 'organization-management', <ApartmentOutlined />, orgItems));

    items.push(getItem('返回前台', '/', <HomeOutlined />));
    return items;
  }, [hasPerm, isSchoolAdmin, isSuperAdmin, user]);

  const handleLogout = () => {
    // 必须同时注销服务端会话：只清 localStorage 会让 HttpSession 继续有效，
    // 换账号登录时可能仍带着上一个账号的身份和权限。
    void logout().finally(() => navigate('/'));
  };

  const checkPagePermission = (path: string) => {
    if (path === '/admin/qq-bot' || path.startsWith('/admin/qq-bot/')) return canManageQqBot(user);
    if (isSchoolAdmin) {
      if (path === '/admin' || path === '/admin/') return true;
      return path.startsWith('/admin/school/');
    }
    if (isAdminUser) return true;
    if (path === '/admin' || path === '/admin/') return true;
    if (path.startsWith('/admin/dashboard') || path.startsWith('/admin/data-panel')) return true;
    if (path.startsWith('/admin/auth-audit')) return hasPerm('manage_user');
    if (path.startsWith('/admin/history-awards')) return hasPerm('manage_user') || hasPerm('manage_sys');
    if (path.startsWith('/admin/certificate') && !path.startsWith('/admin/certificate-grant') && !path.startsWith('/admin/certificate-stats') && !path.startsWith('/admin/certificate/weights')) return true;
    if (path.startsWith('/admin/certificate-stats')) return true;
    if (path.startsWith('/admin/certificate/weights')) return true;
    if (path.startsWith('/admin/intro') || path.startsWith('/admin/content/') || path.startsWith('/admin/system/')) return hasPerm('manage_sys');
    if (path.startsWith('/admin/notice')) return hasPerm('manage_notice');
    if (path.startsWith('/admin/training')) return hasPerm('manage_training');
    if (path.startsWith('/admin/contest') && !path.includes('/category')) return hasPerm('manage_contest');
    if (path.includes('/admin/contest/category')) return hasPerm('manage_category');
    if (path.startsWith('/admin/users')) return hasPerm('manage_user');
    if (path.startsWith('/admin/oj-accounts')) return hasPerm('manage_oj');
    if (path.startsWith('/admin/app-key')) return hasPerm('manage_api');
    if (path.startsWith('/admin/bulk-email')) return isSuperAdmin;
    if (path.startsWith('/admin/certificate-grant')) return hasPerm('manage_cert_grant');
    if (path.startsWith('/admin/org/info')) return isSuperAdmin;
    if (path.startsWith('/admin/org')) return hasPerm('manage_org');
    if (path.startsWith('/admin/term')) return hasPerm('manage_term');
    if (path.startsWith('/admin/permissions')) return hasPerm('manage_permission');
    if (path.startsWith('/admin/roles')) return hasPerm('manage_permission');
    return false;
  };

  if (isAuthorized === null) return null;

  if (!isAuthorized) {
    return (
      <Result
        status="403"
        title="403 Forbidden"
        subTitle={`您没有权限访问管理后台，将在 ${countdown} 秒后返回首页。`}
        extra={<Button type="primary" onClick={() => navigate('/')}>立即返回</Button>}
      />
    );
  }

  if (!checkPagePermission(location.pathname)) {
    if (isSchoolAdmin && (location.pathname === '/admin' || location.pathname === '/admin/' || location.pathname.startsWith('/admin/dashboard'))) {
      return <Navigate to="/admin/school/students" replace />;
    }
    if (location.pathname === '/admin' || location.pathname === '/admin/') {
      return <Navigate to="/admin/dashboard" replace />;
    }
    return (
      <Result
        status="403"
        title="403 Forbidden"
        subTitle="对不起，您没有权限访问此页面。"
        extra={<Button type="primary" onClick={() => navigate('/admin/dashboard')}>返回仪表盘</Button>}
      />
    );
  }

  const userMenu: MenuProps['items'] = [
    { key: 'home', label: '返回前台首页', icon: <HomeOutlined />, onClick: () => navigate('/') },
    { type: 'divider' },
    { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, onClick: handleLogout }
  ];

  const breadcrumbNameMap: Record<string, string> = {
    '/admin': '首页',
    '/admin/dashboard': '仪表盘',
    '/admin/data-panel': '数据面板',
    '/admin/intro': '内容展示',
    '/admin/content': '内容展示',
    '/admin/content/showcase-background': '首页背景轮播图',
    '/admin/content/honors': '校队荣誉墙设置',
    '/admin/content/association': '校队简介',
    '/admin/content/rules': '基本规则',
    '/admin/content/members': '校队成员',
    '/admin/system': '系统配置',
    '/admin/system/register': '是否开放注册',
    '/admin/system/email': '邮箱发送设置',
    '/admin/system/storage': '对象存储服务设置',
    '/admin/qq-bot': 'QQ 机器人管理',
    '/admin/notice': '公告管理',
    '/admin/training': '培训发布',
    '/admin/contest': '赛事列表',
    '/admin/contest/category': '赛事类别',
    '/admin/certificate': '证书审核',
    '/admin/certificate/weights': '荣誉权重',
    '/admin/certificate-grant': '发放记录',
    '/admin/certificate-stats': '证书统计',
    '/admin/users': '用户列表',
    '/admin/oj-accounts': 'OJ账号管理',
    '/admin/auth-audit': '实名认证',
    '/admin/org': '学院管理',
    '/admin/org/info': '组织信息管理',
    '/admin/term': '学期设置',
    '/admin/history-awards': '历史战绩归属',
    '/admin/app-key': '接口授权管理',
    '/admin/bulk-email': '批量邮件',
    '/admin/permissions': '权限管理',
    '/admin/roles': '管理员管理',
    '/admin/school': '学校报名管理',
    '/admin/school/profile': '学校信息',
    '/admin/school/organization': '学院组织',
    '/admin/school/students': '学生管理',
    '/admin/school/teams': '队伍管理',
    '/admin/school/signups': '报名管理',
    '/': '返回前台',
  };
  const getSelectedKeys = () => {
    const pathname = location.pathname;
    if (pathname.startsWith('/admin/data-panel')) return ['/admin/data-panel'];
    if (pathname.startsWith('/admin/notice/')) return ['/admin/notice'];
    if (pathname.startsWith('/admin/training/')) return ['/admin/training'];
    if (pathname.startsWith('/admin/school/signups')) return ['/admin/school/signups'];
    if (pathname.startsWith('/admin/school/teams')) return ['/admin/school/teams'];
    if (pathname.startsWith('/admin/school/students')) return ['/admin/school/students'];
    if (pathname.startsWith('/admin/school/profile')) return ['/admin/school/profile'];
    if (pathname.startsWith('/admin/school/organization')) return ['/admin/school/organization'];
    if (pathname.startsWith('/admin/contest/category')) return ['/admin/contest/category'];
    if (pathname.startsWith('/admin/contest/')) return ['/admin/contest'];
    if (pathname.startsWith('/admin/content/showcase-background')) return ['/admin/content/showcase-background'];
    if (pathname.startsWith('/admin/content/honors')) return ['/admin/content/honors'];
    if (pathname.startsWith('/admin/content/association')) return ['/admin/content/association'];
    if (pathname.startsWith('/admin/content/rules')) return ['/admin/content/rules'];
    if (pathname.startsWith('/admin/content/members')) return ['/admin/content/members'];
    return [pathname];
  };

  const selectedRoute = getSelectedKeys()[0];
  const logicalSection = (() => {
    if (selectedRoute.startsWith('/admin/content/')) return { label: '内容展示', href: selectedRoute };
    if (selectedRoute.startsWith('/admin/system/') || selectedRoute === '/admin/app-key' || selectedRoute === '/admin/qq-bot') return { label: '系统配置', href: selectedRoute };
    if (['/admin/notice', '/admin/training', '/admin/contest/category', '/admin/contest', '/admin/bulk-email'].includes(selectedRoute)) return { label: '内容发布', href: selectedRoute };
    if (['/admin/data-panel', '/admin/auth-audit', '/admin/users', '/admin/oj-accounts', '/admin/roles', '/admin/permissions'].includes(selectedRoute)) return { label: '用户管理', href: selectedRoute };
    if (selectedRoute.startsWith('/admin/certificate')) return { label: '证书管理', href: selectedRoute };
    if (['/admin/org', '/admin/org/info', '/admin/term'].includes(selectedRoute)) return { label: '组织管理', href: selectedRoute };
    return null;
  })();
  const actionLabel = location.pathname.includes('/edit/') ? '编辑'
    : location.pathname.endsWith('/publish') ? '发布'
    : location.pathname.includes('/signups/') ? '报名管理'
    : null;
  const breadcrumbItems = [
    { title: <Link href="/admin/dashboard">首页</Link> },
    ...(logicalSection ? [{ title: <Link href={logicalSection.href}>{logicalSection.label}</Link> }] : []),
    { title: breadcrumbNameMap[selectedRoute] || selectedRoute.split('/').pop() },
    ...(actionLabel ? [{ title: actionLabel }] : []),
  ];

  return (
    <Layout className="admin-shell">
      {isMobile && !collapsed && <div className="admin-mobile-mask" onClick={() => setCollapsed(true)} />}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        onCollapse={(value) => setCollapsed(value)}
        breakpoint="lg"
        collapsedWidth="0"
        zeroWidthTriggerStyle={{ display: 'none' }}
        onBreakpoint={(broken) => {
          if (broken) setCollapsed(true);
        }}
        style={{ height: '100vh', position: 'fixed', top: 0, left: 0, overflow: 'hidden', zIndex: 200 }}
      >
        <style>{`.ant-layout-sider-children::-webkit-scrollbar{display:none;}`}</style>
        <Link href="/admin/dashboard" className="admin-team-brand" aria-label="BESTI ACM 管理中心">
          <TeamBrand size={40} caption="管理中心" />
        </Link>
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto', scrollbarWidth: 'none', msOverflowStyle: 'none' }}>
          <Menu 
            theme="dark" 
            defaultSelectedKeys={getSelectedKeys()} 
            mode="inline" 
            items={menuItems} 
            onClick={(e) => {
              if (e.domEvent && (e.domEvent.ctrlKey || e.domEvent.metaKey)) return;
              navigate(e.key);
              if (isMobile) setCollapsed(true);
            }} 
            selectedKeys={getSelectedKeys()} 
          />
        </div>
      </Sider>
      <Layout
        className="admin-main-layout"
        style={{
          marginLeft: isMobile || collapsed ? 0 : 200,
          minHeight: '100vh',
          transition: 'margin-left 0.2s ease',
        }}
      >
        <Header style={{ position: 'sticky', top: 0, zIndex: 100, padding: '0 24px', background: colorBgContainer, display: 'flex', justifyContent: 'space-between', alignItems: 'center', boxShadow: '0 1px 4px rgba(0,21,41,0.08)' }}>
          <div style={{ display: 'flex', alignItems: 'center' }}>
            <Button
              type="text"
              icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
              onClick={() => setCollapsed(!collapsed)}
              style={{ fontSize: '16px', width: 64, height: 64, marginLeft: -24 }}
            />
            <div className="admin-header-title" style={{ display: 'flex', alignItems: 'center', gap: 10, fontSize: 18, fontWeight: 500, marginLeft: 8 }}>
              <TeamLogo size={36} />
              <span>管理控制台</span>
            </div>
          </div>
          <Dropdown menu={{ items: userMenu }} placement="bottomRight" arrow>
            <Space className="admin-user-chip" style={{ cursor: 'pointer' }}>
              <Avatar style={{ backgroundColor: '#1890ff' }} icon={<UserOutlined />} src={user?.avatarUrl} />
              <span style={{ fontSize: 14 }}>{user?.realName || user?.userName}</span>
              <span style={{ fontSize: 12, color: '#999' }}>{isSuperAdmin ? '超级管理员' : user?.role === 'school_admin' ? '学校管理员' : '管理员'}</span>
            </Space>
          </Dropdown>
        </Header>
        <Content className="admin-content-shell" style={{ margin: '16px 16px' }}>
          <Breadcrumb style={{ margin: '0 0 16px 0' }} items={[{ title: <Link href="/admin">Admin</Link> }, ...breadcrumbItems]} />
          <div className="admin-content-panel" style={{ padding: 24, minHeight: 360, background: colorBgContainer, borderRadius: 8 }}>
            {children}
          </div>
        </Content>
        <Footer className="admin-footer" style={{ textAlign: 'center', color: 'rgba(0, 0, 0, 0.45)', fontSize: 12, lineHeight: '1.5', padding: '12px 0' }}>
          <span>北京电子科技学院 ACM 校队 · 管理中心</span>
        </Footer>
      </Layout>
    </Layout>
  );
};

export default AdminLayoutClient;
