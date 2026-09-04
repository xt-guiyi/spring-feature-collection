package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.math.BigDecimal;

/** 适配器：统一遗留物流接口，并集中处理公斤、克和分、元的单位转换。 */
public final class AdapterPatternDemo {

    private AdapterPatternDemo() {
    }

    public static void main(String[] args) {
        Shipment shipment = new Shipment("ORDER-1001", new BigDecimal("2.50"));
        LegacyLogisticsApi legacyApi = new LegacyLogisticsApi();

        int weightInGrams = shipment.weightInKilograms().multiply(BigDecimal.valueOf(1000)).intValueExact();
        int feeInCents = legacyApi.freightInCents(shipment.orderId(), weightInGrams);
        ShippingQuote directQuote = new ShippingQuote(
                BigDecimal.valueOf(feeInCents, 2),
                (legacyApi.estimatedHours(shipment.orderId()) + 23) / 24,
                "Y".equals(legacyApi.availabilityCode(shipment.orderId()))
                        ? DeliveryStatus.AVAILABLE
                        : DeliveryStatus.UNAVAILABLE
        );

        ShippingService shippingService = new LegacyLogisticsAdapter(legacyApi);
        ShippingQuote patternQuote = shippingService.quote(shipment);

        System.out.println("直接写法：" + directQuote.describe(shipment.orderId()));
        System.out.println("适配器写法：" + patternQuote.describe(shipment.orderId()));

        int wrongFee = legacyApi.freightInCents(shipment.orderId(), shipment.weightInKilograms().intValue());
        System.out.println("单位转换散落的风险：把公斤误传成克时，运费变为 "
                + BigDecimal.valueOf(wrongFee, 2) + " 元");
    }

    /** 业务代码希望使用的目标接口。 */
    private interface ShippingService {

        ShippingQuote quote(Shipment shipment);
    }

    /** 适配器负责接口形状与计量单位的转换。 */
    private static final class LegacyLogisticsAdapter implements ShippingService {

        private final LegacyLogisticsApi legacyApi;

        private LegacyLogisticsAdapter(LegacyLogisticsApi legacyApi) {
            this.legacyApi = legacyApi;
        }

        @Override
        public ShippingQuote quote(Shipment shipment) {
            int grams = shipment.weightInKilograms().multiply(BigDecimal.valueOf(1000)).intValueExact();
            int cents = legacyApi.freightInCents(shipment.orderId(), grams);
            int days = (legacyApi.estimatedHours(shipment.orderId()) + 23) / 24;
            DeliveryStatus status = switch (legacyApi.availabilityCode(shipment.orderId())) {
                case "Y" -> DeliveryStatus.AVAILABLE;
                case "N" -> DeliveryStatus.UNAVAILABLE;
                default -> throw new IllegalArgumentException("未知物流状态码");
            };
            return new ShippingQuote(BigDecimal.valueOf(cents, 2), days, status);
        }
    }

    /** 无法修改的遗留接口：重量用克、金额用分、时效用小时、可用状态用 Y/N。 */
    private static final class LegacyLogisticsApi {

        private int freightInCents(String orderId, int weightInGrams) {
            return 500 + weightInGrams / 10;
        }

        private int estimatedHours(String orderId) {
            return 48;
        }

        private String availabilityCode(String orderId) {
            return "Y";
        }
    }

    private record Shipment(String orderId, BigDecimal weightInKilograms) {
    }

    private record ShippingQuote(
            BigDecimal feeInYuan,
            int estimatedDays,
            DeliveryStatus status
    ) {

        private String describe(String orderId) {
            return orderId + " 运费 " + feeInYuan + " 元，预计 " + estimatedDays
                    + " 天，状态 " + status;
        }
    }

    private enum DeliveryStatus {
        AVAILABLE, UNAVAILABLE
    }
}
