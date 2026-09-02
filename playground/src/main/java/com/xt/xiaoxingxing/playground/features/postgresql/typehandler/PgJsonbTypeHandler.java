package com.xt.xiaoxingxing.playground.features.postgresql.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * PostgreSQL {@code jsonb} 与 Jackson {@link JsonNode} 的双向类型转换器。
 *
 * <p>JDBC 并不知道项目希望把 JSONB 映射成哪个 Java 类型。写入时，本处理器把
 * JsonNode 序列化成 JSON 文本，再包装成类型为 {@code jsonb} 的 {@link PGobject}；
 * 读取时则把数据库返回的 JSON 文本反序列化为 JsonNode。</p>
 *
 * <p>MySQL 的 JDBC 驱动不使用 PostgreSQL 的 PGobject，因此这个 TypeHandler 不能
 * 原样复用到 MySQL。MySQL 同样支持 JSON 列，但需要按照 MySQL 驱动的参数绑定方式处理。</p>
 */
public class PgJsonbTypeHandler extends BaseTypeHandler<JsonNode> {

    /**
     * TypeHandler 由 MyBatis 反射创建，不依赖 Spring 注入，因此保留一个线程安全的 JsonMapper。
     * 本案例只处理标准 JSON 数据，不涉及需要额外模块的日期对象序列化。
     */
    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, JsonNode parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject jsonb = new PGobject();
        jsonb.setType("jsonb");
        jsonb.setValue(write(parameter));
        ps.setObject(i, jsonb);
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return read(rs.getString(columnName));
    }

    @Override
    public JsonNode getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return read(rs.getString(columnIndex));
    }

    @Override
    public JsonNode getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return read(cs.getString(columnIndex));
    }

    private String write(JsonNode value) throws SQLException {
        try {
            return JSON_MAPPER.writeValueAsString(value);
        } catch (JacksonException ex) {
            throw new SQLException("JSONB序列化失败", ex);
        }
    }

    private JsonNode read(String json) throws SQLException {
        if (json == null) {
            return null;
        }
        try {
            return JSON_MAPPER.readTree(json);
        } catch (JacksonException ex) {
            throw new SQLException("JSONB反序列化失败", ex);
        }
    }
}
