package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ReportConfigDiff {
    private ReportConfigDiff() {}

    static List<ReportDtos.DiffItem> compare(JsonNode before, JsonNode after) {
        List<ReportDtos.DiffItem> result = new ArrayList<>();
        walk("$", before, after, result);
        return List.copyOf(result);
    }

    private static void walk(String path, JsonNode before, JsonNode after, List<ReportDtos.DiffItem> result) {
        if (before == null || before.isMissingNode() || after == null || after.isMissingNode()) {
            result.add(new ReportDtos.DiffItem(path, before, after)); return;
        }
        if (before.equals(after)) return;
        if (before.isObject() && after.isObject()) {
            Set<String> names = new LinkedHashSet<>();
            before.fieldNames().forEachRemaining(names::add); after.fieldNames().forEachRemaining(names::add);
            for (String name : names) walk(path + "." + name, before.path(name), after.path(name), result);
        } else if (before.isArray() && after.isArray()) {
            int size = Math.max(before.size(), after.size());
            for (int i = 0; i < size; i++) walk(path + "[" + i + "]", before.path(i), after.path(i), result);
        } else result.add(new ReportDtos.DiffItem(path, before, after));
    }
}
