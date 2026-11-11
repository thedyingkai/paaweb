package com.cwnupaa.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.cwnupaa.backend.dto.SmtpConfigDTO;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class EmailServiceTests {

    @Test
    void signupDeliveryPropagatesSmtpFailureToTheRetryWorker() {
        EmailService service = new EmailService();
        JavaMailSender sender = mock(JavaMailSender.class);
        SystemConfigService configs = mock(SystemConfigService.class);
        NotificationService notifications = mock(NotificationService.class);
        ReflectionTestUtils.setField(service, "defaultMailSender", sender);
        ReflectionTestUtils.setField(service, "systemConfigService", configs);
        ReflectionTestUtils.setField(service, "notificationService", notifications);
        ReflectionTestUtils.setField(service, "defaultFromEmail", "sender@example.invalid");
        when(configs.getSmtpConfig()).thenReturn(new SmtpConfigDTO());
        when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("simulated outage")).when(sender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> service.sendEventSignupStatusToRecipient("user@example.invalid", "张三",
                "比赛", null, "pending", null)).isInstanceOf(MailSendException.class);
        verifyNoInteractions(notifications);
    }

    @Test
    void eventSignupEmailUsesFormalGreetingAndIndentedBody() {
        EmailService service = new EmailService();

        String html = ReflectionTestUtils.invokeMethod(
                service,
                "buildEventSignupHtml",
                "张三",
                "您的队伍“示例队伍”",
                "程序设计竞赛",
                "报名成功",
                "#52c41a",
                ""
        );

        assertThat(html)
                .contains("https://example.com/brand/besti-acm-logo-v1.png")
                .contains("此邮件由BESTI ACM系统自动发送")
                .doesNotContain("PAAWeb", "alt='PAA'")
                .contains("你好，<strong>张三</strong>：")
                .contains("text-indent: 2em")
                .contains("队伍")
                .doesNotContain("您好")
                .doesNotContain("尊敬的")
                .doesNotContain("战队")
                .doesNotContain("小队");
        assertThat(html.indexOf("你好，")).isLessThan(html.indexOf("报名状态已更新"));
    }

    @Test
    void teamInviteEmailUsesRecipientGreetingAndTeamWording() {
        EmailService service = new EmailService();

        String html = ReflectionTestUtils.invokeMethod(
                service,
                "buildInviteHtmlEmail",
                "李四",
                "王五",
                "算法一队"
        );

        assertThat(html)
                .contains("你好，<strong>李四</strong>：")
                .contains("新的队伍邀请")
                .contains("我的队伍")
                .doesNotContain("战队")
                .doesNotContain("小队");
    }
}
