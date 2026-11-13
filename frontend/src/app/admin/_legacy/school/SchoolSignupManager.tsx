"use client";

/* eslint-disable @typescript-eslint/no-explicit-any, react-hooks/set-state-in-effect */

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Form, Input, Modal, Popconfirm, Radio, Select, Space, Table, Tag, message } from 'antd';
import { ArrowLeftOutlined, ReloadOutlined, SearchOutlined, SendOutlined, StopOutlined, SyncOutlined } from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';
import AdvisorInput, { advisorText } from '@/app/components/AdvisorInput';
import { useNavigate, useParams } from '@/app/admin/_legacy/router';
import { signupGroupText, signupSourceText, signupStatusText } from '@/utils/contestDisplay';

const { Option } = Select;

const statusMap: Record<string, { text: string; color: string }> = {
  approved: { text: '已通过', color: 'green' },
  pending: { text: '待审核', color: 'gold' },
  rejected: { text: '已拒绝', color: 'red' },
  cancelled: { text: '已取消', color: 'default' }
};

const SchoolSignupManager: React.FC = () => {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const [eventInfo, setEventInfo] = useState<any>({});
  const [list, setList] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [keyword, setKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [teamTypeFilter, setTeamTypeFilter] = useState('all');
  const [viewType, setViewType] = useState('summary');
  const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);
  const [signupOpen, setSignupOpen] = useState(false);
  const [studentOptions, setStudentOptions] = useState<any[]>([]);
  const [teamOptions, setTeamOptions] = useState<any[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [now, setNow] = useState(() => dayjs());
  const canCancel = !!eventInfo.eventId && eventInfo.signupMode !== 'roster' && (!eventInfo.signupEnd || now.isBefore(eventInfo.signupEnd));
  const signupEnded = !!eventInfo.signupEnd && !now.isBefore(eventInfo.signupEnd);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(dayjs()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  const fetchEvent = async () => {
    const res = await axios.get(`/api/event/detail/${eventId}`);
    if (res.data.code === 200) setEventInfo(res.data.data || {});
  };

  const fetchSignups = async (current = pagination.current, size = pagination.pageSize) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/admin/school-signup/signups', {
        params: { eventId, current, size, keyword, status: statusFilter, teamType: teamTypeFilter, viewType }
      });
      if (res.data.code === 200) {
        setList(res.data.data.records || []);
        setPagination({ current: res.data.data.current, pageSize: res.data.data.size, total: res.data.data.total });
        setSelectedRowKeys([]);
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvent();
  }, [eventId]);

  useEffect(() => {
    if (eventId) fetchSignups(1, pagination.pageSize);
  }, [eventId, statusFilter, teamTypeFilter, viewType]);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (eventId) fetchSignups(1, pagination.pageSize);
    }, 400);
    return () => clearTimeout(timer);
  }, [keyword]);

  const refreshAll = async () => {
    await fetchEvent();
    await fetchSignups();
  };

  const searchStudents = async (value: string) => {
    if (!value) return;
    const res = await axios.get('/api/admin/school-signup/students/search', { params: { keyword: value } });
    if (res.data.code === 200) setStudentOptions(res.data.data || []);
  };

  const fetchTeams = async () => {
    const res = await axios.get('/api/admin/school-signup/teams');
    if (res.data.code === 200) setTeamOptions(res.data.data || []);
  };

  const openSignup = async () => {
    form.resetFields();
    form.setFieldsValue({ isRanking: 1, teamType: eventInfo.type === 'GPLT' ? undefined : eventInfo.type === 'LANQIAO' ? undefined : 'official' });
    if (eventInfo.isTeam === 1) await fetchTeams();
    setSignupOpen(true);
  };

  const submitSignup = async () => {
    const values = await form.validateFields();
    setSubmitting(true);
    try {
      const payload = {
        eventId: Number(eventId),
        userIds: values.userIds,
        teamIds: values.teamIds,
        teamType: values.teamType,
        isRanking: values.isRanking,
        advisor: advisorText(values.advisor)
      };
      const res = await axios.post('/api/admin/school-signup/signup', payload);
      if (res.data.code === 200) {
        const data = res.data.data || {};
        message.success(`提交完成：成功 ${data.success || 0} 条，失败 ${data.failure || 0} 条`);
        if (data.failedList?.length) {
          Modal.warning({ title: '部分报名失败', content: <pre style={{ whiteSpace: 'pre-wrap' }}>{data.failedList.join('\n')}</pre> });
        }
        setSignupOpen(false);
        refreshAll();
      } else {
        message.error(res.data.msg || '提交失败');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleBatchAction = async (action: 'cancel' | 'reapply', ids?: React.Key[]) => {
    if (action === 'cancel' && eventInfo.signupEnd && !dayjs().isBefore(eventInfo.signupEnd)) {
      setNow(dayjs());
      return message.warning('报名已结束，无法取消报名');
    }
    const targetIds = (ids || selectedRowKeys).map(Number);
    if (!targetIds.length) return message.warning('请先选择报名记录');
    const url = action === 'cancel' ? '/api/admin/school-signup/cancel' : '/api/admin/school-signup/reapply';
    const res = await axios.post(url, { eventId: Number(eventId), signupIds: targetIds });
    if (res.data.code === 200) {
      if (action === 'reapply' && res.data.data) {
        message.success(`重新提交完成：成功 ${res.data.data.success || 0} 条，失败 ${res.data.data.failure || 0} 条`);
      } else {
        message.success(res.data.data || '操作成功');
      }
      refreshAll();
    } else {
      message.error(res.data.msg || '操作失败');
    }
  };

  const typeOptions = useMemo(() => {
    if (eventInfo.type === 'GPLT') return ['星芒初绽', '长风破浪', '凌霄问鼎'].map(value => ({ value, label: value }));
    if (eventInfo.type === 'LANQIAO') return ['C/C++程序设计', 'Python程序设计', 'Java软件开发'].map(value => ({ value, label: value }));
    return [
      { value: 'official', label: eventInfo.isTeam === 1 ? '正式队伍' : '正式参赛' },
      { value: 'girl', label: '女生队伍' },
      { value: 'star', label: eventInfo.isTeam === 1 ? '打星队伍' : '打星参赛' }
    ];
  }, [eventInfo.type, eventInfo.isTeam]);

  const columns: any[] = [
    { title: '学院', dataIndex: 'schoolName', width: 140, fixed: 'left' }
  ];

  if (eventInfo.isTeam === 1) {
    columns.push({ title: '队伍名称', dataIndex: 'teamName', width: 160, fixed: 'left' });
    columns.push({ title: '队伍类型', dataIndex: 'teamType', width: 120, render: (value: string) => <Tag>{signupGroupText(value, true, '正式队伍')}</Tag> });
    if (viewType === 'detail') {
      columns.push(
        { title: '姓名', dataIndex: 'realName', width: 100 },
        { title: '角色', dataIndex: 'membersContent', width: 80 },
        { title: '学号', dataIndex: 'numberId', width: 120 },
        { title: '专业', dataIndex: 'majorName', width: 140 },
        { title: '年级', dataIndex: 'gradeYear', width: 80 },
        { title: '班级', dataIndex: 'className', width: 100 }
      );
    } else {
      columns.push({ title: '队伍成员（含队长）', dataIndex: 'membersContent', width: 220 });
    }
  } else {
    columns.push(
      { title: '姓名', dataIndex: 'realName', width: 100, fixed: 'left' },
      { title: '学号', dataIndex: 'numberId', width: 120 },
      { title: '专业', dataIndex: 'majorName', width: 140 },
      { title: '年级', dataIndex: 'gradeYear', width: 80 },
      { title: '班级', dataIndex: 'className', width: 100 },
      { title: '参赛组别', dataIndex: 'teamType', width: 130, render: (value: string) => <Tag>{signupGroupText(value, false, '正式参赛')}</Tag> }
    );
  }

  columns.push(
    { title: '指导老师', dataIndex: 'advisor', width: 120 },
    { title: '来源', dataIndex: 'signupSource', width: 150, render: (value: string, record: any) => <Tag color={value === 'school_admin' ? 'purple' : undefined}>{signupSourceText(value)}{record.createdByName ? `：${record.createdByName}` : ''}</Tag> },
    { title: '报名时间', dataIndex: 'signupTime', width: 130, render: (value: string) => value ? dayjs(value).format('MM-DD HH:mm') : '-' },
    { title: '状态', dataIndex: 'status', width: 100, fixed: 'right', render: (value: string) => <Tag color={statusMap[value]?.color || 'default'}>{signupStatusText(value)}</Tag> },
    {
      title: '操作',
      key: 'action',
      width: 150,
      fixed: 'right',
      render: (_: any, record: any) => (
        <Space size="small">
          {['rejected', 'cancelled'].includes(record.status) && <Button type="link" icon={<SyncOutlined />} onClick={() => handleBatchAction('reapply', [record.signupId])}>重报</Button>}
          {['approved', 'pending'].includes(record.status) && (
            <Popconfirm disabled={!canCancel} title="确定取消该报名？" onConfirm={() => handleBatchAction('cancel', [record.signupId])}>
              <Button type="link" danger disabled={!canCancel} icon={<StopOutlined />}>{signupEnded ? '报名已结束' : '取消'}</Button>
            </Popconfirm>
          )}
        </Space>
      )
    }
  );

  return (
    <Card>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/school/signups')}>返回</Button>
          <strong>{eventInfo.name || '报名管理'}</strong>
          <Tag color={eventInfo.isTeam === 1 ? 'purple' : 'blue'}>{eventInfo.isTeam === 1 ? '团队赛' : '个人赛'}</Tag>
        </Space>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={refreshAll}>刷新</Button>
          <Button type="primary" icon={<SendOutlined />} disabled={eventInfo.signupMode === 'roster'} onClick={openSignup}>批量报名</Button>
        </Space>
      </div>

      {signupEnded && <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="报名已结束，无法取消报名，学校报名管理员也不能取消。" />}
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message={eventInfo.signupMode === 'roster' ? '本场比赛按确定名单参赛，不开放代报名、重报或自行取消。名单调整请联系赛事管理员。' : '学校管理员提交的报名会进入待审核状态，审核通过后才计入正式报名名单。'}
      />

      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, marginBottom: 16, flexWrap: 'wrap' }}>
        <Space wrap>
          <Select value={statusFilter} onChange={setStatusFilter} style={{ width: 120 }}>
            <Option value="all">全部状态</Option>
            <Option value="approved">已通过</Option>
            <Option value="pending">待审核</Option>
            <Option value="rejected">已拒绝</Option>
            <Option value="cancelled">已取消</Option>
          </Select>
          <Select value={teamTypeFilter} onChange={setTeamTypeFilter} style={{ width: 150 }}>
            <Option value="all">全部组别</Option>
            {typeOptions.map(option => <Option key={option.value} value={option.value}>{option.label}</Option>)}
          </Select>
          <Input placeholder="搜索姓名/学号/队名" value={keyword} onChange={event => setKeyword(event.target.value)} prefix={<SearchOutlined />} style={{ width: 220 }} allowClear />
        </Space>
        <Space wrap>
          {eventInfo.isTeam === 1 && (
            <Radio.Group value={viewType} onChange={event => setViewType(event.target.value)} buttonStyle="solid">
              <Radio.Button value="summary">按队伍</Radio.Button>
              <Radio.Button value="detail">按成员</Radio.Button>
            </Radio.Group>
          )}
          <Button disabled={!selectedRowKeys.length || eventInfo.signupMode === 'roster'} onClick={() => handleBatchAction('reapply')}>批量重报</Button>
          <Button danger disabled={!selectedRowKeys.length || !canCancel} onClick={() => handleBatchAction('cancel')}>批量取消</Button>
        </Space>
      </div>

      <Table
        rowKey={(record) => viewType === 'detail' && record.numberId ? `${record.signupId}_${record.numberId}` : record.signupId}
        columns={columns}
        dataSource={list}
        loading={loading}
        rowSelection={{ selectedRowKeys, onChange: setSelectedRowKeys }}
        pagination={{ ...pagination, showTotal: total => `共 ${total} 条`, onChange: (page, size) => fetchSignups(page, size) }}
        scroll={{ x: 1500 }}
      />

      <Modal title="批量报名" open={signupOpen} onCancel={() => setSignupOpen(false)} onOk={submitSignup} confirmLoading={submitting} width={680}>
        <Form form={form} layout="vertical">
          {eventInfo.isTeam === 1 ? (
            <Form.Item name="teamIds" label="选择队伍" rules={[{ required: true, message: '请选择队伍' }]}>
              <Select mode="multiple" options={teamOptions.map(team => ({ value: team.teamId, label: `${team.teamName}（${team.leaderName || '无队长'}）` }))} />
            </Form.Item>
          ) : (
            <Form.Item name="userIds" label="选择学生" rules={[{ required: true, message: '请选择学生' }]}>
              <Select
                mode="multiple"
                showSearch
                filterOption={false}
                onSearch={searchStudents}
                placeholder="输入姓名或学号搜索本校学生"
                options={studentOptions.map(user => ({ value: user.userId, label: `${user.realName} (${user.numberId})` }))}
              />
            </Form.Item>
          )}
          <Form.Item name="teamType" label="参赛组别/类型" rules={[{ required: ['GPLT', 'LANQIAO'].includes(eventInfo.type), message: '请选择参赛组别' }]}>
            <Select options={typeOptions} />
          </Form.Item>
          <Form.Item name="advisor" label="指导老师">
            <AdvisorInput />
          </Form.Item>
          <Form.Item name="isRanking" label="是否参与排名">
            <Radio.Group options={[{ value: 1, label: '是' }, { value: 0, label: '否' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default SchoolSignupManager;
