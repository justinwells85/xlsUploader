package com.justinwells.xlsUploader.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class XlsSpec {
    private static final Logger logger = LoggerFactory.getLogger(XlsSpec.class);
    private final ObjectMapper objectMapper;
    private final Map<String, List<HeaderSpec>> specs = new HashMap<>();

    @Autowired
    public XlsSpec(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        loadSpecs();
    }

    private void loadSpecs() throws IOException {
        File specDir = new File("src/main/resources/xlsSpecs");
        if (!specDir.exists() || !specDir.isDirectory()) {
            throw new IOException("Spec directory not found: " + specDir.getAbsolutePath());
        }

        File[] specFiles = specDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (specFiles == null || specFiles.length == 0) {
            throw new IOException("No JSON spec files found in " + specDir.getAbsolutePath());
        }

        for (File specFile : specFiles) {
            String specName = specFile.getName().replace(".json", "");
            Map<String, Object> specData = objectMapper.readValue(specFile, new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> headersData = (List<Map<String, Object>>) specData.get("headers");
            if (headersData == null) {
                throw new IOException("No 'headers' field found in spec file: " + specFile.getName());
            }

            specs.put(specName, parseHeaders(headersData));
            logger.info("Loaded spec: {} with headers: {}", specName, specs.get(specName));
        }
    }

    private List<HeaderSpec> parseHeaders(List<Map<String, Object>> headersData) {
        List<HeaderSpec> headers = new ArrayList<>();
        for (Map<String, Object> data : headersData) {
            headers.add(new HeaderSpec(
                (String) data.get("header"),
                (String) data.get("field"),
                (String) data.get("type"),
                (Boolean) data.get("required")));
        }
        return headers;
    }

    public List<HeaderSpec> getSpec(String specName) {
        return specs.get(specName);
    }

    public Map<String, Integer> getHeaderMap(String specName, Row headerRow) {
        List<HeaderSpec> expectedHeaders = getSpec(specName);
        if (expectedHeaders == null) {
            throw new IllegalArgumentException("Unknown spec: " + specName);
        }

        Map<String, Integer> headerMap = new HashMap<>();
        // Map headers to their positions
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = headerRow.getCell(i) != null ? headerRow.getCell(i).toString().trim() : "";
            for (HeaderSpec h : expectedHeaders) {
                if (h.getHeader().equals(header)) {
                    headerMap.put(h.getField(), i);
                    break; // Stop checking once a match is found
                }
            }
        }

        // Validate required headers
        List<String> missingHeaders = new ArrayList<>();
        for (HeaderSpec h : expectedHeaders) {
            if (h.isRequired() && !headerMap.containsKey(h.getField())) {
                missingHeaders.add(h.getHeader());
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("Missing required headers for spec " + specName + ": " + missingHeaders);
        }

        return headerMap;
    }

    // Inner class to represent header spec
    public static class HeaderSpec {
        private final String header;
        private final String field;
        private final String type;
        private final boolean required;

        public HeaderSpec(String header, String field, String type, Boolean required) {
            this.header = header;
            this.field = field;
            this.type = type;
            this.required = required != null ? required : false; // Default to false if null
        }

        public String getHeader() {
            return header;
        }

        public String getField() {
            return field;
        }

        public String getType() {
            return type;
        }

        public boolean isRequired() {
            return required;
        }

        @Override
        public String toString() {
            return "HeaderSpec{header='" + header + "', field='" + field + "', type='" + type + "', required=" + required + "}";
        }
    }
}