# `@Retryable` — Spring 7.x 声明式重试（真实 API）

> Spring Framework 7.0 将 Spring Retry 的核心功能合并入 `spring-context`。

---

## 依赖

Spring 7.x 的 `@Retryable` 位于 `spring-context`，无需额外引入 Spring Retry：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
    <!-- 传递依赖包含 spring-context -->
</dependency>
```

## 实际包路径

```
org.springframework.resilience.annotation.Retryable     ← 正确
```

**不是** `org.springframework.retry.annotation.Retryable`（那是旧版 Spring Retry）。

## 启用

```java
@Configuration
@EnableResilientMethods
public class ResilienceConfig {
}
```

## 注解参数（基于 7.0.8 字节码反编译）

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `value` / `includes` | `{}` | 触发重试的异常类型 |
| `excludes` | `{}` | 不触发重试的异常类型 |
| `predicate` | — | 自定义 `MethodRetryPredicate` 断言 |
| `maxRetries` | — | 最大重试次数 |
| `maxRetriesString` | `""` | 字符串形式 maxRetries（支持占位符） |
| `timeout` | `0` | 超时时间（ms） |
| `delay` | `0` | 初始延迟（ms） |
| `jitter` | `0` | 随机抖动（ms），**单位 ms 而非百分比** |
| `multiplier` | `1.0` | 指数退避倍率 |
| `maxDelay` | `0` | 最大延迟（0 = 无限制） |
| `timeUnit` | `MILLISECONDS` | 时间单位 |

### 示例（与项目代码对齐）

```java
@Service
public class ResilientUserService {

    @Retryable(
        includes = TimeoutException.class,
        maxRetries = 3,
        delay = 100,
        multiplier = 2.0,
        jitter = 10
    )
    public UserProfile getUser(Long id) {
        return downstream.fetchUser(id);
    }
}
```

### 指数退避 + jitter 图解

```
Attempt 1:  立即调用
  ↓ TimeoutException
Retry 1:   等待 100 ± 10 ms
  ↓ TimeoutException
Retry 2:   等待 200 ± 10 ms
  ↓ TimeoutException
Retry 3:   等待 400 ± 10 ms
  ↓ 成功
```

### 响应式方法自动适配

```java
@Retryable(
    includes = TimeoutException.class,
    maxRetries = 3,
    delay = 100,
    multiplier = 2.0
)
public Mono<UserProfile> getUserReactive(Long id) {
    return Mono.fromCallable(() -> downstream.fetchUser(id));
}
```

当返回类型为 `Mono`/`Flux` 时，`@Retryable` 自动使用 Reactor 的 `retryWhen` 装饰 pipeline（由 `AbstractRetryInterceptor.ReactorDelegate` 代理）。

### ⚠️ 注意事项

- `@Retryable` 必须由 Spring AOP 代理 → 不能在同一类内调用
- 非 `public` 方法不会被代理
- 响应式方法的 `includes`/`excludes` 作用于信号 `onError` 的异常类型
- `jitter` 单位为 **毫秒**（与 Reactor `Retry.jitter(0.1)` 百分比不同）