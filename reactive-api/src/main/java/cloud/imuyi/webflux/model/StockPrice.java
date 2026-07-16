package cloud.imuyi.webflux.model;

import java.math.BigDecimal;
import java.time.Instant;

public record StockPrice(
        String code,
        String name,
        BigDecimal price,
        BigDecimal changePercent,
        Instant timestamp
) {}