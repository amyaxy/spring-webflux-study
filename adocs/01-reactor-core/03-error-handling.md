# Reactor 错误处理

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java` — 错误处理（第 4 组）
> **测试定位**：`ReactorPlaygroundTest.java` — `fallbackExample` / `fallbackResumeExample` / `retryExample` / `timeoutExample`

---

## 核心理念：错误是终止事件

在 Reactor 中，错误（`onError`）和完成（`onComplete`）都是**终止信号**——一旦触发，流就结束了，后续不再发射任何元素。

```
正常流:  onNext → onNext → onNext → onComplete ✓
错误流:  onNext → onNext → onError(✗)          ← 到此为止
```

所以必须用操作符**在错误到达 Subscriber 之前拦截处理**。

---

## 一、四类错误处理操作符

### 1. `onErrorReturn` — 返回默认值

```java
public Mono<String> fallbackExample() {
    return Mono.<String>error(new RuntimeException("oops"))
            .onErrorReturn("fallback");
}
```

| 特性 | 说明 |
|------|------|
| 场景 | 已知错误只需一个兜底值 |
| 效果 | 拦截错误，发射一个默认值，然后 `onComplete` |
| 流状态 | 错误流 → **正常完成** |

**执行流程**：

```
原始: error("oops") ──→ onError
                         ↓
                  onErrorReturn("fallback")
                         ↓
                  "fallback" ──→ onComplete ✓
```

**测试**：
```java
StepVerifier.create(playground.fallbackExample())
        .expectNext("fallback")
        .verifyComplete();
```

**条件式返回**：根据异常类型返回不同默认值：
```java
Flux.just(1, 2, 0, 3)
    .map(i -> 100 / i)
    .onErrorReturn(ArithmeticException.class, -1)
    .onErrorReturn(NullPointerException.class, -2);
```

---

### 2. `onErrorResume` — 切换到备用流

```java
public Mono<String> fallbackResumeExample() {
    return Mono.<String>error(new RuntimeException("failed"))
            .onErrorResume(e -> Mono.just("resumed: " + e.getMessage()));
}
```

| 特性 | 说明 |
|------|------|
| 场景 | 想用另一个流替代错误的发生 |
| 效果 | 拦截错误，切换到备选 Publisher |
| 流状态 | 错误流 → **切换到新流继续** |

**执行流程**：

```
原始: error("failed") ──→ onError
                           ↓
                    onErrorResume(e → Mono.just(...))
                           ↓
                    "resumed: failed" ──→ onComplete ✓
```

**与 `onErrorReturn` 的差异**：`onErrorResume` 可以返回任意类型的 `Publisher`（Mono / Flux / 多个元素），而 `onErrorReturn` 只能返回单个值。

```java
// 场景：调用外部 API 失败，查本地缓存
Mono<User> user = fetchFromRemote(id)
    .onErrorResume(e -> fetchFromCache(id));

// 场景：按异常类型选择备用策略
.onErrorResume(BusinessException.class, e -> Mono.just(fallback))
.onErrorResume(TimeoutException.class, e -> retryLater(e));
```

**测试**：
```java
StepVerifier.create(playground.fallbackResumeExample())
        .expectNext("resumed: failed")
        .verifyComplete();
```

---

### 3. `retry` — 重新订阅重试

```java
public Flux<Integer> retryExample() {
    return Flux.range(1, 3)
            .map(i -> {
                if (i == 2) {
                    throw new RuntimeException("retry me");
                }
                return i;
            })
            .retry(2);
}
```

| 特性 | 说明 |
|------|------|
| 场景 | 操作可能因临时故障失败（网络抖动、数据库锁） |
| 效果 | **重新订阅整个上游**，从头开始发射 |
| 流状态 | 失败 → 重新订阅重试 → 失败次数超限 → 最终 `onError` |

**执行流程**（`retry(2)` 重试 2 次）：

```
① 初始: 1 → 2(抛异常 "retry me") → onError
          ↓ retry(2) 触发第1次重试
② 第1次: 1 → 2(抛异常 "retry me") → onError
          ↓ retry(2) 触发第2次重试
③ 第2次: 1 → 2(抛异常 "retry me") → onError
          ↓ 重试次数用完
最终: onError("retry me") ← 错误传播到下游
```

**重要特征**：`retry(n)` 是**重新订阅整个流**，而不是从失败位置恢复。

```
输入序列: [1, 2, 3]
失败点: i == 2 时抛异常

retry(2) 输出:
  第1个1 (初始)
  第1个1 (第1次重试)
  第1个1 (第2次重试)
  onError("retry me")
```

> **⚠️ 重试陷阱**：如果上游有副作用（如计数器、数据库写入），`retry` 会重复执行它们。确保操作是**幂等**的。

**测试**：
```java
StepVerifier.create(playground.retryExample())
        .expectNext(1)   // 初始
        .expectNext(1)   // 第1次重试
        .expectNext(1)   // 第2次重试
        .expectErrorMessage("retry me")
        .verify();
```

**进阶重试策略**（`Retry` 构建器）：

```java
// 指数退避 + 最大重试次数
Flux.range(1, 3)
    .map(i -> { if (i == 2) throw new RuntimeException(); return i; })
    .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
        .maxBackoff(Duration.ofSeconds(5))
        .jitter(0.5)
        .filter(ex -> ex instanceof TransientException));

// 基于异常的判断式重试
.retryWhen(Retry.max(3).filter(ex -> ex instanceof TimeoutException));

// 无限重试（谨慎使用）
.retryWhen(Retry.indefinitely());
```

---

### 4. `timeout` — 超时降级

```java
public Flux<Long> timeoutExample() {
    return Flux.interval(Duration.ofSeconds(10))
            .take(1)
            .timeout(Duration.ofMillis(100))
            .onErrorResume(e -> Flux.just(-1L));
}
```

| 特性 | 说明 |
|------|------|
| 场景 | 下游服务响应太慢，需要快速失败 |
| 效果 | 超时 → 发射 `TimeoutException` → 可以被 `onErrorResume`/`onErrorReturn` 捕获 |
| 链式风格 | 通常 `timeout()` + `onErrorResume()` 搭配使用 |

**执行流程**：

```
Flux.interval(10s) ──→ 等待 10s 才发射
                         ↓
                .timeout(100ms) ← 100ms 内没等到元素
                         ↓
                抛 TimeoutException
                         ↓
                .onErrorResume(e → Flux.just(-1L))
                         ↓
                -1 ──→ onComplete ✓
```

> **关键理解**：`timeout()` 本身不是错误处理操作符——它发射一个错误。**真正的降级**由后续的 `onErrorReturn` / `onErrorResume` 完成。

**测试**：
```java
StepVerifier.create(playground.timeoutExample())
        .expectNext(-1L)
        .verifyComplete();
```

**进阶用法**：不同阶段设置不同超时 + 备用源：

```java
// 优先查缓存（10ms），缓存未命中查数据库（200ms）
Mono.fromCallable(() -> cache.get(id))
    .timeout(Duration.ofMillis(10))
    .onErrorResume(e ->
        Mono.fromCallable(() -> db.query(id))
            .timeout(Duration.ofMillis(200))
            .onErrorReturn(fallback)
    );
```

---

## 二、操作符对比速查

| 操作符 | 行为 | 执行顺序 | 测试断言 |
|--------|------|----------|----------|
| `onErrorReturn` | 错误时返回单个默认值 | 同步 | `expectNext → verifyComplete` |
| `onErrorResume` | 错误时切换到备用 Publisher | 取决于备用流 | `expectNext → verifyComplete` |
| `retry(n)` | 重新订阅整个流 n 次 | 每次重新订阅 | `expectErrorMessage`（重试用完仍失败） |
| `timeout` | 超时时发射 TimeoutException | 异步等待 | 配合 `onErrorReturn/onErrorResume` 降级 |

### 选型决策

```
需要处理错误吗？
 ├─ 只要一个静态默认值 → onErrorReturn
 ├─ 需要动态备选值/流 → onErrorResume
 ├─ 愿意花时间重试 → retry
 │   └─ 需要指数退避 → retryWhen(Retry.backoff(...))
 └─ 需要在 X 时间内没结果就降级 → timeout + onErrorReturn/onErrorResume
```

---

## 三、错误处理链式组合

实际应用中往往多种策略叠加：

### 模式 1：超时 + 降级

```java
fetchRemote(id)
    .timeout(Duration.ofMillis(100))
    .onErrorResume(e -> fetchCache(id))    // 网络超时走缓存
    .onErrorReturn(fallback);              // 缓存也失败 -> 兜底
```

### 模式 2：重试 + 降级

```java
Flux.range(1, 10)
    .flatMap(i -> processItem(i)
        .retryWhen(Retry.backoff(2, Duration.ofMillis(50)))
        .onErrorReturn(-1))               // 重试用完仍失败 -> 跳过
    .filter(i -> i != -1);                // 剔除失败项
```

### 模式 3：全局降级（`switchIfEmpty`）

虽然不是错误处理，但 `switchIfEmpty` 是常用的"空降级"模式：

```java
Mono.fromCallable(() -> cache.get(id))
    .switchIfEmpty(Mono.fromCallable(() -> db.query(id)))
    .switchIfEmpty(Mono.just(defaultValue));
```

---

## 四、⚠️ 重要注意事项

### 1. 响应式 vs 传统 try-catch

| 维度 | try-catch | Reactor 操作符 |
|------|-----------|---------------|
| 作用域 | 同步代码块 | 整个响应式链 |
| 异常类型 | 所有异常 | 可条件过滤 |
| 链式传播 | 嵌套问题 | 扁平链式 |

```java
// ❌ 错误用法：在 map 内 try-catch，不通知响应式系统
.map(i -> {
    try { return riskyOp(i); }
    catch (Exception e) { return fallback; }
})

// ✅ 正确用法：让异常传播到操作符
.map(i -> riskyOp(i))
.onErrorReturn(fallback);
```

### 2. `retry` 的幂等性陷阱

```java
// ❌ 非幂等操作 — retry 会重复调用
AtomicInteger counter = new AtomicInteger(0);
Flux.range(1, 5)
    .map(i -> { counter.incrementAndGet(); return risky(i); })
    .retry(1);

// ✅ 幂等操作 — 重试不影响结果
Flux.range(1, 5)
    .map(i -> fetchReadOnlyData(i))
    .retry(1);
```

### 3. `timeout` 不能单独使用

```java
// ❌ timeout 不处理 → 超时错误直接终止流
Flux.interval(Duration.ofSeconds(10)).timeout(Duration.ofMillis(100));

// ✅ timeout + 降级
Flux.interval(Duration.ofSeconds(10))
    .timeout(Duration.ofMillis(100))
    .onErrorResume(e -> Flux.just(-1L));
```

---

## 五、完整测试对照

```java
class ErrorHandlingTest {
    ReactorPlayground playground = new ReactorPlayground();

    @Test @DisplayName("onErrorReturn — 错误时返回默认值")
    void fallbackExample() {
        StepVerifier.create(playground.fallbackExample())
            .expectNext("fallback")
            .verifyComplete();
    }

    @Test @DisplayName("onErrorResume — 错误时切换到备用流")
    void fallbackResumeExample() {
        StepVerifier.create(playground.fallbackResumeExample())
            .expectNext("resumed: failed")
            .verifyComplete();
    }

    @Test @DisplayName("retry — 重试 2 次后最终失败")
    void retryExample() {
        StepVerifier.create(playground.retryExample())
            .expectNext(1)   // 初始
            .expectNext(1)   // 第1次重试
            .expectNext(1)   // 第2次重试
            .expectErrorMessage("retry me")
            .verify();
    }

    @Test @DisplayName("timeout — 超时降级")
    void timeoutExample() {
        StepVerifier.create(playground.timeoutExample())
            .expectNext(-1L)
            .verifyComplete();
    }
}
```

---

## 关联参考

- 阶段概要笔记：[README.md](./README.md)
- 定时发射器：[01-flux-interval.md](./01-flux-interval.md)
- 操作符详解：[02-operators.md](./02-operators.md)
- 进阶：Spring 7.x `@Retryable` 声明式重试（阶段三）