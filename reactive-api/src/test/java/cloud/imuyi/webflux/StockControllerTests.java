package cloud.imuyi.webflux;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

import java.time.Duration;

/**
 * SSE 实时行情独立测试
 * <p>
 * 通过 unique property 强制独立的 Spring Context。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"test.group=stock"})
class StockControllerTests {

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("GET /stocks — 返回 SSE 流，至少收到 1 条数据")
    void stockStream() {
        var webClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();

        webClient.get().uri("/stocks")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .returnResult(String.class)
                .getResponseBody()
                .take(Duration.ofSeconds(3))
                .as(StepVerifier::create)
                .expectNextCount(1)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}