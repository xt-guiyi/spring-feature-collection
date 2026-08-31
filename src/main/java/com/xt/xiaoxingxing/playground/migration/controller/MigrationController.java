package com.xt.xiaoxingxing.playground.migration.controller;

import com.xt.xiaoxingxing.playground.migration.service.FlywayMigrationService;
import com.xt.xiaoxingxing.playground.migration.vo.MigrationHistoryVO;
import com.xt.xiaoxingxing.playground.migration.vo.MigrationStatusVO;
import com.xt.xiaoxingxing.shared.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Flyway 数据库迁移学习模块的只读接口。 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/playground/migration")
public class MigrationController {

    private final FlywayMigrationService migrationService;

    /** 查询当前迁移版本、数量和校验状态。 */
    @GetMapping("/status")
    public Result<MigrationStatusVO> status() {
        return Result.ok(migrationService.status());
    }

    /** 查询所有迁移的安装和解析详情。 */
    @GetMapping("/history")
    public Result<List<MigrationHistoryVO>> history() {
        return Result.ok(migrationService.history());
    }
}
