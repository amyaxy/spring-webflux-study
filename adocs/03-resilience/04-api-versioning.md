# API Versioning — Spring 7.x 内置支持（**无 `@ApiVersion`**）

> Spring Framework 7.0 为 WebFlux 提供了一等公民的 API 版本化支持。

---

## ⚠️ 重要纠正：没有 `@ApiVersion` 注解

**常见误解**：Spring 7.x 的 `@ApiVersion` 注解。

**事实**：Spring 7.0.8 jar 中 **没有任何 `@ApiVersion` 注解类**。

版本化只能在 **函数式端点（RouterFunction）** 中通过 `RequestPredicates.version(Object)` 谓词实现，无法与 `@RestController` 注解式控制器配合。

### 正确做法

```
✅ RouterFunction + RequestPredicates.version("1")    ← 真实可用
❌ @RestController + @ApiVersion("2")                 ← 不存在
```

## 版本解析方式

Spring 7.x 的 `ApiVersionConfigurer` 支持多种解析策略：

| 策略 | 方法 | 请求示例 |
|------|------|---------|
| **请求头** | `useRequestHeader("X-API-Version")` | `X-API-Version: 2` |
| **媒体类型参数** | `useMediaTypeParameter(MediaType.APPLICATION_JSON, "version")` | `Accept: application/json; version=2` |
| **查询参数** | `useQueryParam("version")` | `?version=2` |
| **路径段** | `usePathSegment(1)` | `/v2/users` |
| **自定义解析器** | `useVersionResolver(...)` | 实现 `ApiVersionResolver` 接口 |

## 完整配置（与项目代码对齐）

```java
@Configuration
public class ApiVersionConfig implements WebFluxConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
            .useRequestHeader("X-API-Version")
            .useMediaTypeParameter(MediaType.APPLICATION_JSON, "version")
            .setDefaultVersion("1");
    }
}
```

## 路由层版本化

```java
@Configuration
public class UserEndpointConfig {

    @Bean
    public RouterFunction<ServerResponse> v1UserRoutes() {
        return RouterFunctions.route()
            .GET("/api/users/{id}",
                accept(MediaType.APPLICATION_JSON).and(version("1")),
                req -> {
                    Long id = Long.valueOf(req.pathVariable("id"));
                    // v1: 不含 email
                    Mono<UserProfile> user = userService.getUserReactive(id)
                        .map(u -> new UserProfile(u.getId(), u.getName(), null));
                    return ServerResponse.ok().body(user, UserProfile.class);
                })
            .build();
    }

    @Bean
    public RouterFunction<ServerResponse> v2UserRoutes() {
        return RouterFunctions.route()
            .GET("/api/users/{id}",
                accept(MediaType.APPLICATION_JSON).and(version("2")),
                req -> ServerResponse.ok()
                    .body(userService.getUserReactive(Long.valueOf(req.pathVariable("id"))),
                        UserProfile.class))
            .GET("/api/users",
                accept(MediaType.APPLICATION_JSON).and(version("2")),
                req -> ServerResponse.ok().body(
                    Flux.just(
                        new UserProfile(1L, "Alice", "alice@example.com"), ...),
                    UserProfile.class))
            .build();
    }
}
```

## 附加配置项

`ApiVersionConfigurer` 还支持：

```java
configurer
    .setVersionRequired(true)                              // 版本必填
    .setVersionParser(new SemanticApiVersionParser())      // 自定义版本解析器
    .addSupportedVersions("1", "2")                        // 指定支持版本
    .detectSupportedVersions(true)                         // 自动检测版本
    .setDeprecationHandler(new StandardApiVersionDeprecationHandler()); // 废弃处理
```

## 请求测试

```bash
# v1
curl http://localhost:8080/api/users/1 -H "X-API-Version: 1" -H "Accept: application/json"

# v2
curl http://localhost:8080/api/users -H "X-API-Version: 2" -H "Accept: application/json"

# v2 媒体类型参数方式
curl http://localhost:8080/api/users/1 -H "Accept: application/json; version=2"
```