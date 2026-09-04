package com.xt.xiaoxingxing.playground.features.basics;

/** 面向对象基础：封装、继承、多态、接口和 record。 */
public final class ObjectOrientedDemo {

    private ObjectOrientedDemo() {
    }

    public static void main(String[] args) {
        JavaStudent student = new JavaStudent(new UserProfile("小星星", 3));

        // 父类引用实际调用子类重写的方法，这就是多态。
        Member member = student;
        System.out.println(member.introduce());

        Learner learner = student;
        System.out.println(learner.learn("Java 集合"));
        System.out.println("record 数据：" + student.profile());
    }

    /** record 适合承载一组不可变数据，并自动生成访问器、equals 和 toString。 */
    private record UserProfile(String name, int level) {
    }

    private interface Learner {

        String learn(String topic);
    }

    private static class Member {

        private final UserProfile profile;

        private Member(UserProfile profile) {
            this.profile = profile;
        }

        protected UserProfile profile() {
            return profile;
        }

        public String introduce() {
            return "普通成员：" + profile.name();
        }
    }

    private static final class JavaStudent extends Member implements Learner {

        private JavaStudent(UserProfile profile) {
            super(profile);
        }

        @Override
        public String introduce() {
            return "Java 学员：%s，等级：%d".formatted(profile().name(), profile().level());
        }

        @Override
        public String learn(String topic) {
            return profile().name() + " 正在学习 " + topic;
        }
    }
}
