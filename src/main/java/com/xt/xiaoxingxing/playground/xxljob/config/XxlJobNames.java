package com.xt.xiaoxingxing.playground.xxljob.config;

/** XXL-JOB Handler 名称。 */
public final class XxlJobNames {

    public static final String BASIC = "xxlBasicJobHandler";
    public static final String LIFECYCLE = "xxlLifecycleJobHandler";
    public static final String SLOW = "xxlSlowJobHandler";
    public static final String RETRY = "xxlRetryJobHandler";
    public static final String DAILY_ORDER_SUMMARY = "xxlDailyOrderSummaryJobHandler";
    public static final String GENERATE_WORK_BATCH = "xxlGenerateWorkBatchJobHandler";
    public static final String PROCESS_WORK_ITEMS = "xxlProcessWorkItemsJobHandler";

    private XxlJobNames() {
    }
}
