package com.message.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/**
 * 对于builder的deserializer，需要在builder类上加上@JsonDeserialize(builder = UserDTO.UserDTOBuilder.class)
 * @author jacksparrow414
 * @date 2023/10/14
 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@JsonDeserialize(builder = UserDTO.UserDTOBuilder.class)
@EqualsAndHashCode(of = {"messageId"})
public class UserDTO {
    
    @JsonProperty("messageId")
    private String messageId;
    
    @JsonProperty("userName")
    private String userName;
    
    @JsonProperty("password")
    private String password;

    @JsonProperty("callbackMetaData")
    private CallbackMetaData callbackMetaData;
}
