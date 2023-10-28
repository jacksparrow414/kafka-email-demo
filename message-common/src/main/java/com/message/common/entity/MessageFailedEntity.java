package com.message.common.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = {"messageId", "failedPhrases"})
public class MessageFailedEntity {
    
    /**
     * 主键
     */
    private Long id;
    
    /**
     * 消息id
     */
    private String messageId;
    
    /**
     * JSON格式的消息内容
     */
    private String messageContentJsonFormat;
    
    /**
     * 消息生成者
     * BUSINESS_SERVER 表示业务系统
     * MESSAGE_SERVER 表示消息系统
     */
    private String messageGenerator;
    
    /**
     * 消息失败的阶段:
     * PRODUCER 表示在生产者发送消息的时候失败
     * CONSUMER 表示在消费者消费消息的时候失败
     */
    private String failedPhrases;
    
    /**
     * 消息重试次数
     */
    private Integer retryCount;
    
    /**
     * 消息重试状态
     * 0 表示重试失败
     * 1 表示重试成功
     */
    private Integer retryStatus;
    
    /**
     * 时间戳
     */
    private LocalDateTime lastUpdateTime;
    
}
