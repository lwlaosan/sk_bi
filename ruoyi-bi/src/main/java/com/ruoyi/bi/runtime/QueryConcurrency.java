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
    private final ConcurrentHashMap<Long, ScopedSemaphore> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScopedSemaphore> datasources = new ConcurrentHashMap<>();

    QueryConcurrency(BiProperties properties) {
        global = new Semaphore(properties.query().globalConcurrency());
        userLimit = properties.query().perUserConcurrency(); datasourceLimit = properties.query().perDatasourceConcurrency();
    }

    Permit acquire(long userId, long datasourceId) {
        ScopedSemaphore user = retain(users,userId,userLimit);
        ScopedSemaphore datasource = retain(datasources,datasourceId,datasourceLimit);
        boolean g = false, u = false, d = false;
        try {
            g = global.tryAcquire(3, TimeUnit.SECONDS);
            u = g && user.semaphore.tryAcquire(3, TimeUnit.SECONDS);
            d = u && datasource.semaphore.tryAcquire(3, TimeUnit.SECONDS);
            if (d) return new Permit(this,userId,datasourceId,user,datasource);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
        if (u) user.semaphore.release();
        if (g) global.release();
        release(users,userId,user);
        release(datasources,datasourceId,datasource);
        throw new BiException(HttpStatus.TOO_MANY_REQUESTS, "BI_QUERY_BUSY", "当前查询较多，等待后仍未获得执行名额，请稍后重试");
    }

    private static ScopedSemaphore retain(ConcurrentHashMap<Long,ScopedSemaphore> scopes,long id,int limit){
        return scopes.compute(id,(key,current)->{ScopedSemaphore value=current==null?new ScopedSemaphore(limit):current;value.references++;return value;});
    }

    private static void release(ConcurrentHashMap<Long,ScopedSemaphore> scopes,long id,ScopedSemaphore expected){
        scopes.compute(id,(key,current)->{
            if(current!=expected)return current;
            current.references--;
            return current.references==0?null:current;
        });
    }

    private static final class ScopedSemaphore {
        private final Semaphore semaphore;
        private int references;
        private ScopedSemaphore(int limit){this.semaphore=new Semaphore(limit);}
    }

    int activeUserScopes(){return users.size();}
    int activeDatasourceScopes(){return datasources.size();}

    record Permit(QueryConcurrency owner,long userId,long datasourceId,ScopedSemaphore user,ScopedSemaphore datasource) implements AutoCloseable {
        public void close() {
            datasource.semaphore.release();user.semaphore.release();owner.global.release();
            release(owner.users,userId,user);release(owner.datasources,datasourceId,datasource);
        }
    }
}
