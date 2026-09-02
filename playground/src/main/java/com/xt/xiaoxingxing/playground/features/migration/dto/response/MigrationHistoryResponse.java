package com.xt.xiaoxingxing.playground.features.migration.dto.response;

import lombok.Data;

import java.time.Instant;

/** Flyway 单条迁移记录的只读详情。 */
@Data
public class MigrationHistoryResponse {

    /** 应用顺序；待执行或未安装记录为空。 */
    private Integer installedRank;

    /** 迁移版本；可重复迁移没有版本。 */
    private String version;

    /** 迁移描述。 */
    private String description;

    /** 迁移类型的稳定名称，例如 SQL。 */
    private String type;

    /** 迁移状态枚举名，例如 SUCCESS、PENDING 或 FAILED。 */
    private String state;

    /** 迁移脚本文件名。 */
    private String script;

    /** Flyway 记录的校验和。 */
    private Integer checksum;

    /** 安装时间；未安装时为空。 */
    private Instant installedOn;

    /** 执行迁移的数据库用户。 */
    private String installedBy;

    /** 执行耗时（毫秒）。 */
    private Integer executionTimeMs;

    /** 是否已经应用到数据库。 */
    private boolean applied;
}
