package com.trappychinchompa.duel;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelResultTextTest
{
	private static final String ME = "Andrew";
	private static final String THEM = "Zezima";
	private static final DuelRules HARD_BS3 = DuelRules.parse("h-bs3");

	private static DuelOutcome outcome(String winner, DuelOutcome.Reason reason)
	{
		return new DuelOutcome(winner, reason, List.of(
			new DuelOutcome.GameLine(1, 5, 8, false),
			new DuelOutcome.GameLine(2, 9, 8, false),
			new DuelOutcome.GameLine(3, 2, 8, false)), "best 9 to 8");
	}

	private static void ascii(String s)
	{
		for (char c : s.toCharArray())
		{
			assertTrue(c < 128, "non-ascii in: " + s);
		}
	}

	@Test
	@DisplayName("a plain win and loss name the opponent, the score line and the rules")
	void winAndLoss()
	{
		String win = DuelResultText.chatLine(outcome(ME, DuelOutcome.Reason.WIN), ME, THEM, HARD_BS3);
		assertEquals("Duel vs Zezima: you won, best 9 to 8 (Hard, best round of 3).", win);
		String loss = DuelResultText.chatLine(outcome(THEM, DuelOutcome.Reason.WIN), ME, THEM, HARD_BS3);
		assertEquals("Duel vs Zezima: you lost, best 9 to 8 (Hard, best round of 3).", loss);
		ascii(win);
		ascii(loss);
	}

	@Test
	@DisplayName("forfeits, failed checks and disconnects say who did what")
	void forfeitsAndCheats()
	{
		assertEquals("Duel vs Zezima: Zezima forfeited, you win.",
			DuelResultText.chatLine(outcome(ME, DuelOutcome.Reason.FORFEIT), ME, THEM, HARD_BS3));
		assertEquals("Duel vs Zezima: you forfeited, Zezima wins.",
			DuelResultText.chatLine(outcome(THEM, DuelOutcome.Reason.FORFEIT), ME, THEM, HARD_BS3));
		assertEquals("Duel vs Zezima: their score did not check out, you win.",
			DuelResultText.chatLine(outcome(ME, DuelOutcome.Reason.CHEAT), ME, THEM, HARD_BS3));
		assertEquals("Duel vs Zezima: your score did not check out, Zezima wins.",
			DuelResultText.chatLine(outcome(THEM, DuelOutcome.Reason.CHEAT), ME, THEM, HARD_BS3));
		assertEquals("Duel vs Zezima: Zezima ran out of time, you win.",
			DuelResultText.chatLine(outcome(ME, DuelOutcome.Reason.DISCONNECT), ME, THEM, HARD_BS3));
	}

	@Test
	@DisplayName("no contest and disputed results record no winner")
	void noWinner()
	{
		assertEquals("Duel vs Zezima: no contest.",
			DuelResultText.chatLine(outcome(null, DuelOutcome.Reason.NO_CONTEST), ME, THEM, HARD_BS3));
		assertEquals("Duel vs Zezima: disputed result, no winner.",
			DuelResultText.chatLine(outcome(null, DuelOutcome.Reason.DISPUTED), ME, THEM, HARD_BS3));
	}

	@Test
	@DisplayName("the card headline is short and shouts")
	void headlines()
	{
		assertEquals("YOU WIN!", DuelResultText.headline(outcome(ME, DuelOutcome.Reason.WIN), ME));
		assertEquals("YOU LOSE", DuelResultText.headline(outcome(THEM, DuelOutcome.Reason.WIN), ME));
		assertEquals("NO CONTEST", DuelResultText.headline(outcome(null, DuelOutcome.Reason.NO_CONTEST), ME));
		assertEquals("DISPUTED", DuelResultText.headline(outcome(null, DuelOutcome.Reason.DISPUTED), ME));
	}

	@Test
	@DisplayName("the reason line under the headline explains anything that was not a plain win")
	void reasonLines()
	{
		assertEquals("", DuelResultText.reasonLine(outcome(ME, DuelOutcome.Reason.WIN), ME, THEM));
		assertEquals("Zezima forfeited", DuelResultText.reasonLine(outcome(ME, DuelOutcome.Reason.FORFEIT), ME, THEM));
		assertEquals("You forfeited", DuelResultText.reasonLine(outcome(THEM, DuelOutcome.Reason.FORFEIT), ME, THEM));
		assertEquals("Their score did not check out", DuelResultText.reasonLine(outcome(ME, DuelOutcome.Reason.CHEAT), ME, THEM));
		assertEquals("Zezima ran out of time", DuelResultText.reasonLine(outcome(ME, DuelOutcome.Reason.DISCONNECT), ME, THEM));
		assertEquals("Nobody showed up", DuelResultText.reasonLine(outcome(null, DuelOutcome.Reason.NO_CONTEST), ME, THEM));
	}
}
