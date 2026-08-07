Example of using Kafka to decouple Email services

# Tech stack (2026-08 refactor)
- Kafka 4.2.1 (official `apache/kafka` image, KRaft single node) + kafka-clients 4.2.1
- Kafka UI: `ghcr.io/kafbat/kafka-ui` at http://localhost:9080/kafkaui (admin / kafkauipassword)
- Broker metrics: jmx_prometheus_javaagent 1.6.0 on :9095
- Persistence: embedded H2 (file at `~/kafka-message-data/message-db`, `AUTO_SERVER` mode); override via `-Dkafka.message.db.url=...`
- Kafka bootstrap: `localhost:9093`, override via `KAFKA_BOOTSTRAP_SERVERS` (system property or env)
- Monitoring (KIP-714): clients push metrics to broker → instaclustr client-metrics-reporter plugin → otel-collector → Prometheus (:9090) → Grafana (:3000, admin/admin), dashboard "Kafka 客户端遥测(KIP-714) + Broker 总览"

# Run
1. `docker compose up -d`
2. `mvn clean package` and deploy `business-server.war` + `message-server.war` to Tomcat 10.1
3. Send a message: `curl "http://localhost:8080/business-server/producerMessage?username=t&password=t"`

# Related blogs
1. [Install standalone Kafka and Kafka UI using Docker Compose](https://blog.csdn.net/dghkgjlh/article/details/133418837)
2. [Messaging system design that decouples email sending from business systems](https://blog.csdn.net/dghkgjlh/article/details/134221924)
3. [Producers send JSON messages + use unified serializers + improve throughput](https://blog.csdn.net/dghkgjlh/article/details/134360108)
4. [Consumers consume JSON messages + use a unified deserializer + improve throughput](https://blog.csdn.net/dghkgjlh/article/details/134477889)
5. [Consumer callback + scheduled retry + understanding Rebalance](https://blog.csdn.net/dghkgjlh/article/details/134610052)
