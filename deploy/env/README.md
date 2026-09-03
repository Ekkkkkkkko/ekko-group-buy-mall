# 生产环境变量使用说明

当前采用最简单的服务器 `EnvironmentFile` 方案，不在 Git、JAR 或前端代码中保存真实密钥。

## 2C4G 适用边界

模板按 **market 和 mall 各自独占一台 2 核 4GB 主机** 配置：

| 资源 | market | mall | 说明 |
| --- | ---: | ---: | --- |
| JVM | `Xms=768MB, Xmx=2GB` | `Xms=768MB, Xmx=2GB` | 给 Metaspace、直接内存、线程栈和系统预留约 2GB |
| Tomcat 工作线程 | 100 | 100 | 2 核机器先限制并发入口，避免大量请求同时压向数据库 |
| Hikari 最大连接 | 12 | 12 | 小连接池形成数据库侧背压，后续按等待时间和 RDS 上限调整 |
| 业务线程池 | 4～8，队列 200 | 2～4，队列 100 | market 一次试算包含两个并行查询，mall 当前任务较少 |
| RabbitMQ 消费线程 | 2～4 | 2～4 | prefetch 为 1，优先保证退款和通知处理的可控性 |

如果两个服务共用同一台 2C4G，不能直接使用该模板：建议每个 JVM 先改成 `-Xms512m -Xmx1024m`，Tomcat 最大线程改为 60，Hikari 最大连接改为 8，并通过压测确认不会发生内存交换。

## RDS MySQL 配置

生产环境使用阿里云 RDS 时：

1. 应用服务器与 RDS 放在同地域、同 VPC，JDBC 使用 RDS **内网读写地址**；RDS 白名单只放行应用服务器私网 IP 或对应安全组，不给业务服务使用公网地址。
2. 在 RDS 控制台开启 SSL 后使用模板中的 `sslMode=REQUIRED`，保证无法建立加密连接时直接失败。若已把阿里云 RDS CA 证书配置到 JVM 信任库，可进一步改为 `sslMode=VERIFY_IDENTITY`，同时校验证书和连接主机名。
3. JDBC 驱动连接超时设为 5 秒、网络读取超时设为 30 秒，并开启 TCP keepalive。不要配置 `autoReconnect=true`，连接失效后的重建交给 Hikari 管理。
4. 不因 RDS 支持较大连接数就放大 Hikari。连接数越大，占用的 RDS 内存和并发资源越多；先使用实例规格默认的 `max_connections`，再依据监控和压测调整。

如果三个服务共用同一个 RDS，应用侧理论最大连接数按下面计算：

```text
总连接上限 = market副本数 × MARKET_DB_MAXIMUM_POOL_SIZE
           + mall副本数   × MALL_DB_MAXIMUM_POOL_SIZE
           + chat副本数   × CHAT_DB_MAXIMUM_POOL_SIZE
           + 其他应用连接池
```

当前各 1 个副本时是 `12 + 12 + 10 = 34`。将“所有应用连接池之和不超过 RDS 可用连接数的 70%”作为本项目的保守起点，剩余连接留给 DMS、发布迁移、监控、故障恢复和临时运维；这是容量预留策略，不是阿里云的固定限制。如果扩成各 2 个副本，则仅这三个服务就会变成 68，必须重新核算，不能照搬单机配置。

上线前使用业务账号或只读运维账号检查实际参数和峰值：

```sql
SHOW VARIABLES WHERE Variable_name IN ('max_connections', 'max_user_connections', 'wait_timeout');
SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected', 'Threads_running', 'Max_used_connections');
```

Hikari 的 `max-lifetime=25分钟` 和 `keepalive-time=5分钟` 是初始值；要保证最大生命周期短于 RDS 或中间网络主动断开连接的时间。上线后重点观察 RDS 连接使用率、活跃连接、慢 SQL，以及应用的 Hikari 获取连接等待时间。

## 服务器操作

1. 将对应的 `*.env.example` 复制到服务器 `/etc/ekko/`，并去掉 `.example` 后缀。
2. 填写真实值；同一项密钥不要复制到聊天、邮件或发布日志。
3. 限制文件权限：`chmod 600 /etc/ekko/*.env`。
4. 每个 Java 服务使用独立的 EnvironmentFile，避免三个服务的 `SPRING_DATASOURCE_*` 相互覆盖。
5. 创建 JVM 日志目录并交给运行用户：`install -d -o ekko -g ekko /var/log/ekko/market /var/log/ekko/mall`。

systemd 服务中加入：

```ini
[Service]
User=ekko
EnvironmentFile=/etc/ekko/market.env
ExecStart=/usr/bin/java $JAVA_OPTS -jar /opt/ekko/ekko-group-buy-market-app.jar
```

商城和客服分别换成 `mall.env`、`chat.env` 以及对应 JAR。修改环境变量后需要重启对应服务。

`SPRING_PROFILES_ACTIVE=prod` 是运行时环境选择的唯一入口。Maven 只负责构建 JAR，不再使用 `mvn -Pprod` 控制 Spring Profile，避免“构建参数叫 prod、实际运行却是 release/dev”的错配。

## 规则

- `market.env` 与 `mall.env` 的 `GROUP_BUY_NOTIFY_TOKEN` 必须相同。
- JWT 和内部回调令牌建议使用 `openssl rand -base64 48` 分别生成，不能复用。
- 支付宝、微信、OSS、MinerU、模型服务和数据库的旧凭据应在各自控制台轮换。
- RDS 为 market、mall、chat 分别创建最小权限账号，只授权各自数据库；不要让应用使用高权限管理账号。
- 不要把真实 `.env` 放回项目目录；即使误放，根目录 `.gitignore` 也会忽略 `deploy/env/*.env`。
- 发布后用 `unzip -l app.jar` 确认不存在 `application-dev.yml`、`application-test.yml`、`application-prod.yml`。
