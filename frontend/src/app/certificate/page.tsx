"use client";
/* eslint-disable @typescript-eslint/no-explicit-any, @typescript-eslint/no-unused-vars, react-hooks/immutability, react-hooks/exhaustive-deps, react-hooks/set-state-in-effect, react/jsx-key, @next/next/no-img-element */

import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, DatePicker, Select, Upload, message, Tag, Cascader, Alert, Tabs, Card, Empty, Tooltip, Row, Col, Result, Switch, Radio, List, Avatar, Space, Popconfirm, AutoComplete, Typography } from 'antd';
import { PlusOutlined, SafetyCertificateOutlined, DownloadOutlined, FieldTimeOutlined, TrophyOutlined, CalendarOutlined, LoginOutlined, UserOutlined, DeleteOutlined, TeamOutlined, EyeOutlined, SearchOutlined } from '@ant-design/icons';
import { useRouter } from 'next/navigation';
import axios from 'axios';
import { userDisplayName, userSearchLabel } from '@/types/user';
import dayjs from 'dayjs';
import SiteHeader from '../components/SiteHeader';
import { CertificateVerifyPanel } from './verify/CertificateVerifyClient';

interface CertificateLevel {
  levelId: number;
  name: string;
  levelType: string;
  rankType: string;
}

const SelfApplyList: React.FC = () => {
  const [list, setList] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editRecord, setEditRecord] = useState<any>(null);
  const [form] = Form.useForm();
  
  const [categories, setCategories] = useState<any[]>([]);
  const [terms, setTerms] = useState<any[]>([]);
  const [levelOptions, setLevelOptions] = useState<any[]>([]);

  // Team related state
  const [isTeam, setIsTeam] = useState(false);
  const [members, setMembers] = useState<any[]>([]);
  const [searchOptions, setSearchOptions] = useState<any[]>([]);
  const [memberType, setMemberType] = useState<'internal' | 'external'>('internal');
  const [tempMember, setTempMember] = useState<any>({});

  const user = JSON.parse(localStorage.getItem('user_info') || '{}');

  useEffect(() => {
    document.title = "证书查询与申领";
    if (user.userId) {
        fetchList();
        fetchOptions();
    }
  }, []);

  // Initialize members with current user when team switch is toggled
  useEffect(() => {
      if (isTeam && members.length === 0) {
          // Add current user as first member
          setMembers([{
              userId: user.userId,
              realName: user.realName,
              numberId: user.numberId,
              type: 'internal',
              isCaptain: 1, 
              sortOrder: 0
          }]);
      }
  }, [isTeam]);

  // 🔴 移除：删除了这里的 useEffect resetFields，改用 Modal 的 destroyOnClose 属性
  // 这样可以防止表单在不该重置的时候被重置

  // 编辑时预填表单数据
  useEffect(() => {
    if (editRecord && modalOpen) {
      form.setFieldsValue({
        categoryId: editRecord.categoryId,
        eventName: editRecord.eventName,
        subProjectName: editRecord.subProjectName || '',
        level: [editRecord.levelType, editRecord.levelId],
        time: dayjs(editRecord.time),
        termId: editRecord.term,
        advisor: editRecord.advisor || '',
        isTeam: editRecord.isTeam === 1,
        fileId: undefined,
      });
      if (editRecord.isTeam === 1) {
        setIsTeam(true);
        // 从 members 构建成员列表
        const memberList = (editRecord.members || []).map((m: any, idx: number) => ({
          userId: m.userId,
          realName: m.name,
          numberId: m.studentId || '',
          name: m.name,
          studentId: m.studentId || '',
          type: m.userId ? 'internal' : 'external',
          isCaptain: m.isCaptain,
          sortOrder: idx,
        }));
        setMembers(memberList);
      } else {
        setIsTeam(false);
        setMembers([]);
      }
    }
  }, [editRecord, modalOpen]);

  const handleSearchUser = async (value: string) => {
      if (!value) {
          setSearchOptions([]);
          return;
      }
      try {
          const res = await axios.get('/api/user/search', { params: { keyword: value } });
          if (res.data.code === 200) {
              const options = res.data.data.map((u: any) => ({
                  value: String(u.userId),
                  label: userSearchLabel(u),
                  user: u
              }));
              setSearchOptions(options);
          }
      } catch (e) {
          console.error(e);
      }
  };

  const onSelectUser = (value: string, option: any) => {
      setTempMember(option.user);
  };

  const handleAddMember = () => {
      if (memberType === 'internal') {
          if (!tempMember.userId) {
              message.warning('请搜索并选择一名站内用户');
              return;
          }
          // Internal members are identified by server-side user ID.
          if (members.some(m => Number(m.userId) === Number(tempMember.userId))) {
              message.warning('该成员已在列表中');
              return;
          }
          setMembers([...members, { ...tempMember, type: 'internal', sortOrder: members.length }]);
          setTempMember({});
      } else {
          if (!tempMember.name || !tempMember.studentId) {
              message.warning('请填写姓名和学号');
              return;
          }
           // Check duplicate
           if (members.some(m => m.studentId === tempMember.studentId)) {
              message.warning('该成员已在列表中');
              return;
          }
          setMembers([...members, { ...tempMember, type: 'external', sortOrder: members.length }]);
          setTempMember({});
      }
  };

  const removeMember = (index: number) => {
      const newMembers = [...members];
      newMembers.splice(index, 1);
      setMembers(newMembers);
  };
  
  const moveMember = (index: number, direction: 'up' | 'down') => {
      if ((direction === 'up' && index === 0) || (direction === 'down' && index === members.length - 1)) return;
      const newMembers = [...members];
      const targetIndex = direction === 'up' ? index - 1 : index + 1;
      [newMembers[index], newMembers[targetIndex]] = [newMembers[targetIndex], newMembers[index]];
      setMembers(newMembers);
  };

  const fetchList = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/certificate/my-list', {
        params: { current: 1, size: 100 }
      });
      const data = res.data.data.records || [];
      // 按创建时间倒序
      data.sort((a: any, b: any) => new Date(b.createTime).getTime() - new Date(a.createTime).getTime());
      setList(data);
    } catch (error) { console.error(error); } 
    finally { setLoading(false); }
  };

  const fetchOptions = async () => {
    try {
      const [catRes, termRes, levelRes] = await Promise.all([
        axios.get('/api/certificate/categories'),
        axios.get('/api/certificate/terms'),
        axios.get('/api/certificate/levels')
      ]);
      setCategories(catRes.data.data);
      setTerms(termRes.data.data);
      
      const levels: CertificateLevel[] = levelRes.data.data;
      const grouped: any = {};
      levels.forEach(l => {
        if (!grouped[l.levelType]) grouped[l.levelType] = [];
        // 级联选择：第一级是类型，第二级是具体等级ID
        grouped[l.levelType].push({ label: l.rankType, value: l.levelId, name: l.name });
      });
      const options = Object.keys(grouped).map(key => ({
        label: key, value: key, children: grouped[key]
      }));
      setLevelOptions(options);
    } catch (e) {}
  };

  const handleSubmit = async (values: any) => {
    setSubmitting(true);
    try {
        const isEdit = editRecord != null;
        const levelId = values.level && values.level.length === 2 ? values.level[1] : null;
        
        const fileObj = values.fileId && values.fileId[0];
        const uploadedFileId = fileObj?.status === 'done' && fileObj?.response?.code === 200
            ? fileObj.response.data : undefined;

        if ((!isEdit || fileObj) && !uploadedFileId) {
             message.warning(fileObj?.response?.msg || "证书图片正在上传中或上传失败，请稍后重试");
             return; 
        }

        const payload: any = {
            userId: user.userId,
            categoryId: values.categoryId,
            levelId: levelId,
            termId: values.termId, // ✅ 这里直接使用 termId，因为 Form.Item name 也改为了 termId
            time: values.time ? values.time.format('YYYY-MM-DD') : null, // ✅ 格式化时间
            eventName: values.eventName,
            subProjectName: values.subProjectName,
            advisor: values.advisor,
            isTeam: values.isTeam ? 1 : 0,
            members: values.isTeam ? members.map((m, index) => m.userId ? {
                userId: m.userId, sortOrder: index
            } : {
                name: m.name || m.realName, studentId: m.studentId, sortOrder: index
            }) : [],
        };
        
        if (isEdit) {
            payload.certificateVerifyId = editRecord.certificateVerifyId;
        }
        if (uploadedFileId) {
            payload.fileId = uploadedFileId;
        }

        const res = await axios.post('/api/certificate/submit', payload);
        
        if (res.data.code === 200) {
            message.success(isEdit ? "修改成功，请等待审核" : "提交成功");
            setModalOpen(false);
            setEditRecord(null);
            setMembers([]); // Clear members
            fetchList();
        } else {
            message.error(res.data.msg || "提交失败，请检查填写信息");
        }
    } catch (error: any) {
        console.error("Submit Error:", error);
        message.error(error.response?.data?.msg || "网络请求失败"); 
    } finally {
        setSubmitting(false);
    }
  };

  const normFile = (e: any) => {
    if (Array.isArray(e)) return e;
    return e?.fileList;
  };

  const handleEdit = (record: any) => {
    setEditRecord(record);
    setModalOpen(true);
  };

  const columns = [
    { title: '认证名称', dataIndex: 'eventName', key: 'eventName' },
    { title: '赛事分类', dataIndex: 'categoryName', key: 'categoryName' },
    { title: '奖项等级', key: 'level', render: (_: any, r: any) => <span><Tag color="geekblue">{r.levelType}</Tag>{r.levelName}</span> },
    { title: '获奖时间', dataIndex: 'time', render: (t: string) => dayjs(t).format('YYYY-MM-DD') },
    { title: '状态', dataIndex: 'status', render: (s: string) => { if(s === 'approved') return <Tag color="green">已通过</Tag>; if(s === 'rejected') return <Tag color="red">已驳回</Tag>; return <Tag color="blue">待审核</Tag>; } },
    { title: '审核意见', dataIndex: 'comments', ellipsis: true },
    { title: '操作', key: 'action', width: 120, render: (_: any, record: any) => {
      if (record.status === 'rejected') {
        return <Button type="link" size="small" onClick={() => handleEdit(record)}>修改信息</Button>;
      }
      return null;
    }},
  ];

  const onFinishFailed = (errorInfo: any) => {
      // 表单校验失败
      message.error('请完整填写所有必填项');
  };

  return (
    <div>
        <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新增自主认证</Button>
        </div>
        <Table 
            dataSource={list} 
            columns={columns} 
            rowKey="certificateVerifyId" 
            loading={loading} 
            pagination={{ pageSize: 10 }} 
            expandable={{ 
                expandedRowRender: (record: any) => (
                    <div style={{ padding: 16, background: '#f9f9f9', display: 'flex', gap: 24 }}>
                        <div style={{width: 300, border: '1px solid #eee', background: '#fff', padding: 4}}>
                            <img src={record.imageUrl} alt="cert" style={{ width: '100%', objectFit: 'contain' }} />
                        </div>
                        <div>
                            <p><b>审核人：</b>{record.auditUserRealName || '-'}</p>
                            <p><b>审核意见：</b>{record.comments || '无'}</p>
                            <p><b>提交时间：</b>{dayjs(record.createTime).format('YYYY-MM-DD HH:mm')}</p>
                            {record.subProjectName && <p><b>子项目：</b>{record.subProjectName}</p>}
                            {record.advisor && <p><b>指导老师：</b>{record.advisor}</p>}
                            {record.isTeam === 1 && <p><b>团队赛：</b><Tag color="purple">是</Tag></p>}
                            {record.isTeam === 1 && record.members && record.members.length > 0 && (
                                <div>
                                    <p><b>团队成员：</b></p>
                                    <div style={{display: 'flex', gap: 4, flexWrap: 'wrap'}}>
                                        {record.members.map((m: any, idx: number) => (
                                            <Tag key={idx} color={m.isCaptain ? 'geekblue' : 'default'}>
                                                {m.name} ({m.studentId})
                                            </Tag>
                                        ))}
                                    </div>
                                </div>
                            )}
                        </div>
                    </div>
                ) 
            }} 
        />
        
        {/* ✅ 关键修复：destroyOnClose={true} 确保每次打开都是全新的表单 */}
        <Modal 
            title={editRecord ? "修改证书认证申请" : "提交证书认证申请"} 
            open={modalOpen} 
            onCancel={() => { setModalOpen(false); setEditRecord(null); }} 
            footer={null} 
            width={650}
            destroyOnClose={true} 
        >
            <Form 
                form={form} 
                layout="vertical" 
                onFinish={handleSubmit}
                onFinishFailed={onFinishFailed}
                preserve={false} // ✅ 配合 destroyOnClose，确保数据不残留
            >
                <Alert description="此处用于上传您自己获得的实体证书照片进行认证。" type="info" showIcon style={{ marginBottom: 20 }} />
                
                <Form.Item name="categoryId" label="1. 赛事库（赛事类型）" rules={[{ required: true, message: '请选择赛事库' }]}> 
                    <Select options={categories.map(c => ({ label: c.name, value: c.categoryId }))} placeholder="请选择" /> 
                </Form.Item>
                
                <Form.Item name="eventName" label="2. 赛事名称（具体比赛活动）" rules={[{ required: true, message: '请输入赛事名称' }]}> 
                    <Input placeholder="如：第45届ICPC亚洲区域赛" /> 
                </Form.Item>

                <Form.Item name="subProjectName" label="3. 子项目名称（选填）">
                    <Input placeholder="例如：C++软件开发赛道" />
                </Form.Item>
                
                <Form.Item name="level" label="4. 获奖等级" rules={[{ required: true, message: '请选择获奖等级' }]}> 
                    <Cascader options={levelOptions} placeholder="请选择" /> 
                </Form.Item>
                
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
                    <Form.Item name="time" label="5. 证书落款日期" rules={[{ required: true, message: '请选择落款日期' }]}> 
                        <DatePicker style={{ width: '100%' }} /> 
                    </Form.Item>
                    <Form.Item name="termId" label="6. 证书所属学期" rules={[{ required: true, message: '请选择所属学期' }]}> 
                        <Select options={terms.map(t => ({ label: t.termName, value: t.termId }))} placeholder="请选择" /> 
                    </Form.Item>
                </div>

                <Form.Item name="advisor" label="7. 第一指导老师（选填）">
                    <Input placeholder="请输入指导老师姓名" />
                </Form.Item>

                <Form.Item name="isTeam" label="8. 是否为团队赛" valuePropName="checked">
                    <Switch onChange={(checked) => setIsTeam(checked)} checkedChildren="是" unCheckedChildren="否" />
                </Form.Item>

                {isTeam && (
                    <div style={{ marginBottom: 24, padding: 12, border: '1px solid #f0f0f0', borderRadius: 8 }}>
                        <div style={{ marginBottom: 8, fontWeight: 500 }}>团队成员（支持排序，第一位默认为申请人）:</div>
                        <Alert type="info" showIcon description="选择站内账号时，仅可添加本人或您担任队长、且已接受邀请的队员。其他历史获奖成员请手工填写，审核通过后由管理员核对材料并确认账号归属。" style={{ marginBottom: 12 }} />
                        
                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                             {members.map((item, index) => (
                                <div key={item.userId ? `user-${item.userId}` : `external-${index}`} style={{
                                    padding: '8px',
                                    backgroundColor: '#fff',
                                    border: '1px solid #ddd',
                                    borderRadius: '4px',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'space-between'
                                }}>
                                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                        <div style={{width: 24, textAlign: 'center', color: '#999'}}>{index + 1}</div>
                                        <Avatar size="small" icon={<UserOutlined />} />
                                        <div>
                                            <div>{item.userId ? userDisplayName(item) : item.name || item.realName} <span style={{fontSize: 12, color: '#888'}}>({item.userId ? `#${item.userId}` : item.studentId})</span></div>
                                            <div style={{fontSize: 10, color: '#999'}}>{item.type === 'internal' ? '站内账号' : '手工填写'}</div>
                                        </div>
                                    </div>
                                    <div style={{ display: 'flex', gap: 4 }}>
                                        <Button type="text" size="small" disabled={index === 0} onClick={() => moveMember(index, 'up')}>↑</Button>
                                        <Button type="text" size="small" disabled={index === members.length - 1} onClick={() => moveMember(index, 'down')}>↓</Button>
                                        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => removeMember(index)} disabled={item.userId === user.userId} />
                                    </div>
                                </div>
                             ))}
                        </div>

                        <div style={{ marginTop: 12 }}>
                            <Radio.Group value={memberType} onChange={e => { setMemberType(e.target.value); setTempMember({}); }} buttonStyle="solid" style={{ marginBottom: 8 }}>
                                <Radio.Button value="internal">站内用户搜索</Radio.Button>
                                <Radio.Button value="external">手工填写成员</Radio.Button>
                            </Radio.Group>
                            
                            <div style={{ display: 'flex', gap: 8 }}>
                                {memberType === 'internal' ? (
                                    <AutoComplete
                                        options={searchOptions}
                                        style={{ flex: 1 }}
                                        onSearch={handleSearchUser}
                                        onSelect={onSelectUser}
                                        placeholder="输入昵称搜索"
                                    />
                                ) : (
                                    <Space style={{ flex: 1 }}>
                                        <Input placeholder="姓名" value={tempMember.name} onChange={e => setTempMember({...tempMember, name: e.target.value})} />
                                        <Input placeholder="学号 (必填)" value={tempMember.studentId} onChange={e => setTempMember({...tempMember, studentId: e.target.value})} />
                                    </Space>
                                )}
                                <Button type="dashed" icon={<PlusOutlined />} onClick={handleAddMember}>添加</Button>
                            </div>
                        </div>
                    </div>
                )}
                
                <Form.Item name="fileId" label="9. 上传证书照片" valuePropName="fileList" getValueFromEvent={normFile} rules={[{ required: !editRecord, message: '请上传证书图片' }]}>
                    <Upload 
                        action="/api/file/upload" 
                        listType="picture-card" 
                        maxCount={1} 
                        withCredentials 
                        data={{ uploaderId: user.userId, uploadType: 'certificate' }}
                        accept=".png,.jpg,.jpeg"
                        onChange={({ file }) => {
                            if (file.status === 'done' && file.response?.code !== 200) {
                                message.error(file.response?.msg || '证书图片上传失败，请重新上传');
                            } else if (file.status === 'error') {
                                message.error(file.response?.msg || '证书图片上传失败，请重新上传');
                            }
                        }}
                        beforeUpload={(file) => {
                            const isImage = file.type === 'image/png' || file.type === 'image/jpeg' || file.type === 'image/jpg';
                            if (!isImage) {
                                message.error('只能上传 PNG/JPG/JPEG 格式的图片文件！');
                            }
                            if (isImage && file.size > 5 * 1024 * 1024) {
                                message.error('证书图片大小不能超过5MB');
                                return Upload.LIST_IGNORE;
                            }
                            return isImage || Upload.LIST_IGNORE;
                        }}
                    > 
                        <div><PlusOutlined /><div>上传</div></div> 
                    </Upload>
                </Form.Item>
                
                <Button type="primary" htmlType="submit" block loading={submitting}>提交</Button>
            </Form>
        </Modal>
    </div>
  );
};

const GrantedCertList: React.FC = () => {
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const user = JSON.parse(localStorage.getItem('user_info') || '{}');

    useEffect(() => { 
        if(user && user.userId) fetchData(); 
    }, []);

    const fetchData = async () => {
        setLoading(true);
        try {
            const res = await axios.get('/api/certificate/grant/my');
            if(res.data.code === 200) {
                const data = res.data.data || [];
                data.sort((a: any, b: any) => new Date(b.time).getTime() - new Date(a.time).getTime());
                setList(data);
            }
        } catch (e) {
            console.error(e);
        } finally { 
            setLoading(false); 
        }
    };

    return (
        <div style={{ paddingTop: 10 }}>
            {loading ? <p>加载中...</p> : list.length === 0 ? <Empty description="暂未收到官方颁发的电子证书" /> : (
                <Row gutter={[16, 16]} className="certificate-grant-grid">
                    {list.map(item => (
                        <Col xs={24} sm={12} lg={8} xl={6} key={item.accessToken}>
                            <Card 
                                className="certificate-grant-card"
                                hoverable
                                actions={[
                                    <Tooltip title="在线预览">
                                        <EyeOutlined key="preview" onClick={() => window.open(`/api/certificate/grant/preview?accessToken=${encodeURIComponent(item.accessToken)}`, '_blank')} />
                                    </Tooltip>,
                                    <Tooltip title="下载证书">
                                        <DownloadOutlined key="download" onClick={() => window.open(`/api/certificate/grant/download?accessToken=${encodeURIComponent(item.accessToken)}`, '_blank')} />
                                    </Tooltip>
                                ]}
                                title={
                                    <Tooltip title={item.typeName}>
                                        <span className="certificate-grant-title">
                                            <SafetyCertificateOutlined style={{ color: '#faad14', marginRight: 8 }} />
                                            {item.typeName}
                                        </span>
                                    </Tooltip>
                                }
                            >
                                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                                    <div className="certificate-grant-detail">
                                        <span style={{ color: '#888' }}><TrophyOutlined /> 奖项等级</span>
                                        <Tag color="geekblue">{item.awardName || item.levelName || '无等级'}</Tag>
                                    </div>
                                    {item.teamName && (
                                      <div className="certificate-grant-detail">
                                        <span style={{ color: '#888' }}><TeamOutlined /> 队伍名称</span>
                                        <span className="certificate-grant-value">{item.teamName}</span>
                                      </div>
                                    )}
                                    {item.certificateNo && (
                                      <div className="certificate-grant-detail">
                                        <span style={{ color: '#888' }}><SafetyCertificateOutlined /> 证书编号</span>
                                        <Typography.Text className="certificate-grant-value" copyable>{item.certificateNo}</Typography.Text>
                                      </div>
                                    )}
                                    <div className="certificate-grant-detail">
                                        <span style={{ color: '#888' }}><CalendarOutlined /> 所属学期</span>
                                        <span>{item.termName || '未知学期'}</span>
                                    </div>
                                    <div className="certificate-grant-detail">
                                        <span style={{ color: '#888' }}><FieldTimeOutlined /> 颁发时间</span>
                                        <span>{dayjs(item.time).format('YYYY-MM-DD')}</span>
                                    </div>
                                </div>
                            </Card>
                        </Col>
                    ))}
                </Row>
            )}
        </div>
    );
};

const GuestDownloadPanel: React.FC = () => {
    const [realName, setRealName] = useState('');
    const [school, setSchool] = useState('');
    const [numberId, setNumberId] = useState('');
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);
    const viewer = JSON.parse(localStorage.getItem('user_info') || '{}');
    const isLoggedIn = !!viewer.userId;

    const handleSearch = async () => {
        if (!realName.trim() || !school.trim() || !numberId.trim()) {
            message.warning('请输入姓名、学校和学号');
            return;
        }
        setLoading(true);
        try {
            const res = await axios.post('/api/certificate/grant/guest/list', {
                realName: realName.trim(),
                school: school.trim(),
                numberId: numberId.trim()
            });
            if (res.data.code === 200) {
                setList(res.data.data || []);
                setSearched(true);
            } else {
                message.error(res.data.msg || '查询失败');
            }
        } catch {
            message.error('查询失败，请稍后重试');
        } finally {
            setLoading(false);
        }
    };

    const download = (item: any) => {
        window.open(
            `/api/certificate/grant/guest/download?accessToken=${encodeURIComponent(item.accessToken)}`,
            '_blank'
        );
    };

    const preview = (item: any) => {
        window.open(
            `/api/certificate/grant/guest/preview?accessToken=${encodeURIComponent(item.accessToken)}`,
            '_blank'
        );
    };

    const renderCertCard = (item: any, keyPrefix: string) => (
        <Col xs={24} sm={12} lg={8} xl={6} key={`${keyPrefix}-${item.accessToken}`}>
            <Card
                className="certificate-grant-card"
                hoverable
                actions={[
                    <Tooltip title="在线预览">
                        <EyeOutlined key="preview" onClick={() => preview(item)} />
                    </Tooltip>,
                    <Tooltip title="下载证书">
                        <DownloadOutlined key="download" onClick={() => download(item)} />
                    </Tooltip>
                ]}
                title={
                    <Tooltip title={item.typeName}>
                    <span className="certificate-grant-title">
                            <SafetyCertificateOutlined style={{ color: '#faad14', marginRight: 8 }} />
                            {item.typeName}
                        </span>
                    </Tooltip>
                }
            >
                <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                    <div className="certificate-grant-detail">
                        <span style={{ color: '#888' }}><TrophyOutlined /> 奖项等级</span>
                        <Tag color="geekblue">{item.awardName || item.levelName || '无等级'}</Tag>
                    </div>
                    {item.teamName && (
                      <div className="certificate-grant-detail">
                        <span style={{ color: '#888' }}><TeamOutlined /> 队伍名称</span>
                        <span className="certificate-grant-value">{item.teamName}</span>
                      </div>
                    )}
                    {item.certificateNo && (
                      <div className="certificate-grant-detail">
                        <span style={{ color: '#888' }}><SafetyCertificateOutlined /> 证书编号</span>
                        <Typography.Text className="certificate-grant-value" copyable>{item.certificateNo}</Typography.Text>
                      </div>
                    )}
                    <div className="certificate-grant-detail">
                        <span style={{ color: '#888' }}><CalendarOutlined /> 所属学期</span>
                        <span className="certificate-grant-value">{item.termName || '未知学期'}</span>
                    </div>
                    <div className="certificate-grant-detail">
                        <span style={{ color: '#888' }}><FieldTimeOutlined /> 颁发时间</span>
                        <span className="certificate-grant-value">{dayjs(item.time).format('YYYY-MM-DD')}</span>
                    </div>
                </div>
            </Card>
        </Col>
    );

    return (
        <div style={{ paddingTop: 10 }}>
            <Card style={{ maxWidth: 720, margin: '0 auto' }}>
                <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
                    <Input
                        style={{ flex: 1, minWidth: 160 }}
                        size="large"
                        placeholder="证书上的姓名"
                        value={realName}
                        onChange={e => setRealName(e.target.value)}
                        onPressEnter={handleSearch}
                    />
                    <Input
                        style={{ flex: 1, minWidth: 200 }}
                        size="large"
                        placeholder="学校（如：北京电子科技学院）"
                        value={school}
                        onChange={e => setSchool(e.target.value)}
                        onPressEnter={handleSearch}
                    />
                    <Input
                        style={{ flex: 1, minWidth: 180 }}
                        size="large"
                        placeholder="学号"
                        value={numberId}
                        onChange={e => setNumberId(e.target.value)}
                        onPressEnter={handleSearch}
                    />
                    <Button type="primary" size="large" icon={<SearchOutlined />} loading={loading} onClick={handleSearch}>
                        查询
                    </Button>
                </div>
                <Alert
                    type="info"
                    showIcon
                    message="说明"
                    description="输入证书上的姓名、学校与学号完成身份核验。查询成功后会签发短期访问凭证，预览和下载链接不再包含可篡改的证书 ID 或身份信息。"
                    style={{ marginTop: 16 }}
                />
            </Card>

            {isLoggedIn && !searched && (
                <>
                    <div style={{ marginTop: 20, marginBottom: 4, fontWeight: 600, color: '#595959' }}>
                        我的证书（仅显示当前登录账号名下的证书）
                    </div>
                    <GrantedCertList />
                </>
            )}

            {isLoggedIn && searched && (
                <div style={{ marginTop: 12, textAlign: 'right' }}>
                    <Button onClick={() => setSearched(false)}>返回我的证书</Button>
                </div>
            )}

            {searched && !loading && list.length === 0 && (
                <Empty style={{ marginTop: 32 }} description="身份信息不匹配或暂无可下载的电子证书" />
            )}

            {list.length > 0 && (
                <Row gutter={[16, 16]} className="certificate-grant-grid" style={{ marginTop: 24 }}>
                    {list.map(item => renderCertCard(item, 'search'))}
                </Row>
            )}
        </div>
    );
};

const CertificateList: React.FC = () => {
  const router = useRouter();
  const [user, setUser] = useState<any | undefined>(undefined);

  useEffect(() => {
    const userStr = localStorage.getItem('user_info');
    setUser(userStr ? JSON.parse(userStr) : null);
  }, []);

  if (user === undefined) {
    return (
      <>
        <SiteHeader />
        <main className="content-page-shell">
          <div className="replica-page-panel certificate-page-panel" style={{ padding: '80px 20px', background: 'rgba(240, 248, 255, 0.82)', borderRadius: '8px', minHeight: '80vh', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            加载中...
          </div>
        </main>
      </>
    );
  }

  if (!user) {
    return (
      <>
        <SiteHeader />
        <main className="content-page-shell">
          <div className="replica-page-panel certificate-page-panel" style={{ padding: 24, background: 'rgba(240, 248, 255, 0.82)', minHeight: '80vh' }}>
            <div style={{ marginBottom: 20 }}>
              <h1 style={{ marginBottom: 8 }}><SafetyCertificateOutlined /> 证书中心</h1>
              <div style={{ color: '#888' }}>
                未登录模式：可查验证书真伪，或凭学校与学号查询并下载您的电子证书。登录后可管理自主认证与官方证书。
              </div>
            </div>
            <Tabs
              items={[
                { key: '1', label: '查验证书', children: <CertificateVerifyPanel embedded /> },
                { key: '2', label: '下载证书', children: <GuestDownloadPanel /> },
              ]}
            />
            <div style={{ textAlign: 'center', marginTop: 12 }}>
              <Button type="link" icon={<LoginOutlined />} onClick={() => router.push('/login')}>登录后查看更多功能</Button>
            </div>
          </div>
        </main>
      </>
    );
  }

  return (
    <>
      <SiteHeader />
      <main className="content-page-shell">
        <div className="replica-page-panel certificate-page-panel" style={{ padding: 24, background: 'rgba(240, 248, 255, 0.82)', minHeight: '80vh' }}>
          <div style={{ marginBottom: 20 }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
              <h1 style={{ marginBottom: 8 }}><SafetyCertificateOutlined /> 证书中心</h1>
            </div>
            <div style={{ color: '#888' }}>
                这里汇集了您主动申请认证的奖项以及校队官方颁发的电子证书。
            </div>
          </div>
          
          <Tabs 
            defaultActiveKey="1"
            items={[
                { key: '1', label: '个人认证申请 (自主)', children: <SelfApplyList /> },
                { key: '2', label: '下载电子证书 (官方)', children: <GuestDownloadPanel /> },
                { key: '3', label: '证书查验', children: <CertificateVerifyPanel embedded /> },
            ]}
          />
        </div>
      </main>
    </>
  );
};

export default CertificateList;
