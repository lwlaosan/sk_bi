package com.ruoyi.bi.support;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class IdGenerator {
    private final AtomicLong sequence = new AtomicLong();

    public long nextId() {
        long millis = System.currentTimeMillis() - 1_704_067_200_000L;
        return (millis << 20) | (sequence.getAndIncrement() & 0xFFFFF);
    }
}

