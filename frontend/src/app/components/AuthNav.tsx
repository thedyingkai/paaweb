"use client";

import { Avatar, Button, Dropdown, Space, Tooltip } from 'antd';
import type { MenuProps } from 'antd';
import { LoginOutlined, LogoutOutlined, SearchOutlined, UserAddOutlined, UserOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { logout, readStoredUser } from '@/utils/auth';

import NotificationCenter from './NotificationCenter';

interface UserInfo {
  userId?: number;
  userName?: string;
  userAccount?: string;
  realName?: string;
  avatarUrl?: string;
}

const AuthNav = () => {
  const router = useRouter();
  const [user, setUser] = useState<UserInfo | null>(null);

  useEffect(() => {
    const readUser = () => {
      setUser(readStoredUser());
    };
    readUser();
    window.addEventListener('storage', readUser);
    window.addEventListener('auth-state-change', readUser);
    return () => {
      window.removeEventListener('storage', readUser);
      window.removeEventListener('auth-state-change', readUser);
    };
  }, []);

  if (!user) {
    return (
      <Space className="auth-nav" size={10}>
        <Tooltip title="全站搜索">
          <Button
            className="header-icon-button"
            shape="circle"
            icon={<SearchOutlined />}
            aria-label="全站搜索"
            onClick={() => router.push('/search')}
          />
        </Tooltip>
        <Button className="bits-ghost-button" icon={<LoginOutlined />} onClick={() => router.push('/login')}>
          登录
        </Button>
        <Button className="bits-primary-button" icon={<UserAddOutlined />} onClick={() => router.push('/register')}>
          注册
        </Button>
      </Space>
    );
  }

  const name = user.userName || user.realName || user.userAccount || '用户';
  const items: MenuProps['items'] = [
    { key: 'team-space', label: '维护我的奖项与职务', onClick: () => router.push('/team-space') },
    { key: 'community', label: '校队内部交流区', onClick: () => router.push('/community') },
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: '进入个人主页',
      onClick: () => router.push(`/profile/${user.userId || ''}`)
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      danger: true,
      label: '退出登录',
      onClick: () => {
        setUser(null);
        // 退出登录必须同时让服务端会话失效，否则会话仍然有效可被复用。
        void logout().finally(() => router.push('/'));
      }
    }
  ];

  return (
    <Space className="auth-nav" size={18} align="center">
      <Tooltip title="全站搜索">
        <Button
          className="header-icon-button"
          shape="circle"
          icon={<SearchOutlined />}
          aria-label="全站搜索"
          onClick={() => router.push('/search')}
        />
      </Tooltip>
      <NotificationCenter />
      <Dropdown menu={{ items }} trigger={['click']} placement="bottomRight">
        <button className="auth-user-trigger" type="button">
          <Avatar src={user.avatarUrl} icon={<UserOutlined />} size={34} />
          <span title={name}>{name}</span>
        </button>
      </Dropdown>
    </Space>
  );
};

export default AuthNav;
