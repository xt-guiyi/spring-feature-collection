package com.xt.xiaoxingxing.playground.postgresql.mapper;

import com.xt.xiaoxingxing.playground.postgresql.dto.request.UserQueryRequest;
import com.xt.xiaoxingxing.playground.postgresql.entity.PgUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * users 表的普通 MyBatis Mapper。
 *
 * <p>接口只定义 Java 契约，SQL 全部位于同名 XML 中，便于学习参数绑定、
 * 动态标签、批量 foreach 和 PostgreSQL RETURNING。</p>
 */
@Mapper
public interface PgMyBatisUserMapper {

    /** 插入用户并通过 PostgreSQL RETURNING 返回主键。 */
    Long insertUser(PgUser user);

    /** 按主键查询单个用户。 */
    PgUser selectUserById(@Param("id") Long id);

    /** 查询全部用户，并由 XML 按 ID 排序。 */
    List<PgUser> selectAllUsers();

    /** 使用动态 set 选择性更新。 */
    int updateUser(PgUser user);

    /** 按主键删除，返回受影响行数。 */
    int deleteUserById(@Param("id") Long id);

    /** 演示 LIKE、等值、IN 与 IS NULL 动态条件。 */
    List<PgUser> selectUsersByCondition(UserQueryRequest request);

    /** 统计总行数，供手写分页计算总页数。 */
    long countUsers();

    /** 使用 LIMIT/OFFSET 查询指定页。 */
    List<PgUser> selectUserPage(@Param("offset") long offset, @Param("pageSize") int pageSize);

    /** foreach 生成多组 VALUES，完成单条 SQL 批量插入。 */
    int batchInsertUsers(@Param("users") List<PgUser> users);

    /** 根据 ID 集合批量更新状态。 */
    int batchUpdateUserStatus(@Param("ids") List<Long> ids, @Param("status") String status);

    /** 根据 ID 集合批量删除。 */
    int batchDeleteUsers(@Param("ids") List<Long> ids);
}
