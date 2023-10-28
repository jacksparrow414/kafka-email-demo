package com.message.common.entity;

/**
 * 消息消费成功之后，将messageId插入到数据库中，用于消息的幂等性判断
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class MessageAckConsumesSuccessEntity {
    
    /**
     * 主键
     * 和{@link MessageFailedEntity#messageId}是同一个
     */
    private String messageId;
}
