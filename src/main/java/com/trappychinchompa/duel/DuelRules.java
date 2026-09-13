package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a duel is played under: difficulty, how the series is scored and
 * how many games. Canonical code "<e|n|h>-<bs|av><1|3|5>", e.g. "h-bs3".
 */
public final class DuelRules
{
	public enum Metric
	{
		BEST_ROUND("bs", "best round"),
		AVERAGE("av", "average");

		private final String code;
		private final String label;

		Metric(String code, String label)
		{
			this.code = code;
			this.label = label;
		}

		public String getCode()
		{
			return code;
		}

		public String getLabel()
		{
			return label;
		}

		static Metric fromCode(String code)
		{
			for (Metric m : values())
			{
				if (m.code.equals(code))
				{
					return m;
				}
			}
			return null;
		}
	}

	public static final int[] SERIES = {1, 3, 5};
	private static final Pattern CANONICAL = Pattern.compile("^([enh])-(bs|av)([135])$");

	private final Difficulty difficulty;
	private final Metric metric;
	private final int series;

	public DuelRules(Difficulty difficulty, Metric metric, int series)
	{
		this.difficulty = Objects.requireNonNull(difficulty);
		this.metric = Objects.requireNonNull(metric);
		if (series != 1 && series != 3 && series != 5)
		{
			throw new IllegalArgumentException("series must be 1, 3 or 5");
		}
		this.series = series;
	}

	/** Null for anything outside the grammar. */
	public static DuelRules parse(String code)
	{
		if (code == null)
		{
			return null;
		}
		Matcher m = CANONICAL.matcher(code.toLowerCase());
		if (!m.matches())
		{
			return null;
		}
		Difficulty d;
		switch (m.group(1))
		{
			case "e":
				d = Difficulty.EASY;
				break;
			case "n":
				d = Difficulty.NORMAL;
				break;
			default:
				d = Difficulty.HARD;
		}
		return new DuelRules(d, Metric.fromCode(m.group(2)), m.group(3).charAt(0) - '0');
	}

	public String canonical()
	{
		return Character.toLowerCase(difficulty.name().charAt(0)) + "-" + metric.code + series;
	}

	/** "Hard, best round of 3" */
	public String describe()
	{
		String name = difficulty.name().charAt(0) + difficulty.name().substring(1).toLowerCase();
		return name + ", " + metric.label + " of " + series;
	}

	public Difficulty getDifficulty()
	{
		return difficulty;
	}

	public Metric getMetric()
	{
		return metric;
	}

	public int getSeries()
	{
		return series;
	}

	@Override
	public boolean equals(Object o)
	{
		if (!(o instanceof DuelRules))
		{
			return false;
		}
		DuelRules r = (DuelRules) o;
		return difficulty == r.difficulty && metric == r.metric && series == r.series;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(difficulty, metric, series);
	}

	@Override
	public String toString()
	{
		return canonical();
	}
}
