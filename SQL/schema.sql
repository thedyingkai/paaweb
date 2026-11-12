-- PAAWeb database schema (PostgreSQL 15+). Run once on an empty database.
SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET search_path = public;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TABLE public.admin_permissions (
    id integer NOT NULL,
    user_id bigint NOT NULL,
    permissions text NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE public.admin_permissions IS '管理员权限表';

COMMENT ON COLUMN public.admin_permissions.permissions IS 'JSON数组存储的权限标识';

CREATE SEQUENCE public.admin_permissions_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.admin_permissions_id_seq OWNED BY public.admin_permissions.id;

CREATE TABLE public.association_member (
    member_id bigint NOT NULL,
    term character varying(50) NOT NULL,
    department character varying(50) NOT NULL,
    user_id bigint,
    real_name character varying(50),
    school_name character varying(100),
    number_id character varying(50),
    avatar_file_id bigint,
    "position" character varying(50) NOT NULL,
    introduction character varying(255),
    sort_order integer DEFAULT 0 NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN public.association_member.school_name IS '导入时的学校（大学）名称，与 number_id 组成身份关键字';

COMMENT ON COLUMN public.association_member.number_id IS '导入时的学号，与 school_name 组成身份关键字';

CREATE SEQUENCE public.association_member_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.association_member_member_id_seq OWNED BY public.association_member.member_id;

CREATE TABLE public.audit_records (
    audit_id bigint NOT NULL,
    biz_type character varying(50) NOT NULL,
    file_id integer,
    status character varying(50) NOT NULL,
    comments text NOT NULL,
    user_id integer NOT NULL,
    applicant_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.audit_records_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.audit_records_audit_id_seq OWNED BY public.audit_records.audit_id;

CREATE TABLE public.certificate_grant (
    certificate_download_id bigint NOT NULL,
    certificate_type_id bigint NOT NULL,
    user_id bigint,
    create_time timestamp without time zone DEFAULT now(),
    custom_name character varying(500),
    user_account character varying(50),
    real_name character varying(100),
    group_id character varying(50),
    issue_id bigint,
    school_name character varying(100)
);

COMMENT ON COLUMN public.certificate_grant.school_name IS '获奖人所属学校（大学）名称，用于按学校+学号匹配用户；未登录获奖人据此在注册后关联';

CREATE SEQUENCE public.certificate_grant_certificate_download_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_grant_certificate_download_id_seq OWNED BY public.certificate_grant.certificate_download_id;

CREATE TABLE public.certificate_issue (
    issue_id bigint NOT NULL,
    certificate_type_id bigint NOT NULL,
    template_file_id bigint,
    certificate_no character varying(100) NOT NULL,
    certificate_kind smallint DEFAULT 0 NOT NULL,
    level_scope character varying(20),
    team_name character varying(200),
    award_name character varying(200) NOT NULL,
    issue_date date NOT NULL,
    status character varying(20) DEFAULT 'active'::character varying NOT NULL,
    template_schema_snapshot text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    revoked_at timestamp without time zone,
    CONSTRAINT certificate_issue_certificate_kind_check CHECK ((certificate_kind = ANY (ARRAY[0, 1]))),
    CONSTRAINT certificate_issue_status_check CHECK (((status)::text = ANY ((ARRAY['active'::character varying, 'revoked'::character varying])::text[])))
);

CREATE SEQUENCE public.certificate_issue_issue_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_issue_issue_id_seq OWNED BY public.certificate_issue.issue_id;

CREATE TABLE public.certificate_issue_member (
    issue_member_id bigint NOT NULL,
    issue_id bigint NOT NULL,
    member_name character varying(100) NOT NULL,
    student_id character varying(50) NOT NULL,
    school_name character varying(100) NOT NULL,
    user_id bigint,
    sort_order integer DEFAULT 0 NOT NULL
);

COMMENT ON COLUMN public.certificate_issue_member.school_name IS '成员所属学校（大学）名称，与 student_id 组成成员唯一身份';

CREATE SEQUENCE public.certificate_issue_member_issue_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_issue_member_issue_member_id_seq OWNED BY public.certificate_issue_member.issue_member_id;

CREATE TABLE public.certificate_level (
    level_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    level_type character varying(20) NOT NULL,
    rank_type character varying(20) NOT NULL,
    base_rating integer NOT NULL,
    sort_weight integer DEFAULT 0
);

CREATE SEQUENCE public.certificate_level_level_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_level_level_id_seq OWNED BY public.certificate_level.level_id;

CREATE TABLE public.certificate_member (
    member_id bigint NOT NULL,
    certificate_verify_id bigint NOT NULL,
    user_id bigint,
    name character varying(100) NOT NULL,
    student_id character varying(50) NOT NULL,
    school_name character varying(200),
    is_captain smallint DEFAULT 0,
    sort_order integer DEFAULT 99
);

COMMENT ON COLUMN public.certificate_member.school_name IS 'Verified school snapshot. NULL legacy recipients require manual confirmation.';

CREATE SEQUENCE public.certificate_member_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_member_member_id_seq OWNED BY public.certificate_member.member_id;

CREATE TABLE public.certificate_type (
    certificate_type_id bigint NOT NULL,
    type_name character varying(50) NOT NULL,
    type_id bigint NOT NULL,
    url_id bigint NOT NULL,
    "time" timestamp without time zone NOT NULL,
    term bigint NOT NULL,
    update_user_id bigint NOT NULL,
    font_size integer DEFAULT 24,
    level_scope character varying(20),
    template_schema text,
    rating_enabled boolean DEFAULT true NOT NULL,
    is_team integer DEFAULT 0
);

CREATE SEQUENCE public.certificate_type_certificate_type_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_type_certificate_type_id_seq OWNED BY public.certificate_type.certificate_type_id;

CREATE TABLE public.certificate_verify (
    certificate_verify_id bigint NOT NULL,
    user_id bigint NOT NULL,
    level_id bigint NOT NULL,
    category_id bigint NOT NULL,
    event_name character varying(200) NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying,
    comments text NOT NULL,
    file_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    replay_time timestamp without time zone,
    term bigint,
    "time" timestamp without time zone NOT NULL,
    audit_user_id bigint,
    sub_project_name character varying(255),
    is_team smallint DEFAULT 0,
    advisor character varying(255)
);

CREATE SEQUENCE public.certificate_verify_certificate_verify_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.certificate_verify_certificate_verify_id_seq OWNED BY public.certificate_verify.certificate_verify_id;

CREATE TABLE public.contest_category (
    category_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    level character varying(50) NOT NULL,
    intro text NOT NULL,
    logo_file_id bigint,
    status smallint DEFAULT 1
);

CREATE SEQUENCE public.contest_category_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.contest_category_category_id_seq OWNED BY public.contest_category.category_id;

CREATE TABLE public.event (
    event_id bigint NOT NULL,
    category_id bigint NOT NULL,
    term_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    is_team smallint DEFAULT 0,
    min_member integer DEFAULT 1,
    max_member integer DEFAULT 1,
    signup_start timestamp without time zone NOT NULL,
    signup_end timestamp without time zone NOT NULL,
    event_start timestamp without time zone NOT NULL,
    event_end timestamp without time zone NOT NULL,
    status smallint DEFAULT 1,
    max_signup integer NOT NULL,
    description text NOT NULL,
    is_external_open boolean DEFAULT false,
    is_unfrozen boolean DEFAULT false,
    admission_ticket_open boolean DEFAULT false,
    admission_ticket_rules text,
    actual_start_time timestamp without time zone,
    actual_end_time timestamp without time zone,
    medal_config text,
    type character varying(50) DEFAULT 'XCPC'::character varying,
    freeze_time timestamp without time zone
);

COMMENT ON COLUMN public.event.admission_ticket_open IS '是否开放参赛用户下载准考证';

COMMENT ON COLUMN public.event.admission_ticket_rules IS '准考证底部显示的比赛主要规则';

CREATE SEQUENCE public.event_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.event_event_id_seq OWNED BY public.event.event_id;

CREATE TABLE public.event_problem (
    relation_id bigint NOT NULL,
    event_id bigint NOT NULL,
    problem character varying(200) NOT NULL,
    score integer DEFAULT 100,
    time_limit integer,
    memory_limit integer,
    display_id character varying(10),
    board_id bigint NOT NULL
);

CREATE SEQUENCE public.event_problem_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.event_problem_relation_id_seq OWNED BY public.event_problem.relation_id;

CREATE TABLE public.event_signup_notification (
    id bigint NOT NULL,
    email text NOT NULL,
    recipient_name text,
    event_name text NOT NULL,
    team_name text,
    signup_status text NOT NULL,
    reason text,
    attempts integer DEFAULT 0 NOT NULL,
    available_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    event_id bigint,
    signup_kind text,
    signup_id bigint
);

CREATE SEQUENCE public.event_signup_notification_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.event_signup_notification_id_seq OWNED BY public.event_signup_notification.id;

CREATE TABLE public.external_app (
    app_id bigint NOT NULL,
    app_name character varying(50) NOT NULL,
    app_key character varying(64) NOT NULL,
    status integer DEFAULT 1,
    external_login_enabled boolean DEFAULT false NOT NULL,
    external_board_enabled boolean DEFAULT false NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN public.external_app.external_login_enabled IS '是否允许调用外部登录及登录验证码接口';

COMMENT ON COLUMN public.external_app.external_board_enabled IS '是否允许向外榜上传题目及提交记录';

CREATE SEQUENCE public.external_app_app_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.external_app_app_id_seq OWNED BY public.external_app.app_id;

CREATE TABLE public.external_board (
    board_id bigint NOT NULL,
    event_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    is_external_open boolean DEFAULT false,
    is_unfrozen boolean DEFAULT false,
    actual_start_time timestamp without time zone,
    actual_end_time timestamp without time zone,
    freeze_time timestamp without time zone,
    medal_config text,
    CONSTRAINT external_board_name_check CHECK (((length(TRIM(BOTH FROM name)) >= 1) AND (length(TRIM(BOTH FROM name)) <= 200)))
);

CREATE SEQUENCE public.external_board_board_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.external_board_board_id_seq OWNED BY public.external_board.board_id;

CREATE TABLE public.file_relation (
    relation_id bigint NOT NULL,
    file_id bigint NOT NULL,
    biz_type character varying(50) NOT NULL,
    business_id bigint NOT NULL
);

CREATE SEQUENCE public.file_relation_relation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.file_relation_relation_id_seq OWNED BY public.file_relation.relation_id;

CREATE TABLE public.file_system (
    file_id bigint NOT NULL,
    url character varying(500) NOT NULL,
    type character varying(20) NOT NULL,
    name character varying(255) NOT NULL,
    uploader_id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    storage_type character varying(20),
    storage_key character varying(1000),
    access_purpose character varying(32) DEFAULT 'GENERAL'::character varying NOT NULL,
    CONSTRAINT ck_file_access_purpose CHECK (((access_purpose)::text = ANY ((ARRAY['GENERAL'::character varying, 'IDENTITY'::character varying, 'CERTIFICATE'::character varying, 'TEMPLATE'::character varying])::text[])))
);

CREATE SEQUENCE public.file_system_file_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.file_system_file_id_seq OWNED BY public.file_system.file_id;

CREATE TABLE public.major (
    major_id bigint NOT NULL,
    school_id bigint NOT NULL,
    major_name character varying(100) NOT NULL,
    schooling_years integer DEFAULT 4
);

CREATE SEQUENCE public.major_major_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.major_major_id_seq OWNED BY public.major.major_id;

CREATE TABLE public.notice (
    info_id bigint NOT NULL,
    type character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    publisher_name character varying(100) DEFAULT '协会'::character varying NOT NULL,
    publisher_id bigint,
    view_count integer DEFAULT 0,
    cover_file_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON COLUMN public.notice.publisher_name IS '发布人名称，发布通知/新闻/公示时手动输入，不关联用户表';

COMMENT ON COLUMN public.notice.publisher_id IS '历史发布人用户ID，已不作为业务关联使用';

COMMENT ON COLUMN public.notice.create_time IS '发布时间，可由后台手动设置';

CREATE SEQUENCE public.notice_info_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.notice_info_id_seq OWNED BY public.notice.info_id;

CREATE TABLE public.notification (
    msg_id bigint NOT NULL,
    receiver_id bigint NOT NULL,
    type smallint NOT NULL,
    title character varying(200) NOT NULL,
    content text NOT NULL,
    is_read smallint DEFAULT 0,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE SEQUENCE public.notification_msg_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.notification_msg_id_seq OWNED BY public.notification.msg_id;

CREATE TABLE public.oj_platform (
    oj_id bigint NOT NULL,
    oj_name character varying(50) NOT NULL,
    oj_url character varying(200) NOT NULL
);

CREATE SEQUENCE public.oj_platform_oj_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.oj_platform_oj_id_seq OWNED BY public.oj_platform.oj_id;

CREATE TABLE public.oj_practice_record (
    practice_problem_id bigint NOT NULL,
    user_id bigint NOT NULL,
    source_id bigint NOT NULL,
    problem_id character varying(100) NOT NULL,
    problem_name character varying(200) NOT NULL,
    problem_difficulty character varying(50) DEFAULT 'N/A'::character varying,
    problem_tag text,
    ac_time timestamp without time zone NOT NULL
);

CREATE SEQUENCE public.oj_practice_record_practice_problem_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.oj_practice_record_practice_problem_id_seq OWNED BY public.oj_practice_record.practice_problem_id;

CREATE TABLE public.oj_recent_contest (
    upcoming_contests_id bigint NOT NULL,
    source_id bigint NOT NULL,
    contests_id character varying(100) NOT NULL,
    contests_name character varying(200) NOT NULL,
    contests_starttime timestamp without time zone NOT NULL,
    url character varying(200) NOT NULL,
    contests_endtime timestamp without time zone,
    registration_start timestamp without time zone,
    registration_end timestamp without time zone
);

CREATE SEQUENCE public.oj_recent_contest_upcoming_contests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.oj_recent_contest_upcoming_contests_id_seq OWNED BY public.oj_recent_contest.upcoming_contests_id;

CREATE TABLE public.oj_user_account (
    user_id bigint NOT NULL,
    newcoder_id character varying(100),
    luogu_id character varying(100),
    luogu_client_id text,
    codeforces_id character varying(100),
    atcoder_id character varying(100),
    cwnuoj_id character varying(100),
    qoj_id character varying(100),
    ucup_id character varying(100),
    qoj_cookie text
);

CREATE TABLE public.qq_bot_config (
    id integer NOT NULL,
    payload text NOT NULL,
    operator_id bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT qq_bot_config_id_check CHECK ((id = 1))
);

CREATE TABLE public.qq_bot_delivery (
    delivery_key character varying(64) NOT NULL,
    group_id character varying(15) NOT NULL,
    task_id character varying(36),
    category character varying(20),
    payload text NOT NULL,
    status character varying(12) DEFAULT 'pending'::character varying NOT NULL,
    attempts integer DEFAULT 0 NOT NULL,
    available_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    lease_until timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    sent_at timestamp without time zone,
    last_error character varying(300)
);

CREATE TABLE public.qq_bot_task (
    id character varying(36) NOT NULL,
    payload text NOT NULL,
    due_at timestamp without time zone NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE public.rating_composition (
    user_id bigint NOT NULL,
    rating_contest integer DEFAULT 0,
    rating_certificate integer DEFAULT 0,
    rating_practice integer DEFAULT 0,
    rating_all integer DEFAULT 0
);

CREATE TABLE public.rating_log (
    log_id bigint NOT NULL,
    user_id bigint NOT NULL,
    change_value integer DEFAULT 0 NOT NULL,
    source_type integer,
    source_id bigint,
    description text,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);

CREATE SEQUENCE public.rating_log_log_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.rating_log_log_id_seq OWNED BY public.rating_log.log_id;

CREATE TABLE public.rating_month_log (
    monthlog_id bigint NOT NULL,
    month timestamp without time zone NOT NULL,
    user_id bigint NOT NULL,
    rating_contest integer DEFAULT 0 NOT NULL,
    rating_certificate integer DEFAULT 0 NOT NULL,
    rating_practice integer DEFAULT 0 NOT NULL,
    rating_all integer DEFAULT 0 NOT NULL
);

CREATE SEQUENCE public.rating_month_log_monthlog_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.rating_month_log_monthlog_id_seq OWNED BY public.rating_month_log.monthlog_id;

CREATE TABLE public.rating_term_record (
    rating_record_id bigint NOT NULL,
    term_id bigint NOT NULL,
    user_id bigint NOT NULL,
    rating_contest integer DEFAULT 0,
    rating_certificate integer DEFAULT 0,
    rating_practice integer DEFAULT 0,
    rating_all integer DEFAULT 0
);

CREATE SEQUENCE public.rating_term_record_rating_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.rating_term_record_rating_record_id_seq OWNED BY public.rating_term_record.rating_record_id;

CREATE TABLE public.school (
    school_id bigint NOT NULL,
    university_id bigint NOT NULL,
    school_name character varying(100) NOT NULL
);

CREATE TABLE public.school_admin_application (
    application_id bigint NOT NULL,
    user_id bigint NOT NULL,
    university_id bigint NOT NULL,
    school_id bigint NOT NULL,
    file_id bigint NOT NULL,
    status character varying(20) DEFAULT 'pending'::character varying NOT NULL,
    apply_source character varying(30) DEFAULT 'profile'::character varying NOT NULL,
    comments text,
    auditor_id bigint,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    audit_time timestamp without time zone
);

COMMENT ON TABLE public.school_admin_application IS '学校报名管理员申请表';

COMMENT ON COLUMN public.school_admin_application.status IS 'pending:待审核, approved:已通过, rejected:已驳回';

COMMENT ON COLUMN public.school_admin_application.apply_source IS 'register:注册时提交, profile:个人主页提交, admin_grant:管理员直接授权';

CREATE SEQUENCE public.school_admin_application_application_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.school_admin_application_application_id_seq OWNED BY public.school_admin_application.application_id;

CREATE SEQUENCE public.school_school_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.school_school_id_seq OWNED BY public.school.school_id;

CREATE TABLE public.security_audit_records (
    audit_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    target_user_id bigint NOT NULL,
    action character varying(100) NOT NULL,
    changed_fields character varying(500) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE SEQUENCE public.security_audit_records_audit_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.security_audit_records_audit_id_seq OWNED BY public.security_audit_records.audit_id;

CREATE TABLE public.signup_personal (
    signup_id bigint NOT NULL,
    event_id bigint NOT NULL,
    user_id bigint NOT NULL,
    status character varying(20) DEFAULT 'approved'::character varying,
    signup_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_ranking integer DEFAULT 1,
    advisor character varying(255),
    contest_account character varying(100),
    contest_password character varying(100),
    seat_number character varying(100),
    type character varying(50),
    created_by_user_id bigint,
    signup_source character varying(30) DEFAULT 'self'::character varying
);

COMMENT ON COLUMN public.signup_personal.is_ranking IS '是否参与排名 1:是 0:否';

COMMENT ON COLUMN public.signup_personal.advisor IS '指导老师姓名';

COMMENT ON COLUMN public.signup_personal.contest_account IS '比赛平台账号';

COMMENT ON COLUMN public.signup_personal.seat_number IS '线下赛座位号';

COMMENT ON COLUMN public.signup_personal.created_by_user_id IS '提交报名的操作者用户ID，个人自报可为空或等于user_id';

COMMENT ON COLUMN public.signup_personal.signup_source IS 'self:学生个人报名, school_admin:学校报名管理员代报名, admin_import:后台导入';

CREATE SEQUENCE public.signup_personal_signup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.signup_personal_signup_id_seq OWNED BY public.signup_personal.signup_id;

CREATE TABLE public.signup_team (
    signup_id bigint NOT NULL,
    event_id bigint NOT NULL,
    team_name character varying(100) NOT NULL,
    status character varying(20) DEFAULT 'approved'::character varying,
    signup_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    is_ranking integer DEFAULT 1,
    advisor character varying(255),
    type character varying(50) DEFAULT 'official'::character varying,
    contest_account character varying(100),
    contest_password character varying(100),
    seat_number character varying(100),
    created_by_user_id bigint,
    signup_source character varying(30) DEFAULT 'self'::character varying,
    source_team_id bigint
);

COMMENT ON COLUMN public.signup_team.type IS '队伍类型: official-正式队伍, star-打星队伍';

COMMENT ON COLUMN public.signup_team.contest_account IS '团队共用比赛账号(ICPC模式)';

COMMENT ON COLUMN public.signup_team.created_by_user_id IS '提交报名的操作者用户ID';

COMMENT ON COLUMN public.signup_team.signup_source IS 'self:队长报名, school_admin:学校报名管理员代报名, admin_import:后台导入';

COMMENT ON COLUMN public.signup_team.source_team_id IS 'Source team ID used only to identify the same team; historical signup snapshots survive source-team deletion';

CREATE TABLE public.signup_team_detail (
    detail_id bigint NOT NULL,
    signup_team_id bigint NOT NULL,
    user_id bigint NOT NULL,
    member_order integer DEFAULT 0,
    contest_account character varying(100),
    contest_password character varying(100),
    seat_number character varying(100)
);

COMMENT ON TABLE public.signup_team_detail IS '团队赛成员详情表（用于存储天梯赛等每人独立账号的情况）';

COMMENT ON COLUMN public.signup_team_detail.signup_team_id IS '关联的团队报名ID';

COMMENT ON COLUMN public.signup_team_detail.user_id IS '队员ID';

COMMENT ON COLUMN public.signup_team_detail.contest_account IS '队员独立的比赛账号';

CREATE SEQUENCE public.signup_team_detail_detail_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.signup_team_detail_detail_id_seq OWNED BY public.signup_team_detail.detail_id;

CREATE SEQUENCE public.signup_team_signup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.signup_team_signup_id_seq OWNED BY public.signup_team.signup_id;

CREATE TABLE public.submit_record (
    submit_record_id bigint NOT NULL,
    event_id bigint NOT NULL,
    user_id bigint,
    signup_team_id bigint,
    problem_id character varying(50) NOT NULL,
    problem_name character varying(200),
    language character varying(20) NOT NULL,
    code_content text NOT NULL,
    code_length integer,
    submit_time timestamp without time zone NOT NULL,
    result character varying(20) NOT NULL,
    score integer DEFAULT 0,
    time_usage integer,
    memory_usage integer,
    rid character varying(200),
    board_id bigint NOT NULL
);

CREATE SEQUENCE public.submit_record_submit_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.submit_record_submit_record_id_seq OWNED BY public.submit_record.submit_record_id;

CREATE TABLE public.sys_intro (
    intro_id bigint NOT NULL,
    category character varying(50) NOT NULL,
    content text NOT NULL,
    update_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    operator_id bigint
);

CREATE SEQUENCE public.sys_intro_intro_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.sys_intro_intro_id_seq OWNED BY public.sys_intro.intro_id;

CREATE TABLE public.team (
    team_id bigint NOT NULL,
    team_name character varying(100) NOT NULL,
    introduce text,
    team_image_id bigint,
    leader_id bigint NOT NULL
);

CREATE TABLE public.team_member (
    team_member_id bigint NOT NULL,
    team_id bigint NOT NULL,
    member_id bigint NOT NULL,
    status character varying(20) NOT NULL
);

CREATE SEQUENCE public.team_member_team_member_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.team_member_team_member_id_seq OWNED BY public.team_member.team_member_id;

CREATE SEQUENCE public.team_team_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.team_team_id_seq OWNED BY public.team.team_id;

CREATE TABLE public.term (
    term_id bigint NOT NULL,
    term_name character varying(50) NOT NULL,
    start_time date NOT NULL,
    end_time date
);

CREATE SEQUENCE public.term_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.term_term_id_seq OWNED BY public.term.term_id;

CREATE TABLE public.training (
    train_id bigint NOT NULL,
    name character varying(200) NOT NULL,
    term_id bigint,
    teacher_id bigint,
    description text NOT NULL,
    signup_start timestamp without time zone NOT NULL,
    signup_end timestamp without time zone NOT NULL,
    train_start timestamp without time zone NOT NULL,
    train_end timestamp without time zone NOT NULL,
    max_count integer NOT NULL,
    publisher_id bigint,
    publisher_name character varying(100) DEFAULT 'PAAWeb'::character varying NOT NULL
);

COMMENT ON COLUMN public.training.teacher_id IS '兼容旧数据的首位讲师ID，多讲师以training_teacher表为准';

COMMENT ON COLUMN public.training.publisher_id IS '实际操作用户ID，用于培训管理权限与历史追溯';

COMMENT ON COLUMN public.training.publisher_name IS '发布者显示名称，发布培训时手动输入';

CREATE TABLE public.training_signup (
    signup_id bigint NOT NULL,
    train_id bigint NOT NULL,
    user_id bigint NOT NULL,
    create_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(20) DEFAULT 'approved'::character varying
);

CREATE SEQUENCE public.training_signup_signup_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.training_signup_signup_id_seq OWNED BY public.training_signup.signup_id;

CREATE TABLE public.training_teacher (
    training_teacher_id bigint NOT NULL,
    train_id bigint NOT NULL,
    teacher_id bigint NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL
);

COMMENT ON TABLE public.training_teacher IS '培训与讲师的多对多关联表';

CREATE SEQUENCE public.training_teacher_training_teacher_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.training_teacher_training_teacher_id_seq OWNED BY public.training_teacher.training_teacher_id;

CREATE SEQUENCE public.training_train_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.training_train_id_seq OWNED BY public.training.train_id;

CREATE TABLE public.university (
    university_id bigint NOT NULL,
    university_name character varying(100) NOT NULL,
    logo_file_id bigint
);

CREATE SEQUENCE public.university_university_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.university_university_id_seq OWNED BY public.university.university_id;

CREATE TABLE public.user_account_legacy_alias (
    user_id bigint NOT NULL,
    legacy_user_account character varying(64) NOT NULL,
    migrated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);

COMMENT ON TABLE public.user_account_legacy_alias IS '历史非法账号迁移别名，仅用于兼容登录与追溯';

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    user_name character varying(50),
    password character varying(255) NOT NULL,
    email character varying(100),
    phone character varying(20),
    role character varying(20) DEFAULT 'student'::character varying,
    auth_version bigint DEFAULT 0 NOT NULL,
    account_status character varying(20) DEFAULT 'pending'::character varying,
    introduce text,
    real_name character varying(50) NOT NULL,
    number_id character varying(50) NOT NULL,
    gender smallint,
    edu_bg character varying(20) NOT NULL,
    major_id bigint NOT NULL,
    grade_year character varying(10) NOT NULL,
    class_name character varying(50),
    avatar_file_id bigint,
    id_photo_file_id bigint,
    id_photo_updated_at timestamp without time zone,
    register_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    student_card_file_id bigint,
    is_enrolled_stu smallint,
    user_account character varying(20) NOT NULL,
    background_file_id bigint,
    ban_until timestamp without time zone,
    ban_reason character varying(1000),
    pre_ban_status character varying(32),
    CONSTRAINT ck_users_enrolled CHECK ((is_enrolled_stu = ANY (ARRAY[0, 1]))),
    CONSTRAINT ck_users_gender CHECK ((gender = ANY (ARRAY[1, 2]))),
    CONSTRAINT ck_users_user_account_format CHECK ((((user_account)::text ~ '^[A-Za-z0-9]{3,20}$'::text) AND ((user_account)::text ~ '[A-Za-z]'::text)))
);

COMMENT ON COLUMN public.users.role IS 'student:学生, teacher:教师, department:部门, stu_admin:学生管理员, school_admin:学校报名管理员, supper_admin:超级管理员';

COMMENT ON COLUMN public.users.account_status IS 'pending:待审核, normal:正常, restricted:受限, banned:封禁, pending_activation:待激活';

COMMENT ON COLUMN public.users.id_photo_file_id IS '用户证件照文件ID，仅本人、必要报名确认流程及管理员可见';

COMMENT ON COLUMN public.users.id_photo_updated_at IS '用户证件照最近上传时间，用于审计与资料更新时间展示';

CREATE SEQUENCE public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;

ALTER SEQUENCE public.users_user_id_seq OWNED BY public.users.user_id;

ALTER TABLE ONLY public.admin_permissions ALTER COLUMN id SET DEFAULT nextval('public.admin_permissions_id_seq'::regclass);

ALTER TABLE ONLY public.association_member ALTER COLUMN member_id SET DEFAULT nextval('public.association_member_member_id_seq'::regclass);

ALTER TABLE ONLY public.audit_records ALTER COLUMN audit_id SET DEFAULT nextval('public.audit_records_audit_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_grant ALTER COLUMN certificate_download_id SET DEFAULT nextval('public.certificate_grant_certificate_download_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_issue ALTER COLUMN issue_id SET DEFAULT nextval('public.certificate_issue_issue_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_issue_member ALTER COLUMN issue_member_id SET DEFAULT nextval('public.certificate_issue_member_issue_member_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_level ALTER COLUMN level_id SET DEFAULT nextval('public.certificate_level_level_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_member ALTER COLUMN member_id SET DEFAULT nextval('public.certificate_member_member_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_type ALTER COLUMN certificate_type_id SET DEFAULT nextval('public.certificate_type_certificate_type_id_seq'::regclass);

ALTER TABLE ONLY public.certificate_verify ALTER COLUMN certificate_verify_id SET DEFAULT nextval('public.certificate_verify_certificate_verify_id_seq'::regclass);

ALTER TABLE ONLY public.contest_category ALTER COLUMN category_id SET DEFAULT nextval('public.contest_category_category_id_seq'::regclass);

ALTER TABLE ONLY public.event ALTER COLUMN event_id SET DEFAULT nextval('public.event_event_id_seq'::regclass);

ALTER TABLE ONLY public.event_problem ALTER COLUMN relation_id SET DEFAULT nextval('public.event_problem_relation_id_seq'::regclass);

ALTER TABLE ONLY public.event_signup_notification ALTER COLUMN id SET DEFAULT nextval('public.event_signup_notification_id_seq'::regclass);

ALTER TABLE ONLY public.external_app ALTER COLUMN app_id SET DEFAULT nextval('public.external_app_app_id_seq'::regclass);

ALTER TABLE ONLY public.external_board ALTER COLUMN board_id SET DEFAULT nextval('public.external_board_board_id_seq'::regclass);

ALTER TABLE ONLY public.file_relation ALTER COLUMN relation_id SET DEFAULT nextval('public.file_relation_relation_id_seq'::regclass);

ALTER TABLE ONLY public.file_system ALTER COLUMN file_id SET DEFAULT nextval('public.file_system_file_id_seq'::regclass);

ALTER TABLE ONLY public.major ALTER COLUMN major_id SET DEFAULT nextval('public.major_major_id_seq'::regclass);

ALTER TABLE ONLY public.notice ALTER COLUMN info_id SET DEFAULT nextval('public.notice_info_id_seq'::regclass);

ALTER TABLE ONLY public.notification ALTER COLUMN msg_id SET DEFAULT nextval('public.notification_msg_id_seq'::regclass);

ALTER TABLE ONLY public.oj_platform ALTER COLUMN oj_id SET DEFAULT nextval('public.oj_platform_oj_id_seq'::regclass);

ALTER TABLE ONLY public.oj_practice_record ALTER COLUMN practice_problem_id SET DEFAULT nextval('public.oj_practice_record_practice_problem_id_seq'::regclass);

ALTER TABLE ONLY public.oj_recent_contest ALTER COLUMN upcoming_contests_id SET DEFAULT nextval('public.oj_recent_contest_upcoming_contests_id_seq'::regclass);

ALTER TABLE ONLY public.rating_log ALTER COLUMN log_id SET DEFAULT nextval('public.rating_log_log_id_seq'::regclass);

ALTER TABLE ONLY public.rating_month_log ALTER COLUMN monthlog_id SET DEFAULT nextval('public.rating_month_log_monthlog_id_seq'::regclass);

ALTER TABLE ONLY public.rating_term_record ALTER COLUMN rating_record_id SET DEFAULT nextval('public.rating_term_record_rating_record_id_seq'::regclass);

ALTER TABLE ONLY public.school ALTER COLUMN school_id SET DEFAULT nextval('public.school_school_id_seq'::regclass);

ALTER TABLE ONLY public.school_admin_application ALTER COLUMN application_id SET DEFAULT nextval('public.school_admin_application_application_id_seq'::regclass);

ALTER TABLE ONLY public.security_audit_records ALTER COLUMN audit_id SET DEFAULT nextval('public.security_audit_records_audit_id_seq'::regclass);

ALTER TABLE ONLY public.signup_personal ALTER COLUMN signup_id SET DEFAULT nextval('public.signup_personal_signup_id_seq'::regclass);

ALTER TABLE ONLY public.signup_team ALTER COLUMN signup_id SET DEFAULT nextval('public.signup_team_signup_id_seq'::regclass);

ALTER TABLE ONLY public.signup_team_detail ALTER COLUMN detail_id SET DEFAULT nextval('public.signup_team_detail_detail_id_seq'::regclass);

ALTER TABLE ONLY public.submit_record ALTER COLUMN submit_record_id SET DEFAULT nextval('public.submit_record_submit_record_id_seq'::regclass);

ALTER TABLE ONLY public.sys_intro ALTER COLUMN intro_id SET DEFAULT nextval('public.sys_intro_intro_id_seq'::regclass);

ALTER TABLE ONLY public.team ALTER COLUMN team_id SET DEFAULT nextval('public.team_team_id_seq'::regclass);

ALTER TABLE ONLY public.team_member ALTER COLUMN team_member_id SET DEFAULT nextval('public.team_member_team_member_id_seq'::regclass);

ALTER TABLE ONLY public.term ALTER COLUMN term_id SET DEFAULT nextval('public.term_term_id_seq'::regclass);

ALTER TABLE ONLY public.training ALTER COLUMN train_id SET DEFAULT nextval('public.training_train_id_seq'::regclass);

ALTER TABLE ONLY public.training_signup ALTER COLUMN signup_id SET DEFAULT nextval('public.training_signup_signup_id_seq'::regclass);

ALTER TABLE ONLY public.training_teacher ALTER COLUMN training_teacher_id SET DEFAULT nextval('public.training_teacher_training_teacher_id_seq'::regclass);

ALTER TABLE ONLY public.university ALTER COLUMN university_id SET DEFAULT nextval('public.university_university_id_seq'::regclass);

ALTER TABLE ONLY public.users ALTER COLUMN user_id SET DEFAULT nextval('public.users_user_id_seq'::regclass);

ALTER TABLE ONLY public.admin_permissions
    ADD CONSTRAINT admin_permissions_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.audit_records
    ADD CONSTRAINT audit_records_pkey PRIMARY KEY (audit_id);

ALTER TABLE ONLY public.certificate_issue
    ADD CONSTRAINT certificate_issue_certificate_no_key UNIQUE (certificate_no);

ALTER TABLE ONLY public.certificate_issue_member
    ADD CONSTRAINT certificate_issue_member_pkey PRIMARY KEY (issue_member_id);

ALTER TABLE ONLY public.certificate_issue
    ADD CONSTRAINT certificate_issue_pkey PRIMARY KEY (issue_id);

ALTER TABLE ONLY public.certificate_member
    ADD CONSTRAINT certificate_member_pkey PRIMARY KEY (member_id);

ALTER TABLE ONLY public.event_signup_notification
    ADD CONSTRAINT event_signup_notification_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.external_app
    ADD CONSTRAINT external_app_app_key_key UNIQUE (app_key);

ALTER TABLE ONLY public.external_app
    ADD CONSTRAINT external_app_pkey PRIMARY KEY (app_id);

ALTER TABLE ONLY public.external_board
    ADD CONSTRAINT external_board_pkey PRIMARY KEY (board_id);

ALTER TABLE ONLY public.association_member
    ADD CONSTRAINT pk_association_member PRIMARY KEY (member_id);

ALTER TABLE ONLY public.certificate_grant
    ADD CONSTRAINT pk_certificate_grant PRIMARY KEY (certificate_download_id);

ALTER TABLE ONLY public.certificate_level
    ADD CONSTRAINT pk_certificate_level PRIMARY KEY (level_id);

ALTER TABLE ONLY public.certificate_type
    ADD CONSTRAINT pk_certificate_type PRIMARY KEY (certificate_type_id);

ALTER TABLE ONLY public.certificate_verify
    ADD CONSTRAINT pk_certificate_verify PRIMARY KEY (certificate_verify_id);

ALTER TABLE ONLY public.contest_category
    ADD CONSTRAINT pk_contest_category PRIMARY KEY (category_id);

ALTER TABLE ONLY public.event
    ADD CONSTRAINT pk_event PRIMARY KEY (event_id);

ALTER TABLE ONLY public.event_problem
    ADD CONSTRAINT pk_event_problem PRIMARY KEY (relation_id);

ALTER TABLE ONLY public.file_relation
    ADD CONSTRAINT pk_file_relation PRIMARY KEY (relation_id);

ALTER TABLE ONLY public.file_system
    ADD CONSTRAINT pk_file_system PRIMARY KEY (file_id);

ALTER TABLE ONLY public.major
    ADD CONSTRAINT pk_major PRIMARY KEY (major_id);

ALTER TABLE ONLY public.notice
    ADD CONSTRAINT pk_notice PRIMARY KEY (info_id);

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT pk_notification PRIMARY KEY (msg_id);

ALTER TABLE ONLY public.oj_platform
    ADD CONSTRAINT pk_oj_platform PRIMARY KEY (oj_id);

ALTER TABLE ONLY public.oj_practice_record
    ADD CONSTRAINT pk_oj_practice_record PRIMARY KEY (practice_problem_id);

ALTER TABLE ONLY public.oj_recent_contest
    ADD CONSTRAINT pk_oj_recent_contest PRIMARY KEY (upcoming_contests_id);

ALTER TABLE ONLY public.oj_user_account
    ADD CONSTRAINT pk_oj_user_account PRIMARY KEY (user_id);

ALTER TABLE ONLY public.rating_composition
    ADD CONSTRAINT pk_rating_composition PRIMARY KEY (user_id);

ALTER TABLE ONLY public.rating_term_record
    ADD CONSTRAINT pk_rating_term_record PRIMARY KEY (rating_record_id);

ALTER TABLE ONLY public.school
    ADD CONSTRAINT pk_school PRIMARY KEY (school_id);

ALTER TABLE ONLY public.signup_personal
    ADD CONSTRAINT pk_signup_personal PRIMARY KEY (signup_id);

ALTER TABLE ONLY public.signup_team
    ADD CONSTRAINT pk_signup_team PRIMARY KEY (signup_id);

ALTER TABLE ONLY public.submit_record
    ADD CONSTRAINT pk_submit_record PRIMARY KEY (submit_record_id);

ALTER TABLE ONLY public.team
    ADD CONSTRAINT pk_team PRIMARY KEY (team_id);

ALTER TABLE ONLY public.team_member
    ADD CONSTRAINT pk_team_member PRIMARY KEY (team_member_id);

ALTER TABLE ONLY public.term
    ADD CONSTRAINT pk_term PRIMARY KEY (term_id);

ALTER TABLE ONLY public.training
    ADD CONSTRAINT pk_training PRIMARY KEY (train_id);

ALTER TABLE ONLY public.training_signup
    ADD CONSTRAINT pk_training_signup PRIMARY KEY (signup_id);

ALTER TABLE ONLY public.training_teacher
    ADD CONSTRAINT pk_training_teacher PRIMARY KEY (training_teacher_id);

ALTER TABLE ONLY public.university
    ADD CONSTRAINT pk_university PRIMARY KEY (university_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT pk_users PRIMARY KEY (user_id);

ALTER TABLE ONLY public.qq_bot_config
    ADD CONSTRAINT qq_bot_config_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.qq_bot_delivery
    ADD CONSTRAINT qq_bot_delivery_pkey PRIMARY KEY (delivery_key);

ALTER TABLE ONLY public.qq_bot_task
    ADD CONSTRAINT qq_bot_task_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.rating_log
    ADD CONSTRAINT rating_log_pkey PRIMARY KEY (log_id);

ALTER TABLE ONLY public.rating_month_log
    ADD CONSTRAINT rating_month_log_pkey PRIMARY KEY (monthlog_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT school_admin_application_pkey PRIMARY KEY (application_id);

ALTER TABLE ONLY public.security_audit_records
    ADD CONSTRAINT security_audit_records_pkey PRIMARY KEY (audit_id);

ALTER TABLE ONLY public.signup_team_detail
    ADD CONSTRAINT signup_team_detail_pkey PRIMARY KEY (detail_id);

ALTER TABLE ONLY public.sys_intro
    ADD CONSTRAINT sys_intro_pkey PRIMARY KEY (intro_id);

ALTER TABLE ONLY public.certificate_level
    ADD CONSTRAINT uk_cert_level_name UNIQUE (name);

ALTER TABLE ONLY public.certificate_issue_member
    ADD CONSTRAINT uk_certificate_issue_member_student UNIQUE (issue_id, school_name, student_id);

ALTER TABLE ONLY public.external_board
    ADD CONSTRAINT uk_external_board_event UNIQUE (board_id, event_id);

ALTER TABLE ONLY public.file_system
    ADD CONSTRAINT uk_file_url UNIQUE (url);

ALTER TABLE ONLY public.major
    ADD CONSTRAINT uk_major_school_name UNIQUE (school_id, major_name);

ALTER TABLE ONLY public.oj_practice_record
    ADD CONSTRAINT uk_opr_unique UNIQUE (user_id, source_id, problem_id);

ALTER TABLE ONLY public.rating_month_log
    ADD CONSTRAINT uk_rating_month_user UNIQUE (month, user_id);

ALTER TABLE ONLY public.school
    ADD CONSTRAINT uk_school_name_uni UNIQUE (university_id, school_name);

ALTER TABLE ONLY public.signup_personal
    ADD CONSTRAINT uk_sp_event_user UNIQUE (event_id, user_id);

ALTER TABLE ONLY public.signup_team
    ADD CONSTRAINT uk_st_event_team_name UNIQUE (event_id, team_name);

ALTER TABLE ONLY public.term
    ADD CONSTRAINT uk_term_name UNIQUE (term_name);

ALTER TABLE ONLY public.training_teacher
    ADD CONSTRAINT uk_training_teacher UNIQUE (train_id, teacher_id);

ALTER TABLE ONLY public.training_signup
    ADD CONSTRAINT uk_ts_train_user UNIQUE (train_id, user_id);

ALTER TABLE ONLY public.university
    ADD CONSTRAINT uk_university_name UNIQUE (university_name);

ALTER TABLE ONLY public.admin_permissions
    ADD CONSTRAINT uk_user_id UNIQUE (user_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT uk_users_useraccount UNIQUE (user_account);

ALTER TABLE ONLY public.user_account_legacy_alias
    ADD CONSTRAINT user_account_legacy_alias_legacy_user_account_key UNIQUE (legacy_user_account);

ALTER TABLE ONLY public.user_account_legacy_alias
    ADD CONSTRAINT user_account_legacy_alias_pkey PRIMARY KEY (user_id);

CREATE INDEX idx_association_member_school_number ON public.association_member USING btree (school_name, number_id);

CREATE INDEX idx_audit_applicant ON public.audit_records USING btree (applicant_id);

CREATE INDEX idx_audit_file ON public.audit_records USING btree (file_id);

CREATE INDEX idx_audit_user ON public.audit_records USING btree (user_id);

CREATE INDEX idx_cert_verify_fileid ON public.certificate_verify USING btree (file_id);

CREATE INDEX idx_cert_verify_user ON public.certificate_verify USING btree (user_id);

CREATE INDEX idx_certificate_grant_issue ON public.certificate_grant USING btree (issue_id);

CREATE INDEX idx_certificate_issue_member_issue ON public.certificate_issue_member USING btree (issue_id);

CREATE INDEX idx_certificate_issue_member_student ON public.certificate_issue_member USING btree (student_id);

CREATE INDEX idx_certificate_issue_member_user ON public.certificate_issue_member USING btree (user_id);

CREATE INDEX idx_certificate_issue_status ON public.certificate_issue USING btree (status);

CREATE INDEX idx_certificate_issue_type ON public.certificate_issue USING btree (certificate_type_id);

CREATE INDEX idx_cg_account ON public.certificate_grant USING btree (user_account);

CREATE INDEX idx_cg_group ON public.certificate_grant USING btree (group_id);

CREATE INDEX idx_cg_school_account ON public.certificate_grant USING btree (school_name, user_account);

CREATE INDEX idx_cim_school_student ON public.certificate_issue_member USING btree (school_name, student_id);

CREATE INDEX idx_cm_student_id ON public.certificate_member USING btree (student_id);

CREATE INDEX idx_cm_user ON public.certificate_member USING btree (user_id);

CREATE INDEX idx_cm_verify ON public.certificate_member USING btree (certificate_verify_id);

CREATE INDEX idx_ep_event ON public.event_problem USING btree (event_id);

CREATE INDEX idx_event_cat ON public.event USING btree (category_id);

CREATE INDEX idx_event_problem_board_display ON public.event_problem USING btree (board_id, display_id);

CREATE INDEX idx_event_signup_notification_due ON public.event_signup_notification USING btree (available_at, id);

CREATE INDEX idx_event_signup_notification_recipient ON public.event_signup_notification USING btree (email, id);

CREATE INDEX idx_event_signup_notification_signup ON public.event_signup_notification USING btree (signup_kind, signup_id);

CREATE INDEX idx_event_term ON public.event USING btree (term_id);

CREATE INDEX idx_external_board_event ON public.external_board USING btree (event_id);

CREATE INDEX idx_file_system_storage_type ON public.file_system USING btree (storage_type);

CREATE INDEX idx_file_uploader ON public.file_system USING btree (uploader_id);

CREATE INDEX idx_fr_biz ON public.file_relation USING btree (biz_type, business_id);

CREATE INDEX idx_fr_file ON public.file_relation USING btree (file_id);

CREATE INDEX idx_intro_oper ON public.sys_intro USING btree (operator_id);

CREATE INDEX idx_major_school ON public.major USING btree (school_id);

CREATE INDEX idx_notify_receiver ON public.notification USING btree (receiver_id);

CREATE INDEX idx_prac_user_source ON public.oj_practice_record USING btree (user_id, source_id);

CREATE INDEX idx_qq_bot_delivery_due ON public.qq_bot_delivery USING btree (status, available_at);

CREATE INDEX idx_qq_bot_task_due ON public.qq_bot_task USING btree (enabled, due_at);

CREATE INDEX idx_rating_log_user_time ON public.rating_log USING btree (user_id, create_time DESC);

CREATE INDEX idx_saa_create_time ON public.school_admin_application USING btree (create_time DESC);

CREATE INDEX idx_saa_school_status ON public.school_admin_application USING btree (school_id, status);

CREATE INDEX idx_saa_user_status ON public.school_admin_application USING btree (user_id, status);

CREATE INDEX idx_school_uni ON public.school USING btree (university_id);

CREATE INDEX idx_security_audit_target_time ON public.security_audit_records USING btree (target_user_id, created_at);

CREATE INDEX idx_signup_personal_event_status ON public.signup_personal USING btree (event_id, status);

CREATE INDEX idx_signup_team_event_status ON public.signup_team USING btree (event_id, status);

CREATE INDEX idx_sp_created_by ON public.signup_personal USING btree (created_by_user_id);

CREATE INDEX idx_st_created_by ON public.signup_team USING btree (created_by_user_id);

CREATE INDEX idx_std_signup_team ON public.signup_team_detail USING btree (signup_team_id);

CREATE INDEX idx_std_user ON public.signup_team_detail USING btree (user_id);

CREATE INDEX idx_sub_event_user ON public.submit_record USING btree (event_id, user_id);

CREATE INDEX idx_sub_prob ON public.submit_record USING btree (problem_id);

CREATE INDEX idx_sub_signup_team ON public.submit_record USING btree (event_id, signup_team_id);

CREATE UNIQUE INDEX idx_submit_record_board_rid_unique ON public.submit_record USING btree (board_id, rid) WHERE ((rid IS NOT NULL) AND ((rid)::text <> ''::text));

CREATE INDEX idx_submit_record_board_time ON public.submit_record USING btree (board_id, submit_time);

CREATE INDEX idx_team_leader ON public.team USING btree (leader_id);

CREATE INDEX idx_team_member_team_status ON public.team_member USING btree (team_id, status);

CREATE INDEX idx_tm_member ON public.team_member USING btree (member_id);

CREATE INDEX idx_tm_team ON public.team_member USING btree (team_id);

CREATE INDEX idx_training_publisher ON public.training USING btree (publisher_id);

CREATE INDEX idx_training_teacher_train ON public.training_teacher USING btree (train_id);

CREATE INDEX idx_training_teacher_user ON public.training_teacher USING btree (teacher_id);

CREATE INDEX idx_university_logo_file_id ON public.university USING btree (logo_file_id);

CREATE INDEX idx_users_ban_until ON public.users USING btree (ban_until) WHERE ((account_status)::text = 'banned'::text);

CREATE INDEX idx_users_major ON public.users USING btree (major_id);

CREATE INDEX idx_users_number ON public.users USING btree (number_id);

CREATE UNIQUE INDEX uk_saa_one_pending_per_user ON public.school_admin_application USING btree (user_id) WHERE ((status)::text = 'pending'::text);

CREATE UNIQUE INDEX uk_signup_team_detail_signup_user ON public.signup_team_detail USING btree (signup_team_id, user_id);

CREATE UNIQUE INDEX uk_signup_team_event_source_team ON public.signup_team USING btree (event_id, source_team_id) WHERE (source_team_id IS NOT NULL);

CREATE UNIQUE INDEX uk_team_member_team_user ON public.team_member USING btree (team_id, member_id);

CREATE UNIQUE INDEX uk_users_email_lower ON public.users USING btree (lower(btrim((email)::text))) WHERE ((email IS NOT NULL) AND (btrim((email)::text) <> ''::text));

CREATE UNIQUE INDEX uk_users_useraccount_lower ON public.users USING btree (lower((user_account)::text));

ALTER TABLE ONLY public.audit_records
    ADD CONSTRAINT audit_records_file_id_fkey FOREIGN KEY (file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.audit_records
    ADD CONSTRAINT audit_records_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.certificate_issue
    ADD CONSTRAINT certificate_issue_certificate_type_id_fkey FOREIGN KEY (certificate_type_id) REFERENCES public.certificate_type(certificate_type_id);

ALTER TABLE ONLY public.certificate_issue_member
    ADD CONSTRAINT certificate_issue_member_issue_id_fkey FOREIGN KEY (issue_id) REFERENCES public.certificate_issue(issue_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.certificate_issue
    ADD CONSTRAINT certificate_issue_template_file_id_fkey FOREIGN KEY (template_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.external_board
    ADD CONSTRAINT external_board_event_id_fkey FOREIGN KEY (event_id) REFERENCES public.event(event_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.association_member
    ADD CONSTRAINT fk_association_member_avatar FOREIGN KEY (avatar_file_id) REFERENCES public.file_system(file_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.association_member
    ADD CONSTRAINT fk_association_member_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.audit_records
    ADD CONSTRAINT fk_audit_records_applicant_id FOREIGN KEY (applicant_id) REFERENCES public.users(user_id) ON DELETE RESTRICT;

ALTER TABLE ONLY public.contest_category
    ADD CONSTRAINT fk_category_logo FOREIGN KEY (logo_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.certificate_grant
    ADD CONSTRAINT fk_certificate_grant_issue FOREIGN KEY (issue_id) REFERENCES public.certificate_issue(issue_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.certificate_issue
    ADD CONSTRAINT fk_certificate_issue_template_file FOREIGN KEY (template_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.certificate_grant
    ADD CONSTRAINT fk_cg_type FOREIGN KEY (certificate_type_id) REFERENCES public.certificate_type(certificate_type_id);

ALTER TABLE ONLY public.certificate_grant
    ADD CONSTRAINT fk_cg_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.certificate_member
    ADD CONSTRAINT fk_cm_verify FOREIGN KEY (certificate_verify_id) REFERENCES public.certificate_verify(certificate_verify_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.certificate_type
    ADD CONSTRAINT fk_ct_levl FOREIGN KEY (type_id) REFERENCES public.certificate_level(level_id);

ALTER TABLE ONLY public.certificate_type
    ADD CONSTRAINT fk_ct_term FOREIGN KEY (term) REFERENCES public.term(term_id);

ALTER TABLE ONLY public.certificate_type
    ADD CONSTRAINT fk_ct_upuser FOREIGN KEY (update_user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.certificate_type
    ADD CONSTRAINT fk_ct_url FOREIGN KEY (url_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.certificate_verify
    ADD CONSTRAINT fk_cv_category FOREIGN KEY (category_id) REFERENCES public.contest_category(category_id);

ALTER TABLE ONLY public.certificate_verify
    ADD CONSTRAINT fk_cv_file_id FOREIGN KEY (file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.certificate_verify
    ADD CONSTRAINT fk_cv_level FOREIGN KEY (level_id) REFERENCES public.certificate_level(level_id);

ALTER TABLE ONLY public.certificate_verify
    ADD CONSTRAINT fk_cv_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.event_problem
    ADD CONSTRAINT fk_ep_event FOREIGN KEY (event_id) REFERENCES public.event(event_id);

ALTER TABLE ONLY public.event
    ADD CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES public.contest_category(category_id);

ALTER TABLE ONLY public.event_problem
    ADD CONSTRAINT fk_event_problem_board FOREIGN KEY (board_id, event_id) REFERENCES public.external_board(board_id, event_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.event
    ADD CONSTRAINT fk_event_term FOREIGN KEY (term_id) REFERENCES public.term(term_id);

ALTER TABLE ONLY public.file_system
    ADD CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.file_relation
    ADD CONSTRAINT fk_fr_file FOREIGN KEY (file_id) REFERENCES public.file_system(file_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.sys_intro
    ADD CONSTRAINT fk_intro_users FOREIGN KEY (operator_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.major
    ADD CONSTRAINT fk_major_school FOREIGN KEY (school_id) REFERENCES public.school(school_id);

ALTER TABLE ONLY public.notice
    ADD CONSTRAINT fk_notice_cover FOREIGN KEY (cover_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.notification
    ADD CONSTRAINT fk_notif_receiver FOREIGN KEY (receiver_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.oj_practice_record
    ADD CONSTRAINT fk_opr_source FOREIGN KEY (source_id) REFERENCES public.oj_platform(oj_id);

ALTER TABLE ONLY public.oj_practice_record
    ADD CONSTRAINT fk_opr_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.oj_recent_contest
    ADD CONSTRAINT fk_orc_source FOREIGN KEY (source_id) REFERENCES public.oj_platform(oj_id);

ALTER TABLE ONLY public.oj_user_account
    ADD CONSTRAINT fk_oua_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.rating_composition
    ADD CONSTRAINT fk_rc_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.rating_term_record
    ADD CONSTRAINT fk_rtr_term FOREIGN KEY (term_id) REFERENCES public.term(term_id);

ALTER TABLE ONLY public.rating_term_record
    ADD CONSTRAINT fk_rtr_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT fk_saa_auditor FOREIGN KEY (auditor_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT fk_saa_file FOREIGN KEY (file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT fk_saa_school FOREIGN KEY (school_id) REFERENCES public.school(school_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT fk_saa_university FOREIGN KEY (university_id) REFERENCES public.university(university_id);

ALTER TABLE ONLY public.school_admin_application
    ADD CONSTRAINT fk_saa_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.school
    ADD CONSTRAINT fk_school_university FOREIGN KEY (university_id) REFERENCES public.university(university_id);

ALTER TABLE ONLY public.signup_personal
    ADD CONSTRAINT fk_sp_created_by FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.signup_personal
    ADD CONSTRAINT fk_sp_event FOREIGN KEY (event_id) REFERENCES public.event(event_id);

ALTER TABLE ONLY public.signup_personal
    ADD CONSTRAINT fk_sp_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.signup_team
    ADD CONSTRAINT fk_st_created_by FOREIGN KEY (created_by_user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.signup_team
    ADD CONSTRAINT fk_st_event FOREIGN KEY (event_id) REFERENCES public.event(event_id);

ALTER TABLE ONLY public.signup_team_detail
    ADD CONSTRAINT fk_std_signup_team FOREIGN KEY (signup_team_id) REFERENCES public.signup_team(signup_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.signup_team_detail
    ADD CONSTRAINT fk_std_user FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.submit_record
    ADD CONSTRAINT fk_sub_event FOREIGN KEY (event_id) REFERENCES public.event(event_id);

ALTER TABLE ONLY public.submit_record
    ADD CONSTRAINT fk_sub_signup_team FOREIGN KEY (signup_team_id) REFERENCES public.signup_team(signup_id);

ALTER TABLE ONLY public.submit_record
    ADD CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.submit_record
    ADD CONSTRAINT fk_submit_record_board FOREIGN KEY (board_id, event_id) REFERENCES public.external_board(board_id, event_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.team
    ADD CONSTRAINT fk_team_image FOREIGN KEY (team_image_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.team
    ADD CONSTRAINT fk_team_leader FOREIGN KEY (leader_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.team_member
    ADD CONSTRAINT fk_tm_member FOREIGN KEY (member_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.team_member
    ADD CONSTRAINT fk_tm_team FOREIGN KEY (team_id) REFERENCES public.team(team_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.training
    ADD CONSTRAINT fk_training_publisher FOREIGN KEY (publisher_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.training
    ADD CONSTRAINT fk_training_teacher FOREIGN KEY (teacher_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.training_teacher
    ADD CONSTRAINT fk_training_teacher_train FOREIGN KEY (train_id) REFERENCES public.training(train_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.training_teacher
    ADD CONSTRAINT fk_training_teacher_user FOREIGN KEY (teacher_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.training
    ADD CONSTRAINT fk_training_term FOREIGN KEY (term_id) REFERENCES public.term(term_id);

ALTER TABLE ONLY public.training_signup
    ADD CONSTRAINT fk_ts_train FOREIGN KEY (train_id) REFERENCES public.training(train_id);

ALTER TABLE ONLY public.training_signup
    ADD CONSTRAINT fk_ts_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);

ALTER TABLE ONLY public.university
    ADD CONSTRAINT fk_university_logo FOREIGN KEY (logo_file_id) REFERENCES public.file_system(file_id) ON DELETE SET NULL;

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_avatar FOREIGN KEY (avatar_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_background FOREIGN KEY (background_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_id_photo FOREIGN KEY (id_photo_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_major FOREIGN KEY (major_id) REFERENCES public.major(major_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT fk_users_student_card FOREIGN KEY (student_card_file_id) REFERENCES public.file_system(file_id);

ALTER TABLE ONLY public.rating_log
    ADD CONSTRAINT rating_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.rating_month_log
    ADD CONSTRAINT rating_month_log_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.user_account_legacy_alias
    ADD CONSTRAINT user_account_legacy_alias_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(user_id) ON DELETE CASCADE;

-- External board resolver, photo and organization fields.

-- migration_external_board_resolver.sql
-- Run before deploying the backend. Existing boards remain disabled.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_password_hash TEXT;

-- migration_external_board_photos.sql
BEGIN;
CREATE TABLE IF NOT EXISTS public.external_board_photo (
    board_id BIGINT NOT NULL REFERENCES public.external_board(board_id) ON DELETE CASCADE,
    participant_id BIGINT NOT NULL CHECK (participant_id >= 0),
    photo_key VARCHAR(36) NOT NULL UNIQUE,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (board_id, participant_id)
);
COMMIT;

-- migration_organization_logos.sql
-- Organization logos and per-board display mode. Run before deploying the backend.
ALTER TABLE public.school ADD COLUMN IF NOT EXISTS logo_file_id BIGINT REFERENCES public.file_system(file_id) ON DELETE SET NULL;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS organization_display VARCHAR(16) NOT NULL DEFAULT 'school';

-- migration_resolver_playback.sql
-- Playback preferences are per external board; existing boards default to single-step.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_single_step BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_speed DOUBLE PRECISION NOT NULL DEFAULT 1.0 CHECK (resolver_speed >= 0.25 AND resolver_speed <= 4.0);

-- migration_resolver_settings.sql
-- Awards and display preferences for the official resolver, isolated per external board.
ALTER TABLE public.external_board ADD COLUMN IF NOT EXISTS resolver_settings TEXT;

CREATE INDEX IF NOT EXISTS idx_team_member_accepted ON team_member(member_id, team_id) WHERE status='1';

-- Only direct, accepted teammates share records. This is a live view, so leaving
-- a team cannot leave copied awards/records behind or spread records transitively.
CREATE OR REPLACE VIEW oj_tracker_peers AS
SELECT user_id AS user_id, user_id AS owner_id FROM users
UNION
SELECT mine.member_id, peer.member_id
FROM team_member mine
JOIN team t ON t.team_id=mine.team_id
JOIN team_member peer ON peer.team_id=mine.team_id
JOIN users viewer ON viewer.user_id=mine.member_id AND viewer.account_status='normal'
JOIN users contributor ON contributor.user_id=peer.member_id AND contributor.account_status='normal'
WHERE mine.status='1' AND peer.status='1';

CREATE OR REPLACE VIEW oj_effective_practice_record AS
SELECT practice_problem_id, user_id, source_id, problem_id, problem_name,
       problem_difficulty, problem_tag, ac_time
FROM (
    SELECT r.practice_problem_id, p.user_id,
           CASE WHEN r.source_id=7 THEN 6 ELSE r.source_id END AS source_id,
           r.problem_id, r.problem_name, r.problem_difficulty, r.problem_tag, r.ac_time,
           ROW_NUMBER() OVER (
               PARTITION BY p.user_id, CASE WHEN r.source_id=7 THEN 6 ELSE r.source_id END, r.problem_id
               ORDER BY r.ac_time, r.practice_problem_id
           ) AS occurrence
    FROM oj_tracker_peers p JOIN oj_practice_record r ON r.user_id=p.owner_id
) merged WHERE occurrence=1;
