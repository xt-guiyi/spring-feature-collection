package com.xt.xiaoxingxing.playground.features.migration.dto.response;

import lombok.Data;

/** Flyway 当前迁移状态的只读摘要。 */
@Data
public class MigrationStatusResponse {

    /** Flyway 管理的默认 schema。 */
    private String schema;

    /** schema history 表的完整名称，例如 flyway_migration.flyway_schema_history。 */
    private String historyTable;

    /** 当前已应用迁移版本；尚未应用任何迁移时为空。 */
    private String currentVersion;

    /** 当前已应用迁移描述；尚未应用任何迁移时为空。 */
    private String currentDescription;

    /** 已应用迁移数量。 */
    private Integer appliedCount;

    /** 待执行迁移数量。 */
    private Integer pendingCount;

    /** 失败迁移数量。 */
    private Integer failedCount;

    /** 当前迁移校验是否通过。 */
    private boolean valid;

    /** 校验失败时的简短原因；校验通过时为空。 */
    private String validationMessage;
}
