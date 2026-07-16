package cloud.imuyi.webflux.service;

import cloud.imuyi.webflux.model.StockPrice;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 模拟股票行情推送 — 使用 Sinks.Many 实现热流
 */
@Service
public class StockService {

    private final List<String> STOCKS = List.of(
            "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA"
    );
    private final Map<String, AtomicReference<BigDecimal>> prices = new ConcurrentHashMap<>();
    private final Sinks.Many<StockPrice> sink = Sinks.many().multicast().onBackpressureBuffer();

    @PostConstruct
    void init() {
        // 初始化价格
        for (String code : STOCKS) {
            var initialPrice = BigDecimal.valueOf(100 + ThreadLocalRandom.current().nextDouble(400))
                    .setScale(2, RoundingMode.HALF_UP);
            prices.put(code, new AtomicReference<>(initialPrice));
        }

        // 每秒模拟价格变化
        Flux.interval(java.time.Duration.ofSeconds(1))
                .subscribe(tick -> {
                    for (String code : STOCKS) {
                        var ref = prices.get(code);
                        var old = ref.get();
                        var change = BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble(-5, 5))
                                .setScale(2, RoundingMode.HALF_UP);
                        var newPrice = old.add(change).max(BigDecimal.valueOf(0.01));
                        ref.set(newPrice);

                        var changePercent = change.divide(old, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);

                        var stock = new StockPrice(code, nameOf(code), newPrice, changePercent, Instant.now());
                        var result = sink.tryEmitNext(stock);
                        if (result.isFailure()) {
                            // 背压丢弃 — 可补充日志
                        }
                    }
                });
    }

    /**
     * 返回所有股票的实时行情流（SSE）
     */
    public Flux<StockPrice> allPrices() {
        return sink.asFlux();
    }

    /**
     * 返回指定股票的实时行情流（SSE）
     */
    public Flux<StockPrice> priceByCode(String code) {
        return sink.asFlux().filter(s -> s.code().equalsIgnoreCase(code));
    }

    private String nameOf(String code) {
        return switch (code) {
            case "AAPL" -> "Apple Inc.";
            case "GOOGL" -> "Alphabet Inc.";
            case "MSFT" -> "Microsoft Corp.";
            case "AMZN" -> "Amazon.com Inc.";
            case "TSLA" -> "Tesla Inc.";
            default -> code;
        };
    }
}