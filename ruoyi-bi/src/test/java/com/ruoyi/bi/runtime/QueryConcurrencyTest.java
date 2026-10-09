package com.ruoyi.bi.runtime;

import com.ruoyi.bi.config.BiProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryConcurrencyTest {
    @Test
    void removesUserAndDatasourceScopesAfterPermitCloses() {
        BiProperties properties = new BiProperties(
            new BiProperties.Query(100, 200, 10, 20, 2, 3, 4), null, null, null, null);
        QueryConcurrency concurrency = new QueryConcurrency(properties);

        try (QueryConcurrency.Permit ignored = concurrency.acquire(11, 22)) {
            assertEquals(1, concurrency.activeUserScopes());
            assertEquals(1, concurrency.activeDatasourceScopes());
        }

        assertEquals(0, concurrency.activeUserScopes());
        assertEquals(0, concurrency.activeDatasourceScopes());
    }
}
