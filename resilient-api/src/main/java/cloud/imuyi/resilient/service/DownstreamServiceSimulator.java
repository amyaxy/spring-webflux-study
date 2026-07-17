package cloud.imuyi.resilient.service;

import cloud.imuyi.resilient.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟下游服务 —— 约 10% 的请求会超时
 *
 * <p>用于验证 {@code @Retryable} 的重试效果。
 */
@Component
public class DownstreamServiceSimulator {

    private static final Logger log = LoggerFactory.getLogger(DownstreamServiceSimulator.class);
    private static final int FAILURE_RATE = 10;   // 每 10 次请求，前 N 次模拟超时

    private final AtomicInteger counter = new AtomicInteger(0);

    /**
     * 模拟获取用户（约 10% 概率超时）
     */
    public UserProfile fetchUser(Long id) {
        int count = counter.incrementAndGet();
        int mod = count % FAILURE_RATE;

        if (mod == 0 || mod == 1) {
            log.warn("Downstream timeout on attempt #{}", count);
            throw new RuntimeException(new TimeoutException("Downstream timeout"));
        }

        log.info("Downstream success on attempt #{}", count);
        return new UserProfile(id, "User-" + count, "user" + count + "@example.com");
    }
}