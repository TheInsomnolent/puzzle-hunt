package com.puzzlehunt;

import com.puzzlehunt.model.PuzzleHunt;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar host panel — manages a {@link CardLayout} of child views and acts
 * as the controller they navigate through.
 */
@Slf4j
@Singleton
public class PuzzleHuntPanel extends PluginPanel implements ActiveHuntService.Listener
{
	static final String CARD_HOME = "home";
	static final String CARD_DETAIL = "detail";
	static final String CARD_CREATE = "create";
	static final String CARD_ACTIVE = "active";
	static final String CARD_SUMMARY = "summary";

	private final HuntManager huntManager;
	private final ActiveHuntService active;
	private final CompletionDetector detector;
	private final TileSelectionOverlay tileOverlay;
	private final CountdownOverlay countdownOverlay;
	private final Client client;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final ScheduledExecutorService executor;

	private final CardLayout cards = new CardLayout();
	/**
	 * CardLayout normally sizes itself to the largest card, which forces
	 * the surrounding scroll pane to be tall enough for whichever view
	 * has the most content. We override prefSize to follow the visible
	 * card so the scrollbar only appears when the current view actually
	 * needs it (mirrors Quest Helper's viewport pattern).
	 *
	 * <p>Implements {@link Scrollable} with
	 * {@code getScrollableTracksViewportWidth() == true} so the inner
	 * content is forced to the viewport width — preventing children with
	 * runaway preferred widths (e.g. an unwrapped JTextArea) from
	 * pushing the layout past the visible sidebar area.
	 */
	private final ScrollableCardHost cardHost = new ScrollableCardHost(cards);

	private static class ScrollableCardHost extends JPanel implements Scrollable
	{
		ScrollableCardHost(CardLayout layout) { super(layout); }

		@Override
		public Dimension getPreferredSize()
		{
			for (Component c : getComponents())
			{
				if (c.isVisible())
				{
					Dimension d = c.getPreferredSize();
					int w = getParent() != null ? getParent().getWidth() : PluginPanel.PANEL_WIDTH;
					return new Dimension(w > 0 ? w : d.width, d.height);
				}
			}
			return super.getPreferredSize();
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
		@Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
		@Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}


	private HomeView homeView;
	private HuntDetailView detailView;
	private CreateHuntView createView;
	private ActiveHuntView activeView;
	private SummaryView summaryView;

	/** Repaints the active view ~5x per second so the timer ticks smoothly. */
	private final Timer uiTick = new Timer(200, e -> tickViews());

	@Inject
	PuzzleHuntPanel(
		HuntManager huntManager,
		ActiveHuntService active,
		CompletionDetector detector,
		TileSelectionOverlay tileOverlay,
		CountdownOverlay countdownOverlay,
		Client client,
		ItemManager itemManager,
		ClientThread clientThread,
		ScheduledExecutorService executor)
	{
		// false = don't let PluginPanel wrap us in its default scroll pane;
		// we install our own with HORIZONTAL_SCROLLBAR_NEVER so wide
		// children wrap rather than producing a horizontal scrollbar.
		super(false);
		this.huntManager = huntManager;
		this.active = active;
		this.detector = detector;
		this.tileOverlay = tileOverlay;
		this.countdownOverlay = countdownOverlay;
		this.client = client;
		this.itemManager = itemManager;
		this.clientThread = clientThread;
		this.executor = executor;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		homeView = new HomeView(this);
		detailView = new HuntDetailView(this);
		createView = new CreateHuntView(this);
		activeView = new ActiveHuntView(this);
		summaryView = new SummaryView(this);

		cardHost.add(homeView, CARD_HOME);
		cardHost.add(detailView, CARD_DETAIL);
		cardHost.add(createView, CARD_CREATE);
		cardHost.add(activeView, CARD_ACTIVE);
		cardHost.add(summaryView, CARD_SUMMARY);
		cardHost.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JScrollPane scroll = new JScrollPane(cardHost);
		scroll.setBorder(null);
		scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(scroll, BorderLayout.CENTER);

		active.addListener(this);
		uiTick.start();
	}

	// --- accessors used by the views ----------------------------------
	HuntManager getHuntManager() { return huntManager; }
	ActiveHuntService getActive() { return active; }
	CompletionDetector getDetector() { return detector; }
	TileSelectionOverlay getTileOverlay() { return tileOverlay; }
	CountdownOverlay getCountdownOverlay() { return countdownOverlay; }
	Client getClient() { return client; }
	ItemManager getItemManager() { return itemManager; }
	ClientThread getClientThread() { return clientThread; }
	ScheduledExecutorService getExecutor() { return executor; }

	// --- navigation ---------------------------------------------------
	void showHome()
	{
		tileOverlay.setStartingTile(null);
		homeView.refresh();
		show(CARD_HOME);
	}

	void showDetail(PuzzleHunt hunt)
	{
		tileOverlay.setStartingTile(hunt == null ? null : hunt.getStartingTile());
		detailView.setHunt(hunt);
		show(CARD_DETAIL);
	}

	void showCreate(PuzzleHunt hunt)
	{
		tileOverlay.setStartingTile(null);
		createView.setHunt(hunt);
		show(CARD_CREATE);
	}

	void showActive()
	{
		tileOverlay.setStartingTile(null);
		activeView.refresh();
		show(CARD_ACTIVE);
	}

	void showSummary()
	{
		tileOverlay.setStartingTile(null);
		summaryView.refresh();
		show(CARD_SUMMARY);
	}

	private void show(String card)
	{
		SwingUtilities.invokeLater(() ->
		{
			cards.show(cardHost, card);
			cardHost.revalidate();
			cardHost.repaint();
		});
	}

	private void tickViews()
	{
		if (active.getActiveHunt() != null && activeView.isShowing())
		{
			activeView.tick();
		}
		if (detailView.isShowing())
		{
			detailView.tick();
		}
	}

	@Override
	public void onActiveHuntChanged()
	{
		SwingUtilities.invokeLater(() ->
		{
			homeView.refresh();
			activeView.refresh();
			summaryView.refresh();
			// If the hunt just completed, jump straight to the summary view.
			if (active.getActiveProgress() != null && active.getActiveProgress().isCompleted()
				&& activeView.isShowing())
			{
				showSummary();
			}
		});
	}

	/** Called by the plugin on shutdown. */
	void dispose()
	{
		uiTick.stop();
		active.removeListener(this);
	}
}
