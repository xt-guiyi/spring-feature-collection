package com.xt.xiaoxingxing.playground.features.basics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class DateTimeDemo {

    private DateTimeDemo() {
    }

    public static void main(String[] args) {
        LocalDateTime localStart = LocalDateTime.of(2026, 9, 4, 10, 30);
        ZonedDateTime shanghaiStart = localStart.atZone(ZoneId.of("Asia/Shanghai"));
        Instant startInstant = shanghaiStart.toInstant();
        ZonedDateTime londonStart = startInstant.atZone(ZoneId.of("Europe/London"));

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
        System.out.println("上海时间：" + shanghaiStart.format(formatter));
        System.out.println("同一时刻的伦敦时间：" + londonStart.format(formatter));
        System.out.println("UTC 时间点：" + startInstant);

        Instant endInstant = startInstant.plus(Duration.ofMinutes(90));
        Duration duration = Duration.between(startInstant, endInstant);
        System.out.println("活动时长：" + duration.toMinutes() + " 分钟");
    }
}
