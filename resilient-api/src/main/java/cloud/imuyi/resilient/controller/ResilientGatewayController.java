package cloud.imuyi.resilient.controller;

import cloud.imuyi.resilient.model.UserProfile;
import cloud.imuyi.resilient.service.ResilientUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 弹性网关控制器 —— 演示手动 Reactive retry 的弹性调用模式
 */
@RestController
@RequestMapping("/api/gateway")
public class ResilientGatewayController {

    private static final Logger log = LoggerFactory.getLogger(ResilientGatewayController.class);

    private final ResilientUserService userService;

    public ResilientGatewayController(ResilientUserService userService) {
        this.userService = userService;
    }

    /**
     * 通过弹性服务层获取用户（走 @Retryable + @ConcurrencyLimit）
     */
    @GetMapping("/users/{id}")
    public Mono<UserProfile> getUserViaElasticService(@PathVariable Long id) {
        log.info("Gateway request for user {}", id);
        return Mono.fromCallable(() -> userService.getUser(id));
    }

    /**
     * 手动 Reactive 重试网关版本 —— 使用 Reactor retryWhen
     */
    @GetMapping("/users/{id}/reactive")
    public Mono<UserProfile> getUserViaReactiveRetry(@PathVariable Long id) {
        log.info("Gateway reactive request for user {}", id);
        return Mono.fromCallable(() -> userService.getUser(id))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100))
                .maxBackoff(Duration.ofMillis(800))
                .jitter(0.1)
                .filter(t -> t instanceof TimeoutException));
    }

    /**
     * SSE 流式演示
     */
    @GetMapping(value = "/users/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<UserProfile> streamUsers() {
        return Flux.just(
            new UserProfile(1L, "Alice", "alice@example.com"),
            new UserProfile(2L, "Bob", "bob@example.com"),
            new UserProfile(3L, "Charlie", "charlie@example.com")
        );
    }
}