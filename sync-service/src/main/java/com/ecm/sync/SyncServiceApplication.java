package com.ecm.sync;

import com.ecm.sync.config.FeignConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(defaultConfiguration = FeignConfig.class)
public class SyncServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncServiceApplication.class, args);
    }

}
