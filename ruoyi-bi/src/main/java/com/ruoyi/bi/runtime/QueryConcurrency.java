package com.ruoyi.bi.runtime;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.config.BiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Component
final class QueryConcurrency {
    private final Semaphore global;
    private final int userLimit;
    private final int datasourceLimit;
    private final ConcurrentHashMap<Long, Semaphore> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Semaphore> datasources = new ConcurrentHashMap<>();

    QueryConcurrency(BiProperties properties) {
        global = new Semaphore(properties.query().globalConcurrency());
        userLimit = properties.query().perUserConcurrency(); datasourceLimit = properties.query().perDatasourceConcurrency();
    }

    Permit acquire(long userId, long datasourceId) {
        Semaphore user = users.computeIfAbsent(userId, key -> new Semaphore(userLimit));
        Semaphore datasource = datasources.computeIfAbsent(datasourceId, key -> new Semaphore(datasourceLimit));
        boolean g = global.tryAcquire(); boolean u = g && user.tryAcquire(); boolean d = u && datasource.tryAcquire();
        if (!d) {
            if (u) user.release(); if (g) global.release();
            throw new BiException(HttpStatus.TOO_MANY_REQUESTS, "BI_QUERY_BUSY", "查询并发已达上限，请稍后重试");
        }
        return new Permit(global, user, datasource);
    }

    record Permit(Semaphore global, Semaphore user, Semaphore datasource) implements AutoCloseable {
        public void close() { datasource.release(); user.release(); global.release(); }
    }
}
