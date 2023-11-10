package com.message.server.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.config.KafkaConfiguration;
import com.message.common.dto.CallbackMetaData;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhrase;
import com.message.common.enums.MessageType;
import com.message.common.serializer.CallbackMetaDataSerializer;
import com.message.common.service.MessageFailedService;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import lombok.SneakyThrows;
import lombok.extern.java.Log;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TimeoutException;

/**
 * @author jacksparrow414
 * @date 2023/11/10
 *
 * 该Producer用于发送回调消息到kafka， 也用于重试任务中重新生产消息
 */
@Log
public class CallbackProducer {
    
    public static final Producer<String, CallbackMetaData> CALLBACK_META_DATA_PRODUCER = new KafkaProducer<>(KafkaConfiguration.loadProducerConfig(CallbackMetaDataSerializer.class.getName()));
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    /**
     * 和{@link com.bussiness.server.producer.MessageProducer#sendMessage(com.message.common.dto.UserDTO) 类似}
     * 一条消息可能在发送到kafka时失败，也可能在发送到kafka成功，但是在消费端消费失败，所以这里需要传递messageFailedPhrase参数
     * @param callbackMetaData
     */
    public void sendCallbackMessage(CallbackMetaData callbackMetaData, MessageFailedPhrase messageFailedPhrase) {
    
        ProducerRecord<String, CallbackMetaData> callbackRecord = new ProducerRecord<>("callback", callbackMetaData.getMessageId(),  callbackMetaData);
        try {
            CALLBACK_META_DATA_PRODUCER.send( callbackRecord, (recordMetadata, e) -> {
                Set<String> messageFailedSet = new HashSet<>();
                if (Objects.nonNull(e)) {
                    log.finest("message has sent failed");
                    // 应该只保存一次，不应该每次都保存
                    if (messageFailedSet.isEmpty()) {
                        saveOrUpdateFailedMessage(callbackMetaData, messageFailedPhrase);
                        messageFailedSet.add(callbackMetaData.getMessageId());
                    }
                }else {
                    log.info("message has sent to topic: " + recordMetadata.topic() + ", partition: " + recordMetadata.partition() );
                    saveOrUpdateFailedMessage(callbackMetaData, messageFailedPhrase);
                }
            });
        } catch (TimeoutException e) {
            log.info("send message to kafka timeout, message: ");
            // TODO: 自定义逻辑，比如发邮件通知kafka管理员
        }
    }
    
    /**
     * @param callbackMetaData
     */
    @SneakyThrows
    private void saveOrUpdateFailedMessage(final CallbackMetaData callbackMetaData, MessageFailedPhrase messageFailedPhrase) {
        MessageFailedEntity messageFailedEntity = new MessageFailedEntity();
        messageFailedEntity.setMessageId(callbackMetaData.getMessageId());
        ObjectMapper mapper = new ObjectMapper();
        messageFailedEntity.setMessageContentJsonFormat(mapper.writeValueAsString(callbackMetaData));
        messageFailedEntity.setMessageType(MessageType.EMAIL_CALLBACK);
        messageFailedEntity.setMessageFailedPhrase(messageFailedPhrase);
        messageFailedService.saveOrUpdateMessageFailed(messageFailedEntity);
    }
}
