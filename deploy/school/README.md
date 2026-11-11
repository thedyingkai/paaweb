# BESTI ACM 校队部署配置

本目录用于 `https://bestiacm.thedyingkai.cn`，安装目录为 `/opt/paaweb`，Compose 项目名为 `paaweb-school`。公网 Nginx 终止 TLS，应用网关只映射到 `127.0.0.1:3403`。数据库、文件卷和网络独立创建。

平台定位为北京电子科技学院 ACM 校队，使用 BESTI ACM 文字标识；这不是学校官方校徽，也不是登峰杯专属平台。保留上游软件的 LICENSE 和第三方声明。

## 构建

在有 Java 21、Maven、Docker 的工作站构建，将结果加载到服务器。这样生产主机不累积 Maven、npm 和 Docker 构建缓存。

```powershell
cd backend
mvn.cmd -B -s settings.xml package -DskipTests
cd ..
docker build --build-arg JRE_IMAGE=public.ecr.aws/docker/library/eclipse-temurin:21-jre-alpine -f backend/Dockerfile.prebuilt -t paaweb-school-backend:20261111-besti-r5 backend
docker build --build-arg NODE_IMAGE=public.ecr.aws/docker/library/node:24-bookworm-slim --build-arg PUBLIC_SITE_URL=https://bestiacm.thedyingkai.cn -t paaweb-school-frontend:20261111-besti-r6 frontend
```

`Dockerfile.prebuilt` 使用刚刚构建的 JAR。前端构建运行 Next.js 编译和 TypeScript 检查。官方镜像的 ECR 地址用于 Docker Hub 不可达的网络；可通过构建参数切换回 Docker Hub。

## 服务器配置

将仓库根目录的 Compose 文件、SQL、网关配置和管理脚本复制到服务器，把本目录的 `docker-compose.override.yml` 复制到部署根目录。首次运行 `bash scripts/init-env.sh`，然后在权限为 `0600` 的 `.env` 中设置：

```dotenv
COMPOSE_PROJECT_NAME=paaweb-school
HOST_BIND=127.0.0.1
HOST_PORT=3403
PUBLIC_SITE_URL=https://bestiacm.thedyingkai.cn
SERVER_SERVLET_SESSION_COOKIE_SECURE=true
JAVA_TOOL_OPTIONS=-Xms128m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError
```

数据库密码由初始化脚本生成，不使用示例值。SMTP、QQ 机器人、爬虫和外部 OJ 需要各自配置；空配置不代表已接通这些服务。

确认 `172.30.34.0/24` 不与现有网络重叠。`gateway.conf` 基于通用网关，增加对宿主机地址 `172.30.34.1` 的真实来源 IP 和 HTTPS 转发支持；调整网段时必须同时更新两个网关配置文件。生产容器均有内存上限和日志轮转限制。

```bash
docker compose config --quiet
docker compose up -d --no-build --pull never db
# 数据库初始化完成后，通过 stdin 输入管理员新密码。
bash scripts/reset-admin-password.sh admin
docker compose up -d --no-build --pull never
```

在开放公网前更换初始化管理员密码，验证应用和管理员登录。随后安装 `bestiacm.thedyingkai.cn.conf` 到宿主机 Nginx 配置目录，运行 `nginx -t` 后 reload。公网配置保留 HTTP-01 验证路径，并将其他 HTTP 请求跳转到 HTTPS。

## TLS 与运行检查

证书使用独立的 ACME 账户和 HTTP-01 验证，密钥位于 `/opt/paaweb/tls`，该目录权限为 `0700`，私钥为 `0600`。`tools/acme_tiny.py` 使用 [acme-tiny](https://github.com/diafygi/acme-tiny) 的原始脚本。验证文件是公开的，需允许 Nginx 工作进程读取。

将本目录的 `paaweb-certificate.service` 和 `.timer` 安装到 `/etc/systemd/system/` 并启用 timer。续期脚本每天检查，在证书剩余不足 30 天时申请新证书，验证成功后替换并重新加载 Nginx；失败时保留可用证书。

```bash
docker compose ps
curl -fsS https://bestiacm.thedyingkai.cn/healthz
systemctl status paaweb-certificate.timer
df -h /
```

备份时保存 `paaweb-school_pg_data` 数据库、`paaweb-school_uploads` 上传文件及受保护的 `.env`、TLS 密钥；运行 `pg_dump` 获取一致的数据库备份。不要使用 `docker compose down -v` 删除业务卷。

## 校队模块升级

升级前使用 `pg_dump` 备份数据库，然后执行 `SQL/migration_besti_team_space.sql`。该迁移只新增校队档案、讨论与回复表，可重复执行；首次安装由 Compose 初始化挂载执行。

`/competitions` 说明赛事范围；`/team-space` 维护个人奖项、职务和参赛登记；`/community` 是仅已审核成员可访问的交流区。核验通过的奖项和职务显示在个人主页，私人补充材料和报名信息不公开。修改已经通过的记录会撤回公开展示，重新进入待核验。

赛事范围在前后端同时限制。联盟主办比赛、ICPC、CCPC、天梯赛允许有赛事管理权限的队长统一登记；蓝桥杯、马蹄杯、百度之星、CCSP、CNCC 仅本人自愿登记，批量接口拒绝这些类别。职务文字不影响系统权限。队长权限由管理后台分配，交流管理使用 `manage_user` 或 `manage_sys` 权限。

历史荣誉以 `docs/besti/records.json` 为可复核快照，通过管理员 `/api/intro/update` 写入 `honors` 类别，`metadata.json` 写入 `honors_meta`。统计以比赛队次计算，同场同队合并；来源歧义记录在元数据中。不要自动创建或绑定历史选手账号。

普通成员使用已有身份审核机制。SMTP 尚未配置时，邮箱验证码注册与账号激活不可用；部署完成不代表邮件功能已接通。管理员可在系统邮件配置中接入邮箱服务后开放成员注册。

## 职务任期与奖项分类（2026-11-11）

升级前备份数据库，执行 `SQL/migration_besti_roles_awards.sql` 后启动 r3 镜像。该迁移增加任期和奖项分类字段，保留历史数据且可重复执行。

r4 继续执行同一迁移，将奖项等次约束扩展为包含“优胜奖”；新增 XCPC 打铁与打星打铁的优胜奖认定，并移除荣誉统计表上方的“学校认定奖项（队次）”标题。

成员职务限定为科学之光技术联盟的社团负责人、主要负责人，计算机工程师联盟的负责人，以及 ACM 校队的校队队长、队员。任期结束当天仍为在任，从次日（Asia/Shanghai）起读接口自动返回“前”职务；保留原始职务名与日期。尚未开始的任期显示待任职。后台新增与自填职务均需明确日期，批量导入未填日期时按所选学年 9 月 1 日至次年 8 月 31 日处理。导入名单不自动授予管理员权限，同一成员可在同一学年兼任不同职务。

个人奖项包含级别（省部级、国家级、校级）与等次（一等奖、二等奖、三等奖、特等奖、优秀奖），前后端共同校验。ICPC、CCPC 区域赛与总决赛自动对应国家级，邀请赛对应省部级；蓝桥杯国赛对应省部级，省赛对应校级。历史竞赛榜单按相同规则换算，赛事金银铜对应学校认定一二三等奖，赛事优胜奖对应学校认定优秀奖，原始奖牌保留在来源说明。正式与打星等效奖项单独统计，并展示排名对应的总队伍数。

## 赛事奖项与学校认定分列（r5）

在职务奖项迁移之后执行 `SQL/migration_besti_xcpc_awards.sql`。该迁移新增 `contest_award` 保存 XCPC 的金牌、银牌、铜牌、优胜奖，并按已有认定等次回填；旧版将 XCPC 优胜奖写入认定等次的记录修正为优秀奖。原始赛事阶段和认定级别保留。

荣誉墙、首页、个人档案及公开个人主页均分别展示赛事奖项与学校认定；个人登记选择原始奖项后自动换算，后端拒绝原始奖项、阶段和认定结果不匹配的请求。历史快照保留原始来源 `award` / `equivalentAward`，使用 `contestAward` / `equivalentContestAward` 展示赛事奖项，`awardLevel` / `awardGrade` 等字段保存学校认定。打星只计在括号内。

## 打星展示调整（r6）

本次只升级前端至 `20261111-besti-r6`，后端继续使用 r5，无数据库迁移。比赛列表和展开详情移除打星的国家级／省部级认定文字，导出文件移除打星等效学校认定列；保留打星标记、等效赛事奖项以及统计表中的括号计数。
