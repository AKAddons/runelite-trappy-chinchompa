package com.trappychinchompa;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

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

	@ConfigSection(
		name = "Duels",
		description = "Challenge friends to the same traps on the same seed",
		position = 10
	)
	String duels = "duels";

	@ConfigItem(
		keyName = "duelsEnabled",
		name = "Enable duels",
		description = "Connects to the AKAddons relay and sends your display name so friends can challenge you. Nothing else is sent and nothing is stored.",
		section = duels,
		position = 11
	)
	default boolean duelsEnabled()
	{
		return false;
	}

	@ConfigItem(
		keyName = "relayUrl",
		name = "Relay URL",
		description = "The duel relay to connect to. Leave it alone unless you run your own.",
		section = duels,
		position = 12
	)
	default String relayUrl()
	{
		return "wss://akaddons-relay.akaddons-relay.workers.dev/ws";
	}

	@ConfigItem(
		keyName = "duelHidden",
		name = "Hide me from top scores",
		description = "Play duels and compete, but keep your name off the top-scores board and rankings.",
		section = duels,
		position = 13
	)
	default boolean duelHidden()
	{
		return false;
	}

	@ConfigItem(
		keyName = "duelBlocked",
		name = "Block challenges from",
		description = "Player names, separated by commas. Their challenges are declined without a word.",
		section = duels,
		position = 14
	)
	default String duelBlocked()
	{
		return "";
	}
}
