package cloud.imuyi.resilient.endpoint;

import cloud.imuyi.resilient.model.UserProfile;
import cloud.imuyi.resilient.service.ResilientUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * API 版本化端点 —— 函数式端点（RouterFunction）+ Spring 7 内置版本匹配
 *
 * <p>v1: 基础信息（不含 email），通过 {@code X-API-Version: 1} 匹配
 * <br>v2: 完整信息（含 email），通过 {@code X-API-Version: 2} 匹配
 * <br>未指定版本 → 使用 {@link cloud.imuyi.resilient.config.ApiVersionConfig} 配置的默认版本
 */
@Configuration(proxyBeanMethods = false)
public class UserEndpointConfig {

    private final ResilientUserService userService;

    public UserEndpointConfig(ResilientUserService userService) {
        this.userService = userService;
    }

    /**
     * v1 路由 — 返回不含 email 的 UserProfile
     */
    @Bean
    public RouterFunction<ServerResponse> v1UserRoutes() {
        return RouterFunctions.route()
            .GET("/api/users/{id}", accept(MediaType.APPLICATION_JSON)
                    .and(version("1")),
                request -> {
                    Long id = Long.valueOf(request.pathVariable("id"));
                    Mono<UserProfile> user = userService.getUserReactive(id)
                        .map(u -> new UserProfile(u.getId(), u.getName(), null));
                    return ServerResponse.ok().body(user, UserProfile.class);
                })
            .build();
    }

    /**
     * v2 路由 — 返回含 email 的完整 UserProfile
     */
    @Bean
    public RouterFunction<ServerResponse> v2UserRoutes() {
        return RouterFunctions.route()
            .GET("/api/users/{id}", accept(MediaType.APPLICATION_JSON)
                    .and(version("2")),
                request -> {
                    Long id = Long.valueOf(request.pathVariable("id"));
                    return ServerResponse.ok().body(
                        userService.getUserReactive(id), UserProfile.class);
                })
            .GET("/api/users", accept(MediaType.APPLICATION_JSON)
                    .and(version("2")),
                request -> ServerResponse.ok().body(
                    Flux.just(
                        new UserProfile(1L, "Alice", "alice@example.com"),
                        new UserProfile(2L, "Bob", "bob@example.com"),
                        new UserProfile(3L, "Charlie", "charlie@example.com")
                    ), UserProfile.class))
            .build();
    }
}