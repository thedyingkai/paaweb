"use client";

import { usePathname } from 'next/navigation';
import AuthNav from './AuthNav';
import Link from 'next/link';
import { Button, Drawer, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import { DownOutlined, MenuOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { readStoredUser } from '@/utils/auth';
import TeamBrand from './TeamBrand';

interface SiteHeaderProps {
  transparent?: boolean;
  onBrandClick?: () => void;
}

const SiteHeader = ({ transparent = false, onBrandClick }: SiteHeaderProps) => {
  const pathname = usePathname();
  const [isAdmin, setIsAdmin] = useState(false);
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [profileUserId, setProfileUserId] = useState<number | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => {
    const checkAdmin = () => {
      const u = readStoredUser();
      const allowedRoles = ['supper_admin', 'super_admin', 'stu_admin', 'department', 'school_admin'];
      setIsAuthenticated(!!u);
      const userId = Number(u?.userId);
      setProfileUserId(Number.isFinite(userId) && userId > 0 ? userId : null);
      setIsAdmin(!!(u && allowedRoles.includes(u.role || '')));
    };
    checkAdmin();
    window.addEventListener('storage', checkAdmin);
    window.addEventListener('auth-state-change', checkAdmin);
    return () => {
      window.removeEventListener('storage', checkAdmin);
      window.removeEventListener('auth-state-change', checkAdmin);
    };
  }, []);

  const handleBrandClick = (e: React.MouseEvent) => {
    if (onBrandClick) {
      e.preventDefault();
      onBrandClick();
    }
  };

  const getLinkStyle = (path: string): React.CSSProperties => {
    let isActive = false;
    if (path === '/') {
      isActive = pathname === '/';
    } else {
      isActive = pathname === path || pathname.startsWith(path + '/');
    }

    if (transparent) {
      return {
        color: isActive ? '#ffffff' : 'rgba(255, 255, 255, 0.72)',
        fontWeight: isActive ? 700 : 500
      };
    } else {
      return {
        color: isActive ? '#1677ff' : '#486174',
        fontWeight: isActive ? 700 : 500
      };
    }
  };

  const getParentLinkStyle = (isActive: boolean): React.CSSProperties => {
    if (transparent) {
      return {
        color: isActive ? '#ffffff' : 'rgba(255, 255, 255, 0.72)',
        fontWeight: isActive ? 700 : 500,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px'
      };
    } else {
      return {
        color: isActive ? '#1677ff' : '#486174',
        fontWeight: isActive ? 700 : 500,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px'
      };
    }
  };

  const noticeItems: MenuProps['items'] = [
    {
      key: 'notice',
      label: <Link href="/notice" style={{ display: 'block', padding: '4px 8px' }}>通知</Link>,
    },
    {
      key: 'news',
      label: <Link href="/news" style={{ display: 'block', padding: '4px 8px' }}>新闻</Link>,
    },
    {
      key: 'publicity',
      label: <Link href="/publicity" style={{ display: 'block', padding: '4px 8px' }}>公示</Link>,
    },
  ];

  const isNoticeActive = pathname.startsWith('/notice') || pathname.startsWith('/news') || pathname.startsWith('/publicity');

  const closeMobile = () => setMobileOpen(false);

  const mobileLinks = [
    { href: '/', label: '首页' },
    ...(isAuthenticated && profileUserId
      ? [{ href: `/profile/${profileUserId}`, label: '个人主页' }]
      : []),
    { href: '/intro', label: '简介' },
    { href: '/member', label: '成员' },
    { href: '/notice', label: '通知' },
    { href: '/news', label: '新闻' },
    { href: '/publicity', label: '公示' },
    { href: '/competitions', label: '赛事与登记' },
    { href: '/team-space', label: '我的参赛档案' },
    { href: '/join-team', label: '申请加入校队' },
    { href: '/community', label: '校队交流区' },
    { href: '/training', label: '培训' },
    { href: '/certificate', label: '证书' },
    { href: '/honor', label: '荣誉墙' },
    { href: '/tracker', label: '训练记录' },
    { href: '/rank', label: '排名' },
    ...(isAdmin ? [{ href: '/admin', label: '控制面板' }] : []),
  ];

  return (
    <header className={`site-header auth-page-header ${transparent ? 'transparent-header' : ''}`}>
      <Link href="/" className="site-brand" onClick={handleBrandClick}>
        <TeamBrand priority />
      </Link>
      <div className="site-header-right">
        <nav className="site-nav-links">
          <Link href="/" style={getLinkStyle('/')}>首页</Link>
          <Link href="/intro" style={getLinkStyle('/intro')}>简介</Link>
          <Link href="/member" style={getLinkStyle('/member')}>成员</Link>
          
          <Dropdown menu={{ items: noticeItems }} trigger={['hover']}>
            <span className="site-nav-dropdown-trigger" style={getParentLinkStyle(isNoticeActive)}>
              公告 <DownOutlined style={{ fontSize: '10px' }} />
            </span>
          </Dropdown>

          <Link href="/competitions" style={getLinkStyle('/competitions')}>赛事</Link>

          <Link href="/training" style={getLinkStyle('/training')}>培训</Link>
          <Link href="/certificate" style={getLinkStyle('/certificate')}>证书</Link>
          <Link href="/honor" style={getLinkStyle('/honor')}>荣誉墙</Link>
          <Link href="/community" style={getLinkStyle('/community')}>交流区</Link>
          <Link href="/tracker" style={getLinkStyle('/tracker')}>训练记录</Link>
          <Link href="/rank" style={getLinkStyle('/rank')}>排名</Link>
          {isAdmin && <Link href="/admin" style={getLinkStyle('/admin')}>控制面板</Link>}
        </nav>
        <AuthNav />
        <Button
          className="site-mobile-menu-button header-icon-button"
          type="text"
          icon={<MenuOutlined />}
          aria-label="打开导航"
          onClick={() => setMobileOpen(true)}
        />
      </div>
      <Drawer
        className="site-mobile-drawer"
        title={<TeamBrand size={44} />}
        placement="right"
        width="min(86vw, 320px)"
        open={mobileOpen}
        onClose={closeMobile}
      >
        <nav className="site-mobile-nav">
          {mobileLinks.map((item) => (
            <Link key={item.href} href={item.href} onClick={closeMobile} className={pathname === item.href || (item.href !== '/' && pathname.startsWith(item.href)) ? 'is-active' : ''}>
              {item.label}
            </Link>
          ))}
        </nav>
        {!isAuthenticated && (
          <div className="site-mobile-account-actions">
            <Link href="/login" onClick={closeMobile}>登录</Link>
            <Link href="/register" onClick={closeMobile}>注册账号</Link>
          </div>
        )}
      </Drawer>
    </header>
  );
};

export default SiteHeader;
