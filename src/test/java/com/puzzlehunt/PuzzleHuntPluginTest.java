package com.puzzlehunt;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PuzzleHuntPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PuzzleHuntPlugin.class);
		RuneLite.main(args);
	}
}
