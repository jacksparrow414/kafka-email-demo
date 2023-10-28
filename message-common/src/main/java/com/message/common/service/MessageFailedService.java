package com.message.common.service;

import com.message.common.entity.MessageFailedEntity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class MessageFailedService {
    
    /**
     * 查询消息失败的记录
     * @param messageIds 要检查的消息id
     * @param failedPhrases 消息失败的阶段
     * @return 消息重试状态为0、重试次数小于3的失败的消息
     */
    public List<MessageFailedEntity> queryMessageFailedByMessageIds(final List<String> messageIds, String failedPhrases) {
        String sql = "select * from message_failed where message_id in (?) and failed_phrases = ? and retry_status = 0 and retry_count < 3 ";
        // execute sql to get messageFailedEntities from database
        return new ArrayList<>();
    }
    
    /**
     * 保存或者更新消息失败的记录
     * 如果消息失败的记录不存在，则保存
     * 如果消息失败的记录存在，则更新，更新可能是更新为重试成功，也可能是更新为重试失败，无论如何，都是更新重试次数+1，更新当前时间
     * @param messageFailedEntity 消息失败的记录
     */
    public void saveOrUpdateMessageFailed(final MessageFailedEntity messageFailedEntity) {
        List<MessageFailedEntity> messageFailedEntities = queryMessageFailedByMessageIds(Collections.singletonList(messageFailedEntity.getMessageId()), messageFailedEntity.getFailedPhrases());
        if (messageFailedEntities.isEmpty()) {
            // execute sql to save messageFailedEntity to database
            String insertSql = "insert into message_failed(message_id, message_content_json_format, message_generator, failed_phrases, retry_count, retry_status) values(?, ?, ?, ?, ?, ?)";
        } else {
            // execute sql to update messageFailedEntity to database
            messageFailedEntities.forEach(each -> {
                Long id = each.getId();
                String updateSql = "update message_failed set retry_count = retry_count + 1, retry_status = ? where id = ? and message_id = ? and last_update_time = ?";
            });
        }
    }
    
    /**
     * 查询未达到重试次数的消息
     * @return 未达到重试次数的消息
     */
    public List<MessageFailedEntity> queryMessageFailedUnReachedRetryCount() {
        String sql = "select * from message_failed where retry_status = 0 and retry_count < 3 order by last_update_time asc limit 1000";
        // execute sql to get messageFailedEntities from database
        return new ArrayList<>();
    }
}
