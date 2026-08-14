package com.xt.xiaoxingxing.playground.xxljob.handler;

import com.xt.xiaoxingxing.playground.xxljob.dto.request.BasicJobOutcome;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.BasicJobParam;
import com.xt.xiaoxingxing.playground.xxljob.dto.request.SlowJobParam;
import com.xt.xiaoxingxing.playground.xxljob.support.XxlJobHandlerSupport;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * XXL-JOB Bean 模式的最小 API 与线程生命周期学习案例。
 *
 * <p>当前 3.4.2 的方法签名是无参数 {@code void}；任务参数和结果都通过 {@link XxlJobHelper} 交互。
 * 本类刻意不出现旧版 {@code ReturnT}，避免把已废弃教程的返回值语义带进新代码。</p>
 */
@Component
public class XxlJobDemoHandler {

    private static final Logger log = LoggerFactory.getLogger(XxlJobDemoHandler.class);

    private final XxlJobHandlerSupport handlerSupport;

    public XxlJobDemoHandler(XxlJobHandlerSupport handlerSupport) {
        this.handlerSupport = handlerSupport;
    }

    /**
     * 对比三种执行结果：默认成功、主动失败和抛异常失败。
     *
     * <p>主动失败适合“参数合法但业务决定拒绝”的场景；异常必须继续冒泡，调度中心才能触发失败重试。</p>
     */
    @XxlJob("xxlBasicJobHandler")
    public void xxlBasicJobHandler() throws Exception {
        handlerSupport.execute("xxlBasicJobHandler", context -> {
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

    /**
     * 展示 {@link XxlJob} 的 JobThread 生命周期钩子。
     *
     * <p>{@code init}/{@code destroy} 不是每次触发前后调用，也不是 Spring Bean 的初始化/销毁：
     * 它们分别在该 Handler 对应的 JobThread 创建和回收时执行。业务代码不能依赖它们为每次调度清理状态。</p>
     */
    @XxlJob(value = "xxlLifecycleJobHandler", init = "init", destroy = "destroy")
    public void xxlLifecycleJobHandler() throws Exception {
        handlerSupport.execute(
                "xxlLifecycleJobHandler",
                context -> handlerSupport.handleSuccess(
                        "生命周期任务执行完成，jobId=" + context.getJobId() + "，logId=" + context.getLogId()
                )
        );
    }

    /** JobThread 创建时调用；此时还没有某一次调度的 XxlJobContext，所以使用应用日志。 */
    public void init() {
        log.info("XXL-JOB lifecycle handler JobThread 初始化；该钩子不会在每次触发前重复执行");
    }

    /** JobThread 回收时调用；它不能替代每次业务执行所需的事务和资源清理。 */
    public void destroy() {
        log.info("XXL-JOB lifecycle handler JobThread 销毁");
    }

    /**
     * 每秒休眠一次，便于观察任务超时、终止和三种阻塞策略。
     *
     * <p>XXL-JOB 的超时/终止依赖线程中断。捕获 {@link InterruptedException} 后必须恢复中断标记并继续抛出，
     * 不能吞掉异常后继续产生数据库或外部接口副作用。</p>
     */
    @XxlJob("xxlSlowJobHandler")
    public void xxlSlowJobHandler() throws Exception {
        handlerSupport.execute("xxlSlowJobHandler", context -> {
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
