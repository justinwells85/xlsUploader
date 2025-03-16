package com.justinwells.xlsUploader.controller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.ObjectTagging;
import com.amazonaws.services.s3.model.PutObjectResult;
import com.amazonaws.services.s3.model.SetObjectTaggingRequest;
import com.amazonaws.services.s3.model.Tag;
import com.justinwells.xlsUploader.model.XlsSpec;
import com.justinwells.xlsUploader.service.SpreadsheetParser;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.officeDocument.x2006.customProperties.CTProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

@RestController
public class UploadController {
    private final AmazonS3 s3Client;
    private final SpreadsheetParser spreadsheetParser;
    private final XlsSpec xlsSpec;
    private final String bucketName;

    @Autowired
    public UploadController(AmazonS3 s3Client, SpreadsheetParser spreadsheetParser, XlsSpec xlsSpec,
                            @Value("${spring.cloud.aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.spreadsheetParser = spreadsheetParser;
        this.xlsSpec = xlsSpec;
        this.bucketName = bucketName;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "specName", required = false) String specNameParam) throws IOException {
        // Validate file extension
        if (!file.getOriginalFilename().toLowerCase().endsWith(".xlsx")) {
            return rejectFile(file, null, "Invalid file type: must be .xlsx");
        }

        // Generate unique S3 key
        String key = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // Upload to S3
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        s3Client.putObject(bucketName, key, file.getInputStream(), metadata);

        // Create temp file and extract spec name
        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        file.transferTo(tempFile);
        String specName;
        try (XSSFWorkbook workbook = new XSSFWorkbook(tempFile)) {
            CTProperty specProperty = workbook.getProperties().getCustomProperties().getProperty("Spec");
            specName = (specProperty != null && specProperty.getLpwstr() != null) ? specProperty.getLpwstr() : specNameParam;
        } catch (InvalidFormatException e) {
            setTags(key, "Rejected", specNameParam);
            tempFile.delete();
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Excel format: " + e.getMessage()));
        }

        // Validate spec
        if (specName == null || specName.trim().isEmpty() || xlsSpec.getSpec(specName) == null) {
            setTags(key, "Rejected", specName != null ? specName : "unknown");
            tempFile.delete();
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Unknown or missing spec: " + (specName != null ? specName : "none provided")));
        }

        setTags(key, "New", specName);

        // Process the file
        try {
            spreadsheetParser.parseAndQueue(tempFile, specName, key);
            setTags(key, "Processed", specName);
        } catch (IOException e) {
            setTags(key, "Quarantine", specName);
            return ResponseEntity.badRequest().body(Map.of("error", "I/O error during processing: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            setTags(key, "Quarantine", specName);
            return ResponseEntity.badRequest().body(Map.of("error", "Data outside spec range: " + e.getMessage()));
        } catch (Exception e) {
            setTags(key, "Quarantine", specName);
            return ResponseEntity.status(500).body(Map.of("error", "Processing failed: " + e.getMessage()));
        } finally {
            tempFile.delete();
        }

        return ResponseEntity.ok(Map.of("message", "File uploaded to S3 and queued: " + key));
    }

    private ResponseEntity<Map<String, String>> rejectFile(MultipartFile file, String specName, String reason)
            throws IOException {
        String key = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        s3Client.putObject(bucketName, key, file.getInputStream(), metadata);
        setTags(key, "Rejected", specName != null ? specName : "unknown");
        return ResponseEntity.badRequest().body(Map.of("error", reason));
    }

    private void setTags(String key, String state, String spec) {
        ObjectTagging tagging = new ObjectTagging(
                Arrays.asList(
                        new Tag("fileProcessingState", state),
                        new Tag("spec", spec)
                )
        );
        SetObjectTaggingRequest taggingRequest = new SetObjectTaggingRequest(bucketName, key, tagging);
        s3Client.setObjectTagging(taggingRequest);
    }
}