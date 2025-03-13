package com.justinwells.xlsUploader.config;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpecConfig {

    @Bean
    public SpreadsheetSpec salesSpec() {
        SpreadsheetSpec spec = new SpreadsheetSpec("sales");
        spec.addMapping("Product ID", "productId");
        spec.addMapping("Quantity", "quantity");
        spec.addMapping("Price", "price");
        return spec;
    }
}