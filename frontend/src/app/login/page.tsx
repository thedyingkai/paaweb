"use client";

import { Button, Card, Col, Divider, Form, Input, Row, Space, Tabs, Tag, Typography, message } from 'antd';
import { LoginOutlined, MailOutlined, SafetyCertificateOutlined, UserAddOutlined, UserOutlined, LockOutlined, StarOutlined } from '@ant-design/icons';
import axios from 'axios';
import { md5 } from 'js-md5';
import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import SiteHeader from '../components/SiteHeader';
import SliderCaptcha from '../components/SliderCaptcha';
import { persistAuth } from '@/utils/auth';

const { Title, Text, Paragraph } = Typography;

type LoginType = 'password' | 'email';

const LoginPage = () => {
  const router = useRouter();
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [loginType, setLoginType] = useState<LoginType>('password');
  const [allowRegister, setAllowRegister] = useState(true);
  const [captchaOpen, setCaptchaOpen] = useState(false);
  const formValuesRef = useRef<Record<string, string> | null>(null);

  useEffect(() => {
    document.title = "登录";
    axios.get('/api/user/register/config').then((res) => {
      if (res.data.code === 200) setAllowRegister(res.data.data);
    });
  }, []);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const handleLoginClick = async () => {
    try {
      const fields = loginType === 'password' ? ['username', 'password'] : ['email', 'code'];
      const values = await form.validateFields(fields);
      submitLogin(values);
    } catch {
      message.warning('请检查输入项是否完整');
    }
  };

  const handleSendCodeClick = async () => {
    try {
      await form.validateFields(['email']);
      formValuesRef.current = form.getFieldsValue(['email']);
      setCaptchaOpen(true);
    } catch {
      message.warning('请输入有效邮箱');
    }
  };

  const sendCode = async () => {
    const email = form.getFieldValue('email')?.toLowerCase();
    if (!email) {
      message.error('请先输入邮箱');
      return;
    }

    setSending(true);
    try {
      const res = await axios.post(`/api/user/send-code?email=${email}&type=login`);
      if (res.data.code === 200) {
        message.success('验证码已发送');
        setCountdown(60);
      } else {
        message.error(res.data.msg);
      }
    } catch {
      message.error('发送失败');
    } finally {
      setSending(false);
    }
  };

  const submitLogin = async (values: Record<string, string>) => {
    setLoading(true);
    try {
      const res = loginType === 'password'
        ? await axios.post('/api/user/login', {
          userName: values.username.includes('@') ? values.username.toLowerCase() : values.username,
          password: md5(values.password)
        })
        : await axios.post('/api/user/login-by-code', {
          userName: values.email.toLowerCase(),
          password: values.code
        });

      if (res.data.code === 200) {
        persistAuth(res.data.data);
        message.success('登录成功');
        router.push(res.data.data?.accountStatus === 'pending_activation' ? '/activation' : '/');
      } else {
        message.error(res.data.msg || '登录失败');
      }
    } catch {
      message.error('请求服务器失败');
    } finally {
      setLoading(false);
    }
  };

  const handleCaptchaSuccess = () => {
    setCaptchaOpen(false);
    sendCode();
  };

  return (
    <main className="auth-page-shell">
      <SiteHeader />
      <SliderCaptcha open={captchaOpen} onSuccess={handleCaptchaSuccess} onCancel={() => setCaptchaOpen(false)} />

      <section className="auth-hero-grid">
        <div className="auth-copy-panel">
          <Tag className="bits-pill" icon={<StarOutlined />}>Algorithm Portal</Tag>
          <Title className="bits-gradient-title">欢迎回来</Title>
          <Paragraph>
            继续查看校队要闻、培训安排、赛事报名与个人算法成长轨迹。
          </Paragraph>
          <div className="auth-metric-row">
            <div><strong>ICPC</strong><span>集训协作</span></div>
            <div><strong>Tracker</strong><span>刷题统计</span></div>
            <div><strong>Rating</strong><span>能力成长</span></div>
          </div>
        </div>

        <Card className="auth-card bits-card" bordered={false}>
          <div className="auth-card-heading">
            <Title level={2}>登录账号</Title>
            <Text>选择适合你的登录方式</Text>
          </div>

          <Tabs
            activeKey={loginType}
            onChange={(key) => setLoginType(key as LoginType)}
            items={[
              { key: 'password', label: '账号密码' },
              { key: 'email', label: '邮箱验证码' }
            ]}
            centered
          />

          <Form form={form} size="large" layout="vertical">
            {loginType === 'password' ? (
              <>
                <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
                  <Input className="bits-input" prefix={<UserOutlined />} placeholder="账号 / 邮箱" onPressEnter={handleLoginClick} />
                </Form.Item>
                <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
                  <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="请输入密码" onPressEnter={handleLoginClick} />
                </Form.Item>
              </>
            ) : (
              <>
                <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }]}>
                  <Input className="bits-input" prefix={<MailOutlined />} placeholder="请输入注册邮箱" onPressEnter={handleLoginClick} />
                </Form.Item>
                <Form.Item required label="验证码">
                  <Row gutter={10}>
                    <Col span={15}>
                      <Form.Item name="code" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                        <Input className="bits-input" prefix={<SafetyCertificateOutlined />} placeholder="6位验证码" onPressEnter={handleLoginClick} />
                      </Form.Item>
                    </Col>
                    <Col span={9}>
                      <Button className="bits-ghost-button auth-inline-button" block onClick={handleSendCodeClick} disabled={sending || countdown > 0}>
                        {countdown > 0 ? `${countdown}s` : '获取验证码'}
                      </Button>
                    </Col>
                  </Row>
                </Form.Item>
              </>
            )}

            <Button className="bits-primary-button auth-submit" block loading={loading} icon={<LoginOutlined />} onClick={handleLoginClick}>
              {loginType === 'password' ? '登录' : '验证并登录'}
            </Button>

            {allowRegister && (
              <div className="auth-switch-line">
                <Space split={<Divider type="vertical" />}>
                  <Text type="secondary">还没有账号？</Text>
                  <Button type="link" icon={<UserAddOutlined />} onClick={() => router.push('/register')}>立即注册</Button>
                </Space>
              </div>
            )}

            <div className="auth-switch-line auth-activate-line">
              <Button type="link" size="small" icon={<SafetyCertificateOutlined />} onClick={() => router.push('/activation')}>
                已有账号，请激活
              </Button>
            </div>
          </Form>
        </Card>
      </section>
    </main>
  );
};

export default LoginPage;
