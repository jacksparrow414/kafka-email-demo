package com.message.common.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * H2 嵌入式数据库工具类.
 * 两个webapp(business-server/message-server)运行在同一个Tomcat中, 但各自有独立的classloader,
 * 因此使用 AUTO_SERVER=TRUE 模式, 让多个classloader(甚至外部H2客户端工具)可以同时访问同一个文件库.
 *
 * 数据库文件默认位于 user.home/kafka-message-data/, 可通过系统属性 kafka.message.db.url 覆盖,
 * 例如: -Dkafka.message.db.url=jdbc:h2:file:/tmp/message-db;AUTO_SERVER=TRUE
 *
 * @author jacksparrow414
 */
public final class DbUtil {

    private static final String DEFAULT_URL =
        "jdbc:h2:file:" + System.getProperty("user.home") + "/kafka-message-data/message-db;AUTO_SERVER=TRUE";

    private static final String URL = System.getProperty("kafka.message.db.url", DEFAULT_URL);

    private static final String USER = "sa";

    private static final String PASSWORD = "";

    private static final String DDL = """
        CREATE TABLE IF NOT EXISTS message_failed (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          message_id VARCHAR(128) NOT NULL,
          message_content_json_format CLOB,
          message_type VARCHAR(32),
          failed_phase VARCHAR(32),
          failed_reason CLOB,
          retry_count INT DEFAULT 0,
          retry_status INT DEFAULT 0,
          last_update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_message_failed_retry ON message_failed(retry_status, retry_count);
        CREATE TABLE IF NOT EXISTS message_ack_consumes_success (
          message_id VARCHAR(128) PRIMARY KEY
        );
        """;

    static {
        try {
            Class.forName("org.h2.Driver");
            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                statement.execute(DDL);
            }
        } catch (Exception e) {
            throw new ExceptionInInitializerError("failed to init h2 database: " + e.getMessage());
        }
    }

    private DbUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
