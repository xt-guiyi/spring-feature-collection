package com.xt.xiaoxingxing.playground.features.mongo.controller;

import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireCreateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireQueryRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.request.QuestionnaireUpdateRequest;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionnaireResponse;
import com.xt.xiaoxingxing.playground.features.mongo.dto.response.QuestionnaireStatisticsResponse;
import com.xt.xiaoxingxing.playground.features.mongo.service.MongoQuestionnaireService;
import com.xt.xiaoxingxing.playground.features.mongo.service.MongoQuestionnaireStatisticsService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MongoDB 动态问卷入口。
 *
 * <p>简单完整文档操作由 Repository 完成；动态分页、内嵌数组修改、状态迁移和聚合统计
 * 由 MongoTemplate 完成。Controller 只负责协议转换，不在这里拼装数据库查询。</p>
 */
@Validated
@RestController
@RequestMapping("/api/playground/mongo/questionnaires")
@RequiredArgsConstructor
public class MongoQuestionnaireController {

    private final MongoQuestionnaireService questionnaireService;
    private final MongoQuestionnaireStatisticsService statisticsService;

    /** 创建一个没有题目的 DRAFT 问卷。 */
    @PostMapping
    public Result<QuestionnaireResponse> create(@Valid @RequestBody QuestionnaireCreateRequest request) {
        return Result.ok(questionnaireService.create(request));
    }

    /** 查询问卷详情，并在 Service 中读取 playground 本地 users 表的创建人信息。 */
    @GetMapping("/{id}")
    public Result<QuestionnaireResponse> getById(@PathVariable String id) {
        return Result.ok(questionnaireService.getById(id));
    }

    /** 动态条件分页：keyword、status、createdByUserId 都可以省略。 */
    @GetMapping
    public Result<PageResult<QuestionnaireResponse>> page(
            @Valid @ModelAttribute QuestionnaireQueryRequest request) {
        return Result.ok(questionnaireService.page(request));
    }

    /** 只有 DRAFT 且版本匹配时才能修改基本信息。 */
    @PutMapping("/{id}")
    public Result<QuestionnaireResponse> update(
            @PathVariable String id,
            @RequestParam @PositiveOrZero long expectedVersion,
            @Valid @RequestBody QuestionnaireUpdateRequest request) {
        return Result.ok(questionnaireService.update(id, expectedVersion, request));
    }

    /** 使用 MongoDB $push 向 questions 数组原子追加一个题目。 */
    @PostMapping("/{id}/questions")
    public Result<QuestionnaireResponse> addQuestion(
            @PathVariable String id,
            @RequestParam @PositiveOrZero long expectedVersion,
            @Valid @RequestBody QuestionCreateRequest request) {
        return Result.ok(questionnaireService.addQuestion(id, expectedVersion, request));
    }

    /** 使用 questions.$ 位置运算符替换指定题目，不覆盖整个数组。 */
    @PutMapping("/{id}/questions/{questionId}")
    public Result<QuestionnaireResponse> updateQuestion(
            @PathVariable String id,
            @PathVariable String questionId,
            @RequestParam @PositiveOrZero long expectedVersion,
            @Valid @RequestBody QuestionUpdateRequest request) {
        return Result.ok(questionnaireService.updateQuestion(id, questionId, expectedVersion, request));
    }

    /** 使用 MongoDB $pull 从 questions 数组删除指定题目。 */
    @DeleteMapping("/{id}/questions/{questionId}")
    public Result<QuestionnaireResponse> deleteQuestion(
            @PathVariable String id,
            @PathVariable String questionId,
            @RequestParam @PositiveOrZero long expectedVersion) {
        return Result.ok(questionnaireService.deleteQuestion(id, questionId, expectedVersion));
    }

    /** DRAFT → PUBLISHED；问卷至少包含一道题。 */
    @PostMapping("/{id}/publish")
    public Result<QuestionnaireResponse> publish(
            @PathVariable String id,
            @RequestParam @PositiveOrZero long expectedVersion) {
        return Result.ok(questionnaireService.publish(id, expectedVersion));
    }

    /** PUBLISHED → CLOSED；关闭后保留查询和统计，但不再接受答卷。 */
    @PostMapping("/{id}/close")
    public Result<QuestionnaireResponse> close(
            @PathVariable String id,
            @RequestParam @PositiveOrZero long expectedVersion) {
        return Result.ok(questionnaireService.close(id, expectedVersion));
    }

    /** 只允许删除 DRAFT 问卷，避免删除已经产生答卷的业务数据。 */
    @DeleteMapping("/{id}")
    public Result<Boolean> deleteDraft(
            @PathVariable String id,
            @RequestParam @PositiveOrZero long expectedVersion) {
        return Result.ok(questionnaireService.deleteDraft(id, expectedVersion));
    }

    /** 使用 $unwind、$group、$avg、$min、$max 统计所有题目。 */
    @GetMapping("/{id}/statistics")
    public Result<QuestionnaireStatisticsResponse> statistics(@PathVariable String id) {
        return Result.ok(statisticsService.statistics(id));
    }
}
