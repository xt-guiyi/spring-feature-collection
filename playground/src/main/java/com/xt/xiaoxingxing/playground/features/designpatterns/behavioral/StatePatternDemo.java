package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

/** 状态模式：把售后申请在不同状态下的行为交给状态对象。 */
public final class StatePatternDemo {

    private StatePatternDemo() {
    }

    public static void main(String[] args) {
        RefundStatus directState = approveDirectly(RefundStatus.PENDING);

        RefundRequest request = new RefundRequest();
        request.approve();

        System.out.println("直接写法：" + directState);
        System.out.println("状态模式：" + request.status());

        try {
            request.reject();
        } catch (IllegalStateException exception) {
            System.out.println("当前状态阻止非法操作：" + exception.getMessage());
        }
    }

    private static RefundStatus approveDirectly(RefundStatus state) {
        return switch (state) {
            case PENDING -> RefundStatus.APPROVED;
            case APPROVED -> throw new IllegalStateException("售后申请已经通过");
            case REJECTED -> throw new IllegalStateException("已拒绝的申请不能通过");
        };
    }

    private enum RefundStatus {
        PENDING, APPROVED, REJECTED
    }

    private interface RefundState {

        void approve(RefundRequest context);

        void reject(RefundRequest context);

        RefundStatus status();
    }

    private static final class RefundRequest {

        private RefundState state = new PendingState();

        private void approve() {
            state.approve(this);
        }

        private void reject() {
            state.reject(this);
        }

        private void changeTo(RefundState nextState) {
            state = nextState;
        }

        private RefundStatus status() {
            return state.status();
        }
    }

    private static final class PendingState implements RefundState {

        @Override
        public void approve(RefundRequest context) {
            context.changeTo(new ApprovedState());
        }

        @Override
        public void reject(RefundRequest context) {
            context.changeTo(new RejectedState());
        }

        @Override
        public RefundStatus status() {
            return RefundStatus.PENDING;
        }
    }

    private static final class ApprovedState implements RefundState {

        @Override
        public void approve(RefundRequest context) {
            throw new IllegalStateException("售后申请已经通过");
        }

        @Override
        public void reject(RefundRequest context) {
            throw new IllegalStateException("已通过的申请不能拒绝");
        }

        @Override
        public RefundStatus status() {
            return RefundStatus.APPROVED;
        }
    }

    private static final class RejectedState implements RefundState {

        @Override
        public void approve(RefundRequest context) {
            throw new IllegalStateException("已拒绝的申请不能通过");
        }

        @Override
        public void reject(RefundRequest context) {
            throw new IllegalStateException("售后申请已经拒绝");
        }

        @Override
        public RefundStatus status() {
            return RefundStatus.REJECTED;
        }
    }
}
