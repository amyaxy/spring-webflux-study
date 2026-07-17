# 阶段三：Spring 7.x 弹性特性与 API Versioning

> **日期**：2026-07-17
> **模块**：`resilient-api`
> **Spring Boot**：4.1.0 / **Spring Framework**：7.0.8
>
> 源码：`ai-coding/spring-webflux-study/resilient-api/`

---

## 🌟 核心特性

### 1. 声明式弹性 Retry（`@Retryable`）

Spring 7.x 将重试处理从独立的 `spring-retry` 库合并进 `spring-context` 模块，包路径变为 `org.springframework.resilience.annotation.Retryable`。

```java
@Retryable(
    includes = TimeoutException.class,
    maxRetries = 3,
    delay = 100,
    multiplier = 2.0,
    jitter = 10
)
public UserProfile getUser(Long id) {
    return downstream.fetchUser(id);
}
```

**关键参数详解**：

| 参数 | 说明 | 示例 |
|------|------|------|
| `maxRetries` | 最大重试次数（不含首次调用） | `3` |
| `delay` | 初始延迟（ms） | `100` |
| `multiplier` | 指数退避倍率，每次 delay × multiplier | `2.0` → 100→200→400ms |
| `jitter` | 抖动范围（±ms），避免惊群效应 | `10` |
| `includes` | 触发重试的异常类型 | `TimeoutException.class` |
| `excludes` | 不触发重试的异常类型 | — |

**启用**：任意 `@Configuration` 类上标注 `@EnableResilientMethods`。

> 💡 **注意**：Spring 7 内置的 `@Retryable` 自动适配 `Mono`/`Flux`，返回响应式类型的方法仍能得到 AOP 增强，无需手动构建 retryWhen。

### 2. 并发限流（`@ConcurrencyLimit`）

同属 `org.springframework.resilience.annotation` 包，声明式控制方法的最大并发数。

```java
@ConcurrencyLimit(20)
public Mono<UserProfile> getUserReactive(Long id) { ... }
```

超过限制的请求会被阻塞等待，直到有槽位释放。

### 3. API Versioning（`RequestPredicates.version`）

**重要变化**：Spring 7.x **彻底去掉了** `@ApiVersion` 注解，改用函数式端点（RouterFunction）+ 内置版本匹配谓词。

**完整的三部曲**：

```java
// Step 1: 配置版本解析策略
@Configuration
public class ApiVersionConfig implements WebFluxConfigurer {
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .useRequestHeader("X-API-Version")                          // 方案A：请求头
            .useMediaTypeParameter(MediaType.APPLICATION_JSON, "version") // 方案B：Accept 参数
            .setDefaultVersion("1");
    }
}

// Step 2: v1 路由
@Bean
public RouterFunction<ServerResponse> v1UserRoutes() {
    return RouterFunctions.route()
        .GET("/api/users/{id}",
            accept(MediaType.APPLICATION_JSON).and(version("1")),
            req -> {
                Long id = Long.valueOf(req.pathVariable("id"));
                return ServerResponse.ok().body(
                    userService.getUserReactive(id).map(u -> new UserProfile(u.getId(), u.getName(), null)),
                    UserProfile.class);
            })
        .build();
}

// Step 3: v2 路由
@Bean
public RouterFunction<ServerResponse> v2UserRoutes() {
    return RouterFunctions.route()
        .GET("/api/users/{id}",
            accept(MediaType.APPLICATION_JSON).and(version("2")),
            req -> ServerResponse.ok().body(userService.getUserReactive(id), UserProfile.class))
        .GET("/api/users",
            accept(MediaType.APPLICATION_JSON).and(version("2")),
            req -> ServerResponse.ok().body(
                Flux.just(new UserProfile(1L, "Alice", "alice@example.com"), ...),
                UserProfile.class))
        .build();
}
```

**支持两种版本解析方式**：

| 方式 | 请求示例 |
|------|---------|
| 请求头 | `X-API-Version: 2` |
| 媒体类型参数 | `Accept: application/json; version=2` |

### 4. 手动 Reactive Retry（`retryWhen`）

除了声明式 `@Retryable`，还演示了纯 reactor 的手动重试方式，适合无需 Spring AOP 的场景：

```java
Mono.fromCallable(() -> userService.getUser(id))
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .maxBackoff(Duration.ofMillis(800))
        .jitter(0.1)
        .filter(t -> t instanceof TimeoutException));
```

---

## 🧱 模块结构

```
resilient-api/
├── pom.xml
├── src/main/java/cloud/imuyi/resilient/
│   ├── ResilientApiApplication.java            # 启动类
│   ├── config/
│   │   ├── ResilienceConfig.java               # @EnableResilientMethods
│   │   └── ApiVersionConfig.java               # WebFluxConfigurer 实现
│   ├── endpoint/
│   │   └── UserEndpointConfig.java             # v1/v2 RouterFunction 端点
│   ├── controller/
│   │   └── ResilientGatewayController.java     # 弹性网关（非版本化控制器）
│   ├── model/
│   │   └── UserProfile.java                    # 用户模型
│   └── service/
│       ├── ResilientUserService.java           # @Retryable + @ConcurrencyLimit
│       └── DownstreamServiceSimulator.java     # 模拟下游（前3次超时）
└── src/test/java/cloud/imuyi/resilient/
    └── ResilientApiTests.java                  # 6 个集成测试
```

---

## 🧪 测试覆盖

6 个集成测试全部通过（`mvn clean test`）：

| 测试 | 验证点 | 通过 |
|------|--------|:----:|
| `shouldReturnV1UserByName` | `X-API-Version: 1` → v1 端点，email 为 null | ✅ |
| `shouldReturnV2UserWithEmail` | `X-API-Version: 2` → v2 端点，email 非 null | ✅ |
| `shouldListV2UsersWithEmails` | v2 列表返回 3 条含 email 的记录 | ✅ |
| `shouldAccessGatewayEndpoint` | 非版本化弹性网关正常路由 | ✅ |
| `shouldStreamUsersViaSSE` | SSE 流式响应 3 条数据 | ✅ |
| `shouldAccessReactiveRetryEndpoint` | Reactive retry 端点正常 | ✅ |

---

## 🆚 Spring 6.x vs 7.x 关键对比

| 特性 | Spring 6.x | Spring 7.x |
|------|-----------|-----------|
| 重试注解 | `@Retryable`（`spring-retry` 独立库） | `@Retryable`（`spring-context` 内置） |
| 重试启用 | `@EnableRetry` | `@EnableResilientMethods` |
| 并发限流 | 需自定义或引入 resilience4j | `@ConcurrencyLimit`（内置） |
| API 版本注解 | `@ApiVersion` | **不存在**，已移除 |
| 版本化路由 | `@ApiVersion` + 自定义 HandlerMapping | `RequestPredicates.version(Object)` 谓词 |
| 版本配置器 | 自定义实现 | `ApiVersionConfigurer`（内置于 WebFlux） |
| WebTestClient 注入 | `@AutoConfigureWebTestClient` | 不存在，改用手动 `bindToServer()` |

---

## 🕳️ 踩坑记录

### ⚠️ 坑1：`@ApiVersion` 注解已不存在

**现象**：引入 `org.springframework.web.bind.annotation.ApiVersion` 找不到。

**原因**：Spring 7.x 完全移除了 `@ApiVersion` 注解，版本化只能通过函数式端点方式实现。

**解决**：
- 用 `RouterFunction` + `RequestPredicates.version(Object)` 替代
- 通过 `ApiVersionConfigurer` 配置解析策略

### ⚠️ 坑2：`@AutoConfigureWebTestClient` 已移除

**现象**：`org.springframework.boot.test.autoconfigure.web.reactive` 包不存在。

**原因**：Spring Boot 4.x 移除了自动配置测试注解的模块。

**解决**：手动构建 WebTestClient：

```java
@LocalServerPort
private int port;

@BeforeEach
void setUp() {
    webTestClient = WebTestClient.bindToServer()
        .baseUrl("http://localhost:" + port)
        .build();
}
```

### ⚠️ 坑3：`WebFluxConfigurer` 配置 `configureApiVersioning` 参数签名

**现象**：`ApiVersionConfigurer` 的方法链支持 `useRequestHeader`、`useMediaTypeParameter`、`setDefaultVersion`。

**注意**：不是所有 boot 文档中的注解式配置都适用于 Spring 7.x，有些 API 签名需要直接翻阅 jar 中的实际字节码确认。

---

## 📚 学习成果

1. ✅ 掌握 Spring 7.x 内置 `@Retryable` 用法（参数含义、启用方式、响应式适配）
2. ✅ 理解 `@ConcurrencyLimit` 的声明式限流模式
3. ✅ 学会函数式端点的 API 版本化（`version()` 谓词 + `ApiVersionConfigurer`）
4. ✅ 验证重试成功日志链路：`timeout → retry#1 → timeout → retry#2 → success`
5. ✅ 掌握 `jitter` 抖动参数在重试中的价值（防惊群）
6. ✅ 学会手动 WebTestClient 构建，适配 Boot 4.x 的测试框架变化
7. ✅ 学会通过 `javap` 反编译 jar 来确认真实 API 签名