package com.message.common.enums;

/**
 * @author jacksparrow414
 * @date 2023/11/10
 */
public enum MessageFailedPhase {
    
    /**
     * 表示在生产者发送消息的时候失败
     */
    PRODUCER,
    
    /**
     * 表示在消费者消费消息的时候失败
     */
    CONSUMER;
}
