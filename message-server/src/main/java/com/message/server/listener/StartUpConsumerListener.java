package com.message.server.listener;

import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.server.consumer.MessageConsumerRunner;
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
public class StartUpConsumerListener implements ServletContextListener {
    
    
    /**
     * 假设开启10个消费者.
     *
     * 消费者的数量要和partition的数量一致，实际情况下，可以调用AdminClient的方法获取到topic的partition数量，然后根据partition数量来创建消费者.
     * @param sce
     */
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(10, 10, 30L, TimeUnit.SECONDS, new LinkedBlockingDeque<>(100), new AbortPolicy());
        for (int i = 0; i < 10; i++) {
            KafkaConsumer<String, UserDTO> consumer = new KafkaConsumer<>(KafkaConfiguration.loadConsumerConfig(i, UserDTO.class.getName()));
            MessageConsumerRunner messageConsumerRunner = new MessageConsumerRunner(consumer, 10);
            // 使用另外一个线程来关闭消费者
            Thread shutdownHooks = new Thread(messageConsumerRunner::shutdown);
            KafkaListener.KAFKA_CONSUMERS.add(shutdownHooks);
            // 启动消费者线程
            threadPoolExecutor.execute(messageConsumerRunner);
        }
    }
}
