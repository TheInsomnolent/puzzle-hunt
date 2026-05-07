package com.puzzlehunt.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A collection of {@link PuzzleStep}s the player attempts to complete in a
 * single play-through.
 */
@Data
@NoArgsConstructor
public class PuzzleHunt
{
	/** Stable identifier so the same hunt can be referenced across imports / saves. */
	private String id = UUID.randomUUID().toString();

	private String name = "Untitled hunt";

	private String author = "";

	private HuntMode mode = HuntMode.TREASURE_TRAIL;

	private List<PuzzleStep> steps = new ArrayList<>();

	// --- Pre-hunt briefing shown on the detail/start screen --------------

	/** Free-text instructions shown to the player before they hit Start. */
	private String startingInstructions = "";

	/** Optional tile the player is expected to be standing on/near to start. */
	private SerializedTile startingTile;

	/** Item ids the player is expected to bring; cross-referenced against inventory. */
	private List<Integer> startingItemIds = new ArrayList<>();
	private List<String> startingItemNames = new ArrayList<>();

	/**
	 * Per-chapter mode for TREASURE_TRAIL hunts. Index = chapter number. Each
	 * entry is either {@link HuntMode#TREASURE_TRAIL} (steps in that chapter must
	 * be completed in order) or {@link HuntMode#DIARY} (any order within the
	 * chapter). Missing/short list entries fall back to the hunt's top-level
	 * {@link #mode}, which preserves behaviour for hunts saved before chapters
	 * existed.
	 */
	private List<HuntMode> chapterModes = new ArrayList<>();

	/** Mode for a given chapter, falling back to the hunt's top-level mode. */
	public HuntMode getChapterMode(int chapterIndex)
	{
		if (chapterModes != null && chapterIndex >= 0 && chapterIndex < chapterModes.size())
		{
			HuntMode m = chapterModes.get(chapterIndex);
			if (m != null)
			{
				return m;
			}
		}
		return mode;
	}
}
