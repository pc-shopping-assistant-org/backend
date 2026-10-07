package com.ecm.catalog.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "catalog")
public class CatalogProperties {

    /**
     * An active variant with 1 up to this many units left counts as "running low" on the dashboard; a first
     * estimate, to be tuned with real sales figures.
     */
    private int lowStockThreshold = 5;
}
