# 监控：KIP-714 客户端遥测 + Broker JMX

## 1. 整体链路

```
producer/consumer (kafka-clients 4.2.1, KIP-714 原生推送, enable.metrics.push 默认 true)
    → broker (PushTelemetry, 订阅由 kafka-client-metrics.sh 配置)
    → instaclustr client-metrics-reporter 插件 (OTLP/HTTP 转发)
    → otel-collector (:4318 接收 → :9464 prometheus exporter)
    → Prometheus (:9090)
    → Grafana (:3000, dashboard "Kafka 客户端遥测(KIP-714) + Broker 总览")

broker 自身 JMX 指标: jmx_prometheus_javaagent 1.6.0 (:9095) → Prometheus → 同一 dashboard
```

## 2. broker 端插件（instaclustr/apache-kafka-client-metrics-reporter-plugin）

- 版本：**v1.1.0**（适配 Kafka 4.2.x 的 KIP-1217 `ClientTelemetryExporter` 接口；v1.2.0 要求 4.3.1，v1.0.4 适配 ≤4.1）
- release 无预编译 jar，需源码构建：
  ```bash
  git clone --depth 1 --branch v1.1.0 \
    https://github.com/instaclustr/apache-kafka-client-metrics-reporter-plugin
  cd apache-kafka-client-metrics-reporter-plugin && mvn clean package -DskipTests
  # 产物: target/apache-kafka-client-metrics-reporter-plugin-1.1.0-kafka-4.2.1-jar-with-dependencies.jar
  ```
- 部署：jar 挂载到 `/opt/kafka/libs/client-metrics-reporter.jar`（compose 中 source 为 `monitoring/client-metrics-reporter.jar`，**未入库 git**，需按上面步骤重建）
- broker 配置（compose 环境变量）：`KAFKA_METRIC_REPORTERS=com.instaclustr.kafka.KafkaClientMetricsReporter`、`KAFKA_CLIENT_METRICS_CONFIG_PATH=/mnt/shared/config/client-metrics-reporter-config.yml`
- 插件配置 `monitoring/client-metrics-reporter-config.yml`：HTTP 模式指向 `http://otel-collector:4318/v1/metrics`，metadata(nodeId/env) 会附加到每条指标的 label

## 3. 客户端订阅（生产级 SRE 视角的关键点）

订阅决定"哪些客户端、推送哪些指标、间隔多少"，broker 侧统一管理、客户端零改动：

```bash
docker exec kafka bash -c 'KAFKA_OPTS= JMX_PORT= KAFKA_JMX_OPTS= /opt/kafka/bin/kafka-client-metrics.sh \
  --bootstrap-server localhost:9092 \
  --metrics org.apache.kafka.producer.,org.apache.kafka.consumer. \
  --alter --generate-name --interval 10000'
```

- `--metrics` 为指标名前缀白名单（demo 全量；生产应按需精简，如 record-send-rate / request-latency / records-lag / commit-latency / rebalance 系列）
- 可加 match selector（如 `--client-id-prefix`）按 client.id 前缀圈定客户端；不加则匹配全部
- interval demo 用 10s；生产默认 5min 即可
- **注意**：已运行的客户端按其自身节奏重新拉取订阅，不会立刻推送；新启动的客户端立即生效（对应 AGENTS.md 坑位 5）
- 管理：`--list` / `--describe --name <n>` / `--delete --name <n>`

## 4. 中间件配置

- `monitoring/otel-collector-config.yml`：otlp receiver(4317 gRPC/4318 HTTP) → batch → prometheus exporter(9464，`resource_to_telemetry_conversion` 把 resource attribute 转为 label) + debug exporter（排障用，可在不需要时去掉）
- `monitoring/prometheus.yml`：job `kafka-jmx`(kafka:9095) 与 `kafka-client-telemetry`(otel-collector:9464)，10s 抓取
- Grafana provisioning：`monitoring/grafana/provisioning/`（datasource uid=prometheus）+ `monitoring/grafana/dashboards/kafka-overview.json`

## 5. 指标速查

客户端指标（OTel 原名点号 → Prometheus 下划线），label 含 `clientId`（=代码里设置的 client.id）、`clientSoftwareName/Version`、插件 metadata：

| 面板 | PromQL |
|---|---|
| Producer 发送速率 | `sum by (clientId) (org_apache_kafka_producer_record_send_rate)` |
| Producer 错误速率 | `sum by (clientId) (org_apache_kafka_producer_record_error_rate)` |
| Producer 请求延迟 | `max by (clientId) (org_apache_kafka_producer_request_latency_avg/max)` |
| Consumer 拉取速率 | `sum by (clientId) (org_apache_kafka_consumer_fetch_manager_fetch_rate)` |
| Consumer 最大 lag | `max by (clientId) (org_apache_kafka_consumer_fetch_manager_records_lag_max)` |
| Consumer 提交延迟/失败 rebalance | `avg by (clientId) (org_apache_kafka_consumer_coordinator_commit_latency_avg)` / `sum(org_apache_kafka_consumer_coordinator_failed_rebalance_total)` |
| Broker 消息写入 | `sum(rate(kafka_server_brokertopicmetrics_messagesin_total[1m]))` |
| Broker 字节进出 | `sum(rate(kafka_server_brokertopicmetrics_bytesin_total[1m]))` / bytesout |
| Broker 请求线程空闲率 | `kafka_server_kafkarequesthandlerpool_brokerrequesthandleravgidle_percent` |

## 6. 排障

| 现象 | 处理 |
|---|---|
| collector 收不到任何 metrics（debug exporter 无输出） | 客户端尚未感知订阅：等一个拉取周期或重启应用；确认订阅存在（--list）；broker 日志确认插件 Initialized |
| broker 启动报插件类加载错误 | 插件版本与 kafka 版本不匹配（v1.1.0↔4.2.x） |
| Grafana 起不来 `Datasource provisioning error: data source not found` | datasource uid 变更与卷内旧记录冲突：`docker compose rm -f grafana && docker volume rm kafka_grafana_data` 后重建 |
| Prometheus target down | `docker compose logs` 对应服务；容器网络内用服务名互访 |
