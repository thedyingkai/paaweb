import dayjs from 'dayjs';
import utc from 'dayjs/plugin/utc';
dayjs.extend(utc);
export { dayjs };
export type RecruitmentWindow = {
  id: number; version: number; title: string; description: string; opens_at: string; closes_at: string;
  term: string; member_start: string; member_end: string; enabled: boolean;
};
export type TeamApplication = {
  id: number; window_id: number; user_id: number; version: number; title: string;
  real_name: string; student_number: string; school_name: string; motivation: string; experience: string;
  contact: string; gender: string; political_status: string; email: string; campus_rank: string;
  learning_content: string; student_work: string; platform_accounts: string;
  weekly_hours?: number; status: string; review_note: string; created_at: string;
};
export const statusLabels: Record<string,string> = { pending:'待审核', approved:'已通过', rejected:'未通过', withdrawn:'已撤回' };
export const windowLabels: Record<string,string> = { open:'申请开放中', upcoming:'尚未开始', closed:'申请已截止', paused:'未开放' };
export const windowState = (w: RecruitmentWindow, now = Date.now()) => !w.enabled ? 'paused' : now < dayjs(w.opens_at).valueOf() ? 'upcoming' : now < dayjs(w.closes_at).valueOf() ? 'open' : 'closed';
export const timeText = (value: string) => dayjs(value).utcOffset(8).format('YYYY-MM-DD HH:mm');
