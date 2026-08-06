package com.trappychinchompa;

/** One difficulty's lifetime tallies, as shown on the stats card. */
public class DifficultyStats
{
	private final int runs;
	private final int best;
	private final long xpTenths;

	public DifficultyStats(int runs, int best, long xpTenths)
	{
		this.runs = runs;
		this.best = best;
		this.xpTenths = xpTenths;
	}

	public int getRuns()
	{
		return runs;
	}

	/** Best score at this difficulty - tracked even on Easy, which never
	 * sets the unlock record. */
	public int getBest()
	{
		return best;
	}

	public long getXpTenths()
	{
		return xpTenths;
	}
}
