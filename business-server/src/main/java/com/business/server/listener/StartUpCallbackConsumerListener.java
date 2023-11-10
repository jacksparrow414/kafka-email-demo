package com.business.server.listener;

import com.business.server.consumer.CallbackConsumerRunner;
import com.message.common.config.KafkaConfiguration;
import com.message.common.deserializer.CallbackMetaDataDeserializer;
import com.message.common.dto.CallbackMetaData;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.KafkaConsumer;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class StartUpCallbackConsumerListener implements ServletContextListener {
    
    
    /**
     * 假设开启1个消费者, 回调的消费者可以少一点，回调消息还是少.
     *
     * 消费者的数量要和partition的数量一致，实际情况下，可以调用AdminClient的方法获取到topic的partition数量，然后根据partition数量来创建消费者.
     * @param sce
     */
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 10, 30L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(100), new AbortPolicy());
        for (int i = 0; i < 1; i++) {
            KafkaConsumer<String, CallbackMetaData> consumer = new KafkaConsumer<>(KafkaConfiguration.loadConsumerConfig(i, CallbackMetaDataDeserializer.class.getName()));
            CallbackConsumerRunner callbackConsumerRunner = new CallbackConsumerRunner(consumer, 10);
            // 使用另外一个线程来关闭消费者
            Thread shutdownHooks = new Thread(callbackConsumerRunner::shutdown);
            KafkaListener.KAFKA_CONSUMERS.add(shutdownHooks);
            // 启动消费者线程
            threadPoolExecutor.execute(callbackConsumerRunner);
        }
    }
}
