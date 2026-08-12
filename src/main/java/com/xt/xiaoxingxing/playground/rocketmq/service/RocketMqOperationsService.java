package com.xt.xiaoxingxing.playground.rocketmq.service;

import com.xt.xiaoxingxing.playground.rocketmq.entity.MqOrderStatistics;
import com.xt.xiaoxingxing.playground.rocketmq.mapper.MqConsumerRecordMapper;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketConsumedMessageVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketNotificationLogVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOrderStatisticsVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketOutboxEventVO;
import com.xt.xiaoxingxing.playground.rocketmq.vo.RocketTransactionRecordVO;
import com.xt.xiaoxingxing.shared.common.PageResult;
import com.xt.xiaoxingxing.shared.validation.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/** 为学习者提供只读的消费幂等、统计和通知观察数据。 */
@Service
@RequiredArgsConstructor
public class RocketMqOperationsService {

    private final MqConsumerRecordMapper consumerRecordMapper;
    private final OutboxEventService outboxEventService;
    private final RocketTransactionRecordService transactionRecordService;

    /**
     * Outbox、事务记录、消费记录等内部查询仍返回持久化实体；本服务在 HTTP 边界统一转换为 VO。
     * 这样数据库新增锁字段或调整实体映射时，不会不经意改变前端契约。
     */
    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public PageResult<RocketOutboxEventVO> pageOutbox(String status, int pageNum, int pageSize) {
        return mapPage(outboxEventService.page(status, pageNum, pageSize), RocketOutboxEventVO::from);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public RocketOutboxEventVO getOutbox(String id) {
        return RocketOutboxEventVO.from(outboxEventService.getById(id));
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public PageResult<RocketTransactionRecordVO> pageTransactions(String status, int pageNum, int pageSize) {
        return mapPage(transactionRecordService.page(status, pageNum, pageSize), RocketTransactionRecordVO::from);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public RocketTransactionRecordVO getTransaction(String transactionId) {
        return RocketTransactionRecordVO.from(BusinessAssert.notNull(
                transactionRecordService.getById(transactionId), "事务消息记录不存在"));
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public PageResult<RocketConsumedMessageVO> pageConsumed(String consumerName, int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<RocketConsumedMessageVO> result = new PageResult<>();
        result.setList(consumerRecordMapper.selectConsumedPage(consumerName, offset, pageSize).stream()
                .map(RocketConsumedMessageVO::from).toList());
        result.setTotal(consumerRecordMapper.countConsumed(consumerName));
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public RocketOrderStatisticsVO getStatistics() {
        MqOrderStatistics statistics = consumerRecordMapper.selectStatistics();
        if (statistics == null) {
            // 尚无消费结果时先构造稳定零值实体，再统一映射为 VO；data=null 不会被误解为查询失败。
            statistics = new MqOrderStatistics();
            statistics.setId((short) 1);
            statistics.setCreatedCount(0L);
            statistics.setPaidCount(0L);
            statistics.setCancelledCount(0L);
            statistics.setCreatedAmount(BigDecimal.ZERO);
        }
        return RocketOrderStatisticsVO.from(statistics);
    }

    @Transactional(transactionManager = "playgroundTransactionManager", readOnly = true)
    public PageResult<RocketNotificationLogVO> pageNotifications(int pageNum, int pageSize) {
        validatePage(pageNum, pageSize);
        long offset = (long) (pageNum - 1) * pageSize;
        PageResult<RocketNotificationLogVO> result = new PageResult<>();
        result.setList(consumerRecordMapper.selectNotificationPage(offset, pageSize).stream()
                .map(RocketNotificationLogVO::from).toList());
        result.setTotal(consumerRecordMapper.countNotifications());
        result.setPageNum(pageNum);
        result.setPageSize(pageSize);
        return result;
    }

    /** 保留分页元数据，只转换列表元素；这是所有运维分页响应共用的边界组装逻辑。 */
    private <S, T> PageResult<T> mapPage(PageResult<S> source,
                                         java.util.function.Function<S, T> converter) {
        PageResult<T> target = new PageResult<>();
        target.setList(source.getList().stream().map(converter).toList());
        target.setTotal(source.getTotal());
        target.setPageNum(source.getPageNum());
        target.setPageSize(source.getPageSize());
        return target;
    }

    private void validatePage(int pageNum, int pageSize) {
        BusinessAssert.isTrue(pageNum > 0, "pageNum必须大于0");
        BusinessAssert.isTrue(pageSize > 0 && pageSize <= 100, "pageSize必须在1到100之间");
    }
}
