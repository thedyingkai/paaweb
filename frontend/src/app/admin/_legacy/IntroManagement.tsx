"use client";

import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Card, Radio, Button, message, Modal, Select, Spin, Avatar, Input, Upload, List, Typography, Tooltip, Switch, Form, Table, Tag, Popconfirm, InputNumber, Space, Alert } from 'antd';
import type { UploadProps } from 'antd'; 

import { MdEditor, type ExposeParam } from '@/app/components/content/SecureMarkdownEditor';
import { SaveOutlined, UserAddOutlined, PlusOutlined, DeleteOutlined, UploadOutlined, PictureOutlined, EditOutlined, ArrowUpOutlined, ArrowDownOutlined, DownloadOutlined } from '@ant-design/icons';
import axios from 'axios';
import { userCardHtml, userSearchLabel } from '@/types/user';
import ImportPreview, { ImportPreviewData } from './components/ImportPreview';
import { downloadExcelTemplate, readExcelRows, rowsToTsv } from '@/utils/excelImport';
import { describeRequestError } from '@/utils/errorMessage';
import { memberRoles, memberRoleKey, currentAcademicTerm, academicTermDates, positionStatusLabels } from '@/lib/besti-members';

const MEMBER_IMPORT_HEADERS = ['组织', '职务', '姓名', '学校', '学号', '任职开始', '任职结束'];

const { Text } = Typography;

const debounce = <F extends (...args: any[]) => void>(func: F, wait: number) => {
    let timeout: ReturnType<typeof setTimeout> | null = null;
    return (...args: Parameters<F>) => {
        if (timeout) clearTimeout(timeout);
        timeout = setTimeout(() => func(...args), wait);
    };
};

interface ImageItem {
    uid: string;
    imageUrl: string;
    linkUrl?: string;
    title?: string;
    theme?: 'light' | 'dark';
    academicYear?: string;
    sortOrder?: number;
}

interface EditorImageUploadResponse {
    code: number;
    data?: number | string;
    msg?: string;
}

type IntroCategory = 'showcase_bg' | 'honors' | 'association' | 'rules' | 'association_member' | 'system_config';

const categoryTitles: Record<IntroCategory, string> = {
    showcase_bg: '首页背景轮播图',
    honors: '校队荣誉墙设置',
    association: '校队简介',
    rules: '基本规则',
    association_member: '校队成员',
    system_config: '是否开放注册',
};

const IntroManagement: React.FC<{ initialCategory?: IntroCategory }> = ({ initialCategory = 'showcase_bg' }) => {
    const category = initialCategory;
    const [content, setContent] = useState('');
    const [imageItems, setImageItems] = useState<ImageItem[]>([]); 
    const [systemConfig, setSystemConfig] = useState({ allow_register: true }); 
    const [loading, setLoading] = useState(false);
    
    const editorRef = useRef<ExposeParam>(null);
    const [isUserModalOpen, setIsUserModalOpen] = useState(false);
    const [fetching, setFetching] = useState(false);
    const [userOptions, setUserOptions] = useState<any[]>([]);
    const user = typeof window === 'undefined' ? {} : JSON.parse(localStorage.getItem('user_info') || '{}');
    const isListMode = ['honors', 'showcase_bg'].includes(category);
    const isConfigMode = category === 'system_config'; 
    
    // States for Association Member Mode
    const isMemberMode = category === 'association_member';
    const [memberTerms, setMemberTerms] = useState<string[]>([]);
    const [selectedMemberTerm, setSelectedMemberTerm] = useState<string>(currentAcademicTerm());
    const [memberList, setMemberList] = useState<any[]>([]);
    const [selectedDeptFilter, setSelectedDeptFilter] = useState<string>('all');
    const [isMemberModalOpen, setIsMemberModalOpen] = useState(false);
    const [editingMember, setEditingMember] = useState<any>(null);
    const [memberForm] = Form.useForm();
    const [isCustomTerm, setIsCustomTerm] = useState(false);
    const [tenureFilter, setTenureFilter] = useState('all');
    const [memberAvatarFileId, setMemberAvatarFileId] = useState<number | null>(null);
    const [memberAvatarUrl, setMemberAvatarUrl] = useState<string>('');
    const [isBatchImportModalOpen, setIsBatchImportModalOpen] = useState(false);
    const [batchImporting, setBatchImporting] = useState(false);
    const [isBatchCustomTerm, setIsBatchCustomTerm] = useState(false);
    const [batchImportForm] = Form.useForm();
    const [batchImportRows, setBatchImportRows] = useState<string[][]>([]);
    const [batchImportPreview, setBatchImportPreview] = useState<ImportPreviewData | null>(null);

    const fetchMemberTermsAndList = useCallback(async (targetTerm?: string) => {
        setLoading(true);
        try {
            const termsRes = await axios.get('/api/association-member/terms');
            let term = targetTerm || selectedMemberTerm;
            if (termsRes.data.code === 200 && termsRes.data.data.length > 0) {
                const termsList = termsRes.data.data;
                termsList.sort((a: string, b: string) => b.localeCompare(a));
                setMemberTerms(termsList);
                if (!targetTerm && !termsList.includes(selectedMemberTerm)) {
                    term = termsList[0];
                    setSelectedMemberTerm(term);
                }
            } else {
                setMemberTerms([currentAcademicTerm()]);
                term = currentAcademicTerm();
                setSelectedMemberTerm(term);
            }

            const listRes = await axios.get(`/api/association-member/list?term=${encodeURIComponent(term)}`);
            if (listRes.data.code === 200) {
                setMemberList(listRes.data.data || []);
            }
        } catch (e) {
            message.error('加载成员列表失败');
        } finally {
            setLoading(false);
        }
    }, [selectedMemberTerm]);

    useEffect(() => {
        setContent('');
        setImageItems([]);
        setSystemConfig({ allow_register: true });
        
        if (isMemberMode) {
            fetchMemberTermsAndList();
        } else {
            axios.get(`/api/intro/${category}`).then(res => {
                if (res.data.code === 200 && res.data.data) {
                    const rawContent = res.data.data.content || '';
                    
                    if (isListMode) {
                        try {
                            const parsed = JSON.parse(rawContent);
                            if (Array.isArray(parsed)) setImageItems(parsed);
                        } catch (e) { setImageItems([]); }
                        } else if (isConfigMode) {
                        try {
                            const parsed = JSON.parse(rawContent);
                            if (parsed && typeof parsed === 'object') setSystemConfig(parsed);
                        } catch (e) { setSystemConfig({ allow_register: true }); }
                    } else {
                        setContent(rawContent);
                    }
                } else if (isConfigMode) {
                    setSystemConfig({ allow_register: true });
                }
            });
        }
    }, [category, isListMode, isConfigMode, isMemberMode, fetchMemberTermsAndList]);

    const debounceSearch = useCallback(
        debounce(async (value: string) => {
            if (!value) return;
            setFetching(true);
            try {
                const res = await axios.get(`/api/user/search?keyword=${value}`);
                if (res.data.code === 200) {
                    setUserOptions(res.data.data);
                }
            } finally {
                setFetching(false);
            }
        }, 500),
        []
    );

    const handleSave = async () => {
        setLoading(true);
        let finalContent = content;
        if (isListMode) {
            finalContent = JSON.stringify(imageItems);
        } else if (isConfigMode) {
            finalContent = JSON.stringify(systemConfig);
        }

        try {
            const res = await axios.post('/api/intro/update', {
                category,
                content: finalContent,
                operatorId: user.userId
            });
            if (res.data.code === 200) message.success('保存成功');
            else message.error(res.data.msg || '保存失败');
        } catch (e) {
            message.error('请求异常');
        } finally {
            setLoading(false);
        }
    };

    const addImageItem = () => {
        setImageItems([...imageItems, { uid: Date.now().toString(), imageUrl: '', linkUrl: '', title: '', academicYear: undefined, sortOrder: 0 }]);
    };

    const removeImageItem = (index: number) => {
        const newItems = [...imageItems];
        newItems.splice(index, 1);
        setImageItems(newItems);
    };

    const updateImageItem = (index: number, field: keyof ImageItem, value: string) => {
        const newItems = [...imageItems];
        newItems[index] = { ...newItems[index], [field]: value };
        setImageItems(newItems);
    };

    const uploadEditorImages = async (files: File[], callback: (urls: string[]) => void) => {
        const results = await Promise.allSettled(files.map(async (file) => {
            if (!/\.(png|jpe?g|gif)$/i.test(file.name)) {
                throw new Error('仅支持 PNG、JPG、JPEG、GIF 图片');
            }
            if (file.size > 5 * 1024 * 1024) {
                throw new Error('图片大小不能超过 5MiB');
            }
            const body = new FormData();
            body.append('file', file);
            const response = await axios.post<EditorImageUploadResponse>('/api/file/upload', body, {
                withCredentials: true,
            });
            if (response.data.code !== 200) {
                throw new Error(response.data.msg || '图片上传失败');
            }
            const fileId = response.data.data;
            if ((typeof fileId === 'number' && !Number.isSafeInteger(fileId))
                || !/^[1-9]\d*$/.test(String(fileId ?? ''))) {
                throw new Error('服务器未返回有效的图片编号，请重试');
            }
            return `/api/file/image/${fileId}`;
        }));

        const urls: string[] = [];
        const failures: string[] = [];
        results.forEach((result, index) => {
            if (result.status === 'fulfilled') urls.push(result.value);
            else failures.push(`${files[index].name}：${describeRequestError(result.reason,
                result.reason instanceof Error ? result.reason.message : '图片上传失败')}`);
        });
        // Always release the editor's upload state and insert each successful image,
        // even when another file in the same batch failed.
        callback(urls);
        if (failures.length) message.error(failures.join('；'));
    };

    const getUploadProps = (index: number): UploadProps => ({
        name: 'file',
        action: '/api/file/upload',
        data: { uploaderId: user?.userId },
        showUploadList: false,
        beforeUpload: (file) => {
            const isLt50M = file.size / 1024 / 1024 < 50;
            if (!isLt50M) {
                message.error('图片大小不能超过 5MB!');
                return Upload.LIST_IGNORE;
            }
            return true;
        },
        onChange: (info) => {
            if (info.file.status === 'done') {
                if (info.file.response && info.file.response.code === 200) {
                    const fileId = info.file.response.data;
                    const imgUrl = `/api/file/image/${fileId}`;
                    updateImageItem(index, 'imageUrl', imgUrl);
                    message.success('图片上传成功');
                } else {
                    message.error('服务器返回错误');
                }
            } else if (info.file.status === 'error') {
                message.error('上传失败');
            }
        }
    });

    const onSelectUser = (userId: string) => {
        const userSelected = userOptions.find(u => String(u.userId) === userId);
        if (userSelected) {
            const tag = userCardHtml(userSelected);
            editorRef.current?.insert(() => ({ targetValue: tag, select: false, deviationStart: 0, deviationEnd: 0 }));
            setIsUserModalOpen(false);
            setUserOptions([]);
        }
    };

    // Association Member Action Handlers
    const handleMemberTermChange = async (term: string) => {
        setSelectedMemberTerm(term);
        setLoading(true);
        try {
            const res = await axios.get(`/api/association-member/list?term=${encodeURIComponent(term)}`);
            if (res.data.code === 200) {
                setMemberList(res.data.data || []);
            }
        } catch (e) {
            message.error('加载该届成员列表失败');
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteMember = async (memberId: number) => {
        try {
            const res = await axios.post(`/api/association-member/delete/${memberId}`);
            if (res.data.code === 200) {
                message.success('删除成功');
                fetchMemberTermsAndList();
            } else {
                message.error(res.data.msg || '删除失败');
            }
        } catch (e) {
            message.error('删除请求异常');
        }
    };

    const openAddMemberModal = () => {
        setEditingMember(null);
        setIsCustomTerm(false);
        setMemberAvatarFileId(null);
        setMemberAvatarUrl('');
        memberForm.resetFields();
        memberForm.setFieldsValue({
            term: selectedMemberTerm,
            roleKey: 'science-club',
            ...academicTermDates(selectedMemberTerm),
            sortOrder: 0
        });
        setIsMemberModalOpen(true);
    };

    const openEditMemberModal = (record: any) => {
        setEditingMember(record);
        
        const standardTerms = Array.from(new Set([currentAcademicTerm(), ...memberTerms]));
        const isCustomT = !standardTerms.includes(record.term);
        setIsCustomTerm(isCustomT);

        setMemberAvatarFileId(record.avatarFileId || null);
        setMemberAvatarUrl(record.avatarUrl || '');

        memberForm.setFieldsValue({
            term: isCustomT ? 'custom' : record.term,
            customTerm: isCustomT ? record.term : '',
            roleKey: memberRoleKey(record.department, record.position),
            startDate: record.startDate,
            endDate: record.endDate,
            userId: record.userId ? String(record.userId) : undefined,
            realName: record.realName || '',
            introduction: record.introduction || '',
            sortOrder: record.sortOrder
        });

        setIsMemberModalOpen(true);
    };

    const getMemberAvatarUploadProps = () => ({
        name: 'file',
        action: '/api/file/upload',
        data: { uploaderId: user?.userId },
        showUploadList: false,
        beforeUpload: (file: any) => {
            const isLt5M = file.size / 1024 / 1024 < 5;
            if (!isLt5M) {
                message.error('图片大小不能超过 5MB!');
                return Upload.LIST_IGNORE;
            }
            return true;
        },
        onChange: (info: any) => {
            if (info.file.status === 'done') {
                if (info.file.response && info.file.response.code === 200) {
                    const fileId = info.file.response.data;
                    setMemberAvatarFileId(fileId);
                    setMemberAvatarUrl(`/api/file/image/${fileId}`);
                    message.success('图片上传成功');
                } else {
                    message.error('服务器返回错误');
                }
            } else if (info.file.status === 'error') {
                message.error('上传失败');
            }
        }
    });
    const filteredMemberList = memberList
        .filter(m => selectedDeptFilter === 'all' || memberRoleKey(m.department, m.position) === selectedDeptFilter)
        .filter(m => tenureFilter === 'all' || m.positionStatus === tenureFilter)
        .sort((a, b) => a.sortOrder - b.sortOrder);

    const handleSwap = async (id1: number, id2: number) => {
        setLoading(true);
        try {
            const res = await axios.post(`/api/association-member/swap/${id1}/${id2}`);
            if (res.data.code === 200) {
                message.success('移动成功');
                fetchMemberTermsAndList();
            } else {
                message.error(res.data.msg || '移动失败');
            }
        } catch (e) {
            message.error('移动排序请求异常');
        } finally {
            setLoading(false);
        }
    };

    const handleSaveMember = async () => {
        try {
            const values = await memberForm.validateFields();
            
            let term = values.term;
            if (term === 'custom') {
                term = values.customTerm;
                if (!term || !term.endsWith('学年')) {
                    message.error('自定义学年必须以“学年”结尾，例如: 2025-2026学年');
                    return;
                }
            }
            
            const role = memberRoles.find(role => role.value === values.roleKey);
            if (!role) return;

            const data: any = {
                term,
                department: role.organization,
                position: role.title,
                startDate: values.startDate,
                endDate: values.endDate,
                userId: values.userId || null,
                realName: values.realName || '',
                schoolName: editingMember?.schoolName || null,
                numberId: editingMember?.numberId || null,
                avatarFileId: memberAvatarFileId || null,
                introduction: values.introduction || '',
                sortOrder: editingMember ? editingMember.sortOrder : 0
            };

            if (editingMember) {
                data.memberId = editingMember.memberId;
                const res = await axios.post('/api/association-member/update', data);
                if (res.data.code === 200) {
                    message.success('修改成功');
                    setIsMemberModalOpen(false);
                    fetchMemberTermsAndList(term);
                } else {
                    message.error(res.data.msg || '修改失败');
                }
            } else {
                const res = await axios.post('/api/association-member/add', data);
                if (res.data.code === 200) {
                    message.success('添加成功');
                    setIsMemberModalOpen(false);
                    fetchMemberTermsAndList(term);
                } else {
                    message.error(res.data.msg || '添加失败');
                }
            }
        } catch (e) {
            console.error('保存成员表单校验失败', e);
        }
    };

    const openBatchImportModal = () => {
        setIsBatchCustomTerm(false);
        batchImportForm.setFieldsValue({
            term: selectedMemberTerm,
            customTerm: ''
        });
        setBatchImportRows([]);
        setBatchImportPreview(null);
        setIsBatchImportModalOpen(true);
    };

    const handleBatchImportMembers = async () => {
        try {
            const values = await batchImportForm.validateFields();
            let term = values.term;
            if (term === 'custom') {
                term = values.customTerm;
                if (!term || !term.endsWith('学年')) {
                    message.error('自定义学年必须以“学年”结尾，例如：2025-2026学年');
                    return;
                }
            }
            if (!batchImportRows.length) {
                message.warning('请先上传填写好的 Excel 文件');
                return;
            }
            setBatchImporting(true);
            const confirming = Boolean(batchImportPreview);
            const res = await axios.post('/api/association-member/batch-import', {
                term,
                content: rowsToTsv(batchImportRows),
                preview: !confirming,
            });
            if (res.data.code === 200) {
                const data = res.data.data || {};
                if (!confirming) {
                    setBatchImportPreview(data);
                    return;
                }
                message.success(`导入完成：新增 ${data.imported || 0} 人，更新 ${data.updated || 0} 人，无账号 ${data.noAccount || 0} 人`);
                if (Array.isArray(data.errors) && data.errors.length > 0) {
                    Modal.warning({
                        title: '部分行未导入',
                        content: (
                            <div style={{ maxHeight: 260, overflow: 'auto', whiteSpace: 'pre-wrap' }}>
                                {data.errors.join('\n')}
                            </div>
                        )
                    });
                }
                setIsBatchImportModalOpen(false);
                setSelectedMemberTerm(term);
                fetchMemberTermsAndList(term);
            } else {
                message.error(res.data.msg || '批量导入失败');
            }
        } catch (e) {
            console.error('批量导入失败', e);
        } finally {
            setBatchImporting(false);
        }
    };

    const selectMemberImportFile = async (file: File) => {
        try {
            const rows = await readExcelRows(file, MEMBER_IMPORT_HEADERS);
            if (!rows.length) {
                message.warning('Excel 中没有待导入数据');
                return false;
            }
            setBatchImportRows(rows);
            setBatchImportPreview(null);
            message.success(`已读取 ${rows.length} 条数据，请上传校验`);
        } catch (error) {
            message.error(error instanceof Error ? error.message : 'Excel 读取失败');
        }
        return false;
    };

    const closeBatchImport = () => {
        setIsBatchImportModalOpen(false);
        setBatchImportRows([]);
        setBatchImportPreview(null);
    };

    return (
        <Card
            className="admin-sticky-save-card"
            title={categoryTitles[category]}
            extra={
                !isMemberMode && (
                    <Button type="primary" icon={<SaveOutlined />} onClick={handleSave} loading={loading}>
                        发布修改
                    </Button>
                )
            }
        >
            <div style={{ marginBottom: 20, display: 'flex', justifyContent: 'flex-end', flexWrap: 'wrap', gap: 10 }}>
                {!isListMode && !isConfigMode && !isMemberMode && (
                    <Button icon={<UserAddOutlined />} onClick={() => setIsUserModalOpen(true)}>添加用户名片</Button>
                )}

                {isMemberMode && (
                    <Space>
                        <Button icon={<UploadOutlined />} onClick={openBatchImportModal}>
                            批量导入
                        </Button>
                        <Button type="primary" icon={<PlusOutlined />} onClick={openAddMemberModal}>
                            添加成员
                        </Button>
                    </Space>
                )}
            </div>

            {isMemberMode ? (
                <div>
                    <Alert style={{ marginBottom: 20 }} type="info" showIcon title="学生身份认证与校队资格分开管理" description="普通参赛者可以注册和报名校赛。确认校队资格时，请选择 ACM 校队的队长或队员职务、设置任期并绑定本人站内账号；仅学生身份已认证且在任期内的绑定账号可以进入内部交流区。联盟负责人、自填职务和未绑定账号的展示资料不会自动授予校队资格。解绑、移出名单或任期结束后，内部交流权限随即收回。" />
                    <div style={{ marginBottom: 16, display: 'flex', alignItems: 'center', gap: 24, flexWrap: 'wrap' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span>当前查看学年：</span>
                            <Select
                                value={selectedMemberTerm}
                                onChange={handleMemberTermChange}
                                style={{ width: 160 }}
                                options={memberTerms.map(t => ({ label: t, value: t }))}
                            />
                        </div>
                        
                        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span>职务筛选：</span>
                            <Radio.Group 
                                value={selectedDeptFilter} 
                                onChange={e => setSelectedDeptFilter(e.target.value)}
                                buttonStyle="solid"
                                style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}
                            >
                                {[{ value: 'all', label: '全部职务' }, ...memberRoles].map(role => (
                                    <Radio.Button key={role.value} value={role.value}>{role.label}</Radio.Button>
                                ))}
                            </Radio.Group>
                        </div>
                    </div>
                    
                    <Space wrap style={{ marginBottom: 16 }}>
                        <span>任职状态：</span>
                        <Select value={tenureFilter} onChange={setTenureFilter} style={{ width: 160 }} options={[
                            { value: 'all', label: '全部任期' },
                            ...Object.entries(positionStatusLabels).map(([value, label]) => ({ value, label })),
                        ]} />
                        <Text type="secondary">结束日期次日起自动显示“前”职务，历史记录保留。</Text>
                    </Space>
                    <Table
                        dataSource={filteredMemberList}
                        rowKey="memberId"
                        loading={loading}
                        pagination={false}
                        columns={[
                            {
                                title: '头像',
                                dataIndex: 'avatarUrl',
                                key: 'avatarUrl',
                                width: 80,
                                render: (text) => (
                                    <Avatar src={text || '/avatar-default.svg'} />
                                )
                            },
                            {
                                title: '姓名',
                                dataIndex: 'realName',
                                key: 'realName',
                                render: (text, record) => (
                                    <Space>
                                        <span>{text}</span>
                                        {record.userId && <Tag color="blue">站内用户</Tag>}
                                    </Space>
                                )
                            },
                            {
                                title: '组织',
                                dataIndex: 'department',
                                key: 'department',
                            },
                            {
                                title: '学校',
                                dataIndex: 'schoolName',
                                key: 'schoolName',
                                render: (text) => text || '-'
                            },
                            {
                                title: '学号',
                                dataIndex: 'numberId',
                                key: 'numberId',
                                render: (text) => text || '-'
                            },
                            {
                                title: '职务',
                                dataIndex: 'position',
                                key: 'position',
                                render: (text, record) => <Tag color={record.positionStatus === 'former' ? 'default' : 'geekblue'}>{record.displayPosition || text}</Tag>
                            },
                            {
                                title: '任期',
                                render: (_, record) => <><div>{record.startDate} — {record.endDate}</div><Text type="secondary">{positionStatusLabels[record.positionStatus]}</Text></>
                            },
                            {
                                title: '个人简介 / 个性签名',
                                dataIndex: 'introduction',
                                key: 'introduction',
                                render: (text) => text || <span style={{color: '#ccc', fontStyle: 'italic'}}>暂无</span>
                            },
                            {
                                title: '排序',
                                key: 'sort',
                                width: 100,
                                render: (_, record, idx) => {
                                    const isFirst = idx === 0 || filteredMemberList[idx - 1].department !== record.department;
                                    const isLast = idx === filteredMemberList.length - 1 || filteredMemberList[idx + 1].department !== record.department;
                                    return (
                                        <Space>
                                            <Button 
                                                icon={<ArrowUpOutlined />} 
                                                disabled={isFirst} 
                                                onClick={() => handleSwap(record.memberId, filteredMemberList[idx - 1].memberId)} 
                                                size="small"
                                            />
                                            <Button 
                                                icon={<ArrowDownOutlined />} 
                                                disabled={isLast} 
                                                onClick={() => handleSwap(record.memberId, filteredMemberList[idx + 1].memberId)} 
                                                size="small"
                                            />
                                        </Space>
                                    );
                                }
                            },
                            {
                                title: '操作',
                                key: 'action',
                                width: 150,
                                render: (_, record) => (
                                    <Space size="middle">
                                        <Button type="link" icon={<EditOutlined />} onClick={() => openEditMemberModal(record)}>编辑</Button>
                                        <Popconfirm
                                            title="确定要删除该成员吗？"
                                            onConfirm={() => handleDeleteMember(record.memberId)}
                                            okText="确定"
                                            cancelText="取消"
                                        >
                                            <Button type="link" danger icon={<DeleteOutlined />}>删除</Button>
                                        </Popconfirm>
                                    </Space>
                                )
                            }
                        ]}
                    />

                    {/* Member Add/Edit Modal */}
                    <Modal
                        title={editingMember ? '修改成员职务' : '添加成员职务'}
                        open={isMemberModalOpen}
                        onOk={handleSaveMember}
                        onCancel={() => setIsMemberModalOpen(false)}
                        width={600}
                        destroyOnClose
                    >
                        <Form form={memberForm} layout="vertical">
                            <Form.Item label="学年届别" required>
                                <Input.Group compact>
                                    <Form.Item
                                        name="term"
                                        noStyle
                                        rules={[{ required: true, message: '请选择学年' }]}
                                    >
                                        <Select 
                                            style={{ width: '40%' }} 
                                            onChange={(val) => {
                                                setIsCustomTerm(val === 'custom');
                                                if (val !== 'custom') memberForm.setFieldsValue(academicTermDates(val));
                                            }}
                                        >
                                            {Array.from(new Set([currentAcademicTerm(), ...memberTerms, '2025-2026学年'])).map(term => <Select.Option key={term} value={term}>{term}</Select.Option>)}
                                            <Select.Option value="2024-2025学年">2024-2025学年</Select.Option>
                                            <Select.Option value="2023-2024学年">2023-2024学年</Select.Option>
                                            <Select.Option value="custom">自定义输入</Select.Option>
                                        </Select>
                                    </Form.Item>
                                    {isCustomTerm && (
                                        <Form.Item
                                            name="customTerm"
                                            noStyle
                                            rules={[{ required: true, message: '请输入自定义学年（如 2025-2026学年）' }]}
                                        >
                                            <Input style={{ width: '60%' }} placeholder="格式如：2025-2026学年" onBlur={event => memberForm.setFieldsValue(academicTermDates(event.target.value))} />
                                        </Form.Item>
                                    )}
                                </Input.Group>
                            </Form.Item>

                            <Form.Item label="职务" name="roleKey" rules={[{ required: true, message: '请选择职务' }]}>
                                <Select options={memberRoles} placeholder="选择组织及职务" />
                            </Form.Item>
                            <Space align="start">
                                <Form.Item label="任职开始" name="startDate" rules={[{ required: true, message: '请选择开始日期' }]}>
                                    <Input type="date" />
                                </Form.Item>
                                <Form.Item label="任职结束" name="endDate" dependencies={['startDate']} rules={[
                                    { required: true, message: '请选择结束日期' },
                                    ({ getFieldValue }) => ({ validator(_, value) {
                                        return !value || !getFieldValue('startDate') || value >= getFieldValue('startDate')
                                            ? Promise.resolve() : Promise.reject(new Error('结束日期不能早于开始日期'));
                                    } }),
                                ]}>
                                    <Input type="date" />
                                </Form.Item>
                            </Space>
                            <p style={{ color: '#777' }}>任期结束次日起，职务自动显示为“前××”。</p>

                            <Form.Item label="绑定站内账号" name="userId" extra="校队交流资格必须绑定本人账号；留空仅展示履历。可清空绑定以收回该任期的交流资格。">
                                <Select
                                    showSearch
                                    placeholder="输入昵称动态检索并绑定站内账号..."
                                    filterOption={false}
                                    onSearch={debounceSearch}
                                    allowClear
                                    notFoundContent={fetching ? <Spin size="small" /> : null}
                                >
                                    {userOptions.map(u => (
                                        <Select.Option key={u.userId} value={String(u.userId)}>
                                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                                <Avatar size="small" src={u.avatarUrl} />
                                                <span>{userSearchLabel(u)}</span>
                                            </div>
                                        </Select.Option>
                                    ))}
                                </Select>
                            </Form.Item>

                            <div style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, marginBottom: 16 }}>
                                <div style={{ fontWeight: 500, marginBottom: 12, fontSize: 13, color: '#595959' }}>
                                    填写以下覆盖信息（若上面绑定了站内用户且以下字段留空，则默认使用站内用户资料）：
                                </div>
                                <Form.Item label="姓名 / 覆盖姓名" name="realName">
                                    <Input placeholder="输入成员姓名" />
                                </Form.Item>

                                <Form.Item label="个性签名 / 覆盖签名" name="introduction">
                                    <Input.TextArea placeholder="输入个人简介或个性签名" rows={2} />
                                </Form.Item>

                                <Form.Item label="头像 / 覆盖头像">
                                    <Space size="middle" align="center">
                                        <Avatar 
                                            size={64} 
                                            src={memberAvatarUrl || '/avatar-default.svg'} 
                                            style={{ border: '1px solid #d9d9d9' }}
                                        />
                                        <Upload {...getMemberAvatarUploadProps()}>
                                            <Button icon={<UploadOutlined />}>上传新头像</Button>
                                        </Upload>
                                        {memberAvatarUrl && (
                                            <Button danger type="text" size="small" onClick={() => { setMemberAvatarFileId(null); setMemberAvatarUrl(''); }}>
                                                清除上传头像
                                            </Button>
                                        )}
                                    </Space>
                                </Form.Item>
                            </div>
                        </Form>
                    </Modal>

                    <Modal
                        title="批量导入校队成员"
                        open={isBatchImportModalOpen}
                        onOk={handleBatchImportMembers}
                        onCancel={closeBatchImport}
                        okText={batchImportPreview ? '确认导入可用数据' : '上传并校验'}
                        confirmLoading={batchImporting}
                        width={760}
                        destroyOnClose
                    >
                        <Form form={batchImportForm} layout="vertical">
                            <Form.Item label="导入学年" required>
                                <Input.Group compact>
                                    <Form.Item
                                        name="term"
                                        noStyle
                                        rules={[{ required: true, message: '请选择导入学年' }]}
                                    >
                                        <Select
                                            showSearch
                                            style={{ width: isBatchCustomTerm ? '42%' : '100%' }}
                                            placeholder="选择学年"
                                            onChange={(value) => setIsBatchCustomTerm(value === 'custom')}
                                            options={[
                                                ...Array.from(new Set([...memberTerms, selectedMemberTerm, '2025-2026学年', '2024-2025学年', '2023-2024学年']))
                                                    .filter(Boolean)
                                                    .map(t => ({ label: t, value: t })),
                                                { label: '新增学年', value: 'custom' }
                                            ]}
                                        />
                                    </Form.Item>
                                    {isBatchCustomTerm && (
                                        <Form.Item
                                            name="customTerm"
                                            noStyle
                                            rules={[{ required: true, message: '请输入自定义学年' }]}
                                        >
                                            <Input style={{ width: '58%' }} placeholder="例如：2026-2027学年" />
                                        </Form.Item>
                                    )}
                                </Input.Group>
                            </Form.Item>
                            <Form.Item label="导入文件" extra="每行 7 列：组织、职务、姓名、学校、学号、任职开始、任职结束（YYYY-MM-DD）。日期留空时按学年 9 月 1 日至次年 8 月 31 日处理。校队队长、队员绑定已认证账号后，在任期内开放内部交流；其他职务不授予校队资格，也不授予管理权限。">
                                <Space wrap>
                                    <Button icon={<DownloadOutlined />} onClick={() => downloadExcelTemplate({ fileName: '校队成员导入模板.xlsx', headers: MEMBER_IMPORT_HEADERS, examples: [['科学之光技术联盟', '社团负责人', '示例同学', '北京电子科技学院', '20260001', '2026-09-01', '2027-08-31'], ['科学之光技术联盟', '主要负责人', '示例同学', '北京电子科技学院', '20260002', '2026-09-01', '2027-08-31'], ['计算机工程师联盟', '负责人', '示例同学', '北京电子科技学院', '20260003', '2026-09-01', '2027-08-31'], ['ACM 校队', '校队队长', '示例同学', '北京电子科技学院', '20260004', '2026-09-01', '2027-08-31'], ['ACM 校队', '队员', '示例同学', '北京电子科技学院', '20260005', '2026-09-01', '2027-08-31']] })}>下载 Excel 模板</Button>
                                    <Upload accept=".xlsx,.xls" maxCount={1} beforeUpload={(file) => selectMemberImportFile(file as File)} onRemove={() => { setBatchImportRows([]); setBatchImportPreview(null); }}>
                                        <Button icon={<UploadOutlined />}>选择填写好的 Excel</Button>
                                    </Upload>
                                </Space>
                            </Form.Item>
                            {batchImportRows.length > 0 && <div style={{ color: '#666' }}>已读取 {batchImportRows.length} 条数据</div>}
                            {batchImportPreview && <ImportPreview data={batchImportPreview} />}
                        </Form>
                    </Modal>
                </div>
            ) : isConfigMode ? (
                <div style={{ maxWidth: 600, margin: '40px auto' }}>
                    <Form layout="horizontal" labelCol={{ span: 8 }} wrapperCol={{ span: 16 }}>
                        <Form.Item label="开放用户注册" tooltip="关闭后，登录页将隐藏注册入口，且禁止新用户注册">
                            <Switch 
                                checked={systemConfig.allow_register} 
                                onChange={checked => setSystemConfig({ ...systemConfig, allow_register: checked })} 
                                checkedChildren="开启" 
                                unCheckedChildren="关闭" 
                            />
                        </Form.Item>
                        <div style={{ color: '#999', textAlign: 'center', marginTop: 20 }}>
                            <Text type="secondary">更多系统配置项开发中...</Text>
                        </div>
                    </Form>
                </div>
            ) : isListMode ? (
                <div style={{ maxWidth: 800, margin: '0 auto' }}>
                    <div style={{ marginBottom: 16, color: '#666' }}>
                        <Text type="secondary">
                            {category === 'showcase_bg'
                                ? '提示：请上传首屏背景比赛/外出合照图片。多张图片将在首屏自动淡入淡出轮播展示。'
                                : '提示：请上传获奖证书或奖杯照片。图片将以网格形式展示，标题将显示在图片下方。'
                            }
                        </Text>
                    </div>
                    
                    <List
                        dataSource={imageItems}
                        rowKey="uid"
                        renderItem={(item, index) => (
                            <List.Item>
                                <Card style={{ width: '100%', background: '#f9f9f9' }} bodyStyle={{ padding: 16 }}>
                                    <div style={{ display: 'flex', gap: 20, alignItems: 'flex-start' }}>
                                        <div style={{ width: 240, flexShrink: 0 }}>
                                            <div style={{ height: 120, background: '#eee', borderRadius: 4, overflow: 'hidden', display: 'flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8, border: '1px solid #d9d9d9' }}>
                                                {item.imageUrl ? (
                                                    <img src={item.imageUrl} alt="preview" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                                                ) : (
                                                    <PictureOutlined style={{ fontSize: 32, color: '#ccc' }} />
                                                )}
                                            </div>
                                            <Upload {...getUploadProps(index)}>
                                                <Button icon={<UploadOutlined />} block>上传图片</Button>
                                            </Upload>
                                        </div>

                                        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 12 }}>
                                            <Input 
                                                prefix={<PictureOutlined style={{color:'#bfbfbf'}}/>}
                                                placeholder="图片地址 (上传后自动生成)" 
                                                value={item.imageUrl} 
                                                onChange={e => updateImageItem(index, 'imageUrl', e.target.value)} 
                                            />
                                            <Input 
                                                placeholder={category === 'showcase_bg' ? "标题/备注 (仅用于后台区分)" : "荣誉名称 (将显示在图片下方)"}
                                                value={item.title} 
                                                onChange={e => updateImageItem(index, 'title', e.target.value)} 
                                            />
                                            {category === 'honors' && (
                                                <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                                                    <Select
                                                        allowClear
                                                        placeholder="绑定学年 (可选，如 2025-2026)"
                                                        value={item.academicYear ?? undefined}
                                                        onChange={(val: string | undefined) => {
                                                            const newItems = [...imageItems];
                                                            newItems[index] = { ...newItems[index], academicYear: val };
                                                            setImageItems(newItems);
                                                        }}
                                                        style={{ flex: 1, minWidth: 200 }}
                                                        options={[
                                                            { value: '2025-2026', label: '2025-2026学年' },
                                                            { value: '2024-2025', label: '2024-2025学年' },
                                                            { value: '2023-2024', label: '2023-2024学年' },
                                                            { value: '2022-2023', label: '2022-2023学年' },
                                                            { value: '2021-2022', label: '2021-2022学年' },
                                                        ]}
                                                    />
                                                    <InputNumber
                                                        min={0}
                                                        placeholder="排序权重"
                                                        value={item.sortOrder ?? 0}
                                                        onChange={(val) => {
                                                            const newItems = [...imageItems];
                                                            newItems[index] = { ...newItems[index], sortOrder: val ?? 0 };
                                                            setImageItems(newItems);
                                                        }}
                                                        style={{ width: 110 }}
                                                        addonBefore="排序"
                                                    />
                                                </div>
                                            )}
                                            {category === 'showcase_bg' && (
                                                <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                                                    <span style={{ fontSize: 13, color: '#595959', flexShrink: 0 }}>前台文字与图标颜色：</span>
                                                    <Select
                                                        value={item.theme || 'dark'}
                                                        onChange={val => updateImageItem(index, 'theme', val)}
                                                        style={{ width: '100%' }}
                                                        options={[
                                                            { label: '白色文字/图标（适合深色/彩色背景）', value: 'dark' },
                                                            { label: '黑色文字/图标（适合纯白/浅色背景）', value: 'light' }
                                                        ]}
                                                    />
                                                </div>
                                            )}
                                        </div>

                                        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', height: '100%' }}>
                                            <Tooltip title="删除该项">
                                                <Button danger type="text" icon={<DeleteOutlined />} onClick={() => removeImageItem(index)} />
                                            </Tooltip>
                                        </div>
                                    </div>
                                </Card>
                            </List.Item>
                        )}
                    />
                    
                    <Button type="dashed" block style={{ height: 50, marginTop: 16 }} onClick={addImageItem} icon={<PlusOutlined />}>
                        {category === 'showcase_bg' ? '添加轮播项' : '添加荣誉项'}
                    </Button>
                </div>
            ) : (
                <MdEditor ref={editorRef} modelValue={content} onChange={setContent} onUploadImg={uploadEditorImages} style={{ height: 'calc(100vh - 320px)' }} />
            )}

            <Modal title="动态搜索用户" open={isUserModalOpen} onCancel={() => setIsUserModalOpen(false)} footer={null} destroyOnClose>
                <Select
                    showSearch
                    placeholder="输入昵称动态检索..."
                    filterOption={false}
                    onSearch={debounceSearch}
                    onChange={onSelectUser}
                    notFoundContent={fetching ? <Spin size="small" /> : null}
                    style={{ width: '100%' }}
                >
                    {userOptions.map(u => (
                        <Select.Option key={u.userId} value={String(u.userId)}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                                <Avatar size="small" src={u.avatarUrl} />
                                <span>{userSearchLabel(u)}</span>
                            </div>
                        </Select.Option>
                    ))}
                </Select>
            </Modal>
        </Card>
    );
};

export default IntroManagement;
