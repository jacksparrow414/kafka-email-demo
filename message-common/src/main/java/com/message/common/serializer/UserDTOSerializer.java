package com.message.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.UserDTO;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Serializer;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
public class UserDTOSerializer implements Serializer<UserDTO> {
    
    @Override
    @SneakyThrows
    public byte[] serialize(final String s, final UserDTO userDTO) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(userDTO);
    }
}
