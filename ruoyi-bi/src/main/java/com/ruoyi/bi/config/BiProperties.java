package com.ruoyi.bi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bi")
public record BiProperties(Query query, Option option, Cache cache, Datasource datasource, Insight insight) {
    public BiProperties {
        query = query == null ? new Query(50_000, 200_000, 60, 600, 4, 20, 100) : query;
        option = option == null ? new Option(1_000, 10, 60) : option;
        cache = cache == null ? new Cache(30, "bi:config:invalidate") : cache;
        datasource = datasource == null ? new Datasource(null, 5_000, 10_000) : datasource;
        insight = insight == null ? new Insight(new Provider(null, "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"),
            new Provider(null, "https://api.deepseek.com/chat/completions"), 10, 90, 200_000, 4) : insight;
    }

    public record Query(int defaultMaxRows, int hardMaxRows, int defaultTimeoutSeconds,
                        int maxTimeoutSeconds, int perUserConcurrency,
                        int perDatasourceConcurrency, int globalConcurrency) {}
    public record Option(int maxRows, int timeoutSeconds, int cacheTtlSeconds) {}
    public record Cache(int reportTtlMinutes, String invalidationChannel) {}
    public record Datasource(String masterKey, int connectionTimeoutMs, int validationTimeoutMs) {}
    public record Insight(Provider qwen, Provider deepseek, int connectTimeoutSeconds,
                          int requestTimeoutSeconds, int maxInputCharacters, int maxConcurrency) {
        public Insight {
            qwen = qwen == null ? new Provider(null, "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions") : qwen;
            deepseek = deepseek == null ? new Provider(null, "https://api.deepseek.com/chat/completions") : deepseek;
        }
    }
    public record Provider(String apiKey, String endpoint) {}
}
