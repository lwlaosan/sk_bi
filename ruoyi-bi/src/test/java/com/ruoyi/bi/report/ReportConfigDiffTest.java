package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportConfigDiffTest {
    @Test void reportsLeafChanges() throws Exception {
        var mapper=new ObjectMapper(); var before=mapper.readTree("{\"baseInfo\":{\"name\":\"A\"},\"controls\":[]}");
        var after=mapper.readTree("{\"baseInfo\":{\"name\":\"B\"},\"controls\":[{\"key\":\"x\"}]}");
        var changes=ReportConfigDiff.compare(before,after);
        assertEquals(2,changes.size()); assertEquals("$.baseInfo.name",changes.get(0).path());
    }
}
