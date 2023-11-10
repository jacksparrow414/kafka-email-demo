package com.business.server.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhrase;
import com.message.common.enums.MessageType;
import com.message.common.serializer.UserDTOSerializer;
import com.message.common.service.MessageFailedService;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
@Log
public class MessageProducer {
    
    public static final KafkaProducer<String, UserDTO> PRODUCER = new KafkaProducer<>(KafkaConfiguration.loadProducerConfig(UserDTOSerializer.class.getName()));
    
    private MessageFailedService messageFailedService = new MessageFailedService();

    /**
     * kafka producer 发送失败时会进行重试，相关参数 retries 和 delivery.timeout.ms, 官方建议使用delivery.timeout.ms，默认2分钟
     * 也就是说在2分钟之后，下列代码中的回调函数会被调用，重试多少次回调函数就会被调用多少次，所以我们在重试期间只要保存一次失败的消息就好，如果在重试期间成功，则去更新
     * @param userDTO
     */
    public void sendMessage(final UserDTO userDTO) {
        ProducerRecord<String, UserDTO> user = new ProducerRecord<>("email", userDTO.getMessageId(),  userDTO);
        try {
            PRODUCER.send( user, (recordMetadata, e) -> {
                Set<String> messageFailedSet = new HashSet<>();
                if (Objects.nonNull(e)) {
                    log.finest("message has sent failed");
                    // 应该只保存一次，不应该每次都保存
                    if (messageFailedSet.isEmpty()) {
                        saveOrUpdateFailedMessage(userDTO);
                        messageFailedSet.add(userDTO.getMessageId());
                    }
                }else {
                    log.info("message has sent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
                    saveOrUpdateFailedMessage(userDTO);
                }
            });
        } catch (TimeoutException e) {
            log.info("send message to kafka timeout, message: ");
            // TODO: 自定义逻辑，比如发邮件通知kafka管理员
        }
    }
    
    /**
     * @param userDTO
     */
    @SneakyThrows
    private void saveOrUpdateFailedMessage(final UserDTO userDTO) {
        MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
        messageFailedEntity.setMessageId(userDTO.getMessageId());
        ObjectMapper mapper = new ObjectMapper();
        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(userDTO));
        messageFailedEntity.setMessageType(MessageType.EMAIL);
        messageFailedEntity.setMessageFailedPhrase(MessageFailedPhrase.PRODUCER);
        messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
    }
}
