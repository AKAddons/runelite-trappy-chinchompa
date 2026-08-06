package com.trappychinchompa;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

/**
 * Deliberately small: critter and zone are chosen from the pickers on the
 * game's start screen (where locked rows can actually be disabled), stored
 * under this group as hidden keys alongside the high score and lifetime xp.
 * Critters unlock at best 10/20/40/60/80/100 plus achievement trophies.
 */
@ConfigGroup(TrappyChinchompaConfig.GROUP)
public interface TrappyChinchompaConfig extends Config
{
	String GROUP = "trappychinchompa";

	@ConfigItem(
		keyName = "difficulty",
		name = "Difficulty",
		description = "Easy: x0.1 xp and no records (practice). Hard: x2.5 xp. Applies from the next run.",
		position = 1
	)
	default Difficulty difficulty()
	{
		return Difficulty.NORMAL;
	}

	@ConfigItem(
		keyName = "showXpDrops",
		name = "XP drops",
		description = "Float +xp above the chin for every trap you dodge.",
		position = 2
	)
	default boolean showXpDrops()
	{
		return true;
	}
}
