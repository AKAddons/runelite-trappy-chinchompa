package com.trappychinchompa.duel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelRecordTest
{
	private static final Gson GSON = new Gson();
	private static final java.lang.reflect.Type MAP = new TypeToken<Map<String, Object>>()
	{
	}.getType();

	/** A record as the relay sends it, with the given reports and verdicts. */
	static Map<String, Object> relayRecord(String status, String rules, int stretchEnd,
		List<Integer> mine, List<Integer> theirs, List<Boolean> myVerdicts, List<Boolean> theirVerdicts, String winner, String reason)
	{
		StringBuilder reps = new StringBuilder();
		reps.append("\"Andrew\":[");
		for (int i = 0; i < mine.size(); i++)
		{
			reps.append(i == 0 ? "" : ",").append("{\"game\":").append(i + 1).append(",\"score\":").append(mine.get(i))
				.append(",\"flaps\":[0,7,14],\"ticks\":60}");
		}
		reps.append("],\"Zezima\":[");
		for (int i = 0; i < theirs.size(); i++)
		{
			reps.append(i == 0 ? "" : ",").append("{\"game\":").append(i + 1).append(",\"score\":").append(theirs.get(i))
				.append(",\"flaps\":[0,7,14],\"ticks\":70}");
		}
		reps.append("]");
		String result = winner == null && reason == null ? "null" : "{\"winner\":" + (winner == null ? "null" : "\"" + winner + "\"") + ",\"reason\":\"" + reason + "\"}";
		String json = "{\"id\":\"abc123\",\"game\":\"tc1\",\"kind\":\"duel\",\"rules\":\"" + rules + "\",\"players\":[\"Andrew\",\"Zezima\"],"
			+ "\"seeds\":{\"1\":\"1\",\"2\":\"2\"},\"created\":1000,\"deadline\":301000,"
			+ "\"reports\":{" + reps + "},\"verdicts\":{\"Andrew\":" + GSON.toJson(myVerdicts) + ",\"Zezima\":" + GSON.toJson(theirVerdicts) + "},"
			+ "\"stretchEnd\":" + stretchEnd + ",\"status\":\"" + status + "\",\"result\":" + result + ",\"games\":[]}";
		return GSON.fromJson(json, MAP);
	}

	@Test
	@DisplayName("a fresh record: my next game is 1, nothing to verify, time left from the deadline")
	void fresh()
	{
		DuelRecord r = DuelRecord.from(relayRecord("ACTIVE", "h-bs3", 3, List.of(), List.of(), List.of(), List.of(), null, null), "andrew");
		assertEquals("Zezima", r.getOpponent());
		assertEquals("h-bs3", r.getRules().canonical());
		assertEquals(1, r.nextGame());
		assertEquals(Long.valueOf(1L), r.seed(1));
		assertTrue(r.hasSeed(1));
		assertFalse(r.hasSeed(3));
		assertNull(r.seed(3));
		assertEquals(300_000L, r.timeLeft(1000));
		assertEquals(0L, r.timeLeft(400_000));
		assertEquals(DuelRecord.Todo.PLAY, r.todo());
		assertFalse(r.isDone());
	}

	@Test
	@DisplayName("once my stretch is in I wait; their reports are readable")
	void waitsOnceMyStretchIsIn()
	{
		DuelRecord r = DuelRecord.from(relayRecord("ACTIVE", "h-bs3", 3, List.of(5, 9, 2), List.of(8, 8), List.of(true), List.of(true, true, true), null, null), "Andrew");
		assertEquals(0, r.nextGame());
		assertEquals(DuelRecord.Todo.WAIT, r.todo());
		DuelRecord.Report theirs = r.theirReport(2);
		assertEquals(8, theirs.getScore());
		assertEquals(List.of(0, 7, 14), theirs.getFlaps());
		assertEquals(70, theirs.getTicks());
		assertEquals(9, r.myReport(2).getScore());
	}

	@Test
	@DisplayName("a sudden-death stretch asks for game 4 and labels it")
	void suddenDeath()
	{
		DuelRecord r = DuelRecord.from(relayRecord("ACTIVE", "n-bs3", 4, List.of(4, 4, 4), List.of(4, 4, 4), List.of(true, true, true), List.of(true, true, true), null, null), "Andrew");
		assertEquals(4, r.nextGame());
		assertFalse(r.hasSeed(4), "sudden death seed comes with start");
		assertTrue(r.isSuddenDeath(4));
		assertEquals("sudden death", r.gameLabel(4));
		assertEquals("game 2 of 3", r.gameLabel(2));
	}

	@Test
	@DisplayName("a settled record becomes an outcome with a You/Them table from my side")
	void outcome()
	{
		DuelRecord r = DuelRecord.from(relayRecord("DONE", "h-bs3", 3, List.of(5, 9, 2), List.of(8, 8, 8), List.of(true, true, true), List.of(true, true, true), "Andrew", "WIN"), "Andrew");
		assertTrue(r.isDone());
		assertEquals(DuelRecord.Todo.DONE, r.todo());
		DuelOutcome o = r.outcome();
		assertEquals("Andrew", o.getWinner());
		assertEquals(DuelOutcome.Reason.WIN, o.getReason());
		assertEquals(3, o.getGames().size());
		assertEquals(9, o.getGames().get(1).getMine());
		assertEquals(8, o.getGames().get(1).getTheirs());
		assertEquals("best 9 to 8", o.getScoreLine());
		DuelRecord theirs = DuelRecord.from(relayRecord("DONE", "h-bs3", 3, List.of(5, 9, 2), List.of(8, 8, 8), List.of(true, true, true), List.of(true, true, true), "Andrew", "WIN"), "Zezima");
		assertEquals("best 8 to 9", theirs.outcome().getScoreLine());
		assertEquals("Andrew", theirs.getOpponent());
		DuelRecord timeout = DuelRecord.from(relayRecord("DONE", "h-bs1", 1, List.of(5), List.of(), List.of(), List.of(true), "Andrew", "TIMEOUT"), "Zezima");
		assertEquals(DuelOutcome.Reason.DISCONNECT, timeout.outcome().getReason());
		DuelRecord nc = DuelRecord.from(relayRecord("DONE", "h-bs1", 1, List.of(), List.of(), List.of(), List.of(), null, "NO_CONTEST"), "Andrew");
		assertNull(nc.outcome().getWinner());
		assertEquals(DuelOutcome.Reason.NO_CONTEST, nc.outcome().getReason());
	}
}
