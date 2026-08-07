# AGENTS.md

Kafka 消息系统示例项目：用 Kafka 将"发邮件"从业务系统解耦。原生 Servlet(Jakarta EE 6) + kafka-clients，无 Spring。设计思想见 `docs/architecture.md`（与作者 CSDN Kafka 系列五篇博客对应）。

## 技术栈与版本（2026-08 重构后）

- JDK 17（Temurin，`/usr/libexec/java_home -v 17`）
- Kafka 4.2.1：官方 `apache/kafka:4.2.1` 镜像（KRaft 单机）+ `kafka-clients:4.2.1`
- 序列化：`io.confluent:kafka-json-serializer:7.5.1`（与 4.2.1 客户端实测兼容）
- 持久层：内嵌 H2 2.4.240（`AUTO_SERVER=TRUE` 模式，原因见 docs/architecture.md）
- 监控：KIP-714 客户端遥测 → instaclustr client-metrics-reporter 插件 → otel-collector → Prometheus → Grafana（见 docs/monitoring.md）
- 构建：Maven（本机在 `~/Downloads/apache-maven-3.9.16/bin/mvn`，无全局 mvn）
- 运行：Tomcat 10.1.50（brew 安装于 `/usr/local/Cellar/tomcat@10/10.1.50/libexec`）

## 模块

| 模块 | 打包 | 职责 |
|---|---|---|
| message-common | jar | Kafka 配置工厂、消息 DTO、失败/幂等实体、H2 持久层、枚举 |
| business-server | war | 业务系统：生产 email 消息、消费自己专属的 callback topic（反射回调） |
| message-server | war | 消息系统：消费 email、发送回调、定时重试失败消息 |

根 pom `<modules>` 含全部三个模块（无 profile  trick）。`message-consumer` 目录已删除，不要重建。

## 常用命令

```bash
# 构建（JAVA_HOME 指向 17）
~/Downloads/apache-maven-3.9.16/bin/mvn clean package -DskipTests

# 基础设施（kafka/kafka-ui/redis/otel-collector/prometheus/grafana）
docker compose up -d

# 部署
cp business-server/target/business-server.war message-server/target/message-server.war \
  /usr/local/Cellar/tomcat@10/10.1.50/libexec/webapps/
JAVA_HOME=$(/usr/libexec/java_home -v 17) /usr/local/Cellar/tomcat@10/10.1.50/libexec/bin/catalina.sh start

# 发消息自测
curl "http://localhost:8080/business-server/producerMessage?username=t&password=t"
```

## 关键配置项

- `KAFKA_BOOTSTRAP_SERVERS`（系统属性或环境变量）：默认 `localhost:9093`，对应 compose 的 EXTERNAL 监听器
- `kafka.message.db.url`（系统属性）：H2 连接串，默认 `jdbc:h2:file:~/kafka-message-data/message-db;AUTO_SERVER=TRUE`

## 端口约定

8080 Tomcat / 9080 kafka-ui(kafbat) / 9092 kafka BROKER(容器网络) / 9093 kafka EXTERNAL(宿主机) / 9095 JMX exporter / 9090 Prometheus / 3000 Grafana(admin/admin) / 6379 redis

## 环境坑位（改动前必读）

1. **网络慢/失败走代理**：`export http_proxy=http://127.0.0.1:7897 https_proxy=http://127.0.0.1:7897`（来自 ~/.zshrc `proxy_on`）。docker pull 走 Docker Desktop 的 system 代理。
2. **容器内跑 kafka CLI 必须清空 JMX 相关环境变量**，否则端口冲突（broker 已占用 9095/9998）：
   `docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= /opt/kafka/bin/kafka-topics.sh ...'`
3. apache/kafka 镜像以 uid=1000(appuser) 运行；数据卷必须挂到镜像声明的 `/var/lib/kafka/data`（首次挂载会继承镜像内目录属主），挂其他路径会 root 属主导致启动失败。
4. 两个 war 在同一 Tomcat 但 classloader 独立 → H2 必须 `AUTO_SERVER=TRUE`，否则文件锁冲突。
5. `enable.metrics.push` 客户端默认 true；但**已运行的客户端不会立刻感知新建订阅**，要等其重新拉取订阅（或重启应用）才有遥测数据。
6. Grafana 数据源 provisioning 一旦入库，改 uid 会因旧记录冲突启动失败 → 删 `kafka_grafana_data` 卷重建。

## 代码约定

- Lombok `@Log`(java.util.logging)；注释用中文、javadoc 风格；消息 key 一律用 `messageId`（保证同消息同分区）
- 消费者 static membership：`group.instance.id=<groupId>-<序号>`，不同业务消费者用不同 groupId，否则 `FencedInstanceIdException`
- offset：平时 `commitAsync`，关闭时 `commitSync`；消费逻辑必须 catch 住所有异常，不能让消费线程退出

## 文档

- `docs/architecture.md` — 设计思想、模块与类级实现细节、消息流、表结构
- `docs/deployment.md` — 基础设施、构建部署、自测步骤、故障排查
- `docs/monitoring.md` — KIP-714 监控链路、插件、订阅、指标与 dashboard
