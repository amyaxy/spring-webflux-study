# 阶段一：响应式编程基础 & Reactor 核心

> **项目模块**：`reactor-playground`
> **包路径**：`cloud.imuyi.reactor`
> **依赖**：Reactor Core + reactor-test（StepVerifier）+ Logback + JUnit 6

---

## 1. Reactive Streams 规范

Reactive Streams 是响应式编程的基石，定义了 4 个核心接口：

| 接口 | 角色 | 说明 |
|------|------|------|
| `Publisher<T>` | 发布者 | 可订阅的数据源，只有一个方法 `subscribe(Subscriber)` |
| `Subscriber<T>` | 订阅者 | 接收数据的消费者，4 个回调方法 |
| `Subscription` | 订阅契约 | 连接 Publisher 和 Subscriber，控制背压 `request(n)` |
| `Processor<T,R>` | 处理器 | 既是 Publisher 又是 Subscriber |

### 关键契约（背压）

```java
// Subscriber 通过 Subscription 向上游请求数据
public void onSubscribe(Subscription s) {
    s.request(1);  // 请求 1 个元素
}

public void onNext(T item) {
    // 处理元素
    subscription.request(1);  // 处理完再请求下一个
}
```

Project Reactor 对这套规范做了完整的实现，并提供了丰富的操作符 DSL。

---

## 2. Reactor 核心：Mono 与 Flux

| 类型 | 语义 | 类比 |
|------|------|------|
| `Mono<T>` | 0 或 1 个元素的异步序列 | 类似 `CompletableFuture<T>` |
| `Flux<T>` | 0 到 N 个元素的异步序列 | 类似 `Stream<T>` + 异步 + 背压 |

### 创建方式

```java
// ——— Flux ———
Flux<String> f1 = Flux.just("a", "b", "c");           // 从数组
Flux<Integer> f2 = Flux.fromIterable(List.of(1,2,3));  // 从集合
Flux<Long> f3 = Flux.range(1, 5).map(i -> (long)i);   // 范围
Flux<Long> f4 = Flux.interval(Duration.ofMillis(100)); // 定时
Flux<Integer> f5 = Flux.empty();                       // 空
Flux<Integer> f6 = Flux.error(new RuntimeException()); // 错误

// ——— Mono ———
Mono<String> m1 = Mono.just("hello");                  // 有值
Mono<String> m2 = Mono.empty();                        // 空
Mono<String> m3 = Mono.fromCallable(() -> "computed"); // 延迟计算
```

**冷流 vs 热流**：`Flux.just()`、`Flux.range()` 是冷流——每次 `subscribe()` 都会重新从头发射；`Sinks.many()` 可以创建热流。

---

## 3. 转换操作符

| 操作符 | 作用 | 顺序保证 |
|--------|------|----------|
| `map` | 同步 1:1 转换 | ✅ 保持顺序 |
| `flatMap` | 异步 1:N 展平 | ❌ 无序（按完成时间） |
| `concatMap` | 顺序 1:N 展平 | ✅ 保持顺序 |
| `filter` | 按条件过滤 | ✅ 保持顺序 |

```java
// map — 每个元素同步转换
Flux.just(1, 2, 3)
    .map(i -> "Number: " + i)               // → "Number: 1", "Number: 2", ...

// flatMap — 每个元素展开为异步流，结果可能乱序
Flux.just(1, 2, 3)
    .flatMap(i -> Mono.just("Item-" + i)
        .subscribeOn(Schedulers.parallel()))

// concatMap — 保持顺序的 flatMap
Flux.just(1, 2, 3)
    .concatMap(i -> Mono.just("Ordered-" + i)
        .delayElement(Duration.ofMillis(50)))

// filter — 保留满足条件的元素
Flux.range(1, 10).filter(i -> i % 2 == 0)   // → 2, 4, 6, 8, 10
```

### 图示

```
map:        1 ──→ "1"    2 ──→ "2"    3 ──→ "3"    （串行）
flatMap:    1 ──→ "1-1" ──→ "1-2"                 （并发，可能交错）
            2 ──→ "2-1"
concatMap:  1 ──→ "1-1" ──→ "1-2"                 （顺序保证）
            2 ──→ "2-1" ──→ (等1完成)
```

---

## 4. 合并操作符

| 操作符 | 行为 | 场景 |
|--------|------|------|
| `merge` | 交错合并多个流，发射顺序取决于完成时间 | 实时数据合并 |
| `concat` | 串行合并，一个流完成后才订阅下一个 | 有序数据拼接 |
| `zip` | 一对一配对合并，所有源都有数据才发射 | 聚合多个 API 结果 |

```java
// merge — 交错合并
Flux<String> fast = Flux.just("A1", "A2").delayElements(Duration.ofMillis(50));
Flux<String> slow = Flux.just("B1", "B2").delayElements(Duration.ofMillis(80));
Flux.merge(fast, slow);   // → A1, B1, A2, B2（取决于延迟）

// concat — 串行合并
Flux.concat(Flux.just("A1", "A2"), Flux.just("B1", "B2"));
                          // → A1, A2, B1, B2（严格顺序）

// zip — 配对合并
Flux.zip(Flux.just("Alice", "Bob"), Flux.just(95, 88),
    (name, score) -> name + ": " + score);
                          // → "Alice: 95", "Bob: 88"
```

---

## 5. 错误处理

响应式中的错误是**终止事件**——一旦错误发生，流会终止。通过以下操作符优雅处理：

| 操作符 | 行为 |
|--------|------|
| `onErrorReturn` | 错误时返回默认值 |
| `onErrorResume` | 错误时切换到备用流 |
| `retry` | 重新订阅源（重试） |
| `timeout` | 超时时发射错误，配合 `onErrorResume` 降级 |

```java
// 返回默认值
Mono.<String>error(new RuntimeException("oops"))
    .onErrorReturn("fallback");           // → "fallback"

// 切换备用流
Mono.<String>error(new RuntimeException("failed"))
    .onErrorResume(e -> Mono.just("resumed: " + e.getMessage()));
                                          // → "resumed: failed"

// 重试 — 重新订阅整个源
Flux.range(1, 3).map(i -> {
    if (i == 2) throw new RuntimeException("retry me");
    return i;
}).retry(2);  // 重试 2 次，总共最多执行 3 次

// 超时降级
Flux.interval(Duration.ofSeconds(10))
    .timeout(Duration.ofMillis(100))
    .onErrorResume(e -> Flux.just(-1L));  // → -1L
```

**Spring 7.x 增强**：可以使用 `@Retryable` 注解以声明式方式配置重试策略（含指数退避 + jitter），见阶段三。

---

## 6. 背压（Backpressure）

背压是响应式编程的核心优势——下游告诉上游"我处理不过来，慢点发"。

| 策略 | 行为 | 适用场景 |
|------|------|----------|
| `onBackpressureBuffer(n)` | 缓冲最多 n 个元素，超出抛异常 | 下游偶尔慢，能接受缓冲 |
| `onBackpressureDrop(callback)` | 丢弃来不及处理的数据 | 只关心最新数据 |
| `onBackpressureLatest()` | 保留最新元素，丢弃旧的 | 实时行情，只关注最新价 |

```java
// 缓冲 100 个
Flux.range(1, 1000).onBackpressureBuffer(100);

// 丢弃并记录日志
Flux.range(1, 1000)
    .onBackpressureDrop(i -> log.warn("Dropped: {}", i));
```

### 背压原理

```
Publisher ──→ onSubscribe(Subscription) ──→ Subscriber
                ↑  subscriber.request(n)  ↓
                           背压控制
```

Subscriber 通过 `Subscription.request(n)` 告诉 Publisher 自己处理能力。Publisher 按需发射，不多发。

---

## 7. 调度器（Schedulers）

调度器控制响应式操作在哪个线程池上执行：

| 调度器 | 线程池 | 用途 |
|--------|--------|------|
| `Schedulers.immediate()` | 当前线程 | 测试、简单场景 |
| `Schedulers.single()` | 单线程 | 共享的单线程池 |
| `Schedulers.parallel()` | 固定线程数（=CPU 核数） | CPU 密集型计算 |
| `Schedulers.boundedElastic()` | 可扩展线程池（有上限） | **阻塞 I/O 操作** |

```java
// 阻塞操作 → boundedElastic
Mono.fromCallable(() -> {
    Thread.sleep(100);  // 模拟阻塞 I/O
    return "processed";
}).subscribeOn(Schedulers.boundedElastic());

// 并行计算 → parallel
Flux.range(1, 10)
    .parallel(4)
    .runOn(Schedulers.parallel())
    .map(i -> i * 2)
    .sequential();
```

---

## 8. 实战模式

### SSE 事件流模拟

每 100ms 发射一个 SSE 格式事件，共 5 个：

```java
public Flux<String> sseStream() {
    return Flux.interval(Duration.ofMillis(100))
        .take(5)
        .map(i -> "data: event-" + i + "\n\n");
}
```

输出：
```
data: event-0
data: event-1
data: event-2
...
```

### 窗口批处理

每 500ms 窗口收集一批数据：

```java
Flux.interval(Duration.ofMillis(100))
    .take(20)
    .window(Duration.ofMillis(500))
    .flatMap(Flux::collectList);
```

---

## 9. 测试：StepVerifier

```java
StepVerifier.create(flux)
    .expectNext("a", "b", "c")    // 断言接下来的元素
    .verifyComplete();            // 验证流正常结束

// 虚拟时间 — 加速时间敏感的测试
StepVerifier.withVirtualTime(() -> flux)
    .thenAwait(Duration.ofSeconds(1))
    .expectNext(0L, 1L, 2L)
    .verifyComplete();

// 错误验证
StepVerifier.create(flux)
    .expectNext(1)
    .expectErrorMessage("retry me")
    .verify();
```

---

## 10. 对应代码结构

```
reactor-playground/src/
├── main/java/cloud/imuyi/reactor/
│   └── ReactorPlayground.java    # 7 组操作符练习
│       ├── 1. 基础创建           fluxFromArray, fluxFromList, monoJust, fluxInterval
│       ├── 2. 转换操作符         map, flatMap, concatMap, filter
│       ├── 3. 合并操作符         merge, concat, zip
│       ├── 4. 错误处理           onErrorReturn, onErrorResume, retry, timeout
│       ├── 5. 背压               onBackpressureBuffer, onBackpressureDrop
│       ├── 6. 调度器             blockingOperation, parallelProcessing
│       └── 7. 实战               sseStream, windowedProcessing
└── test/java/cloud/imuyi/reactor/
    └── ReactorPlaygroundTest.java # 21 个 StepVerifier 测试用例

测试：mvn clean test -pl reactor-playground
```

---

## 📝 关键总结

| 概念 | 一句话 |
|------|--------|
| Reactive Streams | Publisher-Subscriber 通过 Subscription 实现背压控制的异步流规范 |
| Mono | 0 或 1 个元素，类比异步的 Optional |
| Flux | 0 到 N 个元素，类比异步的 Stream |
| flatMap vs concatMap | flatMap 并发（可能乱序），concatMap 顺序 |
| 背压 | 下游通过 `request(n)` 控制上游发射速率 |
| 调度器 | 控制操作执行的线程池，阻塞 I/O 用 `boundedElastic` |
| StepVerifier | 响应式流的测试利器，支持虚拟时间加速 |