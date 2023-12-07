package com.message.common.config;

import io.confluent.kafka.serializers.KafkaJsonDeserializer;
import io.confluent.kafka.serializers.KafkaJsonDeserializerConfig;
import io.confluent.kafka.serializers.KafkaJsonSerializer;
import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.record.CompressionType;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class KafkaConfiguration {

    /**
     * 以下配置建议搭配 官方文档 + kafka权威指南相关章节 + 实际业务场景吞吐量需求 自己调整
     * 如果是本地， IP地址和docker-compose.yml中的EXTERNAL保持一致
     * 压缩类型官方建议选lz4, https://www.confluent.io/blog/apache-kafka-message-compression/
     * @return
     */
    public static Properties loadProducerConfig() {
        Properties result = new Properties();
        result.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "192.168.0.102:9093");
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
     * @param groupInstanceId
     * @return
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
