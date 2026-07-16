package cloud.imuyi.webflux.client;

import cloud.imuyi.webflux.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * WebClient 演示 — 调用自身 API 并流式处理结果
 *
 * 使用 @ApplicationRunner 在应用启动后自动执行一次，
 * 仅用于教学演示，实际生产中应作为独立的 Service 使用。
 */
@Configuration
public class WebClientDemo {

    private static final Logger log = LoggerFactory.getLogger(WebClientDemo.class);

    @Bean
    WebClient webClient() {
        return WebClient.create("http://localhost:8080/api");
    }

    // 注释掉启动自动执行，避免干扰正常启动
    // @Bean
    public ApplicationRunner demoWebClient(WebClient client) {
        return args -> {
            log.info("=== WebClient Demo: retrieve() ===");

            // 1. retrieve() — 获取 List
            client.get().uri("/users")
                    .retrieve()
                    .bodyToFlux(User.class)
                    .doOnNext(user -> log.info("User: {}", user))
                    .blockLast(Duration.ofSeconds(5));

            log.info("=== WebClient Demo: exchange() ===");

            // 2. exchange() — 获取完整 Response
            client.get().uri("/users/1")
                    .exchangeToMono(response ->
                            response.bodyToMono(User.class))
                    .doOnNext(user -> log.info("User by ID: {}", user))
                    .block(Duration.ofSeconds(5));

            log.info("=== WebClient Demo: SSE Stream ===");

            // 3. SSE 流式接收
            client.get().uri("/stocks/AAPL")
                    .retrieve()
                    .bodyToFlux(String.class)
                    .doOnNext(event -> log.info("SSE: {}", event))
                    .take(Duration.ofSeconds(3))
                    .blockLast(Duration.ofSeconds(10));
        };
    }
}