package com.vlink.edge;

import com.vlink.common.protocol.NodeId;
import java.util.LinkedHashMap;
import java.util.Map;

// DedupCache 用于接收端去重，防止重复包重复写入 TUN。

final class DedupCache {
    private static final int MAX_ENTRIES = 4096;
    private final Map<String, Long> cache = new LinkedHashMap<String, Long>(MAX_ENTRIES, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    boolean seen(NodeId src, int seq) {
        String key = src.toString() + ":" + seq;
        if (cache.containsKey(key)) {
            return true;
        }
        cache.put(key, System.currentTimeMillis());
        return false;
    }
}
