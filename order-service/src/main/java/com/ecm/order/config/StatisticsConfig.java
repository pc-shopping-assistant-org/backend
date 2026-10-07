package com.ecm.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class StatisticsConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** The zone that decides which day or month a delivery belongs to; the shop's own, not the server's. */
    @Bean
    public ZoneId statisticsZone(@Value("${order.statistics.zone:Asia/Ho_Chi_Minh}") String zone) {
        return ZoneId.of(zone);
    }
}
