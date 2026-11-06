package com.ashenox.starter.subscription;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/admin/subscriptions")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminSubscriptionController {
    private final AdminSubscriptionService service;

    @GetMapping
    public AdminSubscriptionPage list(@RequestParam SubscriptionRequest.Channel channel,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.list(channel, page, size);
    }

    @GetMapping("/export")
    public ResponseEntity<StreamingResponseBody> export(@RequestParam SubscriptionRequest.Channel channel) {
        String date = LocalDate.now(ZoneId.of("America/Buenos_Aires")).toString();
        String filename = "suscriptores-" + channel.name().toLowerCase(java.util.Locale.ROOT) + "-" + date + ".xlsx";
        StreamingResponseBody body = output -> service.exportActive(channel, output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
