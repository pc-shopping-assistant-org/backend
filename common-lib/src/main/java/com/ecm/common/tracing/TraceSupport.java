package com.ecm.common.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
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

    /**
     * Current trace context in W3C {@code traceparent} format, or {@code null} when there is no active
     * trace. Stored with outbox rows so the relay can continue the original request's trace.
     */
    public static String currentTraceparent() {
        try {
            SpanContext context = Span.current().getSpanContext();
            if (context.isValid()) {
                return "00-" + context.getTraceId() + "-" + context.getSpanId() + "-" + context.getTraceFlags().asHex();
            }
        } catch (LinkageError ignored) {
            // opentelemetry-api not on the classpath
        }
        return null;
    }

    /**
     * Makes the given {@code traceparent} the remote parent of everything started until the returned
     * scope is closed (e.g. the producer span of a message published by a scheduled job). A null or
     * malformed value yields a no-op scope.
     */
    public static TraceScope restore(String traceparent) {
        try {
            String[] parts = traceparent == null ? new String[0] : traceparent.split("-");
            if (parts.length == 4) {
                SpanContext remote = SpanContext.createFromRemoteParent(parts[1], parts[2],
                        TraceFlags.fromHex(parts[3], 0), TraceState.getDefault());
                if (remote.isValid()) {
                    // Built from the root context on purpose: Micrometer keeps its own trace context under a
                    // private key of the current OTel context and would otherwise keep using the scheduler's span.
                    Scope scope = Span.wrap(remote).storeInContext(Context.root()).makeCurrent();
                    return scope::close;
                }
            }
        } catch (LinkageError ignored) {
            // opentelemetry-api not on the classpath
        }
        return () -> { };
    }

    /** Scope returned by {@link #restore}; {@link #close()} does not throw. */
    @FunctionalInterface
    public interface TraceScope extends AutoCloseable {
        @Override
        void close();
    }
}
