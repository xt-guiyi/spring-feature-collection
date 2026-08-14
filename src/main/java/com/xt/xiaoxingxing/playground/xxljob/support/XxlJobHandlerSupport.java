package com.xt.xiaoxingxing.playground.xxljob.support;

import com.xxl.job.core.context.XxlJobHelper;
import org.springframework.stereotype.Component;

/**
 * 所有 Bean Handler 共用的入口、结果和失败语义。
 *
 * <p>核心原则是：成功必须在业务完成后明确回报；主动失败使用 {@code handleFail}；异常则记录完整堆栈并继续抛出，
 * 让 XXL-JOB 的 JobThread 生成失败回调和失败重试。这里绝不能 catch 后静默返回，否则框架默认的成功状态会掩盖故障。</p>
 */
@Component
public class XxlJobHandlerSupport {

    private final XxlJobParamParser paramParser;

    public XxlJobHandlerSupport(XxlJobParamParser paramParser) {
        this.paramParser = paramParser;
    }

    public <T> T parseParam(Class<T> parameterType) {
        return paramParser.parse(parameterType);
    }

    /**
     * 统一建立上下文并执行 Handler 业务。
     *
     * <ol>
     *     <li>在仍处于 XXL-JOB JobThread 时截取调度身份和分片信息；</li>
     *     <li>执行参数解析与业务逻辑，业务方法必须在返回前完成副作用，不能 fire-and-forget；</li>
     *     <li>任何 Exception/Error 都先写入 Rolling Log，再原样抛出交给 XXL-JOB 判定失败。</li>
     * </ol>
     */
    public void execute(String handlerName, JobAction action) throws Exception {
        try {
            XxlJobRunContext context = XxlJobRunContext.current();
            XxlJobHelper.log(
                    "[{}]开始执行，jobId={}，logId={}，shard={}/{}",
                    handlerName,
                    context.getJobId(),
                    context.getLogId(),
                    context.getShardIndex(),
                    context.getShardTotal()
            );
            action.run(context);
        } catch (Exception exception) {
            XxlJobHelper.log(exception);
            throw exception;
        } catch (Error error) {
            XxlJobHelper.log(error);
            throw error;
        }
    }

    /** 使用业务服务返回的同一段说明同时写 Rolling Log 和执行结果。 */
    public void handleSuccess(String result) {
        XxlJobHelper.log("任务业务结果：{}", result);
        XxlJobHelper.handleSuccess(result);
    }

    /** 演示“没有抛异常但业务明确失败”的路径；调用后 Handler 应立即返回。 */
    public void handleFailure(String reason) {
        XxlJobHelper.log("任务主动标记失败：{}", reason);
        XxlJobHelper.handleFail(reason);
    }

    @FunctionalInterface
    public interface JobAction {

        void run(XxlJobRunContext context) throws Exception;
    }
}
