package com.justinwells.xlsUploader.controller;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.justinwells.xlsUploader.service.SpreadsheetParser;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;

@RestController
public class UploadController {
    private final AmazonS3 s3Client;
    private final SpreadsheetParser spreadsheetParser;
    private final String bucketName;

    @Autowired
    public UploadController(AmazonS3 s3Client, SpreadsheetParser spreadsheetParser,
                            @Value("${spring.cloud.aws.s3.bucket}") String bucketName) {
        this.s3Client = s3Client;
        this.spreadsheetParser = spreadsheetParser;
        this.bucketName = bucketName;
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file,
                                                          @RequestParam(defaultValue = "participant") String specName)
            throws IOException, InvalidFormatException {
        String key = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        // Set content length to avoid buffering warning
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(file.getSize());
        s3Client.putObject(bucketName, key, file.getInputStream(), metadata);

        File tempFile = File.createTempFile("upload-", file.getOriginalFilename());
        file.transferTo(tempFile);
        spreadsheetParser.parseAndQueue(tempFile, specName);
        tempFile.delete();

        return ResponseEntity.ok(Map.of("message", "File uploaded to S3 and queued: " + key));
    }
}