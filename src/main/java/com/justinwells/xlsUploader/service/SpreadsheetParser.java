package com.justinwells.xlsUploader.service;

import com.justinwells.xlsUploader.model.SpreadsheetSpec;
import com.justinwells.xlsUploader.model.SpreadsheetData;
import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class SpreadsheetParser {
    private static final Logger logger = LoggerFactory.getLogger(SpreadsheetParser.class);

    public SpreadsheetData parseSpreadsheet(MultipartFile file, SpreadsheetSpec spec) throws IOException {
        logger.info("Parsing spreadsheet: {}", file.getOriginalFilename());
        Workbook workbook = WorkbookFactory.create(file.getInputStream());
        Sheet sheet = workbook.getSheetAt(0);

        // Check if sheet is empty or has no rows
        if (sheet == null || sheet.getLastRowNum() < 0) {
            logger.info("Spreadsheet is completely empty: {}", file.getOriginalFilename());
            return new SpreadsheetData(spec.getSpecName(), file.getOriginalFilename(), new ArrayList<>());
        }

        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            logger.info("No header row found in spreadsheet: {}", file.getOriginalFilename());
            return new SpreadsheetData(spec.getSpecName(), file.getOriginalFilename(), new ArrayList<>());
        }

        // Parse headers
        List<String> headers = new ArrayList<>();
        Iterator<Cell> headerCells = headerRow.cellIterator();
        while (headerCells.hasNext()) {
            Cell cell = headerCells.next();
            headers.add(cell.getStringCellValue().trim());
        }

        // Parse data rows
        List<Map<String, String>> data = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue; // Skip empty rows
            Map<String, String> rowData = new HashMap<>();
            for (int j = 0; j < headers.size(); j++) {
                Cell cell = row.getCell(j, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                String value = cell.getCellType() == CellType.NUMERIC 
                    ? String.valueOf(cell.getNumericCellValue()) 
                    : cell.getStringCellValue();
                rowData.put(headers.get(j), value.trim());
            }
            data.add(rowData);
        }

        logger.info("Parsed spreadsheet with {} rows", data.size());
        return new SpreadsheetData(spec.getSpecName(), file.getOriginalFilename(), data);
    }
}