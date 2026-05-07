package com.puzzlehunt;

import com.puzzlehunt.model.ClueType;
import com.puzzlehunt.model.ItemSource;
import com.puzzlehunt.model.LocationSubtype;
import com.puzzlehunt.model.PuzzleStep;
import com.puzzlehunt.model.SerializedTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;

/**
 * Wires game events to the active hunt and the tile-walk capture flow.
 *
 * <p>Adds RuneLite-only menu entries — these never go to the server, so they
 * comply with the "no menu entries that send actions" hub guideline.
 */
@Slf4j
@Singleton
public class CompletionDetector
{
	private static final String TAKE_OPTION = "Take";

	private final Client client;
	private final ClientThread clientThread;
	private final ActiveHuntService active;

	/** Per-item baseline counts in the inventory, used to detect gains. */
	private final Map<Integer, Integer> lastInventoryCounts = new HashMap<>();

	/** Per-step running totals for accumulator-style steps (item count, kc, gp, xp). */
	private final Map<String, Integer> stepCounters = new HashMap<>();

	/** Per-step baseline XP totals captured when the step first becomes active. */
	private final Map<String, Map<Skill, Integer>> stepXpBaselines = new HashMap<>();

	/** Tracks whether the active hunt's timer was running on the last XP event. */
	private boolean wasTimerRunning;

	/** Set when a skilling XP drop arrives; cleared on the next game tick. Used
	 * to disambiguate skilling-sourced inventory gains from bank/shop withdrawals. */
	private volatile boolean skillingThisTick;

	/**
	 * Step currently in walk-loop sampling mode, or null. Player tiles
	 * are appended to {@link #sampleBuffer} every game tick while set;
	 * the overlay shows them at low opacity for live feedback.
	 */
	private volatile PuzzleStep sampleStep;
	private final List<SerializedTile> sampleBuffer = new ArrayList<>();
	private volatile Runnable onSampleChanged;

	@Inject
	CompletionDetector(Client client, ClientThread clientThread, ActiveHuntService active)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.active = active;
	}

	// ------------------------------------------------------------------
	// Walk-loop sampling
	// ------------------------------------------------------------------

	/** Begin sampling player tiles for {@code step}. */
	public synchronized void startSampling(PuzzleStep step, Runnable onChanged)
	{
		this.sampleStep = step;
		this.onSampleChanged = onChanged;
		this.sampleBuffer.clear();
	}

	/**
	 * Stop sampling and write the convex-hull-filled tile set onto the
	 * step. Returns null if no sampling was in progress.
	 */
	public synchronized PuzzleStep finishSampling()
	{
		PuzzleStep step = this.sampleStep;
		if (step == null)
		{
			return null;
		}
		List<SerializedTile> filled = TileHull.hullFill(new ArrayList<>(sampleBuffer));
		step.setTiles(filled);
		this.sampleStep = null;
		this.sampleBuffer.clear();
		Runnable cb = this.onSampleChanged;
		this.onSampleChanged = null;
		if (cb != null)
		{
			cb.run();
		}
		return step;
	}

	/** Cancel sampling without committing tiles to the step. */
	public synchronized void cancelSampling()
	{
		this.sampleStep = null;
		this.sampleBuffer.clear();
		this.onSampleChanged = null;
	}

	public PuzzleStep getSampleStep()
	{
		return sampleStep;
	}

	/** Live snapshot of buffered samples, for the overlay to render. */
	synchronized List<SerializedTile> getSampleBuffer()
	{
		return new ArrayList<>(sampleBuffer);
	}

	// ------------------------------------------------------------------
	// Item-pickup ("Take") detection for GROUND_SPAWN
	// ------------------------------------------------------------------

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!TAKE_OPTION.equals(event.getMenuOption()))
		{
			return;
		}
		// For GROUND_ITEM_FIRST_OPTION, the ground item id lives in getIdentifier(), not getItemId().
		int itemId = event.getMenuEntry().getIdentifier();
		if (itemId <= 0)
		{
			return;
		}
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() == ClueType.GET_ITEM
				&& s.getItemSource() == ItemSource.GROUND_SPAWN
				&& itemMatches(s, itemId))
			{
				accumulateItem(s, 1);
				return;
			}
		}
	}

	// ------------------------------------------------------------------
	// Inventory gain detection for GET_ITEM / ANY
	// ------------------------------------------------------------------

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		ItemContainer inv = event.getItemContainer();
		Map<Integer, Integer> current = countItems(inv);

		List<PuzzleStep> watching = active.getActiveStepsForDetection();
		boolean bankOrShopOpen = isBankOpen() || isShopOpen();
		boolean skilledThisTick = skillingThisTick;

		// GP gain detection (coin stack delta).
		int coinPrev = lastInventoryCounts.getOrDefault(ItemID.COINS, 0);
		int coinNow = current.getOrDefault(ItemID.COINS, 0);
		int coinDelta = coinNow - coinPrev;

		for (PuzzleStep s : watching)
		{
			if (s.getType() == ClueType.GAIN_GP)
			{
				if (coinDelta > 0 && s.getGpAmount() > 0)
				{
					int accum = stepCounters.getOrDefault(s.getId(), 0) + coinDelta;
					stepCounters.put(s.getId(), accum);
					if (accum >= s.getGpAmount())
					{
						active.completeStep(s.getId());
					}
				}
				continue;
			}
			if (s.getType() != ClueType.GET_ITEM)
			{
				continue;
			}
			ItemSource src = s.getItemSource();
			if (src != ItemSource.ANY && src != ItemSource.SKILLING_RESOURCE)
			{
				// MONSTER_DROP / GROUND_SPAWN are handled elsewhere.
				continue;
			}
			int gained = totalGain(s, current);
			if (gained <= 0)
			{
				continue;
			}
			if (src == ItemSource.SKILLING_RESOURCE)
			{
				if (!skilledThisTick || bankOrShopOpen)
				{
					continue;
				}
			}
			accumulateItem(s, gained);
		}
		lastInventoryCounts.clear();
		lastInventoryCounts.putAll(current);
	}

	/** Returns the total gain (across all valid item ids) since the last snapshot. */
	private int totalGain(PuzzleStep s, Map<Integer, Integer> current)
	{
		int gained = 0;
		for (int id : validItemIds(s))
		{
			int now = current.getOrDefault(id, 0);
			int prev = lastInventoryCounts.getOrDefault(id, 0);
			if (now > prev)
			{
				gained += (now - prev);
			}
		}
		return gained;
	}

	private static java.util.Set<Integer> validItemIds(PuzzleStep s)
	{
		java.util.Set<Integer> ids = new HashSet<>();
		if (s.getItemId() > 0)
		{
			ids.add(s.getItemId());
		}
		if (s.getAdditionalItemIds() != null)
		{
			for (Integer id : s.getAdditionalItemIds())
			{
				if (id != null && id > 0) ids.add(id);
			}
		}
		return ids;
	}

	private static boolean itemMatches(PuzzleStep s, int itemId)
	{
		return validItemIds(s).contains(itemId);
	}

	private void accumulateItem(PuzzleStep s, int gained)
	{
		int required = Math.max(1, s.getRequiredCount());
		int accum = stepCounters.getOrDefault(s.getId(), 0) + gained;
		stepCounters.put(s.getId(), accum);
		if (accum >= required)
		{
			active.completeStep(s.getId());
		}
	}

	private Map<Integer, Integer> countItems(ItemContainer inv)
	{
		Map<Integer, Integer> out = new HashMap<>();
		if (inv == null)
		{
			return out;
		}
		for (Item item : inv.getItems())
		{
			if (item == null || item.getId() <= 0)
			{
				continue;
			}
			out.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
		}
		return out;
	}

	// ------------------------------------------------------------------
	// NPC drop detection for GET_ITEM / MONSTER_DROP
	// ------------------------------------------------------------------

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null)
		{
			return;
		}
		String npcName = npc.getName() == null ? "" : npc.getName();
		Set<Integer> droppedIds = new HashSet<>();
		event.getItems().forEach(stack -> droppedIds.add(stack.getId()));

		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			// MONSTER_DROP — must drop one of the configured items from a matching monster.
			if (s.getType() == ClueType.GET_ITEM && s.getItemSource() == ItemSource.MONSTER_DROP)
			{
				if (!nameMatchesAny(npcName, s.getMonsterNames())) continue;
				int matched = 0;
				for (Integer id : validItemIds(s))
				{
					if (droppedIds.contains(id)) matched++;
				}
				if (matched > 0)
				{
					accumulateItem(s, matched);
				}
			}
			// KILL_MONSTER — just count the kill.
			else if (s.getType() == ClueType.KILL_MONSTER)
			{
				if (!nameMatchesAny(npcName, s.getKillMonsterNames())) continue;
				int required = Math.max(1, s.getKillCount());
				int accum = stepCounters.getOrDefault(s.getId(), 0) + 1;
				stepCounters.put(s.getId(), accum);
				if (accum >= required)
				{
					active.completeStep(s.getId());
				}
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() == ClueType.DIE)
			{
				active.completeStep(s.getId());
			}
		}
	}

	/** Player input from the active panel for {@link ClueType#PASSWORD} steps. */
	public boolean submitPassword(String stepId, String text)
	{
		if (stepId == null || text == null) return false;
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (!stepId.equals(s.getId())) continue;
			if (s.getType() != ClueType.PASSWORD) return false;
			String want = s.getPasswordAnswer() == null ? "" : s.getPasswordAnswer().trim();
			if (!want.isEmpty() && want.equalsIgnoreCase(text.trim()))
			{
				active.completeStep(s.getId());
				return true;
			}
			return false;
		}
		return false;
	}

	private static boolean nameMatchesAny(String npcName, List<String> configured)
	{
		if (configured == null || configured.isEmpty())
		{
			// Empty list = any monster counts.
			return true;
		}
		String lower = npcName.toLowerCase(Locale.ROOT);
		for (String n : configured)
		{
			if (n == null || n.trim().isEmpty()) continue;
			if (lower.equals(n.trim().toLowerCase(Locale.ROOT)))
			{
				return true;
			}
		}
		return false;
	}

	// ------------------------------------------------------------------
	// Per-tick handling: tile completion + walk-loop sampling
	// ------------------------------------------------------------------

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		WorldPoint pos = local.getWorldLocation();
		if (pos == null)
		{
			return;
		}

		// Walk-loop sampling: append the current tile if it's new.
		PuzzleStep capturing = sampleStep;
		if (capturing != null)
		{
			Runnable notify = null;
			synchronized (this)
			{
				boolean already = false;
				for (SerializedTile t : sampleBuffer)
				{
					if (t.getX() == pos.getX() && t.getY() == pos.getY() && t.getPlane() == pos.getPlane())
					{
						already = true;
						break;
					}
				}
				if (!already)
				{
					sampleBuffer.add(new SerializedTile(pos.getX(), pos.getY(), pos.getPlane()));
					notify = onSampleChanged;
				}
			}
			if (notify != null)
			{
				notify.run();
			}
		}

		// Tile completion for active LOCATION_PUZZLE / TILES steps,
		// plus NPC adjacency for LOCATION_PUZZLE / NPC steps.
		WorldArea playerArea = local.getWorldArea();
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() != ClueType.LOCATION_PUZZLE)
			{
				continue;
			}
			if (s.getLocationSubtype() == LocationSubtype.TILES)
			{
				List<SerializedTile> tiles = s.getTiles();
				if (tiles == null || tiles.isEmpty())
				{
					continue;
				}
				for (SerializedTile t : tiles)
				{
					if (t.getX() == pos.getX() && t.getY() == pos.getY() && t.getPlane() == pos.getPlane())
					{
						active.completeStep(s.getId());
						break;
					}
				}
			}
			else if (s.getLocationSubtype() == LocationSubtype.NPC && playerArea != null)
			{
				String want = s.getNpcName();
				if (want == null || want.trim().isEmpty())
				{
					continue;
				}
				for (NPC npc : client.getTopLevelWorldView().npcs())
				{
					if (npc == null || npc.getName() == null) continue;
					if (!want.trim().equalsIgnoreCase(npc.getName())) continue;
					WorldArea na = npc.getWorldArea();
					if (na == null) continue;
					if (na.getPlane() != playerArea.getPlane()) continue;
					if (playerArea.distanceTo(na) <= 1)
					{
						active.completeStep(s.getId());
						break;
					}
				}
			}
		}

		// Clear the per-tick skilling flag last so ItemContainerChanged
		// events that fire during this tick (server tick → inventory)
		// can still see it.
		skillingThisTick = false;

		// Poll XP for active GAIN_XP steps. We sample every tick rather than
		// reacting to StatChanged because StatChanged is not always fired for
		// every XP gain (notably small/incremental gains can be coalesced).
		pollXpProgress();
	}

	private void pollXpProgress()
	{
		// XP credit is only awarded while the hunt timer is actively running.
		// Any pause/resume transition clears existing baselines so the next
		// poll after resume re-anchors instead of crediting the gap.
		boolean nowRunning = active.isTimerRunning();
		if (nowRunning != wasTimerRunning)
		{
			stepXpBaselines.clear();
			wasTimerRunning = nowRunning;
		}
		if (!nowRunning)
		{
			return;
		}

		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() != ClueType.GAIN_XP) continue;
			List<String> wantSkills = s.getXpSkills();
			boolean anySkill = wantSkills == null || wantSkills.isEmpty();
			Map<Skill, Integer> baseline = stepXpBaselines
				.computeIfAbsent(s.getId(), k -> new HashMap<>());

			int accum = stepCounters.getOrDefault(s.getId(), 0);
			boolean changed = false;
			for (Skill skill : Skill.values())
			{
				@SuppressWarnings("deprecation")
				boolean isOverall = skill == Skill.OVERALL;
				if (isOverall) continue;
				if (!anySkill)
				{
					boolean match = false;
					for (String name : wantSkills)
					{
						if (name != null && name.equalsIgnoreCase(skill.name())) { match = true; break; }
					}
					if (!match) continue;
				}
				int nowXp = client.getSkillExperience(skill);
				Integer prev = baseline.get(skill);
				if (prev == null)
				{
					baseline.put(skill, nowXp);
					continue;
				}
				int delta = nowXp - prev;
				if (delta <= 0) continue;
				baseline.put(skill, nowXp);
				accum += delta;
				changed = true;
			}
			if (changed)
			{
				stepCounters.put(s.getId(), accum);
				if (s.getXpAmount() > 0 && accum >= s.getXpAmount())
				{
					active.completeStep(s.getId());
				}
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == null)
		{
			return;
		}
		if (skill != Skill.HITPOINTS)
		{
			// Used to disambiguate skilling-sourced inventory gains from bank/shop withdrawals.
			skillingThisTick = true;
		}
	}

	private boolean isBankOpen()
	{
		Widget w = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return w != null && !w.isHidden();
	}

	private boolean isShopOpen()
	{
		Widget w = client.getWidget(InterfaceID.Shopmain.UNIVERSE);
		return w != null && !w.isHidden();
	}

	/** Resets the inventory baseline. Safe to call from any thread. */
	public void resetInventoryBaseline()
	{
		clientThread.invoke(() ->
		{
			lastInventoryCounts.clear();
			ItemContainer inv = client.getItemContainer(InventoryID.INV);
			if (inv != null)
			{
				lastInventoryCounts.putAll(countItems(inv));
			}
		});
	}

	/** Resets all per-step accumulators. Call when starting/resetting a hunt. */
	public void resetForNewHunt()
	{
		stepCounters.clear();
		stepXpBaselines.clear();
		wasTimerRunning = false;
		resetInventoryBaseline();
	}

	/** Current accumulator value for a step (items gained, kc, gp, xp delta). */
	public int getStepProgress(String stepId)
	{
		return stepCounters.getOrDefault(stepId, 0);
	}
}
