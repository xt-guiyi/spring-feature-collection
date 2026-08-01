package com.xt.xiaoxingxing.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Metrics;
import org.springframework.data.redis.connection.stream.MapRecord;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisUtil 集成测试。
 * <p>
 * 运行前请确保本地 Redis 服务已启动（默认 localhost:6379）。
 */
@SpringBootTest
class RedisUtilTest {

    private static final String PREFIX = "test:";

    @Autowired
    private RedisUtil redisUtil;

    @AfterEach
    void clean() {
        // 清理当前测试产生的 key
        redisUtil.delete(PREFIX + "string");
        redisUtil.delete(PREFIX + "hash");
        redisUtil.delete(PREFIX + "list");
        redisUtil.delete(PREFIX + "set");
        redisUtil.delete(PREFIX + "zset");
        redisUtil.delete(PREFIX + "bitmap");
        redisUtil.delete(PREFIX + "hyperloglog");
        redisUtil.delete(PREFIX + "geo");
        redisUtil.delete(PREFIX + "stream");
    }

    @Test
    void testString() {
        String key = PREFIX + "string";

        redisUtil.set(key, "hello");
        assertEquals("hello", redisUtil.get(key));

        redisUtil.set(key, "world", 10, TimeUnit.SECONDS);
        assertEquals("world", redisUtil.get(key));
        assertTrue(redisUtil.hasKey(key));

        assertTrue(redisUtil.delete(key));
        assertFalse(redisUtil.hasKey(key));
    }

    @Test
    void testHash() {
        String hashKey = PREFIX + "hash";

        redisUtil.setHash(hashKey, "name", "zhangsan");
        redisUtil.setHash(hashKey, "age", "25");

        assertEquals("zhangsan", redisUtil.getHash(hashKey, "name"));
        assertEquals("25", redisUtil.getHash(hashKey, "age"));
    }

    @Test
    void testList() {
        String key = PREFIX + "list";

        redisUtil.rightPush(key, "a");
        redisUtil.rightPush(key, "b");
        redisUtil.leftPush(key, "c");

        List<String> range = redisUtil.listRange(key, 0, -1);
        assertEquals(3, range.size());
        assertEquals("c", range.get(0));

        assertEquals("c", redisUtil.leftPop(key));
        assertEquals(2, redisUtil.listRange(key, 0, -1).size());
    }

    @Test
    void testSet() {
        String key = PREFIX + "set";

        redisUtil.addSet(key, "java");
        redisUtil.addSet(key, "python");
        redisUtil.addSet(key, "java"); // 重复不加入

        Set<String> members = redisUtil.members(key);
        assertEquals(2, members.size());
        assertTrue(redisUtil.isMember(key, "java"));
    }

    @Test
    void testZSet() {
        String key = PREFIX + "zset";

        redisUtil.addZSet(key, "zhangsan", 80);
        redisUtil.addZSet(key, "lisi", 90);
        redisUtil.addZSet(key, "wangwu", 70);

        Set<String> range = redisUtil.rangeZSet(key, 0, -1);
        assertEquals(3, range.size());
        assertArrayEquals(new String[]{"wangwu", "zhangsan", "lisi"}, range.toArray(new String[0]));
    }

    @Test
    void testBitmap() {
        String key = PREFIX + "bitmap";

        redisUtil.setBit(key, 0, true);
        redisUtil.setBit(key, 1, false);
        redisUtil.setBit(key, 2, true);

        assertTrue(redisUtil.getBit(key, 0));
        assertFalse(redisUtil.getBit(key, 1));
        assertTrue(redisUtil.getBit(key, 2));
    }

    @Test
    void testHyperLogLog() {
        String key = PREFIX + "hyperloglog";

        redisUtil.addHyperLogLog(key, "user1");
        redisUtil.addHyperLogLog(key, "user2");
        redisUtil.addHyperLogLog(key, "user1"); // 重复不计入

        Long count = redisUtil.countHyperLogLog(key);
        assertTrue(count >= 2);
    }

    @Test
    void testGeo() {
        String key = PREFIX + "geo";

        redisUtil.addGeo(key, 116.40, 39.90, "beijing");
        redisUtil.addGeo(key, 121.47, 31.23, "shanghai");

        var distance = redisUtil.distanceGeo(key, "beijing", "shanghai", Metrics.KILOMETERS);
        assertNotNull(distance);
        assertTrue(distance.getValue() > 1000);
    }

    @Test
    void testStream() {
        String key = PREFIX + "stream";

        Map<String, String> fields = new HashMap<>();
        fields.put("name", "zhangsan");
        fields.put("action", "login");

        var recordId = redisUtil.addStream(key, fields);
        assertNotNull(recordId);

        List<MapRecord<String, Object, Object>> records = redisUtil.readStream(key);
        assertFalse(records.isEmpty());
    }
}
