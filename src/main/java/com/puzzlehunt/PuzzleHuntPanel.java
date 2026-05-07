package com.puzzlehunt;

import com.puzzlehunt.model.PuzzleHunt;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import lombok.extern.slf4j.Slf4j;
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
	private final EyedropperService eyedropper;
	private final CompletionDetector detector;
	private final TileSelectionOverlay tileOverlay;

	private final CardLayout cards = new CardLayout();
	private final JPanel cardHost = new JPanel(cards);

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
		EyedropperService eyedropper,
		CompletionDetector detector,
		TileSelectionOverlay tileOverlay)
	{
		super();
		this.huntManager = huntManager;
		this.active = active;
		this.eyedropper = eyedropper;
		this.detector = detector;
		this.tileOverlay = tileOverlay;

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

		add(cardHost, BorderLayout.CENTER);

		active.addListener(this);
		uiTick.start();
	}

	// --- accessors used by the views ----------------------------------
	HuntManager getHuntManager() { return huntManager; }
	ActiveHuntService getActive() { return active; }
	EyedropperService getEyedropper() { return eyedropper; }
	CompletionDetector getDetector() { return detector; }
	TileSelectionOverlay getTileOverlay() { return tileOverlay; }

	// --- navigation ---------------------------------------------------
	void showHome()
	{
		homeView.refresh();
		show(CARD_HOME);
	}

	void showDetail(PuzzleHunt hunt)
	{
		detailView.setHunt(hunt);
		show(CARD_DETAIL);
	}

	void showCreate(PuzzleHunt hunt)
	{
		createView.setHunt(hunt);
		show(CARD_CREATE);
	}

	void showActive()
	{
		activeView.refresh();
		show(CARD_ACTIVE);
	}

	void showSummary()
	{
		summaryView.refresh();
		show(CARD_SUMMARY);
	}

	private void show(String card)
	{
		SwingUtilities.invokeLater(() -> cards.show(cardHost, card));
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
