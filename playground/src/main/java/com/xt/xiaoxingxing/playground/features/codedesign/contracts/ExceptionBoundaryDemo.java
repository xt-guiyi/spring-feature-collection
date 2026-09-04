package com.xt.xiaoxingxing.playground.features.codedesign.contracts;

/** 内部保留真实异常，在最外层统一转换为调用方需要的错误。 */
public final class ExceptionBoundaryDemo {

    private ExceptionBoundaryDemo() {
    }

    public static void main(String[] args) {
        System.out.println("直接写法：" + directEndpoint(5));
        System.out.println("改进写法：" + improvedEndpoint(5));
        System.out.println("直接失败：" + directEndpoint(0));
        System.out.println("改进失败：" + improvedEndpoint(0));
    }

    private static ApiResult directEndpoint(int stock) {
        try {
            return new ApiResult(true, "OK", createDirect(stock));
        } catch (RuntimeException exception) {
            return new ApiResult(false, "ORDER_FAILED", exception.getMessage());
        }
    }

    private static String createDirect(int stock) {
        try {
            return reserve(stock);
        } catch (OutOfStockException exception) {
            // 每层重新包装且不保留 cause，真正原因已经丢失。
            throw new IllegalStateException("下单失败");
        }
    }

    private static ApiResult improvedEndpoint(int stock) {
        try {
            return new ApiResult(true, "OK", createImproved(stock));
        } catch (OutOfStockException exception) {
            return new ApiResult(false, "OUT_OF_STOCK", exception.getMessage());
        }
    }

    private static String createImproved(int stock) {
        return reserve(stock);
    }

    private static String reserve(int stock) {
        if (stock <= 0) {
            throw new OutOfStockException("库存不足");
        }
        return "下单成功：O-1001";
    }

    private record ApiResult(boolean success, String code, String message) {
    }

    private static final class OutOfStockException extends RuntimeException {

        private OutOfStockException(String message) {
            super(message);
        }
    }
}
