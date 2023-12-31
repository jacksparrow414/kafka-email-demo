package com.message.server.listener;

import com.message.server.producer.MessageFailedProducer;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Vector;
import org.apache.kafka.clients.producer.KafkaProducer;

public class KafkaListener implements ServletContextListener {

    private static final Vector<KafkaProducer> KAFKA_PRODUCERS = new Vector<>();
    
    public static final Vector<Thread> KAFKA_CONSUMERS = new Vector<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        KAFKA_PRODUCERS.add(MessageFailedProducer.PRODUCER);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        KAFKA_PRODUCERS.forEach(KafkaProducer::close);
        KAFKA_CONSUMERS.forEach(Thread::run);
    }
}
