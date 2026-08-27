package com.xt.xiaoxingxing.playground.elasticsearch.controller;

import com.xt.xiaoxingxing.playground.elasticsearch.service.ArticleIndexManager;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleBulkResultVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleIndexStatusVO;
import com.xt.xiaoxingxing.playground.elasticsearch.vo.ArticleRebuildVO;
import com.xt.xiaoxingxing.shared.common.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Elasticsearch 文章索引初始化、固定数据与 Alias 迁移入口。 */
@RestController
@RequestMapping("/api/playground/elasticsearch/index")
public class ArticleIndexController {

    private final ArticleIndexManager articleIndexManager;

    /** 注入文章索引管理器。 */
    public ArticleIndexController(ArticleIndexManager articleIndexManager) {
        this.articleIndexManager = articleIndexManager;
    }

    /** 初始化文章索引和查询别名。 */
    @PostMapping("/initialize")
    public Result<ArticleIndexStatusVO> initialize() {
        return Result.ok(articleIndexManager.initialize());
    }

    /** 写入固定的演示文章数据。 */
    @PostMapping("/seed")
    public Result<ArticleBulkResultVO> seed() {
        return Result.ok(articleIndexManager.seed());
    }

    /** 查询当前文章索引和别名状态。 */
    @GetMapping("/status")
    public Result<ArticleIndexStatusVO> status() {
        return Result.ok(articleIndexManager.status());
    }

    /** 重建文章索引并切换查询别名。 */
    @PostMapping("/rebuild")
    public Result<ArticleRebuildVO> rebuild() {
        return Result.ok(articleIndexManager.rebuild());
    }

    /** 删除指定索引或 Alias 下的全部文章数据。 */
    @DeleteMapping("/{indexName}/documents")
    public Result<Long> deleteAllDocuments(@PathVariable String indexName) {
        return Result.ok(articleIndexManager.deleteAllDocuments(indexName));
    }

    /** 删除指定索引或 Alias 下的一篇文章。 */
    @DeleteMapping("/{indexName}/documents/{id}")
    public Result<Boolean> deleteDocument(@PathVariable String indexName,
                                          @PathVariable String id) {
        return Result.ok(articleIndexManager.deleteDocument(indexName, id));
    }

    /** 删除指定的 Elasticsearch 索引。 */
    @DeleteMapping("/{indexName}")
    public Result<Boolean> deleteIndex(@PathVariable String indexName) {
        return Result.ok(articleIndexManager.deleteIndex(indexName));
    }
}
