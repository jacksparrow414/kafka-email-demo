package com.message.common.service;

import java.util.HashSet;
import java.util.Set;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
public class MessageAckConsumesSuccessService {
    
    /**
     * 检查消息是否在数据库中存在
     * @param checkedMessageIds 被检查的消息id
     * @return 已存在的消息id
     */
    public Set<String> checkMessageIfExistInDatabase(final Set<String> checkedMessageIds) {
        String sql = "select message_id from message_ack_consumes_success where message_id in (?)";
        // execute sql to get messageIds from database
        return new HashSet<>();
    }
    
    public void insertMessageIds(Set<String> messageIds) {
        String sql = "insert into message_ack_consumes_success (message_id) value (), (), ()....";
    }
    
}
