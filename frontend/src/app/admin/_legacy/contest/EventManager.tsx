"use client";

import React, { useEffect, useState } from 'react';
import { Table, Card, Button, Tag, Space, Input, message, Popconfirm, Tooltip } from 'antd';
import { PlusOutlined, SearchOutlined, EditOutlined, TeamOutlined, DeleteOutlined, ReloadOutlined, SettingOutlined } from '@ant-design/icons';
import { useNavigate } from '@/app/admin/_legacy/router';
import axios from 'axios';
import dayjs from 'dayjs';
import { eventSignupPeriod, eventSignupCount } from '@/utils/eventSignup';
import ExternalBoardManagerModal from './ExternalBoardManagerModal';
import { contestTypeText } from '@/utils/contestDisplay';

const EventManager: React.FC = () => {
    const navigate = useNavigate();
    const [list, setList] = useState<any[]>([]);
    const [loading, setLoading] = useState(false);
    const [keyword, setKeyword] = useState('');
    const [configModalVisible, setConfigModalVisible] = useState(false);
    const [currentEvent, setCurrentEvent] = useState<any>(null);

    useEffect(() => {
        fetchData();
    }, []);

    const getEventStatus = (item: any) => {
        const now = dayjs();
  if (item.signupMode === 'roster' && now.isBefore(item.eventStart)) return { text: '名单已确定', color: 'blue', status: 'waiting', order: 3 };
  if (item.signupMode === 'roster') return now.isBefore(item.eventEnd) ? { text: '正在比赛中', color: 'red', status: 'ongoing', order: 4 } : { text: '已结束', color: 'default', status: 'ended', order: 5 };
        if (now.isBefore(item.signupStart)) return { text: '报名未开始', color: 'orange', status: 'pending', order: 2 };
        if (!item.signupEnd || now.isBefore(item.signupEnd)) return { text: '报名进行中', color: 'green', status: 'registering', order: 1 };
        if (now.isBefore(item.eventStart)) return { text: '等待开赛', color: 'blue', status: 'waiting', order: 3 };
        if (now.isBefore(item.eventEnd)) return { text: '正在比赛中', color: 'red', status: 'ongoing', order: 4 };
        return { text: '比赛已结束', color: 'default', status: 'ended', order: 5 };
    };

    const fetchData = async () => {
        setLoading(true);
        try {
            const res = await axios.get('/api/event/list'); 
            if (res.data.code === 200) {
                const eventList = res.data.data || [];
                
                eventList.sort((a: any, b: any) => {
                    const statusA = getEventStatus(a);
                    const statusB = getEventStatus(b);
                    
                    if (statusA.order !== statusB.order) {
                        return statusA.order - statusB.order;
                    }
                    
                    if (statusA.status === 'registering') {
                         return (a.signupEnd ? dayjs(a.signupEnd).valueOf() : Number.MAX_SAFE_INTEGER) - (b.signupEnd ? dayjs(b.signupEnd).valueOf() : Number.MAX_SAFE_INTEGER);
                    } else if (statusA.status === 'pending') {
                         return dayjs(a.signupStart).valueOf() - dayjs(b.signupStart).valueOf();
                    } else if (statusA.status === 'waiting') {
                         return dayjs(a.eventStart).valueOf() - dayjs(b.eventStart).valueOf();
                    } else if (statusA.status === 'ongoing') {
                         return dayjs(a.eventEnd).valueOf() - dayjs(b.eventEnd).valueOf();
                    } else {
                         return dayjs(b.eventEnd).valueOf() - dayjs(a.eventEnd).valueOf();
                    }
                });
                
                setList(eventList);
            }
        } finally {
            setLoading(false);
        }
    };

    const handleDelete = async (eventId: number) => {
        try {
            const userStr = localStorage.getItem('user_info');
            const operatorId = userStr ? JSON.parse(userStr).userId : null;
            
            const res = await axios.post('/api/event/delete', { eventId, operatorId });
            if (res.data.code === 200) {
                message.success('删除成功');
                fetchData();
            } else {
                message.error(res.data.msg);
            }
        } catch (e) {
            message.error('操作失败');
        }
    };

    const handleOpenConfig = (record: any) => {
        setCurrentEvent(record);
        setConfigModalVisible(true);
    };

    const filteredList = list.filter(item => item.name.toLowerCase().includes(keyword.toLowerCase()));

    const columns = [
        { title: 'ID', dataIndex: 'eventId', width: 60 },
        { title: '赛事名称', dataIndex: 'name', width: 200, ellipsis: true },
        { 
            title: '赛制', 
            dataIndex: 'type', 
            width: 190,
            render: (val: string, record: any) => {
                if (val === 'XCPC') return <Tag color="geekblue">{contestTypeText(val)}</Tag>;
                if (val === 'GPLT') return <Tag color="purple">{contestTypeText(val)}</Tag>;
                if (val === 'LANQIAO') return <Tag color="cyan">{contestTypeText(val)}</Tag>;
                return record.isTeam === 1 ? <Tag color="blue">团队赛</Tag> : <Tag color="orange">个人赛</Tag>;
            }
        },
        { 
            title: '状态', 
            width: 120,
            render: (_: any, record: any) => {
                const status = getEventStatus(record);
                return <Tag color={status.color}>{status.text}</Tag>;
            }
        },
        { 
            title: '报名', 
            width: 120,
            render: (_: any, record: any) => (
                <span>{eventSignupCount(record.signupCount,record.maxSignup)}</span>
            )
        },
        {
            title: '操作',
            key: 'action',
            fixed: 'right' as 'right',
            width: 320,
            render: (_: any, record: any) => (
                <Space>
                    <Tooltip title="基本信息编辑">
                        <Button 
                            type="primary" 
                            size="small" 
                            icon={<EditOutlined />} 
                            onClick={() => navigate(`/admin/contest/edit/${record.eventId}`)}
                        />
                    </Tooltip>
                    
                    <Tooltip title="管理本场比赛的多个外榜">
                        <Button 
                            size="small" 
                            style={{ borderColor: '#fa8c16', color: '#fa8c16' }}
                            icon={<SettingOutlined />}
                            onClick={() => handleOpenConfig(record)}
                        >
                            外榜管理
                        </Button>
                    </Tooltip>

                    <Tooltip title="报名名单管理">
                        <Button 
                            size="small" 
                            style={{ borderColor: '#52c41a', color: '#52c41a' }}
                            icon={<TeamOutlined />} 
                            onClick={() => navigate(`/admin/contest/signups/${record.eventId}`)}
                        >
                            名单
                        </Button>
                    </Tooltip>

                    <Popconfirm title="确定删除？" onConfirm={() => handleDelete(record.eventId)}>
                        <Button danger size="small" icon={<DeleteOutlined />} />
                    </Popconfirm>
                </Space>
            )
        }
    ];

    return (
        <Card title="比赛发布与管理" extra={
            <Space>
                <Button icon={<ReloadOutlined />} onClick={fetchData}>刷新</Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/admin/contest/publish')}>发布新赛事</Button>
            </Space>
        }>
            <div style={{ marginBottom: 16 }}>
                <Input 
                    placeholder="搜索赛事名称" 
                    prefix={<SearchOutlined />} 
                    style={{ width: 300 }} 
                    value={keyword}
                    onChange={e => setKeyword(e.target.value)}
                />
            </div>
            <Table 
                rowKey="eventId"
                columns={columns} 
                dataSource={filteredList} 
                loading={loading}
                scroll={{ x: 1200 }}
            />

            {currentEvent && (
                <ExternalBoardManagerModal
                    eventId={currentEvent.eventId}
                    eventName={currentEvent.name}
                    eventType={currentEvent.type}
                    open={configModalVisible}   
                    onCancel={() => {           
                        setConfigModalVisible(false);
                        setCurrentEvent(null);
                        fetchData();
                    }}
                />
            )}
        </Card>
    );
};

export default EventManager;
