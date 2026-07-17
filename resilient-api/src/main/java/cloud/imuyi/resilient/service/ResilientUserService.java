package cloud.imuyi.resilient.service;

import cloud.imuyi.resilient.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeoutException;

/**
 * 弹性用户服务 —— 演示 @Retryable + @ConcurrencyLimit
 *
 * <p>使用场景：WebClient 调用下游不稳定服务时的弹性保护层。
 */
@Service
public class ResilientUserService {

    private static final Logger log = LoggerFactory.getLogger(ResilientUserService.class);

    private final DownstreamServiceSimulator downstream;

    public ResilientUserService(DownstreamServiceSimulator downstream) {
        this.downstream = downstream;
    }

    /**
     * 获取用户 —— 带重试 + 并发限流
     *
     * <p>重试策略：最多 3 次，初始延迟 100ms，指数退避 ×2，抖动 ±10ms
     * 限流策略：最大 20 个并发
     */
    @ConcurrencyLimit(20)
    @Retryable(
        includes = TimeoutException.class,
        maxRetries = 3,
        delay = 100,
        multiplier = 2.0,
        jitter = 10
    )
    public UserProfile getUser(Long id) {
        log.info("ResilientUserService.getUser({})", id);
        return downstream.fetchUser(id);
    }

    /**
     * 响应式版本 —— @Retryable 自动适配 Mono/Flux
     */
    @ConcurrencyLimit(20)
    @Retryable(
        includes = TimeoutException.class,
        maxRetries = 3,
        delay = 100,
        multiplier = 2.0
    )
    public Mono<UserProfile> getUserReactive(Long id) {
        log.info("ResilientUserService.getUserReactive({})", id);
        return Mono.fromCallable(() -> downstream.fetchUser(id));
    }
}