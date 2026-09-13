package com.trappychinchompa.ui;

import com.trappychinchompa.Difficulty;
import com.trappychinchompa.duel.DuelCommand;
import com.trappychinchompa.duel.DuelController;
import com.trappychinchompa.duel.DuelLedger;
import com.trappychinchompa.duel.DuelOutcome;
import com.trappychinchompa.duel.DuelRecord;
import com.trappychinchompa.duel.DuelResultText;
import com.trappychinchompa.duel.DuelRules;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The duel dock under the game: every duel in flight as one line with
 * its chip (Play / Concede / Done), the lobby line when a challenge is
 * pending, and a peek bar that opens into the challenge form, Compete,
 * your record and the top scores. Peek-and-expand, after the Goal
 * Planner dock: the game stays above, nothing pops up.
 */
final class DuelDock extends JPanel
{
	private static final int PEEK_H = 28;
	/** A settled line shows itself this long, then goes; the Record keeps it. */
	private static final long RESULT_LINGER_MS = 15_000;
	private static final Color BTN_BG = new Color(0x3B, 0x3B, 0x3B);
	private static final Color BTN_HOVER = new Color(0x4C, 0x4C, 0x52);
	private static final Color BTN_FG = new Color(0xDC, 0xDC, 0xDC);
	private static final Color HINT_FG = new Color(0x9A, 0x9A, 0x9A);
	private static final Color CREATE_BG = new Color(0x1D, 0x2A, 0x1F);
	private static final Color CREATE_HOVER = new Color(0x26, 0x38, 0x29);
	private static final Color CREATE_FG = new Color(0xBF, 0xE0, 0xBF);
	private static final Color LINE_BG = new Color(0x2C, 0x2C, 0x30);
	private static final Color PILL_ON = new Color(0x5A, 0x4A, 0x1A);
	private static final Color PILL_ON_HOVER = new Color(0x6B, 0x58, 0x20);
	private static final Color FIELD_BG = new Color(0x22, 0x22, 0x22);

	private final Supplier<DuelController> duels;
	private final Supplier<Difficulty> currentDifficulty;
	private final BiConsumer<String, DuelRules> send;
	private final Consumer<Difficulty> compete;
	private final Consumer<String> play;
	private final Runnable focusGame;

	private final JPanel lines = new JPanel();
	private final Rounded.Button challengePeek;
	private final Rounded.Button duelPeek;
	private final JPanel surface = new JPanel();
	private final JPanel form = new JPanel();
	private final JPanel record = new JPanel();
	private final JPanel scores = new JPanel();
	private final JTextField name = new JTextField();
	private final JLabel command = new JLabel();
	private final List<Rounded.Button> difficultyPills = new ArrayList<>();
	private final List<Rounded.Button> metricPills = new ArrayList<>();
	private final List<Rounded.Button> seriesPills = new ArrayList<>();
	/** One selector for the whole Challenge side: the difficulty to compete at and the board to show. */
	private final List<Rounded.Button> challengePills = new ArrayList<>();
	private Rounded.Button competeLead;

	/** Which half of the bar is open: nothing, Challenge (compete, record, top scores), or Duel a friend (the form). */
	private enum Open
	{
		NONE, CHALLENGE, FRIEND
	}

	private Open open = Open.NONE;
	private final JPanel leads = new JPanel();
	private int difficulty = Difficulty.NORMAL.ordinal();
	private int metric;
	private int series = 1;
	private int boardTab = Difficulty.NORMAL.ordinal();
	private String lastBoardKey;

	DuelDock(Supplier<DuelController> duels, Supplier<Difficulty> currentDifficulty, BiConsumer<String, DuelRules> send,
		Consumer<Difficulty> compete, Consumer<String> play, Runnable focusGame)
	{
		super(new BorderLayout());
		this.duels = duels;
		this.currentDifficulty = currentDifficulty;
		this.send = send;
		this.compete = compete;
		this.play = play;
		this.focusGame = focusGame;
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

		final JPanel column = new JPanel();
		column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
		column.setOpaque(false);
		lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
		lines.setOpaque(false);
		column.add(lines);
		surface.setLayout(new BoxLayout(surface, BoxLayout.Y_AXIS));
		surface.setOpaque(false);
		surface.setVisible(false);
		column.add(surface);
		add(column, BorderLayout.CENTER);

		challengePeek = new Rounded.Button("+ Challenge", CREATE_BG, CREATE_HOVER, CREATE_FG);
		challengePeek.setFont(FontManager.getRunescapeSmallFont());
		challengePeek.addActionListener(e -> toggle(Open.CHALLENGE));
		duelPeek = new Rounded.Button("+ Duel a friend", CREATE_BG, CREATE_HOVER, CREATE_FG);
		duelPeek.setFont(FontManager.getRunescapeSmallFont());
		duelPeek.addActionListener(e -> toggle(Open.FRIEND));
		final JPanel bar = new JPanel(new GridLayout(1, 2, 6, 0));
		bar.setOpaque(false);
		bar.setPreferredSize(new Dimension(0, PEEK_H));
		bar.add(challengePeek);
		bar.add(duelPeek);
		add(bar, BorderLayout.SOUTH);

		buildLeads();
		buildForm();
		buildRecord();
		buildScores();
		surface.add(form);
		surface.add(scores);
		surface.add(record);
		surface.add(leads);
		// Clocks on the duel lines tick down; redraw them now and then.
		new Timer(5_000, e -> refresh()).start();
		refresh();
	}

	// ---- the two ways in: compete now, or challenge a friend ----

	private void buildLeads()
	{
		leads.setLayout(new BoxLayout(leads, BoxLayout.Y_AXIS));
		leads.setOpaque(false);
		leads.setAlignmentX(LEFT_ALIGNMENT);
		leads.add(Box.createVerticalStrut(6));
		leads.add(row("Difficulty", pills(challengePills, new String[]{"Easy", "Normal", "Hard"}, i ->
		{
			boardTab = i;
			lastBoardKey = null;
		})));
		competeLead = lead("Compete", e ->
		{
			compete.accept(Difficulty.values()[boardTab]);
			open = Open.NONE;
			refresh();
			focusGame.run();
		});
		leads.add(competeLead);
		leads.add(Box.createVerticalStrut(4));
	}

	// ---- the form ----

	private void buildForm()
	{
		form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
		form.setOpaque(false);
		form.setAlignmentX(LEFT_ALIGNMENT);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setBackground(FIELD_BG);
		name.setForeground(Color.WHITE);
		name.setCaretColor(Color.WHITE);
		name.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		name.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshCommand();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshCommand();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshCommand();
			}
		});
		final Rounded.Panel nameBox = new Rounded.Panel(new BorderLayout());
		nameBox.setBackground(FIELD_BG);
		nameBox.add(name, BorderLayout.CENTER);
		form.add(row("Player", nameBox));
		form.add(row("Difficulty", pills(difficultyPills, new String[]{"Easy", "Normal", "Hard"}, i -> difficulty = i)));
		form.add(row("Scored by", pills(metricPills, new String[]{"Best round", "Average"}, i -> metric = i)));
		form.add(row("Games", pills(seriesPills, new String[]{"1", "3", "5"}, i -> series = i)));

		command.setFont(FontManager.getRunescapeSmallFont());
		command.setForeground(BTN_FG);
		command.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		final Rounded.Panel commandBox = new Rounded.Panel(new BorderLayout());
		commandBox.setBackground(FIELD_BG);
		commandBox.add(command, BorderLayout.CENTER);
		commandBox.add(chip("Copy", e -> copyCommand()), BorderLayout.EAST);
		form.add(row("Chat", commandBox));

		form.add(Box.createVerticalStrut(4));
		form.add(lead("Send challenge", e -> sendChallenge()));
		form.add(Box.createVerticalStrut(6));
	}

	private Rounded.Button lead(String label, java.awt.event.ActionListener action)
	{
		final Rounded.Button b = new Rounded.Button(label, CREATE_BG, CREATE_HOVER, CREATE_FG);
		b.setFont(FontManager.getRunescapeBoldFont().deriveFont(12f));
		b.setAlignmentX(LEFT_ALIGNMENT);
		b.setMaximumSize(new Dimension(Integer.MAX_VALUE, PEEK_H));
		b.addActionListener(action);
		return b;
	}

	private JPanel row(String label, JPanel field)
	{
		final JPanel r = new JPanel(new BorderLayout(6, 0));
		r.setOpaque(false);
		r.setAlignmentX(LEFT_ALIGNMENT);
		r.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
		final JLabel l = new JLabel(label);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(HINT_FG);
		l.setPreferredSize(new Dimension(62, PEEK_H));
		r.add(l, BorderLayout.WEST);
		r.add(field, BorderLayout.CENTER);
		r.setMaximumSize(new Dimension(Integer.MAX_VALUE, r.getPreferredSize().height + 6));
		return r;
	}

	private JPanel pills(List<Rounded.Button> into, String[] labels, java.util.function.IntConsumer pick)
	{
		final JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		p.setOpaque(false);
		for (int i = 0; i < labels.length; i++)
		{
			final int index = i;
			final Rounded.Button b = chip(labels[i], e ->
			{
				pick.accept(index);
				refresh();
			});
			into.add(b);
			p.add(b);
		}
		return p;
	}

	private Rounded.Button chip(String label, java.awt.event.ActionListener action)
	{
		final Rounded.Button b = new Rounded.Button(label, BTN_BG, BTN_HOVER, BTN_FG);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.addActionListener(action);
		return b;
	}

	private static void paintPills(List<Rounded.Button> pills, int on)
	{
		for (int i = 0; i < pills.size(); i++)
		{
			final boolean sel = i == on;
			pills.get(i).recolour(sel ? PILL_ON : BTN_BG, sel ? PILL_ON_HOVER : BTN_HOVER, sel ? GameCanvas.TEXT_YELLOW : BTN_FG);
		}
	}

	private DuelRules rules()
	{
		return new DuelRules(Difficulty.values()[difficulty],
			metric == 0 ? DuelRules.Metric.BEST_ROUND : DuelRules.Metric.AVERAGE, DuelRules.SERIES[series]);
	}

	private void refreshCommand()
	{
		final String player = name.getText().trim();
		command.setText(player.isEmpty() ? "::duel <player> trappy" : DuelCommand.format(player, rules(), currentDifficulty.get()));
	}

	private void copyCommand()
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(command.getText()), null);
		}
		catch (RuntimeException e)
		{
			// Headless or clipboard busy: the line is still on screen.
		}
	}

	private void sendChallenge()
	{
		final String player = name.getText().trim();
		if (player.isEmpty())
		{
			name.requestFocusInWindow();
			return;
		}
		send.accept(player, rules());
		open = Open.NONE;
		refresh();
		focusGame.run();
	}

	// ---- record and top scores ----

	private void buildRecord()
	{
		record.setLayout(new BoxLayout(record, BoxLayout.Y_AXIS));
		record.setOpaque(false);
		record.setAlignmentX(LEFT_ALIGNMENT);
	}

	private void buildScores()
	{
		scores.setLayout(new BoxLayout(scores, BoxLayout.Y_AXIS));
		scores.setOpaque(false);
		scores.setAlignmentX(LEFT_ALIGNMENT);
	}

	private void fillRecord(DuelController d)
	{
		record.removeAll();
		final DuelLedger ledger = d.getLedger();
		final Map<String, Object> h = ledger.history();
		record.add(heading("Record"));
		record.add(line("Duels: " + ledger.getWins() + " W - " + ledger.getLosses() + " L", Color.WHITE));
		final Object opponents = h.get("opponents");
		if (opponents instanceof Map)
		{
			int shown = 0;
			for (Map.Entry<?, ?> e : ((Map<?, ?>) opponents).entrySet())
			{
				if (shown++ >= 6 || !(e.getValue() instanceof Map))
				{
					break;
				}
				final Map<?, ?> vs = (Map<?, ?>) e.getValue();
				record.add(line("  vs " + e.getKey() + ": " + num(vs.get("wins")) + " W - " + num(vs.get("losses")) + " L", HINT_FG));
			}
		}
		final Object comp = h.get("compete");
		if (comp instanceof Map)
		{
			final Map<?, ?> c = (Map<?, ?>) comp;
			record.add(line("Compete: " + num(c.get("wins")) + " W - " + num(c.get("losses")) + " L", Color.WHITE));
			final Object by = h.get("competeBy");
			if (by instanceof Map)
			{
				for (String k : new String[]{"e", "n", "h"})
				{
					final Object v = ((Map<?, ?>) by).get(k);
					if (v instanceof Map)
					{
						final Map<?, ?> m = (Map<?, ?>) v;
						record.add(line("  " + (k.equals("e") ? "Easy" : k.equals("n") ? "Normal" : "Hard") + ": "
							+ num(m.get("wins")) + " W - " + num(m.get("losses")) + " L", HINT_FG));
					}
				}
			}
			final Object ratings = h.get("ratings");
			if (ratings instanceof Map && !((Map<?, ?>) ratings).isEmpty())
			{
				final StringBuilder sb = new StringBuilder("Rating:");
				for (String k : new String[]{"e", "n", "h"})
				{
					final Object v = ((Map<?, ?>) ratings).get(k);
					if (v != null)
					{
						sb.append(sb.length() > 7 ? "," : "").append(' ')
							.append(k.equals("e") ? "Easy" : k.equals("n") ? "Normal" : "Hard").append(' ').append(num(v));
					}
				}
				record.add(line(sb.toString(), HINT_FG));
			}
		}
		final Object recent = h.get("recent");
		if (recent instanceof List)
		{
			int shown = 0;
			for (Object o : (List<?>) recent)
			{
				if (shown++ >= 5 || !(o instanceof Map))
				{
					break;
				}
				final Map<?, ?> r = (Map<?, ?>) o;
				record.add(line("  " + r.get("result") + " vs " + r.get("opponent") + ", " + r.get("scoreLine")
					+ ("compete".equals(r.get("kind")) ? " (compete)" : "") + (Boolean.TRUE.equals(r.get("dev")) ? " (dev)" : ""), HINT_FG));
			}
		}
	}

	private void fillScores(DuelController d)
	{
		scores.removeAll();
		final String title = new String[]{"Easy", "Normal", "Hard"}[boardTab];
		scores.add(heading("Top scores - " + title + (d.isDevBoards() ? " (dev)" : "")));
		final String key = "enh".substring(boardTab, boardTab + 1);
		if (!key.equals(lastBoardKey))
		{
			lastBoardKey = key;
			d.refreshBoards(key);
		}
		final List<Map<String, Object>> top = d.board(key);
		final List<Map<String, Object>> ranks = d.rankings(key);
		if (top == null || top.isEmpty())
		{
			scores.add(line(top == null ? "Loading..." : "No verified scores yet.", HINT_FG));
		}
		else
		{
			int i = 1;
			for (Map<String, Object> e : top)
			{
				if (i > 10)
				{
					break;
				}
				final boolean me = com.trappychinchompa.duel.Names.same(String.valueOf(e.get("name")), d.getName());
				scores.add(line("  " + i++ + ". " + e.get("name") + "  " + num(e.get("score")), me ? GameCanvas.TEXT_YELLOW : Color.WHITE));
			}
		}
		if (ranks != null && !ranks.isEmpty())
		{
			scores.add(line("Rankings", HINT_FG));
			int i = 1;
			for (Map<String, Object> e : ranks)
			{
				if (i > 5)
				{
					break;
				}
				scores.add(line("  " + i++ + ". " + e.get("name") + "  " + num(e.get("rating")), Color.WHITE));
			}
		}
	}

	private static int num(Object o)
	{
		return o instanceof Number ? ((Number) o).intValue() : 0;
	}

	private JLabel heading(String text)
	{
		final JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(GameCanvas.TEXT_YELLOW);
		l.setBorder(BorderFactory.createEmptyBorder(8, 4, 2, 4));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel line(String text, Color color)
	{
		final JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 8));
		l.setAlignmentX(LEFT_ALIGNMENT);
		return l;
	}

	// ---- the lines: lobby + every duel in flight ----

	private JPanel duelLine(String text, Color color, String tooltip, List<Rounded.Button> chips)
	{
		final Rounded.Panel p = new Rounded.Panel(new BorderLayout(4, 0));
		p.setBackground(LINE_BG);
		p.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 4));
		p.setAlignmentX(LEFT_ALIGNMENT);
		final JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setToolTipText(tooltip);
		p.add(l, BorderLayout.CENTER);
		final JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		right.setOpaque(false);
		for (Rounded.Button b : chips)
		{
			right.add(b);
		}
		p.add(right, BorderLayout.EAST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, PEEK_H + 2));
		final JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		wrap.add(p, BorderLayout.CENTER);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, PEEK_H + 6));
		return wrap;
	}

	/** "23-4", "16-24", or "4-4, SD 2-3": the score line with the words taken out. */
	private static String compactScore(DuelOutcome o)
	{
		if (o.getGames().isEmpty())
		{
			return "";
		}
		return o.getScoreLine().replace("best ", "").replace("total ", "").replace(" to ", "-").replace(", sudden death ", ", SD ");
	}

	private static String clock(long ms)
	{
		final long s = Math.max(0, ms / 1000);
		return (s / 60) + ":" + String.format("%02d", s % 60);
	}

	private void fillLines(DuelController d)
	{
		lines.removeAll();
		final List<Rounded.Button> chips = new ArrayList<>();
		switch (d.getPhase())
		{
			case SENT:
				chips.add(chip("Cancel", e -> act(d::cancel)));
				lines.add(duelLine("Challenge sent to " + d.getOpponent() + " - waiting", GameCanvas.TEXT_YELLOW,
					d.getRules() == null ? null : d.getRules().describe(), chips));
				break;
			case INCOMING:
				chips.add(chip("Accept", e -> act(d::accept)));
				chips.add(chip("Decline", e -> act(d::decline)));
				lines.add(duelLine(d.getOpponent() + " challenges you!", GameCanvas.TEXT_YELLOW,
					d.getRules() == null ? null : d.getRules().describe(), chips));
				break;
			case QUEUED:
				chips.add(chip("Cancel", e -> act(d::cancel)));
				lines.add(duelLine("Looking for an opponent...", GameCanvas.TEXT_YELLOW,
					d.getRules() == null ? null : d.getRules().describe(), chips));
				break;
			default:
				if (d.getNoMatch() != null)
				{
					chips.add(chip("Retry", e -> act(d::retryCompete)));
					chips.add(chip("Dismiss", e -> act(d::dismissNoMatch)));
					lines.add(duelLine("No opponent found (" + d.getNoMatch().getDifficulty().name().charAt(0)
						+ d.getNoMatch().getDifficulty().name().substring(1).toLowerCase() + ")", HINT_FG, null, chips));
				}
				break;
		}
		final long now = System.currentTimeMillis();
		final List<String> expired = new ArrayList<>();
		for (DuelRecord r : d.getLedger().duels())
		{
			if (r.isDone() && r.getSettledAt() > 0 && now - r.getSettledAt() > RESULT_LINGER_MS)
			{
				expired.add(r.getId());
				continue;
			}
			final List<Rounded.Button> c = new ArrayList<>();
			final String who = "vs " + r.getOpponent();
			String text;
			Color color = Color.WHITE;
			final boolean playingNow = r.getId().equals(d.getPlayingId()) && r.nextGame() > 0;
			final boolean starting = playingNow && !r.hasSeed(r.nextGame());
			switch (playingNow ? DuelRecord.Todo.WAIT : r.todo())
			{
				case PLAY:
					text = who + " - play " + r.gameLabel(r.nextGame()) + " - " + clock(r.timeLeft(now));
					color = GameCanvas.TEXT_YELLOW;
					c.add(chip("Play", e -> play.accept(r.getId())));
					c.add(chip("Concede", e -> act(() -> d.concede(r.getId()))));
					break;
				case WAIT:
					if (starting)
					{
						text = who + " - starting " + r.gameLabel(r.nextGame()) + "...";
						color = CREATE_FG;
					}
					else if (playingNow)
					{
						text = "NOW: " + who + " - " + r.gameLabel(r.nextGame()) + " in progress";
						color = CREATE_FG;
					}
					else
					{
						text = who + " - waiting for them - " + clock(r.timeLeft(now));
					}
					c.add(chip("Concede", e -> act(() -> d.concede(r.getId()))));
					break;
				case DONE:
				default:
					final DuelOutcome o = r.outcome();
					text = DuelResultText.headlineTitle(o, r.getMe()).replace("!", "") + " " + who + " " + compactScore(o);
					color = GameCanvas.TEXT_ORANGE;
					c.add(chip("Done", e -> act(() -> d.dismiss(r.getId()))));
					break;
			}
			// Friendly duels carry their rules as a tooltip; compete is always one game, so nothing to say.
			final String tip = r.isCompete() || r.isDone() || r.getRules() == null ? null : r.getRules().describe();
			lines.add(duelLine(text, color, tip, c));
		}
		// Dismiss after the loop: dismiss() repaints, and a repaint mid-build would re-enter this method.
		for (String id : expired)
		{
			d.getLedger().dismiss(id);
		}
	}

	private void act(Runnable r)
	{
		r.run();
		refresh();
		focusGame.run();
	}

	// ---- peek and expand ----

	private void toggle(Open which)
	{
		final DuelController d = duels.get();
		if (d == null || !d.isOnline())
		{
			return;
		}
		open = open == which ? Open.NONE : which;
		if (open == Open.FRIEND)
		{
			difficulty = currentDifficulty.get().ordinal();
		}
		else if (open == Open.CHALLENGE)
		{
			boardTab = currentDifficulty.get().ordinal();
			lastBoardKey = null;
			d.refreshHistory();
		}
		refresh();
	}

	private boolean refreshing;

	/** The controller changed, the clock ticked, or the panel came back: redraw. */
	void refresh()
	{
		if (refreshing)
		{
			return;
		}
		refreshing = true;
		try
		{
			redraw();
		}
		finally
		{
			refreshing = false;
		}
	}

	private void redraw()
	{
		final DuelController d = duels.get();
		if (d == null || !d.isOnline())
		{
			challengePeek.setText("Duels off");
			duelPeek.setText("Enable in settings");
			challengePeek.recolour(BTN_BG, BTN_BG, HINT_FG);
			duelPeek.recolour(BTN_BG, BTN_BG, HINT_FG);
			open = Open.NONE;
			lines.removeAll();
		}
		else
		{
			challengePeek.setText(open == Open.CHALLENGE ? "- Challenge" : "+ Challenge");
			duelPeek.setText(open == Open.FRIEND ? "- Duel a friend" : "+ Duel a friend");
			challengePeek.recolour(CREATE_BG, CREATE_HOVER, CREATE_FG);
			duelPeek.recolour(CREATE_BG, CREATE_HOVER, CREATE_FG);
			fillLines(d);
			if (open == Open.FRIEND)
			{
				paintPills(difficultyPills, difficulty);
				paintPills(metricPills, metric);
				paintPills(seriesPills, series);
				refreshCommand();
			}
			else if (open == Open.CHALLENGE)
			{
				paintPills(challengePills, boardTab);
				competeLead.setText("Compete: " + new String[]{"Easy", "Normal", "Hard"}[boardTab] + ", 1 game");
				competeLead.setToolTipText("One game against the next player who queues at this difficulty");
				fillRecord(d);
				fillScores(d);
			}
		}
		leads.setVisible(open == Open.CHALLENGE);
		record.setVisible(open == Open.CHALLENGE);
		scores.setVisible(open == Open.CHALLENGE);
		form.setVisible(open == Open.FRIEND);
		surface.setVisible(open != Open.NONE);
		revalidate();
		repaint();
	}
}
