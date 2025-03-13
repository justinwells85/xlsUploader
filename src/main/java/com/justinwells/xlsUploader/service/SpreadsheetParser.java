
package com.justinwells.xlsUploader.service;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import com.justinwells.xlsUploader.model.SpreadsheetData;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

@Service
public class SpreadsheetParser {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetParser.class);

    public SpreadsheetData parseSpreadsheet(MultipartFile file, SpreadsheetSpec spec) throws IOException {
        SpreadsheetData result = new SpreadsheetData(spec.getSpecName(), file.getOriginalFilename());

        // Open the file as a workbook
        try (InputStream is = file.getInputStream();
            Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0); // First sheet
            Row headerRow = sheet.getRow(0); // Assume headers in first row
            Map<Integer, String> columnIndexToField = new HashMap<>();

            // Map column indices to JSON field names based on spec
            for (Cell cell : headerRow) {
                String header = cell.getStringCellValue().trim();
                String jsonField = spec.getColumnMappings().get(header);
                if (jsonField != null) {
                    columnIndexToField.put(cell.getColumnIndex(), jsonField);
                }
            }

            // Parse data rows
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Skip header
                Map<String, String> rowData = new HashMap<>();
                for (Cell cell : row) {
                    String jsonField = columnIndexToField.get(cell.getColumnIndex());
                    if (jsonField != null) {
                        String value = getCellValue(cell);
                        rowData.put(jsonField, value);
                    }
                }
                if (!rowData.isEmpty()) {
                    result.addRow(rowData);
                }
            }
        }

        logger.info("Parsed spreadsheet with {} rows", result.getData().size());
        return result;
    }

    private String getCellValue(Cell cell) {
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            default:
                return "";
        }
    }
}