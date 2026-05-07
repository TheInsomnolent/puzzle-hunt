package com.puzzlehunt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PuzzleHuntPluginTest
{
	public static void main(String[] args) throws Exception
	{
		@SuppressWarnings({"unchecked", "rawtypes"})
		Class<? extends net.runelite.client.plugins.Plugin>[] plugins = new Class[] { PuzzleHuntPlugin.class };
		ExternalPluginManager.loadBuiltin(plugins);
		RuneLite.main(args);
	}
}
