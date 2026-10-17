package com.ecm.sync.dto.response;

/**
 * Outcome of one replay of the dead-letter topic: changes processed again, changes that are unreadable and were passed
 * over (they stay on the topic until its retention ends), and changes still waiting. {@code blocked} means a change
 * failed again for a transient reason, so the replay stopped there and can be repeated once the cause is fixed.
 */
public record DltReplayResponse(int replayed, int skipped, long pending, boolean blocked) {
}
