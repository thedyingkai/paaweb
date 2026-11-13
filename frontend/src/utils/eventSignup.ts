import dayjs from 'dayjs';

export const eventSignupPeriod = (start: string, end?: string | null) =>
  `${dayjs(start).format('YYYY-MM-DD HH:mm')}${end ? ` 至 ${dayjs(end).format('YYYY-MM-DD HH:mm')}` : ' 起，不设截止时间'}`;

export const eventSignupCount = (count: number, maximum?: number | null) =>
  maximum == null ? `已确认 ${count || 0} 人` : `${count || 0} / ${maximum}`;
