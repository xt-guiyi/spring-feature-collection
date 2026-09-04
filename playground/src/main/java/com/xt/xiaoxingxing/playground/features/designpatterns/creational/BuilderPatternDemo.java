package com.xt.xiaoxingxing.playground.features.designpatterns.creational;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builder 模式：逐步创建并校验不可变订单查询。 */
public final class BuilderPatternDemo {

    private BuilderPatternDemo() {
    }

    public static void main(String[] args) {
        DirectOrderSearchQuery direct = new DirectOrderSearchQuery(
                List.of(OrderStatus.PAID),
                new BigDecimal("100.00"),
                new BigDecimal("500.00"),
                "C-1001",
                20,
                1,
                SortDirection.CREATED_AT_DESC);
        System.out.println("直接写法（页码和大小传反）：page="
                + direct.pageNumber() + "，size=" + direct.pageSize());

        OrderSearchQuery query = OrderSearchQuery.builder()
                .status(OrderStatus.PAID)
                .amountBetween(new BigDecimal("100.00"), new BigDecimal("500.00"))
                .customer("C-1001")
                .page(1, 20)
                .sortBy(SortDirection.CREATED_AT_DESC)
                .build();
        System.out.println("Builder 写法：" + query.summary());

        try {
            OrderSearchQuery.builder()
                    .amountBetween(new BigDecimal("500.00"), new BigDecimal("100.00"))
                    .build();
        } catch (IllegalArgumentException exception) {
            System.out.println("构建时拒绝非法条件：" + exception.getMessage());
        }
    }

    /** 直接写法用同类型长参数，编译器无法发现位置传反。 */
    private record DirectOrderSearchQuery(
            List<OrderStatus> statuses,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            String customerId,
            int pageNumber,
            int pageSize,
            SortDirection sortDirection) {
    }

    /** Product：字段均为 final，集合在构建时复制。 */
    private static final class OrderSearchQuery {

        private final List<OrderStatus> statuses;
        private final BigDecimal minAmount;
        private final BigDecimal maxAmount;
        private final String customerId;
        private final int pageNumber;
        private final int pageSize;
        private final SortDirection sortDirection;

        private OrderSearchQuery(Builder builder) {
            this.statuses = List.copyOf(builder.statuses);
            this.minAmount = builder.minAmount;
            this.maxAmount = builder.maxAmount;
            this.customerId = builder.customerId;
            this.pageNumber = builder.pageNumber;
            this.pageSize = builder.pageSize;
            this.sortDirection = builder.sortDirection;
        }

        private static Builder builder() {
            return new Builder();
        }

        private String summary() {
            return "statuses=" + statuses
                    + "，amount=" + minAmount + "~" + maxAmount
                    + "，customer=" + customerId
                    + "，page=" + pageNumber
                    + "，size=" + pageSize
                    + "，sort=" + sortDirection;
        }

        /** Builder。 */
        private static final class Builder {

            private final List<OrderStatus> statuses = new ArrayList<>();
            private BigDecimal minAmount;
            private BigDecimal maxAmount;
            private String customerId;
            private int pageNumber = 1;
            private int pageSize = 20;
            private SortDirection sortDirection = SortDirection.CREATED_AT_DESC;

            private Builder status(OrderStatus status) {
                statuses.add(Objects.requireNonNull(status));
                return this;
            }

            private Builder amountBetween(BigDecimal minAmount, BigDecimal maxAmount) {
                this.minAmount = minAmount;
                this.maxAmount = maxAmount;
                return this;
            }

            private Builder customer(String customerId) {
                this.customerId = customerId;
                return this;
            }

            private Builder page(int pageNumber, int pageSize) {
                this.pageNumber = pageNumber;
                this.pageSize = pageSize;
                return this;
            }

            private Builder sortBy(SortDirection sortDirection) {
                this.sortDirection = Objects.requireNonNull(sortDirection);
                return this;
            }

            private OrderSearchQuery build() {
                if (minAmount != null && minAmount.signum() < 0) {
                    throw new IllegalArgumentException("最小金额不能小于 0");
                }
                if (maxAmount != null && maxAmount.signum() < 0) {
                    throw new IllegalArgumentException("最大金额不能小于 0");
                }
                if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
                    throw new IllegalArgumentException("最小金额不能大于最大金额");
                }
                if (customerId != null && customerId.isBlank()) {
                    throw new IllegalArgumentException("客户编号不能为空白");
                }
                if (pageNumber < 1 || pageSize < 1 || pageSize > 100) {
                    throw new IllegalArgumentException("页码从 1 开始，每页数量必须为 1~100");
                }
                return new OrderSearchQuery(this);
            }
        }
    }

    private enum OrderStatus {
        CREATED, PAID, SHIPPED, COMPLETED
    }

    private enum SortDirection {
        CREATED_AT_ASC, CREATED_AT_DESC
    }
}
