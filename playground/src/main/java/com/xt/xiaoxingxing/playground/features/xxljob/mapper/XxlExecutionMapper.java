package com.xt.xiaoxingxing.playground.features.xxljob.mapper;

import com.xt.xiaoxingxing.playground.features.xxljob.entity.XxlExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行台账数据访问。 */
@Mapper
public interface XxlExecutionMapper {
    XxlExecution claim(@Param("execution") XxlExecution execution,
                               @Param("leaseSeconds") int leaseSeconds);

    int markSuccess(@Param("id") long id, @Param("leaseToken") String leaseToken,
                    @Param("resultMessage") String resultMessage);

    int markFailed(@Param("id") long id, @Param("leaseToken") String leaseToken,
                   @Param("lastError") String lastError);

    int renewLease(@Param("id") long id, @Param("leaseToken") String leaseToken,
                   @Param("leaseSeconds") int leaseSeconds);

    /** 关闭过期运行记录。 */
    void closeExpiredRunning(@Param("handlerName") String handlerName,
                             @Param("lastError") String lastError);

    XxlExecution selectByExecutionKey(@Param("executionKey") String executionKey);

    XxlExecution selectById(@Param("id") long id);

    List<XxlExecution> selectPage(@Param("handlerName") String handlerName,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("pageSize") int pageSize);

    long countPage(@Param("handlerName") String handlerName, @Param("status") String status);
}
