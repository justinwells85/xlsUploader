package com.justinwells.xlsUploader.model;

import com.fasterxml.jackson.core.type.TypeReference; // Added for TypeReference
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row; // Added for Row
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class XlsSpec {
    private final ObjectMapper objectMapper;
    private final Map<String, List<HeaderSpec>> specs = new HashMap<>();

    @Autowired
    public XlsSpec(ObjectMapper objectMapper) throws IOException {
        this.objectMapper = objectMapper;
        File specDir = new File("src/main/resources/xlsSpecs");
        File[] specFiles = specDir.listFiles((dir, name) -> name.endsWith(".json"));
        for (File specFile : specFiles) {
            String specName = specFile.getName().replace(".json", "");
            Map<String, Object> specData = objectMapper.readValue(specFile, new TypeReference<Map<String, Object>>() {});
            List<Map<String, Object>> headersData = (List<Map<String, Object>>) specData.get("headers");
            List<HeaderSpec> headers = new ArrayList<>();
            for (Map<String, Object> data : headersData) {
                headers.add(new HeaderSpec(
                    (String) data.get("header"),
                    (String) data.get("field"),
                    (String) data.get("type"),
                    (Boolean) data.get("required"),
                    (String) data.get("constraint")
                ));
            }
            specs.put(specName, headers);
        }
    }

    public List<HeaderSpec> getSpec(String specName) {
        return specs.get(specName);
    }

    public Map<String, Integer> getHeaderMap(String specName, Row headerRow) {
        List<HeaderSpec> expectedHeaders = getSpec(specName);
        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = headerRow.getCell(i) != null ? headerRow.getCell(i).toString().trim() : "";
            for (HeaderSpec h : expectedHeaders) {
                if (h.getHeader().equals(header)) {
                    headerMap.put(h.getField(), i);
                    break;
                }
            }
        }
        List<String> missingHeaders = new ArrayList<>();
        for (HeaderSpec h : expectedHeaders) {
            if (h.isRequired() && !headerMap.containsKey(h.getField())) {
                missingHeaders.add(h.getHeader());
            }
        }
        if (!missingHeaders.isEmpty()) {
            throw new IllegalArgumentException("Missing required headers: " + missingHeaders);
        }
        return headerMap;
    }

    public static class HeaderSpec {
        private final String header;
        private final String field;
        private final String type;
        private final boolean required;
        private final String constraint;

        public HeaderSpec(String header, String field, String type, Boolean required, String constraint) {
            this.header = header;
            this.field = field;
            this.type = type;
            this.required = required != null ? required : false;
            this.constraint = constraint != null ? constraint.toLowerCase() : "any";
        }

        public String getHeader() { return header; }
        public String getField() { return field; }
        public String getType() { return type; }
        public boolean isRequired() { return required; }
        public String getConstraint() { return constraint; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            HeaderSpec that = (HeaderSpec) o;
            return required == that.required &&
                   Objects.equals(header, that.header) &&
                   Objects.equals(field, that.field) &&
                   Objects.equals(type, that.type) &&
                   Objects.equals(constraint, that.constraint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(header, field, type, required, constraint);
        }
    }
}