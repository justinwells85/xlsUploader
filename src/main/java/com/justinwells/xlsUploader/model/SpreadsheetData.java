package com.justinwells.xlsUploader.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SpreadsheetData {
    private String spec;
    private String filename;
    private List<Map<String, String>> data;

    public SpreadsheetData(String spec, String filename) {
        this.spec = spec;
        this.filename = filename;
        this.data = new ArrayList<>();
    }

    public void addRow(Map<String, String> row) {
        data.add(row);
    }

    // Getters and setters
    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public List<Map<String, String>> getData() {
        return data;
    }

    public void setData(List<Map<String, String>> data) {
        this.data = data;
    }
}