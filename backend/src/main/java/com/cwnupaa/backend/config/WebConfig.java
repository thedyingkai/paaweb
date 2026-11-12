package com.cwnupaa.backend.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private LoginInterceptor loginInterceptor;

    @Value("${file.upload.path}")
    private String uploadPath;

    @Bean
    @Primary
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Bean
    public RestTemplate trackerRestTemplate() {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(15000);
        requests.setReadTimeout(30000);
        return new RestTemplate(requests);
    }

    @Bean
    public RestTemplate qojRestTemplate() {
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(java.net.HttpURLConnection connection, String method) throws java.io.IOException {
                super.prepareConnection(connection, method);
                connection.setInstanceFollowRedirects(false);
            }
        };
        requests.setConnectTimeout(15000);
        requests.setReadTimeout(30000);
        return new RestTemplate(requests);
    }

    private String[] corsOrigins() {
        String configured = System.getenv("CORS_ALLOWED_ORIGINS");
        if (configured == null || configured.isBlank()) return new String[]{com.cwnupaa.backend.utils.DeploymentUrls.site()};
        return java.util.Arrays.stream(configured.split(",")).map(String::trim)
                .filter(origin -> !origin.isBlank() && !origin.contains("*")).toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**").allowedOrigins(corsOrigins()).allowCredentials(true).allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS").allowedHeaders("*").maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/send-code",
                        "/user/external/send-code",
                        "/user/login-by-code",
                        // 注意：/user/search 不在白名单，需登录（防未认证按邮箱/手机号/学号检索用户）
                        "/user/external/login",
                        "/user/register/config",
                        // 注册页每一步的即时唯一性校验必须匿名可访问：
                        // 路径模式 /user/register 只匹配该路径本身、不覆盖子路径，
                        // 漏掉这一条会让注册页的校验请求拿到 401，前端只能提示通用错误，
                        // “用户名已存在/邮箱已被注册”这类具体原因永远显示不出来。
                        "/user/register/validate-step",
                        "/user/activate/verify-identity",
                        "/user/activate-by-identity",
                        "/common/**",
                        "/certificate/grant/public/**",
                        "/certificate/grant/guest/**",
                        "/error",
                        "/external/board/**", // Allow external crawler access without login session
                        "/external/**" // Allow all external API
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String path = uploadPath;
        if (!path.endsWith(File.separator) && !path.endsWith("/")) {
            path = path + File.separator;
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations("file:" + path);
    }
}
