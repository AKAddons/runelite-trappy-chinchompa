package com.trappychinchompa.duel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Duels from the plugin's side: the relay connection, the challenge
 * lobby (sent / incoming / queued for compete), and the ledger of duels
 * in flight. Driven on the EDT; talks back through notices and a
 * repaint hook.
 */
public final class DuelController implements RelayClient.Lobby
{
	/** The game id every message and room carries. */
	public static final String GAME = "tc1";
	static final long TOUCH_EVERY_MS = 60_000;
	/** Matchmaking gives up after this; the player retries by hand. */
	static final long QUEUE_LIMIT_MS = 30_000;

	public enum Phase
	{
		IDLE,
		/** Our challenge is out; waiting for their answer. */
		SENT,
		/** Their challenge is in; accept or decline. */
		INCOMING,
		/** Waiting in the compete queue. */
		QUEUED
	}

	private final RelayClient relay;
	private final BooleanSupplier blocked;
	private final Consumer<String> notice;
	private final Runnable changed;
	/** The canvas is handed a record whose next game has its seed. */
	private final Consumer<DuelRecord> ready;
	/** Names whose challenges are declined without a word. */
	private final Supplier<Set<String>> blockList;
	private String pendingPlay;

	private DuelLedger ledger = new DuelLedger("");
	private Phase phase = Phase.IDLE;
	private String opponent;
	private DuelRules rules;
	private String playingId;
	private long lastTouch;
	private long queuedAt;
	/** The rules of a compete that found nobody, until retried or dismissed. */
	private DuelRules noMatch;
	private final Map<String, List<Map<String, Object>>> boards = new HashMap<>();
	private final Map<String, List<Map<String, Object>>> ranks = new HashMap<>();
	/** The relay said the boards it sent are the developer-mode shadow. */
	private boolean devBoards;

	public DuelController(String relayUrl, RelayClient.Socket socket, Executor callbacks, RelayClient.Scheduler scheduler,
		BooleanSupplier blocked, Consumer<String> notice, Runnable changed, Consumer<DuelRecord> ready, Supplier<Set<String>> blockList,
		com.google.gson.Gson gson)
	{
		this.relay = new RelayClient(relayUrl, socket, callbacks, scheduler, this, gson);
		this.blocked = blocked;
		this.notice = notice;
		this.changed = changed;
		this.ready = ready;
		this.blockList = blockList;
	}

	// ---- relay lifecycle ----

	/** account = RuneLite's account hash as text; the relay binds the name to it. */
	public void connect(String displayName, String account, boolean hidden, boolean dev)
	{
		ledger = new DuelLedger(displayName);
		relay.connect(displayName, GAME, account, hidden, dev);
	}

	public void disconnect()
	{
		relay.disconnect();
		phase = Phase.IDLE;
		playingId = null;
		changed.run();
	}

	public boolean isOnline()
	{
		return relay.isOnline();
	}

	public String getName()
	{
		return relay.getName();
	}

	/** Once a second from the plugin: keeps the duel being played alive on the relay. */
	public void tick(long now)
	{
		if (playingId != null && now - lastTouch >= TOUCH_EVERY_MS)
		{
			lastTouch = now;
			relay.touch(playingId);
		}
		if (phase == Phase.QUEUED && queuedAt > 0 && now - queuedAt >= QUEUE_LIMIT_MS)
		{
			relay.uncompete();
			noMatch = rules;
			reset();
		}
	}

	/** A compete that timed out, waiting for Retry or Dismiss; null otherwise. */
	public DuelRules getNoMatch()
	{
		return noMatch;
	}

	public void retryCompete()
	{
		DuelRules again = noMatch;
		noMatch = null;
		if (again != null)
		{
			compete(again);
		}
	}

	public void dismissNoMatch()
	{
		noMatch = null;
		changed.run();
	}

	// ---- queries ----

	public Phase getPhase()
	{
		return phase;
	}

	public String getOpponent()
	{
		return opponent;
	}

	public DuelRules getRules()
	{
		return rules;
	}

	public DuelLedger getLedger()
	{
		return ledger;
	}

	public String getPlayingId()
	{
		return playingId;
	}

	public List<Map<String, Object>> board(String key)
	{
		return boards.get(key);
	}

	public List<Map<String, Object>> rankings(String key)
	{
		return ranks.get(key);
	}

	public boolean isDevBoards()
	{
		return devBoards;
	}

	/** The player whose challenge is waiting on us, for the right-click menu. */
	public String pendingChallenger()
	{
		return phase == Phase.INCOMING ? opponent : null;
	}

	/** Settled duels not yet announced, once each. */
	public List<DuelRecord> freshlySettled()
	{
		return ledger.freshlySettled();
	}

	// ---- what the player does ----

	public boolean challenge(String player, DuelRules newRules)
	{
		if (!relay.isOnline())
		{
			notice.accept("Duels need the relay: turn duels on in the plugin settings and log in.");
			return false;
		}
		if (phase != Phase.IDLE)
		{
			notice.accept("Answer the current challenge first.");
			return false;
		}
		if (blocked.getAsBoolean())
		{
			notice.accept("Duels are off while dev toggles are on.");
			return false;
		}
		if (player == null || player.trim().isEmpty() || Names.same(player, relay.getName()))
		{
			notice.accept(DuelCommand.USAGE);
			return false;
		}
		opponent = player.trim();
		rules = newRules;
		relay.challenge(opponent, GAME, rulesData(newRules));
		phase = Phase.SENT;
		changed.run();
		return true;
	}

	public boolean accept()
	{
		if (phase != Phase.INCOMING)
		{
			return false;
		}
		if (blocked.getAsBoolean())
		{
			notice.accept("Duels are off while dev toggles are on.");
			return false;
		}
		relay.accept(opponent, GAME, rulesData(rules));
		reset();
		return true;
	}

	public void decline()
	{
		if (phase == Phase.INCOMING)
		{
			relay.decline(opponent, GAME, rulesData(rules));
			reset();
		}
	}

	/** Withdraw a sent challenge, drop a pending one, or leave the queue. */
	public void cancel()
	{
		switch (phase)
		{
			case SENT:
				relay.decline(opponent, GAME, rulesData(rules));
				reset();
				break;
			case INCOMING:
				decline();
				break;
			case QUEUED:
				uncompete();
				break;
			default:
				break;
		}
	}

	public boolean compete(DuelRules competeRules)
	{
		if (!relay.isOnline())
		{
			notice.accept("Duels need the relay: turn duels on in the plugin settings and log in.");
			return false;
		}
		if (phase != Phase.IDLE || blocked.getAsBoolean())
		{
			return false;
		}
		rules = competeRules;
		opponent = null;
		noMatch = null;
		relay.compete(GAME, competeRules.canonical());
		phase = Phase.QUEUED;
		queuedAt = System.currentTimeMillis();
		changed.run();
		return true;
	}

	public void uncompete()
	{
		if (phase == Phase.QUEUED)
		{
			relay.uncompete();
			reset();
		}
	}

	/**
	 * Play pressed. With the seed already revealed the record comes back to
	 * start on; otherwise the relay is asked and the record arrives through
	 * the ready callback. Null means nothing to play right now.
	 */
	public DuelRecord play(String id)
	{
		DuelRecord r = ledger.get(id);
		if (r == null || r.isDone() || r.nextGame() == 0 || blocked.getAsBoolean() || id.equals(playingId))
		{
			// Already being played: a second Play must not restart the run.
			return null;
		}
		playingId = id;
		// The first tick touches right away; then once a minute.
		lastTouch = Long.MIN_VALUE / 2;
		if (r.hasSeed(r.nextGame()))
		{
			pendingPlay = null;
			changed.run();
			return r;
		}
		pendingPlay = id;
		relay.start(id, r.nextGame());
		changed.run();
		return null;
	}

	public void stopPlaying()
	{
		playingId = null;
		pendingPlay = null;
		changed.run();
	}

	/** A duel game finished on this client. */
	public void gameEnded(String id, int gameIndex, int score, List<Integer> flaps, int ticks)
	{
		playingId = null;
		relay.report(id, gameIndex, score, flaps, ticks);
		changed.run();
	}

	public void concede(String id)
	{
		if (ledger.get(id) != null)
		{
			relay.concede(id);
		}
		if (id.equals(playingId))
		{
			playingId = null;
		}
	}

	/** Concede whatever is being played, else the first duel still open. */
	public void concedeCurrent()
	{
		String id = playingId;
		if (id == null)
		{
			for (DuelRecord r : ledger.duels())
			{
				if (!r.isDone())
				{
					id = r.getId();
					break;
				}
			}
		}
		if (id != null)
		{
			concede(id);
		}
	}

	public void dismiss(String id)
	{
		ledger.dismiss(id);
		changed.run();
	}

	public void refreshBoards(String key)
	{
		if (relay.isOnline())
		{
			relay.board(GAME, key);
			relay.ranks(GAME, key);
		}
	}

	public void refreshHistory()
	{
		if (relay.isOnline())
		{
			relay.history(GAME);
		}
	}

	// ---- relay lobby events ----

	@Override
	public void connected(boolean online)
	{
		if (!online && phase != Phase.IDLE)
		{
			notice.accept("Lost the duel relay.");
			reset();
		}
		changed.run();
	}

	@Override
	public void challenge(String from, String game, Map<String, Object> data)
	{
		if (!GAME.equals(game))
		{
			return;
		}
		DuelRules theirRules = DuelRules.parse(RelayClient.str(data, "rules"));
		if (theirRules == null)
		{
			return;
		}
		if (phase != Phase.IDLE || isBlocked(from))
		{
			relay.decline(from, game, data);
			return;
		}
		opponent = from;
		rules = theirRules;
		phase = Phase.INCOMING;
		changed.run();
	}

	@Override
	public void answer(boolean accepted, String from, String game, Map<String, Object> data)
	{
		if (!GAME.equals(game) || opponent == null || !Names.same(from, opponent))
		{
			return;
		}
		if (phase == Phase.SENT)
		{
			if (!accepted)
			{
				notice.accept(from + " declined the duel.");
			}
			// Accepted: the relay pushes the record next; nothing starts here.
			reset();
		}
		else if (phase == Phase.INCOMING && !accepted)
		{
			notice.accept(from + " withdrew the challenge.");
			reset();
		}
	}

	@Override
	public void offline(String to)
	{
		if (phase == Phase.SENT && opponent != null && Names.same(to, opponent))
		{
			notice.accept(to + " is not online, or has duels turned off.");
			reset();
		}
	}

	@Override
	public void sent(String to)
	{
	}

	@Override
	public void share(String from, String game, String kind, Map<String, Object> data)
	{
	}

	@Override
	public void who(List<String> online)
	{
	}

	@Override
	public void duel(Map<String, Object> record)
	{
		ledger.update(record);
		if (phase == Phase.QUEUED)
		{
			phase = Phase.IDLE;
			queuedAt = 0;
		}
		afterLedger();
	}

	@Override
	public void sync(List<Map<String, Object>> duels, Map<String, Object> history)
	{
		ledger.sync(duels, history);
		afterLedger();
	}

	@Override
	public void queued(String queuedRules)
	{
		phase = Phase.QUEUED;
		if (queuedAt == 0)
		{
			queuedAt = System.currentTimeMillis();
		}
		changed.run();
	}

	@Override
	public void dequeued()
	{
		if (phase == Phase.QUEUED)
		{
			reset();
		}
	}

	@Override
	public void board(String key, List<Map<String, Object>> top, boolean dev)
	{
		boards.put(key, top);
		devBoards = dev;
		changed.run();
	}

	@Override
	public void ranks(String key, List<Map<String, Object>> top)
	{
		ranks.put(key, top);
		changed.run();
	}

	@Override
	public void history(Map<String, Object> history)
	{
		ledger.setHistory(history);
		changed.run();
	}

	// ---- plumbing ----

	private void afterLedger()
	{
		if (playingId != null && (ledger.get(playingId) == null || ledger.get(playingId).isDone()))
		{
			playingId = null;
			pendingPlay = null;
		}
		if (pendingPlay != null)
		{
			DuelRecord r = ledger.get(pendingPlay);
			if (r != null && r.nextGame() > 0 && r.hasSeed(r.nextGame()))
			{
				pendingPlay = null;
				ready.accept(r);
			}
		}
		changed.run();
	}

	private boolean isBlocked(String name)
	{
		Set<String> names = blockList == null ? null : blockList.get();
		if (names == null)
		{
			return false;
		}
		for (String n : names)
		{
			if (Names.same(n, name))
			{
				return true;
			}
		}
		return false;
	}

	private void reset()
	{
		phase = Phase.IDLE;
		opponent = null;
		rules = null;
		queuedAt = 0;
		changed.run();
	}

	private static Map<String, Object> rulesData(DuelRules r)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("rules", r == null ? "" : r.canonical());
		return m;
	}
}
