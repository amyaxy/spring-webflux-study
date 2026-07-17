# 05 · Virtual Threads 混合架构

## 概述

Spring Boot 3.4+（对应本项目的 4.x）支持 Virtual Threads。在 WebFlux 应用中，可以混用 Virtual Threads 处理阻塞 I/O 操作，同时保持响应式端点的非阻塞特性。

## 启用 Virtual Threads

```yaml
spring:
  threads:
    virtual:
      enabled: true
```

这个配置使 Spring MVC（如果使用）的请求处理在虚拟线程中运行。对于 WebFlux，**默认仍使用事件循环**，但可以显式指定某些方法走虚拟线程。

## 混合架构模式

### 阻塞方法（Virtual Threads）

```java
public User findByIdBlocking(Long id) {
    return userRepository.findById(id).block(); // 阻塞等待
}
```

这个 `block()` 调用在虚拟线程中执行时，虚拟线程会被挂起而非阻塞平台线程，实现了高效阻塞。

### 响应式方法（WebFlux 事件循环）

```java
public Mono<User> findById(Long id) {
    return userRepository.findById(id); // 完全非阻塞
}
```

## 何时使用哪种

| 场景 | 推荐模式 | 理由 |
|------|---------|------|
| 纯 R2DBC 查询 | 响应式 | 零阻塞，零上下文切换 |
| 调用第三方 REST API | 响应式（WebClient） | 天然非阻塞 |
| 数据库批量操作 | 响应式 | FlatMap 并发控制 |
| Legacy JDBC 调用 | Virtual Threads | block() 在 VT 中高效挂起 |
| 复杂计算/IO 混合 | Virtual Threads | 避免复杂响应式链 |
| 文件系统操作 | Virtual Threads | 无响应式替代方案 |

## 架构示意

```
┌──────────────┐     ┌──────────────────────┐
│  /api/users   │────▶│  WebFlux 事件循环     │──▶ R2DBC (非阻塞)
│  (响应式)     │     │  (少量平台线程)        │
└──────────────┘     └──────────────────────┘

┌──────────────┐     ┌──────────────────────┐
│  /api/users/  │────▶│  Virtual Threads     │──▶ JDBC (阻塞)
│  hybrid/{id}  │     │  (大量虚拟线程)        │
│  (VT 混合)    │     └──────────────────────┘
└──────────────┘
```

## 踩坑记录

### 1. `block()` 的事务边界

在虚拟线程中使用 `.block()` 会中断响应式事务上下文（`@Transactional` 基于 Reactor Context）。如果需要在事务中 `.block()`，应该使用 JDBC + 传统 `@Transactional`。

### 2. 线程池隔离

- 响应式路径：使用 Netty 事件循环（`reactor-http-nio-*`）
- 阻塞路径：使用虚拟线程（`<unnamed>`）
- 不要混用：不要在事件循环线程中调用 `.block()`