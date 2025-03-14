package com.justinwells.xlsUploader.model;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class SpreadsheetSpec {
    private final String specName;
    private final Map<String, String> headerMappings;

    // Constructor for SpecConfig
    public SpreadsheetSpec(String specName) {
        this.specName = specName;
        this.headerMappings = new HashMap<>();
    }

    // Default constructor for @Component (optional, if needed elsewhere)
    public SpreadsheetSpec() {
        this("sales"); // Default to "sales"
    }

    public void addMapping(String header, String field) {
        headerMappings.put(header, field);
    }

    public String getSpecName() {
        return specName;
    }

    public Map<String, String> getHeaderMappings() {
        return headerMappings;
    }

    // For SpreadsheetParser compatibility
    public List<String> getExpectedHeaders() {
        return List.copyOf(headerMappings.keySet());
    }
}