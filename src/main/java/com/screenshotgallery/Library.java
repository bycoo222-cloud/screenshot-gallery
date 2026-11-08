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

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import net.runelite.client.util.Filepath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The gallery's own copies, in the plugin's folder (RuneLite's {@code Filepath}: a Plugin Hub plugin reads and writes
 * only there, and in folders the player picks), laid out like RuneLite's screenshots folder:
 * {@code shots/<character>/<type>/<name> <time>.jpg}. A copy is a JPEG at 90% quality, so keeping copies doesn't double
 * the space: MEASURED 2026-11-07 on three real 1920-wide game frames, 27-45% of the PNG's size (a small flat picture
 * can come out bigger). New screenshots arrive with RuneLite's
 * {@code ScreenshotTaken}; older ones with {@link #importFrom} from a folder the player picks.
 */
final class Library
{
	private static final Logger log = LoggerFactory.getLogger(Library.class);
	/** shots/<character>/<type>/<file>, a level to spare */
	private static final int DEPTH = 4;
	private static final float QUALITY = 0.9f;

	private final Filepath root;

	Library(Filepath root)
	{
		this.root = root;
	}

	/** Every picture in the gallery, newest first. */
	List<Shot> scan()
	{
		List<Shot> out = new ArrayList<>();
		if (!root.isDirectory())
		{
			return out;
		}
		try (Stream<Filepath> files = root.walk(DEPTH))
		{
			for (Filepath f : files.collect(Collectors.toList()))
			{
				if (!f.isFile() || !Shot.picture(f.getFileName()))
				{
					continue;
				}
				long time = 0;
				try
				{
					time = f.getLastModifiedTime().toMillis();
				}
				catch (IOException e)
				{
					// the name's time is used
				}
				Shot s = Shot.of(f, parts(root, f), time);
				if (s != null)
				{
					out.add(s);
				}
			}
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Screenshot Gallery: can't read the gallery", e);
		}
		out.sort(Comparator.comparingLong((Shot s) -> s.time).reversed());
		return out;
	}

	/** A file's names under {@code top}: [character, type..., file name]. */
	static List<String> parts(Filepath top, Filepath f)
	{
		List<String> out = new ArrayList<>();
		for (Filepath p = f; p != null && !p.equals(top) && !p.isRoot(); p = p.getParent())
		{
			out.add(0, p.getFileName());
		}
		return out;
	}

	/**
	 * Keeps a copy of a screenshot RuneLite just saved, at {@code parts} ({@link #place}). A name already taken gets
	 * "(1)", "(2)"... as RuneLite's own files do (several pictures in one second).
	 */
	void keep(List<String> parts, BufferedImage img) throws IOException
	{
		if (parts.isEmpty() || img == null)
		{
			return;
		}
		Filepath out = target(parts);
		String base = parts.get(parts.size() - 1).replaceFirst("(?i)\\.(png|jpe?g)$", "");
		for (int i = 1; out.exists() && i < 1000; i++)
		{
			List<String> next = new ArrayList<>(parts);
			next.set(next.size() - 1, base + "(" + i + ").png");
			out = target(next);
		}
		out.getParent().createDirectories();
		writeJpeg(img, out);
	}

	/**
	 * Where RuneLite filed a new screenshot, as [character, type, file name]: its character folder (null = none, then the
	 * file alone), its type folder (none for a manual screenshot) and "&lt;name&gt; &lt;time&gt;.png".
	 */
	static List<String> place(String character, ShotNamer.Name name, long at)
	{
		List<String> out = new ArrayList<>();
		if (character != null && !character.isEmpty())
		{
			out.add(character);
			if (!name.manual())
			{
				out.add(name.type);
			}
		}
		out.add(ShotNamer.fileName(name.file, at) + ".png");
		return out;
	}

	/**
	 * Copies every picture under {@code picked} into the gallery (skipping ones already in it). {@code picked} may be
	 * the .runelite folder (only its screenshots folder is read: never RuneLite's other pictures or the gallery's own
	 * copies), RuneLite's screenshots folder, one character's folder or one type's folder ({@link #base}). Returns how
	 * many were added.
	 */
	int importFrom(Filepath picked, IntConsumer progress, java.util.function.BooleanSupplier stop) throws IOException
	{
		if (RUNELITE.equals(picked.getFileName()))
		{
			Filepath shots = picked.joinSegment("screenshots");
			if (!shots.isDirectory())
			{
				throw new IOException("no screenshots folder in " + RUNELITE);
			}
			picked = shots;
		}
		List<String> base = base(picked);
		int added = 0;
		List<Filepath> files;
		try (Stream<Filepath> all = picked.walk(DEPTH))
		{
			files = all.filter(f -> f.isFile() && Shot.picture(f.getFileName())).collect(Collectors.toList());
		}
		for (Filepath f : files)
		{
			if (stop.getAsBoolean())
			{
				break;   // the plugin was turned off
			}
			List<String> parts = new ArrayList<>(base);
			parts.addAll(parts(picked, f));
			Filepath out = target(parts);
			if (out.exists())
			{
				continue;
			}
			try (InputStream in = f.openInputStream())
			{
				BufferedImage img = ImageIO.read(in);
				if (img == null)
				{
					continue;
				}
				out.getParent().createDirectories();
				writeJpeg(img, out);
				added++;
				progress.accept(added);
			}
			catch (IOException | RuntimeException e)
			{
				log.debug("Screenshot Gallery: can't import {}", f, e);
			}
		}
		return added;
	}

	/** RuneLite's type folders (its Screenshot plugin's names, read from client 1.13.6) */
	static final java.util.Set<String> TYPES = new java.util.HashSet<>(java.util.Arrays.asList("Levels", "Pets", "Deaths",
		"Boss Kills", "Clue Scroll Rewards", "Collection Log", "Chest Loot", "Valuable Drops", "Untradeable Drops",
		"Combat Achievements", "Kingdom Rewards", "Quests", "Duels", "Wilderness Loot Chest", "Friends Chat Kicks",
		"League Tasks", "BA High Gambles", "Friend Deaths", "Clan Deaths"));
	/** RuneLite's own folder: picking it imports its screenshots folder */
	static final String RUNELITE = ".runelite";
	/** the character a picked type folder's pictures are filed under (its own folder can't see its parent's name) */
	static final String IMPORTED = "Imported";

	/**
	 * Where under the gallery a picked folder's pictures go, from its own name only: RuneLite's file API keeps a picked
	 * folder from looking at the folders above it. "screenshots" = the whole folder; a type's name ("Levels") = that
	 * type, filed under {@link #IMPORTED}; anything else = one character's folder.
	 */
	static List<String> base(Filepath picked)
	{
		String name = picked.getFileName();
		List<String> out = new ArrayList<>();
		if (name == null || name.isEmpty() || name.equalsIgnoreCase("screenshots"))
		{
			return out;
		}
		if (TYPES.contains(name))
		{
			out.add(IMPORTED);
		}
		out.add(name);
		return out;
	}

	void delete(Shot s) throws IOException
	{
		s.file.deleteIfExists();
	}

	/** The gallery's file for [character, type..., file name]: the same names, as a .jpg. */
	private Filepath target(List<String> parts)
	{
		Filepath p = root;
		for (int i = 0; i < parts.size(); i++)
		{
			String seg = parts.get(i);
			if (i == parts.size() - 1)
			{
				seg = seg.replaceFirst("(?i)\\.(png|jpe?g)$", "") + ".jpg";
			}
			p = p.joinSegment(seg);
		}
		return p;
	}

	static void writeJpeg(BufferedImage img, Filepath out) throws IOException
	{
		BufferedImage rgb = img;
		if (img.getType() != BufferedImage.TYPE_INT_RGB)
		{
			rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);   // JPEG has no see-through
			Graphics2D g = rgb.createGraphics();
			g.drawImage(img, 0, 0, null);
			g.dispose();
		}
		Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
		if (!writers.hasNext())
		{
			throw new IOException("no JPEG writer");
		}
		ImageWriter w = writers.next();
		try (OutputStream raw = out.openOutputStream(); ImageOutputStream ios = ImageIO.createImageOutputStream(raw))
		{
			ImageWriteParam param = w.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(QUALITY);
			w.setOutput(ios);
			w.write(null, new IIOImage(rgb, null, null), param);
		}
		finally
		{
			w.dispose();
		}
	}
}
