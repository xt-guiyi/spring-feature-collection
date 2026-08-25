package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行台账数据访问。 */
@Mapper
public interface XxlLearningExecutionMapper {
    XxlLearningExecution claim(@Param("execution") XxlLearningExecution execution,
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

    XxlLearningExecution selectByExecutionKey(@Param("executionKey") String executionKey);

    XxlLearningExecution selectById(@Param("id") long id);

    List<XxlLearningExecution> selectPage(@Param("handlerName") String handlerName,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("pageSize") int pageSize);

    long countPage(@Param("handlerName") String handlerName, @Param("status") String status);
}
