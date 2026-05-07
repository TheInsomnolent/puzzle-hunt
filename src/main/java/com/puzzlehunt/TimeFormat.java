package com.puzzlehunt;

/** Shared formatting helpers for hunt timer / split displays. */
final class TimeFormat
{
	private TimeFormat() {}

	/** Formats a millisecond duration as {@code mm:ss.t} (tenths of a second). */
	static String formatTime(long ms)
	{
		long totalSec = ms / 1000L;
		long mins = totalSec / 60L;
		long secs = totalSec % 60L;
		long tenths = (ms % 1000L) / 100L;
		return String.format("%02d:%02d.%d", mins, secs, tenths);
	}
}
