package com.example.gateway.service;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;


@Service
public class ItemCacheService {

    private static final String REDIS_PREFIX = "ITEM_CACHE";

    private final RedisTemplate<String, Object> redisTemplate;
    private final HashOperations<String, String, Map<String, Object>> hashOperations;

    public ItemCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = this.redisTemplate.opsForHash();
    }

    public void cacheItem(Long id, Map<String, Object> item) {
        hashOperations.put(REDIS_PREFIX, String.valueOf(id), item);
    }

    public Map<String, Object> getCachedItem(Long id) {
        return hashOperations.get(REDIS_PREFIX, String.valueOf(id));
    }

}