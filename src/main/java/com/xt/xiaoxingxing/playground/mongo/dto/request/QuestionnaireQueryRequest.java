package com.xt.xiaoxingxing.playground.mongo.dto.request;

import com.xt.xiaoxingxing.playground.mongo.enums.QuestionnaireStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** MongoTemplate 动态条件分页请求。 */
@Data
public class QuestionnaireQueryRequest {

    private String keyword;

    private QuestionnaireStatus status;

    private Long createdByUserId;

    @Min(value = 1, message = "页码必须大于等于1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "每页数量必须大于等于1")
    @Max(value = 100, message = "每页数量不能超过100")
    private Integer pageSize = 10;
}
