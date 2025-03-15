package com.justinwells.xlsUploader;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@EnableRetry
@SpringBootApplication
public class XlsUploaderApplication {
    public static void main(String[] args) {
        SpringApplication.run(XlsUploaderApplication.class, args);
    }
}