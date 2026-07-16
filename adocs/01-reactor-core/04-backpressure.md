# Reactor 背压（Backpressure）

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java` — 背压示例（第 5 组）
> **测试定位**：`ReactorPlaygroundTest.java` — `bufferBackpressure` / `dropBackpressure`

---

## 一、什么是背压？

**背压**是响应式编程区别于传统拉取模型的核心特性——下游告诉上游"我处理不过来，慢点发"。

```
传统拉取模型（Iterable）:
  消费者 ──→ 拉取 → 生产者 ──→ 阻塞等待

响应式推送模型（Reactive Streams）:
  生产者 ──→ 推送 → 消费者 ──→ request(n) 控制速率
                                 ↑
                              背压信号
```

Reactive Streams 规范通过 `Subscription.request(n)` 实现背压：

```java
@Override
public void onSubscribe(Subscription s) {
    this.subscription = s;
    s.request(1);  // 告诉上游：我需要 1 个
}

@Override
public void onNext(T item) {
    handle(item);              // 处理当前元素
    subscription.request(1);   // 处理完再要下一个
}
```

---

## 二、三种背压策略

Reactor 提供了三种开箱即用的背压策略，对应代码中的三个方法：

| 策略 | 操作符 | 行为 | 适用场景 |
|------|--------|------|----------|
| **缓冲** | `onBackpressureBuffer(max)` | 在内存中暂存多余元素 | 下游偶尔慢，能接受缓冲 |
| **丢弃** | `onBackpressureDrop(callback)` | 丢弃来不及处理的元素 | 只关心最新数据 |
| **最新** | `onBackpressureLatest()` | 保留最新元素，丢弃旧的 | 实时行情，只关注最新价 |

### 1. `onBackpressureBuffer` — 缓冲策略

```java
/** onBackpressureBuffer: 缓冲背压 */
public Flux<Integer> bufferBackpressure() {
    return Flux.range(1, 1000)
            .onBackpressureBuffer(100);
}
```

| 参数 | 值 | 含义 |
|------|----|------|
| 缓冲容量 | `100` | 最多缓存 100 个未消费的元素 |
| 超出行为 | 抛 `OverflowException` | 缓冲池满了，丢不下去了 |

**执行流程**：

```
上游（快速）: ──→ 1 → 2 → 3 → ... → 100 → 101 → ...
                      ↓                    ↓
                  缓冲池 [1..100]        缓冲池满了！
                      ↓                    ↓
下游（慢速）: ──→ 消费完毕 ← request(1)   抛异常
```

**测试**：
```java
// onBackpressureBuffer 对上界检查
StepVerifier.create(playground.bufferBackpressure().limitRate(50))
        .expectNextCount(1000)
        .verifyComplete();
```

> 💡 `onBackpressureBuffer()` 无参版本使用**无限缓冲**，要小心 OOM；**始终指定一个上限**。

---

### 2. `onBackpressureDrop` — 丢弃策略

```java
/** onBackpressureDrop: 丢弃背压 */
public Flux<Integer> dropBackpressure() {
    return Flux.range(1, 1000)
            .onBackpressureDrop(i -> log.warn("Dropped: {}", i));
}
```

| 特性 | 说明 |
|------|------|
| 行为 | 下游 request(n) 没到位时，上游新元素直接丢弃 |
| 回调 | `onBackpressureDrop(i -> ...)` 通知你丢了哪个元素 |
| 风险 | **数据丢失** — 需要业务上能容忍丢失 |

**执行流程**：

```
上游（快速）: ──→ 1 → 2 → 3 → 4 → 5 → 6 → ...
下游 request(1):  ↑         ↑         ↑
                 消费了 1  消费了 3  消费了 5
                        2,4,6... 被丢弃
```

**测试**：
```java
// 丢弃策略 — 下游限速 50 → 上游 1000 个元素，大量被丢弃
StepVerifier.create(playground.dropBackpressure().limitRate(50))
        .expectNextCount(50)    // 只消费了 50 个
        .verifyComplete();
```

> 💡 `limitRate(50)` 将下游的 request 上限设为 50，模拟慢消费者。

---

### 3. `onBackpressureLatest` — 最新策略

```java
// 代码中无独立方法，但用法如下
Flux.range(1, 100)
        .onBackpressureLatest();
```

| 特性 | 说明 |
|------|------|
| 行为 | 只保留最新元素，旧的丢弃 |
| 缓冲大小 | 1 个（最新值）+ 内部 volatile 保证可见性 |
| 场景 | **实时行情**、**最新状态**、**进度更新** |

**执行流程**：

```
上游(快速): ──→ 1 → 2 → 3 → 4 → 5 → 6 → 7 → ...
下游(慢速):      ↑ 消费了1    ↑ 消费了3    ↑ 消费了5
                   最新：4      最新：6      最新：8...
                  （2被丢弃）  （4被丢弃）  （6被丢弃）
```

**典型场景**：
```java
// 股票实时行情 — 下游只关心最新价
stockPriceStream()
    .onBackpressureLatest()
    .subscribe(price -> updateDisplay(price));

// 定时状态刷新
Flux.interval(Duration.ofMillis(10))
    .map(tick -> fetchStatus())
    .onBackpressureLatest()
    .subscribe(status -> updateDashboard(status));
```

---

## 三、背压原理深入

### 3.1 背压的底层机制

Reactive Streams 规范定义了一个**链条**上的背压传播：

```
Publisher ──→ Subscriber
                ↑
          Subscription
          request(n) ←── 背压信号
              │
          上游 Publisher ──→ 上游 Subscriber
                              ↑
                        上游 Subscription
                        request(n)
```

关键的规则：
1. **request(n) 是累积的**：`request(1) + request(2) = request(3)`，不是覆盖
2. **Long.MAX_VALUE 表示"无界"**：不需要背压（等价于 `Iterable`）
3. **cancel() 终止**：下游调用 `cancel()` 表示不再需要任何数据

### 3.2 Reactor 默认背压

Flux/Mono 的默认 request 上限是 `Long.MAX_VALUE`（无界）。通过 `limitRate(n)` 手动控制：

```java
Flux.range(1, 1000)
    .limitRate(50)  // 内部初始 request(50)，消费完再 request(75%) 分两批
    .subscribe();
```

`limitRate` 的 smart-batching 算法：
1. 初始 `request(50)` → 上游发射 50 个
2. 消费到 **75%（约 38 个）** 时，自动 `request(50)` 重新填满
3. 这样上游不会等下游完全消费完才发射下一批

### 3.3 背压支持的 Publisher

| Publisher | 是否响应背压 | 说明 |
|-----------|-------------|------|
| `Flux.just()` | ✅ | 由 subscriber.request 控制 |
| `Flux.fromIterable()` | ✅ | 按需拉取 |
| `Flux.range()` | ✅ | 按需生成 |
| `Flux.interval()` | ✅ | 内部定时器根据 request 发射 |
| `Mono.just()` | ✅ | 单个元素，不涉及 |
| `Flux.never()` | N/A | 永远不会发射 |
| `Sinks.Many` | ✅ | 可配置背压策略 |
| `Flux.generate()` | ✅ | 程序化控制 |

---

## 四、实战模式

### 模式 1：限流消费

```java
// 大数据批处理 — 每批 100 个
Flux.range(1, 10000)
    .limitRate(100)
    .buffer(100)
    .subscribe(batch -> {
        processBatch(batch);              // 处理一批
        // limitRate 自动 request 下一批
    });
```

### 模式 2：实时监控 + 最新状态

```java
// 每 10ms 发一个状态更新，界面每 200ms 刷新一次
Flux.interval(Duration.ofMillis(10))
    .map(tick -> collectMetrics())
    .onBackpressureLatest()
    .sample(Duration.ofMillis(200))       // 每 200ms 采样一个
    .subscribe(metrics -> renderChart(metrics));
```

### 模式 3：观察背压行为

```java
// 带日志的背压观察
Flux.range(1, 100)
    .doOnRequest(n -> log.info("上游收到请求: {}", n))
    .doOnNext(i -> log.info("上游发射: {}", i))
    .limitRate(5)
    .doOnRequest(n -> log.info("下游请求: {}", n))
    .doOnNext(i -> log.info("下游消费: {} (延迟 50ms)", i))
    .delayElements(Duration.ofMillis(50))
    .blockLast();
```

输出样本：
```
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游收到请求: 5
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 1
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 1 (延迟 50ms)
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 2
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 3
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 4
[main] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 5
[parallel-1] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-1] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 2 (延迟 50ms)
[parallel-2] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-2] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 3 (延迟 50ms)
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 4 (延迟 50ms)
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 上游收到请求: 4
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 6
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 7
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 8
[parallel-3] INFO  c.i.reactor.ReactorPlaygroundTest - 上游发射: 9
[parallel-4] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-4] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 5 (延迟 50ms)
[parallel-5] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-5] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 6 (延迟 50ms)
[parallel-6] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
[parallel-6] INFO  c.i.reactor.ReactorPlaygroundTest - 下游消费: 7 (延迟 50ms)
[parallel-7] INFO  c.i.reactor.ReactorPlaygroundTest - 下游请求: 1
...
```

---

## 五、⚠️ 常见陷阱

### 1. 无限缓冲 = OOM

```java
// ❌ 危险！无限缓冲可能撑爆内存
Flux.interval(Duration.ofMillis(1))
    .onBackpressureBuffer()                         // 无上限
    .delayElements(Duration.ofSeconds(1))
    .subscribe();

// ✅ 安全做法：设上限 + 定义溢出策略
.onBackpressureBuffer(1000, BufferOverflowStrategy.DROP_OLDEST)
```

### 2. `publishOn`/`subscribeOn` 切换线程会重置 request

```java
// ❌ limitRate 在切换线程后失效
Flux.range(1, 1000)
    .limitRate(50)
    .publishOn(Schedulers.boundedElastic())   // 切换线程
    .subscribe();

// ✅ 切换后重新限速
Flux.range(1, 1000)
    .publishOn(Schedulers.boundedElastic())
    .limitRate(50)
    .subscribe();
```

### 3. hot publisher 的背压

```java
// Sinks.Many 是一个 hot publisher，背压行为不同
Sinks.many().multicast()
    .onBackpressureBuffer(100);              // 每个 Subscriber 独立缓冲

Sinks.many().unicast()
    .onBackpressureDrop();                    // 单播丢弃
```

---

## 六、策略选择速查

```
下游偶尔比上游慢
 ├─ 能接受多花内存缓冲 → onBackpressureBuffer(上限)
 ├─ 数据丢了也无所谓 → onBackpressureDrop(回调)
 ├─ 只关注最新值 → onBackpressureLatest()
 └─ 数据极其重要，不能丢不能乱
     └─ 调整架构：分区 / 消息队列 / 异步解耦
```

| 策略 | 内存开销 | 数据完整性 | 实时性 | 适用场景 |
|------|---------|-----------|--------|----------|
| Buffer | 高 | ✅ 完整 | 延迟高 | 批量导出 |
| Drop | 低 | ❌ 丢失 | 低 | 日志采样 |
| Latest | 极低 | ❌ 中间丢失 | ✅ 最实时 | 行情、监控 |

---

## 七、完整测试对照

```java
class BackpressureTest {
    ReactorPlayground playground = new ReactorPlayground();

    @Test @DisplayName("onBackpressureBuffer — 缓冲背压，限速消费")
    void bufferBackpressure() {
        StepVerifier.create(playground.bufferBackpressure().limitRate(50))
            .expectNextCount(1000)
            .verifyComplete();
    }

    @Test @DisplayName("onBackpressureDrop — 丢弃溢出的元素")
    void dropBackpressure() {
        StepVerifier.create(playground.dropBackpressure().limitRate(50))
            .expectNextCount(50)   // 只消费了 50 个
            .verifyComplete();
    }
}
```

---

## 关联参考

- 阶段概要笔记：[README.md](./README.md)
- 定时发射器：[01-flux-interval.md](./01-flux-interval.md)
- 操作符详解：[02-operators.md](./02-operators.md)
- 错误处理：[03-error-handling.md](./03-error-handling.md)
- 进阶：阶段二 SSE 实时行情中的背压控制