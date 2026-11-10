# BESTI ACM · 北京电子科技学院 ACM 校队

本分支基于 [PAA Web](https://github.com/Jiangys-Crz/paaweb) 维护北京电子科技学院 ACM 校队平台，部署于 [bestiacm.thedyingkai.cn](https://bestiacm.thedyingkai.cn)。校队改造说明见 [部署文档](deploy/school/README.md)，历年成绩的来源、去重与统计口径见 [数据说明](docs/besti/README.md)。

- 个人维护奖项、职务、任期与参赛记录；核验后的奖项和职务展示在个人主页。
- 联盟主办比赛、ICPC、CCPC、天梯赛支持队长统一登记；蓝桥杯、马蹄杯、百度之星、CCSP、CNCC 仅本人自愿登记。
- 交流区仅已审核成员可见，支持主题、回复、编辑、置顶和锁定。
- 历史成绩包含 60 场比赛、127 条队伍参赛记录，已确认 5 银、3 铜，保留逐条来源。

下文保留上游通用功能、初始化与运维说明。线上管理员已替换初始密码；首次部署的新数据库仍需自行重置。

## PAA Web 基础功能

PAA Web 是面向程序设计竞赛、校内训练和学生组织的管理平台，提供门户展示、比赛报名、队伍管理、训练管理、电子证书、荣誉展示、练习统计和管理后台。前后端通过同一入口提供服务，可使用 Docker Compose 独立部署。

本仓库提供通用初始配置和最小基础数据。首次初始化不会导入真实人员、历史比赛、证书、报名记录或上传附件。

- 仓库地址：[github.com/Jiangys-Crz/paaweb](https://github.com/Jiangys-Crz/paaweb)
- 主应用原创部分许可：[MIT](LICENSE)。Hydro 衍生模板适用 AGPL-3.0；滚榜的 ICPC Tools 组件、参考字体及图片适用独立的 ICPC Software License，含非商业用途和公开源码条件；其他字体及图标另有许可，详见 [第三方说明](THIRD_PARTY_NOTICES.md)。
- 初始管理员：`admin`
- 初始密码：`admin123456`
- 默认访问地址：`http://localhost:3403`

> 首次登录后请立即修改管理员密码。数据库密码由初始化脚本随机生成，与管理员登录密码相互独立。

## 目录

- [功能与技术架构](#功能与技术架构)
- [快速部署](#快速部署)
- [首次配置](#首次配置)
- [环境变量](#环境变量)
- [部署结构与数据持久化](#部署结构与数据持久化)
- [域名与 HTTPS](#域名与-https)
- [日常运维](#日常运维)
- [备份与恢复](#备份与恢复)
- [本地开发](#本地开发)
- [可选集成](#可选集成)
- [常见问题](#常见问题)
- [贡献与发布](#贡献与发布)

## 功能与技术架构

| 模块 | 能力 |
| --- | --- |
| 门户与内容 | 公告、协会介绍、成员展示、制度、荣誉、展示背景 |
| 账号与组织 | 用户、学校、学院、专业、学期、认证、导入、账号状态与权限 |
| 比赛 | 比赛分类、个人/团队报名、队伍与成员、报名审核、准考证 |
| 训练与统计 | 训练发布、教师关联、训练报名、OJ 账号关联、练习与积分统计 |
| 电子证书 | 模板设计、发放、批量导入、预览/下载、身份复核、撤销、公开验真 |
| 通知与管理 | 站内消息、实时通知、邮件、系统配置、权限与操作审计 |
| 外部服务 | Hydro 插件、外部榜单、QQ 机器人、对象存储，可按需配置 |

技术组成：

- 前端：Next.js 16、React 19、TypeScript、Ant Design。
- 后端：Java 21、Spring Boot 3.2、MyBatis Plus。
- 数据库：PostgreSQL 15。
- 网关：Nginx，提供 `/api` 转发、附件受控下载、静态缓存和流量限制。
- 登录：服务端 Session；浏览器通过同源 Cookie 维持登录状态。当前口令接口保留双 MD5 兼容流程，部署时应启用 HTTPS；该兼容流程不等同于现代慢速密码哈希。

```text
浏览器
  │ HTTP / HTTPS
  ▼
外部 HTTPS 反向代理（部署者按需提供）
  │
  ▼
Nginx gateway :3403
  ├── /api/*、受控附件 ──► Spring Boot backend :8080
  │                              ├── PostgreSQL db :5432
  │                              └── uploads 数据卷
  └── 页面与静态资源 ──► Next.js frontend :3000
```

默认只将网关端口发布到宿主机，数据库、后端和前端通过内部 Docker 网络通信。

```text
.
├── backend/                 # Java 后端、单元/集成测试、Dockerfile
├── frontend/                # Next.js 前端与回归检查
├── SQL/
│   ├── schema.sql           # 全新安装使用的完整数据库结构
│   └── seed.sql             # 最小基础数据与默认管理员
├── deploy/
│   ├── nginx/               # 网关及转发配置
│   ├── napcat/              # 可选 QQ 机器人部署示例
│   └── compose.dev.yml      # 本地开发用数据库端口覆盖配置
├── hydro-api/               # 可选 Hydro 扩展，独立安装
├── Board-Tools/             # 可选榜单采集工具
├── maintenance/             # 可选静态维护页
├── scripts/                 # 环境初始化与维护/验证工具
├── .env.example             # 无私有凭证的配置模板
├── docker-compose.yml       # 核心服务编排
└── up.sh                    # 初始化、构建、启动与可用性检查
```

## 快速部署

### 1. 准备环境

建议准备至少 2 核 CPU、4 GB 内存、10 GB 可用磁盘；上传附件和构建缓存较多时需要增加磁盘容量。构建期间的内存需求通常高于运行阶段。

需要安装：

- Docker Engine / Docker Desktop，启用 Docker Compose v2.20+ 或兼容版本。
- Git、Bash、OpenSSL、curl。
- 能访问容器镜像源、Maven 与 npm 依赖源的网络。

使用 Docker 部署时，宿主机无需另行安装 Java、Node.js 或 PostgreSQL。Linux、macOS 和启用了 Linux 容器的 Windows 环境均可使用；Windows 建议在 WSL2 中执行以下命令。本文终端示例按 Bash 编写。

### 2. 获取代码

```bash
git clone https://github.com/Jiangys-Crz/paaweb.git
cd paaweb
```

### 3. 初始化配置

```bash
bash scripts/init-env.sh
```

脚本根据 `.env.example` 创建权限为 `600` 的 `.env`，生成独立随机数据库密码；重复执行会保留已有配置，不会重置数据或管理员密码。`.env` 已加入忽略规则，不能提交到 Git。

编辑 `.env` 中的站点地址：

```dotenv
HOST_PORT=3403
PUBLIC_SITE_URL=http://localhost:3403
```

服务器部署时，将 `PUBLIC_SITE_URL` 改为实际访问地址，例如 `https://paa.example.com`。需要邮件或 OJ 功能时，再填写相应可选配置。

也可以手动复制 `.env.example` 为 `.env`，但必须将 `POSTGRES_PASSWORD` 占位符替换为足够长的随机密码。密码包含 `$`、`#` 或空格时应遵循 Compose 环境文件的引用规则；初始化脚本生成的十六进制密码无需额外转义。

### 4. 启动

```bash
bash up.sh
```

首次构建会下载依赖，需要一定时间。脚本完成后访问 `http://localhost:3403`，进入 `/login`，选择密码登录，使用：

```text
账号：admin
密码：admin123456
```

管理员入口为 `/admin`。首次部署可在不配置 SMTP、Hydro、QQ 机器人和对象存储的情况下完成密码登录和核心功能配置。

如果希望手动执行相同步骤：

```bash
docker compose config --quiet
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:3403/healthz
curl -fsS http://localhost:3403/api/intro/showcase_bg
```

`/healthz` 只表示 Nginx 网关存活；后端是否就绪还应检查第二个接口返回的 `code` 是否为 `200`。`up.sh` 会同时检查首页与后端接口，未通过时以非零状态退出。

## 首次配置

建议按以下顺序完成后台设置：

1. 首次部署后，在服务器项目根目录运行 `bash scripts/reset-admin-password.sh`，按提示输入并确认新密码。脚本不依赖邮箱或 SMTP，并使管理员已有登录会话失效；随后使用新密码登录。
2. 在组织管理中建立本单位的学校、学院、专业与学期。保留的通用示例组织可按实际需要修改。
3. 配置门户介绍、制度、成员、公告、荣誉和展示背景。
4. 在系统服务配置中填写 SMTP，并测试邮件发送；完成后再开放需要邮箱验证码的注册、激活与找回流程。
5. 根据业务设置用户权限、比赛分类、证书类型与模板，再发布比赛和训练。
6. 如需对接外部系统，在后台创建自己的应用密钥，并为该应用授予所需权限。

初始账号只在全新数据库初始化时创建。重启、重新构建或执行 `init-env.sh` 不会覆盖已修改的密码。已有数据库也不会重新执行 `seed.sql`。

### 管理员密码重置

初始管理员没有预设邮箱。可以直接在服务器项目目录执行：

```bash
bash scripts/reset-admin-password.sh
```

密码通过交互输入，不会显示在终端，也不应作为命令参数或写入 `.env`。默认处理账号 `admin`；如部署中有其他超级管理员，可在命令后提供该账号名。脚本直接更新当前 Compose 实例数据库，并增加账号安全版本，撤销该账号原有登录会话；不会恢复默认密码或重置其他业务数据。

执行前数据库应已就绪：

```bash
docker compose up -d --wait db
bash scripts/reset-admin-password.sh
```

部署者不需要先配置 SMTP 就能完成首次改密。后台/个人资料中的邮箱验证码改密流程，需要绑定邮箱并配置邮件后使用。若忘记密码，也可在拥有服务器管理权限时使用同一脚本重置。

## 环境变量

根目录 `.env` 由 Compose 读取。不要将它用于存放需要提交的公共文档。

| 变量 | 默认/用途 |
| --- | --- |
| `COMPOSE_PROJECT_NAME` | `paaweb`；决定容器、网络、数据卷的命名前缀 |
| `HOST_BIND` | `0.0.0.0`；网关监听地址；仅本机代理访问时可设为 `127.0.0.1` |
| `HOST_PORT` | `3403`；宿主机访问端口 |
| `TZ` | `Asia/Shanghai` |
| `POSTGRES_DB` | `paaweb`；数据库名 |
| `POSTGRES_USER` | `paaweb`；数据库用户 |
| `POSTGRES_PASSWORD` | 必填；初始化脚本生成随机值 |
| `PUBLIC_SITE_URL` | `http://localhost:3403`；公开站点地址，供页面元信息、邮件和证书验真链接使用 |
| `PUBLIC_VP_SITE_URL` | 留空；填写 HTTP/HTTPS 地址后在页脚显示“VP系统”链接 |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | 默认 `false` 便于本机 HTTP；公开站点使用 HTTPS 时设为 `true` |
| `CORS_ALLOWED_ORIGINS` | 默认空时只允许站点地址；填写后覆盖允许列表，本地开发可设 `http://localhost:3000`，多个地址用逗号分隔 |
| `SPRING_MAIL_HOST` | 默认空；SMTP 服务器 |
| `SPRING_MAIL_PORT` | `587` |
| `SPRING_MAIL_USERNAME` | 默认空；SMTP 账号 |
| `SPRING_MAIL_PASSWORD` | 默认空；SMTP 密码/授权码 |
| `SPRING_MAIL_PROTOCOL` | `smtp` |
| `SPRING_MAIL_SSL_ENABLED` | `false`；例如使用 465 隐式 TLS 时改为 `true` |
| `SPRING_MAIL_STARTTLS_ENABLED` | `true`；按服务商要求配置 |
| `HYDRO_BASE_URL` | 默认空；可选 Hydro 站点地址 |
| `NEXT_PUBLIC_SCHOOL_OJ_URL` | 默认空；前端学校 OJ 跳转地址，修改后需要重新构建前端 |
| `QQ_BOT_IMAGE_HOSTS` | 默认空；额外允许机器人读取图片的 HTTPS 主机，多个主机用逗号分隔；站点自身主机自动允许 |
| `CRAWLER_SERVICE_URL` | `http://localhost:8000`；可选练习统计采集服务地址；Compose 不内置该服务 |
| `JAVA_TOOL_OPTIONS` | 可在 `.env` 中覆盖 JVM 内存参数，默认最大堆内存为 `768m` |

`PUBLIC_SITE_URL` 和 `NEXT_PUBLIC_SCHOOL_OJ_URL` 会参与前端构建，修改后运行 `docker compose up -d --build frontend backend`，再重建网关。后端的 `FILE_UPLOAD_PATH` 在 Compose 中设为 `/app/uploads/`，与共享附件数据卷一致。

SMTP 示例（请替换为自己的服务商配置）：

```dotenv
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=mailer@example.com
SPRING_MAIL_PASSWORD=your-own-smtp-credential
SPRING_MAIL_PROTOCOL=smtp
SPRING_MAIL_SSL_ENABLED=false
SPRING_MAIL_STARTTLS_ENABLED=true
```

SMTP 和对象存储也可以在后台配置；后台保存的运行时设置可能覆盖环境中的初始设置。正式部署前应在后台使用测试功能验证实际生效配置。

## 部署结构与数据持久化

| 数据 | 位置 | 说明 |
| --- | --- | --- |
| 用户、报名、配置、证书记录等 | `pg_data` 命名卷 | PostgreSQL 持久化数据 |
| 上传的图片与附件 | `uploads` 命名卷 | 后端写入，Nginx 只读挂载 |
| 环境配置与数据库凭证 | 宿主机 `.env` | 不进入镜像与版本库 |
| 可选对象存储文件 | 部署者配置的存储桶 | 需单独备份并维护访问策略 |

默认卷名形如 `paaweb_pg_data`、`paaweb_uploads`，网络名为 `paaweb_app_network`。使用其他项目名时前缀会变化。

全新数据卷首次启动时按顺序执行 `SQL/schema.sql`、`SQL/seed.sql`。这些文件用于新装环境；已有数据库的结构变更应结合版本发布说明和备份处理，不能直接覆盖运行库。

`docker compose down` 保留数据卷。`docker compose down -v` 会删除数据库和附件，应只用于明确要清空的测试环境。更改项目名会连接到另一组数据卷，看起来像“数据丢失”；原卷通常仍在，可通过 `docker volume ls` 检查。

当前登录态保存在后端进程中，重启后端后需要重新登录。默认部署适用于单后端实例；扩容为多实例前需要自行配置共享会话和分布式任务协调。

## 域名与 HTTPS

建议由宿主机的 Nginx、Caddy 或其他入口服务终止 HTTPS，再将请求转发到 `127.0.0.1:3403`。

例如外部 Nginx：

```nginx
server {
    listen 443 ssl;
    server_name paa.example.com;

    ssl_certificate     /path/to/fullchain.pem;
    ssl_certificate_key /path/to/privkey.pem;

    client_max_body_size 21m;
    location / {
        proxy_pass http://127.0.0.1:3403;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-For $remote_addr;
        proxy_buffering off;
        proxy_read_timeout 3600s;
    }
}
```

证书文件由部署者自行申请、续期，不能提交到仓库。配套环境建议设置：

```dotenv
HOST_BIND=127.0.0.1
PUBLIC_SITE_URL=https://paa.example.com
SERVER_SERVLET_SESSION_COOKIE_SECURE=true
```

内置网关会覆盖来自客户端的转发头，避免直接访问时伪造来源绕过限流。若外层还有反向代理，默认会按该代理的地址进行部分限流；需要保留真实用户 IP 时，应仅信任明确的代理网段，在 `deploy/nginx/nginx.conf` 中配置 `set_real_ip_from`、`real_ip_header`，并禁止绕过外部代理访问网关。

修改内置 Nginx 配置后使用：

```bash
docker compose up -d --no-deps --force-recreate gateway
docker compose exec -T gateway nginx -t
```

## 日常运维

```bash
# 查看状态
docker compose ps

# 查看最近日志
docker compose logs --tail=100 backend frontend gateway

# 持续查看后端日志
docker compose logs -f --tail=100 backend

# 停止服务，保留数据
docker compose down

# 重新启动现有服务
docker compose up -d

# 获取更新并重新构建（先完成备份）
git pull --ff-only
bash up.sh
```

更新前阅读相关版本说明，特别关注数据库结构变化。不要将原私有部署的数据备份直接放入代码仓库。

内置限制包括上传大小、请求速率、连接数、附件下载带宽和实时通知连接。普通附件上传上限为 20 MB，部分 Excel 导入接口有单独限制；调整时需同步检查 Nginx、Spring multipart 配置和业务校验。

## 备份与恢复

数据库与附件必须配套备份。以下命令在项目根目录执行，备份目录已被 Git 忽略。

### 创建备份

维护窗口内停止应用写入，再备份数据库和本地附件：

```bash
mkdir -p backups
chmod 700 backups
docker compose stop gateway frontend backend

docker compose exec -T db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > backups/database.dump

docker compose run --rm --no-deps -T --entrypoint sh backend \
  -c 'tar -C /app/uploads -czf - .' > backups/uploads.tar.gz

# 备份本机配置；离线归档时应与数据备份分开存储
cp .env backups/deployment.env
chmod 600 backups/deployment.env

docker compose start backend frontend gateway
```

将备份复制到受控的离线或远程位置，并将 `deployment.env` 与数据库、附件备份分开保管。数据库备份、附件以及 `.env` 都可能包含私有信息，不应上传到公开 Issue 或仓库。启用对象存储时还需另行备份存储桶。

### 恢复备份

以下操作会替换目标实例的数据，只能在已确认的恢复环境执行。先准备同版本代码及 `.env`，启动数据库，再恢复：

```bash
docker compose up -d --wait db
docker compose stop gateway frontend backend

# 清空并恢复应用对象；保留 PostgreSQL 系统对象
docker compose exec -T db sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner --exit-on-error' \
  < backups/database.dump

# 目标附件卷应为空；不要直接与另一套业务附件混合
docker compose run --rm --no-deps -T --entrypoint sh backend \
  -c 'tar -C /app/uploads -xzf -' < backups/uploads.tar.gz

docker compose up -d backend frontend
docker compose up -d --no-deps --force-recreate gateway
```

恢复完成后验证管理员登录、比赛数据、受控附件下载和证书预览。目标服务仍在启动时，请先等待数据库健康检查通过再执行恢复命令。

## 本地开发

宿主机需要 Java 21、Maven 3.9+、Node.js 22+ 和 npm。数据库可以复用 Compose 的初始化流程，并通过覆盖配置仅向本机暴露端口。

### 数据库与后端

```bash
bash scripts/init-env.sh
docker compose -f docker-compose.yml -f deploy/compose.dev.yml up -d db
```

在后端终端中配置实际数据库信息后启动；`POSTGRES_PASSWORD` 的值从本机 `.env` 获取，不要将实际密码写入脚本：

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/paaweb
export SPRING_DATASOURCE_USERNAME=paaweb
read -rs -p 'Database password: ' SPRING_DATASOURCE_PASSWORD
export SPRING_DATASOURCE_PASSWORD
printf '\n'
export PUBLIC_SITE_URL=http://localhost:3000
export FILE_UPLOAD_PATH="$PWD/uploads/"

cd backend
mvn spring-boot:run
```

若 5432 被占用，可通过 `DB_PORT=5434 docker compose ...` 指定宿主机映射端口，同时修改 `SPRING_DATASOURCE_URL`。后端默认端口为 8080。

在 Maven 环境不方便访问默认依赖源时，可以检查 `backend/settings.xml` 并按需使用 `mvn -s settings.xml ...`；该文件只提供公共镜像配置，不含账号。

### 前端

新开终端：

```bash
cd frontend
npm ci
API_URL=http://localhost:8080 PUBLIC_SITE_URL=http://localhost:3000 npm run dev
```

访问 `http://localhost:3000`。前端将 `/api/*` 转发到后端，浏览器继续使用同源会话。

生产构建与已有回归检查：

```bash
cd frontend
npm run build
npm run test:security
npm run test:qq-bot-security
npm run test:board-progress
npm run test:board-participants

cd ../backend
mvn test
```

Java 源码包名和部分历史接口字段保留兼容命名；它们属于程序标识符，部署时无需修改，也不关联任何外部账号。

## 可选集成

核心平台不依赖这些集成启动，按需要单独配置。

### SMTP 邮件

填写环境变量或在后台系统服务设置中配置，并执行测试发送。SMTP 未配置时，密码登录和管理功能可使用，但验证码注册、邮箱登录与邮件通知需要先完成配置。

### 对象存储

默认使用本地附件卷。后台支持 S3 兼容存储配置；启用前准备自己的 endpoint、bucket、access key、secret key 和访问策略。对象存储密钥保存在运行库中，数据库备份也应按敏感资料管理。

### Hydro 与外部榜单

参见 [hydro-api/README.md](hydro-api/README.md)。插件需要部署到独立 Hydro 服务，主 Compose 不会自动安装或启动 Hydro。

- 比赛接口与榜单插件：为赛事提供数据访问或外部展示能力。
- `paa-external-login`：对接本平台的外部登录。
- `paa-proctor-auth`：提供可选监考鉴权对接。
- `rating-system`：可选 Rating 展示插件。
- `algospark-hydro-bridge-v0.2.0`：独立桥接组件，遵循其包内说明。

在后台创建独立应用密钥，按需授权，再填入 Hydro 的私有配置。不要将真实应用密钥写回插件源代码。

榜单采集工具见 [Board-Tools](Board-Tools/)，需自行配置比赛地址、身份和推送目标。采集游标属于运行数据，不应提交。

### QQ 机器人

参见 [deploy/napcat/README.md](deploy/napcat/README.md)。NapCat/OneBot 服务单独启动，然后在后台配置连接和权限。使用不同的 Compose 项目名时，将 `PAAWEB_NETWORK` 指向对应的 `<项目名>_app_network`。

机器人图片只允许从本站及额外配置的 HTTPS 主机读取。需要其他图片来源时，将主机名填入 `QQ_BOT_IMAGE_HOSTS`；保持为空不允许其他远程图片来源。

### 练习统计采集服务

`CRAWLER_SERVICE_URL` 指向外部采集服务；仓库未提供通用的采集服务镜像。在容器中，`localhost` 指当前容器自身。需要启用此功能时，应配置为 Docker 网络可达的服务地址或部署者提供的 URL。

### 维护页

参见 [maintenance/README.md](maintenance/README.md)。静态维护页用于外部入口的维护切换，需要自行集成外层反向代理。核心 `up.sh` 不依赖维护页开关。

## 常见问题

### 默认管理员无法登录

确认选择的是密码登录，并输入 `admin`、`admin123456`。该密码仅适用于全新初始化数据库。已有实例的密码不会因更新代码或重建镜像而恢复默认。检查是否误用了其他项目的数据卷，以及数据库初始化日志是否报错。

### 页面打开但接口返回 502

运行 `docker compose ps` 和 `docker compose logs --tail=100 backend`，等待 Java 服务启动。若后端或前端重新创建后网关仍引用旧地址，执行 `docker compose up -d --no-deps --force-recreate gateway`。

### 修改数据库密码后连接失败

PostgreSQL 初始化环境变量只在空数据目录时生效。仅修改 `.env` 不会修改已有数据库用户的密码。应先通过数据库管理方式变更该用户密码，再同步 `.env` 并重建后端；不要为了修改密码删除业务数据卷。

### 注册或验证码邮件无法发送

检查后台 SMTP 设置是否覆盖环境变量；确认端口、TLS 方式、授权码和服务商发信限制。默认配置不提供共享邮箱或邮件凭证。

### OJ、榜单、机器人或采集统计无数据

检查可选服务是否已单独部署、网络是否可达、应用密钥及授权是否正确。空配置不会连接任何预设的原站点。学校 OJ 链接变更后需要重建前端。

### 端口已被占用

在 `.env` 中修改 `HOST_PORT`，同步修改 `PUBLIC_SITE_URL`，再执行 `bash up.sh`。多个实例需要不同的项目名与访问端口。

### 中文证书乱码或字体不同

后端随包提供开源 Noto Sans SC 中文字体及其 OFL 许可。自定义模板指定的字体需要在部署环境中可用；本地 Java 运行时也应安装中文字体。仓库不附带商业字体、机构印章或真实证书背景。

### 构建时无法下载依赖

先检查 Docker 镜像源、Maven 与 npm 网络访问。可以按网络环境配置公共镜像或代理，但不要提交本地镜像账号、代理凭证和证书私钥。不要通过跳过数据库初始化错误或复制现成运行数据来完成部署。

## 贡献与发布

欢迎通过 Issue 和 Pull Request 提交改进。提交时说明问题、预期行为、验证方式；涉及数据库变化时提供可审阅的迁移说明。

发布前建议检查：

```bash
python3 scripts/check-public-release.py
docker compose config --quiet
```

- `.env`、备份、附件、日志、IDE 配置和生成目录均不应进入版本库。
- 示例使用 `example.com`、合成用户与占位符，不放真实邮箱、群二维码、手机号或业务记录。
- 对新增文件执行相应的构建、回归检查和一次空数据库部署验证。
- 变更默认管理员初始化时，要同步更新本文档并验证真实登录流程。
- 主应用使用 [MIT License](LICENSE)；[第三方说明](THIRD_PARTY_NOTICES.md) 中列出的组件适用各自许可。新增代码、字体或图片时保留必要许可与来源。

扫描脚本用于发现常见泄漏和残留，不能替代对新引入资料的人工检查。


## 外榜、滚榜与报名更新

外榜支持 XCPC、天梯赛和蓝桥杯，提供响应式题格、按排名排列的搜索置顶、悬浮提交详情和完整长图 PDF 导出。外榜名称按“比赛名称 - 场次”显示；场次可填写正式赛、热身赛或自定义文字。滚榜开场以比赛名称为主标题、场次为副标题。

外榜与滚榜分别配置。XCPC 的滚榜在每个外榜中独立开启，可设置密码、封榜时间、播放间隔、队伍照片、中央赛事封面、学校/学院 LOGO 和奖项。默认显示冠军、亚军、季军及“最佳女队 Best Female”，均可调整；打星队伍不计正式名次。滚榜仅替换中央赛事图片，保留标题和 ICPC Tools 署名。按下空格可推进，Shift+Q 退出；完整键位见滚榜帮助。状态色统一为一血 `#1B5E20`、封榜 `#1447E6`、AC `#4CAF50`、WA `#D32F2F`。

报名指导老师保持选填，提示“选填指导老师（可填班主任）”，多位老师通过“添加”逐个录入。报名结束后不可取消报名；名单公示复用外榜参赛者卡片。后台 Excel 导出读取全部筛选结果，不受列表每页 100 条的限制。批量邮件可按“学校＋学号”粘贴或导入 Excel，收件地址取自站内账号已绑定邮箱；导入后仍需确认发送。

### 已有部署升级

先备份数据库和上传数据，再在**启动新后端之前**按以下顺序执行新增迁移：

```bash
bash -e <<'BASH'
for migration in \
  migration_external_board_resolver.sql \
  migration_external_board_photos.sql \
  migration_organization_logos.sql \
  migration_resolver_playback.sql \
  migration_resolver_settings.sql \
  migration_external_board_session_names.sql \
  migration_signup_advisor_format.sql
do
  docker compose exec -T db sh -c 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < "SQL/$migration"
done
BASH
```

这些脚本可重复执行；已有外榜默认不启用滚榜。场次脚本仅整理由旧迁移复制的完整比赛名称，自定义场次不变；指导老师脚本仅规范历史数组/方括号格式，保留姓名及顺序。首次全新部署使用更新后的 `schema.sql` 和通用 `seed.sql`，无需额外执行这些迁移。

迁移成功后运行 `bash up.sh` 构建启动新版本。滚榜照片写入现有 uploads 数据卷，数据库和 uploads 必须一起备份。照片 ZIP 最大 512 MB、单图解压后最大 32 MB；公网反向代理的请求大小和超时应与内部网关保持一致。

滚榜参考组件、字体及图片随包提供并保留原许可；使用前请阅读 [第三方说明](THIRD_PARTY_NOTICES.md#icpc-tools-resolver-与参考资源)，根目录 MIT 许可不覆盖这些独立第三方资源。

### 只读检查与本地回归

```bash
python3 scripts/check-public-release.py
cd frontend
npm ci
npm run test:resolver
node scripts/resolver-awards-regression.mjs
node scripts/award-team-selection-regression.mjs
```

需要测试封榜权限时，可对隔离的测试榜设置 `RESOLVER_TEST_PASSWORD` 环境变量，再运行 `python3 scripts/test-resolver-http.py http://localhost:3403/api 外榜ID`。脚本只读取公开接口，不解除封榜或修改比赛数据。不要把真实比赛包、密码、上传照片、日志或数据库备份加入仓库。
