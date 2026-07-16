package cloud.imuyi.webflux.router;

import cloud.imuyi.webflux.service.StockService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 函数式端点 — SSE 行情路由
 */
@Configuration
public class StockRouter {

    @Bean
    public RouterFunction<ServerResponse> stockRoutes(StockService stockService) {
        return RouterFunctions.route()
                .GET("/func/stocks", request -> ServerResponse
                        .ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(stockService.allPrices(), cloud.imuyi.webflux.model.StockPrice.class))
                .GET("/func/stocks/{code}", request -> ServerResponse
                        .ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(stockService.priceByCode(request.pathVariable("code")),
                                cloud.imuyi.webflux.model.StockPrice.class))
                .build();
    }
}