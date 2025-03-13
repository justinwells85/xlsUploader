package com.justinwells.xlsUploader.controller;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import com.justinwells.xlsUploader.model.SpreadsheetData;
import com.justinwells.xlsUploader.service.SpreadsheetParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;

import java.io.IOException;
import java.util.Map;

@RestController
public class UploadController {
    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final SpreadsheetSpec salesSpec;
    private final SpreadsheetParser spreadsheetParser;
    private final ObjectMapper objectMapper;
    private final SqsTemplate sqsTemplate;

    @Autowired
    public UploadController(SpreadsheetSpec salesSpec, SpreadsheetParser spreadsheetParser, 
                            ObjectMapper objectMapper, SqsTemplate sqsTemplate) {
        this.salesSpec = salesSpec;
        this.spreadsheetParser = spreadsheetParser;
        this.objectMapper = objectMapper;
        this.sqsTemplate = sqsTemplate;
        logger.info("UploadController initialized with spec: {}", salesSpec.getSpecName());
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("Received upload request for file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("{\"message\": \"No file uploaded\"}");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xls") && !filename.endsWith(".xlsx"))) {
            return ResponseEntity.badRequest().body("{\"message\": \"Invalid file type. Only .xls or .xlsx allowed\"}");
        }

        try {
            SpreadsheetData parsedData = spreadsheetParser.parseSpreadsheet(file, salesSpec);
            String jsonData = objectMapper.writeValueAsString(parsedData);
            logger.info("Converted to JSON: {}", jsonData);

            sqsTemplate.send("spreadsheet-queue", jsonData);
            logger.info("Sent JSON to SQS queue: spreadsheet-queue");

            return ResponseEntity.ok("{\"message\": \"File uploaded, parsed, and queued successfully: " + filename + "\"}");
        } catch (IOException e) {
            logger.error("Failed to process spreadsheet: {}", e.getMessage());
            return ResponseEntity.status(500).body("{\"message\": \"Error processing file\"}");
        }
    }
}