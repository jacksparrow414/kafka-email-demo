package com.message.common.service;

import com.message.common.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.java.Log;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
@Log
public class MessageAckConsumesSuccessService {

    /**
     * 检查消息是否在数据库中存在
     * @param checkedMessageIds 被检查的消息id
     * @return 已存在的消息id
     */
    public Set<String> checkMessageIfExistInDatabase(final Set<String> checkedMessageIds) {
        if (checkedMessageIds.isEmpty()) {
            return Collections.emptySet();
        }
        String placeholders = String.join(", ", Collections.nCopies(checkedMessageIds.size(), "?"));
        String sql = "select message_id from message_ack_consumes_success where message_id in (" + placeholders + ")";
        Set<String> existed = new HashSet<>();
        try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (String messageId : checkedMessageIds) {
                ps.setString(index++, messageId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existed.add(rs.getString("message_id"));
                }
            }
        } catch (SQLException e) {
            log.severe("check message_ack_consumes_success failed: " + e.getMessage());
        }
        return existed;
    }

    /**
     * 消费成功之后, 将messageId批量插入到数据库中, 用于消息的幂等性判断
     * @param messageIds 消费成功的消息id
     */
    public void insertMessageIds(final Set<String> messageIds) {
        if (messageIds.isEmpty()) {
            return;
        }
        String sql = "merge into message_ack_consumes_success key(message_id) values (?)";
        try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            for (String messageId : messageIds) {
                ps.setString(1, messageId);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            log.severe("insert message_ack_consumes_success failed: " + e.getMessage());
        }
    }
}
