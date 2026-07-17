# 阶段三：Spring 7.x 弹性特性与 API Versioning

> Spring Framework 7.0.8 内置弹性特性（`spring-context`/`resilience` 包）+ WebFlux API Versioning。
>
> 源码模块：`resilient-api/`

---

## 📌 核心知识点

| # | 主题 | 真实 API | 说明 |
|---|------|----------|------|
| 1 | `@Retryable` | `org.springframework.resilience.annotation.Retryable` | 声明式重试，支持指数退避 + jitter + 自定义 predicate |
| 2 | `@ConcurrencyLimit` | `org.springframework.resilience.annotation.ConcurrencyLimit` | 并发限流，支持 `ThrottlePolicy`（阻塞/快速失败） |
| 3 | `RetryTemplate` | `org.springframework.core.retry.RetryTemplate` | 编程式重试，`new RetryTemplate(RetryPolicy)`，无 builder |
| 4 | `@EnableResilientMethods` | 统一开关 | 一键开启弹性注解 |
| 5 | 响应式重试 | `@Retryable` 自动适配 `Mono`/`Flux` | 装饰 Reactor retry pipeline |
| 6 | API Versioning | `RequestPredicates.version(Object)` 谓词 | **无 `@ApiVersion` 注解**，纯函数式端点 |
| 7 | `HttpMessageConverters` | `org.springframework.http.converter.HttpMessageConverters` | `HttpMessageConverters.forClient()` / `forServer()` |
| 8 | 弹性网关 | `ResilientGatewayController` | 非版本化 `@RestController`，与函数式端点共存 |

---

## ⚠️ 重要：Spring 7.x 真实 API 变化

| 特性 | 常见误解（Spring 6.x 或旧文档） | 实际 API（Spring 7.0.8） |
|------|-------------------------------|--------------------------|
| `@ApiVersion` 注解 | `@ApiVersion("2")` 或 `@ApiVersion(value="1", deprecated=true)` | **不存在** → 改用 `RequestPredicates.version(Object)` 谓词 |
| RetryTemplate 构建 | `RetryTemplate.builder().maxRetries(3).exponentialBackOff(...)` | `new RetryTemplate(policy)`，setter 模式无 builder |
| SimpleRetryPolicy | 独立的 Policy 类 | `org.springframework.core.retry.RetryPolicy` 直建 |
| ExponentialBackOff | 独立的 BackOff 类 | 不存在，`RetryPolicy.Builder` 内嵌配置 |
| WebFluxConfigurer 消息转换 | `configureHttpMessageCodecs(HttpMessageConverters.ServerBuilder)` | `configureHttpMessageCodecs(ServerCodecConfigurer)` |
| 响应式 retry jitter | `@Retryable(jitter=10)` 单位 ms | 单位 ms（匹配 Reactor 的 `jitter(0.1)` = 10%） |
| Virtual Threads 配置 | `spring.threads.virtual.enabled=true` | Boot 4.x 默认开启，无需配置 |

---

## 📁 目录索引

| 文件 | 主题 |
|------|------|
| [`01-retryable.md`](./01-retryable.md) | `@Retryable` 真实 API 详解 |
| [`02-concurrency-limit.md`](./02-concurrency-limit.md) | `@ConcurrencyLimit` 并发限流（含 ThrottlePolicy） |
| [`03-retry-template.md`](./03-retry-template.md) | `RetryTemplate` 编程式重试 |
| [`04-api-versioning.md`](./04-api-versioning.md) | API Versioning（**无 `@ApiVersion`**，纯函数式） |
| [`05-reactive-retry.md`](./05-reactive-retry.md) | 响应式手动 retry + `@Retryable` 自动适配 |
| [`06-http-message-converters.md`](./06-http-message-converters.md) | `HttpMessageConverters` 使用 |
| [`07-common-patterns.md`](./07-common-patterns.md) | 速查表 & 常见模式 |

---

## 🔧 产出模块

**`resilient-api/`** — 弹性增强版 API 项目，6 个集成测试全部通过。

```
resilient-api/
├── pom.xml
├── src/main/java/cloud/imuyi/resilient/
│   ├── ResilientApiApplication.java
│   ├── config/
│   │   ├── ResilienceConfig.java              # @EnableResilientMethods
│   │   └── ApiVersionConfig.java              # WebFluxConfigurer → ApiVersionConfigurer
│   ├── endpoint/
│   │   └── UserEndpointConfig.java            # v1/v2 RouterFunction（函数式端点）
│   ├── controller/
│   │   └── ResilientGatewayController.java    # 非版本化弹性网关
│   ├── service/
│   │   ├── ResilientUserService.java          # @Retryable + @ConcurrencyLimit
│   │   └── DownstreamServiceSimulator.java    # 下游模拟器（固定前3次超时）
│   └── model/
│       └── UserProfile.java
└── src/test/java/cloud/imuyi/resilient/
    └── ResilientApiTests.java                 # 6 个集成测试
```

## 弹性网关架构

```
请求 → X-API-Version: 2  →  ApiVersionConfigurer 解析 → RouterFunction 路由
    ├── version("1") → v1 端点（无 email）
    └── version("2") → v2 端点（含 email）

请求 → /api/gateway/*  →  ResilientGatewayController（注解式路由）
    └── @Retryable(maxRetries=3, delay=100ms, multiplier=2.0, jitter=10)
    └── @ConcurrencyLimit(20)
    └── 手动 retryWhen 演示（/reactive 端点）
```

## 应用配置

```yaml

spring:
  application:
    name: resilient-api

  threads:
    virtual:
      enabled: true

```