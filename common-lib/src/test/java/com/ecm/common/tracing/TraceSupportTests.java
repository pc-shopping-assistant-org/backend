package com.ecm.common.tracing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TraceSupportTests {

    private static final String TRACEPARENT = "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01";

    @Test
    void currentTraceparentIsNullWithoutActiveTrace() {
        assertThat(TraceSupport.currentTraceparent()).isNull();
    }

    @Test
    void restoredTraceparentBecomesTheCurrentTraceUntilClosed() {
        try (TraceSupport.TraceScope ignored = TraceSupport.restore(TRACEPARENT)) {
            assertThat(TraceSupport.currentTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(TraceSupport.currentTraceparent()).isEqualTo(TRACEPARENT);
        }
        assertThat(TraceSupport.currentTraceparent()).isNull();
    }

    @Test
    void nullOrMalformedTraceparentIsIgnored() {
        for (String value : new String[]{null, "", "garbage", "00-short-ids-01"}) {
            try (TraceSupport.TraceScope ignored = TraceSupport.restore(value)) {
                assertThat(TraceSupport.currentTraceparent()).isNull();
            }
        }
    }
}
