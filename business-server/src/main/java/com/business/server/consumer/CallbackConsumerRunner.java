package com.business.server.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import com.message.common.service.MessageAckConsumesSuccessService;
import com.message.common.service.MessageFailedService;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.java.Log;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Log
public class CallbackConsumerRunner implements Runnable{

    /**
     * 回调topic和{@link com.message.server.producer.CallbackProducer} 保持一致, 每台服务器一个回调topic: callback + hostName
     */
    private static final String CALLBACK_TOPIC = "callback" + hostName();

    private final AtomicBoolean closed = new AtomicBoolean(false);

    private MessageAckConsumesSuccessService messageAckConsumesSuccessService = new MessageAckConsumesSuccessService();

    private MessageFailedService messageFailedService = new MessageFailedService();

    private final KafkaConsumer<String, CallbackMetaData> consumer;

    private final int consumerPollIntervalSecond;

    public CallbackConsumerRunner(KafkaConsumer<String, CallbackMetaData> consumer, int consumerPollIntervalSecond) {
        this.consumer = consumer;
        this.consumerPollIntervalSecond = consumerPollIntervalSecond;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            log.warning("failed to get hostname for callback topic, fallback to unknown");
            return "unknown";
        }
    }

    /**
     * 和{@link com.message.server.consumer.MessageConsumerRunner#run()} 类似
     */
    @Override
    public void run() {
        consumer.subscribe(Collections.singletonList(CALLBACK_TOPIC));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // TODO 不再补充类似的代码， 自己完成

        while (!closed.get()) {
            ConsumerRecords<String, CallbackMetaData> records = consumer.poll(Duration.ofSeconds(consumerPollIntervalSecond));
            records.forEach(each -> {
                Class<?> destClass;
                try {
                    //        核心消费代码, 通过反射调用目标方法
                    destClass = Class.forName(each.value().getClassName());
                    Object instance = objectMapper.readValue(each.value().getInstanceJsonStr(), destClass);
                    MethodUtils.invokeMethod(instance, true, each.value().getMethodName(), parseArguments(each.value()));
                    log.info("callback message consumed, messageId: " + each.value().getMessageId());
                } catch (Exception e) {
                    log.severe("failed to consume callback message: " + e);
                }
            });
        }
    }
    
    /**
     * arguments中的每个元素是一个参数经Jackson序列化后的JSON字符串, 这里逐个解析回Object
     */
    private static Object[] parseArguments(CallbackMetaData callbackMetaData) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        List<String> arguments = callbackMetaData.getArguments();
        Object[] result = new Object[arguments.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = objectMapper.readValue(arguments.get(i), Object.class);
        }
        return result;
    }

    public void shutdown() {
        log.info( Thread.currentThread().getName() + " shutdown kafka consumer");
        closed.set(true);
        consumer.wakeup();
    }
}
