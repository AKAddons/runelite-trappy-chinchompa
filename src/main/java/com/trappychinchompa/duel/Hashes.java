package com.trappychinchompa.duel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 helpers for commits, channel keys and trace hashes. */
public final class Hashes
{
	private Hashes()
	{
	}

	/** The id the relay binds a name to: a SHA-256 of the RuneLite account
	 * hash under the prefix every AKAddons plugin shares, so the number
	 * itself never leaves the client and one name stays one account
	 * across plugins. The relay keys it again with its own secret. */
	public static String account(long accountHash)
	{
		return sha256Hex("akaddons:" + accountHash);
	}

	public static String sha256Hex(String text)
	{
		return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
	}

	public static String sha256Hex(byte[] bytes)
	{
		return hex(sha256(bytes));
	}

	public static byte[] sha256(byte[] bytes)
	{
		try
		{
			return MessageDigest.getInstance("SHA-256").digest(bytes);
		}
		catch (NoSuchAlgorithmException e)
		{
			throw new IllegalStateException("SHA-256 missing", e);
		}
	}

	public static String hex(byte[] bytes)
	{
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes)
		{
			sb.append(Character.forDigit((b >> 4) & 0xF, 16));
			sb.append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
