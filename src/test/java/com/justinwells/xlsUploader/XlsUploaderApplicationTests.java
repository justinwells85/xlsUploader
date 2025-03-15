package com.justinwells.xlsUploader;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class XlsUploaderApplicationTests {

    @MockBean
    private AmazonS3 amazonS3;

    @MockBean
    private AmazonSQSAsync amazonSQSAsync;

    @Autowired
    private XlsUploaderApplication application;

    @Test
    void contextLoads() {
        assertThat(application).isNotNull();
    }
}