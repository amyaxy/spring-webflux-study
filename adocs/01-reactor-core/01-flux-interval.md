# `Flux.interval` — 定时发射器

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java#fluxInterval()`

---

## 函数定义

```java
public Flux<Long> fluxInterval() {
    return Flux.interval(Duration.ofMillis(100)).take(5);
}
```

## 逐层拆解

| 环节 | 行为 |
|------|------|
| `Flux.interval(Duration.ofMillis(100))` | 每 100ms 从 0 开始递增发射：`0 → 100ms → 1 → 100ms → 2 → …`（**无限流**） |
| `.take(5)` | 只取前 5 个元素（0, 1, 2, 3, 4），取够后自动 `cancel()` 上游并 `onComplete()` |

## 完整执行时序

```
t=0ms      subscribe 触发，内部定时器启动
t=100ms    发射 0
t=200ms    发射 1
t=300ms    发射 2
t=400ms    发射 3
t=500ms    发射 4 ← take(5) 到达上限，立即 cancel 上游
                    → 发射 onComplete，流结束
```

## 关键特性

| 特性 | 说明 |
|------|------|
| **调度器** | 默认跑在 `Schedulers.parallel()` 上（独立定时器线程），不是主线程 |
| **冷流** | 每次 `subscribe()` 都会创建新的独立定时器，互不干扰 |
| **背压感知** | `interval` 内部会根据下游 `request(n)` 调整发射节奏，下游慢时不会积压 |
| **测试** | 用 `StepVerifier.withVirtualTime()` 跳过真实等待，避免测试跑 500ms |

## 测试对照

```java
StepVerifier.withVirtualTime(() -> playground.fluxInterval())
        .thenAwait(Duration.ofSeconds(1))   // 虚拟快进 1s
        .expectNext(0L, 1L, 2L, 3L, 4L)    // 断言 5 个值
        .verifyComplete();
```

## 应用场景

| 场景 | 代码模式 |
|------|----------|
| ✅ 定时轮询 | `Flux.interval(Duration.ofSeconds(10)).flatMap(tick → fetchStatus())` |
| ✅ 心跳 / 健康检查 | `Flux.interval(Duration.ofSeconds(5)).map(tick → ping())` |
| ✅ 限流 / 节流 | `Flux.interval(Duration.ofMillis(200)).zipWith(source)` 配对节流 |
| ✅ SSE 事件推送 | `Flux.interval(Duration.ofMillis(100)).map(i → "data: " + i)` |
| ❌ 精确时间调度 | 用 `Scheduler` / `@Scheduled`（interval 没有 compensate 机制） |

---

## 关联参考

- 阶段一笔记：[README.md](./README.md)
- 阶段一「实战模式」中 SSE 事件流也用到了 `interval + take`