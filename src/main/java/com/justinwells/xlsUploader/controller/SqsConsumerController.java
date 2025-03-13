package com.justinwells.xlsUploader.controller;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.messaging.Message;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;

import java.util.Optional;

@RestController
public class SqsConsumerController {
    private static final Logger logger = LoggerFactory.getLogger(SqsConsumerController.class);

    private final SqsTemplate sqsTemplate;
    private final SqsClient sqsClient;

    @Autowired
    public SqsConsumerController(SqsTemplate sqsTemplate, SqsClient sqsClient) {
        this.sqsTemplate = sqsTemplate;
        this.sqsClient = sqsClient;
    }

    @GetMapping("/consume")
    public ResponseEntity<String> consumeMessage() {
        try {
            Optional<Message<String>> receiveResult = sqsTemplate.receive("spreadsheet-queue", String.class);
            if (receiveResult.isEmpty()) {
                logger.info("No messages available in spreadsheet-queue");
                return ResponseEntity.ok("{\"message\": \"No messages available\"}");
            }

            Message<String> message = receiveResult.get();
            String messageBody = message.getPayload();
            logger.info("Received message from SQS: {}", messageBody);

            // Debug headers to confirm
            logger.debug("Message headers: {}", message.getHeaders());

            // Use the correct header key: Sqs_ReceiptHandle
            String receiptHandle = message.getHeaders().get("Sqs_ReceiptHandle", String.class);
            if (receiptHandle != null) {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl("http://localhost:4566/000000000000/spreadsheet-queue")
                    .receiptHandle(receiptHandle)
                    .build());
                logger.info("Deleted message with receipt handle: {}", receiptHandle);
            } else {
                logger.warn("No Sqs_ReceiptHandle found in message headers; message not deleted");
            }

            return ResponseEntity.ok("{\"message\": \"Message consumed\", \"data\": " + messageBody + "}");
        } catch (Exception e) {
            logger.error("Failed to consume message: {}", e.getMessage());
            return ResponseEntity.status(500).body("{\"message\": \"Error consuming message\"}");
        }
    }
}