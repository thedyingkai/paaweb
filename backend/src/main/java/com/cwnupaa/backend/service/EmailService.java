package com.cwnupaa.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.cwnupaa.backend.entity.Users;
import com.cwnupaa.backend.dto.SmtpConfigDTO;
import com.cwnupaa.backend.mapper.UserMapper;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

@Service
public class EmailService {
    private static final String LOGO_URL = com.cwnupaa.backend.utils.DeploymentUrls.site() + "/brand/besti-acm-logo-v1.png";

    @Autowired
    private JavaMailSender defaultMailSender;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserMapper userMapper;

    @Value("${spring.mail.username:}")
    private String defaultFromEmail;

    private JavaMailSender activeMailSender() {
        SmtpConfigDTO config = systemConfigService.getSmtpConfig();
        return config.isEnabled() ? systemConfigService.buildMailSender(config) : defaultMailSender;
    }

    private String activeFromEmail() {
        SmtpConfigDTO config = systemConfigService.getSmtpConfig();
        return config.isEnabled() && StringUtils.hasText(config.getUsername()) ? config.getUsername() : defaultFromEmail;
    }

    private String activeFromName() {
        SmtpConfigDTO config = systemConfigService.getSmtpConfig();
        return config.isEnabled() && StringUtils.hasText(config.getFromName()) ? config.getFromName() : "BESTI ACM";
    }

    public void sendAccountBanEmail(String email, String name, java.time.LocalDateTime until, String reason) throws Exception {
        MimeMessage message = activeMailSender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(email);
        helper.setSubject("【BESTI ACM】账号封禁通知");
        helper.setText((StringUtils.hasText(name) ? name : "用户") + "，您好：\n\n您的账号已被封禁。\n封禁原因：" + reason
                + "\n解封时间（北京时间）：" + until.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                + "\n到期后系统将自动解除本次封禁。如有疑问，请联系管理员。", false);
        activeMailSender().send(message);
    }

    public void sendAccountSecurityChangedEmail(String email, java.util.Collection<String> fields) throws Exception {
        MimeMessage message = activeMailSender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(email);
        helper.setSubject("【BESTI ACM】账号安全信息变更通知");
        helper.setText("您的账号安全信息已发生变更，其他登录会话已失效。如非本人操作，请立即联系管理员。通知不会包含新邮箱、密码或验证码。", false);
        activeMailSender().send(message);
    }

    public void sendCodeEmail(String toEmail, String code, String type) throws Exception {
        MimeMessage message = activeMailSender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(toEmail);

        String subjectType = type;
        if ("login".equals(type)) subjectType = "账号登录";
        else if ("register".equals(type)) subjectType = "账号注册";
        else if ("update".equals(type)) subjectType = "登录账号修改";
        else if ("binding".equals(type)) subjectType = "第三方账号绑定";
        else if ("change-password".equals(type)) subjectType = "密码修改";
        else if ("change-email-current".equals(type)) subjectType = "邮箱变更原邮箱确认";
        else if ("change-email-new".equals(type)) subjectType = "邮箱变更新邮箱确认";

        helper.setSubject("【BESTI ACM】" + subjectType + "安全验证");
        setHtmlText(helper, buildHtmlEmail(resolveRecipientName(toEmail, null), code, subjectType));

        activeMailSender().send(message);
    }

    public void sendCodeRiskEmail(String toEmail) throws Exception {
        MimeMessage message = activeMailSender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(toEmail);

        String title = "【BESTI ACM】账号安全风险提醒";
        helper.setSubject(title);
        setHtmlText(helper, buildCodeRiskHtmlEmail(resolveRecipientName(toEmail, null)));

        activeMailSender().send(message);
        notificationService.sendByEmail(toEmail, 2, title, "您的账号邮箱验证码连续多次输入错误，存在被盗风险。若非本人操作，请尽快修改密码并检查账号安全。");
    }

    public void sendTeamInviteEmail(String toEmail, String inviterName, String teamName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】队伍邀请通知";
            helper.setSubject(title);
            setHtmlText(helper, buildInviteHtmlEmail(resolveRecipientName(toEmail, null), inviterName, teamName));
            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 1, title, inviterName + " 邀请您加入队伍 [" + teamName + "]，请前往个人中心处理。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendInviteResponseEmail(String toEmail, String inviteeName, String teamName, boolean accepted) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);

            String action = accepted ? "接受" : "拒绝";
            String title = "【BESTI ACM】队伍邀请" + action + "通知";
            helper.setSubject(title);

            setHtmlText(helper, buildInviteResponseHtmlEmail(resolveRecipientName(toEmail, null), inviteeName, teamName, accepted));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 1, title, "用户 " + inviteeName + " 已" + action + "加入队伍 [" + teamName + "] 的邀请。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendTeamRemovalEmail(String toEmail, String memberName, String teamName, String leaderName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】队伍变动通知";
            helper.setSubject(title);

            setHtmlText(helper, buildRemovalHtmlEmail(memberName, teamName, leaderName));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 1, title, "您已被队长 " + leaderName + " 移出队伍 [" + teamName + "]。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendTeamDisbandEmail(String toEmail, String memberName, String teamName, String leaderName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】队伍解散通知";
            helper.setSubject(title);

            setHtmlText(helper, buildDisbandHtmlEmail(memberName, teamName, leaderName));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 1, title, "队长 " + leaderName + " 已解散队伍 [" + teamName + "]。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendMemberLeaveEmail(String toEmail, String memberName, String teamName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】队员退队通知";
            helper.setSubject(title);

            setHtmlText(helper, buildMemberLeaveHtmlEmail(resolveRecipientName(toEmail, null), memberName, teamName));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 1, title, "成员 " + memberName + " 已退出队伍 [" + teamName + "]。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendTrainingTeacherNotification(String toEmail, String teacherName, String trainingName, String timeRange, String type) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);

            String subject = "【BESTI ACM】" + ("update".equals(type) ? "培训安排变更通知" : "新培训讲师任命通知");
            if ("remove".equals(type)) subject = "【BESTI ACM】培训讲师解任通知";

            helper.setSubject(subject);

            setHtmlText(helper, buildTrainingHtmlEmail(teacherName, trainingName, timeRange, type));
            activeMailSender().send(message);

            String content = "remove".equals(type) ? "您已被取消课程 [" + trainingName + "] 的讲师资格。" : "您被任命为课程 [" + trainingName + "] 的讲师，时间：" + timeRange;
            if ("update".equals(type)) content = "课程 [" + trainingName + "] 的安排已变更，请查看详情。";
            notificationService.sendByEmail(toEmail, 2, subject, content);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendTrainingSignupStatusEmail(String toEmail, String userName, String trainingName, String status, String reason) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);

            String subject = "【BESTI ACM】培训报名状态通知";
            String statusText = "";
            String color = "#1890ff";
            String reasonText = "";

            if ("approved".equals(status)) {
                statusText = "报名成功";
                color = "#52c41a";
            } else if ("pending".equals(status)) {    
                statusText = "待审核";
                color = "#1890ff";               
            } else if ("cancelled".equals(status)) {
                statusText = "报名已取消";
                color = "#8c8c8c";
            } else if ("rejected".equals(status)) {
                statusText = "报名未通过";
                color = "#ff4d4f";
                if (reason != null && !reason.isEmpty()) {
                    reasonText = reason;
                }
            }

            helper.setSubject(subject);
            setHtmlText(helper, buildSignupHtml(userName, trainingName, statusText, color, reasonText));
            activeMailSender().send(message);
            
            String content = "您关于课程 [" + trainingName + "] 的报名状态更新为：" + statusText;
            if (!reasonText.isEmpty()) {
                content += "。原因：" + reasonText;
            }
            notificationService.sendByEmail(toEmail, 2, subject, content);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendAuthAuditResultEmail(String toEmail, String userName, boolean isPassed, String reason) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);

            String resultText = isPassed ? "认证通过" : "认证未通过";
            String title = "【BESTI ACM】身份认证审核结果通知 - " + resultText;
            helper.setSubject(title);

            setHtmlText(helper, buildAuthAuditHtml(userName, isPassed, reason));
            activeMailSender().send(message);
            String content = isPassed ? "您的身份认证已通过审核。" : "您的身份认证未通过，原因：" + reason;
            notificationService.sendByEmail(toEmail, 0, title, content);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendEventSignupStatusEmail(Map<String, String> targets, String eventName, String teamName, String status, String reason) {
        targets.forEach((address, name) -> {
            try {
                sendEventSignupStatusToRecipient(address, name, eventName, teamName, status, reason);
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
            }
        });
    }

    // The durable notification worker needs failures to propagate so it can retry.
    public void sendEventSignupStatusToRecipient(String toEmail, String userName, String eventName,
                                                  String teamName, String status, String reason) throws Exception {
        String subject = "【BESTI ACM】赛事报名状态通知";
        String statusText = switch (status) {
            case "approved" -> "报名成功";
            case "pending" -> "待审核";
            case "cancelled" -> "报名已取消";
            case "rejected" -> "报名未通过";
            default -> "";
        };
        String color = switch (status) {
            case "approved" -> "#52c41a";
            case "cancelled" -> "#8c8c8c";
            case "rejected" -> "#ff4d4f";
            default -> "#1890ff";
        };
        String reasonText = "rejected".equals(status) && reason != null ? reason : "";
        JavaMailSender sender = activeMailSender();
        MimeMessage message = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(toEmail);
        helper.setSubject(subject);
        String contentName = teamName != null ? "您的队伍「" + teamName + "」" : "您";
        setHtmlText(helper, buildEventSignupHtml(userName, contentName, eventName, statusText, color, reasonText));
        sender.send(message);
        String content = contentName + " 关于赛事 [" + eventName + "] 的报名状态更新为：" + statusText;
        if (!reasonText.isEmpty()) content += "。原因：" + reasonText;
        notificationService.sendByEmail(toEmail, 3, subject, content);
    }

    public void sendCertificateGrantEmail(String toEmail, String userName, String certificateName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】新证书颁发通知";
            helper.setSubject(title);
            setHtmlText(helper, buildCertificateGrantHtml(userName, certificateName));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 4, title, "恭喜！您获得了一张新证书：[" + certificateName + "]，请前往证书中心查看。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
        }
    }

    public void sendCertificateAuditResultEmail(String toEmail, String userName, String eventName, boolean isPassed, String reason) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);

            String resultText = isPassed ? "认证通过" : "认证未通过";
            String title = "【BESTI ACM】证书认证审核结果通知 - " + resultText;
            helper.setSubject(title);

            setHtmlText(helper, buildCertificateAuditHtml(userName, eventName, isPassed, reason));
            activeMailSender().send(message);
            String content = isPassed ? "您提交的关于 [" + eventName + "] 的证书认证已通过。" : "您提交的关于 [" + eventName + "] 的证书认证未通过，原因：" + reason;
            notificationService.sendByEmail(toEmail, 4, title, content);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
        }
    }

    public void sendCertificateRevokeEmail(String toEmail, String userName, String certificateName) {
        try {
            MimeMessage message = activeMailSender().createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(activeFromEmail(), activeFromName());
            helper.setTo(toEmail);
            String title = "【BESTI ACM】证书撤回通知";
            helper.setSubject(title);
            setHtmlText(helper, buildCertificateRevokeHtml(userName, certificateName));

            activeMailSender().send(message);
            notificationService.sendByEmail(toEmail, 4, title, "您获得的证书 [" + certificateName + "] 已被管理员撤回，如有疑问请联系管理员。");
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("操作失败，请检查请求参数和服务配置");
            org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
        }
    }

    public void sendEventTimeChangeEmail(Map<String, String> targets, String eventName, String changeDetail) {
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            try {
                String toEmail = entry.getKey();
                String userName = entry.getValue();

                MimeMessage message = activeMailSender().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(activeFromEmail(), activeFromName());
                helper.setTo(toEmail);
                String title = "【BESTI ACM】赛事安排变更通知 - " + eventName;
                helper.setSubject(title);

                setHtmlText(helper, buildEventTimeChangeHtml(userName, eventName, changeDetail));
                activeMailSender().send(message);
                notificationService.sendByEmail(toEmail, 3, title, "赛事 [" + eventName + "] 安排变更：" + changeDetail);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
            }
        }
    }

    public void sendTrainingTimeChangeEmail(Map<String, String> targets, String trainingName, String changeDetail) {
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            try {
                String toEmail = entry.getKey();
                String userName = entry.getValue();

                MimeMessage message = activeMailSender().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(activeFromEmail(), activeFromName());
                helper.setTo(toEmail);
                String title = "【BESTI ACM】培训安排变更通知 - " + trainingName;
                helper.setSubject(title);

                setHtmlText(helper, buildTrainingTimeChangeHtml(userName, trainingName, changeDetail));
                activeMailSender().send(message);
                notificationService.sendByEmail(toEmail, 3, title, "培训 [" + trainingName + "] 安排变更：" + changeDetail);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
            }
        }
    }

    public void sendEventReminderEmail(Map<String, String> targets, String eventName, String eventTime, String description) {
        for (Map.Entry<String, String> entry : targets.entrySet()) {
            try {
                String toEmail = entry.getKey();
                String userName = entry.getValue();

                MimeMessage message = activeMailSender().createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setFrom(activeFromEmail(), activeFromName());
                helper.setTo(toEmail);
                String title = "【BESTI ACM】赛事提醒 - " + eventName;
                helper.setSubject(title);

                setHtmlText(helper, buildEventReminderHtml(userName, eventName, eventTime, description));
                activeMailSender().send(message);
                notificationService.sendByEmail(toEmail, 3, title, "赛事 [" + eventName + "] 即将开始。\n时间：" + eventTime + "\n备注：" + description);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(EmailService.class).warn("邮件通知处理失败，请检查 SMTP 配置");
            }
        }
    }

    public void sendBulkHtmlEmail(String toEmail, String subject, String htmlContent) throws Exception {
        MimeMessage message = activeMailSender().createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(activeFromEmail(), activeFromName());
        helper.setTo(toEmail);
        String finalSubject = subject.startsWith("【BESTI ACM】") ? subject : "【BESTI ACM】" + subject;
        helper.setSubject(finalSubject);
        setHtmlText(helper, buildBulkHtmlEmail(htmlContent));
        activeMailSender().send(message);
    }

    private void setHtmlText(MimeMessageHelper helper, String html) throws Exception {
        helper.setText(html, true);
    }

    private String resolveRecipientName(String email, String preferredName) {
        if (StringUtils.hasText(preferredName)) {
            return preferredName.trim();
        }
        if (!StringUtils.hasText(email) || userMapper == null) {
            return "用户";
        }
        try {
            Users user = userMapper.selectOne(new QueryWrapper<Users>()
                    .eq("email", email.trim())
                    .last("LIMIT 1"));
            if (user != null) {
                if (StringUtils.hasText(user.getRealName())) {
                    return user.getRealName().trim();
                }
                if (StringUtils.hasText(user.getUserName())) {
                    return user.getUserName().trim();
                }
            }
        } catch (Exception ignored) {
            // Name lookup must not prevent delivery of security or notification emails.
        }
        return "用户";
    }

    private String formalGreeting(String recipientName) {
        String displayName = StringUtils.hasText(recipientName) ? recipientName.trim() : "用户";
        return "<p style='font-size: 16px; color: #333333; margin: 0 0 18px; text-align: left; text-indent: 0;'>你好，<strong>"
                + escapeHtml(displayName) + "</strong>：</p>";
    }

    private String formalParagraph(String content) {
        return "<p style='font-size: 15px; color: #555555; line-height: 1.9; margin: 0 0 18px; text-align: justify; text-indent: 2em;'>"
                + content + "</p>";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String buildLogoHeaderHtml() {
        return "<div style='text-align: center; padding: 4px 0 28px 0; margin-bottom: 28px; border-bottom: 1px solid #d9e2ec;'>" +
                "<img src='" + LOGO_URL + "' alt='BESTI ACM' width='104' height='104' style='display: inline-block; width: 104px; height: 104px; object-fit: contain; border: 0; border-radius: 50%;'>" +
                "</div>";
    }

    private String buildEventSignupHtml(String userName, String subjectName, String eventName, String statusText, String color, String reason) {
        String reasonHtml = "";
        if (reason != null && !reason.isEmpty()) {
            reasonHtml = "<div style='margin-top: 15px; padding-top: 15px; border-top: 1px dashed #eee;'>" +
                    "<p style='color: #666; font-size: 14px; margin: 0;'>原因：</p>" +
                    "<p style='color: #333; font-size: 15px; margin: 5px 0;'>" + reason + "</p></div>";
        }

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph(escapeHtml(subjectName) + "关于赛事 <strong style='color: #333;'>" + escapeHtml(eventName) + "</strong> 的报名状态已更新，具体结果如下。") +
                "<div style='margin: 30px 0; padding: 15px; background-color: #f9f9f9; border-radius: 6px;'>" +
                "<h3 style='color: " + color + "; margin: 0; font-size: 22px;'>" + statusText + "</h3>" + reasonHtml + "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送，请勿直接回复。</p>" +
                "</div></div></body></html>";
    }

    private String buildHtmlEmail(String recipientName, String code, String typeTitle) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(recipientName) +
                formalParagraph("您正在进行 <strong>" + escapeHtml(typeTitle) + "</strong> 操作。为确认本次操作由您本人发起，请使用以下验证码完成安全验证。") +
                "<p style='font-size: 14px; color: #666666; margin-bottom: 10px;'>您的验证码为：</p>" +
                "<div style='margin: 25px 0;'><span style='display: inline-block; padding: 16px 32px; background-color: #f0f7ff; color: #1890ff; font-size: 32px; font-weight: 700; border-radius: 6px; letter-spacing: 4px;'>" + code + "</span></div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>验证码有效期为5分钟。如非本人操作，请忽略此邮件。</p>" +
                "</div></div></body></html>";
    }

    private String buildCodeRiskHtmlEmail(String recipientName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(recipientName) +
                formalParagraph("系统检测到您的账号邮箱验证码已连续多次输入错误，现向您发送安全风险提醒。") +
                "<div style='background-color: #fff1f0; border: 1px solid #ffa39e; border-radius: 6px; padding: 18px; margin: 24px 0; text-align: left;'>" +
                "<p style='font-size: 14px; color: #555555; line-height: 1.8; margin: 0;'>为保护账号安全，本次验证码已自动失效。若非本人操作，说明您的账号可能存在被盗风险，请尽快修改密码，并检查邮箱、手机号等账号安全信息。</p>" +
                "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送，请勿直接回复。</p>" +
                "</div></div></body></html>";
    }

    private String buildInviteHtmlEmail(String recipientName, String inviterName, String teamName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(recipientName) +
                formalParagraph("您收到一条新的队伍邀请，具体信息如下。") +
                "<div style='background-color: #f8f9fa; padding: 25px; border-radius: 6px; margin: 25px 0; border: 1px solid #eeeeee;'>" +
                "<p style='margin: 8px 0; font-size: 15px; color: #555;'>邀请人：<strong style='color: #333; font-size: 16px;'>" + inviterName + "</strong></p>" +
                "<div style='height: 1px; background: #eee; margin: 15px 0;'></div>" +
                "<p style='margin: 8px 0; font-size: 15px; color: #555;'>邀请您加入队伍：<strong style='color: #1890ff; font-size: 18px;'>" + teamName + "</strong></p></div>" +
                formalParagraph("请登录系统，进入“个人主页 - 我的队伍”处理该邀请。") +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildInviteResponseHtmlEmail(String recipientName, String inviteeName, String teamName, boolean accepted) {
        String actionText = accepted ? "接受" : "拒绝";
        String color = accepted ? "#52c41a" : "#ff4d4f";
        String bgColor = accepted ? "#f6ffed" : "#fff1f0";
        String borderColor = accepted ? "#b7eb8f" : "#ffa39e";

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(recipientName) +
                formalParagraph("您发出的队伍邀请已有新的处理结果，具体信息如下。") +
                "<div style='background-color: " + bgColor + "; padding: 25px; border-radius: 6px; margin: 25px 0; border: 1px solid " + borderColor + ";'>" +
                "<p style='margin: 5px 0; font-size: 15px;'>用户 <strong>" + inviteeName + "</strong></p>" +
                "<p style='margin: 5px 0; font-size: 15px;'>针对队伍 <strong>" + teamName + "</strong></p>" +
                "<p style='font-size: 20px; font-weight: bold; color: " + color + "; margin-top: 15px;'>已" + actionText + "邀请</p></div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildRemovalHtmlEmail(String memberName, String teamName, String leaderName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(memberName) +
                formalParagraph("现将您的队伍成员资格变动情况通知如下。") +
                "<div style='background-color: #fff1f0; border: 1px solid #ffa39e; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='font-size: 16px; color: #333; margin: 0;'>您已被队长 <strong>" + leaderName + "</strong> 移出</p>" +
                "<p style='font-size: 20px; font-weight: bold; color: #cf1322; margin: 12px 0;'>「 " + teamName + " 」</p><p style='margin:0; color: #666;'>队伍</p></div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildDisbandHtmlEmail(String memberName, String teamName, String leaderName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(memberName) +
                formalParagraph("现将您所在队伍的解散情况通知如下。") +
                "<div style='background-color: #fffbe6; border: 1px solid #ffe58f; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='font-size: 16px; color: #333; margin: 0;'>队长 <strong>" + leaderName + "</strong> 已解散</p>" +
                "<p style='font-size: 20px; font-weight: bold; color: #d48806; margin: 12px 0;'>「 " + teamName + " 」</p><p style='margin:0; color: #666;'>队伍</p></div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildMemberLeaveHtmlEmail(String recipientName, String memberName, String teamName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(recipientName) +
                formalParagraph("您的队伍成员发生变动，具体信息如下。") +
                "<div style='background-color: #fff1f0; border: 1px solid #ffa39e; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='font-size: 16px; color: #333; margin: 0;'>用户 <strong>" + memberName + "</strong></p>" +
                "<p style='font-size: 16px; color: #666; margin: 10px 0;'>已退出</p>" +
                "<p style='font-size: 20px; font-weight: bold; color: #cf1322; margin: 0;'>「 " + teamName + " 」</p></div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }


    private String buildTrainingHtmlEmail(String name, String trainingName, String timeRange, String type) {
        String title;
        String color = "#722ed1";
        String bgColor = "#f9f0ff";
        String borderColor = "#d3adf7";

        if ("update".equals(type)) title = "课程安排已变更";
        else if ("remove".equals(type)) {
            title = "您已被取消该课程讲师资格";
            color = "#ff4d4f";
            bgColor = "#fff1f0";
            borderColor = "#ffa39e";
        } else title = "您被指定为讲师";

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(name) +
                formalParagraph("现将您的课程讲师安排通知如下。") +
                "<div style='background-color: " + bgColor + "; border: 1px solid " + borderColor + "; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='font-size: 18px; font-weight: bold; color: " + color + "; margin: 0 0 15px 0;'>" + title + "</p>" +
                "<div style='height: 1px; background: rgba(0,0,0,0.05); margin: 15px 0;'></div>" +
                "<p style='margin: 8px 0; color: #555;'>课程名称：<strong style='color: #333;'>" + trainingName + "</strong></p>" +
                ("remove".equals(type) ? "" : "<p style='margin: 8px 0; color: #555;'>授课时间：<strong style='color: #333;'>" + timeRange + "</strong></p>") +
                "</div>" +
                formalParagraph("请登录系统查看详细安排，并按要求做好相关准备。") +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildSignupHtml(String name, String trainingName, String statusText, String color, String reason) {
        String reasonHtml = "";
        if (reason != null && !reason.isEmpty()) {
            reasonHtml = "<div style='margin-top: 15px; padding-top: 15px; border-top: 1px dashed #eee;'>" +
                    "<p style='color: #666; font-size: 14px; margin: 0;'>原因：</p>" +
                    "<p style='color: #333; font-size: 15px; margin: 5px 0;'>" + reason + "</p></div>";
        }

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(name) +
                formalParagraph("您关于课程 <strong>" + escapeHtml(trainingName) + "</strong> 的报名状态已更新，具体结果如下。") +
                "<div style='margin: 30px 0; padding: 15px; background-color: #f9f9f9; border-radius: 6px;'>" +
                "<h3 style='color: " + color + "; margin: 0; font-size: 22px;'>" + statusText + "</h3>" + reasonHtml + "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildAuthAuditHtml(String userName, boolean isPassed, String reason) {
        String color = isPassed ? "#52c41a" : "#ff4d4f";
        String title = isPassed ? "认证审核通过" : "认证审核未通过";
        String content = isPassed ? "<p style='color: #555;'>恭喜您！您的身份认证已通过审核。</p><p style='color: #555;'>您现在可以正常使用报名、组队等功能。</p>" : "<p style='color: #555;'>很遗憾，您的身份认证未通过审核。</p><div style='background: #fff1f0; padding: 10px; border-radius: 4px; margin: 15px 0;'><p style='margin:0; color: #cf1322;'>原因：<strong>" + (reason == null ? "信息不符" : reason) + "</strong></p></div><p style='color: #666; font-size: 14px;'>请修改资料或重新上传证件后再次提交。</p>";

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("您提交的身份认证申请已完成审核，具体结果如下。") +
                "<div style='background-color: #f8f9fa; padding: 25px; border-radius: 6px; margin: 25px 0; border: 1px solid #eee;'>" + content + "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildCertificateAuditHtml(String userName, String eventName, boolean isPassed, String reason) {
        String color = isPassed ? "#52c41a" : "#ff4d4f";
        String title = isPassed ? "证书认证审核通过" : "证书认证审核未通过";
        String content = isPassed ? "<p style='color: #555;'>恭喜您！您提交的关于 <strong>" + eventName + "</strong> 的证书认证已通过审核。</p><p style='color: #555;'>相关荣誉已记录在案。</p>" : "<p style='color: #555;'>很遗憾，您提交的关于 <strong>" + eventName + "</strong> 的证书认证未通过审核。</p><div style='background: #fff1f0; padding: 10px; border-radius: 4px; margin: 15px 0;'><p style='margin:0; color: #cf1322;'>原因：<strong>" + (reason == null ? "信息不符" : reason) + "</strong></p></div><p style='color: #666; font-size: 14px;'>请检查信息后重新提交。</p>";

        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("您提交的证书认证申请已完成审核，具体结果如下。") +
                "<div style='background-color: #f8f9fa; padding: 25px; border-radius: 6px; margin: 25px 0; border: 1px solid #eee;'>" + content + "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildCertificateGrantHtml(String userName, String certificateName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("现将您的证书发放信息通知如下。") +
                "<div style='background-color: #fffbe6; border: 1px solid #ffe58f; padding: 30px; border-radius: 6px; margin: 25px 0; box-shadow: 0 2px 8px rgba(250, 173, 20, 0.1);'>" +
                "<p style='font-size: 16px; color: #555; margin: 0;'>恭喜您获得荣誉证书：</p>" +
                "<div style='margin-top: 15px; padding-top: 15px; border-top: 1px dashed #d48806;'>" +
                "<p style='font-size: 22px; font-weight: bold; color: #d48806; margin: 10px 0; letter-spacing: 1px;'>" + certificateName + "</p></div>" +
                "</div>" +
                "<p style='font-size: 14px; color: #666666;'>请登录协会系统，进入【个人中心 - 证书中心】查看并下载电子证书。</p>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildCertificateRevokeHtml(String userName, String certificateName) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("现将您的证书撤回信息通知如下。") +
                "<div style='background-color: #fff1f0; border: 1px solid #ffa39e; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='font-size: 16px; color: #333; margin: 0;'>您获得的以下证书已被管理员撤回：</p>" +
                "<p style='font-size: 20px; font-weight: bold; color: #cf1322; margin: 12px 0; text-decoration: line-through;'>" + certificateName + "</p>" +
                "<p style='font-size: 14px; color: #666;'>如有疑问，请联系协会管理员。</p>" +
                "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildEventTimeChangeHtml(String userName, String eventName, String detail) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("您已报名的赛事 <strong>" + escapeHtml(eventName) + "</strong> 安排发生变动，具体信息如下。") +
                "<div style='background-color: #f9f0ff; border: 1px solid #d3adf7; padding: 25px; border-radius: 6px; margin: 25px 0; text-align: left;'>" +
                "<p style='font-size: 15px; color: #333; margin: 0; line-height: 1.8; white-space: pre-wrap;'>" + detail + "</p>" +
                "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildTrainingTimeChangeHtml(String userName, String trainingName, String detail) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("您已报名的培训 <strong>" + escapeHtml(trainingName) + "</strong> 安排发生变动，具体信息如下。") +
                "<div style='background-color: #e6fffb; border: 1px solid #87e8de; padding: 25px; border-radius: 6px; margin: 25px 0; text-align: left;'>" +
                "<p style='font-size: 15px; color: #333; margin: 0; line-height: 1.8; white-space: pre-wrap;'>" + detail + "</p>" +
                "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildEventReminderHtml(String userName, String eventName, String eventTime, String description) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: center;'>" + buildLogoHeaderHtml() + formalGreeting(userName) +
                formalParagraph("您报名的赛事 <strong>" + escapeHtml(eventName) + "</strong> 将于明日开始，现将有关事项提醒如下。") +
                "<div style='background-color: #fffbe6; border: 1px solid #ffe58f; padding: 25px; border-radius: 6px; margin: 25px 0;'>" +
                "<p style='margin: 8px 0; font-size: 15px;'>比赛时间：<strong style='color: #333;'>" + eventTime + "</strong></p>" +
                "<p style='margin: 8px 0; color: #d48806; font-size: 14px;'>" + description + "</p>" +
                "</div>" +
                formalParagraph("请提前做好参赛准备，预祝您取得优异成绩。") +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }

    private String buildBulkHtmlEmail(String htmlContent) {
        return "<html><head></head><body style='background-color: #f0f2f5; margin: 0; padding: 20px; font-family: \"Helvetica Neue\", Helvetica, Arial, sans-serif;'>" +
                "<div style='max-width: 600px; margin: 0 auto; background-color: #ffffff; border-radius: 8px; overflow: hidden; box-shadow: 0 4px 12px rgba(0,0,0,0.05);'>" +
                "<div style='padding: 40px 30px; text-align: left;'>" + buildLogoHeaderHtml() +
                "<div style='font-size: 15px; color: #333333; line-height: 1.9; text-align: justify; text-indent: 2em;'>" + htmlContent + "</div>" +
                "<p style='font-size: 13px; color: #999999; margin-top: 40px; border-top: 1px solid #eeeeee; padding-top: 20px; text-align: center;'>此邮件由BESTI ACM系统自动发送。</p>" +
                "</div></div></body></html>";
    }
}
