package com.puzzlehunt;

import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Shared helpers for sidebar styling. Mirrors the look-and-feel used by
 * Quest Helper / RuneLite-bundled plugins (dark backgrounds, no L&amp;F
 * decoration painting, hover highlight) instead of relying on the
 * platform default which renders a light "system" button.
 */
final class PanelComponents
{
	private PanelComponents() {}

	/** Apply RuneLite-style dark theming to a button. */
	static <T extends AbstractButton> T styleButton(T button)
	{
		button.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		button.setForeground(Color.WHITE);
		button.setFocusPainted(false);
		button.setBorderPainted(false);
		button.setOpaque(true);
		button.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		button.addMouseListener(new MouseAdapter()
		{
			private final Color base = button.getBackground();
			private final Color hover = ColorScheme.DARKER_GRAY_HOVER_COLOR;

			@Override
			public void mouseEntered(MouseEvent e)
			{
				if (button.isEnabled())
				{
					button.setBackground(hover);
				}
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				button.setBackground(base);
			}
		});
		return button;
	}

	/** Convenience for {@code styleButton(new JButton(text))}. */
	static JButton button(String text)
	{
		return styleButton(new JButton(text));
	}

	/**
	 * A panel whose preferred width tracks its parent (typically a
	 * {@link javax.swing.JScrollPane} viewport), so children that report
	 * unbounded preferred widths still wrap inside the sidebar instead of
	 * triggering a horizontal scrollbar.
	 */
	static class FixedWidthPanel extends JPanel
	{
		@Override
		public Dimension getPreferredSize()
		{
			Container parent = getParent();
			int width = parent != null ? parent.getWidth() : PluginPanel.PANEL_WIDTH;
			if (width <= 0)
			{
				width = PluginPanel.PANEL_WIDTH;
			}
			return new Dimension(width, super.getPreferredSize().height);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return getPreferredSize();
		}
	}
}
