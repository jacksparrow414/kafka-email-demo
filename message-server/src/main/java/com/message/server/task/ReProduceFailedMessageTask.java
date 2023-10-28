package com.message.server.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.service.MessageFailedService;
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
    
    @SneakyThrows
    @Override
    public void run() {
        List<MessageFailedEntity> messageFailedEntities = messageFailedService.queryMessageFailedUnReachedRetryCount();
        for (MessageFailedEntity messageFailedEntity : messageFailedEntities) {
            if (messageFailedEntity.getMessageGenerator().equals("BUSINESS_SERVER")) {
                ObjectMapper mapper = new ObjectMapper();
                UserDTO userDTO = mapper.readValue(messageFailedEntity.getMessageContentJsonFormat(), UserDTO.class);
                messageFailedProducer.sendMessage(userDTO);
            }
        }
    }
}
