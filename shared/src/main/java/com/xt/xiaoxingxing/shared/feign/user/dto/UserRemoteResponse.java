package com.xt.xiaoxingxing.shared.feign.user.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/** user-service 对外返回的用户数据契约。 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserRemoteResponse {

    private Long id;

    private String username;

    private String email;

    private String status;

    private LocalDateTime createTime;
}
