package com.ashenox.starter.subscription;

import com.ashenox.starter.shared.error.BusinessException;
import lombok.RequiredArgsConstructor;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {
    private static final int MAX_ROWS_PER_SHEET = 1_048_576;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("America/Buenos_Aires"));
    private final SubscriptionRepository repository;

    @Transactional(readOnly = true)
    public AdminSubscriptionPage list(SubscriptionRequest.Channel channel, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new BusinessException("page debe ser mayor o igual a 0 y size debe estar entre 1 y 100.");
        }
        long total = repository.countByChannel(channel);
        return new AdminSubscriptionPage(repository.findPageByChannel(channel, page, size), page, size,
                total, (total + size - 1) / size);
    }

    @Transactional(readOnly = true)
    public void exportActive(SubscriptionRequest.Channel channel, OutputStream output) throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
            workbook.setCompressTempFiles(true);
            AtomicInteger count = new AtomicInteger();
            Sheet[] current = {newSheet(workbook, 1)};
            repository.streamActiveByChannel(channel, row -> {
                int index = count.getAndIncrement();
                if (index > 0 && index % (MAX_ROWS_PER_SHEET - 1) == 0) {
                    current[0] = newSheet(workbook, index / (MAX_ROWS_PER_SHEET - 1) + 1);
                }
                int sheetRow = index % (MAX_ROWS_PER_SHEET - 1) + 1;
                var excelRow = current[0].createRow(sheetRow);
                excelRow.createCell(0).setCellValue(row.contact());
                excelRow.createCell(1).setCellValue(DATE_FORMAT.format(row.consentedAt()));
            });
            workbook.write(output);
        }
    }

    private Sheet newSheet(SXSSFWorkbook workbook, int number) {
        Sheet sheet = workbook.createSheet("Suscriptores " + number);
        var header = sheet.createRow(0);
        header.createCell(0).setCellValue("Contacto");
        header.createCell(1).setCellValue("Consentimiento (Argentina)");
        return sheet;
    }
}
