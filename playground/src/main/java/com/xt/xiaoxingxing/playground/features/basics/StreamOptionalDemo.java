package com.xt.xiaoxingxing.playground.features.basics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Stream 与 Optional 基础：从课程列表生成学习摘要。 */
public final class StreamOptionalDemo {

    private StreamOptionalDemo() {
    }

    public static void main(String[] args) {
        List<Course> courses = List.of(
                new Course("集合", "List 入门", 30),
                new Course("集合", "Map 入门", 40),
                new Course("并发", "线程安全", 60)
        );

        Predicate<Course> isLongCourse = course -> course.minutes() >= 40;
        List<String> longCourseNames = courses.stream()
                .filter(isLongCourse)
                .map(Course::name)
                .toList();

        Map<String, List<String>> namesByCategory = courses.stream()
                .collect(Collectors.groupingBy(
                        Course::category,
                        LinkedHashMap::new,
                        Collectors.mapping(Course::name, Collectors.toList())
                ));

        int totalMinutes = courses.stream()
                .map(Course::minutes)
                .reduce(0, Integer::sum);

        Optional<Course> concurrencyCourse = courses.stream()
                .filter(course -> "并发".equals(course.category()))
                .findFirst();

        System.out.println("filter + map：" + longCourseNames);
        System.out.println("groupingBy 分组：" + namesByCategory);
        System.out.println("reduce 汇总分钟数：" + totalMinutes);
        System.out.println("Optional 推荐课程："
                + concurrencyCourse.map(Course::name).orElse("暂无推荐"));
    }

    private record Course(String category, String name, int minutes) {
    }
}
