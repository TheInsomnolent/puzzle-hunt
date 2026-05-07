package com.puzzlehunt;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PuzzleHuntPanel panel;

	@Inject
	private PuzzleHuntConfig config;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		log.debug("Puzzle Hunt starting up");

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

		panel.refresh();
	}

	@Override
	protected void shutDown()
	{
		log.debug("Puzzle Hunt shutting down");
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
	}

	@Provides
	PuzzleHuntConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PuzzleHuntConfig.class);
	}
}
