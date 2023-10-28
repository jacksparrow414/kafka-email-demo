package com.message.common.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.UserDTO;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;

/**
 * @author jacksparrow414
 * @date 2023/10/14
 */
public class UserDTODeserializer implements Deserializer<UserDTO> {
    
    @Override
    @SneakyThrows
    public UserDTO deserialize(final String s, final byte[] bytes) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, UserDTO.class);
    }
}
