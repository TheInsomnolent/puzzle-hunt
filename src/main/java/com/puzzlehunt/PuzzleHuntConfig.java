package com.puzzlehunt;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PuzzleHuntConfig.GROUP)
public interface PuzzleHuntConfig extends Config
{
	String GROUP = "puzzle-hunt";

	@ConfigSection(
		name = "General",
		description = "General puzzle hunt options",
		position = 0
	)
	String generalSection = "general";

	@ConfigItem(
		keyName = "showSidebar",
		name = "Show sidebar",
		description = "Display the puzzle hunt sidebar navigation button",
		section = generalSection,
		position = 0
	)
	default boolean showSidebar()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pauseTimerOnLogout",
		name = "Pause timer on logout",
		description = "Automatically pause the active hunt timer when you log out",
		section = generalSection,
		position = 1
	)
	default boolean pauseTimerOnLogout()
	{
		return true;
	}
}
