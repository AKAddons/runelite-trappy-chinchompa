package com.trappychinchompa;

import com.trappychinchompa.game.FlappyGame;

/**
 * Gap size / scroll speed presets - and the fairness rules that stop Easy
 * from being the optimal farm: Easy pays a tenth of the xp and never sets
 * the high score (so critter unlocks are Normal+ only), Hard pays x2.5.
 * A run's difficulty locks at launch; config changes apply from the
 * next run.
 *
 * Constant names render as titleCase labels in the config combo.
 */
public enum Difficulty
{
	EASY(130, 1.6, 200, 1, false),
	NORMAL(110, 1.9, 170, 10, true),
	HARD(90, 2.3, 95, 25, true);

	private final int gapSize;
	private final double speed;
	/** Max travel of the gap centre between consecutive traps. Tighter as
	 * speed rises - Hard leaves the fewest ticks to change altitude, so its
	 * gaps wander the least. */
	private final int maxGapStep;
	/** XP multiplier in tenths: 1 = x0.1, 10 = x1, 25 = x2.5. */
	private final int xpMultTenths;
	private final boolean countsForRecords;

	Difficulty(int gapSize, double speed, int maxGapStep, int xpMultTenths,
		boolean countsForRecords)
	{
		this.gapSize = gapSize;
		this.speed = speed;
		this.maxGapStep = maxGapStep;
		this.xpMultTenths = xpMultTenths;
		this.countsForRecords = countsForRecords;
	}

	public int getGapSize()
	{
		return gapSize;
	}

	public double getSpeed()
	{
		return speed;
	}

	public int getMaxGapStep()
	{
		return maxGapStep;
	}

	/** Whether runs at this difficulty can set the high score. */
	public boolean countsForRecords()
	{
		return countsForRecords;
	}

	/** Scale a base xp amount (in tenths) by this difficulty's multiplier. */
	public long applyXp(long baseTenths)
	{
		return baseTenths * xpMultTenths / 10;
	}

	/**
	 * The nth pole's award, scaled then rounded half-up to a WHOLE point.
	 * Because the base step is 10, the interval stays constant per
	 * difficulty: Easy 1,2,3... Normal 5,15,25... Hard 13,38,63...
	 */
	public long xpTenthsForPole(int pole)
	{
		final long scaled = applyXp(FlappyGame.xpTenthsForPole(pole));
		return (scaled + 5) / 10 * 10;
	}

	/**
	 * A run's total is exactly the sum of its per-pole awards, so what the
	 * xp drops show is always what gets banked.
	 */
	public long xpTenthsForRun(int score)
	{
		long total = 0;
		for (int pole = 1; pole <= score; pole++)
		{
			total += xpTenthsForPole(pole);
		}
		return total;
	}
}
