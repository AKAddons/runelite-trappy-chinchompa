package com.trappychinchompa;

import com.trappychinchompa.game.BackgroundTheme;
import com.trappychinchompa.game.ChinSkin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.util.Text;

/**
 * The achievement catalog: every entry, its display strings, and - where an
 * achievement coincides with a critter/zone unlock - the reward it carries.
 * Rewards are pure attribution: the unlock mechanics still run off the high
 * score and level exactly as before; the matching achievement rows simply
 * own the credit. Not every achievement unlocks something, and that's fine.
 *
 * Ids are stable and persisted (CSV of earned ids), so never rename one.
 */
public final class Achievement
{
	/** Single-run score milestones, per difficulty. */
	public static final int[] SCORE_THRESHOLDS = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 250};
	/** Anti-hunter level milestones. */
	public static final int[] LEVEL_MILESTONES =
		{5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 99};
	/** Streak requirements: clear at least this many poles... */
	public static final int[] STREAK_THRESHOLDS = {10, 25, 50};
	/** ...in this many consecutive runs of one difficulty. */
	public static final int[] STREAK_LENGTHS = {5, 10, 20};

	public static final String ID_ZERO_POLES = "zero_poles";
	public static final String ID_CHANGE_ZONE = "change_zone";
	public static final String ID_CHANGE_CRITTER = "change_critter";
	public static final String ID_PRIF_25K = "prif_25k";
	public static final String ID_CABBAGE_INFERNO = "cabbage_inferno";
	public static final String ID_OPEN_STATS = "open_stats";
	public static final String ID_OPEN_ACHIEVEMENTS = "open_achievements";
	public static final String ID_LOADOUT_LAB = "loadout_lab";
	/** One-run xp bar for Song of the Chins: 25,000 xp, in tenths. */
	public static final long PRIF_RUN_XP_TENTHS = 250_000;
	/** How sealed rewards and secret achievements render until earned. */
	public static final String MYSTERY_MASK = "??????";

	/** Every achievement, in display order. */
	public static final List<Achievement> ALL;
	private static final Map<String, Achievement> BY_ID;

	private final String id;
	private final String name;
	private final String description;
	/** "Unlocks: X" attribution, or null for trophy-only achievements. */
	private final String reward;
	/** Mystery rewards display as "Unlocks: ??????" until earned. */
	private final boolean mysteryReward;
	/** Secret achievements mask their name and description until earned. */
	private final boolean secret;
	private final String category;

	private Achievement(String id, String name, String description, String reward,
		boolean mysteryReward, boolean secret, String category)
	{
		this.id = id;
		this.name = name;
		this.description = description;
		this.reward = reward;
		this.mysteryReward = mysteryReward;
		this.secret = secret;
		this.category = category;
	}

	static
	{
		final List<Achievement> all = new ArrayList<>();
		all.add(new Achievement(ID_ZERO_POLES, "Instant Kaboom",
			"End a run with exactly 0 traps cleared.", null, false, false, "General"));
		all.add(new Achievement(ID_CHANGE_CRITTER, "New Look",
			"Change your critter.", null, false, false, "General"));
		all.add(new Achievement(ID_CHANGE_ZONE, "Tourist",
			"Change your zone.", null, false, false, "General"));
		all.add(new Achievement(ID_OPEN_STATS, "Bean Counter",
			"Open the stats card.", null, false, false, "General"));
		all.add(new Achievement(ID_OPEN_ACHIEVEMENTS, "Window Shopping",
			"Open the achievements list.", null, false, false, "General"));
		all.add(new Achievement(ID_PRIF_25K, "Song of the Chins",
			"Bank 25,000 xp in a single run with Prifddinas as the backdrop.",
			null, false, false, "General"));
		all.add(new Achievement(ID_CABBAGE_INFERNO, "Well Done",
			"Fly a cabbage through the Inferno.", null, false, true, "General"));
		final RowRewards lab = gatedRowRewards(ID_LOADOUT_LAB);
		all.add(new Achievement(ID_LOADOUT_LAB, "Lab Partner",
			"Have Loadout Lab installed and enabled.", lab.text, lab.mystery, true, "General"));

		for (Difficulty d : Difficulty.values())
		{
			for (int t : SCORE_THRESHOLDS)
			{
				final RowRewards rewards = scoreRowRewards(d, t);
				all.add(new Achievement(scoreId(d, t),
					t + " Traps (" + Text.titleCase(d) + ")",
					"Clear " + t + " traps in a single " + Text.titleCase(d) + " run.",
					rewards.text, rewards.mystery, false, Text.titleCase(d)));
			}
		}

		for (Difficulty d : Difficulty.values())
		{
			for (int t : STREAK_THRESHOLDS)
			{
				for (int n : STREAK_LENGTHS)
				{
					final RowRewards rewards = gatedRowRewards(streakId(d, t, n));
					all.add(new Achievement(streakId(d, t, n),
						t + "x" + n + " Streak (" + Text.titleCase(d) + ")",
						"Clear " + t + "+ traps in " + n + " consecutive "
							+ Text.titleCase(d) + " runs.", rewards.text, rewards.mystery,
						false, "Streaks"));
				}
			}
		}

		for (int t : LEVEL_MILESTONES)
		{
			all.add(new Achievement(levelId(t), "Level " + t,
				"Reach Anti-hunter level " + t + ".", levelReward(t), false, false, "Levels"));
		}

		ALL = Collections.unmodifiableList(all);
		final Map<String, Achievement> byId = new LinkedHashMap<>();
		for (Achievement a : all)
		{
			if (byId.put(a.id, a) != null)
			{
				throw new IllegalStateException("duplicate achievement id: " + a.id);
			}
		}
		BY_ID = Collections.unmodifiableMap(byId);
	}

	/**
	 * Everything one achievement row unlocks, gathered in a single pass so
	 * the reward text and the mystery seal can never disagree: a row is
	 * sealed exactly when any critter it carries is a mystery reward, or
	 * when it awards a gated zone (all of which are mysteries by design).
	 */
	private static final class RowRewards
	{
		static final RowRewards NONE = new RowRewards(null, false);

		final String text;
		final boolean mystery;

		RowRewards(String text, boolean mystery)
		{
			this.text = text;
			this.mystery = mystery;
		}
	}

	/**
	 * A score row's rewards: record-driven ranks ride the record-eligible
	 * rows at their threshold; achievement-gated unlockables ride exactly
	 * the row whose id gates them (which is how the Easy 100 row gets to
	 * award something despite the practice pond's no-records rule).
	 */
	private static RowRewards scoreRowRewards(Difficulty d, int threshold)
	{
		// The Hard 250's uncovered text IS the reward.
		if (d == Difficulty.HARD && threshold == 250)
		{
			return new RowRewards("You're insane.", true);
		}
		final List<String> parts = new ArrayList<>();
		boolean mystery = false;
		for (ChinSkin skin : ChinSkin.values())
		{
			if (skin.getUnlockAchievementId() == null && d.countsForRecords()
				&& skin.getUnlockHighScore() == threshold)
			{
				parts.add(skinName(skin));
				mystery |= skin.isMysteryReward();
			}
		}
		mystery |= collectGated(scoreId(d, threshold), parts);
		return rowOf(parts, mystery);
	}

	/** Rewards gated on exactly this achievement row id. */
	private static RowRewards gatedRowRewards(String rowId)
	{
		final List<String> parts = new ArrayList<>();
		return rowOf(parts, collectGated(rowId, parts));
	}

	/** Adds gated unlockables to parts; returns whether any is a mystery. */
	private static boolean collectGated(String rowId, List<String> parts)
	{
		boolean mystery = false;
		for (ChinSkin skin : ChinSkin.values())
		{
			if (rowId.equals(skin.getUnlockAchievementId()))
			{
				parts.add(skinName(skin));
				mystery |= skin.isMysteryReward();
			}
		}
		for (BackgroundTheme theme : BackgroundTheme.values())
		{
			for (String id : theme.getUnlockAchievementIds())
			{
				if (rowId.equals(id))
				{
					parts.add(Text.titleCase(theme));
					mystery = true;
				}
			}
		}
		return mystery;
	}

	private static RowRewards rowOf(List<String> parts, boolean mystery)
	{
		return parts.isEmpty() ? RowRewards.NONE
			: new RowRewards("Unlocks: " + String.join(", ", parts), mystery);
	}

	private static String skinName(ChinSkin skin)
	{
		return skin.getDisplayName() != null ? skin.getDisplayName() : Text.titleCase(skin);
	}

	/** Zone unlocks ride their exact level row. */
	private static String levelReward(int level)
	{
		for (BackgroundTheme theme : BackgroundTheme.values())
		{
			if (theme.getUnlockLevel() == level)
			{
				return "Unlocks: " + Text.titleCase(theme);
			}
		}
		return null;
	}

	public static Achievement byId(String id)
	{
		return BY_ID.get(id);
	}

	public static String scoreId(Difficulty d, int threshold)
	{
		return "score_" + d.name() + "_" + threshold;
	}

	public static String levelId(int level)
	{
		return "level_" + level;
	}

	public static String streakId(Difficulty d, int threshold, int length)
	{
		return "streak_" + d.name() + "_" + threshold + "x" + length;
	}

	/** Ids a run's final score satisfies at this difficulty. */
	public static List<String> scoreIds(Difficulty d, int score)
	{
		return idsAtOrBelow(SCORE_THRESHOLDS, score, t -> scoreId(d, t));
	}

	/** Ids an Anti-hunter level satisfies. */
	public static List<String> levelIds(int level)
	{
		return idsAtOrBelow(LEVEL_MILESTONES, level, Achievement::levelId);
	}

	/** Ids a consecutive-run counter satisfies for one threshold. */
	public static List<String> streakIds(Difficulty d, int threshold, int streak)
	{
		return idsAtOrBelow(STREAK_LENGTHS, streak, n -> streakId(d, threshold, n));
	}

	private static List<String> idsAtOrBelow(int[] thresholds, int value,
		java.util.function.IntFunction<String> idFor)
	{
		final List<String> ids = new ArrayList<>();
		for (int t : thresholds)
		{
			if (value >= t)
			{
				ids.add(idFor.apply(t));
			}
		}
		return ids;
	}

	public String getId()
	{
		return id;
	}

	public String getName()
	{
		return name;
	}

	/** List display: secrets mask their name until earned. */
	public String getNameDisplay(boolean earned)
	{
		return secret && !earned ? MYSTERY_MASK : name;
	}

	/** List display: secrets mask their description until earned. */
	public String getDescriptionDisplay(boolean earned)
	{
		return secret && !earned ? "A secret achievement." : description;
	}

	public String getReward()
	{
		return reward;
	}

	/** What the achievements list shows: mysteries stay sealed until earned. */
	public String getRewardDisplay(boolean earned)
	{
		if (reward == null)
		{
			return null;
		}
		return mysteryReward && !earned ? "Unlocks: " + MYSTERY_MASK : reward;
	}

	public String getCategory()
	{
		return category;
	}
}
