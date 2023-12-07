package com.business.server.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhrase;
import com.message.common.enums.MessageType;
import com.message.common.service.MessageFailedService;
import java.util.Objects;
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
    
    public static final KafkaProducer<String, UserDTO> PRODUCER = new KafkaProducer<>(KafkaConfiguration.loadProducerConfig());
    
    private MessageFailedService messageFailedService = new MessageFailedService();

    /**
     * kafka producer 发送失败时会进行重试，相关参数 retries 和 delivery.timeout.ms, 官方建议使用delivery.timeout.ms，默认2分钟
     * callback函数只有在最后一次重试之后才会调用， 详情可以看https://lists.apache.org/thread/nwg326bxpo7ry116nqhxmsmc3bokc6hm
     * @param userDTO
     */
    public void sendMessage(final UserDTO userDTO) {
        ProducerRecord<String, UserDTO> user = new ProducerRecord<>("email", userDTO.getMessageId(),  userDTO);
        try {
            PRODUCER.send( user, (recordMetadata, e) -> {
                if (Objects.nonNull(e)) {
                    log.severe("message has sent failed");
                    MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
                    messageFailedEntity.setMessageId(userDTO.getMessageId());
                    ObjectMapper mapper = new ObjectMapper();
                    try {
                        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(userDTO));
                    } catch (JsonProcessingException jsonProcessingException) {
                        log.severe("message content json format failed");
                    }
                    messageFailedEntity.setMessageType(MessageType.EMAIL);
                    messageFailedEntity.setMessageFailedPhrase(MessageFailedPhrase.PRODUCER);
                    messageFailedEntity.setFailedReason(e.getMessage());
                    // 如果sendMessage传进来的是个list，也同理，不能放到list.foreach外面
                    // 如果放在主线程里，由于kafka producer是异步的，
                    // kafka producer的执行速度可能慢于主线程，可能拿到的值是空的是有问题的，例如拿到的failedReason是空的
                    messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
                } else {
                    log.info("message has sent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
                }
            });
        } catch (TimeoutException e) {
            log.info("send message to kafka timeout, message: ");
            // TODO: 自定义逻辑，比如发邮件通知kafka管理员
        }
    }
}
