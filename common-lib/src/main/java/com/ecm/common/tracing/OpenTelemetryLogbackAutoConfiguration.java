package com.ecm.common.tracing;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Connects the Logback {@code OTEL} appender (declared in logback-spring.xml) to the
 * OpenTelemetry SDK built by Spring Boot, so application logs are exported over OTLP together
 * with traces and metrics. Records logged before this runs are buffered by the appender.
 */
@AutoConfiguration(afterName = "org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration")
@ConditionalOnClass({OpenTelemetry.class, OpenTelemetryAppender.class})
public class OpenTelemetryLogbackAutoConfiguration {

    @Bean
    InitializingBean openTelemetryLogbackAppenderInstaller(ObjectProvider<OpenTelemetry> openTelemetry) {
        return () -> openTelemetry.ifAvailable(OpenTelemetryAppender::install);
    }
}
