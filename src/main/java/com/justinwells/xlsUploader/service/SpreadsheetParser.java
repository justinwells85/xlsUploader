package com.justinwells.xlsUploader.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justinwells.xlsUploader.model.XlsSpec;
import com.justinwells.xlsUploader.model.XlsSpec.HeaderSpec;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

@Service
public class SpreadsheetParser {

    private final SqsAsyncClient sqsClient;
    private final XlsSpec xlsSpec;
    private final ObjectMapper objectMapper;
    private final Map<String, String> queueUrls = new HashMap<>();

    @Autowired
    public SpreadsheetParser(SqsAsyncClient sqsClient, XlsSpec xlsSpec, ObjectMapper objectMapper) {
        this.sqsClient = sqsClient;
        this.xlsSpec = xlsSpec;
        this.objectMapper = objectMapper;
        queueUrls.put("participant", "participant-queue");
    }

    public void parseAndQueue(File file, String specName, String s3Key) throws IOException {
        String queueUrl = queueUrls.get(specName);
        if (queueUrl == null) {
            throw new IllegalArgumentException("No queue configured for spec: " + specName);
        }
        List<HeaderSpec> specHeaders = xlsSpec.getSpec(specName);
        if (specHeaders == null) {
            throw new IllegalArgumentException("Unknown spec: " + specName);
        }

        try (Workbook workbook = new XSSFWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Map<String, Integer> headerMap = xlsSpec.getHeaderMap(specName, headerRow);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, Object> rowData = new HashMap<>();
                List<String> errors = new ArrayList<>();

                for (HeaderSpec spec : specHeaders) {
                    Integer column = headerMap.get(spec.getField());
                    if (column == null) continue;
                    Cell cell = row.getCell(column);
                    Object value = validateCell(spec, cell, i + 1, errors);
                    rowData.put(spec.getField(), value);
                }

                if (!errors.isEmpty()) {
                    throw new IllegalArgumentException("Row " + (i + 1) + " errors: " + String.join(", ", errors));
                }

                rowData.put("s3Key", s3Key);
                rowData.put("lineNumber", i + 1);
                rowData.put("createdAt", Instant.now().toString());

                String messageBody = objectMapper.writeValueAsString(rowData);
                sendMessage(queueUrl, messageBody);
            }
        } catch (Exception e) {
            throw new IOException("Failed to parse and queue file: " + e.getMessage(), e);
        }
    }

    private void sendMessage(String queueUrl, String messageBody) {
        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        CompletableFuture<Void> future = sqsClient.sendMessage(request)
                .thenAccept(response -> {
                    System.out.println("Message sent successfully. Message ID: " + response.messageId());
                })
                .exceptionally(error -> {
                    System.err.println("Failed to send message: " + error.getMessage());
                    return null;
                });

        future.join();
    }

    private Object validateCell(HeaderSpec spec, Cell cell, int rowNum, List<String> errors) {
        String rawValue = cell != null ? cell.toString().trim() : null;
        System.out.println("Validating row " + rowNum + ", field " + spec.getField() + ": '" + rawValue + "'");

        if (rawValue == null || rawValue.isEmpty()) {
            if (spec.isRequired()) {
                errors.add(spec.getField() + " is required");
            }
            return null;
        }

        String constraint = spec.getConstraint();

        switch (spec.getType().toUpperCase()) {
            case "STRING":
                if ("email".equals(constraint)) {
                    Pattern emailPattern = Pattern.compile("^[\\w-\\.]+@([\\w-]+\\.)+[a-zA-Z]{2,}$");
                    if (!emailPattern.matcher(rawValue).matches()) {
                        errors.add(spec.getField() + " must be a valid email address (e.g., user@domain.com)");
                        return null;
                    }
                }
                return rawValue;

            case "DATE":
                LocalDate date = null;
                if (cell != null && cell.getCellType() == CellType.NUMERIC && DateUtil.isValidExcelDate(cell.getNumericCellValue())) {
                    date = cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                } else {
                    try {
                        date = LocalDate.parse(rawValue); // Try ISO format (yyyy-MM-dd)
                    } catch (DateTimeParseException e) {
                        // Try Excel's default format (dd-MMM-yyyy)
                        try {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
                            date = LocalDate.parse(rawValue, formatter);
                        } catch (DateTimeParseException e2) {
                            errors.add(spec.getField() + " must be a valid date (e.g., 2025-03-01 or 01-Mar-2025)");
                            return null;
                        }
                    }
                }

                LocalDate now = LocalDate.now(ZoneId.systemDefault());
                System.out.println("Current date: " + now + ", Parsed date: " + date);
                if ("past".equals(constraint) && !date.isBefore(now)) {
                    errors.add(spec.getField() + " must be a past date");
                } else if ("future".equals(constraint) && !date.isAfter(now)) {
                    errors.add(spec.getField() + " must be a future date");
                } else if (!"any".equals(constraint) && !"past".equals(constraint) && !"future".equals(constraint)) {
                    errors.add(spec.getField() + " has invalid constraint for DATE: " + constraint);
                }
                return date != null ? date.toString() : null;

            default:
                errors.add(spec.getField() + " has unsupported type " + spec.getType());
                return null;
        }
    }
}