Example of using Kafka to decouple Email services

# Tech stack (2026-08 refactor)
- Kafka 4.2.1 (official `apache/kafka` image, KRaft single node) + kafka-clients 4.2.1
- Schema management: Apicurio Registry 3.3.1 (kafkasql storage, schemas live in the `kafkasql-journal` topic), schema language Avro. DTO classes (`UserDTO`/`CallbackMetaData`) are generated from `message-common/src/main/avro/*.avsc` by avro-maven-plugin — do not hand-edit
  - REST API at http://localhost:8081/apis/registry/v3, web console at http://localhost:8082; override via `APICURIO_REGISTRY_URL`
  - Build-time BACKWARD compatibility check against the published schemas via apicurio-registry-maven-plugin (test phase, dryRun; requires the registry online and the global rule below; skip with `-DskipRegister=true`)
- Kafka UI: `ghcr.io/kafbat/kafka-ui` at http://localhost:9080/kafkaui (admin / adb-1234), message values auto-decoded as Avro via the registry ccompat endpoint
- Broker metrics: jmx_prometheus_javaagent 1.6.0 on :9095
- Persistence: embedded H2 (file at `~/kafka-message-data/message-db`, `AUTO_SERVER` mode); override via `-Dkafka.message.db.url=...`
- Kafka bootstrap: `localhost:9093`, override via `KAFKA_BOOTSTRAP_SERVERS` (system property or env)
- Monitoring (KIP-714): clients push metrics to broker → instaclustr client-metrics-reporter plugin → otel-collector → Prometheus (:9090) → Grafana (:3000, admin/admin), dashboard "Kafka 客户端遥测(KIP-714) + Broker 总览"

# Run
Requires JDK 17. Start only the components you need (minimal set for the message pipeline shown here; see AGENTS.md for other combinations):

1. `docker compose up -d kafka apicurio-registry` (add `kafka-ui` to inspect messages)
2. Once per fresh `kafka_data` volume, create the global BACKWARD compatibility rule (required by the build-time schema check):
   `curl -X POST "http://localhost:8081/apis/registry/v3/admin/rules" -H "Content-Type: application/json" -d '{"ruleType":"COMPATIBILITY","config":"BACKWARD"}'`
3. `mvn clean package -DskipTests` and deploy `business-server.war` + `message-server.war` to Tomcat 10.1
4. Send a message: `curl "http://localhost:8080/business-server/producerMessage?username=t&password=t"`

# Docs
- `docs/architecture.md` — design, module/class details, message flow, table schema
- `docs/deployment.md` — infra, build/deploy, self-test, curl/CLI cheat sheet, troubleshooting
- `docs/monitoring.md` — KIP-714 pipeline, metric subscription, dashboards
- `AGENTS.md` — repo conventions and environment pitfalls

# Related blogs
1. [Install standalone Kafka and Kafka UI using Docker Compose](https://blog.csdn.net/dghkgjlh/article/details/133418837)
2. [Messaging system design that decouples email sending from business systems](https://blog.csdn.net/dghkgjlh/article/details/134221924)
3. [Producers send JSON messages + use unified serializers + improve throughput](https://blog.csdn.net/dghkgjlh/article/details/134360108)
4. [Consumers consume JSON messages + use a unified deserializer + improve throughput](https://blog.csdn.net/dghkgjlh/article/details/134477889)
5. [Consumer callback + scheduled retry + understanding Rebalance](https://blog.csdn.net/dghkgjlh/article/details/134610052)
