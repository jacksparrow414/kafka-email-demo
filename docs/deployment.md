# 部署与自测

## 1. docker-compose 服务

| 服务 | 镜像 | 说明 |
|---|---|---|
| kafka | apache/kafka:4.2.1 | KRaft 单机 broker+controller。BROKER :9092（容器网络）、EXTERNAL :9093（宿主机，advertised `localhost:9093`）、CONTROLLER :9094（不映射）、JMX exporter :9095、JMX remote :9998（容器网络，供 kafka-ui）。数据卷挂 `/var/lib/kafka/data`（必须此路径，见 AGENTS.md 坑位 3）。heap 2G，容器限 4G |
| kafka-ui | ghcr.io/kafbat/kafka-ui:latest | 9080→8080，路径 `/kafkaui`，admin/kafkauipassword |
| redis | bitnami/redis:latest | 6379，password123，定时任务分布式锁预留 |
| otel-collector | otel/opentelemetry-collector-contrib:0.155.0 | 4317/4318 收 OTLP → prometheus exporter :9464 |
| prometheus | prom/prometheus:latest | 9090，scrape kafka:9095 + otel-collector:9464 |
| grafana | grafana/grafana:13.0.1 | 3000，admin/admin，provision 数据源 + dashboard |

配置文件：`monitoring/`（otel-collector-config.yml、prometheus.yml、client-metrics-reporter-config.yml、grafana/）。

## 2. apache/kafka 官方镜像要点

- 以 uid=1000(appuser) 运行；声明卷 `/etc/kafka/secrets`、`/mnt/shared/config`、`/var/lib/kafka/data`
- 环境变量 `KAFKA_XXX` 自动转 server.properties（如 `KAFKA_METRIC_REPORTERS` → `metric.reporters`）；`KAFKA_LOG_DIRS` 同理
- 首次启动自动 format 存储（日志中 `CLUSTER_ID not set. Setting it to default value`）
- `KAFKA_OPTS=-javaagent:...=9095:...` 与 `JMX_PORT=9998` 会被容器内所有 kafka 命令行工具继承 → **CLI 前必须清空**：
  `docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list'`

## 3. 构建与部署

```bash
# 1) 构建（JDK 17；本机 Maven 在 ~/Downloads/apache-maven-3.9.16）
~/Downloads/apache-maven-3.9.16/bin/mvn clean package -DskipTests

# 2) 基础设施
docker compose up -d

# 3) 部署到 Tomcat 10.1.50（brew 安装）
TH=/usr/local/Cellar/tomcat@10/10.1.50/libexec
cp business-server/target/business-server.war message-server/target/message-server.war $TH/webapps/
JAVA_HOME=$(/usr/libexec/java_home -v 17) $TH/bin/catalina.sh start   # stop 同理

# 4) email topic（10 分区，与 10 个消费线程对应）
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 --create --if-not-exists --topic email --partitions 10 --replication-factor 1'
```

重新部署：先 `catalina.sh stop`，删 `$TH/webapps/{business-server,message-server}*`（war+解压目录），再复制新 war 启动。

## 4. 端到端自测步骤

```bash
# 1. 发消息（HTTP 200 即已异步投递）
curl "http://localhost:8080/business-server/producerMessage?username=tom&password=123456"

# 2. 看日志（catalina.out）
#    "message has sent to topic: email"                        — 生产成功
#    "callback message has sent to topic: callback<hostname>"  — 消费成功且回调已发
#    "email sent success callback invoked"                     — 反射回调执行

# 3. 查 H2（幂等表有 messageId，失败表应为空）
echo | java -cp ~/.m2/repository/com/h2database/h2/2.4.240/h2-2.4.240.jar org.h2.tools.Shell \
  -url "jdbc:h2:file:$HOME/kafka-message-data/message-db;AUTO_SERVER=TRUE" -user sa \
  -sql "select message_id from message_ack_consumes_success; select count(*) from message_failed;"

# 4. 失败重试链路：docker stop kafka → 再发一条 → 等约 2 分钟(delivery.timeout) →
#    message_failed 出现 PRODUCER 记录 → docker start kafka → 等定时任务(≤10min) →
#    日志 "message has resent" 且 retry_status=1
```

kafka-ui：`http://localhost:9080/kafkaui`（admin/kafkauipassword）看 topic/消费组 offset。

## 5. 故障排查

| 现象 | 原因与处理 |
|---|---|
| kafka 容器起不来，`AccessDeniedException: .../bootstrap.checkpoint.tmp` | 数据卷挂错路径（必须 `/var/lib/kafka/data`），删卷重建 |
| 容器内 kafka CLI 报 `Address in use` 9095/9998 | KAFKA_OPTS/JMX_PORT 泄漏进 CLI，清环境变量再执行 |
| `FencedInstanceIdException` | 不同业务消费者共用了 groupId；用 `loadConsumerConfig(..., groupId)` 隔离 |
| 消费线程无日志、不消费 | 检查是否踩了空 poll `return` 的老 bug；现为 `continue` |
| H2 `Database may be already in use` | 连接串漏了 `AUTO_SERVER=TRUE` |
| 两个 webapp 都连不上 kafka | 检查 `KAFKA_BOOTSTRAP_SERVERS` 与 compose EXTERNAL advertised 是否一致（默认 localhost:9093） |
