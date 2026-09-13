package com.trappychinchompa;

import com.google.common.base.MoreObjects;
import com.google.inject.Provides;
import com.trappychinchompa.duel.DuelChat;
import com.trappychinchompa.duel.DuelCommand;
import com.trappychinchompa.duel.DuelController;
import com.trappychinchompa.duel.DuelRecord;
import com.trappychinchompa.duel.DuelResultText;
import com.trappychinchompa.duel.DuelRules;
import com.trappychinchompa.duel.OkHttpSocket;
import com.trappychinchompa.game.BackgroundTheme;
import com.trappychinchompa.game.ChinSkin;
import com.trappychinchompa.game.HunterXp;
import com.trappychinchompa.ui.TrappyChinchompaPanel;
import java.awt.image.BufferedImage;
import java.security.SecureRandom;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Named;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.CommandExecuted;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.events.ProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.OkHttpClient;

@PluginDescriptor(
	name = "Trappy Chinchompa",
	description = "Flap a chinchompa through the box traps and train Anti-hunter, the skill of not getting caught.",
	tags = {"game", "minigame", "chinchompa", "flappy", "hunter", "fun"}
)
public class TrappyChinchompaPlugin extends Plugin
{
	private static final String KEY_HIGH_SCORE = "highScore";
	private static final String KEY_LIFETIME_XP = "lifetimeXpTenths";
	private static final String KEY_STAT_RUNS = "runs_";
	private static final String KEY_STAT_BEST = "best_";
	private static final String KEY_STAT_XP = "xpTenths_";
	private static final String KEY_STAT_POLES = "poles_";
	private static final String KEY_ACHIEVEMENTS = "achievements";
	private static final String KEY_ACHIEVEMENTS_DEBUG = "achievementsDebug";
	private static final String KEY_STREAK = "streak_";

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private com.google.gson.Gson gson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private PluginManager pluginManager;

	@Inject
	private TrappyChinchompaConfig config;

	@Inject
	@Named("developerMode")
	private boolean developerMode;

	@Inject
	private Client client;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	private DuelController duels;
	private DuelChat duelChat;
	private ScheduledFuture<?> duelTicker;
	/** Read on the client thread by the right-click menu. */
	private volatile String pendingChallenger;
	private volatile boolean duelIdle;
	/** Set once we have asked the relay to connect for this login. */
	private volatile boolean relayRequested;

	private TrappyChinchompaPanel panel;
	private NavigationButton navButton;

	@Provides
	TrappyChinchompaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TrappyChinchompaConfig.class);
	}

	@Override
	protected void startUp()
	{
		publishUnlockState();
		panel = new TrappyChinchompaPanel(this, itemManager, spriteManager, config);

		final BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Trappy Chinchompa")
			.icon(icon)
			.priority(9)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		startDuels();
		checkLoadoutLab();
	}

	private void startDuels()
	{
		duels = new DuelController(config.relayUrl(), new OkHttpSocket(okHttpClient), SwingUtilities::invokeLater,
			(delayMs, task) -> executor.schedule(() -> SwingUtilities.invokeLater(task), delayMs, TimeUnit.MILLISECONDS),
			this::isDuelBlocked, this::duelNotice, this::onDuelChanged, this::onDuelReady, this::blockedNames, gson);
		duelChat = new DuelChat(client, new DuelChat.Actions()
		{
			@Override
			public String pendingChallenger()
			{
				return pendingChallenger;
			}

			@Override
			public boolean canChallenge()
			{
				return duelIdle;
			}

			@Override
			public void challengeFromChat(String name)
			{
				if (duels != null)
				{
					duels.challenge(name, defaultRules());
				}
			}

			@Override
			public void acceptFromChat()
			{
				if (duels != null)
				{
					duels.accept();
				}
			}

			@Override
			public void declineFromChat()
			{
				if (duels != null)
				{
					duels.decline();
				}
			}
		});
		duelTicker = executor.scheduleAtFixedRate(() -> SwingUtilities.invokeLater(() ->
		{
			if (duels != null)
			{
				duels.tick(System.currentTimeMillis());
			}
		}), 1, 1, TimeUnit.SECONDS);
		relayRequested = false;
	}

	private void stopDuels()
	{
		if (duelTicker != null)
		{
			duelTicker.cancel(false);
			duelTicker = null;
		}
		final DuelController d = duels;
		duels = null;
		duelChat = null;
		pendingChallenger = null;
		duelIdle = false;
		relayRequested = false;
		if (d != null)
		{
			SwingUtilities.invokeLater(d::disconnect);
		}
	}

	/** The relay URL changed: rebuild on the EDT. */
	private void restartDuels()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (duels != null)
			{
				duels.disconnect();
			}
			if (duelTicker != null)
			{
				duelTicker.cancel(false);
			}
			if (panel != null)
			{
				startDuels();
			}
		});
	}

	private DuelRules defaultRules()
	{
		return new DuelRules(config.difficulty(), DuelRules.Metric.BEST_ROUND, 3);
	}

	private void onDuelChanged()
	{
		final DuelController d = duels;
		pendingChallenger = d == null ? null : d.pendingChallenger();
		duelIdle = d != null && d.isOnline() && d.getPhase() == DuelController.Phase.IDLE;
		announceSettled();
		if (panel != null)
		{
			panel.repaintGame();
		}
	}

	/** One chatbox line per settled duel, only this client sees it. */
	private void announceSettled()
	{
		final DuelController d = duels;
		if (d == null)
		{
			return;
		}
		for (DuelRecord r : d.freshlySettled())
		{
			if (r.getRules() != null)
			{
				postDuelChat(DuelResultText.chatLine(r.outcome(), r.getMe(), r.getOpponent(), r.getRules()));
			}
		}
	}

	public DuelController getDuels()
	{
		return duels;
	}

	/** Developer toggles and duels do not mix: no invincible or unlock-all duels. */
	public boolean isDuelBlocked()
	{
		return UnlockState.isDevUnlockAll() || (panel != null && panel.isSandbox());
	}

	/** The challenge form was confirmed (EDT). */
	public void challengeDuel(String them, DuelRules rules)
	{
		if (duels != null)
		{
			duels.challenge(them, rules);
		}
	}

	/** The relay revealed the seed for the game the player pressed Play on. */
	private void onDuelReady(DuelRecord record)
	{
		if (panel != null)
		{
			panel.playNow(record);
		}
	}

	private java.util.Set<String> blockedNames()
	{
		final java.util.Set<String> out = new java.util.HashSet<>();
		for (String n : config.duelBlocked().split(","))
		{
			if (!n.trim().isEmpty())
			{
				out.add(n.trim());
			}
		}
		return out;
	}

	/** Compete: one game at this difficulty against whoever is next in the queue. */
	public void competeDuel(Difficulty difficulty)
	{
		if (duels != null)
		{
			duels.compete(new DuelRules(difficulty, DuelRules.Metric.BEST_ROUND, 1));
		}
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (duelChat != null)
		{
			duelChat.onMenuEntryAdded(event);
		}
	}

	/** ::duel <player> trappy [games] [style] [difficulty], or ::duel accept | no | gg. Never reaches the game. */
	@Subscribe
	public void onCommandExecuted(CommandExecuted event)
	{
		if (duels == null || !"duel".equalsIgnoreCase(event.getCommand()))
		{
			return;
		}
		final DuelCommand cmd = DuelCommand.parse(event.getArguments(), config.difficulty());
		SwingUtilities.invokeLater(() ->
		{
			final DuelController d = duels;
			if (d == null)
			{
				return;
			}
			switch (cmd.getKind())
			{
				case CHALLENGE:
					d.challenge(cmd.getPlayer(), cmd.getRules());
					break;
				case ACCEPT:
					if (!d.accept())
					{
						duelNotice("No challenge is waiting.");
					}
					break;
				case DECLINE:
					d.cancel();
					break;
				case CONCEDE:
					d.concedeCurrent();
					break;
				case USAGE:
					duelNotice(DuelCommand.USAGE);
					break;
				case NOT_OURS:
				default:
					break;
			}
		});
	}

	/** Duels on and logged in: connect under the player's name (once). */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (duels == null || relayRequested || !config.duelsEnabled() || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		final Player me = client.getLocalPlayer();
		final long hash = client.getAccountHash();
		if (me == null || me.getName() == null || hash == -1L)
		{
			return;
		}
		relayRequested = true;
		final String name = Text.toJagexName(me.getName());
		final String account = com.trappychinchompa.duel.Hashes.account(hash);
		final boolean hidden = config.duelHidden();
		// Developer mode: two clients on one machine may pair, and nothing they play counts.
		final boolean dev = developerMode;
		final DuelController d = duels;
		SwingUtilities.invokeLater(() -> d.connect(name, account, hidden, dev));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN && duels != null)
		{
			relayRequested = false;
			final DuelController d = duels;
			SwingUtilities.invokeLater(d::disconnect);
		}
	}

	/** A line only this client sees, in the chatbox. Safe from any thread. */
	public void postDuelChat(String line)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.GAMEMESSAGE)
			.runeLiteFormattedMessage(line)
			.build());
	}

	private void duelNotice(String text)
	{
		postDuelChat("Trappy: " + text);
	}

	/**
	 * The cross-plugin easter egg: Lab Partner earns the moment Loadout
	 * Lab is seen installed and enabled - at our startup or whenever it
	 * starts later (external plugin load order is not guaranteed). Checks
	 * every same-named plugin, since a hub copy and a sideloaded dev copy
	 * can coexist.
	 */
	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		checkLoadoutLab();
	}

	/** Profile switch: every per-profile number changes under us. */
	@Subscribe
	public void onProfileChanged(ProfileChanged event)
	{
		publishUnlockState();
		checkLoadoutLab();
		if (panel != null)
		{
			final TrappyChinchompaPanel p = panel;
			SwingUtilities.invokeLater(p::reloadProgress);
		}
	}

	private void checkLoadoutLab()
	{
		if (loadEarnedAchievements().contains(Achievement.ID_LOADOUT_LAB))
		{
			return;
		}
		for (Plugin p : pluginManager.getPlugins())
		{
			if ("Loadout Lab".equals(p.getName()) && pluginManager.isPluginEnabled(p))
			{
				final Achievement earned = earnOne(Achievement.ID_LOADOUT_LAB);
				if (earned != null && panel != null)
				{
					final TrappyChinchompaPanel target = panel;
					SwingUtilities.invokeLater(() -> target.announceAchievement(earned));
				}
				return;
			}
		}
	}

	@Override
	protected void shutDown()
	{
		stopDuels();
		clientToolbar.removeNavigation(navButton);
		if (panel != null)
		{
			panel.dispose();
			panel = null;
		}
		navButton = null;
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!TrappyChinchompaConfig.GROUP.equals(event.getGroup()) || panel == null)
		{
			// ConfigChanged can fire on any thread; only ours matter here.
			return;
		}
		if ("duelsEnabled".equals(event.getKey()) && !config.duelsEnabled() && duels != null)
		{
			relayRequested = false;
			final DuelController d = duels;
			SwingUtilities.invokeLater(d::disconnect);
		}
		else if ("relayUrl".equals(event.getKey()))
		{
			restartDuels();
		}
		else if ("duelHidden".equals(event.getKey()) && duels != null)
		{
			// Reconnect so the relay hears the new hidden flag.
			relayRequested = false;
			final DuelController d = duels;
			SwingUtilities.invokeLater(d::disconnect);
		}
		publishUnlockState();
		final TrappyChinchompaPanel p = panel;
		SwingUtilities.invokeLater(p::refreshFromConfig);
	}

	/**
	 * Core's config-panel Reset button lands here after its own confirm
	 * dialog: the full factory reset. Declared settings are already back
	 * at defaults by now; this wipes the hidden keys too - progress AND
	 * the critter/zone picks.
	 */
	@Override
	public void resetConfiguration()
	{
		for (String key : progressKeys())
		{
			configManager.unsetConfiguration(TrappyChinchompaConfig.GROUP, key);
		}
		configManager.unsetConfiguration(TrappyChinchompaConfig.GROUP, "skin");
		configManager.unsetConfiguration(TrappyChinchompaConfig.GROUP, "background");
		publishUnlockState();
		if (panel != null)
		{
			panel.reloadProgress();
		}
		// Presence achievements re-earn immediately: the condition still holds.
		checkLoadoutLab();
	}

	/**
	 * The stats card's reset button lands here: confirm, then wipe
	 * progress only - settings (critter, zone, difficulty, xp drops)
	 * survive, and unset keys read back as a fresh install.
	 */
	public void confirmAndResetProgress()
	{
		final int answer = JOptionPane.showConfirmDialog(panel,
			"Wipe ALL Trappy Chinchompa progress?\n"
				+ "XP, levels, records, stats, streaks, and achievements are\n"
				+ "deleted for this profile. This cannot be undone.",
			"Reset progress", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (answer == JOptionPane.YES_OPTION)
		{
			for (String key : progressKeys())
			{
				configManager.unsetConfiguration(TrappyChinchompaConfig.GROUP, key);
			}
			publishUnlockState();
			if (panel != null)
			{
				panel.reloadProgress();
			}
			// Presence achievements re-earn immediately: the condition still holds.
			checkLoadoutLab();
		}
	}

	private List<String> progressKeys()
	{
		final List<String> keys = new ArrayList<>();
		keys.add(KEY_HIGH_SCORE);
		keys.add(KEY_LIFETIME_XP);
		keys.add(KEY_ACHIEVEMENTS);
		keys.add(KEY_ACHIEVEMENTS_DEBUG);
		for (Difficulty d : Difficulty.values())
		{
			keys.add(KEY_STAT_RUNS + d.name());
			keys.add(KEY_STAT_BEST + d.name());
			keys.add(KEY_STAT_POLES + d.name());
			keys.add(KEY_STAT_XP + d.name());
			for (int threshold : Achievement.STREAK_THRESHOLDS)
			{
				keys.add(KEY_STREAK + d.name() + "_" + threshold);
			}
		}
		return keys;
	}

	private void publishUnlockState()
	{
		UnlockState.update(loadHighScore(), HunterXp.levelForXpTenths(loadLifetimeXpTenths()),
			loadEarnedAchievements());
	}

	/**
	 * Critter/zone picks live under hidden config keys (no panel widgets -
	 * the start-screen pickers are the only selection surface). Typed
	 * ConfigManager reads unmarshal-or-null, so stale or hand-edited values
	 * simply fall back to the always-unlocked defaults; a stale LOCKED value
	 * is likewise neutralized at render time by the canvas fallback.
	 */
	public CritterChoice loadCritter()
	{
		return MoreObjects.firstNonNull(
			configManager.getConfiguration(TrappyChinchompaConfig.GROUP, "skin", CritterChoice.class),
			CritterChoice.PIXEL_CHIN);
	}

	public BackgroundChoice loadBackground()
	{
		return MoreObjects.firstNonNull(
			configManager.getConfiguration(TrappyChinchompaConfig.GROUP, "background", BackgroundChoice.class),
			BackgroundChoice.FELDIP_MARSH);
	}

	public void setCritter(CritterChoice choice)
	{
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP, "skin", choice.name());
	}

	public void setBackground(BackgroundChoice choice)
	{
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP, "background", choice.name());
	}

	public void setDifficulty(Difficulty difficulty)
	{
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP, "difficulty", difficulty.name());
	}

	/** Earned achievement ids, persisted as one CSV key. */
	public Set<String> loadEarnedAchievements()
	{
		return loadCsvSet(KEY_ACHIEVEMENTS);
	}

	/** Ids earned while the dev-unlock cheat was on: permanently tainted. */
	public Set<String> loadDebugAchievements()
	{
		return loadCsvSet(KEY_ACHIEVEMENTS_DEBUG);
	}

	private Set<String> loadCsvSet(String key)
	{
		return new LinkedHashSet<>(Text.fromCSV(MoreObjects.firstNonNull(
			configManager.getConfiguration(TrappyChinchompaConfig.GROUP, key), "")));
	}

	private void saveCsvSet(String key, Set<String> ids)
	{
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP, key, Text.toCSV(ids));
	}

	/**
	 * Fold candidate ids into the earned set; returns only the NEW ones,
	 * in catalog order, ready for the death panel and floats. Anything
	 * earned while the dev-unlock cheat is active is marked DEBUG forever.
	 */
	private List<Achievement> earn(List<String> candidateIds)
	{
		final Set<String> earned = loadEarnedAchievements();
		final List<Achievement> fresh = new ArrayList<>();
		for (String id : candidateIds)
		{
			if (Achievement.byId(id) != null && earned.add(id))
			{
				fresh.add(Achievement.byId(id));
			}
		}
		if (!fresh.isEmpty())
		{
			saveCsvSet(KEY_ACHIEVEMENTS, earned);
			if (UnlockState.isDevUnlockAll())
			{
				final Set<String> debug = loadDebugAchievements();
				for (Achievement a : fresh)
				{
					debug.add(a.getId());
				}
				saveCsvSet(KEY_ACHIEVEMENTS_DEBUG, debug);
			}
			publishUnlockState();
		}
		return fresh;
	}

	private Achievement earnOne(String id)
	{
		final List<Achievement> fresh = earn(Collections.singletonList(id));
		return fresh.isEmpty() ? null : fresh.get(0);
	}

	/**
	 * Live mid-run earn: score milestones land the instant the score hits
	 * the exact threshold, so the banner pops mid-flight rather than at
	 * the kaboom. Run-end evaluation dedups against these via the earned
	 * set, so nothing double-fires.
	 */
	public Achievement earnScoreAchievement(Difficulty difficulty, int score)
	{
		for (int t : Achievement.SCORE_THRESHOLDS)
		{
			if (score == t)
			{
				return earnOne(Achievement.scoreId(difficulty, t));
			}
		}
		return null;
	}

	/** UI hooks: one-off achievements, earned at most once each. */
	public Achievement markCritterChanged()
	{
		return earnOne(Achievement.ID_CHANGE_CRITTER);
	}

	public Achievement markZoneChanged()
	{
		return earnOne(Achievement.ID_CHANGE_ZONE);
	}

	public Achievement markStatsOpened()
	{
		return earnOne(Achievement.ID_OPEN_STATS);
	}

	public Achievement markAchievementsOpened()
	{
		return earnOne(Achievement.ID_OPEN_ACHIEVEMENTS);
	}

	public void openAchievements()
	{
		if (panel != null)
		{
			final TrappyChinchompaPanel p = panel;
			SwingUtilities.invokeLater(p::showAchievements);
		}
	}

	public boolean isDeveloperMode()
	{
		return developerMode;
	}

	/**
	 * Dev-client-only cheat (5x-tap Stats): flips every critter and zone to
	 * unlocked for testing. Session-only, never persisted, and inert
	 * without --developer-mode.
	 */
	public boolean toggleDevUnlocks()
	{
		if (!developerMode)
		{
			return false;
		}
		UnlockState.setDevUnlockAll(!UnlockState.isDevUnlockAll());
		return UnlockState.isDevUnlockAll();
	}

	private int loadStreak(Difficulty d, int threshold)
	{
		return loadInt(KEY_STREAK + d.name() + "_" + threshold);
	}

	private void saveStreak(Difficulty d, int threshold, int value)
	{
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP,
			KEY_STREAK + d.name() + "_" + threshold, String.valueOf(value));
	}

	public int loadHighScore()
	{
		return loadInt(KEY_HIGH_SCORE);
	}

	public long loadLifetimeXpTenths()
	{
		return MoreObjects.firstNonNull(
			configManager.getConfiguration(TrappyChinchompaConfig.GROUP, KEY_LIFETIME_XP, Long.class), 0L);
	}

	private int loadInt(String key)
	{
		return MoreObjects.firstNonNull(
			configManager.getConfiguration(TrappyChinchompaConfig.GROUP, key, Integer.class), 0);
	}

	/** Per-difficulty tallies for the stats card; zeros on a fresh install. */
	public DifficultyStats loadStats(Difficulty difficulty)
	{
		return new DifficultyStats(
			loadInt(KEY_STAT_RUNS + difficulty.name()),
			loadInt(KEY_STAT_BEST + difficulty.name()),
			MoreObjects.firstNonNull(configManager.getConfiguration(TrappyChinchompaConfig.GROUP,
				KEY_STAT_XP + difficulty.name(), Long.class), 0L),
			MoreObjects.firstNonNull(configManager.getConfiguration(TrappyChinchompaConfig.GROUP,
				KEY_STAT_POLES + difficulty.name(), Long.class), 0L));
	}

	/**
	 * Persist one finished run and fold it into the lifetime tally.
	 * Called from the EDT on the RUNNING -> DEAD transition; writes are
	 * per-death, never per-frame (ConfigManager flushes are not free).
	 * The run's locked-in difficulty scales the xp and gates the record.
	 */
	public RunResult recordRun(int score, Difficulty difficulty, BackgroundTheme runZone,
		ChinSkin runSkin)
	{
		final int previousBest = loadHighScore();
		final boolean newBest = difficulty.countsForRecords() && score > previousBest;
		final int best = newBest ? score : previousBest;
		if (newBest)
		{
			configManager.setConfiguration(TrappyChinchompaConfig.GROUP, KEY_HIGH_SCORE,
				String.valueOf(best));
		}

		final long before = loadLifetimeXpTenths();
		final long runXp = difficulty.xpTenthsForRun(score);
		final long lifetime = before + runXp;
		if (runXp > 0)
		{
			configManager.setConfiguration(TrappyChinchompaConfig.GROUP, KEY_LIFETIME_XP,
				String.valueOf(lifetime));
		}

		// Per-difficulty tallies (Easy keeps its own casual best here even
		// though it never touches the unlock record above).
		final DifficultyStats stats = loadStats(difficulty);
		final boolean newDifficultyBest = score > stats.getBest();
		final int difficultyBest = Math.max(stats.getBest(), score);
		configManager.setConfiguration(TrappyChinchompaConfig.GROUP,
			KEY_STAT_RUNS + difficulty.name(), String.valueOf(stats.getRuns() + 1));
		if (newDifficultyBest)
		{
			configManager.setConfiguration(TrappyChinchompaConfig.GROUP,
				KEY_STAT_BEST + difficulty.name(), String.valueOf(score));
		}
		if (runXp > 0)
		{
			configManager.setConfiguration(TrappyChinchompaConfig.GROUP,
				KEY_STAT_XP + difficulty.name(), String.valueOf(stats.getXpTenths() + runXp));
		}
		if (score > 0)
		{
			configManager.setConfiguration(TrappyChinchompaConfig.GROUP,
				KEY_STAT_POLES + difficulty.name(), String.valueOf(stats.getPoles() + score));
		}

		final int levelBefore = HunterXp.levelForXpTenths(before);
		final int levelAfter = HunterXp.levelForXpTenths(lifetime);
		final int leveledTo = levelAfter > levelBefore ? levelAfter : 0;

		// Achievements: score milestones, the zero-pole special, level
		// milestones, the zone/skin-bound feats, and the streak counters.
		final List<String> candidates = new ArrayList<>(Achievement.scoreIds(difficulty, score));
		if (score == 0)
		{
			candidates.add(Achievement.ID_ZERO_POLES);
		}
		candidates.addAll(Achievement.levelIds(levelAfter));
		if (runXp >= Achievement.PRIF_RUN_XP_TENTHS && runZone == BackgroundTheme.PRIFDDINAS)
		{
			candidates.add(Achievement.ID_PRIF_25K);
		}
		if (score >= 1 && runSkin == ChinSkin.CABBAGE && runZone == BackgroundTheme.INFERNO)
		{
			candidates.add(Achievement.ID_CABBAGE_INFERNO);
		}
		for (int threshold : Achievement.STREAK_THRESHOLDS)
		{
			final int streak = score >= threshold ? loadStreak(difficulty, threshold) + 1 : 0;
			saveStreak(difficulty, threshold, streak);
			candidates.addAll(Achievement.streakIds(difficulty, threshold, streak));
		}
		final List<Achievement> newAchievements = earn(candidates);

		UnlockState.update(best, levelAfter, loadEarnedAchievements());
		return new RunResult(score, runXp, lifetime, levelAfter, leveledTo,
			BackgroundTheme.newlyUnlocked(levelBefore, levelAfter),
			newBest ? ChinSkin.newlyUnlocked(previousBest, score) : null,
			newAchievements, difficultyBest, newDifficultyBest);
	}
}
