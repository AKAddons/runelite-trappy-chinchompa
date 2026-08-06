package com.trappychinchompa.game;

import net.runelite.api.Experience;

/**
 * Anti-hunter levelling: thin wrappers over the client's canonical OSRS
 * experience curve ({@link Experience}), clamped to real levels.
 *
 * XP is tracked in TENTHS of a point (like the game client does internally)
 * so fractional awards stay exact.
 */
public final class HunterXp
{
	public static final int MAX_LEVEL = Experience.MAX_REAL_LEVEL;

	private HunterXp()
	{
	}

	/** Whole experience points required to be the given level (1-99). */
	public static int xpForLevel(int level)
	{
		return Experience.getXpForLevel(level);
	}

	/** The level this much Anti-hunter xp (in tenths) amounts to. */
	public static int levelForXpTenths(long xpTenths)
	{
		final long xp = Math.max(0, xpTenths) / 10;
		// The API keeps counting into virtual levels; Anti-hunter caps at 99.
		return Math.min(MAX_LEVEL, Experience.getLevelForXp((int) Math.min(xp, Integer.MAX_VALUE)));
	}
}
