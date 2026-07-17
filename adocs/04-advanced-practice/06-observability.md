# 06 · 可观测性

## 概述

Spring Boot Actuator + Micrometer + OpenTelemetry 提供了开箱即用的可观测性能力。

## 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
```

## 配置

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics
  tracing:
    sampling:
      probability: 1.0
```

## Actuator 端点

| 端点 | 路径 | 用途 |
|------|------|------|
| `health` | `/actuator/health` | 应用健康检查 |
| `metrics` | `/actuator/metrics` | JVM 指标、请求统计 |
| `info` | `/actuator/info` | 应用信息 |

## 响应式应用的特殊考量

### WebFlux 的指标自动收集

Spring Boot Actuator 自动为 WebFlux 端点收集：

- `http.server.requests` — 请求耗时、状态码、异常
- `reactor.netty.*` — Netty 事件循环指标
- `r2dbc.*` — 连接池和查询指标

### 分布式追踪

使用 Micrometer Tracing，响应式应用的追踪通过 Reactor Context 传播：

```java
// 自动生成 traceId/spanId，跨异步边界自动传递
@GetMapping("/api/users")
public Flux<User> listUsers() {
    return userService.findAll();
    // 每个请求自动生成 traceId，日志自动关联
}
```

## 日志配置建议

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
```

在生产环境推荐 MDC 模式：

```yaml
logging:
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] [%X{traceId:-}] %-5level %logger{36} - %msg%n"
```

## 踩坑记录

### 采样率设置

开发环境设置 `probability: 1.0` 全量采样，生产环境建议设为 `0.1`（10%）避免存储压力。

### 端点暴露

WebFlux 的 Actuator 默认端口与应用端口一致。生产环境建议通过 `management.server.port` 隔离到管理端口。