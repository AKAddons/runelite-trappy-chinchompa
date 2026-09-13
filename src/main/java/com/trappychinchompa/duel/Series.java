package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.List;

/**
 * Series bookkeeping: every game is played, the metric is aggregate, and
 * a dead-even aggregate goes to sudden death one game at a time.
 */
public final class Series
{
	private final DuelRules rules;
	private final List<DuelOutcome.GameLine> lines = new ArrayList<>();

	public Series(DuelRules rules)
	{
		this.rules = rules;
	}

	public DuelRules getRules()
	{
		return rules;
	}

	public void record(int gameIndex, int mine, int theirs)
	{
		lines.add(new DuelOutcome.GameLine(gameIndex, mine, theirs, gameIndex > rules.getSeries()));
	}

	/** The recorded line for a game, or a prospective unplayed one. */
	public DuelOutcome.GameLine lineFor(int gameIndex)
	{
		for (DuelOutcome.GameLine l : lines)
		{
			if (l.getGameIndex() == gameIndex)
			{
				return l;
			}
		}
		return new DuelOutcome.GameLine(gameIndex, 0, 0, gameIndex > rules.getSeries());
	}

	public List<DuelOutcome.GameLine> lines()
	{
		return new ArrayList<>(lines);
	}

	public int nextGameIndex()
	{
		return lines.size() + 1;
	}

	/** Positive when my side leads, negative when theirs does, zero when even. A sudden-death game decides on its own. */
	public int compare()
	{
		if (lines.size() > rules.getSeries())
		{
			DuelOutcome.GameLine last = lines.get(lines.size() - 1);
			return Integer.compare(last.getMine(), last.getTheirs());
		}
		return Integer.compare(aggregate(true), aggregate(false));
	}

	public boolean complete()
	{
		return lines.size() >= rules.getSeries() && compare() != 0;
	}

	private int aggregate(boolean mine)
	{
		int best = 0;
		int sum = 0;
		for (DuelOutcome.GameLine l : lines.subList(0, Math.min(lines.size(), rules.getSeries())))
		{
			int s = mine ? l.getMine() : l.getTheirs();
			best = Math.max(best, s);
			sum += s;
		}
		return rules.getMetric() == DuelRules.Metric.BEST_ROUND ? best : sum;
	}

	/** "best 9 to 8" or "total 16 to 24", my side first. */
	public String scoreLine()
	{
		String label = rules.getMetric() == DuelRules.Metric.BEST_ROUND ? "best" : "total";
		String line = label + " " + aggregate(true) + " to " + aggregate(false);
		if (lines.size() > rules.getSeries())
		{
			DuelOutcome.GameLine last = lines.get(lines.size() - 1);
			line += ", sudden death " + last.getMine() + " to " + last.getTheirs();
		}
		return line;
	}
}
