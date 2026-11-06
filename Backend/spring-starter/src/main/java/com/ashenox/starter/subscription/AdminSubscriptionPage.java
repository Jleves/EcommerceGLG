package com.ashenox.starter.subscription;

import java.util.List;

public record AdminSubscriptionPage(List<AdminSubscriptionRow> content, int page, int size,
                                    long totalElements, long totalPages) {}
