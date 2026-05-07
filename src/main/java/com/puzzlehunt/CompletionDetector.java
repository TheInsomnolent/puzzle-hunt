package com.puzzlehunt;

import com.puzzlehunt.model.ClueType;
import com.puzzlehunt.model.ItemSource;
import com.puzzlehunt.model.LocationSubtype;
import com.puzzlehunt.model.PuzzleStep;
import com.puzzlehunt.model.SerializedTile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;

/**
 * Wires game events to the active hunt and the eyedropper.
 *
 * <p>Adds RuneLite-only menu entries — these never go to the server, so they
 * comply with the "no menu entries that send actions" hub guideline.
 */
@Slf4j
@Singleton
public class CompletionDetector
{
	private static final String TAKE_OPTION = "Take";
	private static final String COMPLETE_CLUE_OPTION = "Complete clue step";
	private static final String SELECT_OPTION_PREFIX = "Puzzle hunt: select ";

	private final Client client;
	private final ActiveHuntService active;
	private final EyedropperService eyedropper;

	/** Per-item baseline counts in the inventory, used to detect gains. */
	private final Map<Integer, Integer> lastInventoryCounts = new HashMap<>();

	/** Step currently in tile-paint mode, or null. */
	private volatile PuzzleStep paintStep;
	private volatile Runnable onPaintChanged;

	public synchronized void setPaintStep(PuzzleStep step, Runnable onChanged)
	{
		this.paintStep = step;
		this.onPaintChanged = onChanged;
	}

	public synchronized PuzzleStep getPaintStep()
	{
		return paintStep;
	}

	@Inject
	CompletionDetector(Client client, ActiveHuntService active, EyedropperService eyedropper)
	{
		this.client = client;
		this.active = active;
		this.eyedropper = eyedropper;
	}

	// ------------------------------------------------------------------
	// Menu entry adapter
	// ------------------------------------------------------------------

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		EyedropperService.Mode mode = eyedropper.getMode();

		// Tile-paint mode: attach a "Paint tile" / "Unpaint tile" entry to "Walk here".
		PuzzleStep ps = paintStep;
		if (ps != null && "Walk here".equals(event.getOption()))
		{
			final int sx = event.getMenuEntry().getParam0();
			final int sy = event.getMenuEntry().getParam1();
			final int plane = client.getPlane();
			WorldPoint wp = WorldPoint.fromScene(client, sx, sy, plane);
			if (wp != null)
			{
				boolean already = false;
				if (ps.getTiles() != null)
				{
					for (SerializedTile t : ps.getTiles())
					{
						if (t.getX() == wp.getX() && t.getY() == wp.getY() && t.getPlane() == wp.getPlane())
						{
							already = true;
							break;
						}
					}
				}
				final WorldPoint capturedWp = wp;
				addRuneliteEntry(already ? "Unpaint tile" : "Paint tile", e ->
				{
					TileSelectionOverlay.toggleTile(ps, capturedWp);
					Runnable cb = onPaintChanged;
					if (cb != null)
					{
						cb.run();
					}
				});
			}
		}

		if (mode == EyedropperService.Mode.ITEM_FROM_INVENTORY && isInventoryEntry(event))
		{
			MenuEntry me = event.getMenuEntry();
			int itemId = me.getItemId();
			if (itemId > 0)
			{
				addRuneliteEntry(SELECT_OPTION_PREFIX + "item", e -> eyedropper.provide(new SelectedItem(itemId, safeName(me.getTarget()))));
			}
		}

		if ((mode == EyedropperService.Mode.NPC || mode == EyedropperService.Mode.MONSTER) && isNpcEntry(event))
		{
			NPC npc = event.getMenuEntry().getNpc();
			if (npc != null)
			{
				int id = npc.getId();
				String name = npc.getName() == null ? "" : npc.getName();
				addRuneliteEntry(SELECT_OPTION_PREFIX + (mode == EyedropperService.Mode.MONSTER ? "monster" : "NPC"),
					e -> eyedropper.provide(new SelectedNpc(id, name)));
			}
		}

		// "Complete clue step" entries on matching NPCs while a hunt is active
		if (isNpcEntry(event))
		{
			NPC npc = event.getMenuEntry().getNpc();
			if (npc != null && shouldOfferCompleteClueOn(npc))
			{
				addRuneliteEntry(COMPLETE_CLUE_OPTION, e ->
				{
					List<PuzzleStep> watching = active.getActiveStepsForDetection();
					String npcName = npc.getName() == null ? "" : npc.getName();
					for (PuzzleStep s : watching)
					{
						if (s.getType() == ClueType.LOCATION_PUZZLE
							&& s.getLocationSubtype() == LocationSubtype.NPC
							&& npcName.equalsIgnoreCase(s.getNpcName()))
						{
							active.completeStep(s.getId());
							return;
						}
					}
				});
			}
		}
	}

	private boolean shouldOfferCompleteClueOn(NPC npc)
	{
		if (npc.getName() == null)
		{
			return false;
		}
		String name = npc.getName();
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() == ClueType.LOCATION_PUZZLE
				&& s.getLocationSubtype() == LocationSubtype.NPC
				&& name.equalsIgnoreCase(s.getNpcName()))
			{
				return true;
			}
		}
		return false;
	}

	private boolean isInventoryEntry(MenuEntryAdded event)
	{
		// itemId is set on inventory item menu entries; combined with a non-NPC target this is a safe heuristic.
		return event.getMenuEntry().getItemId() > 0 && event.getMenuEntry().getNpc() == null;
	}

	private boolean isNpcEntry(MenuEntryAdded event)
	{
		return event.getMenuEntry().getNpc() != null;
	}

	private void addRuneliteEntry(String option, java.util.function.Consumer<MenuEntry> onClick)
	{
		client.getMenu().createMenuEntry(-1)
			.setOption(option)
			.setTarget("")
			.setType(MenuAction.RUNELITE)
			.onClick(onClick);
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
				&& s.getItemId() == itemId)
			{
				active.completeStep(s.getId());
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
		if (event.getContainerId() != InventoryID.INVENTORY.getId())
		{
			return;
		}
		ItemContainer inv = event.getItemContainer();
		Map<Integer, Integer> current = countItems(inv);

		List<PuzzleStep> watching = active.getActiveStepsForDetection();
		for (PuzzleStep s : watching)
		{
			if (s.getType() != ClueType.GET_ITEM || s.getItemSource() != ItemSource.ANY)
			{
				continue;
			}
			int id = s.getItemId();
			if (id <= 0)
			{
				continue;
			}
			int now = current.getOrDefault(id, 0);
			int prev = lastInventoryCounts.getOrDefault(id, 0);
			if (now > prev)
			{
				active.completeStep(s.getId());
			}
		}
		lastInventoryCounts.clear();
		lastInventoryCounts.putAll(current);
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
		int npcId = npc.getId();
		Set<Integer> droppedIds = new HashSet<>();
		event.getItems().forEach(stack -> droppedIds.add(stack.getId()));

		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() != ClueType.GET_ITEM || s.getItemSource() != ItemSource.MONSTER_DROP)
			{
				continue;
			}
			if (s.getMonsterIds() != null && !s.getMonsterIds().isEmpty() && !s.getMonsterIds().contains(npcId))
			{
				continue;
			}
			if (droppedIds.contains(s.getItemId()))
			{
				active.completeStep(s.getId());
			}
		}
	}

	// ------------------------------------------------------------------
	// Tile detection for LOCATION_PUZZLE / TILES
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
		for (PuzzleStep s : active.getActiveStepsForDetection())
		{
			if (s.getType() != ClueType.LOCATION_PUZZLE || s.getLocationSubtype() != LocationSubtype.TILES)
			{
				continue;
			}
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
	}

	private static String safeName(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		// Strip RuneLite menu colour tags: <col=ffff00>Bones</col>
		return raw.replaceAll("<[^>]*>", "");
	}

	/** Resets the inventory baseline; called when starting a new hunt. */
	public synchronized void resetInventoryBaseline()
	{
		lastInventoryCounts.clear();
		ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
		if (inv != null)
		{
			lastInventoryCounts.putAll(countItems(inv));
		}
	}

	// Simple value carriers returned to the eyedropper callback.
	public static final class SelectedItem
	{
		public final int id;
		public final String name;
		public SelectedItem(int id, String name) { this.id = id; this.name = name == null ? "" : name; }
	}

	public static final class SelectedNpc
	{
		public final int id;
		public final String name;
		public SelectedNpc(int id, String name) { this.id = id; this.name = name == null ? "" : name; }
	}
}
