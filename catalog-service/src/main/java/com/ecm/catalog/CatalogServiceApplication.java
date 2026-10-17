package com.ecm.catalog;

import com.ecm.catalog.bootstrap.CatalogSeed;
import com.ecm.catalog.config.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
@EnableScheduling
public class CatalogServiceApplication {

    private static final String SEED_PROD_ARGUMENT = "--seed-prod";

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && SEED_PROD_ARGUMENT.equals(args[0])) {
            CatalogSeed.run();
            return;
        }
        SpringApplication.run(CatalogServiceApplication.class, args);
    }

}
