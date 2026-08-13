package com.message.server.task;

import com.message.common.dto.CallbackMetaData;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageType;
import com.message.common.service.MessageFailedService;
import com.message.common.util.AvroJsonUtil;
import com.message.server.producer.CallbackProducer;
import com.message.server.producer.MessageFailedProducer;
import java.util.List;
import lombok.extern.java.Log;

/**
 *
 * 定时任务，用于重新生产失败的消息.
 *
 * @author jacksparrow414
 * @date 2023/10/28
 */
@Log
public class ReProduceFailedMessageTask implements Runnable {
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    private MessageFailedProducer messageFailedProducer = new MessageFailedProducer();
    
    private CallbackProducer callbackProducer = new CallbackProducer();
    
    /**
     * 如果是部署多台服务器，定时任务需要保证只有一台服务器执行，如有需要可以引入Redis分布式锁
     */
    @Override
    public void run() {
        List<MessageFailedEntity> messageFailedEntities = messageFailedService.queryMessageFailedUnReachedRetryCount();
        for (MessageFailedEntity messageFailedEntity : messageFailedEntities) {
            // 单条记录解析或发送失败(例如库中残留旧格式的消息内容)不能影响其他记录, 更不能让定时任务线程退出
            try {
                if (messageFailedEntity.getMessageType().equals(MessageType.EMAIL)) {
                    UserDTO userDTO = AvroJsonUtil.fromJson(messageFailedEntity.getMessageContentJsonFormat(), UserDTO.class);
                    messageFailedProducer.sendMessage(userDTO, messageFailedEntity.getMessageFailedPhase());
                }
                if (messageFailedEntity.getMessageType().equals(MessageType.EMAIL_CALLBACK)) {
                    CallbackMetaData callbackMetaData = AvroJsonUtil.fromJson(messageFailedEntity.getMessageContentJsonFormat(), CallbackMetaData.class);
                    callbackProducer.sendCallbackMessage(callbackMetaData, messageFailedEntity.getMessageFailedPhase());
                }
            } catch (Exception e) {
                log.severe("failed to re-produce message, messageId: " + messageFailedEntity.getMessageId() + ", error: " + e);
            }
        }
    }
}
