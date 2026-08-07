package com.message.common.service;

import com.message.common.entity.MessageFailedEntity;
import com.message.common.enums.MessageFailedPhase;
import com.message.common.enums.MessageType;
import com.message.common.util.DbUtil;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.extern.java.Log;

/**
 * @author jacksparrow414
 * @date 2023/10/28
 */
@Log
public class MessageFailedService {

    /**
     * 查询消息失败的记录
     * @param messageIds 要检查的消息id
     * @param failedPhase 消息失败的阶段
     * @return 消息重试状态为0、重试次数小于3的失败的消息
     */
    public List<MessageFailedEntity> queryMessageFailedByMessageIds(final List<String> messageIds, String failedPhase) {
        if (messageIds.isEmpty()) {
            return Collections.emptyList();
        }
        String placeholders = String.join(", ", Collections.nCopies(messageIds.size(), "?"));
        String sql = "select id, message_id, message_content_json_format, message_type, failed_phase, failed_reason, retry_count, retry_status, last_update_time"
            + " from message_failed where message_id in (" + placeholders + ") and failed_phase = ? and retry_status = 0 and retry_count < 3";
        List<MessageFailedEntity> result = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            for (String messageId : messageIds) {
                ps.setString(index++, messageId);
            }
            ps.setString(index, failedPhase);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            log.severe("query message_failed by messageIds failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * 保存或者更新消息失败的记录
     * 如果消息失败的记录不存在，则保存
     * 如果消息失败的记录存在，则更新，更新可能是更新为重试成功，也可能是更新为重试失败，无论如何，都是更新重试次数+1，更新当前时间
     * @param messageFailedEntity 消息失败的记录
     */
    public void saveOrUpdateMessageFailed(final MessageFailedEntity messageFailedEntity) {
        List<MessageFailedEntity> messageFailedEntities = queryMessageFailedByMessageIds(
            Collections.singletonList(messageFailedEntity.getMessageId()), messageFailedEntity.getMessageFailedPhase().name());
        if (messageFailedEntities.isEmpty()) {
            String insertSql = "insert into message_failed(message_id, message_content_json_format, message_type, failed_phase, failed_reason, retry_count, retry_status, last_update_time)"
                + " values(?, ?, ?, ?, ?, ?, ?, ?)";
            try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(insertSql)) {
                ps.setString(1, messageFailedEntity.getMessageId());
                ps.setString(2, messageFailedEntity.getMessageContentJsonFormat());
                ps.setString(3, messageFailedEntity.getMessageType() == null ? null : messageFailedEntity.getMessageType().name());
                ps.setString(4, messageFailedEntity.getMessageFailedPhase() == null ? null : messageFailedEntity.getMessageFailedPhase().name());
                ps.setString(5, messageFailedEntity.getFailedReason());
                ps.setInt(6, messageFailedEntity.getRetryCount() == null ? 0 : messageFailedEntity.getRetryCount());
                ps.setInt(7, messageFailedEntity.getRetryStatus() == null ? 0 : messageFailedEntity.getRetryStatus());
                ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
                ps.executeUpdate();
            } catch (SQLException e) {
                log.severe("insert message_failed failed: " + e.getMessage());
            }
        } else {
            String updateSql = "update message_failed set retry_count = retry_count + 1, retry_status = ?, failed_reason = ?, last_update_time = ? where id = ?";
            try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(updateSql)) {
                for (MessageFailedEntity each : messageFailedEntities) {
                    ps.setInt(1, messageFailedEntity.getRetryStatus() == null ? 0 : messageFailedEntity.getRetryStatus());
                    ps.setString(2, messageFailedEntity.getFailedReason());
                    ps.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
                    ps.setLong(4, each.getId());
                    ps.addBatch();
                }
                ps.executeBatch();
            } catch (SQLException e) {
                log.severe("update message_failed failed: " + e.getMessage());
            }
        }
    }

    /**
     * 消息重试成功之后调用, 只有已存在失败记录时才更新为重试成功, 不存在则不新增任何记录
     * @param messageId 消息id
     * @param failedPhase 消息失败的阶段
     */
    public void markRetrySuccessIfExists(final String messageId, final MessageFailedPhase failedPhase) {
        String sql = "update message_failed set retry_status = 1, retry_count = retry_count + 1, last_update_time = ?"
            + " where message_id = ? and failed_phase = ? and retry_status = 0";
        try (Connection connection = DbUtil.getConnection(); PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now()));
            ps.setString(2, messageId);
            ps.setString(3, failedPhase.name());
            ps.executeUpdate();
        } catch (SQLException e) {
            log.severe("mark message_failed retry success failed: " + e.getMessage());
        }
    }

    /**
     * 查询未达到重试次数的消息
     * @return 未达到重试次数的消息
     */
    public List<MessageFailedEntity> queryMessageFailedUnReachedRetryCount() {
        String sql = "select id, message_id, message_content_json_format, message_type, failed_phase, failed_reason, retry_count, retry_status, last_update_time"
            + " from message_failed where retry_status = 0 and retry_count < 3 order by last_update_time asc limit 1000";
        List<MessageFailedEntity> result = new ArrayList<>();
        try (Connection connection = DbUtil.getConnection();
             PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(mapRow(rs));
            }
        } catch (SQLException e) {
            log.severe("query message_failed unreached retry count failed: " + e.getMessage());
        }
        return result;
    }

    private MessageFailedEntity mapRow(final ResultSet rs) throws SQLException {
        MessageFailedEntity entity = new MessageFailedEntity();
        entity.setId(rs.getLong("id"));
        entity.setMessageId(rs.getString("message_id"));
        entity.setMessageContentJsonFormat(rs.getString("message_content_json_format"));
        String messageType = rs.getString("message_type");
        entity.setMessageType(messageType == null ? null : MessageType.valueOf(messageType));
        String failedPhase = rs.getString("failed_phase");
        entity.setMessageFailedPhase(failedPhase == null ? null : MessageFailedPhase.valueOf(failedPhase));
        entity.setFailedReason(rs.getString("failed_reason"));
        entity.setRetryCount(rs.getInt("retry_count"));
        entity.setRetryStatus(rs.getInt("retry_status"));
        Timestamp lastUpdateTime = rs.getTimestamp("last_update_time");
        entity.setLastUpdateTime(lastUpdateTime == null ? null : lastUpdateTime.toLocalDateTime());
        return entity;
    }
}
