package com.trappychinchompa;

import java.util.Collections;
import java.util.Set;

/**
 * Current unlock progress, published by the plugin so picker enums can
 * render lock-aware labels and answer isUnlocked without a plugin handle.
 */
public final class UnlockState
{
	private static volatile int bestScore;
	private static volatile int antiHunterLevel = 1;
	private static volatile Set<String> earnedAchievements = Collections.emptySet();
	/** Developer-mode override: everything reads as unlocked. Session-only. */
	private static volatile boolean devUnlockAll;

	private UnlockState()
	{
	}

	/** Full update, as the plugin publishes it. */
	public static void update(int newBestScore, int newAntiHunterLevel, Set<String> earned)
	{
		bestScore = newBestScore;
		antiHunterLevel = newAntiHunterLevel;
		earnedAchievements = earned == null ? Collections.emptySet() : earned;
	}

	/** Convenience for tests: resets the earned set and the dev override. */
	public static void update(int newBestScore, int newAntiHunterLevel)
	{
		update(newBestScore, newAntiHunterLevel, Collections.emptySet());
		devUnlockAll = false;
	}

	public static boolean isDevUnlockAll()
	{
		return devUnlockAll;
	}

	public static void setDevUnlockAll(boolean on)
	{
		devUnlockAll = on;
	}

	public static int getBestScore()
	{
		return bestScore;
	}

	public static int getAntiHunterLevel()
	{
		return antiHunterLevel;
	}

	public static Set<String> getEarnedAchievements()
	{
		return earnedAchievements;
	}
}
