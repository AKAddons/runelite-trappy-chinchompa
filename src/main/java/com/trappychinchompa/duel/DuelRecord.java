package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * One duel as the relay keeps it, read from my side: who I play, which
 * game is mine to play next, which of their runs I still have to judge,
 * how long is left, and the outcome once settled.
 */
public final class DuelRecord
{
	public enum Todo
	{
		PLAY, WAIT, DONE
	}

	/** One reported run. */
	public static final class Report
	{
		private final int game;
		private final int score;
		private final List<Integer> flaps;
		private final int ticks;

		Report(int game, int score, List<Integer> flaps, int ticks)
		{
			this.game = game;
			this.score = score;
			this.flaps = Collections.unmodifiableList(flaps);
			this.ticks = ticks;
		}

		public int getGame()
		{
			return game;
		}

		public int getScore()
		{
			return score;
		}

		public List<Integer> getFlaps()
		{
			return flaps;
		}

		public int getTicks()
		{
			return ticks;
		}
	}

	private final Map<String, Object> raw;
	private final String id;
	private final String kind;
	private final DuelRules rules;
	private final String me;
	private final String opponent;
	/** Seeds by game index: only the games I have started are revealed. */
	private final Map<Integer, Long> seeds;
	private final long deadline;
	private final int stretchEnd;
	private final boolean done;
	private final String winner;
	private final String reason;
	private final List<Report> mine;
	private final List<Report> theirs;

	private DuelRecord(Map<String, Object> raw, String meName)
	{
		this.raw = raw;
		this.id = str(raw, "id");
		this.kind = str(raw, "kind");
		this.rules = DuelRules.parse(str(raw, "rules"));
		List<String> players = strings(raw.get("players"));
		String a = players.size() > 0 ? players.get(0) : "";
		String b = players.size() > 1 ? players.get(1) : "";
		boolean meIsA = Names.same(a, meName);
		this.me = meIsA ? a : b;
		this.opponent = meIsA ? b : a;
		this.seeds = new java.util.HashMap<>();
		Object seedMap = raw.get("seeds");
		if (seedMap instanceof Map)
		{
			for (Map.Entry<?, ?> e : ((Map<?, ?>) seedMap).entrySet())
			{
				try
				{
					seeds.put(Integer.parseInt(String.valueOf(e.getKey())), parseSeed(String.valueOf(e.getValue())));
				}
				catch (NumberFormatException ignored)
				{
					// A key that is not a game index: not ours.
				}
			}
		}
		this.deadline = num(raw, "deadline");
		this.stretchEnd = (int) num(raw, "stretchEnd");
		this.done = "DONE".equals(str(raw, "status"));
		Object result = raw.get("result");
		this.winner = result instanceof Map ? nullable(((Map<?, ?>) result).get("winner")) : null;
		this.reason = result instanceof Map ? nullable(((Map<?, ?>) result).get("reason")) : null;
		this.mine = reports(raw, me);
		this.theirs = reports(raw, opponent);
	}

	public static DuelRecord from(Map<String, Object> raw, String me)
	{
		return new DuelRecord(raw, me);
	}

	// ---- identity ----

	public String getId()
	{
		return id;
	}

	public boolean isCompete()
	{
		return "compete".equals(kind);
	}

	public DuelRules getRules()
	{
		return rules;
	}

	public String getMe()
	{
		return me;
	}

	public String getOpponent()
	{
		return opponent;
	}

	public long getDeadline()
	{
		return deadline;
	}

	/** When the relay settled it (0 while active). */
	public long getSettledAt()
	{
		return num(raw, "settled");
	}

	public boolean isDone()
	{
		return done;
	}

	public Map<String, Object> raw()
	{
		return raw;
	}

	// ---- what to do ----

	/** 1-based index of the game I should play next, or 0 when my stretch is in. */
	public int nextGame()
	{
		if (done || mine.size() >= stretchEnd || mine.size() >= 10)
		{
			return 0;
		}
		return mine.size() + 1;
	}

	/** The seed for a game I have started, else null (ask the relay with start). */
	public Long seed(int gameIndex)
	{
		return seeds.get(gameIndex);
	}

	public boolean hasSeed(int gameIndex)
	{
		return seeds.containsKey(gameIndex);
	}

	public Report theirReport(int gameIndex)
	{
		return gameIndex >= 1 && gameIndex <= theirs.size() ? theirs.get(gameIndex - 1) : null;
	}

	public Report myReport(int gameIndex)
	{
		return gameIndex >= 1 && gameIndex <= mine.size() ? mine.get(gameIndex - 1) : null;
	}

	public Todo todo()
	{
		if (done)
		{
			return Todo.DONE;
		}
		return nextGame() > 0 ? Todo.PLAY : Todo.WAIT;
	}

	public long timeLeft(long now)
	{
		return Math.max(0, deadline - now);
	}

	public boolean isSuddenDeath(int gameIndex)
	{
		return rules != null && gameIndex > rules.getSeries();
	}

	public String gameLabel(int gameIndex)
	{
		return isSuddenDeath(gameIndex) ? "sudden death" : "game " + gameIndex + (rules == null ? "" : " of " + rules.getSeries());
	}

	// ---- the verdict ----

	public DuelOutcome outcome()
	{
		int n = Math.min(mine.size(), theirs.size());
		Series series = new Series(rules == null ? DuelRules.parse("n-bs1") : rules);
		for (int i = 0; i < n; i++)
		{
			series.record(i + 1, mine.get(i).getScore(), theirs.get(i).getScore());
		}
		DuelOutcome.Reason r;
		switch (reason == null ? "" : reason)
		{
			case "WIN":
				r = DuelOutcome.Reason.WIN;
				break;
			case "FORFEIT":
				r = DuelOutcome.Reason.FORFEIT;
				break;
			case "CHEAT":
				r = DuelOutcome.Reason.CHEAT;
				break;
			case "TIMEOUT":
				r = DuelOutcome.Reason.DISCONNECT;
				break;
			case "DRAW":
				r = DuelOutcome.Reason.DRAW;
				break;
			default:
				r = DuelOutcome.Reason.NO_CONTEST;
		}
		return new DuelOutcome(winner, r, series.lines(), series.scoreLine());
	}

	// ---- parsing ----

	private static List<Report> reports(Map<String, Object> raw, String who)
	{
		List<Report> out = new ArrayList<>();
		Object all = raw.get("reports");
		if (!(all instanceof Map))
		{
			return out;
		}
		Object list = ((Map<?, ?>) all).get(who);
		if (!(list instanceof List))
		{
			return out;
		}
		for (Object o : (List<?>) list)
		{
			if (o instanceof Map)
			{
				Map<?, ?> m = (Map<?, ?>) o;
				List<Integer> flaps = new ArrayList<>();
				Object f = m.get("flaps");
				if (f instanceof List)
				{
					for (Object x : (List<?>) f)
					{
						flaps.add(((Number) x).intValue());
					}
				}
				out.add(new Report(((Number) m.get("game")).intValue(), ((Number) m.get("score")).intValue(),
					flaps, ((Number) m.get("ticks")).intValue()));
			}
		}
		return out;
	}

	private static List<String> strings(Object o)
	{
		List<String> out = new ArrayList<>();
		if (o instanceof List)
		{
			for (Object x : (List<?>) o)
			{
				out.add(String.valueOf(x));
			}
		}
		return out;
	}

	private static long parseSeed(String s)
	{
		try
		{
			return Long.parseLong(s.trim());
		}
		catch (NumberFormatException e)
		{
			return s.hashCode();
		}
	}

	private static String str(Map<String, Object> m, String key)
	{
		Object v = m.get(key);
		return v == null ? "" : String.valueOf(v);
	}

	private static String nullable(Object v)
	{
		return v == null ? null : String.valueOf(v);
	}

	private static long num(Map<String, Object> m, String key)
	{
		Object v = m.get(key);
		return v instanceof Number ? ((Number) v).longValue() : 0L;
	}
}
