"use client";

/* eslint-disable react-hooks/set-state-in-effect */

import {
  Alert,
  Avatar,
  Button,
  Col,
  Divider,
  Form,
  Image,
  Input,
  Modal,
  Row,
  Segmented,
  Select,
  Tabs,
  Upload,
  message
} from 'antd';
import ImgCrop from 'antd-img-crop';
import {
  CameraOutlined,
  CheckCircleFilled,
  ExclamationCircleOutlined,
  GlobalOutlined,
  IdcardOutlined,
  InfoCircleOutlined,
  LockOutlined,
  MailOutlined,
  SafetyCertificateOutlined,
  UploadOutlined,
  UserOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { md5 } from 'js-md5';
import { useEffect, useState } from 'react';
import { refreshAuth } from '@/utils/auth';
import { describeRequestError } from '@/utils/errorMessage';

interface UserData {
  userId: string | number;
  userName?: string;
  userAccount?: string;
  realName?: string;
  numberId?: string;
  email?: string;
  phone?: string;
  avatarUrl?: string;
  avatarFileId?: string | number;
  idPhotoUrl?: string;
  idPhotoFileId?: string | number;
  idPhotoUpdatedAt?: string;
  accountStatus?: string;
  eduBg?: string;
  universityId?: number;
  schoolId?: number;
  majorId?: number;
  gradeYear?: string;
  className?: string;
  isEnrolledStu?: number;
  introduce?: string;
  newcoderId?: string;
  luoguId?: string;
  codeforcesId?: string;
  atcoderId?: string;
  cwnuojId?: string;
  role?: string;
}

interface DictionaryItem {
  universityId?: number;
  universityName?: string;
  schoolId?: number;
  schoolName?: string;
  majorId?: number;
  majorName?: string;
}

interface EditProfileModalProps {
  open: boolean;
  onCancel: () => void;
  onSuccess: () => void;
  userData: UserData;
}

type SecurityMode = 'password' | 'email' | 'account';
type EmailIdentityMethod = 'password' | 'email';

const EditProfileModal = ({ open, onCancel, onSuccess, userData }: EditProfileModalProps) => {
  const [form] = Form.useForm();
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('profile');
  const [securityMode, setSecurityMode] = useState<SecurityMode>('password');
  const [emailIdentityMethod, setEmailIdentityMethod] = useState<EmailIdentityMethod>('password');
  const [avatarUrl, setAvatarUrl] = useState(userData.avatarUrl);
  const [avatarFileId, setAvatarFileId] = useState<string | number | undefined>(userData.avatarFileId);
  const [idPhotoUrl, setIdPhotoUrl] = useState(userData.idPhotoUrl);
  const [idPhotoUploading, setIdPhotoUploading] = useState(false);
  const [authFileId, setAuthFileId] = useState<string | number | null>(null);
  const [authFileReady, setAuthFileReady] = useState(false);
  const [authUploading, setAuthUploading] = useState(false);
  const [authLoading, setAuthLoading] = useState(false);
  const [revokeLoading, setRevokeLoading] = useState(false);
  const [countdownOld, setCountdownOld] = useState(0);
  const [countdownNew, setCountdownNew] = useState(0);
  const [universities, setUniversities] = useState<DictionaryItem[]>([]);
  const [schools, setSchools] = useState<DictionaryItem[]>([]);
  const [majors, setMajors] = useState<DictionaryItem[]>([]);
  const [eduBg, setEduBg] = useState(userData.eduBg || '');

  useEffect(() => {
    axios.get<{ code: number; data?: DictionaryItem[] }>('/api/common/universities').then(res => {
      if (res.data.code === 200) setUniversities(res.data.data || []);
    });
  }, []);

  useEffect(() => {
    if (countdownOld <= 0) return;
    const timer = window.setTimeout(() => setCountdownOld(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdownOld]);

  useEffect(() => {
    if (countdownNew <= 0) return;
    const timer = window.setTimeout(() => setCountdownNew(value => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdownNew]);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue({
      ...userData,
      password: '',
      confirmPassword: '',
      newEmail: '',
      verifyCode: '',
      oldVerifyCode: '',
      oldPassword: '',
      newEmailVerifyCode: ''
    });
    setAvatarUrl(userData.avatarUrl);
    setAvatarFileId(userData.avatarFileId);
    setIdPhotoUrl(userData.idPhotoUrl);
    setActiveTab('profile');
    setSecurityMode('password');
    setEmailIdentityMethod('password');
    setAuthFileId(null);
    setAuthFileReady(false);
    setRevokeLoading(false);
    setEduBg(userData.eduBg || '');

    if (userData.universityId) {
      axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/schools/${userData.universityId}`).then(res => {
        if (res.data.code === 200) setSchools(res.data.data || []);
      });
    }
    if (userData.schoolId) {
      axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/majors/${userData.schoolId}`).then(res => {
        if (res.data.code === 200) setMajors(res.data.data || []);
      });
    }
  }, [form, open, userData]);

  const isIdentityLocked = ['normal', 'pending', 'banned'].includes(userData.accountStatus || '');
  const isCollegeStudent = ['undergraduate', 'postgraduate'].includes(eduBg);
  const isAllBound = Boolean(userData.newcoderId && userData.luoguId && userData.codeforcesId && userData.atcoderId && userData.cwnuojId);

  const handleUniChange = async (value: number) => {
    setSchools([]);
    setMajors([]);
    form.setFieldsValue({ schoolId: null, majorId: null });
    const res = await axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/schools/${value}`);
    if (res.data.code === 200) setSchools(res.data.data || []);
  };

  const handleSchoolChange = async (value: number) => {
    setMajors([]);
    form.setFieldsValue({ majorId: null });
    const res = await axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/majors/${value}`);
    if (res.data.code === 200) setMajors(res.data.data || []);
  };

  const handleSendCode = async (target: 'current' | 'new') => {
    const email = target === 'current' ? userData.email : form.getFieldValue('newEmail');
    if (!email) {
      message.error(target === 'current' ? '当前账号未绑定邮箱' : '请先填写新邮箱');
      return;
    }
    if (target === 'new' && email === userData.email) {
      message.error('新邮箱不能与旧邮箱相同');
      return;
    }

    const type = target === 'new' ? 'change-email-new'
      : activeTab === 'binding' ? 'binding'
      : securityMode === 'email' ? 'change-email-current'
      : securityMode === 'password' ? 'change-password' : 'update';
    try {
      if (target === 'new') await form.validateFields(['newEmail']);
      const res = await axios.post('/api/user/send-code', null, { params: { email, type } });
      if (res.data.code !== 200) {
        message.error(res.data.msg || '验证码发送失败');
        return;
      }
      message.success(`验证码已发送至 ${email}`);
      if (target === 'current') setCountdownOld(60);
      else setCountdownNew(60);
    } catch {
      message.error('验证码发送失败，请检查邮箱或稍后重试');
    }
  };

  const handleUploadAvatar = async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('uploaderId', String(userData.userId));
    const res = await axios.post<{ code: number; data?: string | number; msg?: string }>('/api/file/upload', formData);
    if (res.data.code === 200 && res.data.data) {
      setAvatarFileId(res.data.data);
      const reader = new FileReader();
      reader.onload = event => setAvatarUrl(String(event.target?.result || ''));
      reader.readAsDataURL(file);
      message.success('头像上传成功');
    } else {
      message.error(res.data.msg || '头像上传失败');
    }
  };

  const handleUploadAuth = async (file: File) => {
    setAuthUploading(true);
    setAuthFileId(null);
    setAuthFileReady(false);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('uploadType', 'student_card');
      const res = await axios.post<{ code: number; data?: string | number; msg?: string }>('/api/file/upload', formData, { timeout: 60000 });
      if (res.data.code === 200 && res.data.data) {
        setAuthFileId(res.data.data);
        setAuthFileReady(true);
        message.success('文件上传成功，请提交认证申请');
      } else {
        message.error(res.data.msg || '文件上传失败，请重新上传');
      }
    } catch (error: unknown) {
      message.error(describeRequestError(error, '认证照片上传失败，请稍后重试'));
    } finally {
      setAuthUploading(false);
    }
    return false;
  };

  const handleUploadIdPhoto = async (file: File) => {
    Modal.confirm({
      title: '确认上传证件照？',
      content: '该照片将用于比赛报名和参赛证明。请确认照片清晰、比例正确且为本人证件照。',
      okText: '确认上传',
      cancelText: '再检查一下',
      onOk: async () => {
        setIdPhotoUploading(true);
        try {
          const formData = new FormData();
          formData.append('file', file);
          formData.append('uploaderId', String(userData.userId));
          formData.append('uploadType', 'id_photo');
          const uploadRes = await axios.post<{ code: number; data?: string | number; msg?: string }>('/api/file/upload', formData);
          if (uploadRes.data.code !== 200 || !uploadRes.data.data) {
            message.error(uploadRes.data.msg || '证件照上传失败');
            return;
          }
          const bindRes = await axios.post<{ code: number; msg?: string }>('/api/user/id-photo', null, {
            params: { fileId: uploadRes.data.data }
          });
          if (bindRes.data.code === 200) {
            const reader = new FileReader();
            reader.onload = event => setIdPhotoUrl(String(event.target?.result || ''));
            reader.readAsDataURL(file);
            message.success('证件照已上传');
            onSuccess();
          } else {
            message.error(bindRes.data.msg || '证件照保存失败');
          }
        } catch (error: unknown) {
          const serverMessage = axios.isAxiosError<{ msg?: string }>(error) ? error.response?.data?.msg : undefined;
          message.error(serverMessage || '证件照上传或保存失败，请稍后重试');
        } finally {
          setIdPhotoUploading(false);
        }
      }
    });
    return false;
  };

  const submitAuth = async () => {
    if (authUploading || authLoading) return;
    if (!authFileId) {
      message.warning('请先上传证件照片');
      return;
    }
    setAuthLoading(true);
    try {
      const res = await axios.post<{ code: number; msg?: string }>('/api/user/submit-auth', null, {
        params: { fileId: authFileId },
        timeout: 15000
      });
      if (res.data.code === 200) {
        message.success('认证申请已提交');
        // 账号状态（register/restricted -> pending）已变化，立即同步本地身份快照。
        void refreshAuth();
        onSuccess();
      } else {
        message.error(res.data.msg || '提交失败');
      }
    } catch (error: unknown) {
      message.error(describeRequestError(error, '认证提交失败，请稍后重试'));
    } finally {
      setAuthLoading(false);
    }
  };

  const revokeAuth = () => {
    Modal.confirm({
      title: '确认撤销身份认证？',
      icon: <ExclamationCircleOutlined />,
      content: '仅待审核期间可以撤销当前申请。撤销后你可以重新上传认证材料并再次提交审核；审核通过后身份信息不可更改，如需更正请联系校队负责人。',
      okText: '确认撤销申请',
      okType: 'danger',
      cancelText: '暂不撤销',
      onOk: async () => {
        setRevokeLoading(true);
        try {
          const res = await axios.post<{ code: number; msg?: string }>('/api/user/revoke-auth');
          if (res.data.code === 200) {
            message.success('认证申请已撤销，请重新上传认证材料');
            // 撤销后账号状态回到 restricted，必须立刻反映到当前界面。
            await refreshAuth();
            onSuccess();
          } else {
            message.error(res.data.msg || '撤销失败');
          }
        } catch {
          message.error('撤销失败，请稍后重试');
        } finally {
          setRevokeLoading(false);
        }
      }
    });
  };

  const resolveMajorForNonStudent = async (universityId: number) => {
    const university = universities.find(item => item.universityId === universityId);
    if (!university?.universityName) return undefined;
    const schoolRes = await axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/schools/${universityId}`);
    const school = (schoolRes.data.data || []).find(item => item.schoolName === university.universityName);
    if (!school?.schoolId) return undefined;
    const majorRes = await axios.get<{ code: number; data?: DictionaryItem[] }>(`/api/common/majors/${school.schoolId}`);
    return (majorRes.data.data || []).find(item => item.majorName === university.universityName)?.majorId;
  };

  const handleSubmit = async () => {
    const fields = activeTab === 'security'
      ? securityMode === 'email' ? ['newEmail', 'newEmailVerifyCode', emailIdentityMethod === 'password' ? 'oldPassword' : 'oldVerifyCode']
        : securityMode === 'password' ? ['password', 'confirmPassword', 'verifyCode'] : ['userAccount', 'verifyCode']
      : activeTab === 'binding' ? ['newcoderId', 'luoguId', 'codeforcesId', 'atcoderId', 'cwnuojId', 'verifyCode']
      : ['userName', 'introduce', 'realName', 'numberId', 'gender', 'phone', 'gradeYear', 'className', 'isEnrolledStu', 'eduBg', 'majorId', 'universityId', 'schoolId'];
    const values = await form.validateFields(fields);
    const payload: Record<string, unknown> = { userId: userData.userId };

    if (activeTab === 'profile') {
      payload.userName = values.userName;
      payload.introduce = values.introduce;
      payload.avatarFileId = avatarFileId;
      if (!isIdentityLocked) {
        Object.assign(payload, {
          realName: values.realName,
          numberId: values.numberId,
          gender: values.gender,
          phone: values.phone,
          gradeYear: values.gradeYear,
          className: values.className,
          isEnrolledStu: values.isEnrolledStu,
          eduBg: values.eduBg
        });
        payload.majorId = isCollegeStudent ? values.majorId : await resolveMajorForNonStudent(values.universityId);
        if (!payload.majorId) {
          message.error('系统未找到该学校关联的默认配置，请联系管理员');
          return;
        }
      }
    }

    if (activeTab === 'binding') {
      Object.assign(payload, {
        newcoderId: values.newcoderId,
        luoguId: values.luoguId,
        codeforcesId: values.codeforcesId,
        atcoderId: values.atcoderId,
        cwnuojId: values.cwnuojId,
        verifyCode: values.verifyCode
      });
    }

    if (activeTab === 'security') {
      if (securityMode === 'account') {
        payload.userAccount = values.userAccount;
        payload.verifyCode = values.verifyCode;
      } else if (securityMode === 'password') {
        payload.password = md5(String(values.password));
        payload.verifyCode = values.verifyCode;
      } else {
        payload.email = values.newEmail;
        if (emailIdentityMethod === 'password') payload.oldPassword = md5(String(values.oldPassword));
        else payload.oldVerifyCode = values.oldVerifyCode;
        payload.newEmailVerifyCode = values.newEmailVerifyCode;
      }
    }

    setLoading(true);
    try {
      const res = await axios.post<{ code: number; msg?: string }>('/api/user/update', payload);
      if (res.data.code === 200) {
        message.success('保存成功');
        // 昵称/头像等资料会被站点页头和后台侧栏缓存，保存后立即刷新本地快照。
        await refreshAuth();
        onSuccess();
      } else {
        message.error(res.data.msg || '保存失败');
      }
    } finally {
      setLoading(false);
    }
  };

  const renderCodeInput = (placeholder: string, target: 'current' | 'new') => (
    <Input
      className="bits-input"
      placeholder={placeholder}
      prefix={<MailOutlined />}
      suffix={
        <Button
          type="link"
          size="small"
          onClick={() => handleSendCode(target)}
          disabled={target === 'current' ? countdownOld > 0 : countdownNew > 0}
          style={{ padding: '0 4px', fontWeight: 600 }}
        >
          {target === 'current' ? (countdownOld > 0 ? `${countdownOld}s` : '获取验证码') : (countdownNew > 0 ? `${countdownNew}s` : '获取验证码')}
        </Button>
      }
    />
  );

  return (
    <Modal
      open={open}
      onCancel={onCancel}
      footer={null}
      width="min(1120px, 96vw)"
      className="profile-edit-modal"
      destroyOnHidden
      maskClosable={false}
    >
      <div className="modal-gradient-heading">
        <span className="bits-pill">PROFILE SETTINGS</span>
        <h2>编辑个人信息</h2>
      </div>
      <Form form={form} layout="vertical">
        <Tabs
          activeKey={activeTab}
          onChange={value => { setActiveTab(value); form.resetFields(['verifyCode', 'oldVerifyCode', 'oldPassword', 'newEmailVerifyCode']); }}
          items={[
            {
              key: 'profile',
              label: <span><UserOutlined /> 基本资料</span>,
              children: (
                <div className="edit-profile-panel">
                  <div className="avatar-edit-zone">
                    <ImgCrop rotationSlider aspect={1} quality={0.8} modalTitle="编辑头像">
                      <Upload showUploadList={false} beforeUpload={file => { handleUploadAvatar(file); return false; }}>
                        <button className="avatar-edit-button" type="button">
                          <Avatar size={108} src={avatarUrl} icon={<UserOutlined />} />
                          <span><CameraOutlined /></span>
                        </button>
                      </Upload>
                    </ImgCrop>
                    <strong>{userData.userName || '未命名用户'}</strong>
                  </div>

                  <Divider>基本资料</Divider>
                  <Row gutter={18}>
                    <Col xs={24} md={12}><Form.Item label="用户昵称" name="userName" rules={[{ required: true }]}><Input className="bits-input" maxLength={20} showCount /></Form.Item></Col>
                    <Col xs={24} md={12}><Form.Item label="个人简介" name="introduce"><Input.TextArea className="bits-input" maxLength={80} showCount autoSize /></Form.Item></Col>
                  </Row>

                  <Divider>证件照</Divider>
                  <div className="id-photo-edit-panel">
                    <div className="id-photo-preview">
                      {idPhotoUrl ? (
                        <Image src={idPhotoUrl} alt="证件照" width={120} height={160} style={{ objectFit: 'cover' }} />
                      ) : (
                        <span>未上传</span>
                      )}
                    </div>
                    <div className="id-photo-actions">
                      <Alert
                        type={idPhotoUrl ? 'success' : 'warning'}
                        showIcon
                        message={idPhotoUrl ? '证件照已上传，如需更换可重新上传。' : '请上传 3:4 证件照，报名和参赛证明将使用此照片。'}
                      />
                      <ImgCrop rotationSlider aspect={3 / 4} quality={0.92} modalTitle="裁剪证件照">
                        <Upload showUploadList={false} beforeUpload={handleUploadIdPhoto} disabled={idPhotoUploading} accept="image/png,image/jpeg">
                          <Button className="bits-ghost-button" icon={<IdcardOutlined />} loading={idPhotoUploading}>
                            上传证件照
                          </Button>
                        </Upload>
                      </ImgCrop>
                    </div>
                  </div>

                  <Divider>
                    身份认证信息
                    {userData.accountStatus === 'normal' && <span className="status-ok"><CheckCircleFilled /> 已认证</span>}
                  {userData.accountStatus === 'pending' && <span className="status-info"><InfoCircleOutlined /> 审核中</span>}
                    {['restricted', 'register'].includes(userData.accountStatus || '') && <span className="status-warn"><ExclamationCircleOutlined /> 未认证</span>}
                  </Divider>

                  {['restricted', 'register'].includes(userData.accountStatus || '') && (
                    <Alert type="warning" showIcon message="请完善身份信息并上传本校学生卡或学生证照片，清晰显示学校、姓名和学号（JPG / PNG，不超过 5 MB）。认证通过后身份信息锁定；校队资格由管理员另行确认。" />
                  )}
                  {userData.accountStatus === 'pending' && (
                    <Alert
                      type="info"
                      showIcon
                      message="身份认证审核中"
                      description="审核期间可撤销申请并重新上传认证材料；审核通过后身份信息不可更改，如需更正请联系校队负责人。"
                      action={<Button danger loading={revokeLoading} onClick={revokeAuth}>撤销申请</Button>}
                      style={{ marginBottom: 16 }}
                    />
                  )}
                  {userData.accountStatus === 'normal' && (
                    <Alert
                      type="success"
                      showIcon
                      message="身份认证已通过"
                      description="身份信息已锁定，不可撤销认证或自行修改。如需更正身份资料或认证材料，请联系校队负责人。"
                      style={{ marginBottom: 16 }}
                    />
                  )}
                  <Row gutter={18}>
                    <Col xs={24} md={8}><Form.Item label="真实姓名" name="realName" rules={[{ required: !isIdentityLocked }]}><Input className="bits-input" disabled={isIdentityLocked} /></Form.Item></Col>
                    <Col xs={24} md={8}><Form.Item label="身份类型" name="eduBg" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked} onChange={setEduBg} options={[{ value: 'undergraduate', label: '本科生' }, { value: 'postgraduate', label: '研究生' }, { value: 'department', label: '部门' }, { value: 'off-campus', label: '校外' }]} /></Form.Item></Col>
                    <Col xs={24} md={8}><Form.Item label="学号/编号" name="numberId" rules={[{ required: !isIdentityLocked }]}><Input className="bits-input" disabled={isIdentityLocked} /></Form.Item></Col>
                    <Col xs={24} md={8}><Form.Item label="性别" name="gender" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked} options={[{ value: 1, label: '男' }, { value: 2, label: '女' }]} /></Form.Item></Col>
                    <Col xs={24} md={8}><Form.Item label="年级" name="gradeYear" rules={[{ required: !isIdentityLocked }, { pattern: /^\d{4}$/, message: '格式如：2024' }]}><Input className="bits-input" disabled={isIdentityLocked} /></Form.Item></Col>
                    <Col xs={24} md={8}><Form.Item label="手机号" name="phone"><Input className="bits-input" disabled={isIdentityLocked} /></Form.Item></Col>
                    {isCollegeStudent ? (
                      <>
                        <Col xs={24} md={8}><Form.Item label="学校" name="universityId" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked} showSearch optionFilterProp="label" onChange={handleUniChange} options={universities.map(item => ({ value: item.universityId, label: item.universityName }))} /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item label="院系 / 培养单位" name="schoolId" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked || !schools.length} showSearch optionFilterProp="label" onChange={handleSchoolChange} options={schools.map(item => ({ value: item.schoolId, label: item.schoolName }))} /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item label="专业" name="majorId" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked || !majors.length} showSearch optionFilterProp="label" options={majors.map(item => ({ value: item.majorId, label: item.majorName }))} /></Form.Item></Col>
                        <Col xs={24} md={8}><Form.Item label="行政班级" name="className" rules={[{ required: !isIdentityLocked }, { pattern: /^\d+$/, message: '班级必须为纯数字' }]}><Input className="bits-input" disabled={isIdentityLocked} /></Form.Item></Col>
                      </>
                    ) : (
                      <Col xs={24} md={16}><Form.Item label="所属学校/单位" name="universityId" rules={[{ required: !isIdentityLocked }]}><Select className="bits-select" disabled={isIdentityLocked} showSearch optionFilterProp="label" options={universities.map(item => ({ value: item.universityId, label: item.universityName }))} /></Form.Item></Col>
                    )}
                    <Col xs={24} md={8}><Form.Item label="是否北京电子科技学院在校生" name="isEnrolledStu"><Select className="bits-select" disabled={isIdentityLocked} options={[{ value: 1, label: '是（在读）' }, { value: 0, label: '否（毕业/其他学校）' }]} /></Form.Item></Col>
                  </Row>
                  {['restricted', 'register'].includes(userData.accountStatus || '') && (
                    <div className="auth-upload-panel">
                      <strong><IdcardOutlined /> 身份认证申请</strong>
                      <Upload beforeUpload={handleUploadAuth} showUploadList={false} maxCount={1} disabled={authUploading || authLoading} accept="image/png,image/jpeg">
                        <Button className="bits-ghost-button" icon={<UploadOutlined />} loading={authUploading} disabled={authLoading}>上传认证照片</Button>
                      </Upload>
                      <Button className="bits-primary-button" onClick={submitAuth} loading={authLoading} disabled={!authFileId || authUploading}>提交认证申请</Button>
                      {authFileReady && <span className="status-ok"><CheckCircleFilled /> 认证照片已上传</span>}
                    </div>
                  )}
                </div>
              )
            },
            {
              key: 'binding',
              label: <span><GlobalOutlined /> 账号绑定</span>,
              children: (
                <div className="edit-narrow-panel">
                  {isAllBound ? <Alert type="success" showIcon message="所有账号已完成绑定，信息仅供展示，不可修改。" /> : <Alert type="warning" showIcon message="账号绑定后不可修改，请确认账号属于本人。" />}
                  <Form.Item label="牛客竞赛数字 UID" name="newcoderId" extra="填写竞赛个人主页地址 /profile/ 后的数字。" rules={[{ pattern: /^\d+$/, message: '请输入数字 UID，不是昵称或学号' }]}><Input className="bits-input" disabled={Boolean(userData.newcoderId)} placeholder="例如 123456" prefix={<GlobalOutlined />} /></Form.Item>
                  <Form.Item label="洛谷数字 UID" name="luoguId" extra="填写洛谷个人主页地址 /user/ 后的数字。" rules={[{ pattern: /^\d+$/, message: '请输入数字 UID，不是昵称' }]}><Input className="bits-input" disabled={Boolean(userData.luoguId)} placeholder="例如 123456" prefix={<GlobalOutlined />} /></Form.Item>
                  <Form.Item label="Codeforces" name="codeforcesId"><Input className="bits-input" disabled={Boolean(userData.codeforcesId)} prefix={<GlobalOutlined />} /></Form.Item>
                  <Form.Item label="AtCoder" name="atcoderId"><Input className="bits-input" disabled={Boolean(userData.atcoderId)} prefix={<GlobalOutlined />} /></Form.Item>
                  <Form.Item label="学校 OJ" name="cwnuojId"><Input className="bits-input" disabled={Boolean(userData.cwnuojId)} prefix={<GlobalOutlined />} /></Form.Item>
                  {!isAllBound && <Form.Item label="邮箱验证码" name="verifyCode" rules={[{ required: true }]}>{renderCodeInput(`验证码将发送至 ${userData.email || ''}`, 'current')}</Form.Item>}
                </div>
              )
            },
            {
              key: 'security',
              label: <span><SafetyCertificateOutlined /> 安全中心</span>,
              children: (
                <div className="edit-narrow-panel">
                  <Segmented
                    block
                    size="large"
                    value={securityMode}
                    onChange={value => { setSecurityMode(value as SecurityMode); form.resetFields(['verifyCode', 'oldVerifyCode', 'oldPassword', 'newEmailVerifyCode']); }}
                    options={[{ label: '修改用户名', value: 'account', icon: <UserOutlined /> }, { label: '修改密码', value: 'password', icon: <LockOutlined /> }, { label: '更换邮箱', value: 'email', icon: <MailOutlined /> }]}
                  />
                  {securityMode === 'account' ? (
                    <>
                      <Alert type="info" message={`当前用户名：${userData.userAccount || '未设置'}`} description="修改用户名需要通过当前绑定邮箱验证码验证。" />
                      <Form.Item label="新用户名" name="userAccount" rules={[{ required: true }, { pattern: /^(?=.*[A-Za-z])[A-Za-z0-9]{3,20}$/, message: '3-20个英文字母或数字，且至少包含1个英文字母' }]}><Input className="bits-input" prefix={<UserOutlined />} maxLength={20} /></Form.Item>
                      <Form.Item label="邮箱验证码" name="verifyCode" rules={[{ required: true }]}>{renderCodeInput(`验证码将发送至 ${userData.email || ''}`, 'current')}</Form.Item>
                    </>
                  ) : securityMode === 'password' ? (
                    <>
                      <Alert type="info" message="修改密码需要验证当前绑定邮箱" />
                      <Form.Item label="新密码" name="password" rules={[{ required: true }, { min: 6 }]}><Input.Password className="bits-input" /></Form.Item>
                      <Form.Item label="确认密码" name="confirmPassword" dependencies={['password']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}><Input.Password className="bits-input" /></Form.Item>
                      <Form.Item label="改密码专用验证码" name="verifyCode" rules={[{ required: true }]}>{renderCodeInput(`验证码将发送至 ${userData.email || ''}`, 'current')}</Form.Item>
                    </>
                  ) : (
                    <>
                      <Alert type="info" message={`当前绑定邮箱：${userData.email || '未绑定'}`} description="先验证当前密码或旧邮箱，再验证新邮箱。" />
                      <Segmented block value={emailIdentityMethod} onChange={value => { setEmailIdentityMethod(value as EmailIdentityMethod); form.resetFields(['oldPassword', 'oldVerifyCode']); }} options={[{ label: '验证当前密码', value: 'password' }, { label: '验证旧邮箱', value: 'email' }]} />
                      {emailIdentityMethod === 'password' ? (
                        <Form.Item label="当前密码" name="oldPassword" rules={[{ required: true }]}><Input.Password className="bits-input" autoComplete="current-password" /></Form.Item>
                      ) : (
                        <Form.Item label="旧邮箱验证码" name="oldVerifyCode" rules={[{ required: true }]}>{renderCodeInput('旧邮箱验证码', 'current')}</Form.Item>
                      )}
                      <Form.Item label="新邮箱" name="newEmail" rules={[{ required: true }, { type: 'email' }]}><Input className="bits-input" prefix={<MailOutlined />} /></Form.Item>
                      <Form.Item label="新邮箱验证码" name="newEmailVerifyCode" rules={[{ required: true }]}>{renderCodeInput('新邮箱验证码', 'new')}</Form.Item>
                    </>
                  )}
                </div>
              )
            }
          ]}
        />
      </Form>
      <div className="modal-action-row">
        <Button className="bits-ghost-button" onClick={onCancel}>关闭</Button>
        {!(activeTab === 'binding' && isAllBound) && <Button className="bits-primary-button" loading={loading} onClick={handleSubmit}>保存</Button>}
      </div>
    </Modal>
  );
};

export default EditProfileModal;
