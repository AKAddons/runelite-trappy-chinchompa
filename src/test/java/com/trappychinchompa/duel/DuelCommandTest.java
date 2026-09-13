package com.trappychinchompa.duel;

import com.trappychinchompa.Difficulty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelCommandTest
{
	private static DuelCommand parse(String line)
	{
		return DuelCommand.parse(line.isEmpty() ? new String[0] : line.split(" "), Difficulty.HARD);
	}

	@Test
	@DisplayName("::duel <player> trappy [games] [style] [difficulty] builds the rules, defaults filled in")
	void challenges()
	{
		DuelCommand c = parse("testpurr trappy 3 best");
		assertEquals(DuelCommand.Kind.CHALLENGE, c.getKind());
		assertEquals("testpurr", c.getPlayer());
		assertEquals("h-bs3", c.getRules().canonical());

		assertEquals("e-av5", parse("test_purr tc 5 avg easy").getRules().canonical());
		assertEquals("test purr", parse("test_purr tc 5 avg easy").getPlayer());
		assertEquals("h-bs3", parse("zezima chin").getRules().canonical());
		assertEquals("n-av1", parse("zezima trappy normal average 1").getRules().canonical());
		assertEquals("h-bs5", parse("zezima Trappy 5").getRules().canonical());
	}

	@Test
	@DisplayName("a game token for another plugin is not ours; a missing one is a usage error")
	void gameToken()
	{
		assertEquals(DuelCommand.Kind.NOT_OURS, parse("zezima loadoutlab 3").getKind());
		assertEquals(DuelCommand.Kind.USAGE, parse("zezima").getKind());
		assertEquals(DuelCommand.Kind.USAGE, parse("zezima 3 best").getKind());
	}

	@Test
	@DisplayName("accept, no and gg are the typed equivalents of the strip buttons")
	void answers()
	{
		assertEquals(DuelCommand.Kind.ACCEPT, parse("accept").getKind());
		assertEquals(DuelCommand.Kind.ACCEPT, parse("yes").getKind());
		assertEquals(DuelCommand.Kind.DECLINE, parse("no").getKind());
		assertEquals(DuelCommand.Kind.DECLINE, parse("decline").getKind());
		assertEquals(DuelCommand.Kind.CONCEDE, parse("gg").getKind());
		assertEquals(DuelCommand.Kind.CONCEDE, parse("forfeit").getKind());
	}

	@Test
	@DisplayName("the generated command is short, and parses back to the same rules")
	void formatRoundTrips()
	{
		assertEquals("::duel testpurr trappy 1", DuelCommand.format("testpurr", DuelRules.parse("h-bs1"), Difficulty.HARD));
		assertEquals("::duel test_purr trappy 5 avg easy", DuelCommand.format("test purr", DuelRules.parse("e-av5"), Difficulty.HARD));
		String line = DuelCommand.format("Test Purr", DuelRules.parse("n-av3"), Difficulty.HARD);
		DuelCommand back = DuelCommand.parse(line.substring("::duel ".length()).split(" "), Difficulty.HARD);
		assertEquals("n-av3", back.getRules().canonical());
		assertEquals("Test Purr", back.getPlayer());
	}

	@Test
	@DisplayName("junk is a usage error with a readable usage line")
	void usage()
	{
		DuelCommand c = parse("");
		assertEquals(DuelCommand.Kind.USAGE, c.getKind());
		assertTrue(DuelCommand.USAGE.startsWith("Usage: ::duel <player> trappy"));
		assertEquals(DuelCommand.Kind.USAGE, parse("zezima trappy 7").getKind());
		assertEquals(DuelCommand.Kind.USAGE, parse("zezima trappy 3 sometimes").getKind());
	}
}
