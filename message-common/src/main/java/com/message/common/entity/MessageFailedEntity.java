package com.message.common.entity;

import com.message.common.enums.MessageFailedPhrase;
import com.message.common.enums.MessageType;
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
     * 消息类型
     * EMAIL 表示此消息为邮件
     * EMAIL_CALLBACK 表示此消息为邮件回调
     *
     */
    private MessageType messageType;
    
    /**
     * 消息失败的阶段:
     * PRODUCER 表示在生产者发送消息的时候失败
     * CONSUMER 表示在消费者消费消息的时候失败
     */
    private MessageFailedPhrase messageFailedPhrase;
    
    /**
     * 失败时的异常堆栈信息
     */
    private String failedReason;
    
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
