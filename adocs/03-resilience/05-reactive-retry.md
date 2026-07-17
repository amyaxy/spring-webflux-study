# 响应式重试 — Mono/Flux + `@Retryable`

> 当 `@Retryable` 作用于返回 `Mono`/`Flux` 的方法时，自动切换为 Reactor retry pipeline。

---

## 自动适配原理

`@Retryable` 作用于返回 `Mono`/`Flux` 的方法时，由 `AbstractRetryInterceptor.ReactorDelegate` 代理，将注解参数转换为 Reactor 的 `retryWhen`：

```java
@Retryable(includes = TimeoutException.class, maxRetries = 3, delay = 100, multiplier = 2.0)
public Mono<UserProfile> getUserReactive(Long id) {
    return Mono.fromCallable(() -> downstream.fetchUser(id));
}
```

等价于手动：

```java
public Mono<UserProfile> getUserReactive(Long id) {
    return Mono.fromCallable(() -> downstream.fetchUser(id))
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .maxBackoff(Duration.ofMillis(800))
            .jitter(0.1)
            .filter(throwable -> throwable instanceof TimeoutException));
}
```

## 配置映射

| `@Retryable` 属性 | Reactor `Retry` API |
|--------------------|---------------------|
| `maxRetries = 3` | `Retry.max(3)` 或 `Retry.backoff(3, ...)` |
| `delay = 100` | `Duration.ofMillis(100)` |
| `multiplier = 2.0` | `Retry.backoff` 内置指数退避 |
| `jitter = 10`（ms） | `.jitter(0.1)`（10%，注意单位换算） |
| `maxDelay = 0`（无限制） | `.maxBackoff(Duration.ofMillis(800))` ≈ 100×2³ |

## 手动 Reactive retry 示例（与项目代码对齐）

```java
// ResilientGatewayController 手动 retry 版本
@GetMapping("/users/{id}/reactive")
public Mono<UserProfile> getUserViaReactiveRetry(@PathVariable Long id) {
    return Mono.fromCallable(() -> userService.getUser(id))
        .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
            .maxBackoff(Duration.ofMillis(800))
            .jitter(0.1)
            .filter(t -> t instanceof TimeoutException));
}
```

## 注意事项

- `@Retryable` 的 `jitter` 单位是 **ms**（整数），Reactor 的 `jitter` 是 **百分比**（double）
- Reactor `Retry.backoff` 内部已实现指数退避 → `multiplier` 自动应用
- `@Retryable` 响应式适配不会在 `retryWhen` 的基础上再叠加 Reactor 手动 retry
- 手动 retry 比注解式更灵活，支持 `.doBeforeRetry()`、`.onRetryExhaustedThrow()` 等