package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ProcedureParameterBinderTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final ProcedureParameterBinder binder=new ProcedureParameterBinder(mapper);

    @Test void resolvesSourcesInOrdinalOrder() throws Exception {
        var mappings=mapper.readTree("""
          [{"parameterOrdinal":4,"mysqlDataType":"date","sourceType":"CONTROL","sourceKey":"ctrl_date.start"},
           {"parameterOrdinal":1,"mysqlDataType":"varchar","sourceType":"SYSTEM","sourceKey":"user_id"},
           {"parameterOrdinal":2,"mysqlDataType":"varchar","sourceType":"REGION","sourceKey":"region_key"},
           {"parameterOrdinal":3,"mysqlDataType":"varchar","sourceType":"COMPONENT","sourceKey":"component_key"},
           {"parameterOrdinal":5,"mysqlDataType":"json","sourceType":"DRILL","sourceKey":"value"},
           {"parameterOrdinal":6,"mysqlDataType":"varchar","sourceType":"CONSTANT","constantValue":"fixed"},
           {"parameterOrdinal":7,"mysqlDataType":"varchar","sourceType":"NULL"},
           {"parameterOrdinal":8,"mysqlDataType":"varchar","sourceType":"CONSTANT","constantValue":""}]
          """);
        var controls=Map.of("ctrl_date",mapper.readTree("{\"start\":\"2026-08-25\"}"));
        var params=binder.resolve(mappings,new ProcedureParameterBinder.Context(42,"TABLE","main_table",controls,"",mapper.nullNode()));
        assertEquals("42",params.get(0).value()); assertEquals("TABLE",params.get(1).value());
        assertEquals("main_table",params.get(2).value()); assertEquals(LocalDate.of(2026,8,25),params.get(3).value());
        assertNull(params.get(4).value()); assertEquals("fixed",params.get(5).value()); assertNull(params.get(6).value());
        assertEquals("", params.get(7).value());
    }
}
