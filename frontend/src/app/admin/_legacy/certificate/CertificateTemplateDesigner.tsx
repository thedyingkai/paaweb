"use client";

import React, { useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Col, Collapse, Empty, Input, InputNumber, Row, Select, Space, Tooltip, Typography } from 'antd';
import { DeleteOutlined, PlusOutlined, QrcodeOutlined } from '@ant-design/icons';

export type CertificateTextBlock = {
  id: string;
  content: string;
  x: number;
  y: number;
  width: number;
  fontSize: number;
  minFontSize: number;
  fontFamily: 'SIMSUN';
  alignment: 'LEFT' | 'CENTER' | 'RIGHT';
  color: string;
  autoFit: boolean;
  bold: boolean;
  underline: boolean;
};

export type CertificateQrCodeBlock = {
  enabled: boolean;
  x: number;
  y: number;
  size: number;
};

type Props = {
  fileId: number | null;
  blocks: CertificateTextBlock[];
  onChange: (blocks: CertificateTextBlock[]) => void;
  qrCode: CertificateQrCodeBlock;
  onQrCodeChange: (qrCode: CertificateQrCodeBlock) => void;
  isTeam: boolean;
};

const commonPlaceholders = [
  { label: '奖项(Excel)', value: '<award>' },
  { label: '编号(Excel)', value: '<number>' },
  { label: '落款日期', value: '<date>' },
];

const personalPlaceholders = [
  { label: '姓名', value: '<name>' },
  { label: '学号', value: '<studentid>' },
  { label: '学校', value: '<school>' },
  ...commonPlaceholders,
];

const teamPlaceholders = [
  { label: '队伍名称', value: '<teamname>' },
  { label: '全部获奖人', value: '<members>' },
  { label: '全部学号', value: '<studentids>' },
  { label: '全部学校', value: '<schools>' },
  ...commonPlaceholders,
  ...Array.from({ length: 10 }, (_, index) => index + 1).flatMap(index => [
    { label: `队员${index}姓名`, value: `<member${index}name>` },
    { label: `队员${index}学号`, value: `<member${index}id>` },
    { label: `队员${index}学校`, value: `<member${index}school>` },
  ]),
];

const sampleValues: Record<string, string> = {
  '<name>': '张三',
  '<studentid>': '20260001',
  '<school>': '北京电子科技学院',
  '<teamname>': '星辰算法队',
  '<members>': '张三、李四、王五、赵六',
  '<studentids>': '20260001、20260002、20260003、20260004',
  '<schools>': '北京电子科技学院、北京电子科技学院、北京电子科技学院、北京电子科技学院',
  '<award>': '一等奖（金奖）',
  '<number>': 'BESTI-2026-0001',
  '<date>': '2026年7月16日',
  ...Object.fromEntries(Array.from({ length: 10 }, (_, index) => index + 1).flatMap(index => [
    [`<member${index}name>`, ['张三', '李四', '王五', '赵六', '孙七', '周八', '吴九', '郑十', '陈一', '刘二'][index - 1]],
    [`<member${index}id>`, `202600${String(index).padStart(2, '0')}`],
    [`<member${index}school>`, '北京电子科技学院'],
  ])),
};

const previewText = (content: string) => Object.entries(sampleValues)
  .reduce((text, [placeholder, value]) => text.replaceAll(placeholder, value), content);

const qrPreviewCells = [
  0, 1, 2, 3, 5, 6, 7, 8,
  9, 12, 14, 17,
  18, 20, 21, 23, 24, 26,
  27, 30, 32, 35,
  37, 39, 40, 42, 44,
  45, 46, 49, 51, 53,
  54, 56, 57, 60, 62,
  63, 66, 68, 71,
  72, 73, 74, 76, 78, 79, 80,
];

type PlaceholderHelp = {
  label: string;
  token: string;
  source: string;
  example: string;
};

const commonFieldHelp: PlaceholderHelp[] = [
  { label: '奖项', token: '<award>', source: 'Excel 的“奖项”列', example: '一等奖（金奖）' },
  { label: '证书编号', token: '<number>', source: 'Excel 的“编号”列', example: 'BESTI-2026-0001' },
  { label: '落款日期', token: '<date>', source: '模板中设置的落款日期', example: '2026年7月16日' },
];

const personalFieldHelp: PlaceholderHelp[] = [
  { label: '姓名', token: '<name>', source: 'Excel 的“姓名”列', example: '张三' },
  { label: '学号', token: '<studentid>', source: 'Excel 的“学号”列', example: '20260001' },
  { label: '学校', token: '<school>', source: 'Excel 的“学校”列', example: '北京电子科技学院' },
  ...commonFieldHelp,
];

const teamFieldHelp: PlaceholderHelp[] = [
  { label: '队伍名称', token: '<teamname>', source: 'Excel 的“队伍名称”列', example: '星辰算法队' },
  { label: '全部队员姓名', token: '<members>', source: '本行已填写的所有“队员N姓名”', example: '张三、李四、王五、赵六' },
  { label: '全部队员学号', token: '<studentids>', source: '本行已填写的所有“队员N学号”', example: '20260001、20260002' },
  { label: '全部队员学校', token: '<schools>', source: '本行已填写的所有“队员N学校”', example: '北京电子科技学院、北京电子科技学院' },
  ...Array.from({ length: 10 }, (_, index) => index + 1).flatMap(index => [
    { label: `队员${index}姓名`, token: `<member${index}name>`, source: `Excel 的“队员${index}姓名”列`, example: sampleValues[`<member${index}name>`] },
    { label: `队员${index}学号`, token: `<member${index}id>`, source: `Excel 的“队员${index}学号”列`, example: sampleValues[`<member${index}id>`] },
    { label: `队员${index}学校`, token: `<member${index}school>`, source: `Excel 的“队员${index}学校”列`, example: sampleValues[`<member${index}school>`] },
  ]),
  ...commonFieldHelp,
];

const createBlock = (index: number, isTeam: boolean): CertificateTextBlock => ({
  id: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
  content: index === 0
    ? (isTeam ? '恭喜<teamname>在本次比赛中获得<award>' : '恭喜<name>同学在本次比赛中获得<award>')
    : '<number>',
  x: 10,
  y: Math.min(35 + index * 10, 85),
  width: 80,
  fontSize: index === 0 ? 28 : 16,
  minFontSize: 10,
  fontFamily: 'SIMSUN',
  alignment: 'CENTER',
  color: '#000000',
  autoFit: true,
  bold: false,
  underline: false,
});

export default function CertificateTemplateDesigner({ fileId, blocks, onChange, qrCode, onQrCodeChange, isTeam }: Props) {
  const [selectedId, setSelectedId] = useState<string | null>(blocks[0]?.id || null);
  const canvasRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ type: 'text' | 'qr'; id?: string; clientX: number; clientY: number; x: number; y: number } | null>(null);

  const selected = useMemo(() => blocks.find(block => block.id === selectedId) || null, [blocks, selectedId]);
  const availablePlaceholders = useMemo(() => isTeam ? teamPlaceholders : personalPlaceholders, [isTeam]);
  const fieldHelp = useMemo(() => isTeam ? teamFieldHelp : personalFieldHelp, [isTeam]);

  const updateBlock = (id: string, patch: Partial<CertificateTextBlock>) => {
    onChange(blocks.map(block => block.id === id ? { ...block, ...patch } : block));
  };

  const updateQrCode = (patch: Partial<CertificateQrCodeBlock>) => {
    const next = { ...qrCode, ...patch };
    const size = Math.max(5, Math.min(40, Number(next.size || 14)));
    const x = Math.max(0, Math.min(100 - size, Number(next.x || 0)));
    const y = Math.max(0, Math.min(96, Number(next.y || 0)));
    onQrCodeChange({ enabled: next.enabled, x: Number(x.toFixed(2)), y: Number(y.toFixed(2)), size: Number(size.toFixed(2)) });
  };

  const addBlock = () => {
    const block = createBlock(blocks.length, isTeam);
    onChange([...blocks, block]);
    setSelectedId(block.id);
  };

  const removeBlock = (id: string) => {
    const next = blocks.filter(block => block.id !== id);
    onChange(next);
    if (selectedId === id) setSelectedId(next[0]?.id || null);
  };

  const insertPlaceholder = (value: string) => {
    if (!selected) return;
    updateBlock(selected.id, { content: `${selected.content}${value}` });
  };

  const onPointerMove = (event: React.PointerEvent<HTMLDivElement>) => {
    const drag = dragRef.current;
    const canvas = canvasRef.current;
    if (!drag || !canvas) return;
    const rect = canvas.getBoundingClientRect();
    if (drag.type === 'qr') {
      const x = Math.max(0, Math.min(100 - qrCode.size, drag.x + (event.clientX - drag.clientX) / rect.width * 100));
      const y = Math.max(0, Math.min(96, drag.y + (event.clientY - drag.clientY) / rect.height * 100));
      updateQrCode({ x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) });
      return;
    }
    const block = blocks.find(item => item.id === drag.id);
    if (!block || !drag.id) return;
    const x = Math.max(0, Math.min(100 - block.width, drag.x + (event.clientX - drag.clientX) / rect.width * 100));
    const y = Math.max(0, Math.min(96, drag.y + (event.clientY - drag.clientY) / rect.height * 100));
    updateBlock(drag.id, { x: Number(x.toFixed(2)), y: Number(y.toFixed(2)) });
  };

  return (
    <div>
      <Collapse
        size="small"
        defaultActiveKey={['guide']}
        style={{ marginBottom: 16, background: 'rgba(255,255,255,.7)' }}
        items={[
          {
            key: 'guide',
            label: '模板制作教程',
            children: (
              <Space direction="vertical" size={10} style={{ width: '100%' }}>
                <Typography.Text>
                  先上传 1 页 PDF 底图，再把文字块拖到证书上对应的位置。文字块可以写普通文字，也可以混入字段，例如：
                  <Typography.Text code>{isTeam ? '恭喜<teamname>在本次比赛中获得<award>' : '恭喜<name>同学在本次比赛中获得<award>'}</Typography.Text>
                </Typography.Text>
                <Typography.Text>
                  多名同学有两种做法：如果证书上只需要一行获奖人名单，使用
                  <Typography.Text code>{'<members>'}</Typography.Text>
                  ，系统会按 Excel 这一行已填写的队员自动拼成“张三、李四、王五”。如果每个姓名要放在不同位置，就分别新建文字块写
                  <Typography.Text code>{'<member1name>'}</Typography.Text>
                  、
                  <Typography.Text code>{'<member2name>'}</Typography.Text>
                  ，一直可用到
                  <Typography.Text code>{'<member10name>'}</Typography.Text>
                  ；某行没有对应队员时，该字段会自动留空。
                </Typography.Text>
                  <Typography.Text type="secondary">
                  个人 Excel 表头：姓名、学号、学校、奖项、编号。团队 Excel 表头：队伍名称、队员1姓名、队员1学号、队员1学校，一直到队员10姓名、队员10学号、队员10学校，最后是奖项、编号。启用二维码后，系统会按每行编号生成验真链接。
                </Typography.Text>
              </Space>
            ),
          },
          {
            key: 'fields',
            label: `可添加字段（${isTeam ? '团队证书' : '个人证书'}）`,
            children: (
              <div style={{ display: 'grid', gap: 8 }}>
                {fieldHelp.map(item => (
                  <div
                    key={item.token}
                    style={{
                      display: 'grid',
                      gridTemplateColumns: '120px minmax(130px, 180px) 1fr',
                      gap: 12,
                      alignItems: 'center',
                      padding: '8px 10px',
                      border: '1px solid #edf1f5',
                      borderRadius: 6,
                      background: '#fff',
                    }}
                  >
                    <Typography.Text strong>{item.label}</Typography.Text>
                    <Typography.Text code copyable>{item.token}</Typography.Text>
                    <Typography.Text type="secondary">{item.source}，示例：{item.example}</Typography.Text>
                  </div>
                ))}
              </div>
            ),
          },
        ]}
      />
      <Alert
        type="info"
        showIcon
        message="占位符文字会作为一个完整句子排版"
        description="奖项和编号来自发放 Excel 的对应列；例如“恭喜<name>同学”会先替换姓名再整体测量，因此“同学”会随姓名长度自动移动。开启自适应后，超出设定宽度时会自动缩小字号。可拖动画布中的文字块定位。"
        style={{ marginBottom: 16 }}
      />
      <Row gutter={16}>
        <Col span={15}>
          <div
            ref={canvasRef}
            onPointerMove={onPointerMove}
            onPointerUp={() => { dragRef.current = null; }}
            onPointerLeave={() => { dragRef.current = null; }}
            style={{
              position: 'relative',
              minHeight: 360,
              overflow: 'hidden',
              background: '#f4f5f7',
              border: '1px solid #d9d9d9',
              borderRadius: 8,
              userSelect: 'none',
            }}
          >
            {fileId ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={`/api/certificate/grant/template/background/${fileId}`}
                alt="证书 PDF 首页"
                draggable={false}
                style={{ width: '100%', height: 'auto', display: 'block' }}
              />
            ) : (
              <Empty description="请先上传 PDF 模板" style={{ paddingTop: 120 }} />
            )}
            {fileId && blocks.map(block => (
              <div
                key={block.id}
                onPointerDown={(event) => {
                  event.preventDefault();
                  setSelectedId(block.id);
                  dragRef.current = { type: 'text', id: block.id, clientX: event.clientX, clientY: event.clientY, x: block.x, y: block.y };
                  event.currentTarget.setPointerCapture(event.pointerId);
                }}
                style={{
                  position: 'absolute',
                  left: `${block.x}%`,
                  top: `${block.y}%`,
                  width: `${block.width}%`,
                  color: block.color,
                  fontSize: `${Math.max(9, block.fontSize * 0.75)}px`,
                  fontFamily: 'sans-serif',
                  fontWeight: block.bold ? 700 : 400,
                  textDecoration: block.underline ? 'underline' : 'none',
                  textAlign: block.alignment.toLowerCase() as 'left' | 'center' | 'right',
                  whiteSpace: 'pre-wrap',
                  cursor: 'move',
                  outline: selectedId === block.id ? '2px solid #1677ff' : '1px dashed rgba(22,119,255,.55)',
                  background: selectedId === block.id ? 'rgba(22,119,255,.08)' : 'rgba(255,255,255,.12)',
                  lineHeight: 1.35,
                }}
              >
                {previewText(block.content)}
              </div>
            ))}
            {fileId && qrCode.enabled && (
              <div
                onPointerDown={(event) => {
                  event.preventDefault();
                  setSelectedId('__qr__');
                  dragRef.current = { type: 'qr', clientX: event.clientX, clientY: event.clientY, x: qrCode.x, y: qrCode.y };
                  event.currentTarget.setPointerCapture(event.pointerId);
                }}
                style={{
                  position: 'absolute',
                  left: `${qrCode.x}%`,
                  top: `${qrCode.y}%`,
                  width: `${qrCode.size}%`,
                  aspectRatio: '1 / 1',
                  cursor: 'move',
                  outline: selectedId === '__qr__' ? '2px solid #1677ff' : '1px dashed rgba(22,119,255,.55)',
                  background: 'transparent',
                  padding: 0,
                }}
              >
                <div
                  style={{
                    width: '100%',
                    height: '100%',
                    display: 'grid',
                    gridTemplateColumns: 'repeat(9, 1fr)',
                    gridTemplateRows: 'repeat(9, 1fr)',
                  }}
                >
                  {Array.from({ length: 81 }, (_, index) => (
                    <span
                      key={index}
                      style={{
                        background: qrPreviewCells.includes(index) ? '#000' : 'transparent',
                        minWidth: 0,
                        minHeight: 0,
                      }}
                    />
                  ))}
                </div>
              </div>
            )}
          </div>
          <Space wrap style={{ marginTop: 12 }}>
            <Button icon={<PlusOutlined />} onClick={addBlock}>添加文字块</Button>
            <Button icon={<QrcodeOutlined />} onClick={() => updateQrCode({ enabled: true })} disabled={qrCode.enabled}>添加二维码</Button>
            {availablePlaceholders.map(item => (
              <Tooltip key={item.value} title={`插入 ${item.value}`}>
                <Button size="small" disabled={!selected} onClick={() => insertPlaceholder(item.value)}>{item.label}</Button>
              </Tooltip>
            ))}
          </Space>
        </Col>
        <Col span={9}>
          <div style={{ maxHeight: 560, overflowY: 'auto', paddingRight: 4 }}>
            {blocks.length === 0 && !qrCode.enabled && <Empty description="请添加文字块或二维码" />}
            <Card
              size="small"
              title="证书二维码"
              onClick={() => setSelectedId('__qr__')}
              style={{ marginBottom: 10, borderColor: selectedId === '__qr__' ? '#1677ff' : undefined }}
              extra={<Checkbox checked={qrCode.enabled} onChange={event => updateQrCode({ enabled: event.target.checked })}>启用</Checkbox>}
            >
              <Typography.Text type="secondary">扫码访问当前部署站点，并自动带入证书编号查询真伪。</Typography.Text>
              <Row gutter={[8, 8]} style={{ marginTop: 10 }}>
                <Col span={8}><InputNumber addonBefore="X" min={0} max={100} value={qrCode.x} onChange={value => updateQrCode({ x: Number(value || 0) })} style={{ width: '100%' }} /></Col>
                <Col span={8}><InputNumber addonBefore="Y" min={0} max={100} value={qrCode.y} onChange={value => updateQrCode({ y: Number(value || 0) })} style={{ width: '100%' }} /></Col>
                <Col span={8}><InputNumber addonBefore="大小" min={5} max={40} value={qrCode.size} onChange={value => updateQrCode({ size: Number(value || 5) })} style={{ width: '100%' }} /></Col>
              </Row>
            </Card>
            {blocks.map((block, index) => (
              <Card
                key={block.id}
                size="small"
                title={`文字块 ${index + 1}`}
                onClick={() => setSelectedId(block.id)}
                style={{ marginBottom: 10, borderColor: selectedId === block.id ? '#1677ff' : undefined }}
                extra={<Button type="text" danger size="small" icon={<DeleteOutlined />} onClick={(event) => { event.stopPropagation(); removeBlock(block.id); }} />}
              >
                <Input.TextArea
                  value={block.content}
                  rows={2}
                  onChange={event => updateBlock(block.id, { content: event.target.value })}
                  placeholder="例如：恭喜<name>同学获得<award>"
                />
                <Row gutter={[8, 8]} style={{ marginTop: 10 }}>
                  <Col span={8}><InputNumber addonBefore="X" min={0} max={100} value={block.x} onChange={value => updateBlock(block.id, { x: Number(value || 0) })} style={{ width: '100%' }} /></Col>
                  <Col span={8}><InputNumber addonBefore="Y" min={0} max={100} value={block.y} onChange={value => updateBlock(block.id, { y: Number(value || 0) })} style={{ width: '100%' }} /></Col>
                  <Col span={8}><InputNumber addonBefore="宽" min={1} max={100} value={block.width} onChange={value => updateBlock(block.id, { width: Number(value || 1) })} style={{ width: '100%' }} /></Col>
                  <Col span={12}><InputNumber addonBefore="字号" min={6} max={120} value={block.fontSize} onChange={value => updateBlock(block.id, { fontSize: Number(value || 6) })} style={{ width: '100%' }} /></Col>
                  <Col span={12}><InputNumber addonBefore="最小" min={6} max={120} value={block.minFontSize} onChange={value => updateBlock(block.id, { minFontSize: Number(value || 6) })} style={{ width: '100%' }} /></Col>
                  <Col span={12}>
                    <Select
                      value="SIMSUN"
                      onChange={() => updateBlock(block.id, { fontFamily: 'SIMSUN' })}
                      style={{ width: '100%' }}
                      options={[{ label: '开源中文字体（Noto Sans SC）', value: 'SIMSUN' }]}
                    />
                  </Col>
                  <Col span={12}>
                    <Select
                      value={block.alignment}
                      onChange={value => updateBlock(block.id, { alignment: value })}
                      style={{ width: '100%' }}
                      options={[{ label: '左对齐', value: 'LEFT' }, { label: '居中', value: 'CENTER' }, { label: '右对齐', value: 'RIGHT' }]}
                    />
                  </Col>
                </Row>
                <Space wrap style={{ marginTop: 10 }}>
                  <Checkbox checked={block.autoFit} onChange={event => updateBlock(block.id, { autoFit: event.target.checked })}>文字自适应</Checkbox>
                  <Checkbox checked={!!block.bold} onChange={event => updateBlock(block.id, { bold: event.target.checked })}>加粗</Checkbox>
                  <Checkbox checked={!!block.underline} onChange={event => updateBlock(block.id, { underline: event.target.checked })}>下划线</Checkbox>
                  <label>颜色 <input type="color" value={block.color} onChange={event => updateBlock(block.id, { color: event.target.value })} /></label>
                </Space>
              </Card>
            ))}
          </div>
        </Col>
      </Row>
    </div>
  );
}

export const defaultTemplateBlocks = (isTeam: boolean): CertificateTextBlock[] => {
  const main = createBlock(0, isTeam);
  const number = { ...createBlock(1, isTeam), content: '证书编号：<number>', x: 65, y: 88, width: 30, fontSize: 12, alignment: 'RIGHT' as const };
  const date = { ...createBlock(2, isTeam), content: '<date>', x: 65, y: 82, width: 30, fontSize: 16, alignment: 'CENTER' as const };
  if (isTeam) {
    const members = { ...createBlock(3, isTeam), content: '获奖成员：<members>', x: 15, y: 50, width: 70, fontSize: 18, alignment: 'CENTER' as const };
    return [main, members, date, number];
  }
  return [main, number, date];
};

export const defaultQrCodeBlock = (): CertificateQrCodeBlock => ({
  enabled: false,
  x: 82,
  y: 72,
  size: 14,
});
