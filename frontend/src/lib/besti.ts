import axios from "axios";

export const organizedFamilies = ["联盟主办", "ICPC", "CCPC", "天梯赛"];
export const voluntaryFamilies = [
  "蓝桥杯",
  "马蹄杯",
  "百度之星",
  "CCSP",
  "CNCC",
];
export const familyOptions = [...organizedFamilies, ...voluntaryFamilies].map(
  (value) => ({
    value,
    label: value === "联盟主办" ? "计算机工程师联盟主办" : value,
  }),
);
export const kindNames: Record<string, string> = {
  award: "获奖经历",
  position: "职务与任期",
  registration: "参赛登记",
};
export const statusNames: Record<string, string> = {
  pending: "待核验",
  verified: "已核验",
  rejected: "需补充材料",
};
export type TeamIdentity = {
  userId: number;
  name: string;
  moderator: boolean;
  coordinator: boolean;
  teamMember: boolean;
  communityAccess: boolean;
};
export async function teamApi<T = unknown>(
  path: string,
  method = "GET",
  data?: unknown,
): Promise<T> {
  const response = await axios({ url: "/api/team-space" + path, method, data });
  if (response.data.code !== 200)
    throw new Error(response.data.msg || "操作失败");
  return response.data.data;
}
export function teamError(error: unknown): string {
  if (axios.isAxiosError(error))
    return (
      error.response?.data?.msg ||
      error.response?.data?.message ||
      "请求失败，请稍后重试"
    );
  return error instanceof Error ? error.message : "操作失败";
}
