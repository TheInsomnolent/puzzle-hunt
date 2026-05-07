package com.puzzlehunt;

import com.puzzlehunt.model.PuzzleStep;
import com.puzzlehunt.model.SerializedTile;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Scene;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Renders painted tiles for the {@link PuzzleStep} currently being edited
 * (paint mode) or the active LOCATION_PUZZLE/TILES step in diary mode when
 * the player has chosen to reveal it.
 *
 * <p>To stay within the "no per-frame scene scans" guideline, the overlay
 * only iterates the configured tile list (typically &lt; 50 tiles) and
 * relies on RuneLite's culling at render time.
 */
@Singleton
public class TileSelectionOverlay extends Overlay
{
	private static final Color FILL = new Color(0, 200, 255, 50);
	private static final Color BORDER = new Color(0, 200, 255, 200);
	private static final Stroke BORDER_STROKE = new BasicStroke(2f);

	private final Client client;

	/** Step being edited (paint mode); null disables rendering. */
	@Setter
	private volatile PuzzleStep paintStep;

	@Inject
	TileSelectionOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.MED);
	}

	@Override
	public java.awt.Dimension render(Graphics2D g)
	{
		PuzzleStep step = paintStep;
		if (step == null || step.getTiles() == null || step.getTiles().isEmpty())
		{
			return null;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return null;
		}
		WorldPoint here = local.getWorldLocation();
		Scene scene = client.getScene();
		if (here == null || scene == null)
		{
			return null;
		}
		int plane = client.getPlane();
		g.setStroke(BORDER_STROKE);
		for (SerializedTile t : step.getTiles())
		{
			if (t.getPlane() != plane)
			{
				continue;
			}
			WorldPoint wp = new WorldPoint(t.getX(), t.getY(), t.getPlane());
			if (wp.distanceTo(here) > Perspective.SCENE_SIZE)
			{
				continue;
			}
			LocalPoint lp = LocalPoint.fromWorld(client, wp);
			if (lp == null)
			{
				continue;
			}
			Polygon poly = Perspective.getCanvasTilePoly(client, lp);
			if (poly == null)
			{
				continue;
			}
			g.setColor(FILL);
			g.fillPolygon(poly);
			g.setColor(BORDER);
			g.drawPolygon(poly);
		}
		return null;
	}

	/** Toggles a world tile in/out of the painted set. */
	public static synchronized void toggleTile(PuzzleStep step, WorldPoint wp)
	{
		if (step == null || wp == null)
		{
			return;
		}
		List<SerializedTile> tiles = step.getTiles();
		if (tiles == null)
		{
			tiles = new ArrayList<>();
			step.setTiles(tiles);
		}
		Set<Long> existing = new HashSet<>();
		for (SerializedTile t : tiles)
		{
			existing.add(key(t.getX(), t.getY(), t.getPlane()));
		}
		long k = key(wp.getX(), wp.getY(), wp.getPlane());
		if (existing.contains(k))
		{
			tiles.removeIf(t -> key(t.getX(), t.getY(), t.getPlane()) == k);
		}
		else
		{
			tiles.add(new SerializedTile(wp.getX(), wp.getY(), wp.getPlane()));
		}
	}

	private static long key(int x, int y, int plane)
	{
		return (((long) plane) << 60) | (((long) x) << 30) | (long) y;
	}
}
