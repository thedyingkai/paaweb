"use client";

import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Checkbox, Input, message, Modal, Select, Space, Table, Tag, Typography } from 'antd';
import { DeleteOutlined, SearchOutlined } from '@ant-design/icons';
import axios from 'axios';
import { memberOrganizations } from '@/lib/besti-members';

const { Text } = Typography;

interface UserItem {
    userId: number;
    userName: string;
    realName: string;
    numberId: string;
    role: string;
    userAccount: string;
}

interface MemberItem {
    memberId: number;
    term: string;
    department: string;
    userId?: number;
}

type GroupItem = {
    department: string;
    users: UserItem[];
};

const roleLabel: Record<string, string> = {
    supper_admin: '超级管理员',
    super_admin: '超级管理员',
    stu_admin: '非超级管理员',
    school_admin: '学校管理员',
      department: '部门',
};

const departmentOrder = memberOrganizations;

const PermissionManager: React.FC<{ mode?: 'roles' | 'permissions' }> = ({ mode = 'permissions' }) => {
    const [loading, setLoading] = useState(false);
    const [data, setData] = useState<UserItem[]>([]);
    const [latestMembers, setLatestMembers] = useState<MemberItem[]>([]);
    const [latestTerm, setLatestTerm] = useState('');
    const [keyword, setKeyword] = useState('');
    const [selectedRowKeys, setSelectedRowKeys] = useState<React.Key[]>([]);

    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingUser, setEditingUser] = useState<UserItem | null>(null);
    const [selectedPerms, setSelectedPerms] = useState<string[]>([]);
    const [saveLoading, setSaveLoading] = useState(false);
    const [roleSavingId, setRoleSavingId] = useState<number | null>(null);

    useEffect(() => {
        fetchAll();
    }, []);

    const fetchAll = async () => {
        setLoading(true);
        try {
            const [userRes, termRes] = await Promise.all([
                axios.get('/api/admin/user/list', {
                    params: { current: 1, size: 1000, keyword, roleFilter: 'admins', accountStatus: 'normal' }
                }),
                axios.get('/api/association-member/terms')
            ]);

            if (userRes.data.code === 200) {
                setData(userRes.data.data.records || []);
            }

            const terms = termRes.data.code === 200 && Array.isArray(termRes.data.data)
                ? [...termRes.data.data].sort((a: string, b: string) => b.localeCompare(a))
                : [];
            const term = terms[0] || '';
            setLatestTerm(term);
            if (term) {
                const memberRes = await axios.get('/api/association-member/list', { params: { term } });
                setLatestMembers(memberRes.data.code === 200 ? (memberRes.data.data || []) : []);
            } else {
                setLatestMembers([]);
            }
        } catch (e) {
            message.error('获取列表失败');
        } finally {
            setLoading(false);
        }
    };

    const visibleData = useMemo(() => {
        const q = keyword.trim().toLowerCase();
        if (!q) return data;
        return data.filter(user => (
            (user.realName || '').toLowerCase().includes(q) ||
            (user.userAccount || '').toLowerCase().includes(q) ||
            (user.numberId || '').toLowerCase().includes(q)
        ));
    }, [data, keyword]);

    const groupedData = useMemo<GroupItem[]>(() => {
        const userById = new Map(visibleData.map(user => [Number(user.userId), user]));
        const usedUserIds = new Set<number>();
        const grouped = new Map<string, UserItem[]>();

        latestMembers.forEach(member => {
            if (!member.userId) return;
            const user = userById.get(Number(member.userId));
            if (!user) return;
            usedUserIds.add(Number(user.userId));
            const department = member.department || '其他';
            grouped.set(department, [...(grouped.get(department) || []), user]);
        });

        const groups = Array.from(grouped.entries())
            .map(([department, users]) => ({ department, users }))
            .sort((a, b) => {
                const ia = departmentOrder.indexOf(a.department);
                const ib = departmentOrder.indexOf(b.department);
                if (ia !== -1 && ib !== -1) return ia - ib;
                if (ia !== -1) return -1;
                if (ib !== -1) return 1;
                return a.department.localeCompare(b.department);
            });

        const otherAdmins = visibleData.filter(user => !usedUserIds.has(Number(user.userId)));
        if (otherAdmins.length > 0) {
            groups.push({ department: '其他管理员', users: otherAdmins });
        }

        return groups;
    }, [latestMembers, visibleData]);

    const handleEditPerms = async (user: UserItem) => {
        setEditingUser(user);
        setIsModalOpen(true);
        setSelectedPerms([]);

        try {
            const res = await axios.get(`/api/admin/permission/get/${user.userId}`);
            if (res.data.code === 200) {
                setSelectedPerms(res.data.data || []);
            }
        } catch (e) {
            message.error('获取权限失败');
        }
    };

    const handleSave = async () => {
        if (!editingUser) return;
        setSaveLoading(true);
        try {
            const res = await axios.post('/api/admin/permission/set', {
                userId: editingUser.userId,
                permissions: selectedPerms
            });
            if (res.data.code === 200) {
                message.success('权限设置成功');
                setIsModalOpen(false);
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            message.error('保存失败');
        } finally {
            setSaveLoading(false);
        }
    };

    const handleRoleChange = async (user: UserItem, role: string) => {
        setRoleSavingId(user.userId);
        try {
            const res = await axios.post('/api/admin/permission/set-role', {
                userId: user.userId,
                role
            });
            if (res.data.code === 200) {
                message.success('管理员级别已更新');
                await fetchAll();
            } else {
                message.error(res.data.msg || '修改失败');
            }
        } catch (e) {
            message.error('修改失败');
        } finally {
            setRoleSavingId(null);
        }
    };

    const confirmCancelAdmins = (users: UserItem[]) => {
        if (users.length === 0) {
            message.warning('请选择需要取消管理员权限的人员');
            return;
        }
        Modal.confirm({
            title: '确认取消管理员权限？',
            width: 560,
            icon: <DeleteOutlined style={{ color: '#ff4d4f' }} />,
            content: (
                <div>
                    <Alert
                        type="warning"
                        showIcon
                        message="取消后，这些人员将不再显示在权限管理页面。"
                        style={{ marginBottom: 12 }}
                    />
                    <div style={{ maxHeight: 240, overflow: 'auto' }}>
                        {users.map(user => (
                            <div key={user.userId} style={{ padding: '6px 0' }}>
                                <Text strong>{user.realName || user.userName}</Text>
                                <Text type="secondary">（{user.numberId || user.userAccount}）</Text>
                            </div>
                        ))}
                    </div>
                </div>
            ),
            okText: '确认取消',
            okButtonProps: { danger: true },
            cancelText: '再想想',
            onOk: async () => {
                const res = await axios.post('/api/admin/permission/cancel-admin', {
                    userIds: users.map(user => user.userId)
                });
                if (res.data.code === 200) {
                    message.success('已取消管理员权限');
                    setSelectedRowKeys([]);
                    await fetchAll();
                } else {
                    message.error(res.data.msg || '取消失败');
                }
            }
        });
    };

    const handleGroupSelectionChange = (groupUsers: UserItem[], keys: React.Key[]) => {
        const groupKeySet = new Set(groupUsers.map(user => user.userId));
        const outsideGroupKeys = selectedRowKeys.filter(key => !groupKeySet.has(Number(key)));
        setSelectedRowKeys([...outsideGroupKeys, ...keys]);
    };

    const columns = [
        { title: '真实姓名', dataIndex: 'realName' },
        { title: '账号', dataIndex: 'userAccount' },
        { title: '学号/工号', dataIndex: 'numberId' },
        ...(mode === 'roles' ? [{
            title: '管理员级别',
            dataIndex: 'role',
            width: 220,
            render: (role: string, record: UserItem) => (
                <Select
                    value={role === 'super_admin' ? 'supper_admin' : role}
                    style={{ width: 160 }}
                    loading={roleSavingId === record.userId}
                    disabled={record.userAccount === 'admin'}
                    onChange={(value) => handleRoleChange(record, value)}
                    options={[
                        { label: '超级管理员', value: 'supper_admin' },
                        { label: '非超级管理员', value: 'stu_admin' },
                    ]}
                />
            )
        }] : []),
        {
            title: '状态',
            dataIndex: 'role',
            width: 140,
            render: (role: string) => <Tag color={role === 'supper_admin' || role === 'super_admin' ? 'red' : 'blue'}>{roleLabel[role] || role}</Tag>
        },
        {
            title: '操作',
            width: 210,
            render: (_: unknown, record: UserItem) => (
                <Space>
                    {mode === 'permissions' && <Button type="link" onClick={() => handleEditPerms(record)}>分配权限</Button>}
                    {mode === 'roles' && (
                        <Button
                            type="link"
                            danger
                            disabled={record.userAccount === 'admin'}
                            onClick={() => confirmCancelAdmins([record])}
                        >
                            取消管理员
                        </Button>
                    )}
                </Space>
            )
        }
    ];

    const normalPerms = [
        { label: '公告管理', value: 'manage_notice' },
        { label: '培训管理', value: 'manage_training' },
        { label: '赛事列表', value: 'manage_contest' },
        { label: '赛事类别管理', value: 'manage_category' },
        { label: '用户列表', value: 'manage_user' },
        { label: '修改用户邮箱（需用户列表权限）', value: 'manage_user_email' },
        { label: '修改用户角色（需用户列表权限）', value: 'manage_user_role' },
        { label: '审核与修改用户状态（需用户列表权限）', value: 'manage_user_status' },
        { label: 'OJ账号', value: 'manage_oj' },
    ];

    const superPerms = [
        { label: '内容展示与系统配置', value: 'manage_sys' },
        { label: '接口管理', value: 'manage_api' },
        { label: '证书发放', value: 'manage_cert_grant' },
        { label: '架构管理', value: 'manage_org' },
        { label: '学期设置', value: 'manage_term' },
    ];

    const handleNormalPermChange = (vals: string[]) => {
        const normalPermValues = normalPerms.map(p => p.value);
        const otherPerms = selectedPerms.filter(p => !normalPermValues.includes(p));
        setSelectedPerms([...otherPerms, ...vals]);
    };

    const handleSuperPermChange = (vals: string[]) => {
        const superPermValues = superPerms.map(p => p.value);
        const otherPerms = selectedPerms.filter(p => !superPermValues.includes(p));
        setSelectedPerms([...otherPerms, ...vals]);
    };

    const selectedUsers = data.filter(user => selectedRowKeys.includes(user.userId));

    return (
        <div>
            <Card title={mode === 'roles' ? '管理员管理' : '权限管理'}>
                <Space style={{ marginBottom: 16, width: '100%', justifyContent: 'space-between' }} wrap>
                    <Space wrap>
                        <Input
                            placeholder="搜索姓名/账号/学号"
                            value={keyword}
                            onChange={e => setKeyword(e.target.value)}
                            onPressEnter={fetchAll}
                            style={{ width: 220 }}
                        />
                        <Button type="primary" icon={<SearchOutlined />} onClick={fetchAll}>查询</Button>
                        {latestTerm && <Tag color="processing">当前干部学年：{latestTerm}</Tag>}
                    </Space>
                    {mode === 'roles' && (
                        <Button
                            danger
                            icon={<DeleteOutlined />}
                            disabled={selectedUsers.length === 0}
                            onClick={() => confirmCancelAdmins(selectedUsers)}
                        >
                            批量取消管理员
                        </Button>
                    )}
                </Space>

                <Alert
                    type="info"
                    showIcon
                    style={{ marginBottom: 16 }}
                    message="本页面按当前最新学年的校队成员分部门展示管理员；不属于最新干部名单的管理员会归入“其他管理员”。"
                />

                {groupedData.map(group => (
                    <Card
                        key={group.department}
                        type="inner"
                        title={`${group.department}（${group.users.length}人）`}
                        style={{ marginBottom: 16 }}
                    >
                        <Table
                            columns={columns}
                            dataSource={group.users}
                            rowKey="userId"
                            loading={loading}
                            pagination={false}
                            rowSelection={mode === 'roles' ? {
                                selectedRowKeys,
                                preserveSelectedRowKeys: true,
                                onChange: (keys) => handleGroupSelectionChange(group.users, keys),
                                getCheckboxProps: (record) => ({
                                    disabled: record.userAccount === 'admin'
                                })
                            } : undefined}
                        />
                    </Card>
                ))}
            </Card>

            <Modal
                title={`分配权限 - ${editingUser?.realName}`}
                open={isModalOpen}
                onOk={handleSave}
                onCancel={() => setIsModalOpen(false)}
                confirmLoading={saveLoading}
                destroyOnClose
            >
                <div style={{ marginBottom: 16 }}>
                    <div style={{ fontWeight: 'bold', marginBottom: 8 }}>基础管理权限</div>
                    <Checkbox.Group
                        options={normalPerms}
                        value={selectedPerms.filter(p => normalPerms.map(np => np.value).includes(p))}
                        onChange={handleNormalPermChange}
                    />
                </div>

                {(editingUser?.role === 'supper_admin' || editingUser?.role === 'super_admin') && (
                    <div>
                        <div style={{ fontWeight: 'bold', marginBottom: 8, color: 'red' }}>高级管理权限</div>
                        <Checkbox.Group
                            options={superPerms}
                            value={selectedPerms.filter(p => superPerms.map(sp => sp.value).includes(p))}
                            onChange={handleSuperPermChange}
                        />
                    </div>
                )}

                {editingUser?.role !== 'supper_admin' && editingUser?.role !== 'super_admin' && (
                    <div style={{ marginTop: 16, color: '#999', fontSize: 12 }}>
                        注：非超级管理员无法分配高级管理权限。
                    </div>
                )}
            </Modal>
        </div>
    );
};

export default PermissionManager;
