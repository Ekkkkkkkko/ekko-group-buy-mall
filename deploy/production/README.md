# 单机 Docker Compose 部署

用于 2 核 4GB 轻量服务器上的单机演示环境：Nginx、market、mall、chat、Redis、RabbitMQ、Elasticsearch 共用一台主机，MySQL 使用外部 RDS。

## 目录

服务器目录为 `/opt/ekko-group-buy`：

```text
artifacts/   三个后端 JAR
web/         Vue 构建产物
logs/        应用日志
certbot/     Let's Encrypt 证书（不得提交 Git）
compose.yml  容器编排
*.env        运行时密钥和连接参数，权限必须为 600
.env         Compose 自身变量，权限必须为 600
```

真实密钥只保存在服务器，不提交到 Git。应用容器不映射端口，公网只开放 Nginx 的 80/443；Redis、RabbitMQ 和 Elasticsearch 仅在 Compose 内部网络访问。域名 HTTP 请求会跳转到 HTTPS，IP 地址的 HTTP 入口仅用于诊断。

复制 `.env.example`、`market.env.example`、`mall.env.example` 和 `chat.env.example` 后替换所有 `__...__` 占位符。真实文件已被根目录 `.gitignore` 排除。

## 资源预算

- market 和 mall：各 `Xmx=512MB`，容器上限 768MB。
- chat：`Xmx=640MB`，容器上限 896MB。
- Elasticsearch：堆 384MB，容器上限 768MB。
- Redis、RabbitMQ、Nginx：容器上限合计 736MB。
- 主机需要 2GB Swap 作为突发保护，但正常运行不应持续使用 Swap。

这是共用 2C4G 的演示型预算，不代表生产容量。若聊天知识库进入高并发或批量索引，应把 Elasticsearch/chat 拆到独立机器或购买云服务。

## 验证

```bash
docker compose ps
curl -fsS http://127.0.0.1/health
curl -fsS https://ekkoliu.com/health
curl -fsS http://127.0.0.1/mall-api/actuator/health
curl -fsS http://127.0.0.1/chat-api/actuator/health
docker compose logs --tail=100 market mall chat
```

证书续期由服务器 `/etc/cron.d/ekko-group-buy-certbot` 每周检查；续期后会自动重载 Nginx。

RDS SSL 未开启时只能暂时使用 `sslMode=PREFERRED`；正式上线必须在 RDS 控制台开启 SSL，再改为 `sslMode=REQUIRED` 并重启三个应用。
