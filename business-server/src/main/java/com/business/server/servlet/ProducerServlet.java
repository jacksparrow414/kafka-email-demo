package com.business.server.servlet;

import com.business.server.producer.MessageProducer;
import com.message.common.dto.UserDTO;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
        MessageProducer messageProducer = new MessageProducer();
        messageProducer.sendMessage(UserDTO.builder().messageId(UUID.randomUUID().toString()).userName(username).password(password).build());
    }
}
