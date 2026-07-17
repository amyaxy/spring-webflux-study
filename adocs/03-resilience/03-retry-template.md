# `RetryTemplate` — 编程式重试（真实 API）

> Spring 7.x 将 `RetryTemplate` 从 Spring Retry 迁移至 `spring-core`。
> 新包路径：`org.springframework.core.retry`

---

## 包结构

```java
org.springframework.core.retry
├── RetryTemplate               // 重试执行器（无 builder）
├── RetryPolicy                 // 重试策略（内置 Builder）
│   └── Builder                 // 策略构建器
├── RetryOperations             // 执行接口
├── RetryListener               // 监听器
├── RetryState                  // 重试状态
├── Retryable<T>                // 执行回调
├── RetryException              // 重试异常
└── support/
    ├── RetryTask               // 线程安全的任务包装
    └── CompositeRetryListener   // 组合监听器
```

## 真实 API（基于 7.0.8 字节码反编译）

**关键发现**：`RetryTemplate` **没有 builder**，只有 setter 注入模式。

```java
// ❌ 不存在：RetryTemplate.builder().maxRetries(3).exponentialBackOff(...)
// ✅ 正确做法：
RetryPolicy policy = new RetryPolicy(); // 或 RetryPolicy.builder()...
RetryTemplate template = new RetryTemplate(policy);
template.setRetryListener(new MyRetryListener());
```

### `RetryPolicy` 使用

```java
// 使用默认策略（重试 3 次）
RetryTemplate template = new RetryTemplate(new RetryPolicy());

// 自定义策略（通过 setter，无 builder 文档）
// RetryPolicy 在 spring-core 中提供，但其内部 Builder 的 API 需查阅源码
```

### 示例（与项目代码对齐）

```java
RetryTemplate template = new RetryTemplate();
template.setRetryPolicy(new RetryPolicy());

UserProfile result = template.execute(new Retryable<>() {
    @Override
    public UserProfile run() {
        log.info("Retry attempt...");
        return downstream.fetchUser(id);
    }
});
```

## `@Retryable` vs `RetryTemplate`

| 特性 | `@Retryable` | `RetryTemplate` |
|------|-------------|-----------------|
| 方式 | 声明式（注解） | 编程式 |
| 适用场景 | 简单重试、标准退避 | 复杂策略、动态重试状态 |
| 响应式 | 自动适配 | 需手动 Reactor retryWhen |
| 运行时配置 | 编译期固定 | 可动态调整 |
| 恢复回调 | `@Recover` 方法 | `execute()` 自行处理异常 |

> 💡 在 resilient-api 项目中，优先使用 `@Retryable` 注解，仅对需要动态策略的场景使用 `RetryTemplate`。