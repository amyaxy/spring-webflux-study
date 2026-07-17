# 速查表 & 常见模式

---

## 速查表

### 弹性注解（`spring-context` → `org.springframework.resilience.annotation`）

| 注解 | 功能 | 启用 | 包路径 |
|------|------|------|--------|
| `@Retryable` | 声明式重试（含响应式适配） | `@EnableResilientMethods` | `org.springframework.resilience.annotation` |
| `@ConcurrencyLimit` | 并发限流（BLOCK / REJECT） | `@EnableResilientMethods` | `org.springframework.resilience.annotation` |
| `@EnableResilientMethods` | 统一开启弹性注解 | — | `org.springframework.resilience.annotation` |

### API Versioning（**无 `@ApiVersion` 注解**）

| API | 用途 |
|-----|------|
| `RequestPredicates.version(Object)` | 版本化路由谓词 |
| `WebFluxConfigurer.configureApiVersioning()` | 版本解析策略配置 |
| `ApiVersionConfigurer` | 配置器（header, media-type, query, path） |
| `ApiVersionDeprecationHandler` | 版本废弃处理 |

### 核心类

| 类 | 包 | 用途 | 注意事项 |
|----|----|------|---------|
| `RetryTemplate` | `org.springframework.core.retry` | 编程式重试 | **无 builder**，setter 模式 |
| `RetryPolicy` | `org.springframework.core.retry` | 重试策略 | 有内部 Builder |
| `HttpMessageConverters` | `org.springframework.http.converter` | 集中式 converter 配置 | `forClient()` / `forServer()` |
| `ServerCodecConfigurer` | `org.springframework.http.codec` | WebFlux 编解码配置 | `WebFluxConfigurer` 实际参数 |

---

## 常见模式

### 模式 1：弹性 API 网关（与项目代码对齐）

```java
@RestController
@RequestMapping("/api/gateway")
public class ResilientGatewayController {

    private final ResilientUserService userService;

    @ConcurrencyLimit(20)
    @GetMapping("/users/{id}")
    public Mono<UserProfile> getUserViaElasticService(@PathVariable Long id) {
        return Mono.fromCallable(() -> userService.getUser(id));
    }

    // 手动 reactive retry 版本
    @GetMapping("/users/{id}/reactive")
    public Mono<UserProfile> getUserViaReactiveRetry(@PathVariable Long id) {
        return Mono.fromCallable(() -> userService.getUser(id))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(800))
                .jitter(0.1)
                .filter(t -> t instanceof TimeoutException));
    }
}
```

### 模式 2：多版本 API 共存（函数式端点）

```java
@Configuration
public class UserEndpointConfig {
    // v1 — 基础信息（无 email）
    @Bean
    public RouterFunction<ServerResponse> v1UserRoutes() { ... }

    // v2 — 完整信息（含 email）
    @Bean
    public RouterFunction<ServerResponse> v2UserRoutes() { ... }
}
```

### 模式 3：Resilience 配置类

```java
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
    // 只需开启 @EnableResilientMethods，无需额外配置
}
```

### 模式 4：模拟下游故障（与项目代码对齐）

```java
@Component
public class DownstreamServiceSimulator {

    private final AtomicInteger counter = new AtomicInteger(0);

    public UserProfile fetchUser(Long id) {
        // 前 2 次模拟超时，之后成功
        int attempt = counter.incrementAndGet();
        if (attempt <= 2) {
            log.warn("Downstream timeout on attempt #{}", attempt);
            throw new TimeoutException("Downstream timeout");
        }
        log.info("Downstream success on attempt #{}", attempt);
        return new UserProfile(id, "User-" + id, "user" + id + "@example.com");
    }
}
```