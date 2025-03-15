package com.justinwells.xlsUploader.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class XlsSpec {
    private Map<String, SpecDefinition> definitions = new HashMap<>();

    @PostConstruct
    public void init() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:xlsSpecs/*.json");
        for (Resource resource : resources) {
            SpecDefinition spec = mapper.readValue(resource.getFile(), SpecDefinition.class);
            definitions.put(spec.getSpecName(), spec);
        }
    }

    public SpecDefinition getSpec(String specName) {
        return definitions.getOrDefault(specName, definitions.get("sales")); // Fallback to "sales" if spec not found
    }

    public static class SpecDefinition {
        private String specName;
        private List<HeaderMapping> headers;

        public String getSpecName() { return specName; }
        public void setSpecName(String specName) { this.specName = specName; }
        public List<HeaderMapping> getHeaders() { return headers; }
        public void setHeaders(List<HeaderMapping> headers) { this.headers = headers; }
    }

    public static class HeaderMapping {
        private String header;
        private String field;
        private String type;
        private boolean required;

        public String getHeader() { return header; }
        public String getField() { return field; }
        public String getType() { return type; }
        public boolean isRequired() { return required; }

        public void setHeader(String header) { this.header = header; }
        public void setField(String field) { this.field = field; }
        public void setType(String type) { this.type = type; }
        public void setRequired(boolean required) { this.required = required; }
    }
}