package com.xt.xiaoxingxing.playground.features.basics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;

/** 集合基础：使用不同集合安排一次学习任务。 */
public final class CollectionsDemo {

    private CollectionsDemo() {
    }

    public static void main(String[] args) {
        List<String> studyOrder = new ArrayList<>();
        studyOrder.add("List");
        studyOrder.add("Map");
        studyOrder.add("并发");

        Set<String> completedTopics = new LinkedHashSet<>();
        completedTopics.add("List");
        completedTopics.add("List");
        completedTopics.add("Set");

        Map<String, Integer> practiceCounts = new LinkedHashMap<>();
        practiceCounts.put("List", 1);
        practiceCounts.merge("List", 1, Integer::sum);
        practiceCounts.put("Map", 1);

        Deque<String> studyHistory = new ArrayDeque<>();
        studyHistory.push("List");
        studyHistory.push("Map");

        // PriorityQueue 默认先取最小元素，所以数字越小表示优先级越高。
        Queue<StudyTask> tasks = new PriorityQueue<>(Comparator.comparingInt(StudyTask::priority));
        tasks.offer(new StudyTask("复习 List", 2));
        tasks.offer(new StudyTask("完成并发练习", 1));
        tasks.offer(new StudyTask("整理笔记", 3));

        System.out.println("List 保持学习顺序：" + studyOrder);
        System.out.println("Set 自动去重：" + completedTopics);
        System.out.println("Map 按主题记录次数：" + practiceCounts);
        System.out.println("Deque 最近学习的主题：" + studyHistory.peek());
        System.out.println("PriorityQueue 下一项任务：" + tasks.poll());
    }

    private record StudyTask(String name, int priority) {
    }
}
