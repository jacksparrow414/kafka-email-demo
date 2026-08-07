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
        // 首次延迟1分钟执行(等待应用初始化完成), 之后每10分钟执行一次, 周期性地重试失败的消息
        scheduledThreadPoolExecutor.scheduleWithFixedDelay(reProduceFailedMessageTask, 1, 10, TimeUnit.MINUTES);
    }
}
