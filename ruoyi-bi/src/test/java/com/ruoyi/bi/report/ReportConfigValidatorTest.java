package com.ruoyi.bi.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportConfigValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ReportConfigValidator validator = new ReportConfigValidator();

    @Test
    void acceptsInitialTableAndRootStructure() throws Exception {
        var config = mapper.readTree("""
            {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1",
             "defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
             "acl":{"roleIds":[],"userIds":[]},"controls":[],"parameterMappings":[],"components":[{"componentKey":"main_table",
             "regionType":"TABLE","routes":[{"routeCode":"ROOT","viewType":"TABLE","fields":[],"drillEdges":[]}]}]}
            """);
        assertTrue(validator.validate(config).valid());
    }

    @Test
    void acceptsAllSupportedComponentViewTypes() throws Exception {
        for (String view : new String[]{"BAR", "STACKED_BAR", "HORIZONTAL_BAR", "LINE", "AREA", "PIE", "DONUT", "GAUGE", "KPI"}) {
            var config = mapper.readTree("""
                {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1",
                 "defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
                 "acl":{"roleIds":[],"userIds":[]},"controls":[],"parameterMappings":[],"components":[{"componentKey":"chart",
                 "regionType":"COMPONENT","routes":[{"routeCode":"ROOT","viewType":"%s","fields":[],"drillEdges":[]}]}]}
                """.formatted(view));
            assertTrue(validator.validate(config).valid(), view);
        }
    }

    @Test
    void rejectsDuplicateTableRegionsAndMissingRoot() throws Exception {
        var config = mapper.readTree("""
            {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1",
             "defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
             "acl":{"roleIds":[],"userIds":[]},"controls":[],"parameterMappings":[],"components":[
             {"componentKey":"a","regionType":"TABLE","routes":[{"routeCode":"A","viewType":"TABLE","fields":[],"drillEdges":[]}]},
             {"componentKey":"b","regionType":"TABLE","routes":[{"routeCode":"ROOT","viewType":"TABLE","fields":[],"drillEdges":[]}]}]}
            """);
        assertFalse(validator.validate(config).valid());
    }

    @Test
    void rejectsUnsafeSqlOptionsAndMissingDatasource() throws Exception {
        var config = mapper.readTree("""
            {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1",
             "defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
             "acl":{"roleIds":[],"userIds":[]},"parameterMappings":[],
             "controls":[{"controlKey":"region","label":"区域","controlType":"SINGLE_SELECT",
             "optionSource":"SQL","optionSql":"select id from region; drop table region","targetComponentKeys":["main_table"]}],
             "components":[{"componentKey":"main_table","regionType":"TABLE",
             "routes":[{"routeCode":"ROOT","viewType":"TABLE","fields":[],"drillEdges":[]}]}]}
            """);
        var result = validator.validate(config);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(issue -> "DATASOURCE_REQUIRED".equals(issue.code())));
        assertTrue(result.errors().stream().anyMatch(issue -> "OPTION_SQL_UNSAFE".equals(issue.code())));
    }

    @Test
    void protectsCurrentUserProcedureParameter() throws Exception {
        var config = mapper.readTree("""
            {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1",
             "defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
             "acl":{"roleIds":[],"userIds":[]},"controls":[],
             "parameterMappings":[{"datasourceId":1,"procedureName":"sp_sales","parameterOrdinal":1,
             "parameterName":"p_user_id","parameterMode":"IN","sourceType":"CONSTANT","constantValue":"1"}],
             "components":[{"componentKey":"main_table","regionType":"TABLE",
             "routes":[{"routeCode":"ROOT","viewType":"TABLE","fields":[],"drillEdges":[]}]}]}
            """);
        var result = validator.validate(config);
        assertFalse(result.valid());
        assertTrue(result.errors().stream().anyMatch(issue -> "SYSTEM_PARAMETER_REQUIRED".equals(issue.code())));
    }

    @Test
    void validatesEnabledInsightAndPosition() throws Exception {
        var config=mapper.readTree("""
            {"baseInfo":{"reportName":"销售分析","status":"DISABLED","defaultDatasourceId":"1","defaultProcedureName":"sp_sales","maxRows":50000,"timeoutSeconds":60},
             "acl":{"roleIds":[],"userIds":[]},"controls":[],"parameterMappings":[],
             "components":[{"componentKey":"main_table","regionType":"TABLE","routes":[{"routeCode":"ROOT","viewType":"TABLE","fields":[],"drillEdges":[]}]}],
             "insight":{"enabled":true,"title":"经营洞察","position":"FLOAT_RIGHT","provider":"QWEN","model":"qwen-plus","prompt":"分析趋势和异常","maxRowsPerComponent":50,"maxTokens":2048,"temperature":0.2}}
            """);
        assertTrue(validator.validate(config).valid());
        ((com.fasterxml.jackson.databind.node.ObjectNode)config.path("insight")).put("position","UNKNOWN");
        assertTrue(validator.validate(config).errors().stream().anyMatch(issue->"INSIGHT_POSITION_INVALID".equals(issue.code())));
    }
}
