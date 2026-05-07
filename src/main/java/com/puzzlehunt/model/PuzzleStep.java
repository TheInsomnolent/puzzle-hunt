package com.puzzlehunt.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single step within a {@link PuzzleHunt}.
 *
 * <p>All completion-criteria fields are optional; the relevant subset is
 * keyed off {@link #type} (and {@link #itemSource} / {@link #locationSubtype}
 * for the relevant types). Storing them flat keeps the JSON shape stable
 * across additions of new clue types.
 */
@Data
@NoArgsConstructor
public class PuzzleStep
{
	/** Stable identifier so progress can be tracked across edits to the hunt. */
	private String id = UUID.randomUUID().toString();

	/** Short human readable title shown in the sidebar. */
	private String title = "";

	/**
	 * Zero-based chapter index. Only consulted in TREASURE_TRAIL hunts: chapters
	 * must be completed in order, while ordering within a chapter is governed by
	 * the per-chapter mode in {@link PuzzleHunt#getChapterModes()}.
	 */
	private int chapter = 0;

	/**
	 * Optional free-text clue. When non-blank this is shown to the player
	 * instead of the underlying completion criteria, so creators can write
	 * riddles or text puzzles.
	 */
	private String clueText = "";

	private ClueType type = ClueType.GET_ITEM;

	// --- GET_ITEM fields -----------------------------------------------
	private int itemId;
	private String itemName = "";
	/** Additional acceptable item ids — a gain of any of these counts. */
	private List<Integer> additionalItemIds = new ArrayList<>();
	private List<String> additionalItemNames = new ArrayList<>();
	/** When > 0, the player must accumulate this many of the configured items before the step completes. */
	private int requiredCount = 0;
	private ItemSource itemSource = ItemSource.ANY;
	/** Monster ids the item must drop from when {@link #itemSource} is {@link ItemSource#MONSTER_DROP}. */
	private List<Integer> monsterIds = new ArrayList<>();
	/** Names of the monsters above; for display only. */
	private List<String> monsterNames = new ArrayList<>();

	// --- LOCATION_PUZZLE fields ----------------------------------------
	private LocationSubtype locationSubtype = LocationSubtype.NPC;
	/** NPC name to add the "Complete clue step" entry to. Case-insensitive match. */
	private String npcName = "";
	/** Painted tiles that complete the step when the player stands on them. */
	private List<SerializedTile> tiles = new ArrayList<>();

	// --- KILL_MONSTER fields -------------------------------------------
	private List<String> killMonsterNames = new ArrayList<>();
	private int killCount = 1;

	// --- GAIN_GP fields ------------------------------------------------
	private int gpAmount = 0;

	// --- GAIN_XP fields ------------------------------------------------
	/** Skill names ({@link net.runelite.api.Skill#name()}); empty list means any skill. */
	private List<String> xpSkills = new ArrayList<>();
	private int xpAmount = 0;

	// --- PASSWORD fields -----------------------------------------------
	private String passwordAnswer = "";

	// --- Optional image (base64-encoded PNG/JPG) -----------------------
	private String imageBase64 = "";
}
