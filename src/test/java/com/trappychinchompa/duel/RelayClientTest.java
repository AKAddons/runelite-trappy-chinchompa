package com.trappychinchompa.duel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelayClientTest
{
	private static class Recorder implements RelayClient.Lobby
	{
		final List<String> events = new ArrayList<>();
		Map<String, Object> lastRecord;
		List<Map<String, Object>> lastList;

		@Override
		public void connected(boolean online)
		{
			events.add("online:" + online);
		}

		@Override
		public void challenge(String from, String game, Map<String, Object> data)
		{
			events.add("challenge:" + from + ":" + game + ":" + data.get("rules"));
		}

		@Override
		public void answer(boolean accepted, String from, String game, Map<String, Object> data)
		{
			events.add((accepted ? "accept:" : "decline:") + from);
		}

		@Override
		public void offline(String to)
		{
			events.add("offline:" + to);
		}

		@Override
		public void sent(String to)
		{
			events.add("sent:" + to);
		}

		@Override
		public void share(String from, String game, String kind, Map<String, Object> data)
		{
			events.add("share:" + kind);
		}

		@Override
		public void who(List<String> online)
		{
			events.add("who:" + online);
		}

		@Override
		public void duel(Map<String, Object> record)
		{
			events.add("duel:" + record.get("id"));
			lastRecord = record;
		}

		@Override
		public void sync(List<Map<String, Object>> duels, Map<String, Object> history)
		{
			events.add("sync:" + duels.size() + ":" + (history == null ? "-" : history.get("wins")));
			lastList = duels;
		}

		@Override
		public void queued(String rules)
		{
			events.add("queued:" + rules);
		}

		@Override
		public void dequeued()
		{
			events.add("dequeued");
		}

		@Override
		public void board(String key, List<Map<String, Object>> top, boolean dev)
		{
			events.add("board:" + key + ":" + top.size() + (dev ? ":dev" : ""));
			lastList = top;
		}

		@Override
		public void ranks(String key, List<Map<String, Object>> top)
		{
			events.add("ranks:" + key + ":" + top.size());
		}

		@Override
		public void history(Map<String, Object> history)
		{
			events.add("history:" + history.get("wins"));
		}
	}

	private static Map<String, Object> map(Object... kv)
	{
		return FakeRelaySocket.map(kv);
	}

	private final FakeRelaySocket socket = new FakeRelaySocket();
	private final Recorder lobby = new Recorder();
	private final List<Long> delays = new ArrayList<>();
	private final RelayClient client = new RelayClient("wss://relay.test/ws", socket, Runnable::run,
		(delayMs, task) -> delays.add(delayMs), lobby, new com.google.gson.Gson());

	private String last(List<String> l)
	{
		return l.isEmpty() ? null : l.get(l.size() - 1);
	}

	@Test
	@DisplayName("connecting says hello with the name and game once the socket is up")
	void helloOnOpen()
	{
		client.connect("Andrew", "tc1", "12345", false, true);
		assertEquals(List.of("wss://relay.test/ws"), socket.opened);
		socket.up();
		assertEquals("hello", socket.last().get("t"));
		assertEquals("Andrew", socket.last().get("name"));
		assertEquals("tc1", socket.last().get("game"));
		assertEquals("12345", socket.last().get("account"));
		assertEquals(false, socket.last().get("hidden"));
		assertEquals(true, socket.last().get("dev"));
		socket.relay(map("t", "welcome", "name", "Andrew", "v", 1));
		assertTrue(client.isOnline());
		assertEquals("online:true", last(lobby.events));
	}

	@Test
	@DisplayName("lobby traffic goes out as JSON and comes back as callbacks")
	void lobbyRoundTrip()
	{
		client.connect("Andrew", "tc1", "12345", false, true);
		socket.up();
		client.challenge("Zezima", "tc1", map("rules", "h-bs3"));
		assertEquals("challenge", socket.last().get("t"));
		assertEquals("h-bs3", ((Map<?, ?>) socket.last().get("data")).get("rules"));
		socket.relay(map("t", "challenge", "from", "Zezima", "game", "tc1", "data", map("rules", "n-av5")));
		assertEquals("challenge:Zezima:tc1:n-av5", last(lobby.events));
		socket.relay(map("t", "accept", "from", "Zezima", "game", "tc1", "data", map()));
		assertEquals("accept:Zezima", last(lobby.events));
		socket.relay(map("t", "offline", "to", "Zezima"));
		assertEquals("offline:Zezima", last(lobby.events));
	}

	@Test
	@DisplayName("the duel book: reports, verdicts, concede, touch, compete and lookups round-trip")
	void duelBook()
	{
		client.connect("Andrew", "tc1", "12345", false, true);
		socket.up();
		client.report("abc", 2, 7, List.of(0, 7, 14), 60);
		assertEquals("report", socket.last().get("t"));
		assertEquals(2.0, socket.last().get("game"));
		assertEquals(List.of(0.0, 7.0, 14.0), socket.last().get("flaps"));
		client.start("abc", 2);
		assertEquals("start", socket.last().get("t"));
		assertEquals(2.0, socket.last().get("game"));
		client.concede("abc");
		assertEquals("concede", socket.last().get("t"));
		client.touch("abc");
		assertEquals("touch", socket.last().get("t"));
		client.compete("tc1", "h-bs1");
		assertEquals("h-bs1", socket.last().get("rules"));
		client.uncompete();
		assertEquals("uncompete", socket.last().get("t"));
		client.board("tc1", "h");
		client.ranks("tc1", "h");
		client.history("tc1");
		assertEquals("history", socket.last().get("t"));

		socket.relay(map("t", "duel", "record", map("id", "abc", "status", "ACTIVE")));
		assertEquals("duel:abc", last(lobby.events));
		socket.relay(map("t", "sync", "duels", List.of(map("id", "abc")), "history", map("wins", 3)));
		assertEquals("sync:1:3.0", last(lobby.events));
		socket.relay(map("t", "queued", "rules", "h-bs1"));
		assertEquals("queued:h-bs1", last(lobby.events));
		socket.relay(map("t", "dequeued"));
		assertEquals("dequeued", last(lobby.events));
		socket.relay(map("t", "board", "key", "h", "top", List.of(map("name", "Zezima", "score", 12))));
		assertEquals("board:h:1", last(lobby.events));
		socket.relay(map("t", "board", "key", "h", "dev", true, "top", List.of()));
		assertEquals("board:h:0:dev", last(lobby.events));
		socket.relay(map("t", "ranks", "key", "h", "top", List.of()));
		assertEquals("ranks:h:0", last(lobby.events));
		socket.relay(map("t", "history", "wins", 2));
		assertEquals("history:2.0", last(lobby.events));
	}

	@Test
	@DisplayName("a dropped socket reconnects with backoff and re-hellos; disconnect stops it")
	void reconnects()
	{
		client.connect("Andrew", "tc1", "12345", false, true);
		socket.up();
		socket.down();
		assertEquals("online:false", last(lobby.events));
		assertEquals(List.of(1000L), delays);
		client.retry();
		socket.down();
		assertEquals(List.of(1000L, 2000L), delays);
		client.retry();
		socket.up();
		assertEquals("hello", socket.last().get("t"));
		socket.relay(map("t", "welcome", "name", "Andrew", "v", 1));
		socket.down();
		assertEquals(1000L, delays.get(delays.size() - 1));
		client.disconnect();
		assertFalse(client.isOnline());
		int before = delays.size();
		socket.down();
		assertEquals(before, delays.size());
	}

	@Test
	@DisplayName("junk from the relay is ignored")
	void junkIgnored()
	{
		client.connect("Andrew", "tc1", "12345", false, true);
		socket.up();
		socket.listener.onText("not json");
		socket.listener.onText("{\"t\":5}");
		socket.relay(map("t", "mystery"));
		assertTrue(lobby.events.isEmpty());
	}
}
