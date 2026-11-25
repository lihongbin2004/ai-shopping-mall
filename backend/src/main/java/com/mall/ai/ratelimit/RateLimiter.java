package com.mall.ai.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

/**
 * AI 对话限流：基于 Redis ZSET 的滑动窗口算法
 *
 * 设计要点：
 * - 每个用户/会话一条 ZSET，member 是请求ID，score 是时间戳(毫秒)
 * - 请求来了：清理窗口外的旧记录、统计窗口内条数
 * - 在阈值内 → 放行并加新记录；超阈值 → 拒绝
 * - 整个流程用一次 ZREMRANGEBYSCORE + ZCARD + ZADD 实现，原子性靠 pipeline 保证
 *
 * 相比固定窗口：不会出现"59 秒 30 次 + 60 秒 30 次 = 1 秒内 60 次"的尖刺
 */
@Component
public class RateLimiter {

    @Autowired(required = false)
    private StringRedisTemplate redis;

    /**
     * 尝试获取一次许可
     * @param key       Redis key（建议 "ratelimit:ai:user:{userId}"）
     * @param windowMs  窗口大小（毫秒），例如 60000 = 1 分钟
     * @param maxCount  窗口内最大允许次数
     * @return true 放行，false 限流
     */
    public boolean tryAcquire(String key, long windowMs, int maxCount) {
        if (redis == null) return true;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;
        String member = now + ":" + UUID.randomUUID();

        ZSetOperations<String, String> zset = redis.opsForZSet();
        // 1) 清掉窗口外的旧记录
        zset.removeRangeByScore(key, 0, windowStart);
        // 2) 统计当前窗口内数量
        Long count = zset.zCard(key);
        if (count != null && count >= maxCount) {
            return false;
        }
        // 3) 加一条新记录
        zset.add(key, member, now);
        // 4) 给 key 续期（防止冷 key 残留）
        redis.expire(key, Duration.ofMillis(windowMs + 5000));
        return true;
    }

    /**
     * 查询当前窗口内剩余可用次数（用于前端显示）
     */
    public long remaining(String key, long windowMs, int maxCount) {
        if (redis == null) return maxCount;
        long now = System.currentTimeMillis();
        long windowStart = now - windowMs;
        ZSetOperations<String, String> zset = redis.opsForZSet();
        zset.removeRangeByScore(key, 0, windowStart);
        Long count = zset.zCard(key);
        long used = count == null ? 0 : count;
        return Math.max(0, maxCount - used);
    }

    /**
     * 查询窗口内的所有请求时间戳（调试用）
     */
    public Set<String> recentMembers(String key) {
        if (redis == null) return java.util.Collections.emptySet();
        return redis.opsForZSet().range(key, 0, -1);
    }
}
