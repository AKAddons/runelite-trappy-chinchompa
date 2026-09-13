package com.trappychinchompa.duel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** The account id never leaves the client raw (Andrew, 2026-09-12:
 * "definitely add hashing"): the relay binds names to a SHA-256 of the
 * RuneLite account hash under the shared akaddons prefix, and keys that
 * again itself. Every AKAddons plugin must derive the same value, so one
 * name stays one account across plugins. */
class HashesTest
{
	@Test
	@DisplayName("the account id sent to the relay is a stable SHA-256 of the RuneLite account hash under the akaddons prefix, never the number itself")
	void accountId()
	{
		String id = Hashes.account(1234567890123L);
		assertEquals(Hashes.sha256Hex("akaddons:1234567890123"), id);
		assertEquals(64, id.length());
		assertFalse(id.contains("1234567890123"));
		assertEquals(id, Hashes.account(1234567890123L), "stable across calls and sessions");
		assertNotEquals(id, Hashes.account(1234567890124L));
	}
}
