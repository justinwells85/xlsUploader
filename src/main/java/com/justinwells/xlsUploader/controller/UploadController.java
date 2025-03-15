package com.justinwells.xlsUploader.controller;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import com.justinwells.xlsUploader.model.SpreadsheetData;
import com.justinwells.xlsUploader.service.SpreadsheetParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

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
    private final S3Client s3Client; // Injected from AwsConfig
    @Value("${spring.cloud.aws.s3.bucket}")
    private String bucketName;

    @Autowired
    public UploadController(SpreadsheetSpec salesSpec, SpreadsheetParser spreadsheetParser, 
                            ObjectMapper objectMapper, SqsTemplate sqsTemplate, 
                            S3Client s3Client) {
        this.salesSpec = salesSpec;
        this.spreadsheetParser = spreadsheetParser;
        this.objectMapper = objectMapper;
        this.sqsTemplate = sqsTemplate;
        this.s3Client = s3Client; // No need to hardcode here anymore
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

            // Upload to S3 using the injected S3Client
            String objectKey = "uploads/" + System.currentTimeMillis() + "_" + filename;
            PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
            s3Client.putObject(putRequest, software.amazon.awssdk.core.sync.RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            logger.info("Uploaded file to S3: {}/{}", bucketName, objectKey);

            // Parse and queue
            SpreadsheetData parsedData = spreadsheetParser.parseSpreadsheet(file, salesSpec);
            if (parsedData.getData().isEmpty()) 
                return ResponseEntity.badRequest().body("{\"message\": \"No valid data found in spreadsheet\"}");

            List<Map<String, String>> rows = parsedData.getData();
            int batchSize = 1;
            for (int i = 0; i < rows.size(); i += batchSize) {
                int end = Math.min(i + batchSize, rows.size());
                List<Map<String, String>> batch = rows.subList(i, end);
                String jsonBatch = objectMapper.writeValueAsString(batch);
                sqsTemplate.send("spreadsheet-queue", jsonBatch);
            }

            return ResponseEntity.ok("{\"message\": \"File uploaded to S3 and queued: " + objectKey + "\"}");
        } catch (IOException e) {
            return ResponseEntity.status(500).body("{\"message\": \"Error processing file: " + e.getMessage() + "\"}");
        } catch (Exception e) {
            throw e; // Let retry handle it
        }
    }

    @Recover
    public ResponseEntity<String> recoverUploadFile(Exception e, MultipartFile file) {
        logger.error("Failed to upload file after retries", e); // Log full stack trace
        return ResponseEntity.status(500).body("{\"message\": \"Failed after retries: " + e.getMessage() + "\"}");
    }
}