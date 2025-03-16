package com.justinwells.xlsUploader.service;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justinwells.xlsUploader.model.XlsSpec;
import com.justinwells.xlsUploader.model.XlsSpec.HeaderSpec;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SpreadsheetParser {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetParser.class);
    private final AmazonSQSAsync sqsClient;
    private final XlsSpec xlsSpec;
    private final ObjectMapper objectMapper;
    private final Map<String, String> queueUrls;

    @Autowired
    public SpreadsheetParser(AmazonSQSAsync sqsClient, XlsSpec xlsSpec, ObjectMapper objectMapper,
                             @Value("${aws.sqs.participant-queue-url}") String participantQueueUrl,
                             @Value("${aws.sqs.payment-queue-url}") String paymentQueueUrl,
                             @Value("${aws.sqs.properties-queue-url}") String propertiesQueueUrl,
                             @Value("${aws.sqs.assignments-queue-url}") String assignmentsQueueUrl,
                             @Value("${aws.sqs.events-queue-url}") String eventsQueueUrl) {
        this.sqsClient = sqsClient;
        this.xlsSpec = xlsSpec;
        this.objectMapper = objectMapper;
        this.queueUrls = new HashMap<>();
        queueUrls.put("participant", participantQueueUrl);
        queueUrls.put("payment", paymentQueueUrl);
        queueUrls.put("properties", propertiesQueueUrl);
        queueUrls.put("assignments", assignmentsQueueUrl);
        queueUrls.put("events", eventsQueueUrl);
    }

    public void parseAndQueue(File file, String specName) throws IOException, InvalidFormatException {
        String s3Key = file.getName(); // Placeholder; we'll pass the real S3 key from UploadController
        parseAndQueue(file, specName, s3Key);
    }

    public void parseAndQueue(File file, String specName, String s3Key) throws IOException, InvalidFormatException {
        String queueUrl = queueUrls.get(specName);
        if (queueUrl == null) {
            throw new IllegalArgumentException("No queue defined for spec: " + specName);
        }

        try (Workbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IllegalArgumentException("No header row found in file: " + file.getName());
            }

            Map<String, Integer> headerMap = xlsSpec.getHeaderMap(specName, headerRow);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, Object> rowData = new HashMap<>();
                for (Map.Entry<String, Integer> entry : headerMap.entrySet()) {
                    String field = entry.getKey(); // field is now "participantId", "firstName", etc.
                    int column = entry.getValue();
                    rowData.put(field, row.getCell(column) != null ? row.getCell(column).toString() : null);
                }

                // Add traceability fields
                rowData.put("s3Key", s3Key);
                rowData.put("lineNumber", i + 1); // +1 since row index is 0-based, line numbers start at 1
                rowData.put("createdAt", Instant.now().toString()); // ISO 8601 timestamp

                String messageBody = objectMapper.writeValueAsString(rowData);
                sqsClient.sendMessage(queueUrl, messageBody);
                logger.info("Queued message for spec {}: {}", specName, messageBody);
            }
        }
    }
}