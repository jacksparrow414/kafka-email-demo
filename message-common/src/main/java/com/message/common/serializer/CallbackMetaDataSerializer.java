package com.message.common.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Serializer;

public class CallbackMetaDataSerializer implements Serializer<CallbackMetaData> {

    @SneakyThrows
    @Override
    public byte[] serialize(String s, CallbackMetaData callbackMetaData) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.writeValueAsBytes(callbackMetaData);
    }
}
