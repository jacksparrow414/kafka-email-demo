# AGENTS.md

Kafka 消息系统示例项目：用 Kafka 将"发邮件"从业务系统解耦。原生 Servlet(Jakarta EE 6) + kafka-clients，无 Spring。设计思想见 `docs/architecture.md`（与作者 CSDN Kafka 系列五篇博客对应）。

## 技术栈与版本（2026-08 重构后）

- JDK 17（Temurin，`/usr/libexec/java_home -v 17`）
- Kafka 4.2.1：官方 `apache/kafka:4.2.1` 镜像（KRaft 单机）+ `kafka-clients:4.2.1`
- Schema Registry：Apicurio Registry 3.3.1（kafkasql 存储，schema 存 Kafka 的 `kafkasql-journal` topic）；schema 语言 Avro；客户端 `io.apicurio:apicurio-registry-avro-serde-kafka:3.3.1`（与 4.2.1 客户端实测兼容）
- 消息 DTO：不再手写，由 `message-common/src/main/avro/*.avsc` 经 avro-maven-plugin 1.12.1 生成 SpecificRecord
- 持久层：内嵌 H2 2.4.240（`AUTO_SERVER=TRUE` 模式，原因见 docs/architecture.md）
- 监控：KIP-714 客户端遥测 → instaclustr client-metrics-reporter 插件 → otel-collector → Prometheus → Grafana（见 docs/monitoring.md）
- 构建：Maven（本机在 `~/Downloads/apache-maven-3.9.16/bin/mvn`，无全局 mvn）
- 运行：Tomcat 10.1.57（brew 已卸载，解压版在 `~/tools/apache-tomcat-10.1.57`）

## 模块

| 模块 | 打包 | 职责 |
|---|---|---|
| message-common | jar | Avro schema（src/main/avro）、Kafka 配置工厂、失败/幂等实体、H2 持久层、枚举、AvroJsonUtil |
| business-server | war | 业务系统：生产 email 消息、消费自己专属的 callback topic（反射回调） |
| message-server | war | 消息系统：消费 email、发送回调、定时重试失败消息 |

根 pom `<modules>` 含全部三个模块（无 profile  trick）。`message-consumer` 目录已删除，不要重建。

## 常用命令

```bash
# 构建（JAVA_HOME 指向 17）。注意: -DskipTests 不会跳过 test 阶段的 schema 兼容性校验
# (apicurio-registry-maven-plugin, 需 registry 在线且已建全局 BACKWARD 规则); 跳过校验用 -DskipRegister=true
~/Downloads/apache-maven-3.9.16/bin/mvn clean package -DskipTests

# 基础设施: 按需只启动相关组件, 不要默认全量起; 仅确有必要(如完整验证监控链路)时才 docker compose up -d 全量启动
docker compose up -d kafka apicurio-registry           # 最小集: 构建(schema校验需registry在线)/消息链路自测; depends_on 会自动拉起依赖
docker compose up -d kafka apicurio-registry kafka-ui  # 需要用 kafbat 查看消息时
docker compose up -d apicurio-registry-ui              # 需要 registry web console(8082) 时
docker compose up -d grafana                           # 监控链路: depends_on 链式拉起 prometheus/otel-collector/kafka

# 部署
cp business-server/target/business-server.war message-server/target/message-server.war \
  ~/tools/apache-tomcat-10.1.57/webapps/
JAVA_HOME=$(/usr/libexec/java_home -v 17) ~/tools/apache-tomcat-10.1.57/bin/catalina.sh start

# 发消息自测
curl "http://localhost:8080/business-server/producerMessage?username=t&password=t"
```

## 关键配置项

- `KAFKA_BOOTSTRAP_SERVERS`（系统属性或环境变量）：默认 `localhost:9093`，对应 compose 的 EXTERNAL 监听器
- `APICURIO_REGISTRY_URL`（系统属性或环境变量）：默认 `http://localhost:8081/apis/registry/v3`，对应 compose 的 apicurio-registry 宿主机端口
- `kafka.message.db.url`（系统属性）：H2 连接串，默认 `jdbc:h2:file:~/kafka-message-data/message-db;AUTO_SERVER=TRUE`

## 端口约定

8080 Tomcat / 8081 Apicurio Registry API / 8082 Apicurio Registry web console / 9080 kafka-ui(kafbat) / 9092 kafka BROKER(容器网络) / 9093 kafka EXTERNAL(宿主机) / 9095 JMX exporter / 9090 Prometheus / 3000 Grafana(admin/admin)

## 环境坑位（改动前必读）

1. **网络慢/失败走代理**：`export http_proxy=http://127.0.0.1:7897 https_proxy=http://127.0.0.1:7897`（来自 ~/.zshrc `proxy_on`）。docker pull 走 Docker Desktop 的 system 代理。
2. **容器内跑 kafka CLI 必须清空 JMX 相关环境变量**，否则端口冲突（broker 已占用 9095/9998）：
   `docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= /opt/kafka/bin/kafka-topics.sh ...'`
3. apache/kafka 镜像以 uid=1000(appuser) 运行；数据卷必须挂到镜像声明的 `/var/lib/kafka/data`（首次挂载会继承镜像内目录属主），挂其他路径会 root 属主导致启动失败。
4. 两个 war 在同一 Tomcat 但 classloader 独立 → H2 必须 `AUTO_SERVER=TRUE`，否则文件锁冲突。
5. `enable.metrics.push` 客户端默认 true；但**已运行的客户端不会立刻感知新建订阅**，要等其重新拉取订阅（或重启应用）才有遥测数据。
6. Grafana 数据源 provisioning 一旦入库，改 uid 会因旧记录冲突启动失败 → 删 `kafka_grafana_data` 卷重建。
7. **apicurio-registry 首次启动可能因 kafkasql topic 刚建好而退出**（`UnknownTopicOrPartitionException`），compose 已配 `restart: on-failure` 自愈。
8. **Apicurio 3.x 的 web console 是独立容器** `apicurio-registry-ui`（8082），API 容器（8081）本身只提供 REST API（`/` 会 302 到 API 文档页，/ui 404 属正常）；UI 的 `REGISTRY_API_URL` 必须是**浏览器可达**的地址，API 侧必须开 `QUARKUS_HTTP_CORS_ORIGINS=*`。
9. message_failed 表的 `message_content_json_format` 是 **Avro 原生 JSON**（union 字段形如 `{"string": "v"}`），只能用 `AvroJsonUtil` 读写，与旧 Jackson 格式不兼容。
10. **kafbat kafka-ui 已接入 registry**（`KAFKA_CLUSTERS_0_SCHEMAREGISTRY=http://apicurio-registry:8080/apis/ccompat/v7` + `KAFKA_CLUSTERS_0_DEFAULTVALUESERDE=SchemaRegistry`）。反序列化机制（v1.5.0 实测）：**主 schema 按消息内嵌的 4 字节 contentId 调 `ccompat /schemas/ids/{id}` 获取**（contentId 全局唯一，不受 group 限制）；**引用则按 references 里的裸 subject 名（不带 group 前缀的 artifactId）调 `/subjects/{subject}/versions/{n}` 解析，只命中 default group**——自定义 group 注册带引用的 schema 时，若 default group 没有同名引用 artifact，引用解析失败，消息列表显示原始字节而非 JSON（kafbat 不报错，静默 fallback）。所以 artifact 命名/group 不要自定义，保持 TopicIdStrategy + default group。**`APICURIO_CCOMPAT_GROUP_CONCAT_ENABLED=true` 不能解决此问题，反而破坏一切（两轮实测）**：该配置的官方语义是「让**主动**构造 `groupId:artifactId` 形式 subject 的 Confluent 客户端能访问自定义 group」（实测 `testgroup2:email-cg2-test-value` 带前缀查询确实 200），但 kafbat 反序列化是**被动**按消息内嵌元数据解析，无处构造前缀：references.subject 是 serializer 注册时写入存储的静态值，concat 对存量/新注册数据都不做渲染（concat 开启状态下新注册的 schema，`/schemas/ids/{id}` 返回的 references.subject 仍是裸名）；且开启后裸名 subject 查询直接 HTTP 400、default group 的 `:artifact` 拼接形式也 400——**任何带引用的消息（不论哪个 group）在 kafbat 全部显示原始字节**。不要开。

## 代码约定

- Lombok `@Log`(java.util.logging)；注释用中文、javadoc 风格；消息 key 一律用 `messageId`（保证同消息同分区）
- 消息 DTO（UserDTO/CallbackMetaData）由 .avsc 生成，**不要手改 target 下的生成类**；改格式先改 `message-common/src/main/avro/*.avsc`（schema 演进注意兼容性：只加有默认值的可空字段）
- registry 中的 artifact 命名：默认 `TopicIdStrategy`（group=default，artifactId=`<topic>-value`），UserDTO 的嵌套 record 注册为引用 artifact `com.message.common.dto.CallbackMetaData`。**不要改成自定义 group 或按 record 命名**——kafbat 解析引用时按裸 subject 名只查 default group（见坑位 10）
- Kafka/Apicurio 配置项一律用官方常量（Kafka 用 `ProducerConfig`/`ConsumerConfig`，Apicurio 用 `io.apicurio.registry.resolver.config.SchemaResolverConfig` / `io.apicurio.registry.serde.avro.AvroSerdeConfig`），不要手写配置字符串
- schema 演进由 `apicurio-registry-maven-plugin` 在 message-common 的 test 阶段兜底：`register` goal + `dryRun=true`（不落库），以 registry 中已发布版本为准执行全局 BACKWARD 规则校验，不兼容直接构建失败。**前提：registry 已启动且已创建全局 BACKWARD 规则**（`POST /admin/rules`，见 docs/deployment.md 步骤2.1，否则无规则可校验、永远通过）；registry 不可用时 `-DskipRegister=true` 跳过。注意插件实现里 dryRun 仅当 artifact 配了 `ifExists` 才真正生效（已配 `FIND_OR_CREATE_VERSION`）；UserDTO 的引用指向 registry 已有版本的 `com.message.common.dto.CallbackMetaData`，该 artifact 演进出新版本后需同步 pom 中 reference 的 `version`
- 消费者 static membership：`group.instance.id=<groupId>-<序号>`，不同业务消费者用不同 groupId，否则 `FencedInstanceIdException`
- offset：平时 `commitAsync`，关闭时 `commitSync`；消费逻辑必须 catch 住所有异常，不能让消费线程退出

## 文档

- `docs/architecture.md` — 设计思想、模块与类级实现细节、消息流、表结构
- `docs/deployment.md` — 基础设施、构建部署、自测步骤、故障排查
- `docs/monitoring.md` — KIP-714 监控链路、插件、订阅、指标与 dashboard
