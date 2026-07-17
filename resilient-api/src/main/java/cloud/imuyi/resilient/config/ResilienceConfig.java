package cloud.imuyi.resilient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;

/**
 * 弹性特性配置 —— 开启 @Retryable、@ConcurrencyLimit 注解
 *
 * <p>Spring 7.x 专用：{@link EnableResilientMethods} 提供声明式重试与并发限流。
 */
@Configuration(proxyBeanMethods = false)
@EnableResilientMethods
public class ResilienceConfig {
}