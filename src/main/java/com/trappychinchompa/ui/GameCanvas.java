package com.trappychinchompa.ui;

import com.trappychinchompa.Achievement;
import com.trappychinchompa.BackgroundChoice;
import com.trappychinchompa.CritterChoice;
import com.trappychinchompa.Difficulty;
import com.trappychinchompa.DifficultyStats;
import com.trappychinchompa.RunResult;
import com.trappychinchompa.TrappyChinchompaConfig;
import com.trappychinchompa.TrappyChinchompaPlugin;
import com.trappychinchompa.UnlockState;
import com.trappychinchompa.duel.DuelController;
import com.trappychinchompa.duel.DuelRecord;
import com.trappychinchompa.duel.DuelRun;
import com.trappychinchompa.duel.DuelViewport;
import com.trappychinchompa.game.BackgroundTheme;
import com.trappychinchompa.game.BoxTrap;
import com.trappychinchompa.game.ChinSkin;
import com.trappychinchompa.game.FlappyGame;
import com.trappychinchompa.game.HunterXp;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Renders and drives the game on the EDT: a Swing Timer ticks the headless
 * {@link FlappyGame} at ~60fps and paints it. All sprites are the player's
 * own client cache via ItemManager/SpriteManager (loaded async, with drawn
 * fallbacks until they arrive) - the repo bundles no game assets.
 */
class GameCanvas extends JPanel
{
	private static final int FRAME_MS = 16;
	/** One simulation step, in wall time; matches the timer's nominal rate. */
	private static final long STEP_NANOS = 16_000_000L;
	/** Ticks recovered after an EDT stall before the debt is dropped. */
	private static final int MAX_CATCHUP_TICKS = 4;
	private static final int EXPLOSION_TICKS = 26;
	private static final int LEVEL_BANNER_TICKS = 150;
	private static final int ACHIEVEMENT_BANNER_TICKS = 150;

	private static final Color WOOD = new Color(0x8a, 0x67, 0x3e);
	private static final Color WOOD_DARK = new Color(0x5e, 0x44, 0x27);
	private static final Color WOOD_LIGHT = new Color(0xa8, 0x84, 0x55);
	private static final Color MESH = new Color(0xd8, 0xc9, 0xa8, 90);
	// Shared with AchievementsPanel so the brand colours cannot drift.
	static final Color TEXT_YELLOW = new Color(0xff, 0xe3, 0x39);
	static final Color TEXT_ORANGE = new Color(0xff, 0x98, 0x1f);
	private static final Color TEXT_WHITE = new Color(0xf2, 0xf2, 0xf2);
	private static final Color TEXT_GREY = new Color(0xc0, 0xc0, 0xc0);
	private static final Color PANEL_BG = new Color(0x2b, 0x27, 0x1c, 225);
	private static final Color PANEL_BORDER = new Color(0x8f, 0x74, 0x3c);
	private static final Color CHIN_BODY = new Color(0xc2, 0x67, 0x3a);
	private static final Color CHIN_BELLY = new Color(0xee, 0xd9, 0xbd);
	static final Color DEBUG_RED = new Color(0xff, 0x3a, 0x3a);
	// Banner + explosion colours, hoisted like the rest (deriveFont rule).
	private static final Color BANNER_TOP = new Color(0x33, 0x28, 0x1b, 245);
	private static final Color BANNER_BOTTOM = new Color(0x17, 0x11, 0x0a, 245);
	private static final Color BANNER_EDGE = new Color(0x1a, 0x12, 0x08);
	private static final Color BANNER_GOLD = new Color(0xc9, 0xa2, 0x4b);
	private static final Color EXPLOSION_SMOKE = new Color(0x66, 0x5c, 0x50);
	private static final Color EXPLOSION_CORE = new Color(0xff, 0xd8, 0x7a);
	private static final Color SMOKE_WISP = new Color(0x8a, 0x84, 0x7a);

	/** One zone's colours: sky gradient, skyline silhouette, ground strips. */
	private static final class Palette
	{
		final Color skyTop;
		final Color skyBottom;
		final Color silhouette;
		final Color ground;
		final Color groundTop;
		final Color accent;

		Palette(int skyTop, int skyBottom, int silhouette, int silhouetteAlpha,
			int ground, int groundTop, int accent)
		{
			this.skyTop = new Color(skyTop);
			this.skyBottom = new Color(skyBottom);
			this.silhouette = new Color((silhouette & 0xffffff) | (silhouetteAlpha << 24), true);
			this.ground = new Color(ground);
			this.groundTop = new Color(groundTop);
			this.accent = new Color(accent);
		}
	}

	private static final Map<BackgroundTheme, Palette> PALETTES = new EnumMap<>(BackgroundTheme.class);

	static
	{
		PALETTES.put(BackgroundTheme.FELDIP_MARSH,
			new Palette(0x87b0c4, 0xcddfc9, 0x5c7452, 120, 0x4a5a38, 0x6d884a, 0x51636b));
		PALETTES.put(BackgroundTheme.LUMBRIDGE,
			new Palette(0x8fc0e8, 0xf2ead0, 0x6b7f96, 150, 0x577f3a, 0x74a04b, 0xe8e4d8));
		PALETTES.put(BackgroundTheme.VARROCK,
			new Palette(0x9aa7b8, 0xd8cfc0, 0x66605a, 150, 0x7b756c, 0x948d82, 0x5f5952));
		PALETTES.put(BackgroundTheme.AL_KHARID,
			new Palette(0x9cc4e0, 0xf0d896, 0xb08d4f, 160, 0xd9b36a, 0xe8c87e, 0xa8814a));
		PALETTES.put(BackgroundTheme.MORYTANIA,
			new Palette(0x4a4458, 0x71805e, 0x2e2a33, 190, 0x3a4030, 0x55644a, 0x6f8a52));
		PALETTES.put(BackgroundTheme.ARDOUGNE,
			new Palette(0xa0b8cc, 0xe8e6d8, 0x5f6e80, 150, 0x4f7a3c, 0x6f9c4e, 0xd8d0b8));
		PALETTES.put(BackgroundTheme.KOUREND,
			new Palette(0x5e5a80, 0xc0b0a8, 0x2e3038, 200, 0x5e6852, 0x788a62, 0x4a5244));
		PALETTES.put(BackgroundTheme.FREMENNIK,
			new Palette(0xa8c8d8, 0xeef4f4, 0x4e5e66, 170, 0x5c6e58, 0xdce8e8, 0x8fa8a0));
		PALETTES.put(BackgroundTheme.VARLAMORE,
			new Palette(0xe8a878, 0xf8dfb8, 0x8a5a40, 170, 0xc08a5c, 0xd8a878, 0x9c6a44));
		PALETTES.put(BackgroundTheme.WILDERNESS,
			new Palette(0x2b2226, 0x63403a, 0x1a1416, 220, 0x3b3330, 0x4a403c, 0xe06428));
		PALETTES.put(BackgroundTheme.PRIFDDINAS,
			new Palette(0xc2d0ee, 0xf2f8ff, 0x8a98c8, 170, 0x7ba888, 0x9cc4a8, 0xa8d8e8));
		PALETTES.put(BackgroundTheme.PANDEMONIUM,
			new Palette(0x3a4a6a, 0xe8926a, 0x2a2018, 215, 0x6a4a2e, 0x8a6440, 0x46586a));
		PALETTES.put(BackgroundTheme.INFERNO,
			new Palette(0x1a0d0a, 0x8a2f14, 0x0d0a08, 235, 0x241410, 0x3a1d12, 0xff6a1f));
	}

	private final TrappyChinchompaPlugin plugin;
	private final ItemManager itemManager;
	private final SpriteManager spriteManager;
	private final TrappyChinchompaConfig config;

	/** Solo: a fresh random game. In a duel: the DuelRun's seeded game on the fixed viewport. */
	private FlappyGame game = new FlappyGame(new Random());
	private DuelRun duelRun;
	private String duelId;
	private int duelGameIndex;
	private boolean duelReported;
	private final Timer timer = new Timer(FRAME_MS, this::onFrame);
	private long lastTickNanos;

	private final Font fontBig;
	private final Font fontTitle;
	private final Font fontBody;
	private final Font fontSmall;
	private final Font fontWatermark;

	/** Item sprites per critter, cached so mid-run upgrades swap instantly. */
	private final Map<ChinSkin, AsyncBufferedImage> skinImages = new EnumMap<>(ChinSkin.class);
	/** Display-format copies for fast per-frame blits (the trap sprite is
	 * drawn dozens of times per frame in the towers). */
	private final Map<ChinSkin, BufferedImage> skinSprites = new EnumMap<>(ChinSkin.class);
	private AsyncBufferedImage trapImage;
	private BufferedImage trapSprite;
	/** The Hunter skill sprite with a red no-sign: the Anti-hunter icon. */
	private BufferedImage antiHunterIcon;

	private FlappyGame.State lastState = FlappyGame.State.READY;
	/** Locked in when a run launches; config changes wait for the next run. */
	private Difficulty runDifficulty = Difficulty.NORMAL;
	/** The backdrop the run launched under, for zone-bound achievements. */
	private BackgroundTheme runZone = BackgroundTheme.FELDIP_MARSH;
	/** The critter flown this run, for skin-bound achievements. */
	private ChinSkin runSkin = ChinSkin.GREY_CHINCHOMPA;
	/** Non-null while the stats card overlays the ready screen. */
	private DifficultyStats[] statsSnapshot;
	// Config-derived state, recomputed on ConfigChanged / run end instead
	// of being re-read and re-parsed by the 60fps paint path.
	private CritterChoice cachedCritter = CritterChoice.GREY_CHINCHOMPA;
	private BackgroundChoice cachedBackground = BackgroundChoice.FELDIP_MARSH;
	private Difficulty cachedDifficulty = Difficulty.NORMAL;
	private int cachedDifficultyBest;
	private int cachedLevel = 1;
	// The sky + skyline never move; rasterize once per (theme, size).
	private BufferedImage backdropCache;
	private BackgroundTheme backdropTheme;
	private RunResult lastResult;
	/** Banner queue: each earned achievement gets its moment, in order. */
	private final Deque<BannerEntry> bannerQueue = new ArrayDeque<>();
	private BannerEntry activeBanner;
	private int bannerAge;
	/** How many achievements this run earned, live and at the kaboom. */
	private int runAchievementCount;
	private int bestScore;
	private long lifetimeXpTenths;
	private int levelBannerTicks;
	private double groundScroll;
	private int lastSeenScore;
	private final List<XpDrop> xpDrops = new ArrayList<>();
	private final Map<BackgroundTheme, ImageIcon> zoneThumbs = new EnumMap<>(BackgroundTheme.class);
	private static final int DEV_GESTURE_TAPS = 5;
	private final TapStreak statsTaps = new TapStreak();
	private final TapStreak zoneTaps = new TapStreak();
	/** Whether this run flew under trap invincibility: pure sandbox. */
	private boolean runSandbox;

	GameCanvas(TrappyChinchompaPlugin plugin, ItemManager itemManager,
		SpriteManager spriteManager, TrappyChinchompaConfig config)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;
		this.spriteManager = spriteManager;
		this.config = config;

		// Derived fonts allocate; make them once.
		fontBig = FontManager.getRunescapeBoldFont().deriveFont(26f);
		fontTitle = FontManager.getRunescapeBoldFont().deriveFont(20f);
		fontBody = FontManager.getRunescapeFont().deriveFont(16f);
		fontSmall = FontManager.getRunescapeSmallFont().deriveFont(16f);
		fontWatermark = FontManager.getRunescapeBoldFont().deriveFont(34f);

		setOpaque(true);
		setFocusable(true);

		bestScore = plugin.loadHighScore();
		lifetimeXpTenths = plugin.loadLifetimeXpTenths();
		refreshFromConfig();
		loadTrapImage();
		spriteManager.getSpriteAsync(SpriteID.SKILL_HUNTER, 0, img ->
			SwingUtilities.invokeLater(() ->
			{
				if (img != null)
				{
					antiHunterIcon = composeAntiIcon(img);
				}
				repaint();
			}));

		addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				requestFocusInWindow();
				if (duelRun != null)
				{
					handleDuelPlayClick(DuelViewport.fit(getWidth(), getHeight()).toDuel(e.getPoint()));
					return;
				}
				if (game.getState() == FlappyGame.State.READY)
				{
					if (statsSnapshot != null)
					{
						if (statsResetButton().contains(e.getPoint()))
						{
							// Confirm-then-wipe; a successful wipe closes
							// the card itself via reloadProgress.
							plugin.confirmAndResetProgress();
							repaint();
							return;
						}
						// Any other click puts the stats card away; taps on
						// the button itself still count toward the dev gesture.
						if (statsButton().contains(e.getPoint()))
						{
							registerStatsTap();
						}
						statsSnapshot = null;
						repaint();
						return;
					}
					if (critterButton().contains(e.getPoint()))
					{
						showPickerMenu(true);
						return;
					}
					if (zoneButton().contains(e.getPoint()))
					{
						// 5 quick taps = the dev invincibility gesture; a
						// rapid tap never opens the picker, so the popup
						// can't swallow the sequence.
						final int streak = zoneTaps.tap();
						if (streak >= DEV_GESTURE_TAPS)
						{
							zoneTaps.reset();
							if (plugin.isDeveloperMode())
							{
								game.setTrapInvincible(!game.isTrapInvincible());
								spawnXpDrop(game.isTrapInvincible()
									? "Dev invincibility: ON" : "Dev invincibility: OFF",
									TEXT_YELLOW);
							}
							return;
						}
						if (streak == 1)
						{
							showPickerMenu(false);
						}
						return;
					}
					if (statsButton().contains(e.getPoint()))
					{
						registerStatsTap();
						queueBanner(plugin.markStatsOpened());
						final Difficulty[] all = Difficulty.values();
						statsSnapshot = new DifficultyStats[all.length];
						for (int i = 0; i < all.length; i++)
						{
							statsSnapshot[i] = plugin.loadStats(all[i]);
						}
						repaint();
						return;
					}
					if (achievementsButton().contains(e.getPoint()))
					{
						// Marked before opening so the list already shows it;
						// the banner plays on the way back to the game.
						queueBanner(plugin.markAchievementsOpened());
						plugin.openAchievements();
						return;
					}
					if (difficultyButton().contains(e.getPoint()))
					{
						// A toggle: each click steps Easy -> Normal -> Hard.
						final Difficulty[] all = Difficulty.values();
						plugin.setDifficulty(all[(config.difficulty().ordinal() + 1) % all.length]);
						repaint();
						return;
					}
				}
				flap();
			}
		});
		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed SPACE"), "flap");
		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed ENTER"), "advance");
		getActionMap().put("advance", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				// Enter clears a finished game: the duel's Next game / Back, or a solo restart.
				if (duelRun != null)
				{
					advanceDuel();
				}
				else if (game.getState() == FlappyGame.State.DEAD && game.canRestart())
				{
					flap();
				}
			}
		});
		getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke("pressed UP"), "flap");
		getActionMap().put("flap", new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				flap();
			}
		});
		addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				if (duelRun == null)
				{
					game.setViewport(getWidth(), getHeight());
				}
			}
		});
	}

	void start()
	{
		if (duelRun == null)
		{
			game.setViewport(getWidth(), getHeight());
		}
		lastTickNanos = System.nanoTime();
		timer.start();
	}

	void stop()
	{
		timer.stop();
	}

	/** After a progress wipe: drop every earned number and start-screen card. */
	void reloadProgress()
	{
		bestScore = plugin.loadHighScore();
		lifetimeXpTenths = plugin.loadLifetimeXpTenths();
		lastResult = null;
		statsSnapshot = null;
		refreshFromConfig();
	}

	void refreshFromConfig()
	{
		// Difficulty deliberately NOT applied to the game here - it locks
		// at run start. These caches feed the paint path.
		cachedCritter = plugin.loadCritter();
		cachedBackground = plugin.loadBackground();
		cachedDifficulty = config.difficulty();
		cachedDifficultyBest = plugin.loadStats(cachedDifficulty).getBest();
		cachedLevel = HunterXp.levelForXpTenths(lifetimeXpTenths);
		// Warm every critter sprite; unlocks mid-run must swap instantly.
		for (ChinSkin skin : ChinSkin.values())
		{
			preloadSkin(skin);
		}
		repaint();
	}

	private void preloadSkin(ChinSkin skin)
	{
		if (skin.getResourceName() != null)
		{
			// Bundled original art loads synchronously, once. Small source
			// art (the 16px mascots) doubles up to item-sprite scale.
			if (!skinSprites.containsKey(skin))
			{
				BufferedImage art = ImageUtil.loadImageResource(
					TrappyChinchompaPlugin.class, skin.getResourceName());
				if (skin == ChinSkin.LAB_MASCOT)
				{
					art = prepareLabMascot(art);
				}
				if (art.getWidth() <= 16)
				{
					art = doublePixels(art);
				}
				skinSprites.put(skin, toDisplayFormat(art));
			}
			return;
		}
		if (skinImages.containsKey(skin))
		{
			return;
		}
		final AsyncBufferedImage img = itemManager.getImage(skin.getItemId());
		skinImages.put(skin, img);
		img.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			skinSprites.put(skin, toDisplayFormat(img));
			repaint();
		}));
	}

	/**
	 * Flight prep for the Loadout Lab bottle: its icon's corner star is
	 * cleared (a UI garnish, not part of the creature), colours deepen,
	 * and a dark outline bolds it against the pale skies.
	 */
	private static BufferedImage prepareLabMascot(BufferedImage src)
	{
		final BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(),
			BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = argb.createGraphics();
		try
		{
			g.drawImage(src, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		for (int y = 0; y < 5; y++)
		{
			for (int x = 11; x < argb.getWidth(); x++)
			{
				argb.setRGB(x, y, 0);
			}
		}
		final BufferedImage dark = new java.awt.image.RescaleOp(
			new float[]{0.76f, 0.76f, 0.76f, 1f}, new float[4], null).filter(argb, null);
		return ImageUtil.outlineImage(dark, new Color(0x25, 0x32, 0x1f));
	}

	/** Crisp 2x for pixel art - nearest neighbour, no smoothing blur. */
	private static BufferedImage doublePixels(BufferedImage src)
	{
		final BufferedImage out = new BufferedImage(src.getWidth() * 2, src.getHeight() * 2,
			BufferedImage.TYPE_INT_ARGB);
		final Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
				RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
			g.drawImage(src, 0, 0, out.getWidth(), out.getHeight(), null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	/**
	 * Copy an image into the display's native pixel format so per-frame
	 * drawImage calls blit directly instead of converting every time.
	 */
	private BufferedImage toDisplayFormat(BufferedImage src)
	{
		final GraphicsConfiguration gc = getGraphicsConfiguration();
		if (gc == null)
		{
			return src;
		}
		final BufferedImage out = gc.createCompatibleImage(src.getWidth(), src.getHeight(),
			Transparency.TRANSLUCENT);
		final Graphics2D g = out.createGraphics();
		try
		{
			g.drawImage(src, 0, 0, null);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	/**
	 * The critter on screen right now: the pick, unless a stale config
	 * pins something locked - then the newest unlock stands in.
	 */
	private ChinSkin currentSkin()
	{
		final ChinSkin picked = cachedCritter.toSkin();
		if (UnlockState.isDevUnlockAll())
		{
			return picked;
		}
		// The live score only counts toward unlocks on record-eligible runs.
		final int liveHigh = runDifficulty.countsForRecords()
			? Math.max(bestScore, game.getScore()) : bestScore;
		return picked.isUnlocked(liveHigh, UnlockState.getEarnedAchievements())
			? picked : ChinSkin.highestUnlocked(liveHigh, UnlockState.getEarnedAchievements());
	}

	private void loadTrapImage()
	{
		trapImage = itemManager.getImage(ItemID.HUNTING_BOX_TRAP);
		trapImage.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			trapSprite = toDisplayFormat(trapImage);
			repaint();
		}));
	}

	private void flap()
	{
		if (duelRun != null)
		{
			duelFlap();
			return;
		}
		if (game.getState() == FlappyGame.State.READY)
		{
			statsSnapshot = null;
			runAchievementCount = 0;
			// Difficulty and zone lock for the whole run - no easy-mode
			// bait and switch mid-flight.
			runDifficulty = config.difficulty();
			runZone = resolveTheme();
			runSkin = currentSkin();
			runSandbox = game.isTrapInvincible();
			game.setDifficulty(runDifficulty.getGapSize(), runDifficulty.getSpeed(),
				runDifficulty.getMaxGapStep());
		}
		game.flap();
	}

	/**
	 * Fixed-timestep pacing: the simulation advances by WALL TIME in 16ms
	 * steps (capped catch-up), so contention on the client's shared EDT
	 * costs smoothness at worst - never game speed. A stall longer than
	 * the cap drops the debt instead of spiralling.
	 */
	private void onFrame(ActionEvent e)
	{
		final long now = System.nanoTime();
		int steps = (int) ((now - lastTickNanos) / STEP_NANOS);
		if (steps <= 0)
		{
			return;
		}
		if (steps > MAX_CATCHUP_TICKS)
		{
			steps = MAX_CATCHUP_TICKS;
			lastTickNanos = now;
		}
		else
		{
			lastTickNanos += steps * STEP_NANOS;
		}
		for (int i = 0; i < steps; i++)
		{
			advanceOneTick();
		}
		// A settled death screen is pixel-identical frame to frame; skip
		// the repaint - but only once the explosion has finished, the
		// panel's first frame has been painted, and the chin has landed.
		final boolean idleDeath = game.getState() == FlappyGame.State.DEAD
			&& game.canRestart() && game.isSettled()
			&& game.getTicksSinceDeath() > EXPLOSION_TICKS
			&& xpDrops.isEmpty() && activeBanner == null && bannerQueue.isEmpty()
			&& levelBannerTicks == 0;
		if (!idleDeath)
		{
			repaint();
		}
	}

	private void advanceOneTick()
	{
		if (duelRun != null)
		{
			duelRun.tick();
		}
		else
		{
			game.tick();
		}
		if (game.getState() == FlappyGame.State.RUNNING || game.getState() == FlappyGame.State.READY)
		{
			groundScroll = (groundScroll + game.getSpeed()) % 24;
		}
		if (game.getState() == FlappyGame.State.RUNNING)
		{
			if (lastState != FlappyGame.State.RUNNING)
			{
				lastSeenScore = 0;
			}
			if (game.getScore() > lastSeenScore)
			{
				if (config.showXpDrops())
				{
					// Escalating whole-number drop per pole for this difficulty.
					spawnXpDrop("+" + formatTenths(
						runDifficulty.xpTenthsForPole(game.getScore())), TEXT_WHITE);
				}
				final int s = game.getScore();
				if (!runSandbox)
				{
					if (runDifficulty.countsForRecords() && s > bestScore)
					{
						// New-best territory (record-eligible runs only -
						// Easy must never announce what it cannot unlock):
						// crossing a wardrobe threshold unlocks it live.
						final ChinSkin fresh =
							ChinSkin.newlyUnlocked(Math.max(bestScore, s - 1), s);
						if (fresh != null)
						{
							spawnXpDrop(titleOf(fresh) + " unlocked!", TEXT_YELLOW);
						}
					}
					// Score-milestone achievements land the instant they happen.
					queueBanner(plugin.earnScoreAchievement(runDifficulty, s));
				}
				lastSeenScore = game.getScore();
			}
		}
		xpDrops.removeIf(d -> ++d.age > XpDrop.LIFETIME_TICKS);
		if (game.getState() == FlappyGame.State.DEAD && lastState == FlappyGame.State.RUNNING)
		{
			onRunEnded();
		}
		if (duelRun != null && duelRun.ended() && !duelReported)
		{
			duelReported = true;
			final DuelController d = duels();
			if (d != null && duelId != null)
			{
				d.gameEnded(duelId, duelGameIndex, duelRun.score(), duelRun.flapTicks(), duelRun.totalTicks());
			}
		}
		if (levelBannerTicks > 0)
		{
			levelBannerTicks--;
		}
		if (activeBanner == null && !bannerQueue.isEmpty())
		{
			activeBanner = bannerQueue.poll();
			bannerAge = 0;
		}
		else if (activeBanner != null && ++bannerAge > ACHIEVEMENT_BANNER_TICKS)
		{
			activeBanner = null;
		}
		lastState = game.getState();
	}

	void queueBanner(Achievement achievement)
	{
		if (achievement != null)
		{
			// Earned under the dev cheat = branded forever, banner included.
			bannerQueue.add(new BannerEntry(achievement, UnlockState.isDevUnlockAll()));
			runAchievementCount++;
		}
	}

	/** One queued unlock banner, with its earned-under-debug brand. */
	private static final class BannerEntry
	{
		final Achievement achievement;
		final boolean debug;

		BannerEntry(Achievement achievement, boolean debug)
		{
			this.achievement = achievement;
			this.debug = debug;
		}
	}

	/**
	 * Hidden dev gesture: five quick taps on Stats toggles unlock-everything
	 * for testing. Inert outside --developer-mode; the same taps untoggle.
	 */
	private void registerStatsTap()
	{
		if (statsTaps.tap() >= DEV_GESTURE_TAPS)
		{
			statsTaps.reset();
			if (plugin.isDeveloperMode())
			{
				final boolean on = plugin.toggleDevUnlocks();
				spawnXpDrop(on ? "Dev unlocks: ON" : "Dev unlocks: OFF", TEXT_YELLOW);
			}
		}
	}

	/** Counts quick consecutive taps for the hidden dev gestures. */
	private static final class TapStreak
	{
		private static final long WINDOW_MS = 600;

		private int taps;
		private long lastMs;

		/** Registers a tap; returns the streak length including it. */
		int tap()
		{
			final long now = System.currentTimeMillis();
			taps = now - lastMs <= WINDOW_MS ? taps + 1 : 1;
			lastMs = now;
			return taps;
		}

		void reset()
		{
			taps = 0;
		}
	}

	/**
	 * The OSRS-style unlock popup: a dark gold-trimmed strip that slides
	 * down from the top, holds, and fades - one per earned achievement.
	 */
	private void paintAchievementBanner(Graphics2D g2, int w)
	{
		if (activeBanner == null)
		{
			return;
		}
		final int bw = Math.min(w - 12, 220);
		final int bh = 48;
		final int bx = (w - bw) / 2;
		// Slide in over the first 14 ticks, fade over the last 26.
		final float in = Math.min(1f, bannerAge / 14f);
		final int by = (int) (-bh + (bh + 6) * (1 - (1 - in) * (1 - in)));
		final float alpha = bannerAge > ACHIEVEMENT_BANNER_TICKS - 26
			? Math.max(0f, (ACHIEVEMENT_BANNER_TICKS - bannerAge) / 26f) : 1f;

		final Graphics2D gb = (Graphics2D) g2.create();
		try
		{
			gb.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
			gb.setPaint(new GradientPaint(0, by, BANNER_TOP, 0, by + bh, BANNER_BOTTOM));
			gb.fillRect(bx, by, bw, bh);
			// Gold trim with corner studs, OSRS-popup style.
			gb.setColor(BANNER_EDGE);
			gb.setStroke(new BasicStroke(3f));
			gb.drawRect(bx, by, bw, bh);
			gb.setColor(BANNER_GOLD);
			gb.setStroke(new BasicStroke(1.4f));
			gb.drawRect(bx + 1, by + 1, bw - 2, bh - 2);
			for (int[] c : new int[][]{{bx, by}, {bx + bw, by}, {bx, by + bh}, {bx + bw, by + bh}})
			{
				gb.fillRect(c[0] - 2, c[1] - 2, 5, 5);
			}

			gb.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			shadowText(gb, "Achievement unlocked!", fontSmall, TEXT_ORANGE, w / 2, by + 18);
			gb.setColor(BANNER_GOLD);
			final int headerHalf = 74;
			sparkle(gb, w / 2 - headerHalf, by + 13);
			sparkle(gb, w / 2 + headerHalf, by + 13);
			shadowText(gb, activeBanner.achievement.getName(), fontBody, TEXT_WHITE, w / 2, by + 38);
			if (activeBanner.debug)
			{
				shadowText(gb, "DEBUG", fontSmall, DEBUG_RED, bx + bw - 24, by + 12);
			}
		}
		finally
		{
			gb.dispose();
		}
	}

	private void spawnXpDrop(String text, Color color)
	{
		final XpDrop drop = new XpDrop();
		drop.text = text;
		drop.color = color;
		drop.y = game.getChinY() - 26;
		xpDrops.add(drop);
	}

	private void onRunEnded()
	{
		if (runSandbox)
		{
			// Invincible flights are pure sandbox: nothing records, the
			// panel shows would-be numbers against unchanged tallies.
			lastResult = RunResult.sandbox(game.getScore(),
				runDifficulty.xpTenthsForRun(game.getScore()), lifetimeXpTenths,
				cachedLevel, plugin.loadStats(runDifficulty).getBest());
			return;
		}
		lastResult = plugin.recordRun(game.getScore(), runDifficulty, runZone, runSkin);
		bestScore = plugin.loadHighScore();
		lifetimeXpTenths = lastResult.getLifetimeXpTenths();
		refreshFromConfig();
		// Streaks, levels, and the zero-pole special resolve at the kaboom;
		// they banner right over the death screen.
		for (Achievement a : lastResult.getNewAchievements())
		{
			queueBanner(a);
		}
		if (lastResult.getLeveledTo() > 0)
		{
			levelBannerTicks = LEVEL_BANNER_TICKS;
		}
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		// No super.paintComponent: the panel is opaque and the backdrop +
		// ground cover every pixel, so the default clear is a wasted fill.
		final int w = getWidth();
		final int h = getHeight();
		if (w <= 0 || h <= 0)
		{
			return;
		}
		final Graphics2D g2 = (Graphics2D) g.create();
		try
		{
			if (duelRun != null)
			{
				// Duels simulate on a fixed 242x480 world; the panel shows
				// it letterboxed so both players see the same traps.
				final DuelViewport vp = DuelViewport.fit(w, h);
				g2.setColor(Color.BLACK);
				g2.fillRect(0, 0, w, h);
				g2.translate(vp.getOffsetX(), vp.getOffsetY());
				g2.scale(vp.getScale(), vp.getScale());
				g2.clipRect(0, 0, DuelRun.WIDTH, DuelRun.HEIGHT);
				paintWorld(g2, DuelRun.WIDTH, DuelRun.HEIGHT);
			}
			else
			{
				paintWorld(g2, w, h);
			}
		}
		finally
		{
			g2.dispose();
		}
	}

	private void paintWorld(Graphics2D g2, int w, int h)
	{
		final int floor = h - FlappyGame.GROUND_HEIGHT;
		final BackgroundTheme theme = resolveTheme();
		final Palette palette = PALETTES.get(theme);
		if (backdropCache == null || backdropTheme != theme
			|| backdropCache.getWidth() != w || backdropCache.getHeight() != floor)
		{
			final GraphicsConfiguration gc = getGraphicsConfiguration();
			backdropCache = gc != null
				? gc.createCompatibleImage(w, floor, Transparency.OPAQUE)
				: new BufferedImage(w, floor, BufferedImage.TYPE_INT_RGB);
			final Graphics2D bg = backdropCache.createGraphics();
			try
			{
				paintSky(bg, w, floor, theme, palette);
			}
			finally
			{
				bg.dispose();
			}
			backdropTheme = theme;
		}
		g2.drawImage(backdropCache, 0, 0, null);
		paintTraps(g2, floor);
		paintGround(g2, w, h, floor, theme, palette, (int) groundScroll);
		paintChin(g2);
		paintXpDrops(g2);
		paintHud(g2, w, h);
		paintAchievementBanner(g2, w);
		if (UnlockState.isDevUnlockAll() || game.isTrapInvincible())
		{
			paintDebugWatermark(g2, w, h);
		}
	}

	/** The zone shown right now: the pick, or the best unlocked if stale-locked. */
	private BackgroundTheme resolveTheme()
	{
		final BackgroundTheme pick = cachedBackground.toTheme();
		if (UnlockState.isDevUnlockAll())
		{
			return pick;
		}
		final Set<String> earned = UnlockState.getEarnedAchievements();
		return pick.isUnlocked(cachedLevel, earned)
			? pick : BackgroundTheme.highestUnlocked(cachedLevel, earned);
	}

	private static String titleOf(Enum<?> e)
	{
		return Text.titleCase(e);
	}

	private Rectangle critterButton()
	{
		return gridButton(0, 0);
	}

	private Rectangle zoneButton()
	{
		return gridButton(1, 0);
	}

	private Rectangle statsButton()
	{
		return gridButton(0, 1);
	}

	private Rectangle achievementsButton()
	{
		return gridButton(1, 1);
	}

	private Rectangle difficultyButton()
	{
		return new Rectangle(getWidth() / 2 - 70, getHeight() - 92, 140, 24);
	}

	/** Two rows of two buttons under the difficulty toggle. */
	private Rectangle gridButton(int col, int row)
	{
		final int bw = 100;
		final int gap = 6;
		final int x = getWidth() / 2 - (2 * bw + gap) / 2 + col * (bw + gap);
		return new Rectangle(x, getHeight() - 62 + row * 30, bw, 24);
	}

	// ---- duels: one game at a time, from the dock ----

	private DuelController duels()
	{
		return plugin.getDuels();
	}

	boolean isSandbox()
	{
		return game.isTrapInvincible();
	}

	void notice(String text)
	{
		spawnXpDrop(text, TEXT_YELLOW);
		repaint();
	}

	/** The dock pressed Play: the duel's next game on the fixed world. */
	void startDuelGame(DuelRecord record)
	{
		final int index = record.nextGame();
		if (index == 0 || record.getRules() == null)
		{
			return;
		}
		duelId = record.getId();
		duelGameIndex = index;
		duelRun = new DuelRun(record.seed(index), record.getRules());
		game = duelRun.game();
		duelReported = false;
		lastState = FlappyGame.State.READY;
		lastResult = null;
		statsSnapshot = null;
		xpDrops.clear();
		backdropCache = null;
		// The dock's button had the keyboard; Space must launch without a click first.
		requestFocusInWindow();
		repaint();
	}

	private DuelRecord duelRecord()
	{
		final DuelController d = duels();
		return d == null || duelId == null ? null : d.getLedger().get(duelId);
	}

	private void duelFlap()
	{
		if (duelRun.ended())
		{
			// Space after the kaboom does what the button does.
			advanceDuel();
			return;
		}
		if (game.getState() == FlappyGame.State.READY)
		{
			final DuelRecord r = duelRecord();
			statsSnapshot = null;
			runAchievementCount = 0;
			runDifficulty = r == null || r.getRules() == null ? config.difficulty() : r.getRules().getDifficulty();
			runZone = resolveTheme();
			runSkin = currentSkin();
			runSandbox = false;
		}
		duelRun.flap();
	}

	/** After a duel game: the next game of the stretch if there is one, else back to the start screen. */
	private void advanceDuel()
	{
		if (duelRun == null || !duelRun.ended() || game.getTicksSinceDeath() <= EXPLOSION_TICKS)
		{
			return;
		}
		final DuelRecord r = duelRecord();
		final DuelController d = duels();
		if (r != null && d != null && r.nextGame() > 0 && r.nextGame() != duelGameIndex)
		{
			// Next game: the relay reveals its seed, then the panel starts it.
			final DuelRecord next = d.play(r.getId());
			if (next != null)
			{
				startDuelGame(next);
			}
			return;
		}
		exitDuel();
	}

	private void handleDuelPlayClick(Point p)
	{
		if (duelRun.ended() && game.getTicksSinceDeath() > EXPLOSION_TICKS)
		{
			if (duelNextButton().contains(p))
			{
				advanceDuel();
			}
			return;
		}
		if (duelForfeitButton().contains(p))
		{
			final DuelController d = duels();
			if (d != null && duelId != null)
			{
				d.concede(duelId);
			}
			exitDuel();
			return;
		}
		duelFlap();
	}

	/** Back to the solo start screen with a fresh random game. */
	private void exitDuel()
	{
		final DuelController d = duels();
		if (d != null)
		{
			d.stopPlaying();
		}
		duelRun = null;
		duelId = null;
		game = new FlappyGame(new Random());
		game.setViewport(getWidth(), getHeight());
		lastState = FlappyGame.State.READY;
		lastResult = null;
		backdropCache = null;
		xpDrops.clear();
		refreshFromConfig();
	}

	private Rectangle duelForfeitButton()
	{
		return new Rectangle(DuelRun.WIDTH - 62, 6, 56, 20);
	}

	private Rectangle duelNextButton()
	{
		return new Rectangle(DuelRun.WIDTH / 2 - 62, DuelRun.HEIGHT - 78, 124, 22);
	}

	private void paintDuelHud(Graphics2D g2, int w, int h)
	{
		final DuelRecord r = duelRecord();
		final String them = r == null ? "?" : r.getOpponent();
		final String label = "vs " + them + " - " + (r == null ? "game " + duelGameIndex : r.gameLabel(duelGameIndex));
		final int cx = w / 2;
		switch (game.getState())
		{
			case READY:
				shadowText(g2, label, fontBody, TEXT_YELLOW, cx, 40);
				if (r != null && r.getRules() != null)
				{
					shadowText(g2, r.getRules().describe(), fontSmall, TEXT_GREY, cx, 60);
				}
				shadowText(g2, "Click / Space to launch", fontBody, TEXT_WHITE, cx, 84);
				paintPickerButton(g2, duelForfeitButton(), "Forfeit", TEXT_WHITE);
				break;
			case RUNNING:
				shadowText(g2, label, fontSmall, TEXT_GREY, cx - 20, 20);
				shadowText(g2, String.valueOf(game.getScore()), fontBig, TEXT_YELLOW, cx, 48);
				paintPickerButton(g2, duelForfeitButton(), "Forfeit", TEXT_WHITE);
				break;
			case DEAD:
			default:
				paintDeathPanel(g2, w, h);
				if (game.getTicksSinceDeath() > EXPLOSION_TICKS)
				{
					final boolean more = r != null && r.nextGame() > 0 && r.nextGame() != duelGameIndex;
					paintPickerButton(g2, duelNextButton(), more ? "Next game" : "Back", TEXT_YELLOW);
				}
				break;
		}
	}

	/** Lifetime tallies per difficulty, over the ready screen. */
	/** Card geometry shared by the painter and the click handler. */
	private Rectangle statsCardBounds()
	{
		final int pw = Math.min(getWidth() - 24, 210);
		final int ph = 232;
		return new Rectangle((getWidth() - pw) / 2, getHeight() / 2 - ph / 2 - 24, pw, ph);
	}

	/** The destructive button lives with the numbers it destroys. */
	private Rectangle statsResetButton()
	{
		final Rectangle card = statsCardBounds();
		return new Rectangle(getWidth() / 2 - 62, card.y + card.height - 32, 124, 22);
	}

	private void paintStatsCard(Graphics2D g2, int w, int h)
	{
		final Rectangle card = statsCardBounds();
		final int pw = card.width;
		final int ph = card.height;
		final int px = card.x;
		final int py = card.y;
		g2.setColor(PANEL_BG);
		g2.fillRoundRect(px, py, pw, ph, 10, 10);
		g2.setColor(PANEL_BORDER);
		g2.setStroke(new BasicStroke(2f));
		g2.drawRoundRect(px, py, pw, ph, 10, 10);

		final int cx = w / 2;
		int y = py + 26;
		shadowText(g2, "Run Stats", fontTitle, TEXT_YELLOW, cx, y);
		final Difficulty[] difficulties = Difficulty.values();
		for (int i = 0; i < difficulties.length; i++)
		{
			final DifficultyStats s = statsSnapshot[i];
			y += 24;
			shadowText(g2, titleOf(difficulties[i]) + " - "
				+ s.getRuns() + (s.getRuns() == 1 ? " run" : " runs"),
				fontBody, difficultyColor(difficulties[i]), cx, y);
			y += 16;
			shadowText(g2, "best " + s.getBest() + ", avg " + s.averageText() + ", "
				+ formatTenths(s.getXpTenths()) + " xp", fontSmall, TEXT_GREY, cx, y);
		}
		y += 24;
		iconShadowText(g2, "Lifetime: " + formatTenths(lifetimeXpTenths) + " xp (lvl "
			+ cachedLevel + ")", fontSmall, TEXT_WHITE, cx, y);
		y += 17;
		shadowText(g2, "Click to close", fontSmall, TEXT_GREY, cx, y);
		paintPickerButton(g2, statsResetButton(), "Reset progress", DEBUG_RED);
	}

	/** One place for the Easy/Normal/Hard colour language. */
	private static Color difficultyColor(Difficulty d)
	{
		return d == Difficulty.EASY ? TEXT_GREY
			: d == Difficulty.HARD ? TEXT_ORANGE : TEXT_WHITE;
	}

	private void paintPickerButton(Graphics2D g2, Rectangle r, String label, Color color)
	{
		g2.setColor(PANEL_BG);
		g2.fillRoundRect(r.x, r.y, r.width, r.height, 8, 8);
		g2.setColor(PANEL_BORDER);
		g2.setStroke(new BasicStroke(1.5f));
		g2.drawRoundRect(r.x, r.y, r.width, r.height, 8, 8);
		shadowText(g2, label, fontSmall, color, r.x + r.width / 2, r.y + 17);
	}

	/**
	 * The whole point of an in-panel picker: unlike core's config combo,
	 * locked rows here are ACTUALLY disabled - greyed visual, requirement
	 * in the label, click refused. Each row shows its real look: the item
	 * sprite for critters, a mini rendered backdrop for zones.
	 */
	private void showPickerMenu(boolean critters)
	{
		final JPopupMenu menu = new JPopupMenu();
		if (critters)
		{
			// Compare against the critter actually ON SCREEN, not the raw
			// stored pick: a stale locked pick renders as the fallback, and
			// re-choosing that fallback is not a change. It also means the
			// change achievement is unearnable while only one is unlocked.
			final ChinSkin shown = currentSkin();
			for (CritterChoice c : CritterChoice.values())
			{
				if (c.isMystery() && !c.isUnlocked())
				{
					// Sealed rewards don't spoil themselves in the picker.
					continue;
				}
				addPickerRow(menu, c.toString(), c.toSkin() == shown, c.isUnlocked(),
					pickerIcon(c.toSkin()), () ->
					{
						plugin.setCritter(c);
						if (c.toSkin() != shown)
						{
							queueBanner(plugin.markCritterChanged());
						}
					});
			}
		}
		else
		{
			final BackgroundTheme shown = resolveTheme();
			for (BackgroundChoice b : BackgroundChoice.values())
			{
				if (b.isMystery() && !b.isUnlocked())
				{
					continue;
				}
				addPickerRow(menu, b.toString(), b.toTheme() == shown, b.isUnlocked(),
					zoneThumb(b.toTheme()), () ->
					{
						plugin.setBackground(b);
						if (b.toTheme() != shown)
						{
							queueBanner(plugin.markZoneChanged());
						}
					});
			}
		}
		final Rectangle anchor = critters ? critterButton() : zoneButton();
		menu.show(this, anchor.x, anchor.y - menu.getPreferredSize().height - 4);
	}

	/** Row icon from the display-format cache; item or bundled resource. */
	private ImageIcon pickerIcon(ChinSkin skin)
	{
		final BufferedImage sprite = skinSprites.get(skin);
		if (sprite != null)
		{
			return new ImageIcon(sprite);
		}
		final AsyncBufferedImage async = skinImages.get(skin);
		return async != null ? new ImageIcon(async) : null;
	}

	private void addPickerRow(JPopupMenu menu, String label, boolean current, boolean unlocked,
		ImageIcon icon, Runnable onPick)
	{
		// Plain JMenuItem: no radio-check well behind the icon. The current
		// pick reads as the yellow row instead.
		final JMenuItem item = new JMenuItem(label, icon);
		item.setFont(fontSmall);
		item.setIconTextGap(10);
		if (current)
		{
			item.setForeground(TEXT_YELLOW);
		}
		if (unlocked)
		{
			item.addActionListener(ev -> onPick.run());
		}
		else
		{
			item.setEnabled(false);
		}
		menu.add(item);
	}

	/** Mini backdrop, rendered at design size and shrunk - cached per zone. */
	private ImageIcon zoneThumb(BackgroundTheme theme)
	{
		return zoneThumbs.computeIfAbsent(theme, t ->
		{
			final BufferedImage full = new BufferedImage(225, 420, BufferedImage.TYPE_INT_RGB);
			final Graphics2D g = full.createGraphics();
			try
			{
				renderBackdrop(g, 225, 420, t);
			}
			finally
			{
				g.dispose();
			}
			return new ImageIcon(full.getScaledInstance(48, 36, Image.SCALE_SMOOTH));
		});
	}

	/**
	 * Whole backdrop (sky, skyline, ground) for one zone - static and
	 * field-free so the BackdropGallery dev tool can render every zone
	 * headlessly without a client.
	 */
	static void renderBackdrop(Graphics2D g2, int w, int h, BackgroundTheme theme)
	{
		final int floor = h - FlappyGame.GROUND_HEIGHT;
		final Palette p = PALETTES.get(theme);
		paintSky(g2, w, floor, theme, p);
		paintGround(g2, w, h, floor, theme, p, 0);
	}

	private static void paintSky(Graphics2D g2, int w, int floor, BackgroundTheme theme, Palette p)
	{
		g2.setPaint(new GradientPaint(0, 0, p.skyTop, 0, floor, p.skyBottom));
		g2.fillRect(0, 0, w, floor);
		g2.setColor(p.silhouette);
		paintSkyline(g2, w, floor, theme, p);
	}

	/** Simple landmark silhouettes; enough to say "you are here" at 225px. */
	private static void paintSkyline(Graphics2D g2, int w, int floor, BackgroundTheme theme, Palette p)
	{
		final int cx = w / 2;
		switch (theme)
		{
			case FELDIP_MARSH:
				g2.fillArc(-40, floor - 70, w, 140, 0, 180);
				g2.fillArc(cx - 20, floor - 50, w, 100, 0, 180);
				break;
			case LUMBRIDGE:
				// The castle: keep, two crenellated towers.
				g2.fillArc(-60, floor - 46, w, 92, 0, 180);
				g2.fillRect(cx - 40, floor - 46, 80, 46);
				g2.fillRect(cx - 54, floor - 66, 18, 66);
				g2.fillRect(cx + 36, floor - 66, 18, 66);
				for (int t = 0; t < 3; t++)
				{
					g2.fillRect(cx - 54 + t * 7, floor - 72, 4, 6);
					g2.fillRect(cx + 36 + t * 7, floor - 72, 4, 6);
				}
				break;
			case VARROCK:
				// The city wall with battlements and gate towers.
				g2.fillRect(0, floor - 32, w, 32);
				for (int x = 2; x < w; x += 14)
				{
					g2.fillRect(x, floor - 40, 8, 8);
				}
				g2.fillRect(w / 5, floor - 56, 22, 56);
				g2.fillRect(3 * w / 5, floor - 56, 22, 56);
				break;
			case AL_KHARID:
				// Dunes and the palace dome + minaret.
				g2.fillArc(-50, floor - 36, w, 72, 0, 180);
				g2.fillArc(cx, floor - 28, w, 56, 0, 180);
				g2.fillRect(cx - 34, floor - 30, 68, 30);
				g2.fillArc(cx - 26, floor - 54, 52, 48, 0, 180);
				g2.fillRect(cx + 40, floor - 60, 8, 60);
				g2.fillArc(cx + 36, floor - 68, 16, 16, 0, 180);
				break;
			case MORYTANIA:
				// Dead trees in the mist.
				for (int i = 0; i < 3; i++)
				{
					final int tx = w / 6 + i * w / 3;
					g2.fillRect(tx, floor - 44, 5, 44);
					g2.drawLine(tx + 2, floor - 36, tx - 10, floor - 48);
					g2.drawLine(tx + 3, floor - 30, tx + 14, floor - 44);
					g2.drawLine(tx + 2, floor - 42, tx + 9, floor - 56);
				}
				g2.setColor(new Color(0xc8, 0xd2, 0xc0, 46));
				g2.fillRect(0, floor - 16, w, 16);
				break;
			case ARDOUGNE:
				// The market city: long wall, twin gate towers, pennants.
				g2.fillRect(0, floor - 26, w, 26);
				for (int x = 4; x < w; x += 16)
				{
					g2.fillRect(x, floor - 33, 8, 7);
				}
				g2.fillRect(cx - 44, floor - 58, 18, 58);
				g2.fillRect(cx + 26, floor - 58, 18, 58);
				g2.fillPolygon(
					new int[]{cx - 35, cx - 35, cx - 21},
					new int[]{floor - 70, floor - 58, floor - 64}, 3);
				g2.fillPolygon(
					new int[]{cx + 35, cx + 35, cx + 49},
					new int[]{floor - 70, floor - 58, floor - 64}, 3);
				// Market stalls out front - the Ardougne tell.
				paintStall(g2, cx - 86, floor);
				paintStall(g2, cx + 56, floor);
				g2.setColor(p.silhouette);
				break;
			case KOUREND:
				// Mount Quidamortem massif right, the Dark Altar afloat left.
				g2.fillPolygon(
					new int[]{-20, w / 6, w / 3},
					new int[]{floor, floor - 36, floor}, 3);
				g2.fillPolygon(
					new int[]{cx - 24, cx + 28, cx + 64, w + 30},
					new int[]{floor, floor - 92, floor - 88, floor}, 4);
				g2.setColor(new Color(0xb0, 0x66, 0xd8, 70));
				g2.fillOval(w / 4 - 18, floor - 98, 36, 36);
				g2.setColor(new Color(0x8a, 0x4f, 0xb8, 210));
				g2.fillPolygon(
					new int[]{w / 4, w / 4 + 11, w / 4, w / 4 - 11},
					new int[]{floor - 95, floor - 80, floor - 65, floor - 80}, 4);
				g2.setColor(new Color(0xd0, 0xa8, 0xf0, 230));
				g2.fillPolygon(
					new int[]{w / 4, w / 4 + 5, w / 4, w / 4 - 5},
					new int[]{floor - 88, floor - 80, floor - 72, floor - 80}, 4);
				g2.setColor(p.silhouette);
				break;
			case FREMENNIK:
				// Rellekka: peaks flanking open water, a longship out on it.
				g2.fillPolygon(
					new int[]{-24, w / 6, w / 2 - 10},
					new int[]{floor, floor - 72, floor}, 3);
				g2.fillPolygon(
					new int[]{cx + 20, 5 * w / 6, w + 24},
					new int[]{floor, floor - 54, floor}, 3);
				g2.setColor(new Color(0x46, 0x66, 0x80, 190));
				g2.fillRect(0, floor - 26, w, 26);
				g2.setColor(new Color(0xa8, 0xc8, 0xd8, 150));
				for (int x = 6; x < w; x += 30)
				{
					g2.fillRect(x, floor - 18 + (x / 30 % 2) * 8, 8, 2);
				}
				// The longship: swept hull, mast, striped sail.
				g2.setColor(new Color(0x2e, 0x3a, 0x42, 235));
				g2.fillPolygon(
					new int[]{cx - 34, cx + 34, cx + 22, cx - 22},
					new int[]{floor - 16, floor - 16, floor - 8, floor - 8}, 4);
				g2.fillRect(cx - 34, floor - 26, 4, 12);
				g2.fillRect(cx + 30, floor - 26, 4, 12);
				g2.fillRect(cx - 2, floor - 44, 3, 28);
				g2.setColor(new Color(0xd8, 0xdc, 0xe0, 225));
				g2.fillRect(cx - 13, floor - 42, 26, 17);
				g2.setColor(new Color(0x9c, 0x3f, 0x34, 225));
				for (int s = 0; s < 3; s++)
				{
					g2.fillRect(cx - 11 + s * 9, floor - 42, 4, 17);
				}
				g2.setColor(p.silhouette);
				break;
			case VARLAMORE:
				// The Fortis Colosseum ring, arches and all.
				g2.fillArc(cx - 70, floor - 56, 140, 112, 0, 180);
				g2.setColor(p.skyBottom);
				for (int i = -2; i <= 2; i++)
				{
					g2.fillArc(cx + i * 24 - 7, floor - 24, 14, 24, 0, 180);
				}
				g2.setColor(p.silhouette);
				break;
			case PRIFDDINAS:
				// The crystal city in full: aurora, two ranks of spires,
				// and the Tower of Voices at the heart. Worth the 99.
				g2.setColor(new Color(0xc8, 0xea, 0xff, 44));
				g2.fillRoundRect(-20, 30, w + 40, 9, 9, 9);
				g2.fillRoundRect(-30, 52, w + 40, 7, 7, 7);
				g2.setColor(new Color(0xa8, 0xb8, 0xdc, 110));
				final int[] backX = {w / 10, w / 4, 2 * w / 5, 3 * w / 5, 3 * w / 4, 9 * w / 10};
				final int[] backH = {44, 66, 54, 58, 70, 48};
				for (int i = 0; i < backX.length; i++)
				{
					g2.fillPolygon(
						new int[]{backX[i] - 10, backX[i] + 10, backX[i]},
						new int[]{floor, floor, floor - backH[i]}, 3);
				}
				g2.setColor(p.silhouette);
				final int[] frontX = {w / 6, w / 3 + 4, 2 * w / 3 - 4, 5 * w / 6};
				final int[] frontH = {60, 80, 76, 56};
				for (int i = 0; i < frontX.length; i++)
				{
					g2.fillPolygon(
						new int[]{frontX[i] - 9, frontX[i] + 9, frontX[i]},
						new int[]{floor, floor, floor - frontH[i]}, 3);
				}
				g2.fillPolygon(
					new int[]{cx - 17, cx + 17, cx},
					new int[]{floor, floor, floor - 124}, 3);
				g2.setColor(new Color(0xd4, 0xe2, 0xff, 170));
				g2.fillPolygon(
					new int[]{cx - 7, cx + 7, cx},
					new int[]{floor, floor, floor - 100}, 3);
				g2.setColor(new Color(0xff, 0xff, 0xff, 220));
				sparkle(g2, cx, floor - 128);
				sparkle(g2, w / 3 + 4, floor - 84);
				sparkle(g2, 2 * w / 3 - 4, floor - 80);
				sparkle(g2, w / 5, floor - 40);
				sparkle(g2, 4 * w / 5, floor - 34);
				g2.setColor(p.silhouette);
				break;
			case WILDERNESS:
				// A jagged black ridge and a lone obelisk.
				final Polygon ridge = new Polygon();
				ridge.addPoint(0, floor);
				for (int x = 0; x <= w; x += 30)
				{
					ridge.addPoint(x, floor - (x / 30 % 2 == 0 ? 22 : 52));
				}
				ridge.addPoint(w, floor);
				g2.fillPolygon(ridge);
				g2.fillRect((int) (w * 0.72), floor - 64, 10, 64);
				g2.fillRect((int) (w * 0.72) - 3, floor - 68, 16, 6);
				break;
			case PANDEMONIUM:
				// The pirate dock at dusk: sea, pier, a moored ship.
				g2.setColor(new Color(0x2e, 0x44, 0x58, 200));
				g2.fillRect(0, floor - 24, w, 24);
				g2.setColor(new Color(0x8a, 0xa4, 0xb4, 130));
				for (int x = 8; x < w; x += 34)
				{
					g2.fillRect(x, floor - 16 + (x / 34 % 2) * 6, 9, 2);
				}
				g2.setColor(p.silhouette);
				// The pier, left: deck on posts with a mooring bollard.
				g2.fillRect(0, floor - 32, cx - 24, 6);
				for (int x = 10; x < cx - 28; x += 26)
				{
					g2.fillRect(x, floor - 26, 5, 26);
				}
				g2.fillRect(28, floor - 42, 6, 10);
				// The ship, right: swept hull, two masts, dusk-lit sails.
				g2.fillPolygon(
					new int[]{cx - 6, w - 8, w - 22, cx + 8},
					new int[]{floor - 26, floor - 26, floor - 10, floor - 10}, 4);
				g2.fillRect(cx + 24, floor - 78, 3, 52);
				g2.fillRect(cx + 58, floor - 66, 3, 40);
				g2.setColor(new Color(0xe8, 0xd4, 0xb0, 220));
				g2.fillRect(cx + 12, floor - 74, 26, 20);
				g2.fillRect(cx + 48, floor - 62, 22, 16);
				g2.setColor(p.silhouette);
				g2.fillPolygon(
					new int[]{cx + 27, cx + 43, cx + 27},
					new int[]{floor - 84, floor - 79, floor - 74}, 3);
				// Lanterns on the pier and stern.
				g2.setColor(new Color(0xff, 0xb8, 0x4a, 230));
				g2.fillOval(30, floor - 47, 4, 4);
				g2.fillOval(w - 16, floor - 34, 4, 4);
				g2.setColor(p.silhouette);
				break;
			case INFERNO:
				// Zuk's shadow looms in the glow behind the pillars.
				final Polygon zuk = new Polygon();
				zuk.addPoint(cx - 88, floor);
				zuk.addPoint(cx - 88, floor - 66);
				zuk.addPoint(cx - 58, floor - 90);
				zuk.addPoint(cx - 28, floor - 86);
				zuk.addPoint(cx - 20, floor - 106);
				zuk.addPoint(cx - 26, floor - 118);
				zuk.addPoint(cx, floor - 112);
				zuk.addPoint(cx + 26, floor - 118);
				zuk.addPoint(cx + 20, floor - 106);
				zuk.addPoint(cx + 28, floor - 86);
				zuk.addPoint(cx + 58, floor - 90);
				zuk.addPoint(cx + 88, floor - 66);
				zuk.addPoint(cx + 88, floor);
				g2.setPaint(new GradientPaint(0, floor - 118, new Color(0x1a, 0x06, 0x04, 40),
					0, floor, new Color(0x1a, 0x06, 0x04, 150)));
				g2.fillPolygon(zuk);
				// The arena's obsidian columns.
				g2.setColor(p.silhouette);
				for (int[] col : new int[][]{{w / 6, 64}, {cx, 84}, {5 * w / 6, 58}})
				{
					g2.fillRect(col[0] - 11, floor - col[1], 22, col[1]);
					g2.fillRect(col[0] - 13, floor - col[1] - 4, 26, 6);
				}
				g2.setColor(p.accent);
				g2.fillOval(w / 5, floor - 128, 3, 3);
				g2.fillOval(cx + 8, floor - 148, 3, 3);
				g2.fillOval(3 * w / 4, floor - 112, 3, 3);
				g2.fillOval(w / 3, floor - 92, 2, 2);
				g2.setColor(p.silhouette);
				break;
		}
	}

	private void paintTraps(Graphics2D g2, int floor)
	{
		final int gap = game.getGapSize();
		for (BoxTrap trap : game.getTraps())
		{
			final int x = (int) Math.round(trap.getX());
			final int gapTop = trap.getGapCenter() - gap / 2;
			final int gapBottom = trap.getGapCenter() + gap / 2;
			paintTower(g2, x, 0, gapTop, true);
			paintTower(g2, x, gapBottom, floor, false);
		}
	}

	/**
	 * A tower that IS box traps: the real trap sprite tiled from the gap
	 * edge to the screen edge over a dark timber spine, so the whole
	 * obstacle reads as a stack of set traps waiting for you.
	 */
	private void paintTower(Graphics2D g2, int x, int top, int bottom, boolean mouthAtBottom)
	{
		final int wTrap = FlappyGame.TRAP_WIDTH;
		if (bottom <= top)
		{
			return;
		}
		if (trapSprite != null)
		{
			final Graphics2D gt = (Graphics2D) g2.create();
			try
			{
				gt.clipRect(x - 3, top, wTrap + 6, bottom - top);
				gt.setColor(WOOD_DARK);
				gt.fillRect(x + 4, top, wTrap - 8, bottom - top);
				final int ih = trapSprite.getHeight();
				final int ix = x + (wTrap - trapSprite.getWidth()) / 2;
				// Slight overlap so the sprites read as a stack; start at the
				// gap edge so the mouth-most trap is always fully visible.
				final int step = ih - 4;
				if (mouthAtBottom)
				{
					for (int y = bottom - ih; y > top - ih; y -= step)
					{
						gt.drawImage(trapSprite, ix, y, null);
					}
				}
				else
				{
					for (int y = top; y < bottom; y += step)
					{
						gt.drawImage(trapSprite, ix, y, null);
					}
				}
			}
			finally
			{
				gt.dispose();
			}
			// Lip at the gap edge, flappy-pipe style.
			g2.setColor(WOOD_DARK);
			g2.fillRect(x - 3, mouthAtBottom ? bottom - 6 : top, wTrap + 6, 6);
			return;
		}

		// Drawn-crate fallback until the sprite loads.
		g2.setColor(WOOD);
		g2.fillRect(x, top, wTrap, bottom - top);
		for (int y = top; y < bottom; y += 26)
		{
			final int segH = Math.min(26, bottom - y);
			g2.setColor(WOOD_DARK);
			g2.drawRect(x, y, wTrap - 1, segH - 1);
			g2.setColor(MESH);
			g2.drawLine(x + 4, y + 4, x + wTrap - 5, y + segH - 5);
			g2.drawLine(x + wTrap - 5, y + 4, x + 4, y + segH - 5);
		}
		g2.setColor(WOOD_LIGHT);
		g2.fillRect(x, top, 4, bottom - top);
		g2.setColor(WOOD_DARK);
		g2.fillRect(x + wTrap - 4, top, 4, bottom - top);
		g2.fillRect(x - 3, mouthAtBottom ? bottom - 8 : top, wTrap + 6, 8);
	}

	/**
	 * Anti-cheat: while the dev-unlock toggle is on, every frame carries a
	 * loud diagonal watermark - no clean screenshots of cheated progress.
	 */
	private void paintDebugWatermark(Graphics2D g2, int w, int h)
	{
		final Graphics2D gw = (Graphics2D) g2.create();
		try
		{
			gw.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			gw.setFont(fontWatermark);
			gw.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.34f));
			gw.setColor(DEBUG_RED);
			final FontMetrics fm = gw.getFontMetrics();
			final int textW = fm.stringWidth("DEBUG MODE");
			for (int i = 0; i < 3; i++)
			{
				final Graphics2D gl = (Graphics2D) gw.create();
				gl.translate(w / 2, h / 5 + i * h / 3);
				gl.rotate(-0.42);
				gl.drawString("DEBUG MODE", -textW / 2, 0);
				gl.dispose();
			}
		}
		finally
		{
			gw.dispose();
		}
	}

	/** A four-point glint, for things made of crystal. */
	private static void sparkle(Graphics2D g2, int x, int y)
	{
		g2.fillPolygon(
			new int[]{x, x + 3, x, x - 3},
			new int[]{y - 4, y, y + 4, y}, 4);
	}

	/** A striped market-stall canopy on legs, Ardougne's signature. */
	private static void paintStall(Graphics2D g2, int x, int floor)
	{
		g2.setColor(new Color(0x5a, 0x46, 0x32, 210));
		g2.fillRect(x + 2, floor - 12, 3, 12);
		g2.fillRect(x + 25, floor - 12, 3, 12);
		g2.setColor(new Color(0xe8, 0xe0, 0xcc, 220));
		g2.fillRect(x, floor - 18, 30, 7);
		g2.setColor(new Color(0x9c, 0x3f, 0x34, 220));
		for (int s = 0; s < 3; s++)
		{
			g2.fillRect(x + 2 + s * 10, floor - 18, 5, 7);
		}
	}

	private static void paintGround(Graphics2D g2, int w, int h, int floor, BackgroundTheme theme,
		Palette p, int offset)
	{
		g2.setColor(p.ground);
		g2.fillRect(0, floor, w, h - floor);
		g2.setColor(p.groundTop);
		g2.fillRect(0, floor, w, 6);
		// Scrolling per-zone detail: tufts, stones, or lava cracks.
		for (int x = -offset; x < w; x += 24)
		{
			switch (theme)
			{
				case VARROCK:
				case AL_KHARID:
				case KOUREND:
				case VARLAMORE:
					g2.setColor(p.accent);
					g2.fillOval(x + 6, floor + 14, 7, 5);
					g2.fillOval(x + 16, floor + 26, 5, 4);
					break;
				case WILDERNESS:
				case INFERNO:
					g2.setColor(p.accent);
					g2.fillRect(x + 8, floor + 18, 11, 3);
					g2.fillRect(x + 16, floor + 30, 6, 2);
					break;
				default:
					g2.setColor(p.groundTop);
					g2.fillArc(x + 4, floor - 4, 10, 8, 0, 180);
					g2.setColor(p.accent);
					g2.fillOval(x + 12, floor + 18, 8, 4);
					break;
			}
		}
	}

	private void paintChin(Graphics2D g2)
	{
		final double y = game.getChinY();
		final FlappyGame.State state = game.getState();
		if (state == FlappyGame.State.DEAD)
		{
			paintExplosion(g2, FlappyGame.CHIN_X, y);
			return;
		}
		final Graphics2D gc = (Graphics2D) g2.create();
		try
		{
			gc.translate(FlappyGame.CHIN_X, y);
			final double vy = game.getChinVy();
			final double theta = state == FlappyGame.State.RUNNING
				? (vy >= 0 ? Math.min(vy * 0.11, 1.1) : Math.max(vy * 0.07, -0.45))
				: 0;
			gc.rotate(theta);
			final BufferedImage img = skinSprites.get(currentSkin());
			if (img != null)
			{
				gc.drawImage(img, -img.getWidth() / 2, -img.getHeight() / 2, null);
			}
			else
			{
				// Fallback critter until the cache sprite arrives.
				gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				gc.setColor(CHIN_BODY);
				gc.fillOval(-13, -11, 26, 22);
				gc.setColor(CHIN_BELLY);
				gc.fillOval(-7, -3, 14, 11);
				gc.setColor(Color.BLACK);
				gc.fillOval(4, -6, 4, 4);
			}
		}
		finally
		{
			gc.dispose();
		}
	}

	/** Chinchompas explode. It's what they do. */
	private void paintExplosion(Graphics2D g2, int cx, double cy)
	{
		final int t = game.getTicksSinceDeath();
		final Graphics2D gc = (Graphics2D) g2.create();
		try
		{
			gc.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			if (t < EXPLOSION_TICKS)
			{
				final float p = t / (float) EXPLOSION_TICKS;
				final float alpha = 1f - p;
				final int r = 8 + (int) (p * 34);
				gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				gc.setColor(EXPLOSION_SMOKE);
				gc.fillOval(cx - r - 4, (int) cy - r - 4, 2 * r + 8, 2 * r + 8);
				gc.setColor(TEXT_ORANGE);
				gc.fillOval(cx - r, (int) cy - r, 2 * r, 2 * r);
				gc.setColor(EXPLOSION_CORE);
				gc.fillOval(cx - r / 2, (int) cy - r / 2, r, r);
			}
			else
			{
				// Just a wisp of smoke where a chinchompa used to be.
				gc.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
				gc.setColor(SMOKE_WISP);
				gc.fillOval(cx - 8, (int) cy - 10, 16, 12);
				gc.fillOval(cx - 4, (int) cy - 18, 10, 9);
			}
		}
		finally
		{
			gc.dispose();
		}
	}

	/** Rising, fading "+265" texts, one per dodge - like the game's xp drops. */
	private void paintXpDrops(Graphics2D g2)
	{
		if (xpDrops.isEmpty())
		{
			return;
		}
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		final Graphics2D gd = (Graphics2D) g2.create();
		try
		{
			for (XpDrop drop : xpDrops)
			{
				final float alpha = 1f - (drop.age / (float) XpDrop.LIFETIME_TICKS);
				final int y = (int) (drop.y - drop.age * 0.8);
				gd.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
				iconShadowText(gd, drop.text, fontBody, drop.color, FlappyGame.CHIN_X, y);
			}
		}
		finally
		{
			gd.dispose();
		}
	}

	private void paintHud(Graphics2D g2, int w, int h)
	{
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
			RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		if (duelRun != null)
		{
			paintDuelHud(g2, w, h);
			return;
		}
		switch (game.getState())
		{
			case READY:
				shadowText(g2, "Trappy Chinchompa", fontTitle, TEXT_YELLOW, w / 2, 60);
				shadowText(g2, "Click / Space to start", fontBody, TEXT_WHITE, w / 2, 84);
				// Best follows the selected difficulty, live with the toggle.
				if (cachedDifficultyBest > 0)
				{
					shadowText(g2, "Best: " + cachedDifficultyBest, fontBody, TEXT_WHITE,
						w / 2, h - 134);
				}
				if (lifetimeXpTenths > 0)
				{
					iconShadowText(g2, "Anti-hunter level: " + cachedLevel,
						fontSmall, TEXT_GREY, w / 2, h - 116);
				}
				// The pickers themselves surface lock state, so no hint text.
				paintPickerButton(g2, difficultyButton(),
					"Difficulty: " + titleOf(cachedDifficulty), difficultyColor(cachedDifficulty));
				paintPickerButton(g2, critterButton(), "Critter", TEXT_WHITE);
				paintPickerButton(g2, zoneButton(), "Zone", TEXT_WHITE);
				paintPickerButton(g2, statsButton(), "Stats", TEXT_WHITE);
				paintPickerButton(g2, achievementsButton(), "Achievements", TEXT_WHITE);
				if (statsSnapshot != null)
				{
					paintStatsCard(g2, w, h);
				}
				break;
			case RUNNING:
				shadowText(g2, String.valueOf(game.getScore()), fontBig, TEXT_YELLOW, w / 2, 48);
				break;
			case DEAD:
				paintDeathPanel(g2, w, h);
				break;
		}
	}

	private void paintDeathPanel(Graphics2D g2, int w, int h)
	{
		if (game.getTicksSinceDeath() < EXPLOSION_TICKS || lastResult == null)
		{
			return;
		}
		final BackgroundTheme unlocked = lastResult.getUnlockedBackground();
		final ChinSkin skinUnlocked = lastResult.getUnlockedSkin();
		final int achievementCount = runAchievementCount;
		final int pw = Math.min(w - 24, 210);
		final int ph = 158 + (unlocked != null ? 19 : 0) + (skinUnlocked != null ? 19 : 0)
			+ (runDifficulty != Difficulty.NORMAL ? 17 : 0)
			+ (achievementCount > 0 ? 19 : 0)
			+ (runSandbox ? 17 : 0);
		final int px = (w - pw) / 2;
		final int py = h / 2 - ph / 2 - 20;
		g2.setColor(PANEL_BG);
		g2.fillRoundRect(px, py, pw, ph, 10, 10);
		g2.setColor(PANEL_BORDER);
		g2.setStroke(new BasicStroke(2f));
		g2.drawRoundRect(px, py, pw, ph, 10, 10);

		final int cx = w / 2;
		int y = py + 26;
		final String headline = game.getDeathCause() == FlappyGame.DeathCause.GROUND
			? "SPLAT!" : "KABOOM!";
		shadowText(g2, headline, fontTitle, TEXT_ORANGE, cx, y);
		y += 24;
		shadowText(g2, "Traps dodged: " + lastResult.getScore(), fontBody, TEXT_WHITE, cx, y);
		y += 19;
		shadowText(g2, lastResult.isNewDifficultyBest()
				? "New best!" : "Best: " + lastResult.getDifficultyBest(),
			fontBody, lastResult.isNewDifficultyBest() ? TEXT_YELLOW : TEXT_WHITE, cx, y);
		y += 19;
		iconShadowText(g2, "+" + formatTenths(lastResult.getRunXpTenths()) + " Anti-hunter xp",
			fontBody, TEXT_WHITE, cx, y);
		y += 19;
		shadowText(g2, "Total: " + formatTenths(lastResult.getLifetimeXpTenths()) + " xp",
			fontSmall, TEXT_GREY, cx, y);
		y += 17;
		iconShadowText(g2, "Anti-hunter level: " + lastResult.getAntiHunterLevel(),
			fontSmall, TEXT_GREY, cx, y);
		if (runDifficulty == Difficulty.EASY)
		{
			y += 17;
			shadowText(g2, "Easy: x0.1 xp", fontSmall, TEXT_GREY, cx, y);
		}
		else if (runDifficulty == Difficulty.HARD)
		{
			y += 17;
			shadowText(g2, "Hard bonus: x2.5 xp", fontSmall, TEXT_YELLOW, cx, y);
		}
		if (runSandbox)
		{
			y += 17;
			shadowText(g2, "Sandbox: nothing recorded", fontSmall, DEBUG_RED, cx, y);
		}
		if (skinUnlocked != null)
		{
			y += 19;
			shadowText(g2, titleOf(skinUnlocked) + " unlocked!", fontSmall, TEXT_YELLOW, cx, y);
		}
		if (unlocked != null)
		{
			y += 19;
			shadowText(g2, titleOf(unlocked) + " unlocked!", fontSmall, TEXT_YELLOW, cx, y);
		}
		if (achievementCount > 0)
		{
			y += 19;
			shadowText(g2, achievementCount == 1 ? "1 achievement earned!"
				: achievementCount + " achievements earned!", fontSmall, TEXT_YELLOW, cx, y);
		}
		y += 22;
		if (game.canRestart() && duelRun == null)
		{
			shadowText(g2, "Click to go again", fontSmall, TEXT_WHITE, cx, y);
		}

		if (levelBannerTicks > 0 && lastResult.getLeveledTo() > 0)
		{
			paintLevelBanner(g2, w, py - 34);
		}
	}

	private void paintLevelBanner(Graphics2D g2, int w, int y)
	{
		iconShadowText(g2, "Anti-hunter level " + lastResult.getLeveledTo() + "!",
			fontTitle, TEXT_YELLOW, w / 2, y);
	}

	/** The Hunter sprite behind a red prohibition ring and slash. */
	private static BufferedImage composeAntiIcon(BufferedImage base)
	{
		// The Hunter sprite is dark browns and vanishes on dark panels:
		// brighten and halo it with the client's own image helpers, then
		// draw the red no-sign.
		final BufferedImage out = ImageUtil.outlineImage(
			ImageUtil.luminanceScale(base, 1.5f), new Color(0xf2, 0xea, 0xd0));
		final Graphics2D g = out.createGraphics();
		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(0xd9, 0x2b, 0x2b));
			g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			final int w = out.getWidth();
			final int h = out.getHeight();
			g.drawOval(1, 1, w - 3, h - 3);
			g.drawLine(4, 4, w - 5, h - 5);
		}
		finally
		{
			g.dispose();
		}
		return out;
	}

	private static void drawShadowString(Graphics2D g2, String text, int x, int y, Color color)
	{
		g2.setColor(Color.BLACK);
		g2.drawString(text, x + 1, y + 1);
		g2.setColor(color);
		g2.drawString(text, x, y);
	}

	private void shadowText(Graphics2D g2, String text, Font font, Color color, int cx, int y)
	{
		g2.setFont(font);
		final FontMetrics fm = g2.getFontMetrics();
		drawShadowString(g2, text, cx - fm.stringWidth(text) / 2, y, color);
	}

	/**
	 * A centred [skill icon] + text ensemble, the way the game labels xp;
	 * falls back to plain text until the sprite arrives. The icon scales to
	 * the font's ascent and sits on the same baseline.
	 */
	private void iconShadowText(Graphics2D g2, String text, Font font, Color color, int cx, int y)
	{
		if (antiHunterIcon == null)
		{
			shadowText(g2, text, font, color, cx, y);
			return;
		}
		g2.setFont(font);
		final FontMetrics fm = g2.getFontMetrics();
		final int ih = fm.getAscent() + 2;
		final int iw = Math.max(1, ih * antiHunterIcon.getWidth() / antiHunterIcon.getHeight());
		final int total = iw + 4 + fm.stringWidth(text);
		final int ix = cx - total / 2;
		g2.drawImage(antiHunterIcon, ix, y - fm.getAscent(), iw, ih, null);
		drawShadowString(g2, text, ix + iw + 4, y, color);
	}

	/** 1984 tenths -> "198.4"; 2650 -> "265". Grouped like game xp. */
	private static String formatTenths(long tenths)
	{
		final long whole = tenths / 10;
		final long frac = tenths % 10;
		final String grouped = String.format("%,d", whole);
		return frac == 0 ? grouped : grouped + "." + frac;
	}

	/** One floating "+xp" text; spawned per dodge, rises and fades out. */
	private static final class XpDrop
	{
		static final int LIFETIME_TICKS = 55;

		String text;
		Color color;
		double y;
		int age;
	}
}
