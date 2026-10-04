package com.ecm.common.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.StatusCode;
import org.slf4j.MDC;

/**
 * Bridges error handling to distributed tracing. opentelemetry-api is an optional dependency,
 * so every call falls back gracefully in a service that does not ship the tracing starter.
 */
public final class TraceSupport {

    private static final String MDC_TRACE_ID = "traceId";

    private TraceSupport() {
    }

    public static String currentTraceId() {
        try {
            SpanContext context = Span.current().getSpanContext();
            if (context.isValid()) {
                return context.getTraceId();
            }
        } catch (LinkageError ignored) {
            // opentelemetry-api not on the classpath
        }
        return MDC.get(MDC_TRACE_ID);
    }

    /** Marks the current span as failed so the exception shows up in Zipkin (error=true). */
    public static void recordError(Throwable error) {
        try {
            Span span = Span.current();
            span.recordException(error);
            span.setStatus(StatusCode.ERROR, error.getMessage());
        } catch (LinkageError ignored) {
            // opentelemetry-api not on the classpath
        }
    }
}
