# Reactor 常用方法速查

> **所属阶段**：阶段一 · 响应式编程基础 & Reactor 核心
> **对应代码**：`ReactorPlayground.java` — 第 8 组「常用方法汇总」
> **测试定位**：`ReactorPlaygroundTest.java` — 第 8 组完整测试

---

## 一、创建

| 方法 | 返回 | 说明 |
|------|------|------|
| `Flux.just(a, b, c)` | `Flux<T>` | 从多个值创建 Flux |
| `Flux.fromArray(arr)` | `Flux<T>` | 从数组创建 |
| `Flux.fromIterable(list)` | `Flux<T>` | 从集合创建 |
| `Flux.range(start, count)` | `Flux<Integer>` | 整数序列 |
| `Flux.interval(period)` | `Flux<Long>` | 定时递增序列 |
| `Flux.empty()` | `Flux<T>` | 空 Flux，无元素直接完成 |
| `Flux.error(ex)` | `Flux<T>` | 立即发射错误的 Flux |
| `Mono.just(val)` | `Mono<T>` | 包装单个非 null 值 |
| `Mono.empty()` | `Mono<T>` | 空 Mono，直接完成 |
| `Mono.fromCallable(() → ...)` | `Mono<T>` | 延迟计算并发射 |
| `Mono.delay(Duration)` | `Mono<Long>` | 延迟一段时间后发射 0 |

```java
// 代码示例
public Flux<String> emptyFlux() {
    return Flux.empty();
}

public Mono<String> delayExample() {
    return Mono.delay(Duration.ofMillis(100))
            .map(i -> "delayed: " + i);
}
```

**测试**：
```java
StepVerifier.create(playground.emptyFlux())
        .verifyComplete();  // 直接完成，无任何元素

StepVerifier.withVirtualTime(playground::delayExample)
        .thenAwait(Duration.ofMillis(150))
        .expectNext("delayed: 0")
        .verifyComplete();
```

---

## 二、选择 / 截取

| 方法 | 效果 | 类比 |
|------|------|------|
| `take(n)` | 只取前 n 个元素 | `stream.limit(n)` |
| `skip(n)` | 跳过前 n 个元素 | `stream.skip(n)` |
| `takeLast(n)` | 只取最后 n 个元素 | — |
| `takeUntil(predicate)` | 一直取到满足条件为止（包含该元素） | — |
| `takeWhile(predicate)` | 条件成立时取，不成立时停（不含该元素） | — |
| `elementAt(n)` | 取第 n 个元素（0-based），返回 `Mono<T>` | — |
| `single()` | 必须有且只有 1 个元素，否则抛异常 | — |
| `next()` | Flux 转 Mono，取第一个元素 | — |

```java
public Flux<Integer> takeExample(Flux<Integer> source, int n) {
    return source.take(n);
}

public Flux<Integer> skipExample(Flux<Integer> source, int n) {
    return source.skip(n);
}
```

**执行流程**：

```
take(3): [1, 2, 3, 4, 5] → [1, 2, 3]  ← 取完 3 个立即 cancel 上游
skip(3): [1, 2, 3, 4, 5] → [4, 5]      ← 忽略前 3 个，继续
```

**测试**：
```java
StepVerifier.create(playground.takeExample(Flux.range(1, 10), 3))
        .expectNext(1, 2, 3)
        .verifyComplete();

StepVerifier.create(playground.skipExample(Flux.range(1, 10), 3))
        .expectNext(4, 5, 6, 7, 8, 9, 10)
        .verifyComplete();
```

---

## 三、去重

| 方法 | 效果 | 内存开销 |
|------|------|----------|
| `distinct()` | **全局去重** — 基于 `equals/hashCode`，保留首次出现的元素 | 高（记住所有已见元素） |
| `distinctUntilChanged()` | **连续去重** — 只去除相邻的重复元素 | 低（只记住上一个） |
| `distinct(keyMapper)` | 按 key 的 hashCode 全局去重 | 中（记住所有 key） |

```java
public Flux<Integer> distinctExample(Flux<Integer> source) {
    return source.distinct();
}

public Flux<Integer> distinctUntilChangedExample(Flux<Integer> source) {
        return source.distinctUntilChanged();
}
```

**两者区别**：

```
输入:        [1, 1, 2, 2, 2, 3, 1, 1]

distinct():           [1, 2, 3]         ← 只认第一次出现
distinctUntilChanged(): [1, 2, 3, 1]    ← 相邻相同才去重，1→1 去重，3→1 保留
```

**测试**：
```java
StepVerifier.create(playground.distinctExample(Flux.just(1, 2, 2, 3, 1, 4, 2)))
        .expectNext(1, 2, 3, 4)        // 全局去重
        .verifyComplete();

StepVerifier.create(playground.distinctUntilChangedExample(
        Flux.just(1, 1, 2, 2, 2, 3, 1, 1)))
        .expectNext(1, 2, 3, 1)        // 连续去重，1 再次出现时保留
        .verifyComplete();
```

> 💡 **选型建议**：数据量大时 `distinctUntilChanged` 更省内存；少量数据 `distinct` 更全面。

---

## 四、聚合 / 累计

| 方法 | 返回 | 说明 |
|------|------|------|
| `reduce(identity, accumulator)` | `Mono<T>` | 累计归约，identity 为初始值 |
| `reduceWith(() → seed, accumulator)` | `Mono<T>` | 自定义初始容器的 reduce |
| `count()` | `Mono<Long>` | 元素个数计数 |
| `collectList()` | `Mono<List<T>>` | 收集为 List |
| `collectSortedList(comparator)` | `Mono<List<T>>` | 收集为排序后的 List |
| `collectMap(keyMapper)` | `Mono<Map<K, T>>` | 按 key 收集为 Map |

```java
public Mono<Integer> reduceExample(Flux<Integer> source) {
    return source.reduce(0, Integer::sum);
}

public Mono<Long> countExample(Flux<Integer> source) {
    return source.count();
}
```

**reduce 执行流程**：

```
reduce(0, Integer::sum)  on Flux.range(1, 5)

初始:  acc = 0
第1个: acc = 0 + 1 = 1
第2个: acc = 1 + 2 = 3
第3个: acc = 3 + 3 = 6
第4个: acc = 6 + 4 = 10
第5个: acc = 10 + 5 = 15
完成:  emit 15
```

**测试**：
```java
StepVerifier.create(playground.reduceExample(Flux.range(1, 5)))
        .expectNext(15)      // 1+2+3+4+5
        .verifyComplete();

StepVerifier.create(playground.countExample(Flux.range(1, 5)))
        .expectNext(5L)      // 5 个元素
        .verifyComplete();
```

---

## 五、条件判断

| 方法 | 返回 | 说明 |
|------|------|------|
| `all(predicate)` | `Mono<Boolean>` | **全部**元素满足条件 → true |
| `any(predicate)` | `Mono<Boolean>` | **任一**元素满足条件 → true |
| `hasElements()` | `Mono<Boolean>` | 流中是否有元素（非空） |
| `hasElement(value)` | `Mono<Boolean>` | 流中是否包含某元素 |
| `sequenceEqual(a, b)` | `Mono<Boolean>` | 两个序列是否相等（元素和顺序） |

```java
public Mono<Boolean> allExample(Flux<Integer> source) {
    return source.all(i -> i > 0);
}
```

**执行流程**：

```
all(i > 0) on [1, 2, 3, 4, 5]:
  i=1 → true（继续）
  i=2 → true（继续）
  i=3 → true（继续）
  i=4 → true（继续）
  i=5 → true（全部完成）
  → emit true ✓

all(i > 0) on [1, 0, -1]:
  i=1 → true（继续）
  i=0 → false（停止检查）
  → emit false ✓
```

> 💡 `all` 和 `any` 都是**短路的**— 一旦确定结果就停止检查剩余元素。

**测试**：
```java
StepVerifier.create(playground.allExample(Flux.range(1, 5)))
        .expectNext(true)   // 全部 > 0
        .verifyComplete();

StepVerifier.create(playground.allExample(Flux.just(1, 0, -1)))
        .expectNext(false)  // 包含非正数
        .verifyComplete();
```

---

## 六、空值处理

| 方法 | 返回 | 说明 |
|------|------|------|
| `defaultIfEmpty(defaultVal)` | 同源类型 | 流为空时发射默认值 |
| `switchIfEmpty(alternative)` | 同源类型 | 流为空时切换到备选 Publisher |
| `switchOnEmpty(alternative)` | `Flux<T>` | 同`switchIfEmpty`，但用于 Flux |

```java
public Mono<String> defaultIfEmptyExample(Mono<String> source) {
    return source.defaultIfEmpty("default");
}
```

**执行流程**：

```
空流: [] ──→ defaultIfEmpty("default") ──→ "default" ──→ onComplete
有值流: ["hello"] ──→ defaultIfEmpty("default") ──→ "hello" ──→ onComplete
```

> `defaultIfEmpty` vs `switchIfEmpty`：前者只返回一个**静态值**，后者可返回一个**完整的 Publisher**（Mono/Flux 都可以）。

```java
// defaultIfEmpty — 静态降级
Mono.empty().defaultIfEmpty("default");

// switchIfEmpty — 动态降级（可执行另一个操作）
Mono.empty().switchIfEmpty(fetchFromCache(id));
```

**测试**：
```java
StepVerifier.create(playground.defaultIfEmptyExample(Mono.empty()))
        .expectNext("default")
        .verifyComplete();

StepVerifier.create(playground.defaultIfEmptyExample(Mono.just("hello")))
        .expectNext("hello")
        .verifyComplete();
```

---

## 七、结果转换

| 方法 | 返回 | 说明 |
|------|------|------|
| `then()` | `Mono<Void>` | 忽略所有元素和值，完成后通知 |
| `thenReturn(value)` | `Mono<V>` | 忽略上游结果，返回固定值 |
| `thenMany(rhs)` | `Flux<V>` | 忽略上游 Flux 结果，切换到另一个 Publisher |
| `and()` | `Mono<Void>` | 等待所有 Mono 完成后通知 |
| `when()` | `Mono<Void>` | 等待所有 Publisher 完成后通知 |

```java
public Mono<String> thenReturnExample(Mono<String> source) {
    return source.thenReturn("done");
}
```

**执行流程**：

```
Mono.just("ignored") ──→ thenReturn("done") ──→ "done" ──→ onComplete
                          ↑
                      上游结果被忽略
```

> 💡 `thenReturn` 常用于"发了消息之后返回成功"、或"处理完后返回固定状态码"的场景。

**测试**：
```java
StepVerifier.create(playground.thenReturnExample(Mono.just("ignored")))
        .expectNext("done")
        .verifyComplete();
```

---

## 八、方法速查表

### Flux 维度

| 分类 | 方法 | 返回 | 一句话 |
|------|------|------|--------|
| **创建** | `just`, `fromArray`, `fromIterable`, `range`, `interval`, `empty`, `error` | `Flux<T>` | 创建各种类型的 Flux |
| **转换** | `map`, `flatMap`, `concatMap`, `switchMap` | `Flux<R>` | 元素映射和展平 |
| **选择** | `take(n)`, `takeLast(n)`, `skip(n)`, `elementAt(n)`, `next()` | `Flux/Mono` | 截取和定位元素 |
| **过滤** | `filter`, `distinct`, `distinctUntilChanged` | `Flux<T>` | 保留或剔除元素 |
| **合并** | `merge`, `concat`, `zip`, `combineLatest` | `Flux<R>` | 合并多个流 |
| **聚合** | `reduce`, `count`, `collectList`, `collectMap` | `Mono` | 汇总为单个结果 |
| **条件** | `all`, `any`, `hasElements`, `hasElement` | `Mono<Boolean>` | 逻辑判断 |
| **错误** | `onErrorReturn`, `onErrorResume`, `retry`, `timeout` | `Flux<T>` | 错误处理和降级 |
| **副作用** | `doOnNext`, `doOnComplete`, `doOnError`, `doFinally` | `Flux<T>` | 观察流行为，不修改数据 |
| **空值** | `defaultIfEmpty`, `switchIfEmpty` | `Flux<T>` | 空流降级 |
| **结果** | `then`, `thenReturn`, `thenMany` | `Mono/Flux` | 忽略结果取固定值 |

### Mono 维度

| 分类 | 方法 | 返回 | 一句话 |
|------|------|------|--------|
| **创建** | `just`, `empty`, `error`, `fromCallable`, `delay` | `Mono<T>` | 创建各种 Mono |
| **转换** | `map`, `flatMap`, `then` | `Mono<R>` | 映射或忽略结果 |
| **空值** | `defaultIfEmpty`, `switchIfEmpty` | `Mono<T>` | 空值降级 |
| **错误** | `onErrorReturn`, `onErrorResume`, `retryWhen` | `Mono<T>` | 错误处理 |
| **阻塞** | `block()`, `blockOptional()` | `T / Optional<T>` | 🔴 阻塞等结果（仅限必要处） |

---

## 九、完整测试对照

```java
class CommonMethodsTest {
    ReactorPlayground playground = new ReactorPlayground();

    // ——— 创建 ———

    @Test @DisplayName("Flux.empty — 空 Flux")
    void emptyFlux() {
        StepVerifier.create(playground.emptyFlux())
            .verifyComplete();
    }

    @Test @DisplayName("Mono.delay — 延迟 100ms 后发射")
    void delayExample() {
        StepVerifier.withVirtualTime(playground::delayExample)
            .thenAwait(Duration.ofMillis(150))
            .expectNext("delayed: 0")
            .verifyComplete();
    }

    // ——— 选择 / 截取 ———

    @Test @DisplayName("take — 取前 3 个元素")
    void takeExample() {
        StepVerifier.create(playground.takeExample(Flux.range(1, 10), 3))
            .expectNext(1, 2, 3)
            .verifyComplete();
    }

    @Test @DisplayName("skip — 跳过前 3 个元素")
    void skipExample() {
        StepVerifier.create(playground.skipExample(Flux.range(1, 10), 3))
            .expectNext(4, 5, 6, 7, 8, 9, 10)
            .verifyComplete();
    }

    // ——— 去重 ———

    @Test @DisplayName("distinct — 全局去重")
    void distinctExample() {
        StepVerifier.create(playground.distinctExample(Flux.just(1, 2, 2, 3, 1, 4, 2)))
            .expectNext(1, 2, 3, 4)
            .verifyComplete();
    }

    @Test @DisplayName("distinctUntilChanged — 连续去重")
    void distinctUntilChangedExample() {
        StepVerifier.create(playground.distinctUntilChangedExample(
            Flux.just(1, 1, 2, 2, 2, 3, 1, 1)))
            .expectNext(1, 2, 3, 1)
            .verifyComplete();
    }

    // ——— 聚合 ———

    @Test @DisplayName("reduce — 1+2+3+4+5 = 15")
    void reduceExample() {
        StepVerifier.create(playground.reduceExample(Flux.range(1, 5)))
            .expectNext(15)
            .verifyComplete();
    }

    @Test @DisplayName("count — 5 个元素")
    void countExample() {
        StepVerifier.create(playground.countExample(Flux.range(1, 5)))
            .expectNext(5L)
            .verifyComplete();
    }

    // ——— 条件 ———

    @Test @DisplayName("all — 全部大于 0 → true")
    void allPositive() {
        StepVerifier.create(playground.allExample(Flux.range(1, 5)))
            .expectNext(true)
            .verifyComplete();
    }

    @Test @DisplayName("all — 包含非正数 → false")
    void allNotAllPositive() {
        StepVerifier.create(playground.allExample(Flux.just(1, 0, -1)))
            .expectNext(false)
            .verifyComplete();
    }

    // ——— 空值 ———

    @Test @DisplayName("defaultIfEmpty — 空流回退")
    void defaultIfEmptyOnEmpty() {
        StepVerifier.create(playground.defaultIfEmptyExample(Mono.empty()))
            .expectNext("default")
            .verifyComplete();
    }

    @Test @DisplayName("defaultIfEmpty — 有值时不受影响")
    void defaultIfEmptyOnPresent() {
        StepVerifier.create(playground.defaultIfEmptyExample(Mono.just("hello")))
            .expectNext("hello")
            .verifyComplete();
    }

    // ——— 结果转换 ———

    @Test @DisplayName("thenReturn — 忽略上游，返回固定值")
    void thenReturnExample() {
        StepVerifier.create(playground.thenReturnExample(Mono.just("ignored")))
            .expectNext("done")
            .verifyComplete();
    }
}
```

---

## 关联参考

- 阶段概要笔记：[README.md](./README.md)
- 操作符详解：[02-operators.md](./02-operators.md)
- 错误处理：[03-error-handling.md](./03-error-handling.md)
- 背压：[04-backpressure.md](./04-backpressure.md)
- 调度器：[05-schedulers.md](./05-schedulers.md)