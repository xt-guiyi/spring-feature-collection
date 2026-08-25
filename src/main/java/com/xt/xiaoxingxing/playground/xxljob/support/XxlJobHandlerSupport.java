package com.xt.xiaoxingxing.playground.xxljob.support;

import com.xxl.job.core.context.XxlJobHelper;
import org.springframework.stereotype.Component;

/** 统一处理 Handler 上下文、日志和执行结果。 */
@Component
public class XxlJobHandlerSupport {

    private final XxlJobParamParser paramParser;

    public XxlJobHandlerSupport(XxlJobParamParser paramParser) {
        this.paramParser = paramParser;
    }

    public <T> T parseParam(Class<T> parameterType) {
        return paramParser.parse(parameterType);
    }

    /** 执行业务并将异常写入 Rolling Log 后继续抛出。 */
    public void execute(String handlerName, JobAction action) throws Exception {
        try {
            XxlJobRunContext context = XxlJobRunContext.current();
            XxlJobHelper.log(
                    "[{}]开始执行，jobId={}，logId={}，shard={}/{}",
                    handlerName,
                    context.jobId(),
                    context.logId(),
                    context.shardIndex(),
                    context.shardTotal()
            );
            action.run(context);
        } catch (Exception | Error exception) {
            XxlJobHelper.log(exception);
            throw exception;
        }
    }

    /** 记录成功结果。 */
    public void handleSuccess(String result) {
        XxlJobHelper.log("任务业务结果：{}", result);
        XxlJobHelper.handleSuccess(result);
    }

    /** 记录主动失败结果。 */
    public void handleFailure(String reason) {
        XxlJobHelper.log("任务主动标记失败：{}", reason);
        XxlJobHelper.handleFail(reason);
    }

    @FunctionalInterface
    public interface JobAction {

        void run(XxlJobRunContext context) throws Exception;
    }
}
