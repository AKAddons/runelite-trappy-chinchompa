package com.trappychinchompa.duel;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelLedgerTest
{
	private final DuelLedger ledger = new DuelLedger("Andrew");

	@Test
	@DisplayName("a pushed record is listed with its to-do; a settled one keeps its outcome until dismissed")
	void listAndDismiss()
	{
		ledger.update(DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(), List.of(), List.of(), List.of(), null, null));
		assertEquals(1, ledger.duels().size());
		assertEquals(DuelRecord.Todo.PLAY, ledger.duels().get(0).todo());
		ledger.update(DuelRecordTest.relayRecord("DONE", "h-bs3", 3, List.of(5, 9, 2), List.of(8, 8, 8), List.of(true, true, true), List.of(true, true, true), "Andrew", "WIN"));
		assertEquals(1, ledger.duels().size());
		assertTrue(ledger.duels().get(0).isDone());
		assertEquals(1, ledger.freshlySettled().size());
		assertTrue(ledger.freshlySettled().isEmpty(), "settled news is delivered once");
		ledger.dismiss("abc123");
		assertTrue(ledger.duels().isEmpty());
	}

	@Test
	@DisplayName("sync replaces the active list and remembers the record; a compete record is labelled")
	void sync()
	{
		Map<String, Object> a = DuelRecordTest.relayRecord("ACTIVE", "h-bs1", 1, List.of(), List.of(), List.of(), List.of(), null, null);
		a.put("id", "one");
		a.put("kind", "compete");
		ledger.sync(List.of(a), Map.of("wins", 2.0, "losses", 1.0));
		assertEquals(1, ledger.duels().size());
		assertTrue(ledger.duels().get(0).isCompete());
		assertEquals(2, ledger.getWins());
		assertEquals(1, ledger.getLosses());
		ledger.sync(List.of(), null);
		assertTrue(ledger.duels().isEmpty());
	}

	@Test
	@DisplayName("a game played from the ledger becomes a report for that duel and game")
	void playAndReport()
	{
		ledger.update(DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(5), List.of(), List.of(), List.of(), null, null));
		DuelRecord r = ledger.get("abc123");
		assertEquals(2, r.nextGame());
		assertEquals(Long.valueOf(2L), r.seed(2));
		assertNull(ledger.get("nope"));
	}
}
