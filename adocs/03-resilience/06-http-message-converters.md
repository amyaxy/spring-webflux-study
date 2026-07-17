# `HttpMessageConverters` — 集中式 Message Converter 配置（真实 API）

> Spring 7.x 新增 `HttpMessageConverters` 类，为 HTTP message converter 提供统一配置入口。

---

## 实际包路径

```
org.springframework.http.converter.HttpMessageConverters
```

## 真实 API（基于 7.0.8 字节码反编译）

```java
public interface HttpMessageConverters extends Iterable<HttpMessageConverter<?>> {
    boolean isEmpty();
    
    // 静态工厂方法（无构造器）
    static ClientBuilder forClient();
    static ServerBuilder forServer();
}
```

## 客户端配置

```java
// 使用 forClient() 静态方法
HttpMessageConverters.ClientBuilder builder = HttpMessageConverters.forClient();
// ... 添加 converter
```

## 服务端配置

```java
// 使用 forServer() 静态方法
HttpMessageConverters.ServerBuilder builder = HttpMessageConverters.forServer();
// ... 添加 converter
```

## 与代码对齐：WebFlux 消息转换

**注意**：`WebFluxConfigurer.configureHttpMessageCodecs()` 的参数为 `ServerCodecConfigurer`，**不是** `HttpMessageConverters.ServerBuilder`。

```java
// WebFluxConfigurer 实际签名（基于 7.0.8 字节码）
public default void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {
    // 不是 HttpMessageConverters.ServerBuilder！
}
```

## 使用场景对比

| 场景 | 推荐方式 |
|------|---------|
| WebFlux 全局编解码配置 | `WebFluxConfigurer.configureHttpMessageCodecs(ServerCodecConfigurer)` |
| WebClient 客户端配置 | `WebClient.builder().codecs()` |
| 独立 converter 管理 | `HttpMessageConverters.forClient()` / `forServer()` |