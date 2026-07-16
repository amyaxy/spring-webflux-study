package cloud.imuyi.webflux.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简单限流 WebFilter — 每 IP 每秒最多 50 请求
 */
@Component
@Order(2)
public class RateLimitWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitWebFilter.class);
    private static final int MAX_PER_SECOND = 50;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";

        Window w = windows.computeIfAbsent(ip, k -> new Window());
        int count = w.increment();

        if (count > MAX_PER_SECOND) {
            log.warn("Rate limit exceeded for IP: {}", ip);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return exchange.getResponse().setComplete();
        }

        return chain.filter(exchange);
    }

    static class Window {
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        int increment() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 1000) {
                synchronized (this) {
                    if (now - windowStart > 1000) {
                        counter.set(0);
                        windowStart = now;
                    }
                }
            }
            return counter.incrementAndGet();
        }
    }
}