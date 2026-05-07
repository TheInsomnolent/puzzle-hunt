package com.puzzlehunt;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Puzzle Hunt",
	description = "Build, run and share custom puzzle hunts",
	tags = {"puzzle", "hunt", "challenge", "trail", "diary"}
)
public class PuzzleHuntPlugin extends Plugin
{
	private static final String ICON_RESOURCE = "icon.png";

	@Inject private ClientToolbar clientToolbar;
	@Inject private OverlayManager overlayManager;
	@Inject private EventBus eventBus;

	@Inject private PuzzleHuntPanel panel;
	@Inject private PuzzleHuntConfig config;
	@Inject private ActiveHuntService active;
	@Inject private CompletionDetector detector;
	@Inject private TileSelectionOverlay tileOverlay;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		log.debug("Puzzle Hunt starting up");

		eventBus.register(detector);
		overlayManager.add(tileOverlay);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), ICON_RESOURCE);
		navButton = NavigationButton.builder()
			.tooltip("Puzzle Hunt")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		if (config.showSidebar())
		{
			clientToolbar.addNavigation(navButton);
		}

		panel.showHome();
	}

	@Override
	protected void shutDown()
	{
		log.debug("Puzzle Hunt shutting down");
		// Persist any in-flight timer before tearing down.
		active.pauseTimer();
		eventBus.unregister(detector);
		overlayManager.remove(tileOverlay);
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		panel.dispose();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!config.pauseTimerOnLogout())
		{
			return;
		}
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			active.pauseTimer();
		}
		else if (state == GameState.LOGGED_IN)
		{
			// Resume only if there's an active hunt that isn't yet completed.
			if (active.getActiveHunt() != null && active.getActiveProgress() != null
				&& !active.getActiveProgress().isCompleted())
			{
				active.startTimer();
				detector.resetInventoryBaseline();
			}
		}
	}

	@Provides
	PuzzleHuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PuzzleHuntConfig.class);
	}
}
