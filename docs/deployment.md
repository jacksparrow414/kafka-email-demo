# 部署与自测

## 1. docker-compose 服务

| 服务 | 镜像 | 说明 |
|---|---|---|
| kafka | apache/kafka:4.2.1 | KRaft 单机 broker+controller。BROKER :9092（容器网络）、EXTERNAL :9093（宿主机，advertised `localhost:9093`）、CONTROLLER :9094（不映射）、JMX exporter :9095、JMX remote :9998（容器网络，供 kafka-ui）。数据卷挂 `/var/lib/kafka/data`（必须此路径，见 AGENTS.md 坑位 3）。heap 2G，容器限 4G |
| apicurio-registry | apicurio/apicurio-registry:3.3.1 | Schema Registry API，8081→8080。kafkasql 存储（schema 存 Kafka 的 kafkasql-journal topic）；REST API `/apis/registry/v3`；已开 CORS（供 UI 跨域） |
| apicurio-registry-ui | apicurio/apicurio-registry-ui:3.3.1 | web console（3.x 起独立于 API 容器），8082→8080，`REGISTRY_API_URL=http://localhost:8081/apis/registry/v3`（SPA 在浏览器里跑，必须指浏览器可达地址） |
| kafka-ui | ghcr.io/kafbat/kafka-ui:latest | 9080→8080，路径 `/kafkaui`，admin/adb-1234。已配 `SCHEMAREGISTRY=http://apicurio-registry:8080/apis/ccompat/v7` + `DEFAULTVALUESERDE=SchemaRegistry`，消息列表自动按 Avro 解码显示 |
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
#    注意: 不跳过测试时(-DskipTests 不加), message-common 在 test 阶段会用 apicurio-registry-maven-plugin
#    以 dryRun 方式对 registry 中已发布版本做 BACKWARD 兼容性校验, 不兼容的 schema 改动直接构建失败;
#    需先执行步骤2启动 registry 并执行2.1创建规则. registry 不可用时加 -DskipRegister=true 跳过校验
~/Downloads/apache-maven-3.9.16/bin/mvn clean package -DskipTests

# 2) 基础设施
docker compose up -d

# 2.1) 首次部署(或重建kafka_data卷)后, 为 registry 创建全局 BACKWARD 兼容性规则(一次性, 幂等).
#      message-common 构建期的 schema 兼容性校验(apicurio-registry-maven-plugin, 见下文)依赖此规则,
#      不创建则校验永远通过、形同虚设
curl -X POST "http://localhost:8081/apis/registry/v3/admin/rules" \
  -H "Content-Type: application/json" -d '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}'

# 3) 部署到 Tomcat 10.1.57（brew 已卸载，现为 ~/tools 下的解压版）
TH=~/tools/apache-tomcat-10.1.57
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

kafka-ui：`http://localhost:9080/kafkaui`（admin/adb-1234）看 topic/消费组 offset；Topics → email/callback → Messages 中 value 自动按 Avro 反序列化显示（union 字段呈现为 `{"string": "v"}` 包装形式属正常）。

Apicurio Registry web console：`http://localhost:8082`，Dashboard/Explore 中 default group 下可见 `email-value`（UserDTO schema，通过 reference 引用 CallbackMetaData）、`callback<hostname>-value`、`com.message.common.dto.CallbackMetaData` 三个 AVRO artifact，点进版本的 Content 标签页可看 schema 全文。命令行验证：

```bash
curl -s http://localhost:8081/apis/registry/v3/search/artifacts   # 列出已注册 artifact
```

## 5. 常用命令速查

以下命令均已实测可用。

### 5.1 生产消息

```bash
# 调用 business-server 的 producer 端点, 产生一条 email 消息(HTTP 200 即已异步投递)
curl "http://localhost:8080/business-server/producerMessage?username=tom&password=123456"
```

### 5.2 Schema Registry（Apicurio REST API）

正常使用时 schema 由生产端首发消息自动注册（`auto-register=true`），以下命令用于手动注册/查看/排障。
注册请求体用 jq 从 .avsc 文件构造，避免手写 JSON 转义：

```bash
# 注册无引用的 schema(以 CallbackMetaData 为例; group=default, artifactId 自定义)
jq -n --arg c "$(cat message-common/src/main/avro/CallbackMetaData.avsc)" \
  '{artifactId:"demo-callback-value", artifactType:"AVRO",
    firstVersion:{content:{content:$c, contentType:"application/json", references:[]}}}' \
| curl -s -X POST "http://localhost:8081/apis/registry/v3/groups/default/artifacts" \
    -H "Content-Type: application/json" -d @-

# 注册带引用的 schema(以 UserDTO 为例):
# references[].name 必须与 UserDTO.avsc 中的具名引用(com.message.common.dto.CallbackMetaData)一致,
# groupId/artifactId/version 指向 registry 中已存在的引用版本
jq -n --arg c "$(cat message-common/src/main/avro/UserDTO.avsc)" \
  '{artifactId:"demo-email-value", artifactType:"AVRO",
    firstVersion:{content:{content:$c, contentType:"application/json",
      references:[{name:"com.message.common.dto.CallbackMetaData", groupId:"default",
                   artifactId:"com.message.common.dto.CallbackMetaData", version:"1"}]}}}' \
| curl -s -X POST "http://localhost:8081/apis/registry/v3/groups/default/artifacts" \
    -H "Content-Type: application/json" -d @-

# 查看 artifact 的版本列表与某版本内容
curl -s "http://localhost:8081/apis/registry/v3/groups/default/artifacts/email-value/versions"
curl -s "http://localhost:8081/apis/registry/v3/groups/default/artifacts/email-value/versions/1/content"

# 通过 ccompat 端点查看 subject 列表与某版本(含 references)
curl -s http://localhost:8081/apis/ccompat/v7/subjects
curl -s "http://localhost:8081/apis/ccompat/v7/subjects/email-value/versions/1"

# 删除 artifact(compose 已开 APICURIO_REST_DELETION_ARTIFACT_ENABLED; 空 group 需单独删)
curl -s -X DELETE "http://localhost:8081/apis/registry/v3/groups/default/artifacts/demo-email-value"
curl -s -X DELETE "http://localhost:8081/apis/registry/v3/groups/default/artifacts/demo-callback-value"
```

### 5.3 Kafka CLI（topics 与 consumer groups）

容器内运行 kafka CLI 必须清空 JMX 相关环境变量（否则与 broker 已占用的 9095/9998 端口冲突，见 AGENTS.md 坑位 2）：

```bash
# 列出全部 topic
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list'

# 查看 topic 详情(分区/副本/ISR)
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic email'

# 列出全部消费者组(apicurio-* 前缀的是 registry 的 kafkasql 存储内部组, 属正常)
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --list'

# 查看某消费者组的 offset/LAG/成员(email 业务消费组为 test, 回调消费组为 callback)
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= \
  /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group test'
```

## 6. 故障排查

| 现象 | 原因与处理 |
|---|---|
| kafka 容器起不来，`AccessDeniedException: .../bootstrap.checkpoint.tmp` | 数据卷挂错路径（必须 `/var/lib/kafka/data`），删卷重建 |
| 容器内 kafka CLI 报 `Address in use` 9095/9998 | KAFKA_OPTS/JMX_PORT 泄漏进 CLI，清环境变量再执行 |
| `FencedInstanceIdException` | 不同业务消费者共用了 groupId；用 `loadConsumerConfig(..., groupId)` 隔离 |
| 消费线程无日志、不消费 | 检查是否踩了空 poll `return` 的老 bug；现为 `continue` |
| H2 `Database may be already in use` | 连接串漏了 `AUTO_SERVER=TRUE` |
| 两个 webapp 都连不上 kafka | 检查 `KAFKA_BOOTSTRAP_SERVERS` 与 compose EXTERNAL advertised 是否一致（默认 localhost:9093） |
| apicurio-registry 首次启动退出，`UnknownTopicOrPartitionException` | kafkasql topic 刚建好时的竞态，compose 已配 `restart: on-failure` 自愈 |
| 浏览器打开 8081 只有 API 文档页、/ui 404 | 3.x 的 web console 在独立容器，访问 http://localhost:8082 |
| web console 打开了但一直转圈/报网络错误 | `REGISTRY_API_URL` 必须是浏览器可达地址（localhost:8081），且 API 侧已开 CORS |
| 应用日志报连不上 registry | 检查 `APICURIO_REGISTRY_URL`（默认 http://localhost:8081/apis/registry/v3）与 8081 端口映射 |
| 重部署后 catalina.out 有 ThreadLocal 泄漏警告（netty/avro） | Apicurio client(Vert.x/netty) 与 avro 的已知现象，仅热重部署时出现，功能无影响 |
| kafka-ui 消息列表 value 是原始字节而非 JSON | 确认 `KAFKA_CLUSTERS_0_SCHEMAREGISTRY` 指向 `http://apicurio-registry:8080/apis/ccompat/v7`；主 schema 按消息内嵌 contentId 全局查询，但**引用按裸 subject 名只查 default group**——若 schema 带引用且被注册到了自定义 group，引用解析失败就会显示原始字节（保持 TopicIdStrategy + default group 即可避免）；serde 客户端与 kafbat 均有缓存，重启 kafka-ui 后刷新 |
| `catalina.sh stop` 后进程不死、越起越多 | 应用的线程池（core=10 非 daemon 核心线程）阻止 JVM 退出，stop 只关连接器。再起前 `pgrep -f "apache-tomcat-10.1.57"` 确认，残留则 kill |
| 重建 topic 后消费组分配卡死（成员 0 分区、不消费） | 组协调器对按 topicId 记的分配有残留状态：先停应用，等幻影成员过期（session.timeout 5min），`kafka-consumer-groups.sh --delete --group <组名>` 删组，再启动 |
| 构建报 `Exception while registering artifact [default] / [email-value]`，含 `RuleViolationException / Incompatible artifact` | schema 兼容性校验拦截：改动与 registry 已发布版本不兼容（BACKWARD）。修正 schema（只加有默认值的可空字段），或确认无误后联系变更已发布版本 |
| 构建报 registry 连接失败（apicurio-registry-maven-plugin） | registry 未启动：先 `docker compose up -d apicurio-registry`；确认无需校验时加 `-DskipRegister=true` |
