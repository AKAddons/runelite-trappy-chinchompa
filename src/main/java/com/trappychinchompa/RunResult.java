package com.trappychinchompa;

import com.trappychinchompa.game.BackgroundTheme;
import com.trappychinchompa.game.ChinSkin;
import java.util.Collections;
import java.util.List;

/**
 * Everything the game-over screen needs about the run that just ended,
 * computed and persisted by the plugin in one place. "Anti-hunter xp" is
 * the xp a hunter would have earned catching you - you earn it instead.
 */
public class RunResult
{
	private final int score;
	private final long runXpTenths;
	private final long lifetimeXpTenths;
	private final int antiHunterLevel;
	/** The level just crossed by this run, or 0 if none. */
	private final int leveledTo;
	/** A zone this run's level-ups unlocked, or null. */
	private final BackgroundTheme unlockedBackground;
	/** A critter this run's new high score unlocked, or null. */
	private final ChinSkin unlockedSkin;
	/** Achievements this run earned for the first time, in catalog order. */
	private final List<Achievement> newAchievements;
	/** Best at THIS run's difficulty (display); the record stays separate. */
	private final int difficultyBest;
	private final boolean newDifficultyBest;

	public RunResult(int score, long runXpTenths, long lifetimeXpTenths, int antiHunterLevel,
		int leveledTo, BackgroundTheme unlockedBackground, ChinSkin unlockedSkin,
		List<Achievement> newAchievements, int difficultyBest, boolean newDifficultyBest)
	{
		this.score = score;
		this.runXpTenths = runXpTenths;
		this.lifetimeXpTenths = lifetimeXpTenths;
		this.antiHunterLevel = antiHunterLevel;
		this.leveledTo = leveledTo;
		this.unlockedBackground = unlockedBackground;
		this.unlockedSkin = unlockedSkin;
		this.newAchievements = newAchievements == null
			? Collections.emptyList() : newAchievements;
		this.difficultyBest = difficultyBest;
		this.newDifficultyBest = newDifficultyBest;
	}

	/** A sandbox flight: would-be numbers for display, nothing changed. */
	public static RunResult sandbox(int score, long wouldBeXpTenths, long lifetimeXpTenths,
		int antiHunterLevel, int difficultyBest)
	{
		return new RunResult(score, wouldBeXpTenths, lifetimeXpTenths, antiHunterLevel, 0,
			null, null, Collections.emptyList(), difficultyBest, false);
	}

	public int getScore()
	{
		return score;
	}

	public long getRunXpTenths()
	{
		return runXpTenths;
	}

	public long getLifetimeXpTenths()
	{
		return lifetimeXpTenths;
	}

	public int getAntiHunterLevel()
	{
		return antiHunterLevel;
	}

	public int getLeveledTo()
	{
		return leveledTo;
	}

	public BackgroundTheme getUnlockedBackground()
	{
		return unlockedBackground;
	}

	public ChinSkin getUnlockedSkin()
	{
		return unlockedSkin;
	}

	public List<Achievement> getNewAchievements()
	{
		return newAchievements;
	}

	public int getDifficultyBest()
	{
		return difficultyBest;
	}

	public boolean isNewDifficultyBest()
	{
		return newDifficultyBest;
	}
}
