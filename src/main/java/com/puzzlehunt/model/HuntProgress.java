package com.puzzlehunt.model;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persisted progress for a single {@link PuzzleHunt}.
 *
 * <p>Tracks the elapsed time accumulated across play sessions and the
 * completion timestamp (relative to the start of the hunt, in milliseconds)
 * for each completed step. Storing per-step splits up front lets the end-of-
 * hunt summary screen show split times without any extra bookkeeping.
 */
@Data
@NoArgsConstructor
public class HuntProgress
{
	private String huntId = "";

	/** Total elapsed time across every play session, in milliseconds. */
	private long elapsedMillis;

	/** Wall-clock time the current session started, or 0 if the timer is paused. */
	private long sessionStartMillis;

	/** Step id → split time (ms since hunt start). Insertion-ordered. */
	private Map<String, Long> stepSplits = new LinkedHashMap<>();

	private boolean completed;
}
