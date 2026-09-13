package com.trappychinchompa.duel;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * One connection to the AKAddons relay: hello by display name, lobby
 * traffic (challenges, answers, shares, presence), the duel book (records,
 * reports, verdicts, compete, boards). The socket is a seam so tests
 * drive it with a fake; the plugin binds OkHttp. Every callback runs on
 * the given executor.
 */
public final class RelayClient
{
	public interface Socket
	{
		void open(String url, SocketListener listener);

		void send(String text);

		void close();
	}

	public interface SocketListener
	{
		void onOpen();

		void onText(String text);

		void onClosed();
	}

	/** Run a task later, on the same thread the callbacks use. */
	public interface Scheduler
	{
		void later(long delayMs, Runnable task);
	}

	/** What the relay tells the plugin. */
	public interface Lobby
	{
		void connected(boolean online);

		void challenge(String from, String game, Map<String, Object> data);

		void answer(boolean accepted, String from, String game, Map<String, Object> data);

		void offline(String to);

		void sent(String to);

		void share(String from, String game, String kind, Map<String, Object> data);

		void who(List<String> online);

		void duel(Map<String, Object> record);

		void sync(List<Map<String, Object>> duels, Map<String, Object> history);

		void queued(String rules);

		void dequeued();

		void board(String key, List<Map<String, Object>> top, boolean dev);

		void ranks(String key, List<Map<String, Object>> top);

		void history(Map<String, Object> history);
	}

	static final int PROTOCOL = 1;
	static final long BACKOFF_MIN_MS = 1_000;
	static final long BACKOFF_MAX_MS = 30_000;
	private static final Type MAP = new TypeToken<Map<String, Object>>()
	{
	}.getType();

	private final String url;
	private final Socket socket;
	private final Executor callbacks;
	private final Scheduler scheduler;
	private final Lobby lobby;
	/** The client's Gson (the hub forbids fresh instances). */
	private final Gson gson;

	private String name;
	private String game;
	private String account;
	private boolean hidden;
	private boolean dev;
	private boolean enabled;
	private boolean open;
	private boolean online;
	private long backoff = BACKOFF_MIN_MS;
	/** Bumped on every dial so a stale socket's late events are ignored. */
	private int generation;

	public RelayClient(String url, Socket socket, Executor callbacks, Scheduler scheduler, Lobby lobby, Gson gson)
	{
		this.gson = gson;
		this.url = url;
		this.socket = socket;
		this.callbacks = callbacks;
		this.scheduler = scheduler;
		this.lobby = lobby;
	}

	public String getName()
	{
		return name;
	}

	public boolean isOnline()
	{
		return online;
	}

	public boolean isEnabled()
	{
		return enabled;
	}

	/** Start (or restart under a new name) and keep reconnecting until disconnect(). */
	/** dev: a developer-mode client; its games pair freely and count for nothing. */
	public void connect(String displayName, String gameId, String accountId, boolean hideFromBoards, boolean developerMode)
	{
		name = displayName;
		game = gameId;
		account = accountId;
		hidden = hideFromBoards;
		dev = developerMode;
		enabled = true;
		backoff = BACKOFF_MIN_MS;
		if (open)
		{
			socket.close();
			open = false;
		}
		dial();
	}

	public void disconnect()
	{
		enabled = false;
		name = null;
		online = false;
		if (open)
		{
			open = false;
			socket.close();
		}
	}

	/** The scheduled reconnect fires. */
	public void retry()
	{
		if (enabled && !open)
		{
			dial();
		}
	}

	private void dial()
	{
		open = true;
		final int gen = ++generation;
		socket.open(url, new SocketListener()
		{
			@Override
			public void onOpen()
			{
				callbacks.execute(() ->
				{
					if (gen == generation)
					{
						opened();
					}
				});
			}

			@Override
			public void onText(String text)
			{
				callbacks.execute(() ->
				{
					if (gen == generation)
					{
						received(text);
					}
				});
			}

			@Override
			public void onClosed()
			{
				callbacks.execute(() ->
				{
					if (gen == generation)
					{
						closed();
					}
				});
			}
		});
	}

	// ---- lobby traffic ----

	public void challenge(String to, String game, Map<String, Object> data)
	{
		send(msg("challenge", "to", to, "game", game, "data", data));
	}

	public void accept(String to, String game, Map<String, Object> data)
	{
		send(msg("accept", "to", to, "game", game, "data", data));
	}

	public void decline(String to, String game, Map<String, Object> data)
	{
		send(msg("decline", "to", to, "game", game, "data", data));
	}

	public void share(String to, String game, String kind, Map<String, Object> data)
	{
		send(msg("send", "to", to, "game", game, "kind", kind, "data", data));
	}

	public void who(List<String> names)
	{
		send(msg("who", "names", new ArrayList<>(names)));
	}

	// ---- the duel book ----

	public void report(String duel, int gameIndex, int score, List<Integer> flaps, int ticks)
	{
		send(msg("report", "duel", duel, "game", gameIndex, "score", score, "flaps", new ArrayList<>(flaps), "ticks", ticks));
	}

	/** Play pressed: ask for this game's seed and start its clock. */
	public void start(String duel, int gameIndex)
	{
		send(msg("start", "duel", duel, "game", gameIndex));
	}

	public void concede(String duel)
	{
		send(msg("concede", "duel", duel));
	}

	public void touch(String duel)
	{
		send(msg("touch", "duel", duel));
	}

	public void compete(String game, String rules)
	{
		send(msg("compete", "game", game, "rules", rules));
	}

	public void uncompete()
	{
		send(msg("uncompete"));
	}

	public void board(String game, String key)
	{
		send(msg("board", "game", game, "key", key));
	}

	public void ranks(String game, String key)
	{
		send(msg("ranks", "game", game, "key", key));
	}

	public void history(String game)
	{
		send(msg("history", "game", game));
	}

	// ---- socket events (already on the callback thread) ----

	private void opened()
	{
		if (!enabled || !open)
		{
			return;
		}
		send(msg("hello", "name", name, "v", PROTOCOL, "game", game, "account", account, "hidden", hidden, "dev", dev));
	}

	private void closed()
	{
		boolean wasOpen = open;
		open = false;
		online = false;
		if (!enabled)
		{
			return;
		}
		if (wasOpen)
		{
			lobby.connected(false);
		}
		long delay = backoff;
		backoff = Math.min(BACKOFF_MAX_MS, backoff * 2);
		scheduler.later(delay, this::retry);
	}

	@SuppressWarnings("unchecked")
	private void received(String text)
	{
		Map<String, Object> m;
		try
		{
			m = gson.fromJson(text, MAP);
		}
		catch (RuntimeException e)
		{
			return;
		}
		if (m == null || !(m.get("t") instanceof String))
		{
			return;
		}
		String t = (String) m.get("t");
		String from = str(m, "from");
		String gameId = str(m, "game");
		switch (t)
		{
			case "welcome":
				online = true;
				backoff = BACKOFF_MIN_MS;
				lobby.connected(true);
				break;
			case "challenge":
				lobby.challenge(from, gameId, data(m));
				break;
			case "accept":
				lobby.answer(true, from, gameId, data(m));
				break;
			case "decline":
				lobby.answer(false, from, gameId, data(m));
				break;
			case "offline":
				lobby.offline(str(m, "to"));
				break;
			case "sent":
				lobby.sent(str(m, "to"));
				break;
			case "send":
				lobby.share(from, gameId, str(m, "kind"), data(m));
				break;
			case "who":
				lobby.who(strings(m.get("online")));
				break;
			case "duel":
				if (m.get("record") instanceof Map)
				{
					lobby.duel((Map<String, Object>) m.get("record"));
				}
				break;
			case "sync":
				lobby.sync(maps(m.get("duels")), m.get("history") instanceof Map ? (Map<String, Object>) m.get("history") : null);
				break;
			case "queued":
				lobby.queued(str(m, "rules"));
				break;
			case "dequeued":
				lobby.dequeued();
				break;
			case "board":
				lobby.board(str(m, "key"), maps(m.get("top")), Boolean.TRUE.equals(m.get("dev")));
				break;
			case "ranks":
				lobby.ranks(str(m, "key"), maps(m.get("top")));
				break;
			case "history":
				lobby.history(m);
				break;
			default:
				break;
		}
	}

	// ---- plumbing ----

	private void send(Map<String, Object> m)
	{
		if (open)
		{
			socket.send(gson.toJson(m));
		}
	}

	private static Map<String, Object> msg(String type, Object... kv)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("t", type);
		for (int i = 0; i < kv.length; i += 2)
		{
			m.put((String) kv[i], kv[i + 1]);
		}
		return m;
	}

	static String str(Map<String, Object> m, String key)
	{
		Object v = m.get(key);
		return v == null ? "" : v.toString();
	}

	@SuppressWarnings("unchecked")
	static Map<String, Object> data(Map<String, Object> m)
	{
		Object d = m.get("data");
		return d instanceof Map ? (Map<String, Object>) d : new HashMap<>();
	}

	@SuppressWarnings("unchecked")
	static List<Map<String, Object>> maps(Object o)
	{
		List<Map<String, Object>> out = new ArrayList<>();
		if (o instanceof List)
		{
			for (Object x : (List<?>) o)
			{
				if (x instanceof Map)
				{
					out.add((Map<String, Object>) x);
				}
			}
		}
		return out;
	}

	static List<String> strings(Object o)
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
}
