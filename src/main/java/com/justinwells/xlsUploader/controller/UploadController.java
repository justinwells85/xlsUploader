package com.justinwells.xlsUploader.controller;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import com.justinwells.xlsUploader.model.SpreadsheetData;
import com.justinwells.xlsUploader.service.SpreadsheetParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SqsTemplate;

import java.io.IOException;

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
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        logger.info("Received upload request for file: {}", file.getOriginalFilename());
        if (file.isEmpty()) {
            logger.warn("Upload rejected: File is empty");
            return ResponseEntity.badRequest().body("{\"message\": \"No file uploaded\"}");
        }

        String filename = file.getOriginalFilename();
        if (filename == null) {
            logger.warn("Upload rejected: Filename is null");
            return ResponseEntity.badRequest().body("{\"message\": \"No filename provided\"}");
        }
        if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) {
            logger.warn("Upload rejected: Invalid file type - {}", filename);
            return ResponseEntity.badRequest().body("{\"message\": \"Invalid file type. Only .xls or .xlsx allowed\"}");
        }

        try {
            if (file.getSize() > 10 * 1024 * 1024) {
                logger.warn("Upload rejected: File too large - {} bytes", file.getSize());
                return ResponseEntity.badRequest().body("{\"message\": \"File exceeds 10MB limit\"}");
            }

            SpreadsheetData parsedData = spreadsheetParser.parseSpreadsheet(file, salesSpec);
            if (parsedData.getData().isEmpty()) {
                logger.warn("Upload rejected: No valid data in file - {}", filename);
                return ResponseEntity.badRequest().body("{\"message\": \"No valid data found in spreadsheet\"}");
            }

            String jsonData = objectMapper.writeValueAsString(parsedData);
            logger.info("Converted to JSON: {}", jsonData);

            try {
                sqsTemplate.send("spreadsheet-queue", jsonData);
                logger.info("Sent JSON to SQS queue: spreadsheet-queue");
            } catch (Exception e) {
                logger.error("SQS send failed: {} - Type: {}", e.getMessage(), e.getClass().getName(), e);
                throw e; // Trigger retry
            }

            return ResponseEntity.ok("{\"message\": \"File uploaded, parsed, and queued successfully: " + filename + "\"}");
        } catch (IOException e) {
            logger.error("Failed to process spreadsheet: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body("{\"message\": \"Error processing file: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            logger.error("Unexpected error during upload: {} - Type: {}", e.getMessage(), e.getClass().getName(), e);
            throw e; // Trigger retry for any other exceptions
        }
    }

    @Recover
    public ResponseEntity<String> recoverUploadFile(Exception e, MultipartFile file) {
        logger.error("All retries failed for file: {}", file.getOriginalFilename(), e);
        return ResponseEntity.status(500)
                .body("{\"message\": \"Failed to queue message after retries: " + e.getMessage() + "\"}");
    }
}