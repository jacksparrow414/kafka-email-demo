package com.message.server.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhrase;
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
    
    public void sendMessage(final UserDTO userDTO, MessageFailedPhrase messageFailedPhrase) {
        ProducerRecord<String, UserDTO> user = new ProducerRecord<>("user", userDTO.getUserName(),  userDTO);
        try {
            PRODUCER.send( user, (recordMetadata, e) -> {
                if (Objects.nonNull(e)) {
                    log.finest("message has resent failed");
                    saveOrUpdateFailedMessage(userDTO, 0, messageFailedPhrase);
                }else {
                    log.info("message has resent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
                    saveOrUpdateFailedMessage(userDTO, 1, messageFailedPhrase);
                }
            });
        }catch (TimeoutException e) {
            log.info("send message to kafka timeout, message: ");
            // TODO: 自定义逻辑，比如发邮件通知kafka管理员
        }
    }
    
    @SneakyThrows
    private void saveOrUpdateFailedMessage(final UserDTO userDTO, Integer retryStatus,MessageFailedPhrase messageFailedPhrase) {
        MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
        messageFailedEntity.setMessageId(userDTO.getMessageId());
        ObjectMapper mapper = new ObjectMapper();
        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(userDTO));
        messageFailedEntity.setMessageType(MessageType.EMAIL);
        messageFailedEntity.setMessageFailedPhrase(messageFailedPhrase);
        messageFailedEntity.setRetryStatus(retryStatus);
        messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
    }
}
