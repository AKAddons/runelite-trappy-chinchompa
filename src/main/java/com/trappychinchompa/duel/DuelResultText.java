package com.trappychinchompa.duel;

/** Player-facing words for how a duel ended. ASCII only. */
public final class DuelResultText
{
	private DuelResultText()
	{
	}

	private static boolean iWon(DuelOutcome outcome, String me)
	{
		return outcome.getWinner() != null && Names.same(outcome.getWinner(), me);
	}

	/** One chatbox line: "Duel vs Zezima: you won, best 9 to 8 (Hard, best round of 3)." */
	public static String chatLine(DuelOutcome outcome, String me, String opponent, DuelRules rules)
	{
		String head = "Duel vs " + opponent + ": ";
		boolean won = iWon(outcome, me);
		switch (outcome.getReason())
		{
			case WIN:
				return head + (won ? "you won" : "you lost") + ", " + outcome.getScoreLine()
					+ " (" + rules.describe() + ").";
			case FORFEIT:
				return head + (won ? opponent + " forfeited, you win." : "you forfeited, " + opponent + " wins.");
			case CHEAT:
				return head + (won ? "their score did not check out, you win."
					: "your score did not check out, " + opponent + " wins.");
			case DISCONNECT:
				return head + (won ? opponent + " ran out of time, you win." : "you ran out of time, " + opponent + " wins.");
			case NO_CONTEST:
				return head + "no contest.";
			case DRAW:
				return head + "a draw, " + outcome.getScoreLine() + " (" + rules.describe() + ").";
			case DISPUTED:
			default:
				return head + "disputed result, no winner.";
		}
	}

	public static String headline(DuelOutcome outcome, String me)
	{
		switch (outcome.getReason())
		{
			case NO_CONTEST:
				return "NO CONTEST";
			case DISPUTED:
				return "DISPUTED";
			case DRAW:
				return "DRAW";
			default:
				return iWon(outcome, me) ? "YOU WIN!" : "YOU LOSE";
		}
	}

	/** The headline in strip case. */
	public static String headlineTitle(DuelOutcome outcome, String me)
	{
		switch (outcome.getReason())
		{
			case NO_CONTEST:
				return "No Contest";
			case DISPUTED:
				return "Disputed";
			case DRAW:
				return "Draw";
			default:
				return iWon(outcome, me) ? "You Win!" : "You Lose";
		}
	}

	/** Empty for a plain win; otherwise why it ended the way it did. */
	public static String reasonLine(DuelOutcome outcome, String me, String opponent)
	{
		boolean won = iWon(outcome, me);
		switch (outcome.getReason())
		{
			case FORFEIT:
				return won ? opponent + " forfeited" : "You forfeited";
			case CHEAT:
				return won ? "Their score did not check out" : "Your score did not check out";
			case DISCONNECT:
				return won ? opponent + " ran out of time" : "You ran out of time";
			case NO_CONTEST:
				return "Nobody showed up";
			case DISPUTED:
				return "The two clients disagree";
			case DRAW:
				return "Ten games and still level";
			case WIN:
			default:
				return "";
		}
	}
}
