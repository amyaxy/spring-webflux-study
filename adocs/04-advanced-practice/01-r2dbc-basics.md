# 01 · R2DBC 基础

## 概述

R2DBC（Reactive Relational Database Connectivity）是响应式数据库访问的规范，提供了非阻塞的 SQL 操作能力。

## 核心依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-r2dbc</artifactId>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.r2dbc</groupId>
    <artifactId>r2dbc-h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

## 配置

```yaml
spring:
  r2dbc:
    url: r2dbc:h2:mem:///reactivechat;MODE=MySQL
    name: reactivechat
    username: sa
    password:
  sql:
    init:
      schema-locations: classpath:schema.sql
      mode: always
```

## 实体模型

使用 `@Table` + `@Column` + `@Id` 注解：

```java
@Data
@Table("users")
public class User {
    @Id
    @Column("id")
    private Long id;
    @Column("username")
    private String username;
    @Column("display_name")
    private String displayName;
    @Column("created_at")
    private LocalDateTime createdAt;
}
```

**重要**：所有字段必须添加 `@Column` 显式指定列名，否则 Spring Data R2DBC 的默认 `NamingStrategy` 会将驼峰转为大写蛇形并加引号（如 `"DISPLAY_NAME"`），与 H2 schema 中定义的小写列名不匹配。

## 创建 Repository

```java
@Repository
public interface UserRepository extends R2dbcRepository<User, Long> {
    Mono<User> findByUsername(String username);
}
```

Spring Data R2DBC 会自动实现 `findByUsername` — 方法名解析出 `WHERE username = ?` 查询。

## 数据库初始化：schema.sql

使用 `schema.sql` 建表：

```sql
CREATE TABLE IF NOT EXISTS "users" (
    "id" BIGINT AUTO_INCREMENT PRIMARY KEY,
    "username" VARCHAR(50) NOT NULL UNIQUE,
    "display_name" VARCHAR(100),
    "created_at" TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

**注意**：表名和列名必须加引号，且与 `@Table` / `@Column` 值完全一致。H2 对未引用的标识符默认转为大写，引号内的标识符区分大小写。

## 踩坑记录

### 1. H2 大小写敏感

Spring Data R2DBC 的默认 NamingStrategy 把所有标识符包装成引号大写形式：`username` → `"USERNAME"`。解决方案：

- 所有字段加 `@Column` 显式指定小写列名
- schema.sql 列名加引号用小写
- 或使用 `MODE=MySQL` 让 H2 忽略大小写

### 2. `@Id` 字段也需要 `@Column`

即使 `id` 字段是主键，也要显式 `@Column("id")`，否则默认 NamingStrategy 生成 `"ID"`，而 schema 定义的是 `"id"`，导致 `Column not found` 错误。

### 3. ID 自动生成

H2 使用 `AUTO_INCREMENT`，主键自动生成。数据库中插入后通过 `save` 返回的实体自动携带生成的 ID。