package com.message.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@JsonDeserialize(builder = CallbackMetaData.CallbackMetaDataBuilder.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
@ToString
public class CallbackMetaData implements Serializable {

    @JsonProperty("id")
    private String messageId;

    @JsonProperty("serverId")
    private String serverId;

    @JsonProperty("className")
    private String className;

    /**
     * this string is the json string of the instance of the class, generated by Jackson.
     * for example:
     * className instance = new className();
     * objectMapper.writeValueAsString(instance);
     */
    @JsonProperty("instanceJsonStr")
    private String instanceJsonStr;

    @JsonProperty("methodName")
    private String methodName;

    @JsonProperty("arguments")
    private Object[] arguments;

}
