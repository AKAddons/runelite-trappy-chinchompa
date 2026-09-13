package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelControllerTest
{
	private static final String ME = "Andrew";
	private static final String THEM = "Zezima";
	private static final DuelRules HARD_BS3 = DuelRules.parse("h-bs3");

	private final FakeRelaySocket socket = new FakeRelaySocket();
	private final List<String> notices = new ArrayList<>();
	private final List<DuelRecord> ready = new ArrayList<>();
	private final java.util.Set<String> blockList = new java.util.HashSet<>();
	private boolean blocked;
	private int changes;

	private final DuelController c = new DuelController("wss://relay.test/ws", socket, Runnable::run, (d, task) -> { },
		() -> blocked, notices::add, () -> changes++, ready::add, () -> blockList);

	private static Map<String, Object> map(Object... kv)
	{
		return FakeRelaySocket.map(kv);
	}

	private void online()
	{
		c.connect(ME, "12345", false, false);
		socket.up();
		socket.relay(map("t", "welcome", "name", ME, "v", 1));
		assertTrue(c.isOnline());
	}

	private String last(List<String> l)
	{
		return l.isEmpty() ? null : l.get(l.size() - 1);
	}

	@Test
	@DisplayName("a challenge goes out; their accept ends the lobby phase and the pushed record lands in the ledger")
	void challengeAcceptRecord()
	{
		online();
		assertTrue(c.challenge(THEM, HARD_BS3));
		assertEquals(DuelController.Phase.SENT, c.getPhase());
		assertEquals("challenge", socket.last().get("t"));
		assertEquals("h-bs3", ((Map<?, ?>) socket.last().get("data")).get("rules"));
		socket.relay(map("t", "accept", "from", THEM, "game", DuelController.GAME, "data", map("rules", "h-bs3")));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		socket.relay(map("t", "duel", "record", DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(), List.of(), List.of(), List.of(), null, null)));
		assertEquals(1, c.getLedger().duels().size());
		assertEquals(DuelRecord.Todo.PLAY, c.getLedger().duels().get(0).todo());
	}

	@Test
	@DisplayName("an incoming challenge is accepted through the relay; nothing starts locally")
	void incomingAccept()
	{
		online();
		socket.relay(map("t", "challenge", "from", THEM, "game", DuelController.GAME, "data", map("rules", "n-av5")));
		assertEquals(DuelController.Phase.INCOMING, c.getPhase());
		assertEquals(THEM, c.pendingChallenger());
		assertTrue(c.accept());
		assertEquals("accept", socket.last().get("t"));
		assertEquals("n-av5", ((Map<?, ?>) socket.last().get("data")).get("rules"));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		assertNull(c.getPlayingId());
	}

	@Test
	@DisplayName("play with a revealed seed hands over the record; the finished game is reported; a minute in, a touch keeps it alive")
	void playAndReport()
	{
		online();
		socket.relay(map("t", "duel", "record", DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(5), List.of(), List.of(), List.of(), null, null)));
		DuelRecord r = c.play("abc123");
		assertNotNull(r, "seed 2 is in the test record");
		assertEquals(2, r.nextGame());
		assertEquals("abc123", c.getPlayingId());
		c.tick(1_000);
		assertEquals("touch", socket.last().get("t"));
		c.tick(30_000);
		assertEquals("touch", socket.last().get("t"));
		c.gameEnded("abc123", 2, 4, List.of(0, 7, 14, 21), 60);
		assertEquals("report", socket.last().get("t"));
		assertEquals(2.0, socket.last().get("game"));
		assertEquals(4.0, socket.last().get("score"));
		assertNull(c.getPlayingId());
		assertNull(c.play("nope"));
		c.play("abc123");
		assertNull(c.play("abc123"), "a second Play while playing is refused");
	}

	@Test
	@DisplayName("play without a seed asks the relay to start the game; the record that comes back with it is handed to the canvas")
	void playAsksForTheSeed()
	{
		online();
		Map<String, Object> rec = DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(5, 9), List.of(), List.of(), List.of(), null, null);
		socket.relay(map("t", "duel", "record", rec));
		assertNull(c.play("abc123"), "game 3 has no seed yet");
		assertEquals("start", socket.last().get("t"));
		assertEquals(3.0, socket.last().get("game"));
		assertEquals("abc123", c.getPlayingId());
		assertTrue(ready.isEmpty());
		Map<String, Object> revealed = DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(5, 9), List.of(), List.of(), List.of(), null, null);
		revealed.put("seeds", map("1", "1", "2", "2", "3", "777"));
		socket.relay(map("t", "duel", "record", revealed));
		assertEquals(1, ready.size());
		assertEquals(Long.valueOf(777L), ready.get(0).seed(3));
		socket.relay(map("t", "duel", "record", revealed));
		assertEquals(1, ready.size(), "handed over once");
	}

	@Test
	@DisplayName("a settled duel is announced once; a blocked player's challenge is declined without a word")
	void newsAndBlockList()
	{
		online();
		socket.relay(map("t", "duel", "record", DuelRecordTest.relayRecord("DONE", "h-bs1", 1, List.of(5), List.of(3), List.of(true), List.of(true), ME, "WIN")));
		assertEquals(1, c.freshlySettled().size());
		assertTrue(c.freshlySettled().isEmpty());
		c.dismiss("abc123");
		assertTrue(c.getLedger().duels().isEmpty());
		blockList.add("durial321");
		socket.relay(map("t", "challenge", "from", "Durial321", "game", DuelController.GAME, "data", map("rules", "h-bs1")));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		assertEquals("decline", socket.last().get("t"));
		assertEquals("Durial321", socket.last().get("to"));
	}

	@Test
	@DisplayName("compete queues at the given rules; a pushed record leaves the queue; cancel withdraws")
	void compete()
	{
		online();
		assertTrue(c.compete(DuelRules.parse("h-bs1")));
		assertEquals("compete", socket.last().get("t"));
		socket.relay(map("t", "queued", "rules", "h-bs1"));
		assertEquals(DuelController.Phase.QUEUED, c.getPhase());
		c.cancel();
		assertEquals("uncompete", socket.last().get("t"));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		c.compete(DuelRules.parse("h-bs1"));
		Map<String, Object> rec = DuelRecordTest.relayRecord("ACTIVE", "h-bs1", 1, List.of(), List.of(), List.of(), List.of(), null, null);
		rec.put("kind", "compete");
		socket.relay(map("t", "duel", "record", rec));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		assertTrue(c.getLedger().duels().get(0).isCompete());
	}

	@Test
	@DisplayName("matchmaking gives up after thirty seconds and waits for a retry")
	void queueTimesOut()
	{
		online();
		c.compete(DuelRules.parse("n-bs1"));
		socket.relay(map("t", "queued", "rules", "n-bs1"));
		long start = System.currentTimeMillis();
		c.tick(start + 10_000);
		assertEquals(DuelController.Phase.QUEUED, c.getPhase());
		c.tick(start + DuelController.QUEUE_LIMIT_MS + 1);
		assertEquals("uncompete", socket.last().get("t"));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		assertEquals("n-bs1", c.getNoMatch().canonical());
		c.retryCompete();
		assertEquals("compete", socket.last().get("t"));
		assertEquals(DuelController.Phase.QUEUED, c.getPhase());
		assertNull(c.getNoMatch());
		c.cancel();
		c.compete(DuelRules.parse("n-bs1"));
		c.tick(System.currentTimeMillis() + DuelController.QUEUE_LIMIT_MS + 1);
		c.dismissNoMatch();
		assertNull(c.getNoMatch());
	}

	@Test
	@DisplayName("sync fills the ledger and record; boards and rankings are cached per difficulty")
	void syncAndBoards()
	{
		online();
		socket.relay(map("t", "sync", "duels", List.of(DuelRecordTest.relayRecord("ACTIVE", "h-bs3", 3, List.of(), List.of(), List.of(), List.of(), null, null)),
			"history", map("wins", 4, "losses", 2)));
		assertEquals(1, c.getLedger().duels().size());
		assertEquals(4, c.getLedger().getWins());
		c.refreshBoards("h");
		assertEquals("ranks", socket.last().get("t"));
		socket.relay(map("t", "board", "key", "h", "top", List.of(map("name", THEM, "score", 12))));
		socket.relay(map("t", "ranks", "key", "h", "top", List.of(map("name", THEM, "rating", 1016))));
		assertEquals(1, c.board("h").size());
		assertEquals(1, c.rankings("h").size());
		assertNull(c.board("e"));
	}

	@Test
	@DisplayName("refusals: no relay, a dev toggle, a pending challenge, or challenging yourself")
	void refusals()
	{
		assertFalse(c.challenge(THEM, HARD_BS3));
		assertTrue(last(notices).toLowerCase().contains("relay"));
		online();
		blocked = true;
		assertFalse(c.challenge(THEM, HARD_BS3));
		blocked = false;
		assertFalse(c.challenge(ME, HARD_BS3));
		assertTrue(c.challenge(THEM, HARD_BS3));
		assertFalse(c.challenge("Durial321", HARD_BS3));
		socket.relay(map("t", "offline", "to", THEM));
		assertEquals(DuelController.Phase.IDLE, c.getPhase());
		assertTrue(changes > 0);
	}
}
