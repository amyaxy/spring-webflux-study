# `@ConcurrencyLimit` — 并发限流（真实 API）

> Spring 7.x 内置的并发限流注解，适用于 Virtual Threads 场景保护下游资源。

---

## 动机

在 Virtual Threads 模式下，传统线程池限流不再适用。`@ConcurrencyLimit` 提供**语义层面的并发控制**，而非线程池容量控制。

## 实际包路径

```
org.springframework.resilience.annotation.ConcurrencyLimit
```

## 注解参数（基于 7.0.8 字节码反编译）

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `value` / `limit` | — | 最大并发数 |
| `limitString` | `""` | 字符串形式 limit（支持占位符） |
| `policy` | — | 限流策略：`ThrottlePolicy` 枚举 |

### `ThrottlePolicy` 枚举

| 值 | 说明 |
|----|------|
| `BLOCK` | 阻塞等待（默认，适合 Virtual Threads） |
| `REJECT` | 快速失败，抛出 `InvocationRejectedException` |

## 示例（与项目代码对齐）

```java
@Service
public class ResilientUserService {

    @ConcurrencyLimit(20)
    @Retryable(maxRetries = 3, delay = 100, multiplier = 2.0)
    public UserProfile getUser(Long id) {
        return downstream.fetchUser(id);
    }
}
```

## 工作原理

```
请求到达 → @ConcurrencyLimit semaphore.acquire()
            │ 并发数 < 20 → 继续执行
            │ 并发数 ≥ 20 → BLOCK: 阻塞等待
            │               REJECT: 抛出 InvocationRejectedException
            ↓
        执行方法体（含 @Retryable 的重试逻辑）
            ↓
        semaphore.release()
```

## 警告：重试期间 permit 被持有

`@ConcurrencyLimit` 在 `@Retryable` 的**外层**执行：

1. 先检查并发限流 → 获取 permit
2. 执行方法体（含 `@Retryable` 的多次重试）
3. 释放 permit

这意味着**每次重试期间 permit 一直被持有**，如果重试次数多 + delay 长，并发容量会降低。

## 与 Virtual Threads

Boot 4.x 默认开启 Virtual Threads，`@ConcurrencyLimit(BLOCK)` 与 VTs 搭配最佳：

- VTs 处理大量并发请求（轻量级，不阻塞平台线程）
- `@ConcurrencyLimit` 保护下游数据库/外部 API（防止过载）