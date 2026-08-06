package com.trappychinchompa;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/**
 * Dev harness main: boots a RuneLite client with the plugin sideloaded.
 * Run via {@code ./gradlew run} (developer mode, -ea).
 */
public class TrappyChinchompaPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TrappyChinchompaPlugin.class);
		RuneLite.main(args);
	}
}
