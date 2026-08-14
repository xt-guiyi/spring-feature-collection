package com.xt.xiaoxingxing.playground.xxljob.mapper;

import com.xt.xiaoxingxing.playground.xxljob.entity.XxlLearningExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/** 执行链的原子抢占、租约条件终结与运维查询。 */
@Mapper
public interface XxlLearningExecutionMapper {
    XxlLearningExecution claim(@Param("execution") XxlLearningExecution execution,
                               @Param("leaseSeconds") int leaseSeconds);

    int markSuccess(@Param("id") long id, @Param("leaseToken") String leaseToken,
                    @Param("resultMessage") String resultMessage);

    int markFailed(@Param("id") long id, @Param("leaseToken") String leaseToken,
                   @Param("lastError") String lastError);

    /** 周期类任务使用每次 logId 作为观察键；进程硬崩溃后由后续周期批量收口过期 RUNNING。 */
    int closeExpiredRunning(@Param("handlerName") String handlerName,
                            @Param("lastError") String lastError);

    XxlLearningExecution selectByExecutionKey(@Param("executionKey") String executionKey);

    XxlLearningExecution selectById(@Param("id") long id);

    List<XxlLearningExecution> selectPage(@Param("handlerName") String handlerName,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("pageSize") int pageSize);

    long countPage(@Param("handlerName") String handlerName, @Param("status") String status);
}
