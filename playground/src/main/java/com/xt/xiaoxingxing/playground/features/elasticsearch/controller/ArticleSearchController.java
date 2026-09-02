package com.xt.xiaoxingxing.playground.features.elasticsearch.controller;

import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleAggregationRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleCursorRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleHybridRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleKnnRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.request.ArticleSearchRequest;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleAggregationResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleCursorPageResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.dto.response.ArticleSearchHitResponse;
import com.xt.xiaoxingxing.playground.features.elasticsearch.service.ArticleSearchService;
import com.xt.xiaoxingxing.shared.core.response.PageResult;
import com.xt.xiaoxingxing.shared.core.response.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api/playground/elasticsearch/articles")
public class ArticleSearchController {

    private final ArticleSearchService articleSearchService;

    /** 注入文章检索服务。 */
    public ArticleSearchController(ArticleSearchService articleSearchService) {
        this.articleSearchService = articleSearchService;
    }

    /** 按条件分页检索文章。 */
    @PostMapping("/search")
    public Result<PageResult<ArticleSearchHitResponse>> search(
        @Valid @RequestBody ArticleSearchRequest request) {
        return Result.ok(articleSearchService.search(request));
    }

    /** 统计文章的聚合结果。 */
    @PostMapping("/aggregations")
    public Result<ArticleAggregationResponse> aggregations(
            @Valid @RequestBody ArticleAggregationRequest request) {
        return Result.ok(articleSearchService.aggregations(request));
    }

    /** 按标题前缀返回文章补全建议。 */
    @GetMapping("/suggestions")
    public Result<List<String>> suggestions(
            @RequestParam @NotBlank(message = "补全前缀不能为空")
            @Size(max = 100, message = "补全前缀长度不能超过100") String prefix,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "补全数量必须大于0")
            @Max(value = 10, message = "补全数量不能超过10") int size) {
        return Result.ok(articleSearchService.suggestions(prefix, size));
    }

    /** 使用 completion suggester 按标题开头返回文章补全建议。 */
    @GetMapping("/completion-suggestions")
    public Result<List<String>> completionSuggestions(
            @RequestParam @NotBlank(message = "补全前缀不能为空")
            @Size(max = 100, message = "补全前缀长度不能超过100") String prefix,
            @RequestParam(defaultValue = "5") @Min(value = 1, message = "补全数量必须大于0")
            @Max(value = 10, message = "补全数量不能超过10") int size) {
        return Result.ok(articleSearchService.completionSuggestions(prefix, size));
    }

    /** 使用游标深分页检索文章。 */
    @PostMapping("/cursor")
    public Result<ArticleCursorPageResponse> cursor(@Valid @RequestBody ArticleCursorRequest request) {
        return Result.ok(articleSearchService.cursor(request));
    }

    /** 使用向量近邻检索文章。 */
    @PostMapping("/knn")
    public Result<List<ArticleSearchHitResponse>> knn(@Valid @RequestBody ArticleKnnRequest request) {
        return Result.ok(articleSearchService.knn(request));
    }

    /** 结合关键词和向量结果检索文章。 */
    @PostMapping("/hybrid")
    public Result<List<ArticleSearchHitResponse>> hybrid(@Valid @RequestBody ArticleHybridRequest request) {
        return Result.ok(articleSearchService.hybrid(request));
    }
}
