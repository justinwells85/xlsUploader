package com.justinwells.xlsUploader.service;

import io.awspring.cloud.sqs.annotation.SqsListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SpreadsheetProcessor {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetProcessor.class);

    @SqsListener("participant-queue")
    public void processParticipant(String message) {
        logger.info("Received participant message: {}", message);
    }

    @SqsListener("payment-queue")
    public void processPayment(String message) {
        logger.info("Received payment message: {}", message);
    }

    @SqsListener("properties-queue")
    public void processProperties(String message) {
        logger.info("Received properties message: {}", message);
    }

    @SqsListener("assignments-queue")
    public void processAssignments(String message) {
        logger.info("Received assignments message: {}", message);
    }

    @SqsListener("events-queue")
    public void processEvents(String message) {
        logger.info("Received events message: {}", message);
    }
}