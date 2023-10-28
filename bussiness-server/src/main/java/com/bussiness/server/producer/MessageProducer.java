package com.bussiness.server.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.service.MessageFailedService;
import java.util.Objects;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
@Log
public class MessageProducer {
    
    private static final KafkaProducer<String, UserDTO> PRODUCER = new KafkaProducer<>(KafkaConfiguration.loadProducerConfig());
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    public void sendMessage(final UserDTO userDTO) {
        ProducerRecord<String, UserDTO> user = new ProducerRecord<>("user", userDTO.getUserName(),  userDTO);
        PRODUCER.send( user, (recordMetadata, e) -> {
            if (Objects.nonNull(e)) {
                log.finest("message has sent failed");
                insertFailedMessage(userDTO);
                //
            }else {
                log.info("message has sent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
            }
        });
    }
    
    @SneakyThrows
    private void insertFailedMessage(final UserDTO userDTO) {
        MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
        messageFailedEntity.setMessageId(userDTO.getMessageId());
        ObjectMapper mapper = new ObjectMapper();
        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(userDTO));
        messageFailedEntity.setMessageGenerator("BUSINESS_SERVER");
        messageFailedEntity.setFailedPhrases("PRODUCER");
        messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
    }
}
