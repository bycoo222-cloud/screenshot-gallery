/*
 * Copyright (c) 2026, bycoo222
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.screenshotgallery;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseMotionAdapter;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * The sidebar: "on this day" at the top, then the filters (type, character, search) and the pictures newest first,
 * a heading per month, {@link #PAGE} at a time.
 */
final class GalleryPanel extends PluginPanel
{
	static final int PAD = 10;
	static final int WIDTH = PANEL_WIDTH - 2 * PAD;
	private static final int PAGE = 24;
	private static final int GAP = 6;
	private static final String ALL_CHARACTERS = "All characters";
	private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.ENGLISH);
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

	private final ScreenshotGalleryPlugin plugin;
	private final Thumbs thumbs;
	private final ScreenshotGalleryConfig config;

	private final JPanel memory = new JPanel();
	private final JComboBox<String> characters = new JComboBox<>();
	private final IconTextField search = new IconTextField();
	private final JLabel count = new JLabel();
	private final JLabel status = new JLabel();
	private final JPanel list = new JPanel();
	private final JButton more = new JButton("Show more");

	private List<Shot> all = new ArrayList<>();
	private List<Shot> shown = new ArrayList<>();
	private int upTo;
	private int gen;
	private boolean filling;   // the combo boxes are being refilled: their events are not the player's
	/** the album shown; null = all pictures */
	private String album;
	/** the auto album shown (a screenshot type, e.g. "Boss Kills"); null = none. Never set together with {@link #album}. */
	private String autoType;
	private final JPanel albumBar = new JPanel(new WrapLayout(FlowLayout.LEFT, 0, 0));
	private final JPanel sizeBar = new JPanel();

	/*
	 * The design (user, 2026-11-07, choosing between three looks: "b overlay grid for photos"): each picture edge to edge,
	 * its title and date on a dark fade over its lower part, and a corner badge — RuneLite's small skill icon for a
	 * level-up, a coloured tag for a pet, death, boss kill, clue, collection log slot or drop.
	 */

	GalleryPanel(ScreenshotGalleryPlugin plugin, Thumbs thumbs, ScreenshotGalleryConfig config)
	{
		this.plugin = plugin;
		this.thumbs = thumbs;
		this.config = config;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(new EmptyBorder(PAD, PAD, PAD, PAD));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel top = row();
		top.setLayout(new BorderLayout());
		JLabel title = new JLabel("Screenshot Gallery");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		top.add(title, BorderLayout.WEST);
		JLabel folder = link("Import", "Add your older screenshots: pick RuneLite's screenshots folder (in your .runelite folder)",
			plugin::importOld);
		top.add(folder, BorderLayout.EAST);
		add(top);
		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setAlignmentX(LEFT_ALIGNMENT);
		status.setBorder(new EmptyBorder(4, 0, 0, 0));
		status.setVisible(false);
		add(status);
		add(Box.createVerticalStrut(6));
		// the albums: All, each album, + New album (drop a picture on one to add it; right-click one to rename / delete)
		albumBar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		albumBar.setAlignmentX(LEFT_ALIGNMENT);
		add(albumBar);
		add(Box.createVerticalStrut(6));

		memory.setLayout(new BoxLayout(memory, BoxLayout.Y_AXIS));
		memory.setBackground(ColorScheme.DARK_GRAY_COLOR);
		memory.setAlignmentX(LEFT_ALIGNMENT);
		memory.setVisible(false);
		add(memory);

		characters.setToolTipText("Show one character's screenshots");
		characters.addActionListener(e -> refilter());
		characters.setVisible(false);
		search.setIcon(IconTextField.Icon.SEARCH);
		search.setPreferredSize(new Dimension(WIDTH, 30));
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		search.setToolTipText("Search by name, like a skill, a boss or an item");
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refilter();
			}
		});
		for (Component c : new Component[]{characters, search})
		{
			JPanel r = row();
			r.setLayout(new BorderLayout());
			r.add(c, BorderLayout.CENTER);
			r.setBorder(new EmptyBorder(0, 0, 4, 0));
			add(r);
		}

		count.setFont(FontManager.getRunescapeSmallFont());
		count.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		JPanel countRow = row();
		countRow.setLayout(new BorderLayout());
		countRow.setBorder(new EmptyBorder(2, 0, 6, 0));
		countRow.add(count, BorderLayout.WEST);
		sizeBar.setLayout(new BoxLayout(sizeBar, BoxLayout.X_AXIS));
		sizeBar.setOpaque(false);
		countRow.add(sizeBar, BorderLayout.EAST);
		add(countRow);
		drawSizes();
		drawAlbums();

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		list.setBackground(ColorScheme.DARK_GRAY_COLOR);
		list.setAlignmentX(LEFT_ALIGNMENT);
		add(list);

		more.setAlignmentX(LEFT_ALIGNMENT);
		more.setFocusPainted(false);
		more.addActionListener(e -> page());
		more.setVisible(false);
		add(Box.createVerticalStrut(6));
		add(more);
	}

	@Override
	public void onActivate()
	{
		plugin.rescan();
	}

	/** A fresh scan (on Swing's thread), keeping the picked album, character and search. */
	void setShots(List<Shot> shots)
	{
		all = shots;
		filling = true;
		try
		{
			refill(characters, ALL_CHARACTERS, shots, s -> s.character.isEmpty() ? "Login screen" : s.character, false);
			characters.setVisible(characters.getItemCount() > 2);
		}
		finally
		{
			filling = false;
		}
		if (autoType != null && shots.stream().noneMatch(s -> autoType.equals(s.type)))
		{
			autoType = null;         // its last picture was deleted: its chip is gone
		}
		drawAlbums();
		refilter();
		showMemory();   // after refilter: its new batch of pictures would throw away the card's request (it stayed blank)
	}

	/** Shows one album (null = all), as a click on its chip does. */
	void showAlbum(String a)
	{
		pick(a);
	}

	/** After an album or title change (Swing's thread): the album chips and the pictures again. */
	void refresh()
	{
		if (album != null && !plugin.albums().names().contains(album))
		{
			album = null;            // the album shown was deleted or renamed
		}
		drawAlbums();
		refilter();
	}

	/** A line under the title (an import's progress); null hides it. */
	void status(String text)
	{
		status.setText(text == null ? "" : text);
		status.setVisible(text != null);
		revalidate();
	}

	/** The picture size changed in the settings. */
	void relayout()
	{
		drawSizes();
		refilter();
		showMemory();
	}

	private void refill(JComboBox<String> box, String allLabel, List<Shot> shots, java.util.function.Function<Shot, String> key,
		boolean counts)
	{
		String picked = box.getSelectedItem() == null ? allLabel : strip((String) box.getSelectedItem());
		Map<String, Integer> n = new TreeMap<>((a, b) -> Shot.OTHER.equals(a) ? 1 : Shot.OTHER.equals(b) ? -1 : a.compareToIgnoreCase(b));
		for (Shot s : shots)
		{
			n.merge(key.apply(s), 1, Integer::sum);
		}
		box.removeAllItems();
		box.addItem(allLabel + (counts ? " (" + shots.size() + ")" : ""));
		String select = box.getItemAt(0);
		for (Map.Entry<String, Integer> e : n.entrySet())
		{
			String item = e.getKey() + (counts ? " (" + e.getValue() + ")" : "");
			box.addItem(item);
			if (e.getKey().equals(picked))
			{
				select = item;
			}
		}
		box.setSelectedItem(select);
	}

	/** "Levels (12)" → "Levels" */
	private static String strip(String item)
	{
		return item.replaceFirst(" \\(\\d+\\)$", "");
	}

	private void refilter()
	{
		if (filling)
		{
			return;
		}
		String who = characters.getSelectedItem() == null ? ALL_CHARACTERS : (String) characters.getSelectedItem();
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		java.util.Set<String> inAlbum = album == null ? null : plugin.albums().keys(album);
		shown = new ArrayList<>();
		for (Shot s : all)
		{
			if (autoType != null && !autoType.equals(s.type))
			{
				continue;
			}
			if (!ALL_CHARACTERS.equals(who) && !who.equals(s.character.isEmpty() ? "Login screen" : s.character))
			{
				continue;
			}
			if (!q.isEmpty() && !plugin.titleOf(s).toLowerCase(Locale.ROOT).contains(q) && !s.name.toLowerCase(Locale.ROOT).contains(q)
				&& !s.type.toLowerCase(Locale.ROOT).contains(q))
			{
				continue;
			}
			if (inAlbum != null && !inAlbum.contains(s.key))
			{
				continue;
			}
			shown.add(s);
		}
		gen = thumbs.newGeneration();
		list.removeAll();
		upTo = 0;
		if (all.isEmpty())
		{
			count.setText("");
			list.add(note("<html><b>No screenshots yet.</b><br><br>RuneLite's own <b>Screenshot</b> plugin takes them. "
				+ "Turn it on in the plugin list and tick what to keep: levels, pets, the collection log, deaths, clue "
				+ "rewards, boss kills and more. Each one shows up here as it's taken.<br><br>Have older ones? Click "
				+ "<b>Import</b> and pick the <b>screenshots</b> folder in your .runelite folder.</html>"));
		}
		else if (album != null && inAlbum.isEmpty())
		{
			count.setText("Empty album");
			list.add(note("<html><b>" + esc(album) + "</b> is empty.<br><br>Drag a picture onto the album's name above, or "
				+ "right-click a picture → <b>Add to album</b>.</html>"));
		}
		else
		{
			count.setText(shown.isEmpty() ? "None match" : shown.size() == 1 ? "1 screenshot" : shown.size() + " screenshots");
			page();
		}
		more.setVisible(upTo < shown.size());
		revalidate();
		repaint();
	}

	/** The next {@link #PAGE} pictures, with a heading where the month changes. */
	private void page()
	{
		int cols = Math.max(1, config.size().perRow);
		boolean small = cols > 1;
		int w = (WIDTH - GAP * (cols - 1)) / cols;
		int end = Math.min(shown.size(), upTo + PAGE);
		JPanel grid = null;
		String month = upTo == 0 ? null : MONTH.format(shown.get(upTo - 1).local());
		for (int i = upTo; i < end; i++)
		{
			Shot s = shown.get(i);
			String m = MONTH.format(s.local());
			if (!m.equals(month))
			{
				month = m;
				list.add(monthHeader(m, i == 0));
				grid = null;
			}
			if (grid == null)
			{
				grid = new JPanel(new GridLayout(0, cols, GAP, GAP));
				grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
				grid.setAlignmentX(LEFT_ALIGNMENT);
				list.add(grid);
			}
			final int index = i;
			grid.add(tile(s, w, !small, () -> plugin.view(shown, index)));
			// a grid's height follows its rows: keep it from stretching in the box layout
			grid.setMaximumSize(new Dimension(WIDTH, grid.getPreferredSize().height));
		}
		upTo = end;
		more.setVisible(upTo < shown.size());
		more.setText("Show more (" + (shown.size() - upTo) + " left)");
		list.revalidate();
		list.repaint();
	}

	/** A picture with its title (and its date when it's one per row); click = the viewer. */
	private JPanel tile(Shot s, int w, boolean dated, Runnable open)
	{
		JPanel t = new JPanel();
		t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
		t.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		t.setBorder(border(false));
		t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		t.setToolTipText("<html>" + esc(plugin.titleOf(s)) + "<br>" + esc(s.type) + " · " + esc(when(s.local()))
			+ "<br><i>right-click for albums, rename, delete · drag onto an album</i></html>");
		// the picture fills the tile; the title (and the date when there is room) on a dark fade over it, a badge
		Overlay ov = new Overlay(w, plugin.titleOf(s), dated || w >= 90 ? when(s.local()) : null, badgeIcon(s), badgeText(s),
			badgeColor(s));
		ov.setAlignmentX(LEFT_ALIGNMENT);
		t.add(ov);
		thumbs.get(s, w, gen, ov::set);
		t.addMouseListener(tileMouse(t, s, open));
		dragFrom(t, s);
		return t;
	}

	/** the tile's hairline frame, orange while hovered */
	private static javax.swing.border.Border border(boolean hover)
	{
		return BorderFactory.createLineBorder(hover ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR, 1);
	}

	/** left-click = the viewer; right-click = the picture's menu; hover = the orange frame */
	private MouseAdapter tileMouse(JPanel t, Shot s, Runnable open)
	{
		return new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					open.run();
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					shotMenu(s, open).show(t, e.getX(), e.getY());
				}
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				if (e.isPopupTrigger())
				{
					shotMenu(s, open).show(t, e.getX(), e.getY());
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				t.setBorder(border(true));
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				t.setBorder(border(false));
			}
		};
	}

	/** drag the tile onto an album chip to add it there */
	private void dragFrom(JPanel t, Shot s)
	{
		t.setTransferHandler(new TransferHandler()
		{
			@Override
			protected Transferable createTransferable(JComponent c)
			{
				return new StringSelection(s.key);
			}

			@Override
			public int getSourceActions(JComponent c)
			{
				return COPY;
			}
		});
		t.addMouseMotionListener(new MouseMotionAdapter()
		{
			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					t.getTransferHandler().exportAsDrag(t, e, TransferHandler.COPY);
				}
			}
		});
	}

	/** a month's heading: quiet text over a hairline */
	private JComponent monthHeader(String m, boolean first)
	{
		JLabel h = new JLabel(m);
		h.setFont(FontManager.getRunescapeBoldFont());
		h.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		h.setBorder(new EmptyBorder(0, 0, 3, 0));
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.setBorder(BorderFactory.createCompoundBorder(new EmptyBorder(first ? 0 : 10, 0, 6, 0),
			BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x3A, 0x3A, 0x3A))));
		row.add(h, BorderLayout.WEST);
		row.setMaximumSize(new Dimension(WIDTH, row.getPreferredSize().height));   // a fixed 26 px cut the year off
		return row;
	}

	// ---- OVERLAY's corner badges ----

	private static final java.util.Map<String, java.awt.image.BufferedImage> SKILL_ICONS = new java.util.HashMap<>();

	/** a level-up's skill icon (RuneLite's own small skill icons), else null */
	private static java.awt.image.BufferedImage badgeIcon(Shot s)
	{
		if (!"Levels".equals(s.type))
		{
			return null;
		}
		String skill = s.name.replaceFirst("\\s*\\(.*$", "").trim().toLowerCase(Locale.ROOT);
		synchronized (SKILL_ICONS)
		{
			if (!SKILL_ICONS.containsKey(skill))
			{
				java.awt.image.BufferedImage img = null;
				try
				{
					img = net.runelite.client.util.ImageUtil.loadImageResource(GalleryPanel.class, "/skill_icons_small/" + skill + ".png");
				}
				catch (RuntimeException e)
				{
					// not a skill name: no icon
				}
				SKILL_ICONS.put(skill, img);
			}
			return SKILL_ICONS.get(skill);
		}
	}

	private static String badgeText(Shot s)
	{
		switch (s.type)
		{
			case "Pets":
				return "Pet";
			case "Deaths":
				return "Death";
			case "Boss Kills":
				return "Boss";
			case "Clue Scroll Rewards":
				return "Clue";
			case "Collection Log":
				return "Log";
			case "Valuable Drops":
			case "Untradeable Drops":
				return "Drop";
			case "Combat Achievements":
				return "CA";
			case "Levels":
				return "Lvl";
			default:
				return null;
		}
	}

	private static Color badgeColor(Shot s)
	{
		return badgeColor(s.type);
	}

	private static Color badgeColor(String type)
	{
		switch (type)
		{
			case "Pets":
				return new Color(0x3F, 0xA8, 0x55);
			case "Deaths":
				return new Color(0xC8, 0x3A, 0x30);
			case "Boss Kills":
				return new Color(0xD9, 0x7A, 0x12);
			case "Clue Scroll Rewards":
				return new Color(0x8A, 0x5C, 0xD6);
			case "Collection Log":
				return new Color(0xB8, 0x92, 0x1C);
			default:
				return new Color(0x3A, 0x7C, 0xB8);
		}
	}

	/** OVERLAY's tile: the picture, a dark fade over its lower part, the title (and date) on it, a corner badge. */
	private static final class Overlay extends JComponent
	{
		private final int w;
		private int h;
		private final String title;
		private final String sub;
		private final java.awt.image.BufferedImage icon;
		private final String tag;
		private final Color tagColor;
		private java.awt.image.BufferedImage img;

		Overlay(int w, String title, String sub, java.awt.image.BufferedImage icon, String tag, Color tagColor)
		{
			this.w = w;
			this.h = w * 9 / 16;
			this.title = title;
			this.sub = sub;
			this.icon = icon;
			this.tag = tag;
			this.tagColor = tagColor;
			setPreferredSize(new Dimension(w, h));
			setMaximumSize(new Dimension(w, h));
		}

		void set(java.awt.image.BufferedImage i)
		{
			img = i;
			if (i != null)
			{
				h = i.getHeight();
				setPreferredSize(new Dimension(w, h));
				setMaximumSize(new Dimension(w, h));
				revalidate();
				java.awt.Container grid = getParent() == null ? null : getParent().getParent();
				if (grid instanceof JPanel)
				{
					// GalleryPanel.WIDTH, not WIDTH: inside a JComponent that is ImageObserver.WIDTH = 1 (it squashed the
					// row to one pixel at Big)
					grid.setMaximumSize(new Dimension(GalleryPanel.WIDTH, grid.getPreferredSize().height));
					((JPanel) grid).revalidate();
				}
			}
			repaint();
		}

		@Override
		protected void paintComponent(java.awt.Graphics g0)
		{
			java.awt.Graphics2D g = (java.awt.Graphics2D) g0.create();
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			if (img != null)
			{
				g.drawImage(img, 0, 0, w, h, null);
			}
			else
			{
				g.setColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
				g.fillRect(0, 0, w, h);
			}
			int fadeTop = (int) (h * 0.42);
			g.setPaint(new java.awt.GradientPaint(0, fadeTop, new Color(0, 0, 0, 0), 0, h, new Color(0, 0, 0, 215)));
			g.fillRect(0, fadeTop, w, h - fadeTop);
			g.setFont(FontManager.getRunescapeSmallFont());
			java.awt.FontMetrics fm = g.getFontMetrics();
			boolean two = sub != null && h >= 56;
			int base = h - (two ? fm.getHeight() + 4 : 5);
			g.setColor(Color.WHITE);
			g.drawString(fit(fm, title, w - 10), 5, base);
			if (two)
			{
				g.setColor(new Color(0xBB, 0xBB, 0xBB));
				g.drawString(fit(fm, sub, w - 10), 5, h - 5);
			}
			if (icon != null)
			{
				g.setColor(new Color(0, 0, 0, 170));
				g.fillRoundRect(4, 4, 20, 20, 6, 6);
				g.drawImage(icon, 4 + (20 - icon.getWidth()) / 2, 4 + (20 - icon.getHeight()) / 2, null);
			}
			else if (tag != null)
			{
				int tw = fm.stringWidth(tag) + 10;
				g.setColor(new Color(tagColor.getRed(), tagColor.getGreen(), tagColor.getBlue(), 225));
				g.fillRoundRect(4, 4, tw, 15, 8, 8);
				g.setColor(Color.WHITE);
				g.drawString(tag, 9, 4 + 12);
			}
			g.dispose();
		}

		private static String fit(java.awt.FontMetrics fm, String s, int max)
		{
			if (fm.stringWidth(s) <= max)
			{
				return s;
			}
			String e = "…";
			while (s.length() > 1 && fm.stringWidth(s + e) > max)
			{
				s = s.substring(0, s.length() - 1);
			}
			return s + e;
		}
	}

	// ---- albums, the picture size, and the right-click menus (user, 2026-11-07: "let me make folders let me pick the
	// layout like on big picture or many smaller ones. let me drag and drop them and right click them. give me a way to
	// add album rename albums ect.") ----

	/** All · each album (with how many) · + New album; the one shown in orange. */
	private void drawAlbums()
	{
		albumBar.removeAll();
		albumBar.add(chip("All (" + all.size() + ")", album == null && autoType == null, null, () -> pick(null)));
		// the auto albums: one per screenshot type the player has, filled by RuneLite's own type folders
		Map<String, Integer> n = new TreeMap<>(GalleryPanel::autoOrder);
		for (Shot s : all)
		{
			n.merge(s.type, 1, Integer::sum);
		}
		for (Map.Entry<String, Integer> e : n.entrySet())
		{
			albumBar.add(autoChip(e.getKey(), e.getValue()));
		}
		for (String a : plugin.albums().names())
		{
			albumBar.add(chip(a + " (" + plugin.albums().size(a) + ")", a.equals(album), a, () -> pick(a)));
		}
		albumBar.add(chip("+ New album", false, "", this::newAlbum));
		albumBar.setMaximumSize(new Dimension(WIDTH, albumBar.getPreferredSize().height));
		albumBar.revalidate();
		albumBar.repaint();
	}

	private void pick(String a)
	{
		album = a;
		autoType = null;
		drawAlbums();
		refilter();
	}

	private void pickType(String type)
	{
		autoType = type;
		album = null;
		drawAlbums();
		refilter();
	}

	/** The auto albums' order: the big moments first, then the rest A–Z, untyped pictures last. */
	static final List<String> AUTO_ORDER = java.util.Arrays.asList("Boss Kills", "Chest Loot", "Valuable Drops",
		"Untradeable Drops", "Pets", "Collection Log", "Clue Scroll Rewards", "Levels", "Quests", "Combat Achievements",
		"Deaths");

	static int autoOrder(String a, String b)
	{
		if (a.equals(b))
		{
			return 0;
		}
		if (Shot.OTHER.equals(a) || Shot.OTHER.equals(b))
		{
			return Shot.OTHER.equals(a) ? 1 : -1;
		}
		int i = AUTO_ORDER.indexOf(a), j = AUTO_ORDER.indexOf(b);
		if (i >= 0 || j >= 0)
		{
			return i < 0 ? 1 : j < 0 ? -1 : Integer.compare(i, j);
		}
		return a.compareToIgnoreCase(b);
	}

	/** An auto album's chip name: RuneLite's folder name in sentence case; only "Clue Scroll Rewards" is shortened ("Clues"). */
	static String autoName(String type)
	{
		switch (type)
		{
			case "Boss Kills":
				return "Boss kills";
			case "Clue Scroll Rewards":
				return "Clues";
			case "Collection Log":
				return "Collection log";
			case "Chest Loot":
				return "Chest loot";
			case "Valuable Drops":
				return "Valuable drops";
			case "Untradeable Drops":
				return "Untradeable drops";
			case "Combat Achievements":
				return "Combat achievements";
			default:
				return type;
		}
	}

	/**
	 * An auto album: fills itself from one screenshot type, so it takes no drops and has no rename/delete menu. A
	 * coloured edge in its pictures' badge colour sets it apart from the player's own albums.
	 */
	private JLabel autoChip(String type, int n)
	{
		boolean on = type.equals(autoType);
		JLabel c = chip(autoName(type) + " (" + n + ")", on, null, () -> pickType(type));
		c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 4, 4, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 3, 0, 0, badgeColor(type)),
				BorderFactory.createEmptyBorder(3, 6, 3, 7))));
		c.setToolTipText(Shot.OTHER.equals(type)
			? "Fills itself: screenshots without a type, like ones you take yourself"
			: "Fills itself: every screenshot RuneLite files under " + type);
		return c;
	}

	/**
	 * A chip: {@code target} null = "All" (no drop), "" = "+ New album" (a drop makes an album for it), else an album
	 * (a drop adds the picture; right-click to rename or delete it).
	 */
	private JLabel chip(String text, boolean on, String target, Runnable click)
	{
		JLabel c = new JLabel(text);
		c.setFont(FontManager.getRunescapeSmallFont());
		c.setOpaque(true);
		c.setBackground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.DARKER_GRAY_COLOR);
		c.setForeground(on ? Color.BLACK : target != null && target.isEmpty() ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
		c.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 4, 4, ColorScheme.DARK_GRAY_COLOR),
			BorderFactory.createEmptyBorder(3, 7, 3, 7)));
		c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		c.setToolTipText(target == null ? "Every picture" : target.isEmpty() ? "Make an album (or drop a picture here to start one)"
			: "Click to show it · drop a picture here to add it · right-click to rename or delete");
		c.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (SwingUtilities.isLeftMouseButton(e))
				{
					click.run();
				}
			}

			@Override
			public void mousePressed(MouseEvent e)
			{
				albumPopup(e, c, target);
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				albumPopup(e, c, target);
			}
		});
		if (target != null)
		{
			c.setTransferHandler(new TransferHandler()
			{
				@Override
				public boolean canImport(TransferSupport t)
				{
					return t.isDataFlavorSupported(DataFlavor.stringFlavor);
				}

				@Override
				public boolean importData(TransferSupport t)
				{
					try
					{
						String key = (String) t.getTransferable().getTransferData(DataFlavor.stringFlavor);
						String into = target.isEmpty() ? askNewAlbum() : target;
						return into != null && plugin.addToAlbum(into, key);
					}
					catch (Exception e)
					{
						return false;
					}
				}
			});
		}
		return c;
	}

	private void albumPopup(MouseEvent e, JLabel c, String target)
	{
		if (!e.isPopupTrigger() || target == null || target.isEmpty())
		{
			return;
		}
		JPopupMenu m = new JPopupMenu();
		m.add(item("Rename album...", () ->
		{
			String to = ask("Rename the album \"" + target + "\" to:", target);
			if (to != null && !to.trim().equals(target) && !plugin.renameAlbum(target, to))
			{
				say("There's already an album called \"" + to.trim() + "\" (or the name is empty).");
			}
			else if (to != null && target.equals(album))
			{
				album = to.trim().replaceAll("\\s+", " ");
				refresh();
			}
		}));
		m.add(item("Delete album", () ->
		{
			if (JOptionPane.showConfirmDialog(this, "Delete the album \"" + target + "\"?\n\nIts pictures stay in the gallery.",
				"Screenshot Gallery", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
			{
				plugin.deleteAlbum(target);
			}
		}));
		m.show(c, e.getX(), e.getY());
	}

	private void newAlbum()
	{
		String name = askNewAlbum();
		if (name != null)
		{
			pick(name);
		}
	}

	/** asks for a name and makes the album; its name, or null */
	private String askNewAlbum()
	{
		String name = ask("Name the new album:", "");
		if (name == null || name.trim().isEmpty())
		{
			return null;
		}
		if (!plugin.createAlbum(name))
		{
			say("There's already an album called \"" + name.trim() + "\".");
			return null;
		}
		return name.trim().replaceAll("\\s+", " ");
	}

	/** A picture's right-click menu. */
	private JPopupMenu shotMenu(Shot s, Runnable open)
	{
		JPopupMenu m = new JPopupMenu();
		m.add(item("Open", open));
		JMenu add = new JMenu("Add to album");
		List<String> names = plugin.albums().names();
		for (String a : names)
		{
			JMenuItem i = item(a, () -> plugin.addToAlbum(a, s.key));
			i.setEnabled(!plugin.albums().keys(a).contains(s.key));
			add.add(i);
		}
		if (!names.isEmpty())
		{
			add.addSeparator();
		}
		add.add(item("New album...", () ->
		{
			String a = askNewAlbum();
			if (a != null)
			{
				plugin.addToAlbum(a, s.key);
			}
		}));
		m.add(add);
		if (album != null)
		{
			m.add(item("Remove from \"" + album + "\"", () -> plugin.removeFromAlbum(album, s.key)));
		}
		m.addSeparator();
		m.add(item("Rename...", () ->
		{
			String t = ask("Rename the picture (empty = RuneLite's name back):", plugin.titleOf(s));
			if (t != null)
			{
				plugin.setTitle(s.key, t);
			}
		}));
		m.add(item("Delete from the gallery", () ->
		{
			if (JOptionPane.showConfirmDialog(this, "Remove \"" + plugin.titleOf(s) + "\" from the gallery?\n\nThe picture in "
				+ "RuneLite's screenshots folder stays.", "Screenshot Gallery", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
			{
				plugin.delete(s);
			}
		}));
		return m;
	}

	/** Big / Medium / Small: one, two or three pictures a row (the setting). */
	private void drawSizes()
	{
		sizeBar.removeAll();
		for (ScreenshotGalleryConfig.Size z : ScreenshotGalleryConfig.Size.values())
		{
			boolean on = z == config.size();
			String word = z == ScreenshotGalleryConfig.Size.LARGE ? "Big" : z == ScreenshotGalleryConfig.Size.MEDIUM ? "Medium" : "Small";
			JLabel b = new JLabel(word);
			b.setFont(on ? FontManager.getRunescapeBoldFont() : FontManager.getRunescapeSmallFont());
			b.setForeground(on ? ColorScheme.BRAND_ORANGE : ColorScheme.LIGHT_GRAY_COLOR);
			b.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
			b.setToolTipText(z.toString());
			b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			b.addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					plugin.setSize(z);
				}
			});
			sizeBar.add(b);
		}
		sizeBar.revalidate();
		sizeBar.repaint();
	}

	private static JMenuItem item(String text, Runnable run)
	{
		JMenuItem i = new JMenuItem(text);
		i.addActionListener(e -> run.run());
		return i;
	}

	private String ask(String prompt, String now)
	{
		Object v = JOptionPane.showInputDialog(this, prompt, "Screenshot Gallery", JOptionPane.PLAIN_MESSAGE, null, null, now);
		return v == null ? null : v.toString();
	}

	private void say(String text)
	{
		JOptionPane.showMessageDialog(this, text, "Screenshot Gallery", JOptionPane.INFORMATION_MESSAGE);
	}

	/** On this day: the newest screenshot from today's date in an earlier year, and how many more there are. */
	private void showMemory()
	{
		memory.removeAll();
		List<Shot> past = onThisDay(all, LocalDate.now());
		memory.setVisible(!past.isEmpty());
		if (past.isEmpty())
		{
			return;
		}
		Shot s = past.get(0);
		int years = LocalDate.now().getYear() - s.local().getYear();
		JLabel h = new JLabel("On this day, " + (years == 1 ? "1 year ago" : years + " years ago"));
		h.setFont(FontManager.getRunescapeBoldFont());
		h.setForeground(ColorScheme.BRAND_ORANGE);
		h.setAlignmentX(LEFT_ALIGNMENT);
		h.setBorder(new EmptyBorder(0, 0, 4, 0));
		memory.add(h);
		JPanel t = tile(s, WIDTH, true, () -> plugin.view(past, 0));
		t.setMaximumSize(new Dimension(WIDTH, Integer.MAX_VALUE));
		memory.add(t);
		if (past.size() > 1)
		{
			JLabel more = new JLabel("+ " + (past.size() - 1) + " more from this day");
			more.setToolTipText("Click the picture to see them all");
			more.setFont(FontManager.getRunescapeSmallFont());
			more.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			more.setAlignmentX(LEFT_ALIGNMENT);
			more.setBorder(new EmptyBorder(3, 0, 0, 0));
			memory.add(more);
		}
		memory.add(Box.createVerticalStrut(10));
	}

	/** Screenshots from {@code day}'s month and day in earlier years, newest first. */
	static List<Shot> onThisDay(List<Shot> shots, LocalDate day)
	{
		List<Shot> out = new ArrayList<>();
		for (Shot s : shots)
		{
			LocalDateTime t = s.local();
			if (t.getMonth() == day.getMonth() && t.getDayOfMonth() == day.getDayOfMonth() && t.getYear() < day.getYear())
			{
				out.add(s);
			}
		}
		return out;
	}

	static String when(LocalDateTime t)
	{
		return DAY.format(t) + ", " + CLOCK.format(t);
	}

	private static String esc(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static JPanel row()
	{
		JPanel p = new JPanel();
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);
		p.setAlignmentX(LEFT_ALIGNMENT);
		p.setMaximumSize(new Dimension(WIDTH, 40));
		return p;
	}

	private static JLabel note(String html)
	{
		JLabel l = new JLabel(html);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(LEFT_ALIGNMENT);
		l.setPreferredSize(new Dimension(WIDTH, 190));
		l.setVerticalAlignment(SwingConstants.TOP);
		return l;
	}

	private static JLabel link(String text, String tip, Runnable run)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setToolTipText(tip);
		l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		l.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				run.run();
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				l.setForeground(Color.WHITE);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			}
		});
		return l;
	}

}
