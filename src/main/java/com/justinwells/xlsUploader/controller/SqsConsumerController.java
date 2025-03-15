package com.justinwells.xlsUploader.controller;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SqsConsumerController {

    private final SqsTemplate sqsTemplate;

    @Autowired
    public SqsConsumerController(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    @PostMapping("/send")
    public String sendMessage(@RequestBody String message) {
        sqsTemplate.send("participant-queue", message); // Example queue, adjust as needed
        return "Message sent to SQS";
    }
}