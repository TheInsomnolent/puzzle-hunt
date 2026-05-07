package com.puzzlehunt;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Big centred 3 / 2 / 1 / Go countdown overlay. The text is set externally
 * via {@link #setText(String)} and the overlay clears itself after a short
 * window via {@link #clear()}.
 */
@Singleton
class CountdownOverlay extends Overlay
{
	private static final Font BIG_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 96);
	private static final Color SHADOW = new Color(0, 0, 0, 200);

	private final Client client;
	private volatile String text = "";
	private volatile Color color = Color.WHITE;

	@Inject
	CountdownOverlay(Client client)
	{
		this.client = client;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPriority(PRIORITY_HIGHEST);
	}

	void setText(String text, Color color)
	{
		this.text = text == null ? "" : text;
		this.color = color == null ? Color.WHITE : color;
	}

	void clear()
	{
		this.text = "";
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String t = text;
		if (t.isEmpty())
		{
			return null;
		}
		int cx = client.getCanvasWidth() / 2;
		int cy = client.getCanvasHeight() / 2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(BIG_FONT);
		Rectangle2D bounds = g.getFontMetrics().getStringBounds(t, g);
		int x = cx - (int) (bounds.getWidth() / 2);
		int y = cy + (int) (bounds.getHeight() / 4);
		g.setColor(SHADOW);
		g.drawString(t, x + 4, y + 4);
		g.setColor(color);
		g.drawString(t, x, y);
		return null;
	}
}
