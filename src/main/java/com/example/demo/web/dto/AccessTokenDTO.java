package com.example.demo.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Created by jerry on 2018/3/13.
 *
 * @author jerry
 */
@Data
public class AccessTokenDTO {

    @JsonProperty(value = "access_token")
    private String accessToken;

    @JsonProperty(value = "token_type")
    private String tokenType;

    @JsonProperty(value = "expires_in")
    private String expiresIn;
}
