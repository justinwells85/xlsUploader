package com.justinwells.xlsUploader.service;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

@Service
public class SpreadsheetProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetProcessor.class);
    private final ObjectMapper objectMapper;

    public SpreadsheetProcessor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SqsListener("spreadsheet-queue")
    public void processMessage(String message) throws Exception {
        logger.info("Received from SQS: {}", message);
        List<Map<String, String>> batch = objectMapper.readValue(message, 
            objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        
        for (Map<String, String> row : batch) {
            logger.info("Row: {}", row);
        }
    }
}