package com.message.common.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.message.common.dto.CallbackMetaData;
import lombok.SneakyThrows;
import org.apache.kafka.common.serialization.Deserializer;

public class CallbackMetaDataDeserializer implements Deserializer<CallbackMetaData> {

    @SneakyThrows
    @Override
    public CallbackMetaData deserialize(String s, byte[] bytes) {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(bytes, CallbackMetaData.class);
    }
}
