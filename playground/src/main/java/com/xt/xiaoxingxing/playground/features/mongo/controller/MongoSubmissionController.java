package com.xt.xiaoxingxing.playground.features.mongo.controller;

import com.xt.xiaoxingxing.playground.features.mongo.dto.request.SubmissionCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.SubmissionResponse;
import com.xt.xiaoxingxing.playground.features.mongo.service.MongoSubmissionService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 答卷提交与查询入口。 */
@Validated
@RestController
@RequestMapping("/api/playground/mongo")
@RequiredArgsConstructor
public class MongoSubmissionController {

    private final MongoSubmissionService submissionService;

    /** 提交答卷；用户来源于 playground 本地 users 表，一个用户对同一问卷只能提交一次。 */
    @PostMapping("/questionnaires/{questionnaireId}/submissions")
    public Result<SubmissionResponse> submit(
            @PathVariable String questionnaireId,
            @Valid @RequestBody SubmissionCreateRequest request) {
        return Result.ok(submissionService.submit(questionnaireId, request));
    }

    /** 根据 MongoDB 答卷ID查询详情。 */
    @GetMapping("/submissions/{id}")
    public Result<SubmissionResponse> getById(@PathVariable String id) {
        return Result.ok(submissionService.getById(id));
    }

    /** 查询某份问卷的答卷，并批量读取 playground 本地用户。 */
    @GetMapping("/questionnaires/{questionnaireId}/submissions")
    public Result<PageResult<SubmissionResponse>> pageByQuestionnaire(
            @PathVariable String questionnaireId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(submissionService.pageByQuestionnaire(questionnaireId, pageNum, pageSize));
    }

    /** 查询某个用户的历史答卷。 */
    @GetMapping("/users/{userId}/submissions")
    public Result<PageResult<SubmissionResponse>> pageByUser(
            @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "1") @Min(1) int pageNum,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize) {
        return Result.ok(submissionService.pageByUser(userId, pageNum, pageSize));
    }
}
