package com.trappychinchompa.game;

import java.util.Set;

/**
 * Backdrop zones, unlocked by Anti-hunter level - plus one achievement-gated
 * mystery (its id matched against the achievement catalog by a drift-guard
 * test, since this package cannot depend on it). Rendering (palettes,
 * skylines) lives in the canvas; this is just the unlock ladder, kept
 * headless so it can be tested.
 *
 * Enum order is ladder order; Prifddinas is the level-99 capstone.
 */
public enum BackgroundTheme
{
	FELDIP_MARSH(1),
	LUMBRIDGE(10),
	VARROCK(20),
	AL_KHARID(30),
	MORYTANIA(40),
	ARDOUGNE(50),
	KOUREND(60),
	FREMENNIK(70),
	VARLAMORE(80),
	WILDERNESS(90),
	PRIFDDINAS(99),
	/** Mystery reward: clear 250 poles on Easy. Pure chaos. */
	PANDEMONIUM(Integer.MAX_VALUE, "score_EASY_250"),
	/** Mystery reward: a 50x20 streak on Normal OR Hard. */
	INFERNO(Integer.MAX_VALUE, "streak_NORMAL_50x20", "streak_HARD_50x20");

	private final int unlockLevel;
	/** Achievements gating this zone (ANY earns it), empty for level zones. */
	private final String[] unlockAchievementIds;

	BackgroundTheme(int unlockLevel, String... unlockAchievementIds)
	{
		this.unlockLevel = unlockLevel;
		this.unlockAchievementIds = unlockAchievementIds;
	}

	public int getUnlockLevel()
	{
		return unlockLevel;
	}

	public boolean isAchievementGated()
	{
		return unlockAchievementIds.length > 0;
	}

	/** The achievements gating this zone; empty for level-based zones. */
	public String[] getUnlockAchievementIds()
	{
		return unlockAchievementIds.clone();
	}

	/** The full check: level threshold, or ANY of the gating achievements. */
	public boolean isUnlocked(int level, Set<String> earnedAchievements)
	{
		if (isAchievementGated())
		{
			for (String id : unlockAchievementIds)
			{
				if (earnedAchievements.contains(id))
				{
					return true;
				}
			}
			return false;
		}
		return level >= unlockLevel;
	}

	/** The best zone available; FELDIP_MARSH is always open. */
	public static BackgroundTheme highestUnlocked(int level, Set<String> earnedAchievements)
	{
		BackgroundTheme best = FELDIP_MARSH;
		for (BackgroundTheme theme : values())
		{
			if (theme.isUnlocked(level, earnedAchievements))
			{
				best = theme;
			}
		}
		return best;
	}

	/**
	 * The most exciting zone newly unlocked by a level-before -> level-after
	 * jump, or null. Achievement-gated zones announce through their
	 * achievements instead.
	 */
	public static BackgroundTheme newlyUnlocked(int levelBefore, int levelAfter)
	{
		BackgroundTheme newest = null;
		for (BackgroundTheme theme : values())
		{
			if (!theme.isAchievementGated()
				&& theme.unlockLevel > levelBefore && theme.unlockLevel <= levelAfter)
			{
				newest = theme;
			}
		}
		return newest;
	}
}
