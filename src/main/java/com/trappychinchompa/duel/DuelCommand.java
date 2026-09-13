package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import java.util.Locale;

/**
 * The typed side of duels: {@code ::duel <player> trappy [1|3|5]
 * [best|avg] [easy|normal|hard]}, plus {@code ::duel accept|no|gg}.
 * RuneLite hands us the words; the command never reaches the game.
 */
public final class DuelCommand
{
	public static final String USAGE = "Usage: ::duel <player> trappy [1|3|5] [best|avg] [easy|normal|hard], or ::duel accept | no | gg";
	private static final String[] GAME_TOKENS = {"trappy", "tc", "chin", "chinchompa", "trappychinchompa", "trappy-chinchompa"};

	public enum Kind
	{
		CHALLENGE, ACCEPT, DECLINE, CONCEDE, USAGE, NOT_OURS
	}

	private final Kind kind;
	private final String player;
	private final DuelRules rules;

	private DuelCommand(Kind kind, String player, DuelRules rules)
	{
		this.kind = kind;
		this.player = player;
		this.rules = rules;
	}

	public Kind getKind()
	{
		return kind;
	}

	public String getPlayer()
	{
		return player;
	}

	public DuelRules getRules()
	{
		return rules;
	}

	public static DuelCommand parse(String[] args, Difficulty current)
	{
		if (args == null || args.length == 0 || args[0].isEmpty())
		{
			return new DuelCommand(Kind.USAGE, null, null);
		}
		String first = args[0].toLowerCase(Locale.ROOT);
		switch (first)
		{
			case "accept":
			case "yes":
			case "ok":
				return new DuelCommand(Kind.ACCEPT, null, null);
			case "no":
			case "decline":
				return new DuelCommand(Kind.DECLINE, null, null);
			case "gg":
			case "concede":
			case "forfeit":
				return new DuelCommand(Kind.CONCEDE, null, null);
			default:
				break;
		}
		if (args.length < 2)
		{
			return new DuelCommand(Kind.USAGE, null, null);
		}
		String game = args[1].toLowerCase(Locale.ROOT);
		if (!isOurs(game))
		{
			// Another plugin's game, or a slot the player forgot to fill.
			return new DuelCommand(game.matches("[0-9]+|best|avg|average|easy|normal|hard") ? Kind.USAGE : Kind.NOT_OURS, null, null);
		}
		int series = 3;
		DuelRules.Metric metric = DuelRules.Metric.BEST_ROUND;
		Difficulty difficulty = current;
		for (int i = 2; i < args.length; i++)
		{
			String w = args[i].toLowerCase(Locale.ROOT);
			switch (w)
			{
				case "1":
				case "3":
				case "5":
					series = w.charAt(0) - '0';
					break;
				case "best":
				case "bs":
					metric = DuelRules.Metric.BEST_ROUND;
					break;
				case "avg":
				case "average":
				case "av":
					metric = DuelRules.Metric.AVERAGE;
					break;
				case "easy":
					difficulty = Difficulty.EASY;
					break;
				case "normal":
					difficulty = Difficulty.NORMAL;
					break;
				case "hard":
					difficulty = Difficulty.HARD;
					break;
				default:
					return new DuelCommand(Kind.USAGE, null, null);
			}
		}
		String player = args[0].replace('_', ' ').trim();
		return new DuelCommand(Kind.CHALLENGE, player, new DuelRules(difficulty, metric, series));
	}

	/** The typed form of a challenge: "::duel test_purr trappy 1 avg easy" (defaults left out). */
	public static String format(String player, DuelRules rules, Difficulty current)
	{
		StringBuilder sb = new StringBuilder("::duel ").append(player.trim().replace(' ', '_')).append(" trappy ")
			.append(rules.getSeries());
		if (rules.getMetric() == DuelRules.Metric.AVERAGE)
		{
			sb.append(" avg");
		}
		if (rules.getDifficulty() != current)
		{
			sb.append(' ').append(rules.getDifficulty().name().toLowerCase(Locale.ROOT));
		}
		return sb.toString();
	}

	private static boolean isOurs(String token)
	{
		for (String t : GAME_TOKENS)
		{
			if (t.equals(token))
			{
				return true;
			}
		}
		return false;
	}
}
