package com.trappychinchompa;

import com.trappychinchompa.game.BackgroundTheme;
import com.trappychinchompa.game.ChinSkin;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AchievementTest
{
	@Test
	@DisplayName("the catalog holds all 88 achievements with unique ids")
	void catalogShape()
	{
		// 8 general + 3x11 score + 3x3x3 streaks + 20 levels = 88.
		assertEquals(88, Achievement.ALL.size());
		assertNotNull(Achievement.byId(Achievement.ID_PRIF_25K));
		assertNotNull(Achievement.byId(Achievement.ID_CABBAGE_INFERNO));
		assertNotNull(Achievement.byId(Achievement.ID_OPEN_STATS));
		assertNotNull(Achievement.byId(Achievement.ID_OPEN_ACHIEVEMENTS));
		assertEquals(25_000, Achievement.PRIF_RUN_XP_TENTHS / 10, "the crystal bar is 25k xp");
		final Set<String> ids = new HashSet<>();
		for (Achievement a : Achievement.ALL)
		{
			assertTrue(ids.add(a.getId()), a.getId() + " is unique");
			assertEquals(a, Achievement.byId(a.getId()));
		}
	}

	@Test
	@DisplayName("a run's score earns every milestone at or below it, for that difficulty")
	void scoreIds()
	{
		assertTrue(Achievement.scoreIds(Difficulty.NORMAL, 9).isEmpty());
		final List<String> at45 = Achievement.scoreIds(Difficulty.NORMAL, 45);
		assertEquals(4, at45.size(), "10 through 40");
		assertTrue(at45.contains(Achievement.scoreId(Difficulty.NORMAL, 40)));
		assertEquals(11, Achievement.scoreIds(Difficulty.HARD, 250).size(), "the full ladder");
	}

	@Test
	@DisplayName("levels earn every milestone at or below, capping with 99")
	void levelIds()
	{
		assertTrue(Achievement.levelIds(4).isEmpty());
		assertEquals(1, Achievement.levelIds(5).size());
		assertEquals(20, Achievement.levelIds(99).size());
	}

	@Test
	@DisplayName("streak milestones fall at 5, 10, and 20 consecutive runs")
	void streakIds()
	{
		assertTrue(Achievement.streakIds(Difficulty.HARD, 25, 4).isEmpty());
		assertEquals(1, Achievement.streakIds(Difficulty.HARD, 25, 5).size());
		assertEquals(3, Achievement.streakIds(Difficulty.HARD, 25, 20).size());
	}

	@Test
	@DisplayName("unlocks are attributed to their achievement rows - and only those")
	void rewardAttribution()
	{
		assertEquals("Unlocks: Lumbridge", Achievement.byId(Achievement.levelId(10)).getReward());
		assertNull(Achievement.byId(Achievement.levelId(15)).getReward(),
			"no zone unlocks at 15");
		assertEquals("Unlocks: Grey Chinchompa",
			Achievement.byId(Achievement.scoreId(Difficulty.NORMAL, 10)).getReward(),
			"grey rides the 10 rows now");
		assertEquals("Unlocks: Black Chinchompa",
			Achievement.byId(Achievement.scoreId(Difficulty.NORMAL, 40)).getReward());
		assertEquals("Unlocks: Rubber Chicken, TzTok-Jad",
			Achievement.byId(Achievement.scoreId(Difficulty.HARD, 100)).getReward(),
			"the Hard 100 row carries the record rank AND the trophy");
		assertEquals("Unlocks: Cabbage",
			Achievement.byId(Achievement.scoreId(Difficulty.EASY, 100)).getReward(),
			"the practice pond's one prize");
		assertNull(Achievement.byId(Achievement.scoreId(Difficulty.EASY, 40)).getReward());
		assertNull(Achievement.byId(Achievement.scoreId(Difficulty.NORMAL, 30)).getReward());
		assertNull(Achievement.byId(Achievement.ID_ZERO_POLES).getReward());
	}

	@Test
	@DisplayName("achievement-gated skins and zones reference real catalog ids")
	void gatedIdsExist()
	{
		assertEquals(Achievement.scoreId(Difficulty.HARD, 100),
			ChinSkin.TZTOK_JAD.getUnlockAchievementId());
		assertEquals(Achievement.scoreId(Difficulty.EASY, 100),
			ChinSkin.CABBAGE.getUnlockAchievementId());
		assertEquals(Achievement.scoreId(Difficulty.NORMAL, 250),
			ChinSkin.VORKI.getUnlockAchievementId());
		assertEquals(Achievement.ID_LOADOUT_LAB, ChinSkin.LAB_MASCOT.getUnlockAchievementId());
		for (ChinSkin skin : ChinSkin.values())
		{
			if (skin.getUnlockAchievementId() != null)
			{
				assertNotNull(Achievement.byId(skin.getUnlockAchievementId()),
					skin + " gates on a real achievement");
			}
		}
		assertEquals(Achievement.scoreId(Difficulty.EASY, 250),
			BackgroundTheme.PANDEMONIUM.getUnlockAchievementIds()[0]);
		assertEquals(Achievement.streakId(Difficulty.NORMAL, 50, 20),
			BackgroundTheme.INFERNO.getUnlockAchievementIds()[0]);
		assertEquals(Achievement.streakId(Difficulty.HARD, 50, 20),
			BackgroundTheme.INFERNO.getUnlockAchievementIds()[1]);
		for (BackgroundTheme theme : BackgroundTheme.values())
		{
			for (String id : theme.getUnlockAchievementIds())
			{
				assertNotNull(Achievement.byId(id), theme + " gates on a real achievement");
			}
		}
	}

	@Test
	@DisplayName("mystery rewards stay sealed as ?????? until earned, then reveal")
	void mysteryRewards()
	{
		final Achievement easy250 = Achievement.byId(Achievement.scoreId(Difficulty.EASY, 250));
		final Achievement normal250 = Achievement.byId(Achievement.scoreId(Difficulty.NORMAL, 250));
		final Achievement hard250 = Achievement.byId(Achievement.scoreId(Difficulty.HARD, 250));
		final Achievement streakN = Achievement.byId(Achievement.streakId(Difficulty.NORMAL, 50, 20));
		final Achievement streakH = Achievement.byId(Achievement.streakId(Difficulty.HARD, 50, 20));

		for (Achievement sealed : new Achievement[]{easy250, normal250, hard250, streakN, streakH})
		{
			assertEquals("Unlocks: ??????", sealed.getRewardDisplay(false),
				sealed.getId() + " stays sealed");
		}
		assertEquals("Unlocks: Pandemonium", easy250.getRewardDisplay(true));
		assertEquals("Unlocks: Vorki", normal250.getRewardDisplay(true));
		assertEquals("You're insane.", hard250.getRewardDisplay(true),
			"the Hard 250's uncovered text is the reward");
		assertEquals("Unlocks: Inferno", streakN.getRewardDisplay(true));
		assertEquals("Unlocks: Inferno", streakH.getRewardDisplay(true));

		assertEquals("Unlocks: Lumbridge", Achievement.byId(Achievement.levelId(10))
			.getRewardDisplay(false), "zone rewards never seal");
		assertNull(Achievement.byId(Achievement.ID_ZERO_POLES).getRewardDisplay(false));
	}

	@Test
	@DisplayName("every non-chinchompa critter reward is sealed; red and black stay advertised")
	void nonChinRewardsSealed()
	{
		assertEquals("Unlocks: Red Chinchompa", Achievement
			.byId(Achievement.scoreId(Difficulty.NORMAL, 20)).getRewardDisplay(false));
		assertEquals("Unlocks: Black Chinchompa", Achievement
			.byId(Achievement.scoreId(Difficulty.HARD, 40)).getRewardDisplay(false));
		for (int t : new int[]{60, 80, 100})
		{
			assertEquals("Unlocks: ??????", Achievement
				.byId(Achievement.scoreId(Difficulty.NORMAL, t)).getRewardDisplay(false),
				"Normal " + t + " seals its critter");
			assertEquals("Unlocks: ??????", Achievement
				.byId(Achievement.scoreId(Difficulty.HARD, t)).getRewardDisplay(false),
				"Hard " + t + " seals its critter");
		}
		assertEquals("Unlocks: ??????", Achievement
			.byId(Achievement.scoreId(Difficulty.EASY, 100)).getRewardDisplay(false),
			"the cabbage row seals too");
	}

	@Test
	@DisplayName("the secret achievement masks itself entirely until earned")
	void secretMasking()
	{
		final Achievement secret = Achievement.byId(Achievement.ID_CABBAGE_INFERNO);
		assertEquals("??????", secret.getNameDisplay(false));
		assertEquals("A secret achievement.", secret.getDescriptionDisplay(false));
		assertEquals("Well Done", secret.getNameDisplay(true));
		assertEquals("Fly a cabbage through the Inferno.", secret.getDescriptionDisplay(true));

		final Achievement lab = Achievement.byId(Achievement.ID_LOADOUT_LAB);
		assertEquals("??????", lab.getNameDisplay(false), "the crossover stays secret");
		assertEquals("Unlocks: ??????", lab.getRewardDisplay(false));
		assertEquals("Unlocks: Loadie", lab.getRewardDisplay(true));

		final Achievement ordinary = Achievement.byId(Achievement.ID_ZERO_POLES);
		assertEquals("Instant Kaboom", ordinary.getNameDisplay(false), "non-secrets never mask");
	}

	@Test
	@DisplayName("every critter and zone unlock is attributed somewhere")
	void everyUnlockAttributed()
	{
		int rewardRows = 0;
		for (Achievement a : Achievement.ALL)
		{
			if (a.getReward() != null)
			{
				rewardRows++;
			}
		}
		// 6 score ranks x (Normal + Hard rows) + 10 level zones + the
		// Cabbage's Easy 100 row + three sealed 250 rows (the jad shares
		// the already-counted Hard 100 row; Vorki and Pandemonium and the
		// Hard 250's message are the 250s) + Inferno's two streak rows
		// + the Loadout Lab crossover row.
		assertEquals(29, rewardRows);
		assertNotNull(Achievement.byId(Achievement.ID_CHANGE_ZONE));
		assertNotNull(Achievement.byId(Achievement.ID_CHANGE_CRITTER));
	}
}
