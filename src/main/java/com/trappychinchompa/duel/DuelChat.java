package com.trappychinchompa.duel;

import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.util.Text;

/**
 * The chatbox side of duels: right-click any player's name for "Duel",
 * or the pending challenger's name for "Accept duel" / "Decline duel".
 * Runs on the client thread; the controller is reached through the EDT.
 */
@Slf4j
public final class DuelChat
{
	public interface Actions
	{
		/** Name of the player whose challenge is waiting on us, or null. Read on the client thread. */
		String pendingChallenger();

		/** True while a fresh challenge could be sent (relay online, nothing in flight). */
		boolean canChallenge();

		void challengeFromChat(String name);

		void acceptFromChat();

		void declineFromChat();
	}

	static final String DUEL = "Duel";
	static final String ACCEPT = "Accept duel";
	static final String DECLINE = "Decline duel";

	private final Client client;
	private final Actions actions;

	public DuelChat(Client client, Actions actions)
	{
		this.client = client;
		this.actions = actions;
	}

	/** Do the accept/decline options belong on this chat name? */
	public static boolean menuFor(String pendingChallenger, String target)
	{
		if (pendingChallenger == null || target == null)
		{
			return false;
		}
		return Names.same(Text.removeTags(pendingChallenger), Text.removeTags(target));
	}

	/** The chatbox's own player options that the duel entries sit beside. */
	public static boolean anchorOption(String option)
	{
		return "Message".equals(option) || "Add ignore".equals(option);
	}

	/** Called from the plugin's MenuEntryAdded subscriber. */
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		// Chat-line player options are low priority ("Walk here" outranks them), so accept both kinds.
		final boolean componentOp = event.getType() == MenuAction.CC_OP.getId()
			|| event.getType() == MenuAction.CC_OP_LOW_PRIORITY.getId();
		if (!componentOp || !anchorOption(event.getOption()))
		{
			return;
		}
		if (WidgetUtil.componentToInterface(event.getActionParam1()) != InterfaceID.CHATBOX)
		{
			return;
		}
		final String target = event.getTarget();
		final String name = Text.toJagexName(Text.removeTags(target));
		if (menuFor(actions.pendingChallenger(), target))
		{
			client.createMenuEntry(-1)
				.setOption(DECLINE)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(actions::declineFromChat));
			client.createMenuEntry(-1)
				.setOption(ACCEPT)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(actions::acceptFromChat));
			return;
		}
		if (actions.canChallenge() && !name.isEmpty())
		{
			client.createMenuEntry(-1)
				.setOption(DUEL)
				.setTarget(target)
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(() -> actions.challengeFromChat(name)));
		}
	}
}
