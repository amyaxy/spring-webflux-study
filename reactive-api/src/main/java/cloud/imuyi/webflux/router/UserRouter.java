package cloud.imuyi.webflux.router;

import cloud.imuyi.webflux.handler.UserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

/**
 * 函数式端点 Router — 等价于 UserController 的注解式路由
 *
 * 请求路径统一为 /func/users，与注解式 /api/users 区分
 */
@Configuration
public class UserRouter {

    @Bean
    public RouterFunction<ServerResponse> userRoutes(UserHandler handler) {
        return RouterFunctions.route()
                .GET("/func/users", handler::findAll)
                .GET("/func/users/{id}", handler::findById)
                .POST("/func/users", handler::create)
                .DELETE("/func/users/{id}", handler::delete)
                .build();
    }
}