package com.xt.xiaoxingxing.playground.features.basics;

public final class ExceptionDemo {

    private ExceptionDemo() {
    }

    public static void main(String[] args) {
        try {
            withdraw(100, 120);
        } catch (InsufficientBalanceException exception) {
            System.out.println("捕获异常：" + exception.getMessage());
        } finally {
            // finally 无论是否发生异常都会执行，适合释放资源。
            System.out.println("finally：交易处理结束");
        }
    }

    private static void withdraw(int balance, int amount) {
        if (amount > balance) {
            throw new InsufficientBalanceException("余额不足，当前余额：" + balance);
        }
        System.out.println("取款成功，剩余余额：" + (balance - amount));
    }

    private static final class InsufficientBalanceException extends RuntimeException {

        private InsufficientBalanceException(String message) {
            super(message);
        }
    }
}
