# Reactor 调度器（Schedulers）

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java` — 调度器示例（第 6 组）+ `flatMap` 中的 `subscribeOn`
> **测试定位**：`ReactorPlaygroundTest.java` — `blockingOperation` / `parallelProcessing`

---

## 一、为什么需要调度器？

Reactor 默认**所有操作都在调用 `subscribe()` 的线程上执行**（即当前线程），这在响应式流中会阻塞调用线程：

```java
// 默认行为：当前线程全部执行
Flux.range(1, 3)
    .map(i -> { Thread.sleep(100); return i; })  // 阻塞当前线程
    .subscribe();
```

调度器（`Scheduler`）的作用：**将操作移交给指定的线程池执行**，做到异步非阻塞。

---

## 二、四种内置调度器

| 调度器 | 线程模型 | 用途 | 类比 |
|--------|----------|------|------|
| `Schedulers.immediate()` | 当前线程，无切换 | 测试、简单场景 | `Runnable.run()` |
| `Schedulers.single()` | 单线程池（共享） | 单线程序列化操作 | `Executors.newSingleThreadExecutor()` |
| `Schedulers.parallel()` | 固定线程数 = `Runtime.availableProcessors()` | **CPU 密集型计算** | `ForkJoinPool` |
| `Schedulers.boundedElastic()` | 可扩展线程池（默认上限 10×CPU 核数） | **阻塞 I/O 操作** | `Executors.newCachedThreadPool()` |

### 快速选型

```
操作会阻塞线程吗（sleep、JDBC、文件读写）？
 ├─ 是 → boundedElastic    ← 阻塞操作专用
 └─ 否 →
      ├─ CPU 密集计算 → parallel
      ├─ 单线程序列化 → single
      └─ 不关心 → immediate（默认）
```

---

## 三、`subscribeOn` vs `publishOn`

这两个操作符控制线程切换，但作用位置不同：

| 操作符 | 作用位置 | 效果 |
|--------|----------|------|
| `subscribeOn` | **订阅起点**（源头） | 改变**上游 Publisher** 的执行线程——从 `subscribe()` 那一步开始 |
| `publishOn` | **链中任意位置** | 改变**下游操作符**的执行线程——从该操作符往后的所有操作 |

### 3.1 `subscribeOn` — 改变源头线程

```java
Mono.fromCallable(() -> {
        Thread.sleep(100);       // 阻塞操作
        return "processed: " + input;
    })
    .subscribeOn(Schedulers.boundedElastic())  // ← 这之后的"订阅"在 boundedElastic 执行
    .map(s -> s.toUpperCase());                // 也跑在 boundedElastic（因为源头在那）
```

**执行流程**：
```
调用线程:   subscribe() ──→ 订阅触发
                             ↓
boundedElastic 线程:         Mono.fromCallable → map → emit
```

> 💡 `subscribeOn` 放在链的**任何位置**效果都一样——它影响的是"订阅行为"（即整个链的起点）。

### 3.2 `publishOn` — 切换下游线程

```java
Flux.range(1, 10)
    .map(i -> i * 2)                           // parallel 线程
    .publishOn(Schedulers.boundedElastic())     // ← 从这里切换
    .map(i -> slowDatabaseOp(i))               // boundedElastic 线程
    .subscribe();
```

**执行流程**：
```
parallel 线程:    range → map(*2) ──→ publishOn 切换
                                       ↓
boundedElastic 线程:                 map(database) → emit
```

### 3.3 两者对比

| 操作符 | 影响上游 | 影响下游 | 可调用次数 | 典型场景 |
|--------|----------|----------|-----------|----------|
| `subscribeOn` | ✅ | ✅（源头决定了整条链） | 第 1 次有效 | 阻塞 I/O 操作移到后台 |
| `publishOn` | ❌ | ✅（从该点以后） | 多次有效 | 链中切换不同线程池 |

```java
// subscribeOn 多个只生效第一个（离源头最近的）
Flux.range(1, 5)
    .subscribeOn(Schedulers.boundedElastic())   // ✅ 生效
    .subscribeOn(Schedulers.parallel());        // ❌ 被忽略

// publishOn 可以多次切换
Flux.range(1, 5)
    .publishOn(Schedulers.boundedElastic())     // ✅ 切换
    .map(i -> dbOp(i))
    .publishOn(Schedulers.parallel())           // ✅ 再切换
    .map(i -> compute(i));
```

---

## 四、`parallel` — 并行处理

### 4.1 基本用法

```java
public Flux<Integer> parallelProcessing(Flux<Integer> source) {
    return source
            .parallel(4)                        // ① 转为 ParallelFlux，分 4 个 rail
            .runOn(Schedulers.parallel())        // ② 指定每个 rail 跑的线程
            .map(i -> i * 2)                     // ③ 每个 rail 独立执行
            .sequential();                       // ④ 合并回普通 Flux
}
```

**执行流程**：
```
输入: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
       │
       ▼ parallel(4) — 分成 4 个 rail
rail-0: [1, 5, 9]  ──→ parallel 线程0 ──→ map(*2) = [2, 10, 18]
rail-1: [2, 6, 10] ──→ parallel 线程1 ──→ map(*2) = [4, 12, 20]
rail-2: [3, 7]     ──→ parallel 线程2 ──→ map(*2) = [6, 14]
rail-3: [4, 8]     ──→ parallel 线程3 ──→ map(*2) = [8, 16]
       │
       ▼ sequential()
输出: [2, 4, 6, 8, 10, 12, 14, 16, 18, 20]（顺序不保证）
```

| 步骤 | 方法 | 说明 |
|------|------|------|
| ① 分组 | `.parallel(n)` | 将 Flux 分为 n 个 rail，数量通常 ≤ CPU 核数 |
| ② 绑定线程 | `.runOn(Scheduler)` | 每个 rail 绑定到指定调度器的一个线程 |
| ③ 并行操作 | `.map/filter/...` | 每个 rail 独立执行（并行的关键） |
| ④ 合并 | `.sequential()` | 将 ParallelFlux 合并回普通 Flux |

### 4.2 与 `flatMap` 的区别

| 特性 | `flatMap` | `parallel().runOn().sequential()` |
|------|-----------|----------------------------------|
| 并发模型 | 每个元素 ⟶ 展开为子 Publisher | 元素分配到 rail 并行处理 |
| 内部机制 | 操作符层面的并发 | 线程池层面的并行 |
| 适用场景 | I/O 密集型（调用外部 API） | CPU 密集型（计算、转换） |
| 排序 | 无序（默认）或有序（`concatMap`） | rail 内有序，整体无序 |
| 控制粒度 | 元素级别 | rail 级别 |

```java
// flatMap — I/O 密集型：每个请求独立并发
Flux.range(1, 100)
    .flatMap(i -> fetchFromApi(i), 16);     // 最多 16 个并发请求

// parallel — CPU 密集型：数据分片并行计算
Flux.range(1, 100)
    .parallel(8)
    .runOn(Schedulers.parallel())
    .map(i -> expensiveCalculation(i))
    .sequential();
```

---

## 五、实战案例

### 5.1 阻塞 I/O → `boundedElastic`

```java
public Mono<String> blockingOperation(String input) {
    return Mono.fromCallable(() -> {
        Thread.sleep(100);                    // 模拟阻塞 I/O（DB、文件、网络）
        return "processed: " + input;
    }).subscribeOn(Schedulers.boundedElastic());
}
```

> **为什么阻塞 I/O 必须用 `boundedElastic`？**
> - `parallel` 的线程数 = CPU 核数，阻塞一个就浪费 CPU
> - `boundedElastic` 的线程数按需增长但有上限，阻塞了也不影响 CPU 密集型线程
> - 每个阻塞线程在被阻塞时可以让出 CPU 给其他线程

### 5.2 CPU 计算 → `parallel`

```java
// 大量数字的数学运算，每个计算独立
Flux.range(1, 10000)
    .parallel(Runtime.getRuntime().availableProcessors())
    .runOn(Schedulers.parallel())
    .map(i -> BigInteger.valueOf(i).isProbablePrime(100))  // CPU 密集
    .sequential()
    .collectList();
```

### 5.3 混合模式

```java
// 场景：先计算再写库
Flux.range(1, 100)
    .parallel(8)
    .runOn(Schedulers.parallel())          // ① CPU 密集：并行计算
    .map(i -> calculateScore(i))
    .sequential()
    .flatMap(score ->
        Mono.fromCallable(() -> saveToDb(score))  // ② I/O 阻塞：boundedElastic
            .subscribeOn(Schedulers.boundedElastic()),
        4                                        // ③ 限制写库并发 4
    );
```

### 5.4 `flatMap` + `subscribeOn` 的异步并发模式

```java
// 来自 flatMapExample — 每个元素异步执行
source.flatMap(i ->
    Mono.just("Item-" + i)
        .subscribeOn(Schedulers.parallel())  // 每个 Mono 跑在 parallel 线程
);
```

> 注意：这种场景 `subscribeOn` 作用于每个 `Mono.just`，使得 n 个元素在 parallel 线程池中并发执行。适合轻量计算，而不只是 I/O。

---

## 六、线程模型可视化

```
操作符链：
      Flux.range(1, 5) ──→ map(*2) ──→ filter(>5) ──→ subscribe(System.out::println)

默认（immediate）：
      main: [range─→map─→filter─→println] 全部在 main 线程

subscribeOn(boundedElastic)：
      main: subscribe()
      boundedElastic-1: [range─→map─→filter─→println]

publishOn(parallel)
  → 并在 subscribeOn(boundedElastic)：
      main: subscribe()
      boundedElastic-1: [range]
                         ↓ publishOn(parallel)
      parallel-1:        [map─→filter─→println]
```

---

## 七、⚠️ 常见陷阱

### 1. 在 `parallel` 上做阻塞操作

```java
// ❌ 错误：阻塞 CPU 线程
Flux.range(1, 100)
    .parallel(4)
    .runOn(Schedulers.parallel())
    .map(i -> { Thread.sleep(1000); return i; })   // 阻塞 4 个 CPU 线程！
    .sequential();

// ✅ 正确：在 parallel 中内嵌 boundedElastic
.parallel(4)
.runOn(Schedulers.parallel())
.map(i -> Mono.fromCallable(() -> blockingOp(i))
              .subscribeOn(Schedulers.boundedElastic())
              .block())                             // 每个 rail 内用 block 等结果
.sequential();
```

### 2. `subscribeOn` 第 2 次无效

```java
Flux.range(1, 5)
    .subscribeOn(Schedulers.boundedElastic())
    .subscribeOn(Schedulers.parallel());   // ❌ 被忽略
```

### 3. `parallel` 后别忘了 `sequential`

```java
Flux.range(1, 5)
    .parallel(4)
    .runOn(Schedulers.parallel())
    .map(i -> i * 2)
    // ❌ 没 sequential()，返回的是 ParallelFlux，有些操作符不兼容
```

### 4. `newSingle` vs `single()` — 共享单线程 vs 独立单线程

```java
Schedulers.single();            // 应用程序内共享一个单线程
Schedulers.newSingle("my-thread");  // 创建一个独立的单线程
```

---

## 八、完整测试对照

```java
class SchedulersTest {
    ReactorPlayground playground = new ReactorPlayground();

    @Test @DisplayName("subscribeOn — 在指定调度器上执行阻塞操作")
    void blockingOperation() {
        StepVerifier.create(playground.blockingOperation("test"))
            .expectNext("processed: test")
            .verifyComplete();
    }

    @Test @DisplayName("parallel — 并行处理")
    void parallelProcessing() {
        Flux<Integer> result = playground.parallelProcessing(Flux.range(1, 10));
        StepVerifier.create(result)
            .expectNextCount(10)    // 10 个值，顺序不保证
            .verifyComplete();
    }

    @Test @DisplayName("subscribeOn vs publishOn 验证")
    void threadSwitching() {
        String mainThread = Thread.currentThread().getName();

        // subscribeOn 效果：链在 boundedElastic 执行
        List<String> threads = new ArrayList<>();
        Flux.range(1, 3)
            .subscribeOn(Schedulers.boundedElastic())
            .doOnNext(i -> threads.add(Thread.currentThread().getName()))
            .blockLast();

        assertThat(threads).allMatch(t -> t.contains("boundedElastic"));
    }
}
```

---

## 九、调度器速查表

```
需要异步执行？
 ├─ 操作会阻塞线程（sleep、JDBC、文件） → boundedElastic
 ├─ CPU 密集型计算 → parallel
 ├─ 单线程序列化 → single
 └─ 不关心/测试 → immediate

控制位置？
 ├─ 改变整个链的线程 → subscribeOn（放在链头或链尾效果一样）
 ├─ 改变下游的线程 → publishOn（可多次调用）
 └─ 同时用来回切换 → subscribeOn(poolA).publishOn(poolB)

并行处理数据？
 ├─ CPU 密集 → parallel(n).runOn(Schedulers.parallel()).sequential()
 └─ I/O 密集 → flatMap(i → asyncOp(i), concurrency)
```

| 场景 | 推荐模式 |
|------|----------|
| 阻塞 I/O（DB、文件、网络） | `subscribeOn(Schedulers.boundedElastic())` |
| CPU 密集计算 | `parallel(n).runOn(Schedulers.parallel())` |
| 混合（计算 + I/O） | `parallel → sequential → flatMap(boundedElastic)` |
| 异步并发 I/O | `flatMap(i → Mono.fromCallable().subscribeOn(boundedElastic))` |

---

## 关联参考

- 阶段概要笔记：[README.md](./README.md)
- 操作符详解（flatMap 调度上下文）：[02-operators.md](./02-operators.md)
- 进阶：阶段二 WebFlux 中 Netty 的事件循环与调度器