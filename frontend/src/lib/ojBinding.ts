export const ojBindingFields = [
  {
    name: 'newcoderId',
    label: '牛客竞赛 · 数字 UID',
    placeholder: '例如 123456，只填数字',
    help: '打开牛客竞赛个人主页，例如 ac.nowcoder.com/acm/contest/profile/123456，填写 /profile/ 后的数字 123456。',
    digitsOnly: true,
  },
  {
    name: 'luoguId',
    label: '洛谷 · 数字 UID',
    placeholder: '例如 123456，只填数字',
    help: '打开洛谷个人主页，例如 www.luogu.com.cn/user/123456，填写 /user/ 后的数字 123456。',
    digitsOnly: true,
  },
  {
    name: 'codeforcesId',
    label: 'Codeforces · 用户名（Handle）',
    placeholder: '例如 acmer_2026',
    help: '打开 Codeforces 个人主页，例如 codeforces.com/profile/acmer_2026，填写 /profile/ 后的用户名 acmer_2026。',
    digitsOnly: false,
  },
  {
    name: 'atcoderId',
    label: 'AtCoder · 用户名（User ID）',
    placeholder: '例如 acmer_2026',
    help: '打开 AtCoder 个人主页，例如 atcoder.jp/users/acmer_2026，填写 /users/ 后的用户名 acmer_2026。',
    digitsOnly: false,
  },
  {
    name: 'cwnuojId',
    label: '学校 OJ · 用户 ID（UID，可选）',
    placeholder: '填写学校 OJ 的用户 ID；没有可留空',
    help: '填写学校 OJ 个人资料中显示的用户 ID（UID）。没有学校 OJ 账号或不确定 ID 时，可先留空。',
    digitsOnly: false,
  },
] as const;
