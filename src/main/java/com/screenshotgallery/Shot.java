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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Filepath;

/**
 * One screenshot in the gallery's own folder ({@link Library}), laid out like RuneLite's: its Screenshot plugin saves to
 * {@code screenshots/<character>[-<world type>]/<type folder>/<name> <yyyy-MM-dd_HH-mm-ss>[ (n)].png}
 * (read from RuneLite 1.13.6's ImageCapture: the player folder, " -" + the profile type when it isn't a normal world,
 * the sub-folder, the time format and a number when the name is taken — "(1)" with no space in the user's real folder).
 * Screenshots taken with RuneLite's camera button
 * have no type folder; a file straight in the screenshots folder was taken on the login screen.
 */
final class Shot
{
	static final String OTHER = "Other";

	/**
	 * "Fishing(99) 2026-11-07_12-30-45.png", "Death 2026-11-07_12-30-45 (2).png", and "2026-11-07_17-08-32(1).png" — the
	 * same-second number with NO space, as the user's own camera-button shots were saved on 2026-11-07
	 */
	private static final Pattern NAME = Pattern.compile("^(.*?)\\s*(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})(?: ?\\(\\d+\\))?\\.(?i:png|jpe?g)$");
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	/** "Fishing(99)", "Vorkath(123)", "Hard(12)" — a name and a number */
	private static final Pattern COUNTED = Pattern.compile("^(.+?)\\s*\\(([\\d,]+)\\)$");
	/** "Collection log (Dragon pickaxe)" */
	private static final Pattern IN_BRACKETS = Pattern.compile("^(?i:collection log|combat task|valuable drop|untradeable drop)\\s*\\((.+)\\)$");

	/** the gallery's copy */
	final Filepath file;
	/** its place in the gallery, "character/type/file" — what albums and renames remember it by */
	final String key;
	/** the character's folder name ("" = taken on the login screen) */
	final String character;
	/** the type folder, e.g. "Levels", "Pets", "Deaths"; OTHER = no folder */
	final String type;
	/** what RuneLite named it, without the time */
	final String name;
	/** when it was taken: from the name, else the file's time (epoch ms) */
	final long time;

	Shot(Filepath file, String key, String character, String type, String name, long time)
	{
		this.file = file;
		this.key = key;
		this.character = character;
		this.type = type;
		this.name = name;
		this.time = time;
	}

	/** True for the picture files RuneLite writes (and the gallery's JPEG copies). */
	static boolean picture(String fileName)
	{
		String lower = fileName.toLowerCase();
		return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
	}

	/**
	 * A picture at {@code parts} (its path under the screenshots folder: [character, type..., file name]); null if it
	 * isn't a picture. {@code fileTime} is used when the name has no time in it.
	 */
	static Shot of(Filepath file, List<String> parts, long fileTime)
	{
		String fileName = parts.isEmpty() ? "" : parts.get(parts.size() - 1);
		if (!picture(fileName))
		{
			return null;
		}
		String character = parts.size() >= 2 ? parts.get(0) : "";
		String type = parts.size() >= 3 ? String.join("/", parts.subList(1, parts.size() - 1)) : OTHER;

		String name = fileName.replaceFirst("(?i)\\.(png|jpe?g)$", "");
		long time = fileTime;
		Matcher m = NAME.matcher(fileName);
		if (m.matches())
		{
			name = m.group(1).trim();
			try
			{
				time = LocalDateTime.parse(m.group(2), TIME).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
			}
			catch (DateTimeParseException e)
			{
				// keep the file's own time
			}
		}
		return new Shot(file, String.join("/", parts), character, type, name, time);
	}

	/**
	 * A readable title. The name patterns are RuneLite's (its screenshot code: a skill with its level, a boss with its
	 * kill count, a clue tier with its count, "Collection log (item)"); anything else is shown as RuneLite named it.
	 */
	String title()
	{
		Matcher in = IN_BRACKETS.matcher(name);
		if (in.matches())
		{
			return in.group(1);
		}
		Matcher c = COUNTED.matcher(name);
		if (c.matches())
		{
			String what = c.group(1), n = c.group(2);
			switch (type)
			{
				case "Levels":
					return what + " level " + n;
				case "Boss Kills":
					return what + " kill " + n;
				case "Clue Scroll Rewards":
					return what + " clue " + n;
				default:
					return what + " " + n;
			}
		}
		if (name.isEmpty())
		{
			return OTHER.equals(type) ? "Screenshot" : singular(type);
		}
		return name;
	}

	/** "Deaths" → "Death" for a nameless file in a type folder */
	private static String singular(String type)
	{
		return type.endsWith("s") && !type.endsWith("ss") ? type.substring(0, type.length() - 1) : type;
	}

	LocalDateTime local()
	{
		return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());
	}
}
