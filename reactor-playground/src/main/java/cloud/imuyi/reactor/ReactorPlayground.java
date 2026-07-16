package cloud.imuyi.reactor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;

/**
 * 阶段一：Reactor 操作符演练场
 *
 * 每个练习对应一个知识点，通过 StepVerifier 验证行为。
 */
public class ReactorPlayground {

    private static final Logger log = LoggerFactory.getLogger(ReactorPlayground.class);

    // ==================== 1. 基础创建 ====================

    /** 从数组创建 Flux */
    public Flux<String> fluxFromArray(String... items) {
        return Flux.fromArray(items);
    }

    /** 从 List 创建 Flux */
    public Flux<Integer> fluxFromList(List<Integer> list) {
        return Flux.fromIterable(list);
    }

    /** 创建只发射一个元素的 Mono */
    public Mono<String> monoJust(String value) {
        return Mono.just(value);
    }

    /** 延迟执行（冷发布） */
    public Flux<Long> fluxInterval() {
        return Flux.interval(Duration.ofMillis(100)).take(5);
    }

    // ==================== 2. 转换操作符 ====================

    /** map: 同步转换 */
    public Flux<String> mapExample(Flux<Integer> source) {
        return source.map(i -> "Number: " + i);
    }

    /** flatMap: 异步展平（无序） */
    public Flux<String> flatMapExample(Flux<Integer> source) {
        return source.flatMap(i ->
                Mono.just("Item-" + i)
                        .subscribeOn(Schedulers.parallel())
        );
    }

    /** concatMap: 保持顺序的展平 */
    public Flux<String> concatMapExample(Flux<Integer> source) {
        return source.concatMap(i ->
                Mono.just("Ordered-" + i)
                        .delayElement(Duration.ofMillis(50))
        );
    }

    /** filter: 过滤 */
    public Flux<Integer> filterExample(Flux<Integer> source) {
        return source.filter(i -> i % 2 == 0);
    }

    // ==================== 3. 合并操作符 ====================

    /** merge: 交错合并（无序） */
    public Flux<String> mergeExample(Flux<String> a, Flux<String> b) {
        return Flux.merge(a, b);
    }

    /** concat: 串行合并（有序） */
    public Flux<String> concatExample(Flux<String> a, Flux<String> b) {
        return Flux.concat(a, b);
    }

    /** zip: 配对合并 */
    public Flux<String> zipExample(Flux<String> names, Flux<Integer> scores) {
        return Flux.zip(names, scores, (name, score) -> name + ": " + score);
    }

    // ==================== 4. 错误处理 ====================

    /** 发生错误时返回默认值 */
    public Mono<String> fallbackExample() {
        return Mono.<String>error(new RuntimeException("oops"))
                .onErrorReturn("fallback");
    }

    /** 错误时切换到备用 Mono */
    public Mono<String> fallbackResumeExample() {
        return Mono.<String>error(new RuntimeException("failed"))
                .onErrorResume(e -> Mono.just("resumed: " + e.getMessage()));
    }

    /** 重试 — 用 map + throw 替代 flatMap 避免并发不确定性 */
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

    /** 超时 */
    public Flux<Long> timeoutExample() {
        return Flux.interval(Duration.ofSeconds(10))
                .take(1)
                .timeout(Duration.ofMillis(100))
                .onErrorResume(e -> Flux.just(-1L));
    }

    // ==================== 5. 背压示例 ====================

    /** onBackpressureBuffer: 缓冲背压 */
    public Flux<Integer> bufferBackpressure() {
        return Flux.range(1, 1000)
                .onBackpressureBuffer(100);
    }

    /** onBackpressureDrop: 丢弃背压 */
    public Flux<Integer> dropBackpressure() {
        return Flux.range(1, 1000)
                .onBackpressureDrop(i -> log.warn("Dropped: {}", i));
    }

    // ==================== 6. 调度器示例 ====================

    /** 在 boundedElastic 调度器上执行阻塞操作 */
    public Mono<String> blockingOperation(String input) {
        return Mono.fromCallable(() -> {
            Thread.sleep(100); // 模拟阻塞 I/O
            return "processed: " + input;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /** 并行处理 */
    public Flux<Integer> parallelProcessing(Flux<Integer> source) {
        return source
                .parallel(4)
                .runOn(Schedulers.parallel())
                .map(i -> i * 2)
                .sequential();
    }

    // ==================== 7. 实战练习：事件流处理 ====================

    /**
     * 模拟 SSE 事件流：
     * 每 100ms 发射一个事件，共 5 个，
     * 转换为 ServerSentEvent 格式字符串
     */
    public Flux<String> sseStream() {
        return Flux.interval(Duration.ofMillis(100))
                .take(5)
                .map(i -> "data: event-" + i + "\n\n")
                .doOnNext(e -> log.info("SSE: {}", e.trim()));
    }

    /**
     * 批处理窗口：
     * 每 500ms 收集一批数据，返回聚合结果
     */
    public Flux<List<Long>> windowedProcessing() {
        return Flux.interval(Duration.ofMillis(100))
                .take(20)
                .window(Duration.ofMillis(500))
                .flatMap(window -> window.collectList());
    }

    // ==================== 8. 常用方法汇总 ====================

    // ——— 创建 ———

    /** Flux.empty — 空 Flux */
    public Flux<String> emptyFlux() {
        return Flux.empty();
    }

    /** Mono.delay — 延迟后发射 */
    public Mono<String> delayExample() {
        return Mono.delay(Duration.ofMillis(100))
                .map(i -> "delayed: " + i);
    }

    // ——— 选择 / 截取 ———

    /** take — 取前 N 个元素 */
    public Flux<Integer> takeExample(Flux<Integer> source, int n) {
        return source.take(n);
    }

    /** skip — 跳过前 N 个元素 */
    public Flux<Integer> skipExample(Flux<Integer> source, int n) {
        return source.skip(n);
    }

    // ——— 去重 ———

    /** distinct — 全局去重（基于 equals/hashCode） */
    public Flux<Integer> distinctExample(Flux<Integer> source) {
        return source.distinct();
    }

    /** distinctUntilChanged — 连续去重（相邻相同才去重） */
    public Flux<Integer> distinctUntilChangedExample(Flux<Integer> source) {
        return source.distinctUntilChanged();
    }

    // ——— 聚合 / 累计 ———

    /** reduce — 累加归约，返回 Mono */
    public Mono<Integer> reduceExample(Flux<Integer> source) {
        return source.reduce(0, Integer::sum);
    }

    /** count — 元素计数 */
    public Mono<Long> countExample(Flux<Integer> source) {
        return source.count();
    }

    // ——— 条件判断 ———

    /** all — 全部元素满足条件 */
    public Mono<Boolean> allExample(Flux<Integer> source) {
        return source.all(i -> i > 0);
    }

    // ——— 空值处理 ———

    /** defaultIfEmpty — 空流时返回默认值 */
    public Mono<String> defaultIfEmptyExample(Mono<String> source) {
        return source.defaultIfEmpty("default");
    }

    // ——— 结果转换 ———

    /** thenReturn — 忽略前一个结果，返回固定值 */
    public Mono<String> thenReturnExample(Mono<String> source) {
        return source.thenReturn("done");
    }
}