package cloud.imuyi.resilient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.config.ApiVersionConfigurer;
import org.springframework.web.reactive.config.WebFluxConfigurer;

/**
 * API Versioning 配置 —— 请求头 + 媒体类型参数版本解析
 *
 * <p>支持两种版本解析方式：
 * <ul>
 *   <li>请求头：{@code X-API-Version: 2}</li>
 *   <li>媒体类型参数：{@code Accept: application/json; version=2}</li>
 * </ul>
 *
 * <p>默认版本为 {@code "1"}，未指定版本时使用默认值。
 */
@Configuration(proxyBeanMethods = false)
public class ApiVersionConfig implements WebFluxConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            // 请求头解析：X-API-Version
            .useRequestHeader("X-API-Version")
            // 媒体类型参数兜底：application/json; version=2
            .useMediaTypeParameter(MediaType.APPLICATION_JSON, "version")
            // 设置默认版本
            .setDefaultVersion("1");
    }
}