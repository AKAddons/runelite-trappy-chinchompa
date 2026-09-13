package com.trappychinchompa.duel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelChatTest
{
	@Test
	@DisplayName("the accept and decline options appear only on the pending challenger's name")
	void menuFor()
	{
		assertTrue(DuelChat.menuFor("Zezima", "Zezima"));
		assertTrue(DuelChat.menuFor("Zezima", "<col=ffffff>zezima</col>"));
		assertTrue(DuelChat.menuFor("Big Brutus", "Big Brutus"));
		assertFalse(DuelChat.menuFor("Zezima", "Durial321"));
		assertFalse(DuelChat.menuFor(null, "Zezima"));
		assertFalse(DuelChat.menuFor("Zezima", null));
	}

	@Test
	@DisplayName("only the chatbox's own player options anchor the duel entries")
	void anchorsOnChatboxPlayerOptions()
	{
		assertTrue(DuelChat.anchorOption("Message"));
		assertTrue(DuelChat.anchorOption("Add ignore"));
		assertFalse(DuelChat.anchorOption("Walk here"));
		assertFalse(DuelChat.anchorOption(null));
	}
}
