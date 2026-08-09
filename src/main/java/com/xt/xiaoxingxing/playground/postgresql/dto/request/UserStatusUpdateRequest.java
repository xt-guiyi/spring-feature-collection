package com.xt.xiaoxingxing.playground.postgresql.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UserStatusUpdateRequest {

    private List<Long> ids;
    private String status;
}
