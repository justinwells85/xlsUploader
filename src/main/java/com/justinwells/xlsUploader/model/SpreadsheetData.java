package com.justinwells.xlsUploader.model;

import java.util.List;
import java.util.Map;

public class SpreadsheetData {
    private String specName;
    private String filename;
    private List<Map<String, String>> data;

    public SpreadsheetData(String specName, String filename, List<Map<String, String>> data) {
        this.specName = specName;
        this.filename = filename;
        this.data = data;
    }

    // Getters
    public String getSpecName() {
        return specName;
    }

    public String getFilename() {
        return filename;
    }

    public List<Map<String, String>> getData() {
        return data;
    }

    // Setters (optional, if needed)
    public void setSpecName(String specName) {
        this.specName = specName;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setData(List<Map<String, String>> data) {
        this.data = data;
    }
}