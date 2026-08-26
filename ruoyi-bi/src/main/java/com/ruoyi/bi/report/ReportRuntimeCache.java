package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ruoyi.bi.config.BiProperties;
import com.ruoyi.common.core.redis.RedisCache;
import org.springframework.stereotype.Component;
import java.util.concurrent.TimeUnit;

@Component
public class ReportRuntimeCache {
    private final RedisCache redis;
    private final ObjectMapper mapper;
    private final BiProperties properties;

    public ReportRuntimeCache(RedisCache redis, ObjectMapper mapper, BiProperties properties) {
        this.redis = redis; this.mapper = mapper; this.properties = properties;
    }

    public ObjectNode get(ReportRepository repository, ReportRepository.RuntimeHead head) {
        String key = key(head.uuid());
        try {
            String cached = redis.getCacheObject(key);
            if (cached != null) {
                ObjectNode node = (ObjectNode) mapper.readTree(cached);
                if (node.path("expectedVersion").asLong() == head.version()) return node;
            }
        } catch (Exception ignored) { }
        ObjectNode loaded = repository.snapshot(head.id(), head.version());
        try {
            redis.setCacheObject(key, mapper.writeValueAsString(loaded), properties.cache().reportTtlMinutes(), TimeUnit.MINUTES);
        } catch (Exception ignored) { }
        return loaded;
    }

    public void invalidate(String uuid) {
        try {
            redis.deleteObject(key(uuid));
            redis.redisTemplate.convertAndSend(properties.cache().invalidationChannel(), uuid);
        } catch (Exception ignored) { }
    }

    public <T> T getOption(String uuid,String controlKey,long userId,Class<T> type){
        try{String value=redis.getCacheObject(optionKey(uuid,controlKey,userId));return value==null?null:mapper.readValue(value,type);}catch(Exception ignored){return null;}
    }
    public void putOption(String uuid,String controlKey,long userId,Object value){
        try{redis.setCacheObject(optionKey(uuid,controlKey,userId),mapper.writeValueAsString(value),properties.option().cacheTtlSeconds(),TimeUnit.SECONDS);}catch(Exception ignored){}
    }

    private static String key(String uuid) { return "bi:config:" + uuid; }
    private static String optionKey(String uuid,String controlKey,long userId){String scope=com.ruoyi.bi.datasource.ProcedureSignature.sha256Text(String.valueOf(userId)).substring(0,16);return "bi:option:"+uuid+":"+controlKey+":"+scope;}
}
