package com.xt.xiaoxingxing.playground.xxljob.handler;

import com.xt.xiaoxingxing.playground.xxljob.config.XxlJobNames;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.BasicJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.SlowJobParam;
import com.xt.xiaoxingxing.playground.xxljob.enums.BasicJobOutcome;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobHandlerSupport;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/** XXL-JOB 基础任务示例。 */
@Component
public class XxlJobDemoHandler {

    private static final Logger log = LoggerFactory.getLogger(XxlJobDemoHandler.class);

    private final XxlJobHandlerSupport handlerSupport;

    public XxlJobDemoHandler(XxlJobHandlerSupport handlerSupport) {
        this.handlerSupport = handlerSupport;
    }

    /** 演示成功、主动失败和异常失败。 */
    @XxlJob(XxlJobNames.BASIC)
    public void xxlBasicJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.BASIC, context -> {
            BasicJobParam param = handlerSupport.parseParam(BasicJobParam.class);
            BasicJobOutcome outcome = param.getOutcome();

            switch (outcome) {
                case SUCCESS -> handlerSupport.handleSuccess(param.getMessage());
                case FAIL -> handlerSupport.handleFailure(param.getMessage());
                case EXCEPTION -> throw new IllegalStateException(
                        "基础任务按参数要求抛出异常：" + param.getMessage()
                );
            }
        });
    }

    /** 演示 JobThread 生命周期钩子。 */
    @XxlJob(value = XxlJobNames.LIFECYCLE, init = "init", destroy = "destroy")
    public void xxlLifecycleJobHandler() throws Exception {
        handlerSupport.execute(
                XxlJobNames.LIFECYCLE,
                context -> handlerSupport.handleSuccess(
                        "生命周期任务执行完成，jobId=" + context.getJobId() + "，logId=" + context.getLogId()
                )
        );
    }

    /** JobThread 创建时调用。 */
    public void init() {
        log.info("XXL-JOB lifecycle handler JobThread 初始化；该钩子不会在每次触发前重复执行");
    }

    /** JobThread 回收时调用。 */
    public void destroy() {
        log.info("XXL-JOB lifecycle handler JobThread 销毁");
    }

    /** 演示阻塞、超时和线程中断。 */
    @XxlJob(XxlJobNames.SLOW)
    public void xxlSlowJobHandler() throws Exception {
        handlerSupport.execute(XxlJobNames.SLOW, context -> {
            SlowJobParam param = handlerSupport.parseParam(SlowJobParam.class);
            for (int elapsedSeconds = 1; elapsedSeconds <= param.getSeconds(); elapsedSeconds++) {
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
                XxlJobHelper.log(
                        "慢任务进度：{}/{} 秒，logId={}",
                        elapsedSeconds,
                        param.getSeconds(),
                        context.getLogId()
                );
            }
            handlerSupport.handleSuccess("慢任务完成，共执行" + param.getSeconds() + "秒");
        });
    }
}
