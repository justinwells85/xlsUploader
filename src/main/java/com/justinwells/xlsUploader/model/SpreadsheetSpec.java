package com.justinwells.xlsUploader.model;

import java.util.HashMap;
import java.util.Map;

public class SpreadsheetSpec {
    private String specName; // e.g., "sales", "inventory"
    private Map<String, String> columnMappings; // Column name in spreadsheet -> JSON field name

    public SpreadsheetSpec(String specName) {
        this.specName = specName;
        this.columnMappings = new HashMap<>();
    }

    public void addMapping(String columnName, String jsonFieldName) {
        columnMappings.put(columnName, jsonFieldName);
    }

    public String getSpecName() {
        return specName;
    }

    public Map<String, String> getColumnMappings() {
        return columnMappings;
    }
}