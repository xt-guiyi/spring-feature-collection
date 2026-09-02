package com.xt.xiaoxingxing.playground.features.migration.service;

import com.xt.xiaoxingxing.playground.features.migration.dto.response.MigrationHistoryResponse;
import com.xt.xiaoxingxing.playground.features.migration.dto.response.MigrationStatusResponse;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.Configuration;
import org.flywaydb.core.api.output.ValidateResult;
import org.flywaydb.core.extensibility.MigrationType;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * Flyway 迁移状态查询服务。
 *
 * <p>本服务只调用 Flyway 的 {@code info()} 和
 * {@code validateWithResult()}，不暴露 migrate、repair、clean 或 undo 等
 * 会改变数据库状态的操作。</p>
 */
@Service
@RequiredArgsConstructor
public class FlywayMigrationService {

    private static final String DEFAULT_SCHEMA = "flyway_migration";
    private static final String DEFAULT_HISTORY_TABLE = "flyway_schema_history";

    private final Flyway flyway;

    /** 查询当前 Flyway 迁移概览。 */
    public MigrationStatusResponse status() {
        MigrationInfoService infoService = flyway.info();
        MigrationInfo[] allMigrations = allMigrations(infoService);
        ValidateResult validationResult = flyway.validateWithResult();

        MigrationStatusResponse status = new MigrationStatusResponse();
        status.setSchema(configuredSchema());
        status.setHistoryTable(configuredHistoryTable());

        MigrationInfo current = infoService == null ? null : infoService.current();
        status.setCurrentVersion(versionOf(current));
        status.setCurrentDescription(current == null ? null : current.getDescription());

        status.setAppliedCount(appliedCount(infoService, allMigrations));
        status.setPendingCount(pendingCount(infoService, allMigrations));
        status.setFailedCount(failedCount(allMigrations));

        status.setValid(validationResult != null && validationResult.validationSuccessful);
        status.setValidationMessage(validationMessage(validationResult));
        return status;
    }

    /** 查询 Flyway 已解析、已应用及待执行迁移的完整详情。 */
    public List<MigrationHistoryResponse> history() {
        MigrationInfoService infoService = flyway.info();
        return Arrays.stream(allMigrations(infoService))
                .filter(Objects::nonNull)
                .sorted(Comparator.naturalOrder())
                .map(this::toHistory)
                .toList();
    }

    private MigrationInfo[] allMigrations(MigrationInfoService infoService) {
        if (infoService == null) {
            return new MigrationInfo[0];
        }
        MigrationInfo[] migrations = infoService.all();
        return migrations == null ? new MigrationInfo[0] : migrations;
    }

    private int appliedCount(MigrationInfoService infoService, MigrationInfo[] allMigrations) {
        if (infoService != null) {
            MigrationInfo[] appliedMigrations = infoService.applied();
            if (appliedMigrations != null) {
                return appliedMigrations.length;
            }
        }
        return (int) Arrays.stream(allMigrations)
                .filter(Objects::nonNull)
                .filter(MigrationInfo::isApplied)
                .count();
    }

    private int pendingCount(MigrationInfoService infoService, MigrationInfo[] allMigrations) {
        if (infoService != null) {
            MigrationInfo[] pendingMigrations = infoService.pending();
            if (pendingMigrations != null) {
                return pendingMigrations.length;
            }
        }
        return (int) Arrays.stream(allMigrations)
                .filter(Objects::nonNull)
                .map(MigrationInfo::getState)
                .filter(MigrationState.PENDING::equals)
                .count();
    }

    private int failedCount(MigrationInfo[] allMigrations) {
        return (int) Arrays.stream(allMigrations)
                .filter(Objects::nonNull)
                .map(MigrationInfo::getState)
                .filter(Objects::nonNull)
                .filter(MigrationState::isFailed)
                .count();
    }

    private String configuredSchema() {
        Configuration configuration = flyway.getConfiguration();
        if (configuration == null) {
            return DEFAULT_SCHEMA;
        }

        String defaultSchema = configuration.getDefaultSchema();
        if (hasText(defaultSchema)) {
            return defaultSchema;
        }

        String[] schemas = configuration.getSchemas();
        if (schemas != null) {
            for (String schema : schemas) {
                if (hasText(schema)) {
                    return schema;
                }
            }
        }
        return DEFAULT_SCHEMA;
    }

    private String configuredHistoryTable() {
        Configuration configuration = flyway.getConfiguration();
        if (configuration == null || !hasText(configuration.getTable())) {
            return configuredSchema() + "." + DEFAULT_HISTORY_TABLE;
        }
        return configuredSchema() + "." + configuration.getTable();
    }

    private MigrationHistoryResponse toHistory(MigrationInfo source) {
        MigrationHistoryResponse target = new MigrationHistoryResponse();
        target.setInstalledRank(source.getInstalledRank());
        target.setVersion(versionOf(source));
        target.setDescription(source.getDescription());
        target.setType(typeName(source.getType()));
        target.setState(stateName(source.getState()));
        target.setScript(source.getScript());
        target.setChecksum(source.getChecksum());
        target.setInstalledOn(toInstant(source.getInstalledOn()));
        target.setInstalledBy(source.getInstalledBy());
        target.setExecutionTimeMs(source.getExecutionTime());
        target.setApplied(source.isApplied());
        return target;
    }

    private String versionOf(MigrationInfo migrationInfo) {
        if (migrationInfo == null) {
            return null;
        }
        MigrationVersion version = migrationInfo.getVersion();
        return version == null ? null : version.getVersion();
    }

    private String typeName(MigrationType type) {
        return type == null ? null : type.name();
    }

    private String stateName(MigrationState state) {
        return state == null ? null : state.name();
    }

    private Instant toInstant(Date installedOn) {
        return installedOn == null ? null : installedOn.toInstant();
    }

    private String validationMessage(ValidateResult validationResult) {
        if (validationResult == null || validationResult.validationSuccessful) {
            return null;
        }

        String message = validationResult.getAllErrorMessages();
        if (hasText(message)) {
            return message;
        }

        if (validationResult.errorDetails != null && hasText(validationResult.errorDetails.errorMessage)) {
            return validationResult.errorDetails.errorMessage;
        }
        return "Flyway 迁移校验失败";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
