package com.message.server.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhase;
import com.message.common.enums.MessageType;
import com.message.common.service.MessageFailedService;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 *
 * 负责重新发送失败的消息， 失败的消息可能在发送时失败， 也可能在消费时失败
 * 无论是发送失败还是消费失败，都会将消息再次发送到kafka中
 */
@Log
public class MessageFailedProducer {
    
    public static final KafkaProducer<String, UserDTO> PRODUCER = new KafkaProducer<>(KafkaConfiguration.loadProducerConfig());
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    public void sendMessage(final UserDTO userDTO, MessageFailedPhase messageFailedPhase) {
        // key使用messageId, 和首次生产保持一致, 保证同一个消息无论重试多少次都会发送到同一分区
        ProducerRecord<String, UserDTO> user = new ProducerRecord<>("email", userDTO.getMessageId(),  userDTO);
        try {
            PRODUCER.send( user, (recordMetadata, e) -> {
                if (Objects.nonNull(e)) {
                    log.finest("message has resent failed");
                    saveOrUpdateFailedMessage(userDTO, messageFailedPhase);
                }else {
                    log.info("message has resent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
                    messageFailedService.markRetrySuccessIfExists(userDTO.getMessageId(), messageFailedPhase);
                }
            });
        }catch (TimeoutException e) {
            log.info("send message to kafka timeout, message: ");
            // TODO: 自定义逻辑，比如发邮件通知kafka管理员
        }
    }

    @SneakyThrows
    private void saveOrUpdateFailedMessage(final UserDTO userDTO, MessageFailedPhase messageFailedPhase) {
        MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
        messageFailedEntity.setMessageId(userDTO.getMessageId());
        ObjectMapper mapper = new ObjectMapper();
        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(userDTO));
        messageFailedEntity.setMessageType(MessageType.EMAIL);
        messageFailedEntity.setMessageFailedPhase(messageFailedPhase);
        messageFailedEntity.setRetryStatus(0);
        messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
    }
}
