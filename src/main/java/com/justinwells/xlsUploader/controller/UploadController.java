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
import java.util.List;
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
    }

    @PostMapping("/upload")
    @Retryable(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public ResponseEntity<String> uploadFile(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) return ResponseEntity.badRequest().body("{\"message\": \"No file uploaded\"}");
        String filename = file.getOriginalFilename();
        if (filename == null) return ResponseEntity.badRequest().body("{\"message\": \"No filename provided\"}");
        if (!filename.endsWith(".xls") && !filename.endsWith(".xlsx")) 
            return ResponseEntity.badRequest().body("{\"message\": \"Invalid file type. Only .xls or .xlsx allowed\"}");
        try {
            if (file.getSize() > 10 * 1024 * 1024) 
                return ResponseEntity.badRequest().body("{\"message\": \"File exceeds 10MB limit\"}");
            SpreadsheetData parsedData = spreadsheetParser.parseSpreadsheet(file, salesSpec);
            if (parsedData.getData().isEmpty()) 
                return ResponseEntity.badRequest().body("{\"message\": \"No valid data found in spreadsheet\"}");

            // Batch queuing: Send each batch as raw data list
            List<Map<String, String>> rows = parsedData.getData();
            int batchSize = 1;
            for (int i = 0; i < rows.size(); i += batchSize) {
                int end = Math.min(i + batchSize, rows.size());
                List<Map<String, String>> batch = rows.subList(i, end);
                String jsonBatch = objectMapper.writeValueAsString(batch); // Just the batch, not SpreadsheetData
                sqsTemplate.send("spreadsheet-queue", jsonBatch);
                logger.info("Queued batch {}/{} for file: {}", (i / batchSize) + 1, (rows.size() + batchSize - 1) / batchSize, filename);
            }

            return ResponseEntity.ok("{\"message\": \"File uploaded, parsed, and queued in batches successfully: " + filename + "\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"message\": \"Error processing file: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            throw e;
        }
    }

    @Recover
    public ResponseEntity<String> recoverUploadFile(Exception e, MultipartFile file) {
        return ResponseEntity.status(500)
                .body("{\"message\": \"Failed to queue message after retries: " + e.getMessage() + "\"}");
    }
}