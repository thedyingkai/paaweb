"use client";

import React, { useState, useEffect, useMemo, useRef } from 'react';
import { Form, Input, Button, Upload, message, Card, Radio, Modal, Select, Avatar, Spin, DatePicker, type UploadFile, type UploadProps } from 'antd';
import { MdEditor, type ExposeParam } from '@/app/components/content/SecureMarkdownEditor';
import { UploadOutlined, ArrowLeftOutlined, UserAddOutlined } from '@ant-design/icons';
import axios from 'axios';
import { userCardHtml, userSearchLabel, type UserSearchResult } from '@/types/user';
import { useNavigate, useParams } from '@/app/admin/_legacy/router';
import dayjs, { type Dayjs } from 'dayjs';

const debounce = <Args extends unknown[]>(func: (...args: Args) => void | Promise<void>, wait: number) => {
    let timeout: ReturnType<typeof setTimeout>;
    return (...args: Args) => {
        clearTimeout(timeout);
        timeout = setTimeout(() => {
            void func(...args);
        }, wait);
    };
};

interface AdminUserInfo {
  userId?: number | string;
  realName?: string;
  userName?: string;
}

type UserOption = UserSearchResult;

interface FileUploadResponse {
  code?: number;
  data?: number | string;
  msg?: string;
}

interface AttachmentFile {
  fileId: number | string;
  name: string;
  url: string;
}

interface NoticeDetailData {
  content?: string;
  createTime?: string;
  attachments?: AttachmentFile[];
  [key: string]: unknown;
}

interface ApiResponse<T> {
  code: number;
  data?: T;
  msg?: string;
}

interface NoticeFormValues {
  title: string;
  type: 'notice' | 'publicity' | 'news';
  publisherName: string;
  createTime?: Dayjs;
}

const NoticePublish: React.FC = () => {
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const { id } = useParams();
  const [content, setContent] = useState('');
  const [fileList, setFileList] = useState<UploadFile<FileUploadResponse>[]>([]);
  const [loading, setLoading] = useState(false);
  
  const editorRef = useRef<ExposeParam>(null);
  const [isUserModalOpen, setIsUserModalOpen] = useState(false);
  const [fetchingUser, setFetchingUser] = useState(false);
  const [userOptions, setUserOptions] = useState<UserOption[]>([]);

  const userStr = localStorage.getItem('user_info');
  const user = useMemo<AdminUserInfo | null>(() => {
    return userStr ? JSON.parse(userStr) : null;
  }, [userStr]);
  const defaultPublisherName = useMemo(() => {
    return user?.realName || user?.userName || '校队';
  }, [user]);

  useEffect(() => {
      if (!user || !user.userId) {
          message.warning('请先登录');
          navigate('/login');
          return;
      }

      if(id) {
          axios.get<ApiResponse<NoticeDetailData>>(`/api/notice/detail/${id}`).then(res => {
              if(res.data.code === 200) {
                  const data = res.data.data;
                  if (!data) return;
                  form.setFieldsValue({
                      ...data,
                      createTime: data.createTime ? dayjs(data.createTime) : dayjs()
                  });
                  setContent(data.content || '');
                  if(data.attachments) {
                      setFileList(data.attachments.map((f) => ({
                          uid: String(f.fileId),
                          name: f.name, 
                          status: 'done', 
                          url: `/api${f.url}`,
                          response: { data: f.fileId }
                      })));
                  }
              }
          });
      }
  }, [id, user, navigate, form]); 

  const handleUserSearch = useMemo(() => debounce(async (value: string) => {
      if (!value) return;
      setFetchingUser(true);
      try {
          const res = await axios.get<ApiResponse<UserOption[]>>(`/api/user/search?keyword=${value}`);
          if (res.data.code === 200) {
              setUserOptions(res.data.data || []);
          }
      } finally {
          setFetchingUser(false);
      }
  }, 500), []);

  const onSelectUser = (userId: string) => {
      const targetUser = userOptions.find(u => String(u.userId) === userId);
      if (targetUser) {
          const tag = userCardHtml(targetUser);
          
          editorRef.current?.insert(() => {
              return {
                  targetValue: tag,
                  select: false,
                  deviationStart: 0,
                  deviationEnd: 0
              };
          });

          setIsUserModalOpen(false);
          setUserOptions([]);
      }
  };

  const onUploadImg = async (files: File[], callback: (urls: string[]) => void) => {
      const res = await Promise.all(
          files.map((file) => {
              const formData = new FormData();
              formData.append('file', file);
              if (user?.userId) {
                  formData.append('uploaderId', String(user.userId));
              }

              return axios.post<FileUploadResponse>('/api/file/upload', formData, {
                  headers: {
                      'Content-Type': 'multipart/form-data',
                  },
              });
          })
      );

      const urls = res.map((item) => {
          if (item.data.code === 200) {
              const fileId = item.data.data;
              return `/api/file/image/${fileId}`;
          }
          return '';
      }).filter(url => url !== '');
      
      callback(urls);
  };

  const handlePublish = async (values: NoticeFormValues) => {
      if(!content) return message.error("请输入正文内容");
      setLoading(true);
      
      const validFileIds = fileList
          .filter((f): f is UploadFile<FileUploadResponse> & { response: { data: number | string } } => f.status === 'done' && !!f.response?.data)
          .map(f => f.response.data);

      const payload = {
          infoId: id, 
          ...values,
          createTime: values.createTime ? dayjs(values.createTime).format('YYYY-MM-DD HH:mm:ss') : undefined,
          publisherName: values.publisherName?.trim(),
          content,
          attachmentIds: validFileIds
      };

      try {
          const res = await axios.post<ApiResponse<string>>('/api/notice/publish', payload);
          if(res.data.code === 200) {
              message.success(id ? '修改成功' : '发布成功');
              navigate('/admin/notice');
          } else {
              message.error(res.data.msg);
          }
      } catch {
          message.error("请求失败，请检查网络");
      } finally {
          setLoading(false);
      }
  };

  const uploadProps: UploadProps<FileUploadResponse> = {
      name: 'file',
      action: '/api/file/upload',
      data: { uploaderId: user?.userId },
      beforeUpload: (file) => {
          const isLt50M = file.size / 1024 / 1024 < 50;
          if (!isLt50M) {
              message.error('文件大小不能超过 50MB!');
              return Upload.LIST_IGNORE;
          }
          return true;
      },
      onChange(info) {
          const newFileList = [...info.fileList];
          if (info.file.status === 'done') {
              message.success(`${info.file.name} 上传成功`);
          } else if (info.file.status === 'error') {
              message.error(`${info.file.name} 上传失败`);
          }
          setFileList(newFileList);
      }
  };

  return (
    <div style={{ maxWidth: 1200, margin: '20px auto', padding: '0 20px' }}>
      <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/admin/notice')} style={{ marginBottom: 16 }}>返回列表</Button>
      <Card title={id ? "编辑公告" : "发布新公告"}>
          <Form form={form} layout="vertical" onFinish={handlePublish} initialValues={{ type: 'notice', publisherName: defaultPublisherName, createTime: dayjs() }}>
              <Form.Item name="title" label="公告标题" rules={[{ required: true }]}>
                  <Input placeholder="请输入标题" size="large" />
              </Form.Item>
              
              <Form.Item name="publisherName" label="发布人" rules={[{ required: true, message: '请输入发布人' }, { max: 100, message: '发布人不能超过100字' }]}>
                  <Input placeholder="请输入发布人，例如：程序设计校队" size="large" />
              </Form.Item>

              <Form.Item name="createTime" label="发布时间" rules={[{ required: true, message: '请选择发布时间' }]}>
                  <DatePicker showTime format="YYYY-MM-DD HH:mm:ss" style={{ width: '100%' }} size="large" />
              </Form.Item>

              <Form.Item label="公告类型" required style={{ marginBottom: 24 }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                      <Form.Item name="type" noStyle rules={[{ required: true }]}>
                          <Radio.Group buttonStyle="solid">
                              <Radio.Button value="notice">通知</Radio.Button>
                              <Radio.Button value="publicity">公示</Radio.Button>
                              <Radio.Button value="news">新闻</Radio.Button>
                          </Radio.Group>
                      </Form.Item>
                      <Button icon={<UserAddOutlined />} onClick={() => setIsUserModalOpen(true)}>
                          插入用户名片
                      </Button>
                  </div>
              </Form.Item>

              <Form.Item label="正文内容" required>
                  <MdEditor 
                    ref={editorRef} 
                    modelValue={content} 
                    onChange={setContent} 
                    onUploadImg={onUploadImg} 
                    style={{ height: '500px' }} 
                  />
              </Form.Item>

              <Form.Item label="附件上传">
                  <Upload {...uploadProps} fileList={fileList}>
                      <Button icon={<UploadOutlined />}>点击上传附件</Button>
                  </Upload>
              </Form.Item>

              <Form.Item>
                  <Button type="primary" htmlType="submit" size="large" loading={loading} block>
                      {id ? "保存修改" : "立即发布"}
                  </Button>
              </Form.Item>
          </Form>

          <Modal title="插入用户名片" open={isUserModalOpen} onCancel={() => setIsUserModalOpen(false)} footer={null} destroyOnClose>
                <Select
                    showSearch
                    placeholder="输入昵称动态检索..."
                    filterOption={false}
                    onSearch={handleUserSearch}
                    onChange={onSelectUser}
                    notFoundContent={fetchingUser ? <Spin size="small" /> : null}
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
    </div>
  );
};

export default NoticePublish;
