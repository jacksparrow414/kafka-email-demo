package com.business.server.listener;

import com.business.server.producer.MessageProducer;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.LinkedList;
import java.util.List;
import java.util.Vector;
import org.apache.kafka.clients.producer.KafkaProducer;

public class KafkaListener implements ServletContextListener {

    private static final List<KafkaProducer> KAFKA_PRODUCERS = new LinkedList<>();
    
    public static final Vector<Thread> KAFKA_CONSUMERS = new Vector<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        KAFKA_PRODUCERS.add(MessageProducer.PRODUCER);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // 关闭回调消费者, 注意这里调用的是Thread的run方法而不是start方法, 在当前销毁线程中同步执行shutdown逻辑
        KAFKA_CONSUMERS.forEach(Thread::run);
        KAFKA_PRODUCERS.forEach(KafkaProducer::close);
    }
}
