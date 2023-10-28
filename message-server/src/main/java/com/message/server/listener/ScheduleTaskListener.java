package com.message.server.listener;

import com.message.server.task.ReProduceFailedMessageTask;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class ScheduleTaskListener implements ServletContextListener {
    
    @Override
    public void contextInitialized(final ServletContextEvent sce) {
        ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
        ReProduceFailedMessageTask reProduceFailedMessageTask = new ReProduceFailedMessageTask();
        scheduledThreadPoolExecutor.schedule(reProduceFailedMessageTask, 10, TimeUnit.MINUTES);
    }
}
