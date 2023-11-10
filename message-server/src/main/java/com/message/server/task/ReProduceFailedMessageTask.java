package com.message.server.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageType;
import com.message.common.service.MessageFailedService;
import com.message.server.producer.CallbackProducer;
import com.message.server.producer.MessageFailedProducer;
import java.util.List;
import lombok.SneakyThrows;

/**
 *
 * 定时任务，用于重新生产失败的消息.
 *
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class ReProduceFailedMessageTask implements Runnable {
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    private MessageFailedProducer messageFailedProducer = new MessageFailedProducer();
    
    private CallbackProducer callbackProducer = new CallbackProducer();
    
    /**
     * 如果是部署多台服务器， 那么定时任务在执行时使用Redis分布式锁，保证只有一台服务器执行定时任务， 这就是为什么docker-compose.yml中配置redis的原因
     */
    @SneakyThrows
    @Override
    public void run() {
        List<MessageFailedEntity> messageFailedEntities = messageFailedService.queryMessageFailedUnReachedRetryCount();
        for (MessageFailedEntity messageFailedEntity : messageFailedEntities) {
            if (messageFailedEntity.getMessageType().equals(MessageType.EMAIL)) {
                ObjectMapper mapper = new ObjectMapper();
                UserDTO userDTO = mapper.readValue(messageFailedEntity.getMessageContentJsonFormat(), UserDTO.class);
                messageFailedProducer.sendMessage(userDTO, messageFailedEntity.getMessageFailedPhrase());
            }
            if (messageFailedEntity.getMessageType().equals(MessageType.EMAIL_CALLBACK)) {
                ObjectMapper mapper = new ObjectMapper();
                CallbackMetaData callbackMetaData = mapper.readValue(messageFailedEntity.getMessageContentJsonFormat(), CallbackMetaData.class);
                callbackProducer.sendCallbackMessage(callbackMetaData, messageFailedEntity.getMessageFailedPhrase());
            }
        }
    }
}
