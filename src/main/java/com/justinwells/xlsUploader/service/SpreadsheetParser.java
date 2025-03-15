package com.justinwells.xlsUploader.service;

import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justinwells.xlsUploader.model.XlsSpec;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
public class SpreadsheetParser {
    private final XlsSpec xlsSpec;
    private final AmazonSQSAsync sqs;
    private final Map<String, String> queueUrls;

    @Autowired
    public SpreadsheetParser(XlsSpec xlsSpec, AmazonSQSAsync sqs,
                             @Value("${aws.sqs.participant-queue-url}") String participantQueueUrl,
                             @Value("${aws.sqs.payment-queue-url}") String paymentQueueUrl,
                             @Value("${aws.sqs.properties-queue-url}") String propertiesQueueUrl,
                             @Value("${aws.sqs.assignments-queue-url}") String assignmentsQueueUrl,
                             @Value("${aws.sqs.events-queue-url}") String eventsQueueUrl) {
        this.xlsSpec = xlsSpec;
        this.sqs = sqs;
        this.queueUrls = new HashMap<>();
        queueUrls.put("participant", participantQueueUrl);
        queueUrls.put("payment", paymentQueueUrl);
        queueUrls.put("properties", propertiesQueueUrl);
        queueUrls.put("assignments", assignmentsQueueUrl);
        queueUrls.put("events", eventsQueueUrl);
    }

    public void parseAndQueue(File file, String specName) throws IOException, InvalidFormatException {
        Workbook workbook = new XSSFWorkbook(file);
        Sheet sheet = workbook.getSheetAt(0);
        Row headerRow = sheet.getRow(0);

        XlsSpec.SpecDefinition specDef = xlsSpec.getSpec(specName);
        if (specDef == null) {
            throw new IllegalArgumentException("Unknown spec: " + specName);
        }

        String queueUrl = queueUrls.get(specName);
        if (queueUrl == null) {
            throw new IllegalArgumentException("No queue defined for spec: " + specName);
        }

        Map<String, Integer> headerMap = new HashMap<>();
        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            String header = headerRow.getCell(i).getStringCellValue();
            headerMap.put(header, i);
        }

        for (XlsSpec.HeaderMapping mapping : specDef.getHeaders()) {
            if (mapping.isRequired() && !headerMap.containsKey(mapping.getHeader())) {
                throw new IllegalArgumentException("Missing required column: " + mapping.getHeader());
            }
        }

        ObjectMapper mapper = new ObjectMapper();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            Map<String, Object> rowData = new HashMap<>();
            for (XlsSpec.HeaderMapping mapping : specDef.getHeaders()) {
                if (headerMap.containsKey(mapping.getHeader())) {
                    Cell cell = row.getCell(headerMap.get(mapping.getHeader()));
                    rowData.put(mapping.getField(), validateAndGetCellValue(cell, mapping.getType()));
                }
            }
            sqs.sendMessageAsync(new SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(mapper.writeValueAsString(rowData)));
        }
    }

    private Object validateAndGetCellValue(Cell cell, String expectedType) {
        if (cell == null) return null;
        switch (expectedType) {
            case "STRING": return cell.getCellType() == CellType.STRING ? cell.getStringCellValue() : null;
            case "INTEGER": return cell.getCellType() == CellType.NUMERIC ? (int) cell.getNumericCellValue() : null;
            case "DATE": return cell.getCellType() == CellType.NUMERIC ? cell.getDateCellValue() : null;
            default: throw new IllegalArgumentException("Unknown type: " + expectedType);
        }
    }
}