package com.ecm.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String AVATAR_EXECUTOR = "avatarExecutor";

    private static final int POOL_SIZE = 2;
    private static final int QUEUE_CAPACITY = 50;

    /** Small and bounded: a burst of signups drops the extra avatars instead of piling up work, since the avatar is optional. */
    @Bean(AVATAR_EXECUTOR)
    public ThreadPoolTaskExecutor avatarExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("google-avatar-");
        executor.setCorePoolSize(POOL_SIZE);
        executor.setMaxPoolSize(POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        return executor;
    }
}
