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
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * One screenshot, as big as the window allows. Previous / Next (or the arrow keys) step through the list it was opened
 * from; Delete removes the gallery's copy (RuneLite's own file is never touched).
 */
final class ViewerWindow extends JFrame
{
	private final ScreenshotGalleryPlugin plugin;
	private final Thumbs thumbs;
	private final Picture picture = new Picture();
	private final JLabel title = new JLabel();
	private final JLabel detail = new JLabel();
	private final JLabel position = new JLabel();
	private final JButton prev = new JButton("Previous");
	private final JButton next = new JButton("Next");

	private List<Shot> shots = new ArrayList<>();
	private int at;
	private int asked;   // which picture the last read was for: a slow read for an older one is dropped

	ViewerWindow(ScreenshotGalleryPlugin plugin, Thumbs thumbs, Window owner, java.awt.Image icon)
	{
		super("Screenshot Gallery");
		this.plugin = plugin;
		this.thumbs = thumbs;
		setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
		if (icon != null)
		{
			setIconImage(icon);
		}
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setContentPane(root);

		picture.setBackground(Color.BLACK);
		root.add(picture, BorderLayout.CENTER);

		JPanel bar = new JPanel(new BorderLayout());
		bar.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bar.setBorder(new EmptyBorder(8, 12, 8, 12));
		JPanel words = new JPanel(new BorderLayout());
		words.setOpaque(false);
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		words.add(title, BorderLayout.NORTH);
		words.add(detail, BorderLayout.SOUTH);
		bar.add(words, BorderLayout.WEST);

		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		buttons.setOpaque(false);
		position.setFont(FontManager.getRunescapeSmallFont());
		position.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		position.setBorder(new EmptyBorder(0, 0, 0, 8));
		buttons.add(position);
		prev.addActionListener(e -> step(-1));
		next.addActionListener(e -> step(1));
		JButton delete = new JButton("Delete");
		delete.setToolTipText("Removes it from the gallery. The picture in RuneLite's screenshots folder stays.");
		delete.addActionListener(e -> delete());
		JButton close = new JButton("Close");
		close.addActionListener(e -> setVisible(false));
		for (JButton b : new JButton[]{prev, next, delete, close})
		{
			b.setFocusPainted(false);
			buttons.add(b);
		}
		bar.add(buttons, BorderLayout.EAST);
		root.add(bar, BorderLayout.SOUTH);

		key(root, KeyEvent.VK_LEFT, "prev", () -> step(-1));
		key(root, KeyEvent.VK_RIGHT, "next", () -> step(1));
		key(root, KeyEvent.VK_ESCAPE, "close", () -> setVisible(false));

		Rectangle screen = owner != null && owner.getGraphicsConfiguration() != null
			? owner.getGraphicsConfiguration().getBounds() : new Rectangle(0, 0, 1280, 800);
		setSize(new Dimension(screen.width * 3 / 4, screen.height * 3 / 4));
		setMinimumSize(new Dimension(640, 420));
		setLocationRelativeTo(owner);
	}

	void show(List<Shot> list, int index)
	{
		shots = new ArrayList<>(list);
		at = Math.max(0, Math.min(index, shots.size() - 1));
		load();
		setVisible(true);
		toFront();
	}

	private Shot current()
	{
		return shots.isEmpty() ? null : shots.get(at);
	}

	private void step(int by)
	{
		if (shots.isEmpty())
		{
			return;
		}
		at = Math.max(0, Math.min(shots.size() - 1, at + by));
		load();
	}

	private void delete()
	{
		Shot s = current();
		if (s == null || JOptionPane.showConfirmDialog(this, "Remove \"" + plugin.titleOf(s) + "\" from the gallery?\n\nThe picture in RuneLite's "
			+ "screenshots folder stays.", "Screenshot Gallery", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
		{
			return;
		}
		plugin.delete(s);
		shots.remove(at);
		if (shots.isEmpty())
		{
			setVisible(false);
			return;
		}
		at = Math.min(at, shots.size() - 1);
		load();
	}

	private void load()
	{
		Shot s = current();
		if (s == null)
		{
			return;
		}
		title.setText(plugin.titleOf(s));
		detail.setText(GalleryPanel.when(s.local()) + (Shot.OTHER.equals(s.type) ? "" : "  ·  " + s.type)
			+ (s.character.isEmpty() ? "" : "  ·  " + s.character));
		position.setText((at + 1) + " of " + shots.size());
		prev.setEnabled(at > 0);
		next.setEnabled(at < shots.size() - 1);
		picture.set(null, "Loading…");
		int ticket = ++asked;
		thumbs.full(s.file, img ->
		{
			if (ticket == asked)
			{
				picture.set(img, img == null ? "Can't open this picture" : null);
			}
		});
	}

	private static void key(JComponent c, int key, String name, Runnable run)
	{
		c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
		c.getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				run.run();
			}
		});
	}

	/** The picture scaled to fit, never stretched past its own size, on black. */
	private static final class Picture extends JPanel
	{
		private BufferedImage img;
		private String text;

		Picture()
		{
			setBorder(BorderFactory.createEmptyBorder());
		}

		void set(BufferedImage img, String text)
		{
			this.img = img;
			this.text = text;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			super.paintComponent(g0);
			Graphics2D g = (Graphics2D) g0;
			if (img == null)
			{
				if (text != null)
				{
					g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
					g.setFont(FontManager.getRunescapeFont());
					int w = g.getFontMetrics().stringWidth(text);
					g.drawString(text, (getWidth() - w) / 2, getHeight() / 2);
				}
				return;
			}
			double k = Math.min(1.0, Math.min(getWidth() / (double) img.getWidth(), getHeight() / (double) img.getHeight()));
			int w = (int) Math.round(img.getWidth() * k), h = (int) Math.round(img.getHeight() * k);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(img, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
		}
	}
}
