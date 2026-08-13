package com.business.server.servlet;

import com.business.server.callback.EmailSuccessCallback;
import com.business.server.producer.MessageProducer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import com.message.common.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Collections;
import java.util.UUID;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
@WebServlet(name = "producerServlet", urlPatterns = "/producerMessage")
public class ProducerServlet extends HttpServlet {

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }

    @Override
    protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");
        String messageId = UUID.randomUUID().toString();
        MessageProducer messageProducer = new MessageProducer();
        messageProducer.sendMessage(UserDTO.newBuilder()
            .setMessageId(messageId)
            .setUserName(username)
            .setPassword(password)
            .setCallbackMetaData(buildCallbackMetaData(messageId, username))
            .build());
    }

    /**
     * 构建演示用的回调消息: 邮件发送成功之后, 回调当前服务器的{@link EmailSuccessCallback#onSuccess()}
     */
    private CallbackMetaData buildCallbackMetaData(final String messageId, final String username) throws IOException {
        EmailSuccessCallback callback = new EmailSuccessCallback();
        callback.setMessageId(messageId);
        callback.setUserName(username);
        return CallbackMetaData.newBuilder()
            .setMessageId(messageId)
            .setServerId(InetAddress.getLocalHost().getHostName())
            .setClassName(EmailSuccessCallback.class.getName())
            .setInstanceJsonStr(new ObjectMapper().writeValueAsString(callback))
            .setMethodName("onSuccess")
            // Avro不支持任意类型参数, 约定每个元素是一个参数经Jackson序列化后的JSON字符串, 消费端再逐个解析
            .setArguments(Collections.emptyList())
            .build();
    }
}
