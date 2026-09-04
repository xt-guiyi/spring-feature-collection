package com.xt.xiaoxingxing.playground.features.designpatterns.structural;

import java.util.List;
import java.util.stream.Collectors;

/** 桥接：让业务报表与渲染格式两个维度可以独立扩展。 */
public final class BridgePatternDemo {

    private BridgePatternDemo() {
    }

    public static void main(String[] args) {
        String directResult = directRender(ReportType.ORDER, RenderFormat.JSON);

        Report orderReport = new OrderReport(new JsonRenderer());
        String patternResult = orderReport.render();

        System.out.println("直接写法：" + directResult);
        System.out.println("桥接写法：" + patternResult);

        Report settlementReport = new SettlementReport(new JsonRenderer());
        System.out.println("新增结算报表复用 JSON：" + settlementReport.render());
        System.out.println("订单报表切换 CSV：" + new OrderReport(new CsvRenderer()).render());
    }

    private static String directRender(ReportType type, RenderFormat format) {
        if (type == ReportType.ORDER && format == RenderFormat.JSON) {
            return "{\"title\":\"订单日报\",\"paidOrders\":\"12\",\"revenue\":\"860.00\"}";
        }
        if (type == ReportType.ORDER && format == RenderFormat.CSV) {
            return "订单日报,paidOrders=12,revenue=860.00";
        }
        if (type == ReportType.SETTLEMENT && format == RenderFormat.JSON) {
            return "{\"title\":\"结算日报\",\"settledOrders\":\"10\",\"amount\":\"720.00\"}";
        }
        return "结算日报,settledOrders=10,amount=720.00";
    }

    /** 报表抽象持有渲染实现，而不是为每一种组合创建子类。 */
    private abstract static class Report {

        private final ReportRenderer renderer;

        private Report(ReportRenderer renderer) {
            this.renderer = renderer;
        }

        protected abstract String title();

        protected abstract List<Metric> metrics();

        private String render() {
            return renderer.render(title(), metrics());
        }
    }

    private static final class OrderReport extends Report {

        private OrderReport(ReportRenderer renderer) {
            super(renderer);
        }

        @Override
        protected String title() {
            return "订单日报";
        }

        @Override
        protected List<Metric> metrics() {
            return List.of(new Metric("paidOrders", "12"), new Metric("revenue", "860.00"));
        }
    }

    private static final class SettlementReport extends Report {

        private SettlementReport(ReportRenderer renderer) {
            super(renderer);
        }

        @Override
        protected String title() {
            return "结算日报";
        }

        @Override
        protected List<Metric> metrics() {
            return List.of(new Metric("settledOrders", "10"), new Metric("amount", "720.00"));
        }
    }

    private interface ReportRenderer {

        String render(String title, List<Metric> metrics);
    }

    private static final class JsonRenderer implements ReportRenderer {

        @Override
        public String render(String title, List<Metric> metrics) {
            String fields = metrics.stream()
                    .map(metric -> "\"" + metric.name() + "\":\"" + metric.value() + "\"")
                    .collect(Collectors.joining(","));
            return "{\"title\":\"" + title + "\"," + fields + "}";
        }
    }

    private static final class CsvRenderer implements ReportRenderer {

        @Override
        public String render(String title, List<Metric> metrics) {
            String fields = metrics.stream()
                    .map(metric -> metric.name() + "=" + metric.value())
                    .collect(Collectors.joining(","));
            return title + "," + fields;
        }
    }

    private record Metric(String name, String value) {
    }

    private enum ReportType {
        ORDER, SETTLEMENT
    }

    private enum RenderFormat {
        JSON, CSV
    }
}
