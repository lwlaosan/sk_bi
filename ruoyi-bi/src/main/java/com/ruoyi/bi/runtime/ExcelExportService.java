package com.ruoyi.bi.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
final class ExcelExportService {
    byte[] component(RuntimeDtos.QueryResult result) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            fill(workbook,result,safeSheet(result.componentKey()));
            workbook.write(output); return output.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("Excel 导出失败", ex); }
    }
    byte[] report(List<RuntimeDtos.QueryResult> results){
        try(SXSSFWorkbook workbook=new SXSSFWorkbook(200);ByteArrayOutputStream output=new ByteArrayOutputStream()){
            java.util.Set<String> names=new java.util.HashSet<>();int index=1;
            for(RuntimeDtos.QueryResult result:results){String base=safeSheet(result.componentKey()),name=base;while(!names.add(name))name=safeSheet(base+"_"+(index++));fill(workbook,result,name);}
            workbook.write(output);return output.toByteArray();
        }catch(Exception ex){throw new IllegalStateException("整报表 Excel 导出失败",ex);}
    }
    private void fill(SXSSFWorkbook workbook,RuntimeDtos.QueryResult result,String sheetName){
            var sheet = workbook.createSheet(sheetName);
            List<JsonNode> fields = new ArrayList<>();
            result.fields().forEach(field -> { if (field.path("visible").asBoolean(true)) fields.add(field); });
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            var font = workbook.createFont(); font.setBold(true); headerStyle.setFont(font);
            for (int i=0;i<fields.size();i++) { Cell cell=header.createCell(i); cell.setCellValue(fields.get(i).path("displayName").asText(fields.get(i).path("physicalName").asText())); cell.setCellStyle(headerStyle); }
            int rowIndex=1;
            for (Map<String,Object> data : result.rows()) {
                Row row=sheet.createRow(rowIndex++);
                for (int i=0;i<fields.size();i++) write(row.createCell(i), data.get(fields.get(i).path("physicalName").asText()));
            }
            if (result.truncated()) {
                Row note=sheet.createRow(rowIndex); note.createCell(0).setCellValue("注意：结果已按平台上限截断");
            }
            for(int i=0;i<fields.size();i++) sheet.setColumnWidth(i, Math.min(80, Math.max(12, fields.get(i).path("displayName").asText().length()+4))*256);
    }

    private void write(Cell cell,Object value) {
        if(value==null)return;
        if(value instanceof Number number) { try { cell.setCellValue(new BigDecimal(number.toString()).doubleValue()); } catch(Exception ex){cell.setCellValue(number.toString());} return; }
        if(value instanceof Boolean bool){cell.setCellValue(bool);return;}
        String text=value instanceof JsonNode node?node.toString():String.valueOf(value);
        if(!text.isEmpty() && "=+-@".indexOf(text.charAt(0))>=0) text="'"+text;
        cell.setCellValue(text);
    }
    private static String safeSheet(String name){String safe=name.replaceAll("[\\\\/?*\\[\\]:]","_");return safe.isBlank()?"报表":safe.substring(0,Math.min(31,safe.length()));}
}
