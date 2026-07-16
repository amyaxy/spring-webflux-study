package cloud.imuyi.webflux.controller;

import cloud.imuyi.webflux.model.StockPrice;
import cloud.imuyi.webflux.service.StockService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * SSE 实时行情推送 — text/event-stream
 */
@RestController
public class StockController {

    private final StockService stockService;

    public StockController(StockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping(value = "/stocks", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StockPrice> allStocks() {
        return stockService.allPrices();
    }

    @GetMapping(value = "/stocks/{code}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StockPrice> stockByCode(@PathVariable String code) {
        return stockService.priceByCode(code);
    }
}