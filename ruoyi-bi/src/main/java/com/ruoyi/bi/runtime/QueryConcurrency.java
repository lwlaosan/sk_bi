package com.ruoyi.bi.runtime;

import com.ruoyi.bi.api.BiException;
import com.ruoyi.bi.config.BiProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

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
        boolean g = false, u = false, d = false;
        try {
            g = global.tryAcquire(3, TimeUnit.SECONDS);
            u = g && user.tryAcquire(3, TimeUnit.SECONDS);
            d = u && datasource.tryAcquire(3, TimeUnit.SECONDS);
            if (d) return new Permit(global, user, datasource);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (u) user.release();
        if (g) global.release();
        throw new BiException(HttpStatus.TOO_MANY_REQUESTS, "BI_QUERY_BUSY", "当前查询较多，等待后仍未获得执行名额，请稍后重试");
    }

    record Permit(Semaphore global, Semaphore user, Semaphore datasource) implements AutoCloseable {
        public void close() { datasource.release(); user.release(); global.release(); }
    }
}
