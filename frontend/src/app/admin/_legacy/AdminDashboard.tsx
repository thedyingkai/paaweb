"use client";

import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Statistic, Spin, Typography } from 'antd';
import { 
  UserOutlined, 
  AuditOutlined, 
  TrophyOutlined, 
  FileTextOutlined, 
  SmileOutlined, 
  CheckCircleTwoTone, 
  ClockCircleTwoTone 
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import { useNavigate } from '@/app/admin/_legacy/router';

const { Title, Text } = Typography;

const AdminDashboard: React.FC = () => {
  const getStoredUser = () => {
    if (typeof window === 'undefined') return null;
    const userInfo = window.localStorage.getItem('user_info');
    return userInfo ? JSON.parse(userInfo) : null;
  };

  const [stats, setStats] = useState({
    pendingUsers: 0,
    ongoingEvents: 0,
    pendingCerts: 0,
    newNotices: 0
  });
  const [loading, setLoading] = useState(true);
  const [user] = useState<{ realName?: string; userName?: string; role?: string } | null>(getStoredUser);
  const [systemTime, setSystemTime] = useState(() => dayjs());
  const navigate = useNavigate();

  const getGreeting = () => {
    const hour = new Date().getHours();
    if (hour < 6) return '夜深了';
    if (hour < 12) return '早上好';
    if (hour < 14) return '中午好';
    if (hour < 18) return '下午好';
    return '晚上好';
  };

  useEffect(() => {
    const fetchStats = async () => {
      try {
        const res = await axios.get('/api/admin/dashboard/stats');
        if (res.data.code === 200) {
          setStats(res.data.data);
        }
      } catch (error) {
        console.error(error);
      } finally {
        setLoading(false);
      }
    };

    fetchStats();
  }, []);

  useEffect(() => {
    const timer = window.setInterval(() => {
      setSystemTime(dayjs());
    }, 1000);

    return () => window.clearInterval(timer);
  }, []);

  const isSuperAdmin = user?.role === 'supper_admin' || user?.role === 'super_admin';
  
  return (
    <div>
      <div style={{ marginBottom: 24, background: '#fff', padding: '20px 24px', borderRadius: 8 }}>
        <Title level={3} style={{ marginBottom: 8, color: '#1f1f1f' }}>
            {getGreeting()}，{user?.realName || user?.userName || '管理员'}，祝你今天过得开心！
        </Title>
        <Text type="secondary" style={{ fontSize: '14px' }}>
           <SmileOutlined style={{ marginRight: 8, color: '#faad14' }} />
           “代码构建世界，算法连接未来。每一行代码都是通往卓越的阶梯。”
        </Text>
      </div>

      <Spin spinning={loading}>
        <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} md={6}>
            <Card 
                bordered={false} 
                hoverable 
                style={{ borderRadius: 8, cursor: 'pointer' }}
                onClick={() => navigate('/admin/auth-audit')}
            >
                <Statistic 
                    title="待审核实名认证" 
                    value={stats.pendingUsers} 
                    prefix={<UserOutlined />} 
                    valueStyle={{ color: stats.pendingUsers > 0 ? '#cf1322' : '#3f8600' }} 
                    suffix="人"
                />
            </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
            <Card 
                bordered={false} 
                hoverable 
                style={{ borderRadius: 8, cursor: 'pointer' }}
                onClick={() => isSuperAdmin && navigate('/admin/contest')}
            >
                <Statistic 
                    title="已发布的赛事" 
                    value={stats.ongoingEvents} 
                    prefix={<TrophyOutlined />} 
                    valueStyle={{ color: '#1890ff' }} 
                    suffix="场"
                />
            </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
            <Card 
                bordered={false} 
                hoverable 
                style={{ borderRadius: 8, cursor: 'pointer' }}
                onClick={() => navigate('/admin/certificate')}
            >
                <Statistic 
                    title="待审核证书申请" 
                    value={stats.pendingCerts} 
                    prefix={<AuditOutlined />} 
                    valueStyle={{ color: stats.pendingCerts > 0 ? '#faad14' : '#3f8600' }} 
                    suffix="个"
                />
            </Card>
            </Col>
            <Col xs={24} sm={12} md={6}>
            <Card 
                bordered={false} 
                hoverable 
                style={{ borderRadius: 8, cursor: 'pointer' }}
                onClick={() => isSuperAdmin && navigate('/admin/notice')}
            >
                <Statistic 
                    title="本月新增公告" 
                    value={stats.newNotices} 
                    prefix={<FileTextOutlined />} 
                    valueStyle={{ color: '#722ed1' }} 
                    suffix="条"
                />
            </Card>
            </Col>
        </Row>
      </Spin>
      
      <Card title="系统状态" bordered={false} style={{ marginTop: 24, borderRadius: 8 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '40px' }}>
             <div>
                <span style={{ color: '#999', marginRight: 8 }}>当前系统版本：</span> 
                <Text strong>2.0.0</Text>
             </div>
             <div>
                <span style={{ color: '#999', marginRight: 8 }}>服务器状态：</span> 
                <Text type="success"><CheckCircleTwoTone twoToneColor="#52c41a" /> 正常运行</Text>
             </div>
             <div>
                <span style={{ color: '#999', marginRight: 8 }}>数据库连接：</span> 
                <Text type="success"><CheckCircleTwoTone twoToneColor="#52c41a" /> 正常</Text>
             </div>
             <div>
                <span style={{ color: '#999', marginRight: 8 }}>系统时间：</span> 
                <Text><ClockCircleTwoTone /> {systemTime.format('YYYY-MM-DD HH:mm:ss')}</Text>
             </div>
          </div>
      </Card>
    </div>
  );
};

export default AdminDashboard;
