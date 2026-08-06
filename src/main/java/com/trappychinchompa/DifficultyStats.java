package com.trappychinchompa;

/** One difficulty's lifetime tallies, as shown on the stats card. */
public class DifficultyStats
{
	private final int runs;
	private final int best;
	private final long xpTenths;
	private final long poles;

	public DifficultyStats(int runs, int best, long xpTenths, long poles)
	{
		this.runs = runs;
		this.best = best;
		this.xpTenths = xpTenths;
		this.poles = poles;
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

	/** Total traps cleared across every run at this difficulty. */
	public long getPoles()
	{
		return poles;
	}

	/** Mean traps cleared per run, one rounded decimal - "0.0" pre-run. */
	public String averageText()
	{
		if (runs <= 0)
		{
			return "0.0";
		}
		final long tenths = (poles * 10 + runs / 2) / runs;
		return (tenths / 10) + "." + (tenths % 10);
	}
}
