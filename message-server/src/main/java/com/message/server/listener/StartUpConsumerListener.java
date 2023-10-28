package com.message.server.listener;

import com.message.server.consumer.MessageConsumerRunner;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor.AbortPolicy;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class StartUpConsumerListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(1, 5, 30L, null, new LinkedBlockingDeque<>(100), new AbortPolicy());
        threadPoolExecutor.execute(new MessageConsumerRunner());
    }
}
