package com.xt.xiaoxingxing.playground.features.designpatterns.behavioral;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** 备忘录模式：保存订单草稿快照，并在编辑校验失败时恢复。 */
public final class MementoPatternDemo {

    private MementoPatternDemo() {
    }

    public static void main(String[] args) {
        DirectDraft directDraft = new DirectDraft(List.of("键盘"), "尽快发货");
        DirectSnapshot directSnapshot = new DirectSnapshot(
                List.copyOf(directDraft.items), directDraft.remark);
        directDraft.addItem("鼠标");
        directDraft.remark = "";
        try {
            requireValid(directDraft.remark);
        } catch (IllegalArgumentException exception) {
            directDraft.items = new ArrayList<>(directSnapshot.items());
            directDraft.remark = directSnapshot.remark();
        }

        OrderDraft patternDraft = new OrderDraft(List.of("键盘"), "尽快发货");
        DraftHistory history = new DraftHistory();
        history.save(patternDraft.createMemento());
        patternDraft.addItem("鼠标");
        patternDraft.changeRemark("");
        try {
            patternDraft.validate();
        } catch (IllegalArgumentException exception) {
            patternDraft.restore(history.undo());
        }

        DraftView directResult = directDraft.view();
        DraftView patternResult = patternDraft.view();
        System.out.println("直接写法：" + directResult);
        System.out.println("备忘录模式：" + patternResult);
        System.out.println("业务结果一致：" + directResult.equals(patternResult));

        try {
            patternResult.items().add("外部修改");
        } catch (UnsupportedOperationException exception) {
            System.out.println("快照和视图未暴露可变内部列表。");
        }
    }

    private static void requireValid(String remark) {
        if (remark == null || remark.isBlank()) {
            throw new IllegalArgumentException("备注不能为空");
        }
    }

    private static final class DirectDraft {

        private List<String> items;
        private String remark;

        private DirectDraft(List<String> items, String remark) {
            this.items = new ArrayList<>(items);
            this.remark = remark;
        }

        private void addItem(String item) {
            items.add(item);
        }

        private DraftView view() {
            return new DraftView(List.copyOf(items), remark);
        }
    }

    private record DirectSnapshot(List<String> items, String remark) {
    }

    private interface Memento {
    }

    private static final class OrderDraft {

        private final List<String> items = new ArrayList<>();
        private String remark;

        private OrderDraft(List<String> items, String remark) {
            this.items.addAll(items);
            this.remark = remark;
        }

        private void addItem(String item) {
            items.add(item);
        }

        private void changeRemark(String remark) {
            this.remark = remark;
        }

        private void validate() {
            requireValid(remark);
        }

        private Memento createMemento() {
            return new Snapshot(List.copyOf(items), remark);
        }

        private void restore(Memento memento) {
            if (!(memento instanceof Snapshot snapshot)) {
                throw new IllegalArgumentException("不是订单草稿快照");
            }
            items.clear();
            items.addAll(snapshot.items());
            remark = snapshot.remark();
        }

        private DraftView view() {
            return new DraftView(List.copyOf(items), remark);
        }

        private record Snapshot(List<String> items, String remark) implements Memento {
        }
    }

    private static final class DraftHistory {

        private final Deque<Memento> history = new ArrayDeque<>();

        private void save(Memento memento) {
            history.push(memento);
        }

        private Memento undo() {
            if (history.isEmpty()) {
                throw new IllegalStateException("没有可恢复的草稿快照");
            }
            return history.pop();
        }
    }

    private record DraftView(List<String> items, String remark) {

        private DraftView {
            items = List.copyOf(items);
        }
    }
}
