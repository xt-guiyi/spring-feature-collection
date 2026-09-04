package com.xt.xiaoxingxing.playground.features.designpatterns.creational;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 原型模式：从优惠活动模板深复制出可独立修改的新活动。 */
public final class PrototypePatternDemo {

    private PrototypePatternDemo() {
    }

    public static void main(String[] args) {
        CouponCampaign directTemplate = sampleCampaign("T-DIRECT");
        CouponCampaign shallowCopy = new CouponCampaign(
                "C-DIRECT",
                directTemplate.name,
                directTemplate.channels,
                directTemplate.rules);
        shallowCopy.changeFirstRuleRate(new BigDecimal("0.80"));
        System.out.println("浅复制后原模板折扣也变为：" + directTemplate.firstRuleRate());

        CouponCampaign prototype = sampleCampaign("T-PROTOTYPE");
        CouponCampaign copied = prototype.copyAs("C-2026-AUTUMN");
        copied.addChannel("MINI_PROGRAM");
        copied.changeFirstRuleRate(new BigDecimal("0.80"));

        System.out.println("原型：" + prototype.summary());
        System.out.println("深复制副本：" + copied.summary());
        System.out.println("规则集合是否共享：" + (prototype.rules == copied.rules));
        System.out.println("规则对象是否共享：" + (prototype.rules.get(0) == copied.rules.get(0)));
    }

    private static CouponCampaign sampleCampaign(String campaignId) {
        return new CouponCampaign(
                campaignId,
                "满额折扣",
                List.of("APP"),
                List.of(new DiscountRule(new BigDecimal("100.00"), new BigDecimal("0.90"))));
    }

    /** Prototype。 */
    private interface Prototype<T> {

        T copyAs(String newId);
    }

    /** ConcretePrototype。 */
    private static final class CouponCampaign implements Prototype<CouponCampaign> {

        private final String campaignId;
        private final String name;
        private final List<String> channels;
        private final List<DiscountRule> rules;

        private CouponCampaign(
                String campaignId,
                String name,
                List<String> channels,
                List<DiscountRule> rules) {
            this.campaignId = campaignId;
            this.name = name;
            this.channels = new ArrayList<>(channels);
            // 这里只复制了集合容器，元素是否复制由原型方法决定。
            this.rules = new ArrayList<>(rules);
        }

        @Override
        public CouponCampaign copyAs(String newId) {
            List<DiscountRule> copiedRules = rules.stream()
                    .map(DiscountRule::copy)
                    .toList();
            return new CouponCampaign(newId, name, channels, copiedRules);
        }

        private void addChannel(String channel) {
            channels.add(channel);
        }

        private void changeFirstRuleRate(BigDecimal rate) {
            rules.get(0).changeRate(rate);
        }

        private BigDecimal firstRuleRate() {
            return rules.get(0).rate;
        }

        private String summary() {
            DiscountRule firstRule = rules.get(0);
            return campaignId
                    + "，channels=" + channels
                    + "，threshold=" + firstRule.threshold
                    + "，rate=" + firstRule.rate;
        }
    }

    private static final class DiscountRule {

        private final BigDecimal threshold;
        private BigDecimal rate;

        private DiscountRule(BigDecimal threshold, BigDecimal rate) {
            this.threshold = threshold;
            this.rate = rate;
        }

        private DiscountRule copy() {
            return new DiscountRule(threshold, rate);
        }

        private void changeRate(BigDecimal rate) {
            this.rate = rate;
        }
    }
}
