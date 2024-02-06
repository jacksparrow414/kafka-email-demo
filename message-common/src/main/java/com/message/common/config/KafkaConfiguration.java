package com.message.common.config;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.java.Log;
import org.apache.kafka.clients.KafkaClient;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.internals.ProducerInterceptors;
import org.apache.kafka.clients.producer.internals.ProducerMetadata;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.common.utils.Time;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
@Log
public class KafkaConfiguration {

    public static final String SERVER_ID = getServerId();

    /**
     * 如果是部署多个应用, 并且提供了手动开启/关闭kafka的功能, 建议使用Redis的incr来作为计数器
     */
    private static final AtomicInteger CONSUMER_CLIENT_ID_SEQUENCE = new AtomicInteger(1);

    /**
     * 使用当前服务器的hostname作为SERVER_ID
     * 更好的做法是: 当前服务器的hostname + contextPath
     * 因为一个Tomcat服务器上可能部署了多个应用，这样可以区分开
     */
    private static String getServerId() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warning("Failed to get server id");
            return "unknown";
        }
    }
    /**
     * 以下配置建议搭配 官方文档 + kafka权威指南相关章节 + 实际业务场景吞吐量需求 自己调整
     * 如果是本地， IP地址和docker-compose.yml中的EXTERNAL保持一致
     * 压缩类型官方建议选lz4, https://www.confluent.io/blog/apache-kafka-message-compression/
     *
     * 建议设置client.id, 防止InstanceAlreadyExistsException 异常,
     * 如果不设置, kafka会自动生成一个client.id, 默认格式是producer-1, 代码逻辑见{@link ProducerConfig#maybeOverrideClientId(Map)}
     * kafka Java client 会使用client.id生成JMX的ObjectName, 代码逻辑见{@link KafkaProducer#KafkaProducer(ProducerConfig, Serializer, Serializer, ProducerMetadata, KafkaClient, ProducerInterceptors, Time)} 中的registerAppInfo
     * 如果多个应用(也就是多个进程)都没有设置client.id, 使用默认的client.id的规则生成的client.id则重复, 会抛出InstanceAlreadyExistsException
     *
     * 如果是同一应用(也就是同一进程)创建多个producer, 不设置client.id的话不会抛出InstanceAlreadyExistsException, 因为其内部有一个自动递增的计数器{@link ProducerConfig#PRODUCER_CLIENT_ID_SEQUENCE}
     */
    public static Properties loadProducerConfig() {
        Properties result = new Properties();
        result.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.102:9093");
        // 建议设置client.id
        result.put(ProducerConfig.CLIENT_ID_CONFIG, SERVER_ID);
        result.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        result.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaJsonSerializer.class.getName());
        result.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, CompressionType.LZ4.name);
        // 每封邮件消息大小大约20KB, 使用默认配置吞吐量不高，下列配置增加kafka的吞吐量
        // 默认16384 bytes，太小了，这会导致邮件消息一个一个发送到kafka，达不到批量发送的目的，不符合发送邮件的场景
        result.put(ProducerConfig.BATCH_SIZE_CONFIG, 1048576 * 10);
        // 默认1048576 bytes，限制的是一个batch的大小，对于20KB的消息来说，消息太小
        result.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1048576 * 10);
        // 等10ms, 为了让更多的消息聚合到一个batch中，提高吞吐量
        result.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        return result;
    }

    /**
     * 以下配置建议搭配 官方文档 + kafka权威指南相关章节 + 实际业务场景需求 自己调整
     * https://kafka.apache.org/26/documentation/#group.instance.id
     *
     * 为什么需要group.instance.id?
     * 假设auto.offset.reset=latest
     * 1. 如果没有group.instance.id，那么kafka会认为此消费者是dynamic member，在重启期间如果有消息发送到topic，那么重启之后，消费者会【丢失这部分消息】
     * 加入auto.offset.reset=earliest
     * 1. 如果没有group.instance.id，那么kafka会认为此消费者是dynamic member，在重启期间如果有消息发送到topic，那么重启之后，消费者会重复消费【全部消息】
     *
     * 光有group.instance.id还不够，还需要修改heartbeat.interval.ms和session.timeout.ms的值为合理的值
     * 如果程序部署，重启期间，重启时间超过了session.timeout.ms的值，那么kafka会认为此消费者已经挂了会触发rebalance，在一些大型消息场景，rebalance的过程可能会很慢, 更详细的解释请参考
     * https://kafka.apache.org/26/documentation/#static_membership
     *
     * 建议设置client.id, 理由和{@link #loadProducerConfig()} 注释中的原因一样
     * Consumer生成client.id的逻辑见 {@link ConsumerConfig#maybeOverrideClientId(Map)}
     * @param groupInstanceId
     */
    public static Properties loadConsumerConfig(int groupInstanceId, String valueType) {
        Properties result = new Properties();
        result.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.102:9093");
        result.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        result.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaJsonDeserializer.class.getName());
        result.put(KafkaJsonDeserializerConfig.JSON_VALUE_TYPE, valueType);
        result.put(ConsumerConfig.GROUP_ID_CONFIG, "test");
        // 代表此消费者是消费者组的static member
        result.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "test-" + ++groupInstanceId);
        // 建议设置client.id
        result.put(ConsumerConfig.CLIENT_ID_CONFIG, SERVER_ID + "-" + CONSUMER_CLIENT_ID_SEQUENCE.getAndIncrement());
        // 修改heartbeat.interval.ms和session.timeout.ms的值，和group.instance.id配合使用，避免重启或重启时间过长的时候，触发rebalance
        result.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 1000 * 60);
        result.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 1000 * 60 * 5);
        // 关闭自动提交
        result.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, Boolean.FALSE);
        // 默认1MB，增加吞吐量，其设置对应的是每个分区，也就是说一个分区返回10MB的数据
        result.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, 1048576 * 10);
        result.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        // 返回全部数据的大小
        result.put(ConsumerConfig.FETCH_MAX_BYTES_CONFIG, 1048576 * 100);
        // 默认5分钟
        result.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 1000 * 60 * 5);
        return result;
    }
}
