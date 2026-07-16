# Reactor 操作符：转换与合并

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java` — 转换操作符、合并操作符
> **测试定位**：`ReactorPlaygroundTest.java` — `mapExample` / `flatMapExample` / `concatMapExample` / `filterExample` / `mergeExample` / `concatExample` / `zipExample`

---

## 一、转换操作符

### 1. `map` — 同步 1:1 转换

```java
public Flux<String> mapExample(Flux<Integer> source) {
    return source.map(i -> "Number: " + i);
}
```

| 特性 | 说明 |
|------|------|
| 映射比例 | 1 个输入 → 恰好 1 个输出 |
| 执行方式 | **同步** — 当前线程直接执行 Lambda |
| 顺序保证 | ✅ 严格保持源顺序 |
| 类比 | `Stream.map()`、`Optional.map()` |

**执行流程**：每个元素到达后立即执行 Lambda，然后发射结果，下一个元素才继续。

```java
// 测试
StepVerifier.create(playground.mapExample(Flux.just(1, 2, 3)))
        .expectNext("Number: 1", "Number: 2", "Number: 3")
        .verifyComplete();
```

---

### 2. `flatMap` — 异步展平（无序）

```java
public Flux<String> flatMapExample(Flux<Integer> source) {
    return source.flatMap(i ->
            Mono.just("Item-" + i)
                    .subscribeOn(Schedulers.parallel())
    );
}
```

| 特性 | 说明 |
|------|------|
| 映射比例 | 1 个输入 → 0 到 N 个输出（展平） |
| 执行方式 | **异步并发** — 每个元素可跑在不同线程 |
| 顺序保证 | ❌ **无序** — 发射顺序取决于各异步任务完成时间 |
| 类比 | `CompletableFuture.thenCompose()` + 多线程 |

**执行流程**：

```
输入:  1 ─────────→ Item-1 ─────────────→ (完成 t=20ms, 第3个发射)
       2 ──→ Item-2 ──→ (完成 t=5ms, 第1个发射)
       3 ──→ Item-3 ───→ (完成 t=10ms, 第2个发射)

输出顺序: Item-2, Item-3, Item-1  (取决于谁先完成)
```

> 💡 `flatMap` 的核心价值：并发处理多个数据项，适合 I/O 密集型任务。

**关键参数**：`concurrency`（默认 256）控制最大并发数：
```java
source.flatMap(i -> doAsync(i), 4)  // 最多 4 个并发
```

**测试**：
```java
StepVerifier.create(playground.flatMapExample(Flux.just(1, 2, 3)))
        .expectNextCount(3)               // 有 3 个值，但顺序不确定
        .verifyComplete();
```

---

### 3. `concatMap` — 保持顺序的展平

```java
public Flux<String> concatMapExample(Flux<Integer> source) {
    return source.concatMap(i ->
            Mono.just("Ordered-" + i)
                    .delayElement(Duration.ofMillis(50))
    );
}
```

| 特性 | 说明 |
|------|------|
| 映射比例 | 1 个输入 → 0 到 N 个输出（展平） |
| 执行方式 | **顺序异步** — 一个完成后才订阅下一个 |
| 顺序保证 | ✅ **严格保持源顺序** |
| 类比 | `flatMap` 的顺序版，类似 `Promise.all()` 但串行 |

**执行流程**：

```
输入:  1 ──→ Ordered-1 (等待 50ms, 完成)
                      2 ──→ Ordered-2 (等待 50ms, 完成)
                                    3 ──→ Ordered-3 (等待 50ms, 完成)

输出顺序: Ordered-1, Ordered-2, Ordered-3 (严格有序)
```

> **什么时候用 `concatMap` 而不是 `flatMap`？**
> - 需要按顺序处理数据（如消息队列、事件回放）
> - 下游有顺序依赖（如写入日志文件）
> - 并发处理可能导致竞态条件

**测试**：
```java
StepVerifier.create(playground.concatMapExample(Flux.just(1, 2, 3)))
        .expectNext("Ordered-1", "Ordered-2", "Ordered-3")
        .verifyComplete();
```

---

### 4. `filter` — 条件过滤

```java
public Flux<Integer> filterExample(Flux<Integer> source) {
    return source.filter(i -> i % 2 == 0);
}
```

| 特性 | 说明 |
|------|------|
| 操作类型 | 筛选 — 保留满足条件的元素 |
| 执行方式 | 同步 |
| 顺序保证 | ✅ 保持顺序 |

```java
// 输入: 1, 2, 3, 4, 5
// 保留: [2, 4]

StepVerifier.create(playground.filterExample(Flux.range(1, 5)))
        .expectNext(2, 4)
        .verifyComplete();
```

---

### 四者对比总览

| 操作符 | 映射比 | 执行方式 | 顺序 | 典型场景 |
|--------|--------|----------|------|----------|
| `map` | 1:1 | 同步 | ✅ | 类型转换、值映射、DTO 转换 |
| `flatMap` | 1:N | **异步并发** | ❌ | 并发 API 调用、并行处理 |
| `concatMap` | 1:N | 顺序异步 | ✅ | 消息队列、顺序文件写入 |
| `filter` | 筛选 | 同步 | ✅ | 条件过滤、null 剔除 |

**选型决策**：

```
需要将元素转为新值？
 ├─ 同步且 1:1 → map
 └─ 异步且 1:N →
      ├─ 不需要有序 → flatMap
      └─ 必须有序 → concatMap
需要剔除元素？
 └─ filter
```

---

## 二、合并操作符

### 1. `merge` — 交错合并（无序）

```java
public Flux<String> mergeExample(Flux<String> a, Flux<String> b) {
    return Flux.merge(a, b);
}
```

| 特性 | 说明 |
|------|------|
| 订阅方式 | **立即订阅所有源** |
| 发射顺序 | 交错 — 哪个源先发射就出哪个 |
| 错误行为 | 任一源出错 → 整体错误 |

**执行流程**：

```
源A: --A1--------A2----------A3------→
源B: ------B1-------B2-------B3------→
输出: --A1---B1---A2---B2---A3---B3---→
```

```java
Flux<String> fast = Flux.just("A1", "A2").delayElements(Duration.ofMillis(50));
Flux<String> slow = Flux.just("B1", "B2").delayElements(Duration.ofMillis(80));

// 输出可能为: A1, B1, A2, B2（取决于实际延迟）
```

---

### 2. `concat` — 串行合并（有序）

```java
public Flux<String> concatExample(Flux<String> a, Flux<String> b) {
    return Flux.concat(a, b);
}
```

| 特性 | 说明 |
|------|------|
| 订阅方式 | **顺序订阅** — 一个完成后才订阅下一个 |
| 发射顺序 | 严格有序 — 源A全部发完才发源B |
| 错误行为 | 当前源出错 → 整体错误，后续不订阅 |

**执行流程**：

```
源A: --A1--A2--A3--A4--A5--| (完成)
                           源B: --B1--B2--B3--| (完成)
输出: --A1--A2--A3--A4--A5--B1--B2--B3--|
```

```java
StepVerifier.create(
    playground.concatExample(
        Flux.just("A1", "A2"),
        Flux.just("B1", "B2")
    ))
    .expectNext("A1", "A2", "B1", "B2")
    .verifyComplete();
```

---

### 3. `zip` — 配对合并

```java
public Flux<String> zipExample(Flux<String> names, Flux<Integer> scores) {
    return Flux.zip(names, scores, (name, score) -> name + ": " + score);
}
```

| 特性 | 说明 |
|------|------|
| 配对方式 | **一对一拉链式** — 第 N 个元素配对 |
| 发射时机 | **所有源都有第 N 个元素**才发射第 N 个 |
| 截断行为 | 以**最短的源**为准 — 任一源结束就整体结束 |

**执行流程**：

```
names:  Alice─Bob──Carol─Dave─→
scores: 95────88────73─────────→
zip:    [Alice,95]─[Bob,88]─[Carol,73]─| (Dave 无配对，被截断)
```

```java
StepVerifier.create(
    playground.zipExample(
        Flux.just("Alice", "Bob", "Carol"),
        Flux.just(95, 88, 73)
    ))
    .expectNext("Alice: 95", "Bob: 88", "Carol: 73")
    .verifyComplete();
```

**多源 zip**：支持 2~8 个源的配对：
```java
Flux.zip(source1, source2, source3, (a, b, c) -> a + b + c);
```

---

### 合并操作符对比

| 操作符 | 订阅策略 | 顺序 | 错误行为 | 典型场景 |
|--------|----------|------|----------|----------|
| `merge` | 同时订阅 | 交错（无序） | 任一源出错报错 | 实时数据合并、日志聚合 |
| `concat` | 串行订阅 | 严格有序 | 当前源出错报错 | 缓存后查数据库、分页拼接 |
| `zip` | 等待配对 | 按索引对应 | 任一源出错报错 | 聚合多个 API 响应、等待全部就绪 |

---

## 三、组合实战

### 模式 1：分页请求 + 合并

```java
// 并发请求第1页和第2页，结果合并去重
Flux.merge(
    fetchPage(1),
    fetchPage(2)
)
.filter(item -> item.isValid())
.distinct()
.collectList();
```

### 模式 2：并行处理但有序输出

```java
// 先并发聚合数据，再按顺序输出
List<Mono<Data>> tasks = ids.stream()
    .map(id -> fetchUser(id))
    .toList();

Flux.merge(tasks)                        // 并发请求
    .collectSortedList(Comparator.comparing(Data::getId)) // 排序
    .flatMapMany(Flux::fromIterable);    // 展平回 Flux
```

### 模式 3：zip 聚合 → flatMap 展开

```java
// 同时请求用户信息和订单信息，合并后处理
Flux.zip(
    fetchUsers(),
    fetchOrders(),
    (users, orders) -> enrichUserWithOrders(users, orders)
).flatMapMany(Flux::fromIterable);
```

---

## 四、测试要点

```java
class OperatorsTest {
    ReactorPlayground playground = new ReactorPlayground();

    @Test @DisplayName("map — 同步转换")
    void mapExample() {
        StepVerifier.create(playground.mapExample(Flux.just(1, 2, 3)))
            .expectNext("Number: 1", "Number: 2", "Number: 3")
            .verifyComplete();
    }

    @Test @DisplayName("flatMap — 异步展平（无序）")
    void flatMapExample() {
        StepVerifier.create(playground.flatMapExample(Flux.just(1, 2, 3)))
            .expectNextCount(3)  // 数量对但顺序不确定
            .verifyComplete();
    }

    @Test @DisplayName("concatMap — 顺序展平")
    void concatMapExample() {
        StepVerifier.create(playground.concatMapExample(Flux.just(1, 2, 3)))
            .expectNext("Ordered-1", "Ordered-2", "Ordered-3")
            .verifyComplete();
    }

    @Test @DisplayName("filter — 过滤")
    void filterExample() {
        StepVerifier.create(playground.filterExample(Flux.range(1, 5)))
            .expectNext(2, 4)
            .verifyComplete();
    }

    @Test @DisplayName("merge — 交错合并（无序）")
    void mergeExample() {
        Flux<String> fast = Flux.just("A1", "A2").delayElements(Duration.ofMillis(50));
        Flux<String> slow = Flux.just("B1", "B2").delayElements(Duration.ofMillis(80));
        StepVerifier.create(playground.mergeExample(fast, slow))
            .expectNextCount(4)
            .verifyComplete();
    }

    @Test @DisplayName("concat — 串行合并（有序）")
    void concatExample() {
        StepVerifier.create(
            playground.concatExample(Flux.just("A1", "A2"), Flux.just("B1", "B2")))
            .expectNext("A1", "A2", "B1", "B2")
            .verifyComplete();
    }

    @Test @DisplayName("zip — 配对合并")
    void zipExample() {
        StepVerifier.create(
            playground.zipExample(Flux.just("Alice", "Bob"), Flux.just(95, 88)))
            .expectNext("Alice: 95", "Bob: 88")
            .verifyComplete();
    }
}
```

---

## 关联参考

- 阶段概要笔记：[README.md](./README.md)
- 定时发射器：[01-flux-interval.md](./01-flux-interval.md)
- 操作符插图参考：阶段一 README 中「转换操作符图示」段落