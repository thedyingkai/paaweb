package com.cwnupaa.backend.controller;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.cwnupaa.backend.common.GlobalExceptionHandler;
import com.cwnupaa.backend.mapper.FileSystemMapper;
import com.cwnupaa.backend.service.*;
import com.cwnupaa.backend.vo.UserProfileVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real template writes and PDF validation, with optional public-content tables absent or containing NULL projections. */
@SpringJUnitConfig(CertificateTemplatePersistenceTests.Config.class)
@MockBean({CertificateExcelImportService.class, CertificateAuthenticityService.class,
        CertificateAccessTokenService.class, CertificateIdentityService.class,
        SecurityAuditService.class, EmailService.class, com.cwnupaa.backend.service.HistoricalAwardService.class})
class CertificateTemplatePersistenceTests {
    @Autowired private CertificateGrantController controller;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @MockBean private FileStorageService storage;
    @TempDir Path uploads;
    private MockMvc mvc;
    private MockHttpSession session;

    @BeforeEach
    void setUp() throws Exception {
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE file_system (
                    file_id BIGINT PRIMARY KEY, url VARCHAR(255), type VARCHAR(20), name VARCHAR(255),
                    uploader_id BIGINT, create_time TIMESTAMP, storage_type VARCHAR(20),
                    storage_key VARCHAR(255), access_purpose VARCHAR(32))
                """);
        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY, user_account VARCHAR(20), user_name VARCHAR(50),
                    password VARCHAR(100), auth_version BIGINT DEFAULT 0,
                    email VARCHAR(100), phone VARCHAR(30), role VARCHAR(20),
                    account_status VARCHAR(20), ban_until TIMESTAMP, ban_reason VARCHAR(255),
                    pre_ban_status VARCHAR(20), introduce TEXT, real_name VARCHAR(50), number_id VARCHAR(50),
                    gender INTEGER, edu_bg VARCHAR(20), major_id BIGINT, grade_year VARCHAR(20),
                    class_name VARCHAR(50), avatar_file_id BIGINT, background_file_id BIGINT,
                    id_photo_file_id BIGINT, id_photo_updated_at TIMESTAMP, register_time TIMESTAMP,
                    student_card_file_id BIGINT, is_enrolled_stu INTEGER)
                """);
        jdbc.execute("CREATE TABLE audit_records (file_id BIGINT, biz_type VARCHAR(50))");
        jdbc.execute("CREATE TABLE school_admin_application (file_id BIGINT)");
        jdbc.execute("CREATE TABLE certificate_verify (file_id BIGINT)");
        jdbc.execute("CREATE TABLE certificate_issue (template_file_id BIGINT)");
        jdbc.execute("""
                CREATE TABLE certificate_level (
                    level_id BIGINT PRIMARY KEY, name VARCHAR(50), level_type VARCHAR(20),
                    rank_type VARCHAR(20), base_rating INTEGER, sort_weight INTEGER)
                """);
        jdbc.execute("CREATE TABLE term (term_id BIGINT PRIMARY KEY)");
        jdbc.execute("""
                CREATE TABLE certificate_type (
                    certificate_type_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
                    type_name VARCHAR(50) NOT NULL, type_id BIGINT NOT NULL REFERENCES certificate_level(level_id),
                    url_id BIGINT NOT NULL REFERENCES file_system(file_id), time TIMESTAMP NOT NULL,
                    term BIGINT NOT NULL REFERENCES term(term_id), update_user_id BIGINT NOT NULL REFERENCES users(user_id),
                    font_size INTEGER, is_team INTEGER, level_scope VARCHAR(20), template_schema TEXT,
                    rating_enabled BOOLEAN DEFAULT TRUE NOT NULL)
                """);
        jdbc.update("INSERT INTO users (user_id, role, account_status) VALUES (8, 'supper_admin', 'normal')");
        jdbc.update("INSERT INTO term VALUES (1)");
        jdbc.update("INSERT INTO certificate_level VALUES (1, '院级参与奖', '院级', '参与奖', 10, 1)");
        for (long id : new long[]{90, 91}) {
            jdbc.update("INSERT INTO file_system (file_id, name, type, uploader_id, storage_type, storage_key, access_purpose) "
                    + "VALUES (?, 'template.pdf', 'pdf', 8, 'LOCAL', ?, 'TEMPLATE')", id, "sensitive/certificate_template/" + id + ".pdf");
        }
        Path pdf = uploads.resolve("template.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        when(storage.materialize(any())).thenReturn(pdf.toFile());
        var actor = new UserProfileVO();
        actor.setUserId(8L);
        session = new MockHttpSession();
        session.setAttribute("user", actor);
        mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update-current", "update-new"})
    void templateSaveCommitsWithoutQueryingPublicContentTables(String operation) throws Exception {
        Map<String, Object> payload = payload();
        boolean editing = !"create".equals(operation);
        if (editing) {
            existingTemplate();
            payload.put("certificateTypeId", 1);
            if ("update-new".equals(operation)) payload.put("urlId", 91);
        }
        mvc.perform(post("/certificate/grant/template/" + (editing ? "update" : "add"))
                        .session(session).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate_type", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT type_name FROM certificate_type", String.class)).isEqualTo("算法比赛证书");
        assertThat(jdbc.queryForObject("SELECT url_id FROM certificate_type", Long.class))
                .isEqualTo("update-new".equals(operation) ? 91L : 90L);
        assertThat(jdbc.queryForObject("SELECT rating_enabled FROM certificate_type", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("SELECT template_schema FROM certificate_type", String.class))
                .contains("\"version\":2", "<name>", "\"bold\":true", "\"underline\":true");
        assertThat(jdbc.queryForList("SELECT access_purpose FROM file_system", String.class)).containsOnly("TEMPLATE");
    }

    @Test
    void invalidSchemaDoesNotCreateATemplate() throws Exception {
        Map<String, Object> payload = payload();
        payload.put("templateSchema", "{invalid-json}");
        mvc.perform(post("/certificate/grant/template/add").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("模板占位符配置格式错误"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate_type", Integer.class)).isZero();
    }

    @Test
    void legacyTemplateSaveHandlesNullProjectedContentRows() throws Exception {
        legacyPublicContent();
        mvc.perform(post("/certificate/grant/template/add").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate_type", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT access_purpose FROM file_system WHERE file_id=90", String.class)).isEqualTo("TEMPLATE");
    }

    @Test
    void realPublicReferencesAfterNullRowsStillPreventReusingTheFileAsATemplate() throws Exception {
        legacyPublicContent();
        jdbc.update("INSERT INTO association_member (introduction) VALUES ('![image](/api/file/image/90)')");
        mvc.perform(post("/certificate/grant/template/add").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(payload())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("已公开展示的文件不能作为私有证书材料或模板，请重新上传"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM certificate_type", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT access_purpose FROM file_system WHERE file_id=90", String.class)).isEqualTo("GENERAL");
    }

    private void legacyPublicContent() {
        jdbc.update("UPDATE file_system SET access_purpose='GENERAL' WHERE file_id=90");
        jdbc.execute("CREATE TABLE university (logo_file_id BIGINT)");
        jdbc.execute("CREATE TABLE school (logo_file_id BIGINT)");
        jdbc.execute("CREATE TABLE file_relation (file_id BIGINT, biz_type VARCHAR(50))");
        jdbc.execute("CREATE TABLE sys_intro (category VARCHAR(50), content TEXT)");
        jdbc.execute("CREATE TABLE notice (cover_file_id BIGINT, content TEXT)");
        jdbc.execute("CREATE TABLE event (description TEXT)");
        jdbc.execute("CREATE TABLE training (description TEXT)");
        jdbc.execute("CREATE TABLE contest_category (logo_file_id BIGINT, intro TEXT)");
        jdbc.execute("CREATE TABLE association_member (avatar_file_id BIGINT, introduction TEXT)");
        jdbc.execute("CREATE TABLE team (team_image_id BIGINT, introduce TEXT)");
        jdbc.update("INSERT INTO association_member (introduction) VALUES (NULL)");
        jdbc.update("INSERT INTO team (introduce) VALUES (NULL)");
    }

    private void existingTemplate() {
        jdbc.update("""
                INSERT INTO certificate_type (type_name, type_id, url_id, time, term, update_user_id,
                    font_size, is_team, level_scope, template_schema)
                VALUES ('旧模板', 1, 90, TIMESTAMP '2026-10-01 00:00:00', 1, 8, 24, 0, '院级', '{}')
                """);
    }

    private Map<String, Object> payload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("typeName", "算法比赛证书");
        payload.put("typeId", 1);
        payload.put("levelScope", "院级");
        payload.put("urlId", 90);
        payload.put("time", "2026-10-21");
        payload.put("term", 1);
        payload.put("updateUserId", 8);
        payload.put("isTeam", 0);
        payload.put("ratingEnabled", false);
        payload.put("templateSchema", "{\"blocks\":[{\"content\":\"恭喜<name>获得<award>\",\"bold\":true,\"underline\":true}]}");
        return payload;
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    @MapperScan(basePackageClasses = FileSystemMapper.class)
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:certificate_templates;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean JdbcTemplate jdbcTemplate(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean PlatformTransactionManager transactionManager(DataSource dataSource) { return new DataSourceTransactionManager(dataSource); }
        @Bean SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception {
            var factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            return factory.getObject();
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
        @Bean CertificateGrantController controller() { return new CertificateGrantController(); }
        @Bean CertificateGrantService grantService() { return new CertificateGrantService(); }
        @Bean CertificateTemplateRenderService renderService() { return new CertificateTemplateRenderService(); }
        @Bean FileAccessPolicy fileAccessPolicy() { return new FileAccessPolicy(); }
    }
}
