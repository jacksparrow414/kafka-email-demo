package com.business.server.listener;

import com.business.server.producer.MessageProducer;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.Vector;
import org.apache.kafka.clients.producer.KafkaProducer;

public class KafkaListener implements ServletContextListener {

    private static final Vector<KafkaProducer> KAFKA_PRODUCERS = new Vector<>();
    
    public static final Vector<Thread> KAFKA_CONSUMERS = new Vector<>();

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        KAFKA_PRODUCERS.add(MessageProducer.PRODUCER);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        KAFKA_PRODUCERS.forEach(KafkaProducer::close);
    }
}
