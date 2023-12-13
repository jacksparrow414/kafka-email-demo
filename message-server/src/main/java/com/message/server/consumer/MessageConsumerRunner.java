package com.message.server.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.UserDTO;
import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhase;
import com.message.common.enums.MessageType;
import com.message.common.service.MessageAckConsumesSuccessService;
import com.message.common.service.MessageFailedService;
import com.message.server.producer.CallbackProducer;
import dev.failsafe.Failsafe;
import dev.failsafe.Fallback;
import dev.failsafe.RetryPolicy;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.extern.java.Log;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
@Log
public class MessageConsumerRunner implements Runnable {
    
    private final AtomicBoolean closed = new AtomicBoolean(false);
    
    private MessageAckConsumesSuccessService messageAckConsumesSuccessService = new MessageAckConsumesSuccessService();
    
    private MessageFailedService messageFailedService = new MessageFailedService();
    
    private final KafkaConsumer<String, UserDTO> consumer;
    
    private final int consumerPollIntervalSecond;
    
    public MessageConsumerRunner(KafkaConsumer<String, UserDTO> consumer, int consumerPollIntervalSecond) {
        this.consumer = consumer;
        this.consumerPollIntervalSecond = consumerPollIntervalSecond;
    }
    
    /**
     * 1. 使用https://failsafe.dev/进行重试
     * 2. 每次消费消息前，判断消息ID是否存在于数据库中和当前Set集合中，避免重复消费，
     *    我们的消息时根据消息的key进行hash分区的，所以同一个消息即使生产多次，一定会到同一个partition中，partition动态增加引起的特殊情况不在考虑范围之内
     * 4. 在一次消费消息中重试两次，如果两次都失败，那么将失败原因、消息的JSON字符串插入到message_failed表中，以便后续再次生产或排查问题
     * 3. 平时异步提交，关闭消费者时使用同步提交
     */
    @Override
    public void run() {
        AtomicReference<String> errorMessage = new AtomicReference<>(StringUtils.EMPTY);
        RetryPolicy<Boolean> retryPolicy = RetryPolicy.<Boolean>builder()
            .handle(Exception.class)
            // 如果业务逻辑返回false或者抛出异常，则重试
            .handleResultIf(Boolean.FALSE::equals)
            // 不包含首次
            .withMaxRetries(2)
            .withDelay(Duration.ofMillis(200))
            .onRetry(e -> log.warning("consume message failed, start the {}th retry"+ e.getAttemptCount()))
            .onRetriesExceeded(e -> {
                Optional.ofNullable(e.getException()).ifPresent(u -> errorMessage.set(u.getMessage()));
                log.severe("max retries exceeded" + e.getException());
            })
            .build();
        Fallback<Boolean> fallback = Fallback.<Boolean>builder(e -> {
            // do nothing, suppress exceptions
        }).build();
        try {
            consumer.subscribe(Collections.singletonList("email"));
            while (!closed.get()) {
                // get message from kafka
                ConsumerRecords<String, UserDTO> records = consumer.poll(Duration.ofSeconds(consumerPollIntervalSecond));
                if (records.isEmpty()) {
                    return;
                }
                Set<UserDTO> successConsumed = new HashSet<>();
                Set<UserDTO> failedConsumed = new HashSet<>();
                Map<String, String> failedConsumedReason = new HashMap<>();
                // check message if exist in database
                Set<String> checkingMessageIds = new HashSet<>(records.count());
                records.iterator().forEachRemaining(item -> checkingMessageIds.add(item.value().getMessageId()));
                Set<String> hasBeenConsumedMessageIds = messageAckConsumesSuccessService.checkMessageIfExistInDatabase(checkingMessageIds);
                records.forEach(item -> {
                    if (hasBeenConsumedMessageIds.contains(item.value().getMessageId())) {
                        // if exist, continue
                        return;
                    }
                    // 每一批消息中也可能存在同样的消息，所以需要再次判断
                    hasBeenConsumedMessageIds.add(item.value().getMessageId());
                    try {
                        Failsafe.with(fallback, retryPolicy)
                            .onSuccess(e -> successConsumed.add(item.value()))
                            .onFailure(e -> {
                                failedConsumed.add(item.value());
                                failedConsumedReason.put(item.value().getMessageId(), StringUtils.isNotBlank(errorMessage.get()) ? errorMessage.get() : "no reason, may be check server log");
                                errorMessage.set(StringUtils.EMPTY);
                            })
                            .get(() -> {
                                // 这里是业务逻辑，可以返回true或false，为什么要这样？是因为上面RetryPolicy这里定义的boolean,根据自己实际业务设置相应的类型
                                return true;
                            });
                        // 这里要catch住所有业务异常，防止由业务异常导致消费者线程退出
                    }catch (Exception e) {
                        log.severe("failed to consume email message" + e);
                        failedConsumed.add(item.value());
                        failedConsumedReason.put(item.value().getMessageId(), StringUtils.isNotBlank(e.getMessage()) ? e.getMessage() : e.getCause().toString());
                    }
                });
                postConsumed(successConsumed, failedConsumed, failedConsumedReason);
                // 平时使用异步提交
                consumer.commitAsync();
            }
        }catch (WakeupException e) {
            if (!closed.get()) {
                throw e;
            }
        } finally {
            // 消费者退出时使用同步提交
            try {
                consumer.commitSync();
            } catch (Exception e) {
                log.info("commit sync occur exception: " + e);
            } finally{
                try {
                    consumer.close();
                }catch (Exception e) {
                    log.info("consumer close occur exception: " + e);
                }
                log.info( "shutdown kafka consumer complete");
            }
        }
    }
    
    /**
     * 处理成功、成功后的回调、失败
     * @param successConsumed
     * @param failedConsumed
     * @param failedConsumedReason
     */
    private void postConsumed(Set<UserDTO> successConsumed, Set<UserDTO> failedConsumed, Map<String, String> failedConsumedReason) {
        // 后置处理开启异步线程处理，不阻塞消费者线程
        
        // 克隆传进来的集合，而不使用原集合的引用，因为原集合每次消费都会重置
        Set<UserDTO> cloneSuccessConsumed = new HashSet<>(successConsumed);
        Set<UserDTO> cloneFailedConsumed = new HashSet<>(failedConsumed);
        Map<String, String> cloneFailedConsumedReason = new HashMap<>(failedConsumedReason);
        new Thread( () -> {
            if (!cloneSuccessConsumed.isEmpty()) {
                messageAckConsumesSuccessService.insertMessageIds(cloneSuccessConsumed.stream().map(UserDTO::getMessageId).collect(Collectors.toSet()));
                cloneFailedConsumed.forEach(item -> {
                    if (Objects.nonNull(item.getCallbackMetaData())) {
                        // do callback
                        CallbackProducer callbackProducer = new CallbackProducer();
                        callbackProducer.sendCallbackMessage(item.getCallbackMetaData(), MessageFailedPhase.PRODUCER);
                    }
                });
            }
            if (!cloneFailedConsumed.isEmpty()) {
                ObjectMapper objectMapper = new ObjectMapper();
                cloneFailedConsumed.forEach(item -> {
                    MessageFailedEntity entity = new MessageFailedEntity();
                    entity.setMessageId(item.getMessageId());
                    entity.setMessageType(MessageType.EMAIL);
                    entity.setMessageFailedPhase(MessageFailedPhase.CONSUMER);
                    entity.setFailedReason(cloneFailedConsumedReason.get(item.getMessageId()));
                    try {
                        entity.setMessageContentJsonFormat(objectMapper.writeValueAsString(item));
                    } catch (JsonProcessingException e) {
                        log.info("failed to convert UserDTO message to json string");
                    }
                    messageFailedService.saveOrUpdateMessageFailed(entity);
                });
            }
        }).start();
    }
    
    public void shutdown() {
        log.info( Thread.currentThread().getName() + " shutdown kafka consumer");
        closed.set(true);
        consumer.wakeup();
    }
}
