"use client";

import { Button, Card, Col, Divider, Form, Input, Radio, Result, Row, Select, Steps, Tag, Typography, Upload, message } from 'antd';
import { IdcardOutlined, LockOutlined, MailOutlined, MobileOutlined, RocketOutlined, SafetyCertificateOutlined, SolutionOutlined, UploadOutlined, UserOutlined } from '@ant-design/icons';
import axios from 'axios';
import { md5 } from 'js-md5';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import SiteHeader from '../components/SiteHeader';
import { describeValidationFailure } from './validationFeedback';
import { describeRequestError } from '../../utils/errorMessage';
import SliderCaptcha from '../components/SliderCaptcha';

const { Title, Paragraph, Text } = Typography;

interface OptionRecord {
  universityId?: number;
  universityName?: string;
  schoolId?: number;
  schoolName?: string;
  majorId?: number;
  majorName?: string;
}

type RegisterValues = Record<string, string | number | undefined>;

// 注册表单里需要忽略前后空格的文本字段（密码走 md5，不在此列）
const TEXT_FIELDS = ['userAccount', 'userName', 'email', 'phone', 'realName', 'numberId', 'className', 'gradeYear', 'verifyCode'];

// 规则里没写 message 时（如 { required: true }）的兜底文案，保证提示里带字段名
const VALIDATE_MESSAGES = {
  required: '${label}不能为空',
  whitespace: '${label}不能只填空格',
  types: {
    email: '${label}格式不正确',
    number: '${label}必须是数字',
  },
  string: {
    len: '${label}长度必须为${len}个字符',
    min: '${label}至少${min}个字符',
    max: '${label}最多${max}个字符',
    range: '${label}长度需在${min}-${max}个字符之间',
  },
  pattern: { mismatch: '${label}格式不正确' },
};

const RegisterPage = () => {
  const router = useRouter();
  const [form] = Form.useForm();
  const [current, setCurrent] = useState(0);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [countdown, setCountdown] = useState(0);
  const [formData, setFormData] = useState<RegisterValues>({});
  const [registeredUserId, setRegisteredUserId] = useState<number | null>(null);
  const [authLoading, setAuthLoading] = useState(false);
  const [universities, setUniversities] = useState<OptionRecord[]>([]);
  const [schools, setSchools] = useState<OptionRecord[]>([]);
  const [majors, setMajors] = useState<OptionRecord[]>([]);
  const [captchaOpen, setCaptchaOpen] = useState(false);
  const [captchaAction, setCaptchaAction] = useState<'sendCode' | 'register' | null>(null);
  const [identityType, setIdentityType] = useState('');

  useEffect(() => {
    document.title = "注册";
    axios.get('/api/user/register/config').then((res) => {
      if (res.data.code === 200 && res.data.data === false) {
        message.error('系统已关闭注册功能');
        router.push('/login');
      }
    });
    axios.get('/api/common/universities').then((res) => {
      if (res.data.code === 200) setUniversities(res.data.data || []);
    });
  }, [router]);

  useEffect(() => {
    if (countdown <= 0) return;
    const timer = window.setTimeout(() => setCountdown((value) => value - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [countdown]);

  const handleUniChange = (value: number) => {
    setSchools([]);
    setMajors([]);
    form.setFieldsValue({ schoolId: null, majorId: null });
    axios.get(`/api/common/schools/${value}`).then((res) => {
      if (res.data.code === 200) setSchools(res.data.data || []);
    });
  };

  const handleSchoolChange = (value: number) => {
    setMajors([]);
    form.setFieldsValue({ majorId: null });
    axios.get(`/api/common/majors/${value}`).then((res) => {
      if (res.data.code === 200) setMajors(res.data.data || []);
    });
  };

  const handleSendCodeClick = async () => {
    try {
      await form.validateFields(['email']);
      setCaptchaAction('sendCode');
      setCaptchaOpen(true);
    } catch {
      message.error('请先输入有效的邮箱地址');
    }
  };

  const sendEmailCode = async () => {
    const email = form.getFieldValue('email')?.toLowerCase();
    setSending(true);
    try {
      const res = await axios.post(`/api/user/send-code?email=${email}`);
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

  // 校验/提交前先去掉文本字段的前后空格，避免“ 2024 ”这类输入被判成格式错误或原样入库
  const trimTextFieldValues = () => {
    const patch: Record<string, string> = {};
    TEXT_FIELDS.forEach((field) => {
      const value = form.getFieldValue(field);
      if (typeof value === 'string' && value !== value.trim()) {
        patch[field] = value.trim();
      }
    });
    if (Object.keys(patch).length > 0) {
      form.setFieldsValue(patch);
    }
  };

  /**
   * 表单校验不通过时，直接指出是哪个格子、错在哪里，并滚动到该字段。
   * 返回 true 表示已经提示过，调用方不要再回落到“请先完善当前步骤”。
   */
  const showValidationErrors = (error: unknown) => {
    const feedback = describeValidationFailure(error);
    if (!feedback) {
      return false;
    }
    message.error(feedback.message);
    if (feedback.field) {
      form.scrollToField(feedback.field, { behavior: 'smooth', block: 'center' });
    }
    return true;
  };

  const next = async () => {
    try {
      trimTextFieldValues();
      const fields = current === 0
        ? ['userAccount', 'password', 'confirmPassword', 'userName', 'email', 'phone']
        : ['eduBg', 'realName', 'numberId', 'gender', 'verifyCode'];
      const values = await form.validateFields(fields);
      if (values.email) values.email = values.email.toLowerCase();

      // 前端提示只是即时反馈，最终注册接口仍会再次校验并由数据库约束兜底。
      const availabilityPayload = current === 0
        ? {
            step: 0,
            userAccount: values.userAccount,
            email: values.email,
            phone: values.phone,
          }
        : {
            step: 1,
            numberId: values.numberId,
            universityId: form.getFieldValue('universityId'),
            majorId: form.getFieldValue('majorId'),
          };
      const availability = await axios.post('/api/user/register/validate-step', availabilityPayload);
      if (availability.data.code !== 200) {
        message.error(availability.data.msg || '当前信息已被使用');
        return;
      }

      setFormData((prev) => ({ ...prev, ...values }));
      setCurrent((value) => value + 1);
    } catch (error) {
      if (showValidationErrors(error)) return;
      message.warning(describeRequestError(error));
    }
  };

  const submitRegister = async () => {
    try {
      trimTextFieldValues();
      const fields = ['eduBg', 'realName', 'numberId', 'gender', 'verifyCode', 'universityId', 'gradeYear'];
      if (['undergraduate', 'postgraduate'].includes(identityType)) fields.push('schoolId', 'majorId', 'className');
      const values = await form.validateFields(fields);
      const isCollegeStudent = ['undergraduate', 'postgraduate'].includes(String(values.eduBg));
      const finalData: RegisterValues = { ...formData, ...values };

      if (!isCollegeStudent) {
        const currentUni = universities.find((item) => item.universityId === values.universityId);
        if (currentUni) {
          const schoolRes = await axios.get(`/api/common/schools/${values.universityId}`);
          const matchedSchool = (schoolRes.data.data || []).find((item: OptionRecord) => item.schoolName === currentUni.universityName);
          if (matchedSchool) {
            finalData.schoolId = matchedSchool.schoolId;
            const majorRes = await axios.get(`/api/common/majors/${matchedSchool.schoolId}`);
            const matchedMajor = (majorRes.data.data || []).find((item: OptionRecord) => item.majorName === currentUni.universityName);
            if (matchedMajor) finalData.majorId = matchedMajor.majorId;
          }
        }
        if (!finalData.majorId) {
          message.error('系统未找到该学校关联的同名预设学院或专业，请联系管理员');
          return;
        }
      }

      delete finalData.confirmPassword;
      finalData.password = md5(String(finalData.password));
      if (finalData.email) finalData.email = String(finalData.email).toLowerCase();

      setLoading(true);
      const res = await axios.post('/api/user/register', finalData);
      if (res.data.code === 200) {
        message.success('注册成功');
        setRegisteredUserId(res.data.data.userId);
        setCurrent(2);
      } else {
        message.error(res.data.msg);
      }
    } catch (error) {
      if (!showValidationErrors(error)) {
        message.error(describeRequestError(error));
      }
    } finally {
      setLoading(false);
    }
  };

  const handleRegister = async () => {
    try {
      trimTextFieldValues();
      const fields = ['eduBg', 'realName', 'numberId', 'gender', 'verifyCode', 'universityId', 'gradeYear'];
      if (['undergraduate', 'postgraduate'].includes(identityType)) fields.push('schoolId', 'majorId', 'className');
      await form.validateFields(fields);
      const availability = await axios.post('/api/user/register/validate-step', {
        step: 1,
        numberId: form.getFieldValue('numberId'),
        universityId: form.getFieldValue('universityId'),
        majorId: form.getFieldValue('majorId'),
      });
      if (availability.data.code !== 200) {
        message.error(availability.data.msg || '该学校的学号已存在');
        return;
      }
      setCaptchaAction('register');
      setCaptchaOpen(true);
    } catch (error) {
      if (showValidationErrors(error)) return;
      message.error(describeRequestError(error));
    }
  };

  const uploadProps = {
    accept: 'image/png,image/jpeg',
    disabled: authLoading,
    beforeUpload: async (file: File) => {
      if (authLoading || !registeredUserId) return false;
      setAuthLoading(true);
      try {
        const data = new FormData();
        data.append('file', file);
        data.append('uploadType', 'student_card');
        const upload = await axios.post<{ code: number; data?: string | number; msg?: string }>('/api/file/upload', data, { timeout: 60000 });
        if (upload.data.code !== 200 || !upload.data.data) {
          message.error(upload.data.msg || '认证照片上传失败，请重新上传');
          return false;
        }
        const res = await axios.post<{ code: number; msg?: string }>('/api/user/submit-auth', null, {
          params: { fileId: upload.data.data },
          timeout: 15000
        });
        if (res.data.code === 200) {
          message.success('认证提交成功，请等待审核');
          router.push('/login');
        } else {
          message.error(res.data.msg || '认证提交失败，请稍后重试');
        }
      } catch (error: unknown) {
        message.error(describeRequestError(error, '认证上传或提交失败，请稍后重试'));
      } finally {
        setAuthLoading(false);
      }
      return false;
    }
  };

  return (
    <main className="auth-page-shell register-shell">
      <SiteHeader />
      <SliderCaptcha
        open={captchaOpen}
        onSuccess={() => {
          setCaptchaOpen(false);
          if (captchaAction === 'sendCode') sendEmailCode();
          if (captchaAction === 'register') submitRegister();
        }}
        onCancel={() => setCaptchaOpen(false)}
      />

      <section className="register-layout">
        <div className="auth-copy-panel register-copy">
          <Tag className="bits-pill" icon={<RocketOutlined />}>BESTI ACM · 账号注册</Tag>
          <Title className="bits-gradient-title">创建你的算法档案</Title>
          <Paragraph>
            注册后可报名培训与校内赛事，绑定个人身份信息，沉淀属于你的算法成长记录。
          </Paragraph>
          <Text className="auth-side-note">学生身份认证通过后可报名校赛、维护个人档案；校队资格由管理员另行确认。</Text>
        </div>

        <Card className="register-card bits-card" bordered={false} extra={<Button type="link" onClick={() => router.push('/login')}>已有账号？去登录</Button>}>
          <div className="auth-card-heading">
            <Title level={2}>注册参赛账号</Title>
            <Text>面向校赛参赛者与校队成员开放</Text>
          </div>

          <Steps
            current={current}
            className="register-steps"
            items={[
              { title: '账号信息', icon: <UserOutlined /> },
              { title: '身份验证', icon: <SolutionOutlined /> },
              { title: '认证审核', icon: <IdcardOutlined /> }
            ]}
          />

          <Form form={form} layout="vertical" size="large" initialValues={{ gender: 1 }} validateMessages={VALIDATE_MESSAGES}>
            <div className={current === 0 ? 'step-panel is-active' : 'step-panel'}>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="userAccount" label="用户名" rules={[{ required: true, message: '请输入用户名' }, { pattern: /^(?=.*[A-Za-z])[A-Za-z0-9]{3,20}$/, message: '3-20个英文字母或数字，且至少包含1个英文字母' }]}>
                    <Input className="bits-input" prefix={<UserOutlined />} placeholder="3-20位，至少包含1个英文字母" maxLength={20} onPressEnter={next} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="userName" label="昵称" rules={[{ required: true, message: '请输入昵称' }, { max: 50, message: '昵称最多50个字符' }]}>
                    <Input className="bits-input" prefix={<UserOutlined />} placeholder="例如：算法小萌新" maxLength={50} onPressEnter={next} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }, { pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d._-]{8,16}$/, message: '8-16位，含大小写字母+数字' }]}>
                    <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="大小写字母 + 数字" onPressEnter={next} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="confirmPassword" label="确认密码" dependencies={['password']} rules={[{ required: true, message: '请再次输入密码' }, ({ getFieldValue }) => ({ validator(_, value) { return !value || getFieldValue('password') === value ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致')); } })]}>
                    <Input.Password className="bits-input" prefix={<LockOutlined />} placeholder="再次输入密码" onPressEnter={next} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="email" label="邮箱" rules={[{ required: true, type: 'email', message: '请输入有效邮箱' }, { max: 100, message: '邮箱最多100个字符' }]}>
                    <Input className="bits-input" prefix={<MailOutlined />} placeholder="用于接收验证码" maxLength={100} onPressEnter={next} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="phone" label="手机号" rules={[{ required: true, pattern: /^1[3-9]\d{9}$/, message: '请输入有效手机号' }]}>
                    <Input className="bits-input" prefix={<MobileOutlined />} placeholder="用于紧急联系" onPressEnter={next} />
                  </Form.Item>
                </Col>
              </Row>
              <Button className="bits-primary-button auth-submit" block onClick={next}>下一步</Button>
            </div>

            <div className={current === 1 ? 'step-panel is-active' : 'step-panel'}>
              <Row gutter={16}>
                <Col xs={24} md={12}>
                  <Form.Item name="eduBg" label="身份类型" rules={[{ required: true, message: '请选择身份' }]}>
                    <Select className="bits-select" placeholder="请选择身份" onChange={setIdentityType} options={[
                      { value: 'undergraduate', label: '本科生' },
                      { value: 'postgraduate', label: '研究生' },
                      { value: 'department', label: '部门' },
                      { value: 'off-campus', label: '校外' }
                    ]} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="realName" label="真实姓名" rules={[{ required: true, message: '请输入真实姓名' }, { max: 50, message: '真实姓名最多50个字符' }]}>
                    <Input className="bits-input" maxLength={50} onPressEnter={handleRegister} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="numberId" label="学号/编号" rules={[{ required: true, message: '请输入学号或编号' }, { max: 50, message: '学号/编号最多50个字符' }]}>
                    <Input className="bits-input" maxLength={50} onPressEnter={handleRegister} />
                  </Form.Item>
                </Col>
                <Col xs={24} md={12}>
                  <Form.Item name="gender" label="性别">
                    <Radio.Group optionType="button" buttonStyle="solid" options={[{ value: 1, label: '男' }, { value: 2, label: '女' }]} />
                  </Form.Item>
                </Col>
              </Row>

              <div className="register-subcard">
                {identityType === 'postgraduate' && <Paragraph type="secondary">研究生请选择“研究生部”，再选择本人实际学科或专业领域。</Paragraph>}
                {['undergraduate', 'postgraduate'].includes(identityType) ? (
                  <Row gutter={16}>
                    <Col xs={24} md={12}><Form.Item name="universityId" label="学校" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" onChange={handleUniChange} options={universities.map((item) => ({ value: item.universityId, label: item.universityName }))} /></Form.Item></Col>
                    <Col xs={24} md={12}><Form.Item name="schoolId" label="院系 / 培养单位" rules={[{ required: true }]}><Select onChange={handleSchoolChange} disabled={!schools.length} options={schools.map((item) => ({ value: item.schoolId, label: item.schoolName }))} /></Form.Item></Col>
                    <Col xs={24} md={12}><Form.Item name="majorId" label="专业 / 领域" rules={[{ required: true }]}><Select disabled={!majors.length} options={majors.map((item) => ({ value: item.majorId, label: item.majorName }))} /></Form.Item></Col>
                    <Col xs={24} md={6}><Form.Item name="gradeYear" label="入学年份" rules={[{ required: true }, { pattern: /^\d{4}$/, message: '请输入4位年份' }]}><Input maxLength={4} onPressEnter={handleRegister} /></Form.Item></Col>
                    <Col xs={24} md={6}><Form.Item name="className" label="行政班级" rules={[{ required: true }, { pattern: /^\d+$/, message: '班级必须为纯数字' }, { max: 50, message: '班级最多50个字符' }]}><Input maxLength={50} onPressEnter={handleRegister} /></Form.Item></Col>
                  </Row>
                ) : (
                  <Row gutter={16}>
                    <Col xs={24} md={14}><Form.Item name="universityId" label="所属学校/单位" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" onChange={handleUniChange} options={universities.map((item) => ({ value: item.universityId, label: item.universityName }))} /></Form.Item></Col>
                    <Col xs={24} md={10}><Form.Item name="gradeYear" label={identityType === 'department' ? '入职年份' : '入学年份'} rules={[{ required: true }, { pattern: /^\d{4}$/, message: '请输入4位年份' }]}><Input maxLength={4} onPressEnter={handleRegister} /></Form.Item></Col>
                  </Row>
                )}
              </div>

              <Divider titlePlacement="left">安全验证</Divider>
              <Form.Item label="邮箱验证码" required>
                <Row gutter={10}>
                  <Col xs={15}>
                    <Form.Item name="verifyCode" noStyle rules={[{ required: true, message: '请输入验证码' }]}>
                      <Input className="bits-input" prefix={<SafetyCertificateOutlined />} placeholder="6位验证码" onPressEnter={handleRegister} />
                    </Form.Item>
                  </Col>
                  <Col xs={9}>
                    <Button className="bits-ghost-button auth-inline-button" block onClick={handleSendCodeClick} disabled={sending || countdown > 0}>
                      {countdown > 0 ? `${countdown}s` : '获取验证码'}
                    </Button>
                  </Col>
                </Row>
              </Form.Item>
              <div className="register-actions">
                <Button className="bits-ghost-button" onClick={() => setCurrent(0)}>上一步</Button>
                <Button className="bits-primary-button" loading={loading} onClick={handleRegister}>立即注册</Button>
              </div>
            </div>

            <div className={current === 2 ? 'step-panel is-active' : 'step-panel'}>
              <Result status="success" title="账号注册成功" subTitle="请上传本校学生卡或学生证照片，清晰显示学校、姓名和学号；支持 JPG / PNG，大小不超过 5 MB。审核用于确认学生身份，校队资格由管理员另行确认。" />
              <div className="upload-zone">
                {authLoading && <Text type="secondary">正在上传并提交认证，请稍候…</Text>}
                <Upload {...uploadProps} listType="picture-card" maxCount={1}>
                  <div><UploadOutlined /><div>上传学生卡</div></div>
                </Upload>
              </div>
              <Button type="link" onClick={() => router.push('/login')}>暂时跳过</Button>
            </div>
          </Form>
        </Card>
      </section>
    </main>
  );
};

export default RegisterPage;
