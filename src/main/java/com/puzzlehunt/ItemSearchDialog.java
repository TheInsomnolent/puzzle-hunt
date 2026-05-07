package com.puzzlehunt;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.http.api.item.ItemPrice;

/**
 * Modal item picker — type to search, click to select. Uses the same
 * {@link ItemManager#search(String)} mechanism the bank tags plugin uses
 * to populate its in-game search overlay, so users get the same coverage
 * of tradeable items with their real icons.
 */
@Slf4j
final class ItemSearchDialog extends JDialog
{
	private static final int MAX_RESULTS = 60;
	private static final int DEBOUNCE_MS = 150;

	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;
	private final Consumer<SelectedItem> onPicked;

	private final IconTextField searchBar = new IconTextField();
	private final JPanel resultsPanel = new JPanel();

	private ScheduledFuture<?> pendingSearch;

	ItemSearchDialog(
		Window owner,
		ItemManager itemManager,
		ClientThread clientThread,
		ScheduledExecutorService executor,
		Consumer<SelectedItem> onPicked)
	{
		super(owner, "Search items", ModalityType.APPLICATION_MODAL);
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.executor = executor;
		this.onPicked = onPicked;

		setLayout(new BorderLayout(0, 4));
		getContentPane().setBackground(ColorScheme.DARK_GRAY_COLOR);

		searchBar.setIcon(IconTextField.Icon.SEARCH);
		searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		searchBar.setPreferredSize(new Dimension(280, 30));
		searchBar.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override public void insertUpdate(DocumentEvent e) { scheduleSearch(); }
			@Override public void removeUpdate(DocumentEvent e) { scheduleSearch(); }
			@Override public void changedUpdate(DocumentEvent e) { scheduleSearch(); }
		});
		add(searchBar, BorderLayout.NORTH);

		resultsPanel.setLayout(new BoxLayout(resultsPanel, BoxLayout.Y_AXIS));
		resultsPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(resultsPanel);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		scroll.setPreferredSize(new Dimension(300, 360));
		add(scroll, BorderLayout.CENTER);

		JButton cancel = PanelComponents.button("Cancel");
		cancel.addActionListener(e -> dispose());
		JPanel south = new JPanel(new BorderLayout());
		south.setBackground(ColorScheme.DARK_GRAY_COLOR);
		south.add(cancel, BorderLayout.EAST);
		add(south, BorderLayout.SOUTH);

		pack();
		setLocationRelativeTo(owner);
		SwingUtilities.invokeLater(searchBar::requestFocusInWindow);
	}

	private void scheduleSearch()
	{
		if (pendingSearch != null)
		{
			pendingSearch.cancel(false);
		}
		final String query = searchBar.getText() == null ? "" : searchBar.getText().trim();
		pendingSearch = executor.schedule(() -> runSearch(query), DEBOUNCE_MS, TimeUnit.MILLISECONDS);
	}

	private void runSearch(String query)
	{
		if (query.isEmpty())
		{
			SwingUtilities.invokeLater(() ->
			{
				resultsPanel.removeAll();
				resultsPanel.revalidate();
				resultsPanel.repaint();
			});
			return;
		}
		// search() only iterates an in-memory map; safe off the client thread.
		List<ItemPrice> matches;
		try
		{
			matches = itemManager.search(query);
		}
		catch (RuntimeException ex)
		{
			log.debug("Item search failed", ex);
			matches = new ArrayList<>();
		}
		final List<ItemPrice> trimmed = matches.size() > MAX_RESULTS
			? matches.subList(0, MAX_RESULTS)
			: matches;
		SwingUtilities.invokeLater(() -> renderResults(trimmed));
	}

	private void renderResults(List<ItemPrice> matches)
	{
		resultsPanel.removeAll();
		if (matches.isEmpty())
		{
			JLabel empty = new JLabel("No items found");
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			resultsPanel.add(empty);
		}
		else
		{
			for (ItemPrice item : matches)
			{
				resultsPanel.add(buildRow(item));
			}
		}
		resultsPanel.add(Box.createVerticalGlue());
		resultsPanel.revalidate();
		resultsPanel.repaint();
	}

	private JPanel buildRow(ItemPrice item)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel iconLabel = new JLabel();
		iconLabel.setPreferredSize(new Dimension(36, 32));
		// getImage must be primed on the client thread; addTo() then
		// repaints the label asynchronously when the sprite is ready.
		clientThread.invoke(() ->
		{
			AsyncBufferedImage img = itemManager.getImage(item.getId());
			if (img != null)
			{
				img.addTo(iconLabel);
				SwingUtilities.invokeLater(() -> iconLabel.setIcon(new ImageIcon(img)));
			}
		});
		row.add(iconLabel, BorderLayout.WEST);

		JLabel name = new JLabel(item.getName());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);

		JLabel id = new JLabel("#" + item.getId());
		id.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		row.add(id, BorderLayout.EAST);

		row.addMouseListener(new MouseAdapter()
		{
			@Override public void mouseEntered(MouseEvent e) { row.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR); }
			@Override public void mouseExited(MouseEvent e) { row.setBackground(ColorScheme.DARKER_GRAY_COLOR); }
			@Override public void mouseClicked(MouseEvent e)
			{
				onPicked.accept(new SelectedItem(item.getId(), item.getName()));
				dispose();
			}
		});
		return row;
	}

	@Override
	public void dispose()
	{
		if (pendingSearch != null)
		{
			pendingSearch.cancel(false);
			pendingSearch = null;
		}
		super.dispose();
	}

	/** Lightweight value passed to the picker's callback. */
	static final class SelectedItem
	{
		final int id;
		final String name;

		SelectedItem(int id, String name)
		{
			this.id = id;
			this.name = name;
		}
	}
}
