"use client";

import React, { useEffect, useState } from 'react';
import { Alert, Button, Card, Checkbox, Col, Form, Input, InputNumber, Row, Select, Space, Switch, message } from 'antd';
import { ApiOutlined, SaveOutlined } from '@ant-design/icons';
import axios from 'axios';

type SettingsMode = 'smtp' | 'storage';

const defaults = {
  smtp: {
    enabled: false,
    host: '',
    port: 465,
    username: '',
    password: '',
    protocol: 'smtps',
    auth: true,
    sslEnabled: true,
    startTlsEnabled: false,
    fromName: 'ACM 校队',
  },
  storage: {
    enabled: false,
    endpoint: '',
    region: 'us-east-1',
    bucket: '',
    accessKey: '',
    secretKey: '',
    prefix: 'paa',
    pathStyleAccess: true,
  },
};

export default function SystemServiceSettings({ mode }: { mode: SettingsMode }) {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const enabled = Form.useWatch('enabled', form);
  const title = mode === 'smtp' ? '邮箱发送设置（SMTP 服务）' : '对象存储服务设置';

  useEffect(() => {
    axios.get(`/api/admin/system-config/${mode}`)
      .then(res => {
        if (res.data.code === 200) {
          form.setFieldsValue({ ...defaults[mode], ...res.data.data, password: '', secretKey: '' });
        } else {
          message.error(res.data.msg || '读取配置失败');
        }
      })
      .catch(() => message.error('读取配置失败'))
      .finally(() => setLoading(false));
  }, [form, mode]);

  const save = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      const res = await axios.put(`/api/admin/system-config/${mode}`, values);
      if (res.data.code === 200) {
        message.success('配置已保存并生效');
        form.setFieldValue(mode === 'smtp' ? 'password' : 'secretKey', '');
      } else {
        message.error(res.data.msg || '保存失败');
      }
    } finally {
      setSaving(false);
    }
  };

  const testConnection = async () => {
    try {
      const values = await form.validateFields();
      setTesting(true);
      const res = await axios.post(`/api/admin/system-config/${mode}/test`, values);
      if (res.data.code === 200) message.success(res.data.data || '连接测试成功');
      else message.error(res.data.msg || '连接测试失败');
    } finally {
      setTesting(false);
    }
  };

  return (
    <Card
      className="admin-sticky-save-card"
      title={title}
      loading={loading}
      extra={
        <Space>
          <Button icon={<ApiOutlined />} loading={testing} onClick={testConnection}>测试连接</Button>
          <Button type="primary" icon={<SaveOutlined />} loading={saving} onClick={save}>保存配置</Button>
        </Space>
      }
    >
      <Alert
        showIcon
        type={mode === 'smtp' ? 'info' : 'warning'}
        message={mode === 'smtp'
          ? '启用后，验证码、审核通知和批量邮件都会使用这里的 SMTP 配置；关闭时继续使用服务器环境变量中的邮件配置。'
          : '启用后新上传文件写入 S3 兼容对象存储；历史本地文件仍从本地磁盘读取，两种存储可同时正常显示。'}
        style={{ marginBottom: 24 }}
      />

      <Form form={form} layout="vertical" initialValues={defaults[mode]}>
        <Form.Item name="enabled" label="启用此服务" valuePropName="checked">
          <Switch checkedChildren="已启用" unCheckedChildren="未启用" />
        </Form.Item>

        {mode === 'smtp' ? (
          <>
            <Row gutter={16}>
              <Col xs={24} md={16}>
                <Form.Item name="host" label="SMTP 服务器" rules={[{ required: enabled, message: '请输入 SMTP 服务器' }]}>
                  <Input placeholder="例如 smtp.163.com" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="port" label="端口" rules={[{ required: enabled, message: '请输入端口' }]}>
                  <InputNumber min={1} max={65535} style={{ width: '100%' }} />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="username" label="SMTP 用户名" rules={[{ required: enabled, message: '请输入 SMTP 用户名' }]}>
                  <Input autoComplete="off" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="password" label="SMTP 密码 / 授权码" extra="留空表示继续使用已保存的密码">
                  <Input.Password autoComplete="new-password" placeholder="已配置时可留空" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col xs={24} md={8}>
                <Form.Item name="protocol" label="协议">
                  <Select options={[{ value: 'smtp', label: 'SMTP' }, { value: 'smtps', label: 'SMTPS' }]} />
                </Form.Item>
              </Col>
              <Col xs={24} md={16}>
                <Form.Item name="fromName" label="发件人名称">
                  <Input />
                </Form.Item>
              </Col>
            </Row>
            <Space size="large" wrap>
              <Form.Item name="auth" valuePropName="checked"><Checkbox>启用身份认证</Checkbox></Form.Item>
              <Form.Item name="sslEnabled" valuePropName="checked"><Checkbox>启用 SSL</Checkbox></Form.Item>
              <Form.Item name="startTlsEnabled" valuePropName="checked"><Checkbox>启用 STARTTLS</Checkbox></Form.Item>
            </Space>
          </>
        ) : (
          <>
            <Form.Item name="endpoint" label="S3 服务地址" rules={[{ required: enabled, message: '请输入对象存储服务地址' }]} extra="AWS S3、MinIO 或其他兼容 S3 API 的对象存储地址，例如 https://s3.example.com">
              <Input placeholder="https://s3.example.com" />
            </Form.Item>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="region" label="区域（Region）" rules={[{ required: enabled, message: '请输入区域' }]}>
                  <Input placeholder="us-east-1" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="bucket" label="存储桶（Bucket）" rules={[{ required: enabled, message: '请输入存储桶名称' }]}>
                  <Input />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col xs={24} md={12}>
                <Form.Item name="accessKey" label="Access Key" rules={[{ required: enabled, message: '请输入 Access Key' }]}>
                  <Input autoComplete="off" />
                </Form.Item>
              </Col>
              <Col xs={24} md={12}>
                <Form.Item name="secretKey" label="Secret Key" extra="留空表示继续使用已保存的密钥">
                  <Input.Password autoComplete="new-password" placeholder="已配置时可留空" />
                </Form.Item>
              </Col>
            </Row>
            <Row gutter={16}>
              <Col xs={24} md={16}>
                <Form.Item name="prefix" label="对象路径前缀" extra="用于区分同一 Bucket 中的不同应用，可留空">
                  <Input placeholder="paa" />
                </Form.Item>
              </Col>
              <Col xs={24} md={8}>
                <Form.Item name="pathStyleAccess" label="访问方式" valuePropName="checked">
                  <Switch checkedChildren="Path Style" unCheckedChildren="Virtual Host" />
                </Form.Item>
              </Col>
            </Row>
          </>
        )}
      </Form>
    </Card>
  );
}
