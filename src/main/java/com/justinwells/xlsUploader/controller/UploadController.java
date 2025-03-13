package com.justinwells.xlsUploader.controller;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class UploadController {
    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final SpreadsheetSpec salesSpec;

    @Autowired
    public UploadController(SpreadsheetSpec salesSpec) {
        this.salesSpec = salesSpec;
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

        // Log the spec mappings for now
        logger.info("Using spec: {} with mappings: {}", salesSpec.getSpecName(), salesSpec.getColumnMappings());

        return ResponseEntity.ok("{\"message\": \"File uploaded successfully: " + filename + "\"}");
    }
}