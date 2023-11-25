package com.business.server.consumer;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import com.message.common.service.MessageAckConsumesSuccessService;
import com.message.common.service.MessageFailedService;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.java.Log;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

@Log
public class CallbackConsumerRunner implements Runnable{
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private MessageAckConsumesSuccessService messageAckConsumesSuccessService = new MessageAckConsumesSuccessService();
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    private final KafkaConsumer<String, CallbackMetaData> consumer;
    
    private final int consumerPollIntervalSecond;
    
    public CallbackConsumerRunner(KafkaConsumer<String, CallbackMetaData> consumer, int consumerPollIntervalSecond) {
        this.consumer = consumer;
        this.consumerPollIntervalSecond = consumerPollIntervalSecond;
    }
    
    /**
     * 和{@link com.message.server.consumer.MessageConsumerRunner#run()} 类似
     */
    @Override
    public void run() {
        consumer.subscribe(Collections.singletonList("callback"));
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        // TODO 不再补充类似的代码， 自己完成

        while (!closed.get()) {
            ConsumerRecords<String, CallbackMetaData> records = consumer.poll(Duration.ofSeconds(10));
            records.forEach(each -> {
                Class<?> destClass;
                try {
                    //        核心消费代码, 通过反射调用目标方法
                    destClass = Class.forName(each.value().getClassName());
                    Object instance = objectMapper.readValue(each.value().getInstanceJsonStr(), destClass);
                    MethodUtils.invokeMethod(instance, true, each.value().getMethodName(), each.value().getArguments());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
    }
    
    public void shutdown() {
        log.info( Thread.currentThread().getName() + " shutdown kafka consumer");
        closed.set(true);
        consumer.wakeup();
    }
}
