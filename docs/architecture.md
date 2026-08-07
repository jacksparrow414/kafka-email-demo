# 架构与设计思想

> 对应作者 CSDN Kafka 系列博客（二）~（五），本文档固化为代码视角的最终实现。

## 1. 设计目标与思想

把"发邮件"从业务系统解耦：业务系统不直接发邮件、不 RPC 调用邮件系统，而是把**组装好的完整数据 + 模板**序列化为 JSON 投递到 Kafka；邮件系统消费后直接渲染发送。

关键决策（来自博客第二篇）：

- **消息携带全量数据而非 id**：避免邮件系统回查数据库造成压力；避免按场景适配带来的维护与滚动部署兼容问题。消息最终落地为与业务类无关的 JSON（博客中是 Map，demo 中为统一 DTO）
- **消息 key = messageId**：Kafka 按 hash(key) 分区，同一消息无论重试/重投多少次都进同一分区，这是消费端幂等设计的前提
- **最终一致、消息不丢**：生产失败重试 N 次 → 入库 → 定时任务重投；消费失败重试 N 次 → 入库 → 定时任务重投
- **可靠性靠副本机制而非刷盘**（官方立场）：acks=all + min.insync.replicas，不设置 log.flush 参数
- **static membership 避免滚动重启引发的 rebalance/重复消费/丢消息**：`group.instance.id` + 调大 `heartbeat.interval.ms`(60s) / `session.timeout.ms`(5min)

## 2. 模块与类级实现

### 2.1 message-common（共享层）

| 类 | 说明 |
|---|---|
| `KafkaConfiguration` | 生产者/消费者配置工厂。bootstrap 走 `KAFKA_BOOTSTRAP_SERVERS`（默认 localhost:9093）；`SERVER_ID`=hostname 作 client.id 前缀防 `InstanceAlreadyExistsException`。生产者：LZ4、batch.size=10MB、max.request.size=10MB、linger.ms=10（邮件约 20KB/条，默认值会导致逐条发送）。消费者：static member、关闭自动提交、max.partition.fetch.bytes=10MB、fetch.max.bytes=100MB、max.poll.records=500。`loadConsumerConfig(instanceId, valueType, groupId)` 三参版本是后来加的，回调消费必须用独立 groupId |
| `UserDTO` | email 消息体：messageId/userName/password/callbackMetaData。Jackson + Lombok Builder |
| `CallbackMetaData` | 回调消息体：messageId/serverId/className/instanceJsonStr/methodName/arguments |
| `MessageFailedEntity` | 失败消息表实体（message_type: EMAIL/EMAIL_CALLBACK；failed_phase: PRODUCER/CONSUMER；retry_count<3、retry_status 0=待重试 1=已成功） |
| `MessageAckConsumesSuccessEntity` | 幂等表实体，仅 messageId 一列 |
| `MessageFailedService` | 失败表的 CRUD（真实 JDBC）。`saveOrUpdate`（无则插入，有则 retry_count+1）；`markRetrySuccessIfExists`（重试成功时置 1，无记录则不动） |
| `MessageAckConsumesSuccessService` | 幂等表：批量 IN 查询 + `MERGE INTO ... KEY(message_id)` 批量插入 |
| `DbUtil` | H2 工具类。`AUTO_SERVER=TRUE` 是关键：两个 war 在同一 Tomcat 但 classloader 独立，各自打开内嵌 H2 会文件锁冲突，AUTO_SERVER 让后开者通过 TCP 接入先开者。首次连接执行 DDL 建表 |
| 枚举 | `MessageType`(EMAIL/EMAIL_CALLBACK)、`MessageFailedPhase`(PRODUCER/CONSUMER) |

### 2.2 business-server（业务系统）

| 类 | 说明 |
|---|---|
| `ProducerServlet` | `/producerMessage?username=&password=` 入口；构造 UserDTO（messageId=UUID）+ 演示用 CallbackMetaData（回调 `EmailSuccessCallback.onSuccess`） |
| `MessageProducer` | 静态 `KafkaProducer<String, UserDTO>` 发 `email` topic。异步发送 + callback：失败（最后一次重试后才回调）→ 写 message_failed(PRODUCER)；成功仅打日志 |
| `CallbackConsumerRunner` | 消费 `callback<hostname>`（**与生产端按服务器一一对应**），poll 循环内反射：`Class.forName` → Jackson 还原实例 → `MethodUtils.invokeMethod`。**消费组 = `callback`**（与 email 消费的 `test` 组隔离，否则 static instance.id 冲突被 fence） |
| `EmailSuccessCallback` | 演示回调目标类，onSuccess() 打日志 |
| `KafkaListener` | 应用启动注册 producer，销毁时先跑消费者 shutdown hooks 再关 producer |
| `StartUpCallbackConsumerListener` | Tomcat 启动时拉起回调消费线程（1 个，回调量小） |

### 2.3 message-server（消息系统）

| 类 | 说明 |
|---|---|
| `MessageConsumerRunner` | 10 个消费线程（与 email 10 分区对应）消费 `email`。流程：批内+库双重幂等检查 → Failsafe 重试 2 次（间隔 200ms）→ 成功：messageId 入幂等表 + 有 callbackMetaData 则发回调；失败：写 message_failed(CONSUMER)。后置处理在独立线程不阻塞 poll。平时 commitAsync，关闭时 wakeup → commitSync |
| `CallbackProducer` | 发 `callback<hostname>`；失败写 message_failed；成功仅 `markRetrySuccessIfExists`（首次成功不产生任何记录） |
| `MessageFailedProducer` | 定时任务重投 email 消息：topic=`email`、key=messageId（与首发一致）；成功 markRetrySuccessIfExists，失败 saveOrUpdate |
| `ReProduceFailedMessageTask` | 扫 message_failed 中 retry_status=0 且 retry_count<3 的记录，按消息类型分发重投（EMAIL→MessageFailedProducer，EMAIL_CALLBACK→CallbackProducer）。多台部署时应加 Redis 分布式锁（compose 里 redis 的用途） |
| `StartUpConsumerListener` / `ScheduleTaskListener` / `KafkaListener` | 启动 10 消费线程 / 定时任务（**scheduleWithFixedDelay**，首次 1 分钟后每 10 分钟）/ 生命周期管理 |

## 3. 消息流

### 3.1 正常链路
```
用户 → ProducerServlet → [email topic] → MessageConsumerRunner(幂等检查→重试→成功)
    → messageId 入 message_ack_consumes_success
    → CallbackProducer → [callback<hostname> topic] → CallbackConsumerRunner → 反射调用业务回调
```

### 3.2 失败链路
```
生产失败(最后一次重试后) / 消费失败(重试2次后) → message_failed 表
    → ReProduceFailedMessageTask(每10min) → 重投 → 成功: retry_status=1；失败: retry_count+1(≥3 不再重试)
```

## 4. 数据库表（H2）

```sql
message_failed(id, message_id, message_content_json_format, message_type,
               failed_phase, failed_reason, retry_count, retry_status, last_update_time)
message_ack_consumes_success(message_id PK)   -- 幂等表
```
默认库文件：`~/kafka-message-data/message-db`；可用 `java -cp h2.jar org.h2.tools.Shell -url "jdbc:h2:file:...;AUTO_SERVER=TRUE" -user sa` 查询。

## 5. Topic 清单

| topic | 分区 | 生产者 | 消费者(组) |
|---|---|---|---|
| email | 10 | business-server / message-server(重投) | message-server × 10（组 `test`，static member test-1..10） |
| callback\<hostname\> | 自动创建 | message-server | business-server × 1（组 `callback`） |

## 6. 已知 demo 简化（非缺陷，按博客意图保留）

- 消费业务逻辑为 no-op（`.get(() -> true)`）；`CallbackConsumerRunner` 的失败持久化博客注明"自己完成"
- 邮件服务商交互、webhook 不在本仓库范围
- 定时任务未实现 Redis 分布式锁（单机 demo）
