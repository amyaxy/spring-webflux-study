package cloud.imuyi.reactor;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reactor 操作符测试 — 使用 StepVerifier 验证行为
 */
@Slf4j
class ReactorPlaygroundTest {

    private ReactorPlayground playground;

    @BeforeEach
    void setUp() {
        playground = new ReactorPlayground();
    }

    // ==================== 1. 基础创建 ====================

    @Test
    @DisplayName("Flux.fromArray — 从数组创建 Flux")
    void fluxFromArray() {
        Flux<String> flux = playground.fluxFromArray("a", "b", "c");

        StepVerifier.create(flux)
                .expectNext("a", "b", "c")
                .verifyComplete();
    }

    @Test
    @DisplayName("Flux.fromIterable — 从 List 创建 Flux")
    void fluxFromList() {
        Flux<Integer> flux = playground.fluxFromList(List.of(1, 2, 3));

        StepVerifier.create(flux)
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    @DisplayName("Mono.just — 单元素 Mono")
    void monoJust() {
        Mono<String> mono = playground.monoJust("hello");

        StepVerifier.create(mono)
                .expectNext("hello")
                .verifyComplete();
    }

    @Test
    @DisplayName("Flux.interval — 定时发射（冷流）")
    void fluxInterval() throws InterruptedException {
        Flux<Long> flux = playground.fluxInterval();

        StepVerifier.withVirtualTime(() -> flux)
                .thenAwait(Duration.ofSeconds(1))
                .expectNext(0L, 1L, 2L, 3L, 4L)
                .verifyComplete();
    }

    // ==================== 2. 转换操作符 ====================

    @Test
    @DisplayName("map — 同步转换")
    void mapExample() {
        Flux<String> result = playground.mapExample(Flux.just(1, 2, 3));

        StepVerifier.create(result)
                .expectNext("Number: 1", "Number: 2", "Number: 3")
                .verifyComplete();
    }

    @Test
    @DisplayName("filter — 过滤偶数")
    void filterExample() {
        Flux<Integer> result = playground.filterExample(Flux.range(1, 10));

        StepVerifier.create(result)
                .expectNext(2, 4, 6, 8, 10)
                .verifyComplete();
    }

    @Test
    @DisplayName("flatMap — 异步展平（无序）")
    void flatMapExample() {
        Flux<String> result = playground.flatMapExample(Flux.just(1, 2, 3));

        StepVerifier.create(result)
                .expectNextCount(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("concatMap — 顺序展平")
    void concatMapExample() {
        Flux<String> result = playground.concatMapExample(Flux.just(1, 2, 3));

        StepVerifier.create(result)
                .expectNext("Ordered-1", "Ordered-2", "Ordered-3")
                .verifyComplete();
    }

    // ==================== 3. 合并操作符 ====================

    @Test
    @DisplayName("merge — 交错合并（无序）")
    void mergeExample() {
        Flux<String> a = Flux.just("A1", "A2", "A3").delayElements(Duration.ofMillis(50));
        Flux<String> b = Flux.just("B1", "B2", "B3").delayElements(Duration.ofMillis(80));

        Flux<String> result = playground.mergeExample(a, b);

        StepVerifier.create(result)
                .expectNextCount(6)
                .verifyComplete();
    }

    @Test
    @DisplayName("concat — 串行合并（有序）")
    void concatExample() {
        Flux<String> result = playground.concatExample(
                Flux.just("A1", "A2"),
                Flux.just("B1", "B2")
        );

        StepVerifier.create(result)
                .expectNext("A1", "A2", "B1", "B2")
                .verifyComplete();
    }

    @Test
    @DisplayName("zip — 配对合并")
    void zipExample() {
        Flux<String> result = playground.zipExample(
                Flux.just("Alice", "Bob"),
                Flux.just(95, 88)
        );

        StepVerifier.create(result)
                .expectNext("Alice: 95", "Bob: 88")
                .verifyComplete();
    }

    // ==================== 4. 错误处理 ====================

    @Test
    @DisplayName("onErrorReturn — 错误时返回默认值")
    void fallbackExample() {
        StepVerifier.create(playground.fallbackExample())
                .expectNext("fallback")
                .verifyComplete();
    }

    @Test
    @DisplayName("onErrorResume — 错误时切换到备用流")
    void fallbackResumeExample() {
        StepVerifier.create(playground.fallbackResumeExample())
                .expectNext("resumed: failed")
                .verifyComplete();
    }

    @Test
    @DisplayName("retry — 重试")
    void retryExample() {
        // retry(2) 重试 2 次：初始→1,error → 第1次→1,error → 第2次→1,error → 最终错误传播
        StepVerifier.create(playground.retryExample())
                .expectNext(1)   // 初始
                .expectNext(1)   // 第1次重试
                .expectNext(1)   // 第2次重试
                .expectErrorMessage("retry me")
                .verify();
    }

    @Test
    @DisplayName("timeout — 超时降级")
    void timeoutExample() {
        StepVerifier.create(playground.timeoutExample())
                .expectNext(-1L)
                .verifyComplete();
    }

    // ==================== 5. 调度器 ====================

    @Test
    @DisplayName("subscribeOn — 在指定调度器上执行阻塞操作")
    void blockingOperation() {
        StepVerifier.create(playground.blockingOperation("test"))
                .expectNext("processed: test")
                .verifyComplete();
    }

    @Test
    @DisplayName("parallel — 并行处理")
    void parallelProcessing() {
        Flux<Integer> result = playground.parallelProcessing(Flux.range(1, 10));

        StepVerifier.create(result)
                .expectNextCount(10)
                .verifyComplete();
    }

    @Test
    void test(){
        Flux.range(1, 100)
                .doOnRequest(n -> log.info("上游收到请求: {}", n))
                .doOnNext(i -> log.info("上游发射: {}", i))
                .limitRate(5)
                .doOnRequest(n -> log.info("下游请求: {}", n))
                .doOnNext(i -> log.info("下游消费: {} (延迟 50ms)", i))
                .delayElements(Duration.ofMillis(50))
                .blockLast();
    }

    // ==================== 6. 实战练习 ====================

    @Test
    @DisplayName("SSE 事件流模拟")
    void sseStream() {
        StepVerifier.create(playground.sseStream())
                .expectNext("data: event-0\n\n")
                .expectNext("data: event-1\n\n")
                .expectNext("data: event-2\n\n")
                .expectNext("data: event-3\n\n")
                .expectNext("data: event-4\n\n")
                .verifyComplete();
    }

    @Test
    @DisplayName("窗口批处理")
    void windowedProcessing() {
        StepVerifier.withVirtualTime(() -> playground.windowedProcessing().take(2))
                .thenAwait(Duration.ofSeconds(3))
                .expectNextCount(2)
                .verifyComplete();
    }

    // ==================== 7. 额外：Mono 空值处理 ====================

    @Test
    @DisplayName("Mono.switchIfEmpty — 空值回退")
    void switchIfEmpty() {
        Mono<String> empty = Mono.empty();
        Mono<String> fallback = empty.switchIfEmpty(Mono.just("default"));

        StepVerifier.create(fallback)
                .expectNext("default")
                .verifyComplete();
    }

    @Test
    @DisplayName("Flux 收集为 List")
    void collectList() {
        Mono<List<Integer>> list = Flux.range(1, 5).collectList();

        StepVerifier.create(list)
                .assertNext(result -> assertThat(result).hasSize(5))
                .verifyComplete();
    }

    // ==================== 8. 常用方法汇总 ====================

    // ——— 创建 ———

    @Test
    @DisplayName("Flux.empty — 空 Flux 无元素直接完成")
    void emptyFlux() {
        StepVerifier.create(playground.emptyFlux())
                .verifyComplete();
    }

    @Test
    @DisplayName("Mono.delay — 延迟 100ms 后发射 0")
    void delayExample() {
        StepVerifier.withVirtualTime(playground::delayExample)
                .thenAwait(Duration.ofMillis(150))
                .expectNext("delayed: 0")
                .verifyComplete();
    }

    // ——— 选择 / 截取 ———

    @Test
    @DisplayName("take — 取前 3 个元素")
    void takeExample() {
        StepVerifier.create(playground.takeExample(Flux.range(1, 10), 3))
                .expectNext(1, 2, 3)
                .verifyComplete();
    }

    @Test
    @DisplayName("skip — 跳过前 3 个元素")
    void skipExample() {
        StepVerifier.create(playground.skipExample(Flux.range(1, 10), 3))
                .expectNext(4, 5, 6, 7, 8, 9, 10)
                .verifyComplete();
    }

    // ——— 去重 ———

    @Test
    @DisplayName("distinct — 全局去重，保留首次出现的元素")
    void distinctExample() {
        StepVerifier.create(playground.distinctExample(Flux.just(1, 2, 2, 3, 1, 4, 2)))
                .expectNext(1, 2, 3, 4)
                .verifyComplete();
    }

    @Test
    @DisplayName("distinctUntilChanged — 连续去重，相邻相同才过滤")
    void distinctUntilChangedExample() {
        StepVerifier.create(playground.distinctUntilChangedExample(
                Flux.just(1, 1, 2, 2, 2, 3, 1, 1)))
                .expectNext(1, 2, 3, 1)
                .verifyComplete();
    }

    // ——— 聚合 / 累计 ———

    @Test
    @DisplayName("reduce — 累加求和：1+2+3+4+5 = 15")
    void reduceExample() {
        StepVerifier.create(playground.reduceExample(Flux.range(1, 5)))
                .expectNext(15)
                .verifyComplete();
    }

    @Test
    @DisplayName("count — 元素计数：5 个元素 = 5")
    void countExample() {
        StepVerifier.create(playground.countExample(Flux.range(1, 5)))
                .expectNext(5L)
                .verifyComplete();
    }

    // ——— 条件判断 ———

    @Test
    @DisplayName("all — 全部大于 0 → true")
    void allPositive() {
        StepVerifier.create(playground.allExample(Flux.range(1, 5)))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("all — 包含非正数 → false")
    void allNotAllPositive() {
        StepVerifier.create(playground.allExample(Flux.just(1, 0, -1)))
                .expectNext(false)
                .verifyComplete();
    }

    // ——— 空值处理 ———

    @Test
    @DisplayName("defaultIfEmpty — 空流回退默认值")
    void defaultIfEmptyOnEmpty() {
        StepVerifier.create(playground.defaultIfEmptyExample(Mono.empty()))
                .expectNext("default")
                .verifyComplete();
    }

    @Test
    @DisplayName("defaultIfEmpty — 非空流不受影响")
    void defaultIfEmptyOnPresent() {
        StepVerifier.create(playground.defaultIfEmptyExample(Mono.just("hello")))
                .expectNext("hello")
                .verifyComplete();
    }

    // ——— 结果转换 ———

    @Test
    @DisplayName("thenReturn — 忽略上游结果，返回固定值")
    void thenReturnExample() {
        StepVerifier.create(playground.thenReturnExample(
                Mono.just("ignored")))
                .expectNext("done")
                .verifyComplete();
    }
}